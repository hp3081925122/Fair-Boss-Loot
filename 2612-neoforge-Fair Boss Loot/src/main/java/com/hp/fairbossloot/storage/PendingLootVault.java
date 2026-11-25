package com.hp.fairbossloot.storage;

import com.hp.fairbossloot.FairBossLoot;
import com.hp.fairbossloot.config.FairBossLootConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 26.1.2 持久化待领战利品暂存库：基于 SavedDataType + Codec 保存待领物品。
 */
public final class PendingLootVault extends SavedData {
    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath(FairBossLoot.MOD_ID, "pending_loot");

    public record PendingLootEntry(ItemStack stack, String sourceName, long gameTime) {
        public static final Codec<PendingLootEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter(PendingLootEntry::stack),
                Codec.STRING.fieldOf("source").forGetter(PendingLootEntry::sourceName),
                Codec.LONG.fieldOf("timestamp").forGetter(PendingLootEntry::gameTime)
        ).apply(instance, PendingLootEntry::new));
    }

    private record PlayerVaultRecord(UUID playerId, List<PendingLootEntry> entries) {
        static final Codec<PlayerVaultRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerVaultRecord::playerId),
                PendingLootEntry.CODEC.listOf().fieldOf("entries").forGetter(PlayerVaultRecord::entries)
        ).apply(instance, PlayerVaultRecord::new));
    }

    private static final Codec<PendingLootVault> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlayerVaultRecord.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(PendingLootVault::toRecords)
    ).apply(instance, PendingLootVault::fromRecords));

    private static final SavedDataType<PendingLootVault> TYPE = new SavedDataType<>(
            DATA_ID,
            PendingLootVault::new,
            CODEC
    );

    private final Map<UUID, List<PendingLootEntry>> vaults = new HashMap<>();

    /**
     * 获取当前存档主世界的全局待领战利品暂存库。
     */
    public static PendingLootVault get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    private static PendingLootVault fromRecords(List<PlayerVaultRecord> records) {
        PendingLootVault vault = new PendingLootVault();
        for (PlayerVaultRecord rec : records) {
            List<PendingLootEntry> clean = new ArrayList<>();
            for (PendingLootEntry entry : rec.entries()) {
                if (!entry.stack().isEmpty()) {
                    clean.add(entry);
                }
            }
            if (!clean.isEmpty()) {
                vault.vaults.put(rec.playerId(), clean);
            }
        }
        return vault;
    }

    private List<PlayerVaultRecord> toRecords() {
        List<PlayerVaultRecord> records = new ArrayList<>();
        for (Map.Entry<UUID, List<PendingLootEntry>> entry : this.vaults.entrySet()) {
            List<PendingLootEntry> clean = new ArrayList<>();
            for (PendingLootEntry itemEntry : entry.getValue()) {
                if (!itemEntry.stack().isEmpty()) {
                    clean.add(itemEntry);
                }
            }
            if (!clean.isEmpty()) {
                records.add(new PlayerVaultRecord(entry.getKey(), clean));
            }
        }
        return records;
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
                if (ItemStack.isSameItemSameComponents(existingStack, remaining)
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
