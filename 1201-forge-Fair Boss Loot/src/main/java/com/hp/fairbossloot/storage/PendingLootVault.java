package com.hp.fairbossloot.storage;

import com.hp.fairbossloot.FairBossLoot;
import com.hp.fairbossloot.config.FairBossLootConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.20.1 持久化待领战利品暂存库：存放因背包满、离场或虚空救援未能直接交付的专属战利品。
 */
public final class PendingLootVault extends SavedData {
    private static final String DATA_NAME = FairBossLoot.MOD_ID + "_pending_loot";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_ITEM = "item";
    private static final String TAG_SOURCE = "source";
    private static final String TAG_TIMESTAMP = "timestamp";

    private final Map<UUID, List<PendingLootEntry>> vaults = new HashMap<>();

    public record PendingLootEntry(ItemStack stack, String sourceName, long gameTime) {
    }

    /**
     * 获取当前存档主世界的全局待领战利品暂存库。
     */
    public static PendingLootVault get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(PendingLootVault::load, PendingLootVault::new, DATA_NAME);
    }

    private static PendingLootVault load(CompoundTag tag) {
        PendingLootVault vault = new PendingLootVault();
        ListTag playersTag = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < playersTag.size(); i++) {
            CompoundTag playerTag = playersTag.getCompound(i);
            if (!playerTag.hasUUID(TAG_UUID)) {
                continue;
            }
            UUID uuid = playerTag.getUUID(TAG_UUID);
            ListTag entriesTag = playerTag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            List<PendingLootEntry> entries = new ArrayList<>();
            for (int j = 0; j < entriesTag.size(); j++) {
                CompoundTag entryTag = entriesTag.getCompound(j);
                ItemStack stack = ItemStack.of(entryTag.getCompound(TAG_ITEM));
                if (stack.isEmpty()) {
                    continue;
                }
                String source = entryTag.getString(TAG_SOURCE);
                long timestamp = entryTag.getLong(TAG_TIMESTAMP);
                entries.add(new PendingLootEntry(stack, source, timestamp));
            }
            if (!entries.isEmpty()) {
                vault.vaults.put(uuid, entries);
            }
        }
        return vault;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, List<PendingLootEntry>> entry : this.vaults.entrySet()) {
            List<PendingLootEntry> list = entry.getValue();
            if (list.isEmpty()) {
                continue;
            }
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_UUID, entry.getKey());
            ListTag entriesTag = new ListTag();
            for (PendingLootEntry itemEntry : list) {
                if (itemEntry.stack().isEmpty()) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(TAG_ITEM, itemEntry.stack().save(new CompoundTag()));
                entryTag.putString(TAG_SOURCE, itemEntry.sourceName());
                entryTag.putLong(TAG_TIMESTAMP, itemEntry.gameTime());
                entriesTag.add(entryTag);
            }
            if (!entriesTag.isEmpty()) {
                playerTag.put(TAG_ENTRIES, entriesTag);
                playersTag.add(playerTag);
            }
        }
        tag.put(TAG_PLAYERS, playersTag);
        return tag;
    }

    /**
     * 向指定玩家的暂存库添加物品（自动尝试合并同来源可堆叠物品），返回因达到上限未能存入的溢出物品列表。
     */
    public List<ItemStack> addLoot(UUID playerId, List<ItemStack> stacks, String sourceName, long gameTime) {
        List<ItemStack> overflow = new ArrayList<>();
        if (stacks.isEmpty()) {
            return overflow;
        }

        int maxStacks = FairBossLootConfig.MAX_PENDING_STACKS_PER_PLAYER.get();
        List<PendingLootEntry> list = this.vaults.computeIfAbsent(playerId, id -> new ArrayList<>());
        boolean changed = false;

        for (ItemStack raw : stacks) {
            if (raw.isEmpty()) {
                continue;
            }
            ItemStack remaining = raw.copy();

            for (PendingLootEntry existing : list) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (!existing.sourceName().equals(sourceName)) {
                    continue;
                }
                ItemStack existingStack = existing.stack();
                if (ItemStack.isSameItemSameTags(existingStack, remaining)
                        && existingStack.getCount() < existingStack.getMaxStackSize()) {
                    int space = existingStack.getMaxStackSize() - existingStack.getCount();
                    int transfer = Math.min(space, remaining.getCount());
                    existingStack.grow(transfer);
                    remaining.shrink(transfer);
                    changed = true;
                }
            }

            while (!remaining.isEmpty()) {
                if (list.size() >= maxStacks) {
                    overflow.add(remaining.copy());
                    break;
                }
                int splitSize = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack chunk = remaining.split(splitSize);
                list.add(new PendingLootEntry(chunk, sourceName, gameTime));
                changed = true;
            }
        }

        if (changed) {
            this.setDirty();
        }
        return overflow;
    }

    public List<PendingLootEntry> getEntries(UUID playerId) {
        List<PendingLootEntry> list = this.vaults.get(playerId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public int getPendingCount(UUID playerId) {
        List<PendingLootEntry> list = this.vaults.get(playerId);
        return list == null ? 0 : list.size();
    }

    public int claimIntoInventory(UUID playerId, Predicate<ItemStack> inserter) {
        List<PendingLootEntry> list = this.vaults.get(playerId);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int claimed = 0;
        Iterator<PendingLootEntry> it = list.iterator();
        while (it.hasNext()) {
            PendingLootEntry entry = it.next();
            ItemStack stack = entry.stack();
            if (stack.isEmpty()) {
                it.remove();
                this.setDirty();
                continue;
            }
            int beforeCount = stack.getCount();
            boolean fullyInserted = inserter.test(stack);
            if (fullyInserted || stack.isEmpty()) {
                it.remove();
                claimed++;
                this.setDirty();
            } else {
                if (stack.getCount() < beforeCount) {
                    this.setDirty();
                }
                break;
            }
        }
        if (list.isEmpty()) {
            this.vaults.remove(playerId);
        }
        return claimed;
    }

    public int clear(UUID playerId) {
        List<PendingLootEntry> removed = this.vaults.remove(playerId);
        if (removed == null || removed.isEmpty()) {
            return 0;
        }
        this.setDirty();
        return removed.size();
    }
}
