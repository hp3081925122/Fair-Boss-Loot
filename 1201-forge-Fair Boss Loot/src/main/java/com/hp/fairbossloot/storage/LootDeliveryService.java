package com.hp.fairbossloot.storage;

import com.hp.fairbossloot.config.DeliveryMode;
import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.drop.IsolatedDropAccess;
import com.hp.fairbossloot.drop.PersonalXpAccess;
import com.hp.fairbossloot.i18n.ServerLang;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 个人专属战利品与经验球发放服务（1.20.1 Forge）。
 */
public final class LootDeliveryService {
    private LootDeliveryService() {
    }

    /**
     * 为单个地面 ItemEntity 应用个人隔离、发光、存活时间与环境/虚空保护属性。
     */
    public static void applyIsolatedDropSettings(ItemEntity itemEntity, UUID ownerId, String sourceName) {
        itemEntity.setTarget(ownerId);
        itemEntity.setGlowingTag(FairBossLootConfig.GLOWING_ISOLATED_DROPS.get());
        itemEntity.lifespan = FairBossLootConfig.ISOLATED_DROP_LIFESPAN_TICKS.get();
        if (itemEntity instanceof IsolatedDropAccess access) {
            access.fairBossLoot$configureIsolated(
                    ownerId,
                    FairBossLootConfig.PROTECT_FROM_ENVIRONMENT.get(),
                    FairBossLootConfig.RESCUE_FROM_VOID.get(),
                    sourceName
            );
        }
    }

