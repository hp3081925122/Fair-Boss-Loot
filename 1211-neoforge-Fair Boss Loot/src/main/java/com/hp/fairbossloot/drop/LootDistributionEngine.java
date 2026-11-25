package com.hp.fairbossloot.drop;

import com.hp.fairbossloot.config.DeliveryMode;
import com.hp.fairbossloot.config.ExperienceMode;
import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.storage.LootDeliveryService;
import com.hp.fairbossloot.tracking.AttackerResolver;
import com.hp.fairbossloot.tracking.DamageTrackerData;
import com.hp.fairbossloot.tracking.ModAttachments;
import com.hp.fairbossloot.tracking.TargetMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 战利品与经验分配核心引擎：衔接 Mixin 死亡捕获与 NeoForge 掉落/经验事件。
 */
public final class LootDistributionEngine {
    private LootDistributionEngine() {
    }

    /**
     * 在 LivingEntity#dropAllDeathLoot 开头冻结结算并初始化阶段捕获状态。
     */
    @Nullable
    public static DropCaptureState beginDeathDropCapture(LivingEntity entity, ServerLevel level, DamageSource source) {
        if (!TargetMatcher.shouldTrack(entity)) {
            return null;
        }
        DamageTrackerData tracker = entity.getData(ModAttachments.DAMAGE_TRACKER);
        AttackerResolver.ResolvedAttacker killer = AttackerResolver.resolve(level, source);
        DamageTrackerData.SettlementResult settlement = tracker.freezeSettlement(entity, killer, level.getGameTime());
        if (!settlement.hasRewardedPlayers()) {
            return null;
        }
        if (settlement.rewardedPlayerIds().size() == 1 && !FairBossLootConfig.PROTECT_SOLO_KILLS.get()) {
            // 单人击杀关闭保护时仅允许可选播报，不接管原版掉落
            DamageSummaryBroadcaster.broadcastIfNeeded(level, entity, tracker, settlement, FairBossLootConfig.DELIVERY_MODE.get());
            return null;
        }
        DropCaptureState state = new DropCaptureState(settlement);
        if (entity instanceof DropCaptureAccess access) {
            access.fairBossLoot$setDropCaptureState(state);
        }
        return state;
    }

    /**
     * 在 LivingEntity#dropAllDeathLoot 结束时清理瞬态捕获上下文，防止引用残留。
     */
    public static void endDeathDropCapture(LivingEntity entity) {
        if (entity instanceof DropCaptureAccess access) {
            access.fairBossLoot$setDropCaptureState(null);
        }
    }

    /**
     * 在 LivingDropsEvent(LOWEST) 汇合主玩家、其他达标玩家独立战利品表掉落、自定义死亡掉落以及第三方模组追加掉落。
     */
    public static void handleLivingDropsLowest(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(entity instanceof DropCaptureAccess access)) {
            return;
        }
        DropCaptureState state = access.fairBossLoot$getDropCaptureState();
        if (state == null) {
            return;
        }

        DamageTrackerData.SettlementResult settlement = state.settlement();
        List<UUID> rewardedIds = settlement.rewardedPlayerIds();
        if (rewardedIds.isEmpty()) {
            return;
        }

        DeliveryMode mode = FairBossLootConfig.DELIVERY_MODE.get();
        boolean duplicateCustom = FairBossLootConfig.DUPLICATE_CUSTOM_DEATH_DROPS.get();
        boolean duplicateModEvents = FairBossLootConfig.DUPLICATE_MOD_EVENT_DROPS.get();
        boolean duplicateEquipment = FairBossLootConfig.DUPLICATE_EQUIPMENT_DROPS.get();

        Component bossDisplayName = entity.getDisplayName();
        String bossNamePlain = bossDisplayName.getString();
        UUID primaryPlayerId = rewardedIds.getFirst();

        Collection<ItemEntity> drops = event.getDrops();
        List<ItemEntity> primaryOwnedEntities = new ArrayList<>();
        List<ItemStack> duplicableExtraStacks = new ArrayList<>();

        for (ItemEntity itemEntity : drops) {
            if (itemEntity == null || itemEntity.getItem().isEmpty()) {
                continue;
            }
            boolean isEquip = state.isEquipmentDrop(itemEntity);
            if (isEquip && !duplicateEquipment) {
                // 装备掉落默认保持公共可见且不复制，彻底堵死扔装备刷复制漏洞
                continue;
            }

            primaryOwnedEntities.add(itemEntity);

            // 非主玩家战利品表阶段的掉落（customDeathDrops / 第三方 LivingDropsEvent 新增掉落 / 允许复制的装备）整理为额外复制池
            boolean shouldCopyForOthers = false;
            if (state.isCustomDeathDrop(itemEntity)) {
                shouldCopyForOthers = duplicateCustom;
            } else if (isEquip) {
                shouldCopyForOthers = duplicateEquipment;
            } else if (!state.isTrackedBaseDrop(itemEntity)) {
                // 未被基础三阶段标记，说明是其他模组在 LivingDropsEvent 里新增的掉落物
                shouldCopyForOthers = duplicateModEvents;
            }

            if (shouldCopyForOthers && !TargetMatcher.isNonDuplicable(itemEntity.getItem())) {
                duplicableExtraStacks.add(itemEntity.getItem().copy());
            }
        }

        // 1. 处理主玩家（索引 0）的掉落物
        if (mode == DeliveryMode.ISOLATED_DROP) {
            for (ItemEntity itemEntity : primaryOwnedEntities) {
                LootDeliveryService.applyIsolatedDropSettings(itemEntity, primaryPlayerId, bossNamePlain);
            }
        } else {
            List<ItemStack> primaryStacks = new ArrayList<>(primaryOwnedEntities.size());
            for (ItemEntity itemEntity : primaryOwnedEntities) {
                primaryStacks.add(itemEntity.getItem().copy());
            }
            drops.removeAll(primaryOwnedEntities);
            LootDeliveryService.deliverLoot(level, primaryPlayerId, primaryStacks, entity.position(), bossDisplayName, mode);
        }

        // 2. 处理其余达标玩家（索引 1..N-1）：各自独立的战利品表重掷结果 + 可复制的自定义/模组事件掉落
        for (int i = 1; i < rewardedIds.size(); i++) {
            UUID playerId = rewardedIds.get(i);
            List<ItemStack> playerStacks = new ArrayList<>();

            for (ItemStack rolled : state.getExtraPlayerLootTableDrops(playerId)) {
                if (!rolled.isEmpty() && !TargetMatcher.isNonDuplicable(rolled)) {
                    playerStacks.add(rolled.copy());
                }
            }
            for (ItemStack extra : duplicableExtraStacks) {
                playerStacks.add(extra.copy());
            }

            LootDeliveryService.deliverLoot(level, playerId, playerStacks, entity.position(), bossDisplayName, mode);
        }

        // 3. 发送伤害贡献结算单
        DamageTrackerData tracker = entity.getData(ModAttachments.DAMAGE_TRACKER);
        DamageSummaryBroadcaster.broadcastIfNeeded(level, entity, tracker, settlement, mode);
    }

    /**
     * 处理常规生物与末影龙分批产生的经验掉落，按配置向每位达标玩家发放专属经验球。
     */
    public static void handleExperienceDrop(LivingExperienceDropEvent event) {
        ExperienceMode xpMode = FairBossLootConfig.EXPERIENCE_MODE.get();
        if (xpMode == ExperienceMode.VANILLA) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        int baseXp = event.getDroppedExperience();
        if (baseXp <= 0 || !TargetMatcher.shouldTrack(entity)) {
            return;
        }

        DamageTrackerData tracker = entity.getData(ModAttachments.DAMAGE_TRACKER);
        DamageTrackerData.SettlementResult settlement = tracker.getFrozenSettlement();
        if (settlement == null) {
            AttackerResolver.ResolvedAttacker killer = event.getAttackingPlayer() != null
                    ? AttackerResolver.resolveEntity(level, event.getAttackingPlayer(), 0)
                    : null;
            settlement = tracker.freezeSettlement(entity, killer, level.getGameTime());
        }
        if (!settlement.hasRewardedPlayers()) {
            return;
        }

        List<UUID> rewardedIds = settlement.rewardedPlayerIds();
        if (rewardedIds.size() == 1 && !FairBossLootConfig.PROTECT_SOLO_KILLS.get()) {
            return;
        }

        event.setCanceled(true);
        DeliveryMode deliveryMode = FairBossLootConfig.DELIVERY_MODE.get();

        if (xpMode == ExperienceMode.FULL_PER_PLAYER) {
            for (UUID playerId : rewardedIds) {
                LootDeliveryService.spawnPersonalExperience(level, entity.position(), playerId, baseXp, deliveryMode);
            }
            return;
        }

        // PROPORTIONAL：按达标玩家之间的归一化伤害占比拆分经验，每人保底 1 点
        double totalRewardedShare = 0.0D;
        for (UUID playerId : rewardedIds) {
            totalRewardedShare += settlement.shareOf(playerId);
        }
        for (UUID playerId : rewardedIds) {
            double weight = totalRewardedShare > 0.0D
                    ? settlement.shareOf(playerId) / totalRewardedShare
                    : 1.0D / rewardedIds.size();
            int shareXp = Math.max(1, (int) Math.round(baseXp * weight));
            LootDeliveryService.spawnPersonalExperience(level, entity.position(), playerId, shareXp, deliveryMode);
        }
    }
}