    /**
     * 在指定位置生成一个受保护的个人隔离掉落物实体。
     */
    public static ItemEntity spawnIsolatedItem(ServerLevel level, Vec3 pos, ItemStack stack, UUID ownerId, String sourceName) {
        ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y + 0.25D, pos.z, stack.copy());
        itemEntity.setDefaultPickUpDelay();
        applyIsolatedDropSettings(itemEntity, ownerId, sourceName);
        level.addFreshEntity(itemEntity);
        return itemEntity;
    }

    /**
     * 按指定模式向玩家发放一批个人专属战利品。
     */
    public static void deliverLoot(
            ServerLevel fallbackLevel,
            UUID ownerId,
            List<ItemStack> stacks,
            Vec3 fallbackPos,
            Component sourceDisplayName,
            DeliveryMode mode
    ) {
        List<ItemStack> cleanStacks = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                cleanStacks.add(stack.copy());
            }
        }
        if (cleanStacks.isEmpty()) {
            return;
        }

        String sourceName = sourceDisplayName.getString();
        if (mode == DeliveryMode.ISOLATED_DROP) {
            for (ItemStack stack : cleanStacks) {
                spawnIsolatedItem(fallbackLevel, fallbackPos, stack, ownerId, sourceName);
            }
            return;
        }

        MinecraftServer server = fallbackLevel.getServer();
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(ownerId);
        List<ItemStack> remaining = new ArrayList<>();

        if (onlinePlayer != null && !onlinePlayer.isDeadOrDying()) {
            for (ItemStack stack : cleanStacks) {
                ItemStack toInsert = stack.copy();
                insertIntoInventorySafely(onlinePlayer, toInsert);
                if (!toInsert.isEmpty()) {
                    remaining.add(toInsert);
                }
            }
            if (remaining.isEmpty()) {
                return;
            }
            if (mode == DeliveryMode.HYBRID) {
                ServerLevel playerLevel = onlinePlayer.serverLevel();
                Vec3 feetPos = onlinePlayer.position();
                for (ItemStack stack : remaining) {
                    spawnIsolatedItem(playerLevel, feetPos, stack, ownerId, sourceName);
                }
                return;
            }
        } else {
            remaining.addAll(cleanStacks);
        }

        PendingLootVault vault = PendingLootVault.get(server);
        List<ItemStack> vaultOverflow = vault.addLoot(ownerId, remaining, sourceName, fallbackLevel.getGameTime());
        int storedCount = remaining.size() - vaultOverflow.size();

        if (!vaultOverflow.isEmpty()) {
            ServerLevel dropLevel = onlinePlayer != null ? onlinePlayer.serverLevel() : fallbackLevel;
            Vec3 dropPos = onlinePlayer != null ? onlinePlayer.position() : fallbackPos;
            for (ItemStack stack : vaultOverflow) {
                spawnIsolatedItem(dropLevel, dropPos, stack, ownerId, sourceName);
            }
            if (onlinePlayer != null) {
                onlinePlayer.sendSystemMessage(ServerLang.format(
                        onlinePlayer,
                        "fair_boss_loot.vault.overflow_dropped",
                        sourceDisplayName
                ));
            }
        }

        if (storedCount > 0 && onlinePlayer != null) {
            MutableComponent msg = ServerLang.format(
                    onlinePlayer,
                    "fair_boss_loot.vault.stored",
                    storedCount,
                    sourceDisplayName
            );
            msg.append(Component.literal(" ")).append(createClaimButton(onlinePlayer));
            onlinePlayer.sendSystemMessage(msg);
        }
    }

    /**
     * 虚空救援：当个人隔离掉落物掉出世界底部时，将其自动转入主人的待领暂存库。
     */
    public static boolean rescueVoidItem(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!(itemEntity instanceof IsolatedDropAccess access) || !access.fairBossLoot$isIsolated() || !access.fairBossLoot$isVoidRescueEnabled()) {
            return false;
        }
        UUID ownerId = access.fairBossLoot$getOwnerId();
        ItemStack stack = itemEntity.getItem();
        if (ownerId == null || stack.isEmpty()) {
            return false;
        }

        MinecraftServer server = level.getServer();
        String sourceName = access.fairBossLoot$getSourceName();
        PendingLootVault vault = PendingLootVault.get(server);
        List<ItemStack> overflow = vault.addLoot(ownerId, List.of(stack.copy()), sourceName, level.getGameTime());

        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(ownerId);
        if (overflow.isEmpty()) {
            if (onlinePlayer != null) {
                MutableComponent msg = ServerLang.format(
                        onlinePlayer,
                        "fair_boss_loot.vault.void_rescued",
                        stack.getDisplayName()
                );
                msg.append(Component.literal(" ")).append(createClaimButton(onlinePlayer));
                onlinePlayer.sendSystemMessage(msg);
            }
            itemEntity.discard();
            return true;
        }

        if (onlinePlayer != null && onlinePlayer.serverLevel() == level && !onlinePlayer.isDeadOrDying()) {
            itemEntity.teleportTo(onlinePlayer.getX(), onlinePlayer.getY() + 0.5D, onlinePlayer.getZ());
            itemEntity.setDeltaMovement(Vec3.ZERO);
            return true;
        }
        var spawn = level.getSharedSpawnPos();
        itemEntity.teleportTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D);
        itemEntity.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /**
     * 为指定玩家生成专属经验球（原生触发经验修补与拾取音效，仅该玩家可见且可拾取）。
     */
    public static void spawnPersonalExperience(
            ServerLevel fallbackLevel,
            Vec3 fallbackPos,
            UUID ownerId,
            int totalXp,
            DeliveryMode mode
    ) {
        if (totalXp <= 0) {
            return;
        }
        ServerLevel targetLevel = fallbackLevel;
        Vec3 targetPos = fallbackPos;

        if (mode == DeliveryMode.DIRECT_INVENTORY || mode == DeliveryMode.HYBRID) {
            ServerPlayer online = fallbackLevel.getServer().getPlayerList().getPlayer(ownerId);
            if (online != null && !online.isDeadOrDying()) {
                targetLevel = online.serverLevel();
                targetPos = online.position();
            }
        }

        int remaining = totalXp;
        while (remaining > 0) {
            int chunk = ExperienceOrb.getExperienceValue(remaining);
            remaining -= chunk;
            ExperienceOrb orb = new ExperienceOrb(targetLevel, targetPos.x, targetPos.y + 0.25D, targetPos.z, chunk);
            if (orb instanceof PersonalXpAccess access) {
                access.fairBossLoot$setOwnerId(ownerId);
            }
            targetLevel.addFreshEntity(orb);
        }
    }

    /**
     * 安全地将物品放入玩家主背包（0..35 槽位），绕开原版 Inventory#add 在创造模式满背包时清零物品的行为。
     */
    public static boolean insertIntoInventorySafely(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        var inventory = player.getInventory();
        boolean changed = false;

        if (stack.isStackable()) {
            for (int slot = 0; slot < inventory.items.size() && !stack.isEmpty(); slot++) {
                ItemStack existing = inventory.items.get(slot);
                if (existing.isEmpty()) {
                    continue;
                }
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    int max = Math.min(inventory.getMaxStackSize(), existing.getMaxStackSize());
                    int space = max - existing.getCount();
                    if (space > 0) {
                        int move = Math.min(space, stack.getCount());
                        existing.grow(move);
                        existing.setPopTime(5);
                        stack.shrink(move);
                        changed = true;
                    }
                }
            }
        }

        for (int slot = 0; slot < inventory.items.size() && !stack.isEmpty(); slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                int move = Math.min(inventory.getMaxStackSize(), Math.min(stack.getMaxStackSize(), stack.getCount()));
                ItemStack placed = stack.split(move);
                placed.setPopTime(5);
                inventory.items.set(slot, placed);
                changed = true;
            }
        }

        if (changed) {
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return stack.isEmpty();
    }

    /**
     * 创建可点击执行 /fairbossloot claim 的聊天交互按钮。
     */
    public static MutableComponent createClaimButton(ServerPlayer player) {
        return ServerLang.format(player, "fair_boss_loot.vault.claim_button").withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fairbossloot claim"))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        ServerLang.format(player, "fair_boss_loot.vault.claim_hover")
                )));
    }
}
