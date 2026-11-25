package com.hp.fairbossloot.tracking;

import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.config.KillerPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 挂载在生物上的伤害贡献追踪数据（1.20.1 服务端 NBT 持久化保存）。
 */
public final class DamageTrackerData {
    private final Map<UUID, DamageContribution> contributions = new HashMap<>();

    // 以下为单次受击/死亡周期内的瞬态字段，不写入存档
    private float preHurtAbsorption = -1.0F;
    @Nullable
    private SettlementResult frozenSettlement;
    private boolean summaryBroadcasted;

    /**
     * 单个参战玩家的结算条目。
     */
    public record ContributorSummary(
            UUID playerId,
            String name,
            double damage,
            double sharePercent,
            boolean rewarded,
            boolean killer
    ) {
    }

    /**
     * 生物死亡时冻结的最终奖励结算结果，供战利品表、自定义掉落、事件掉落与经验球共享。
     */
    public record SettlementResult(
            List<ContributorSummary> allContributors,
            List<UUID> rewardedPlayerIds,
            double totalTrackedDamage,
            double referenceMaxHealth
    ) {
        public boolean hasRewardedPlayers() {
            return !this.rewardedPlayerIds.isEmpty();
        }

        @Nullable
        public UUID primaryPlayerId() {
            return this.rewardedPlayerIds.isEmpty() ? null : this.rewardedPlayerIds.get(0);
        }

        public double shareOf(UUID playerId) {
            for (ContributorSummary summary : this.allContributors) {
                if (summary.playerId().equals(playerId)) {
                    return summary.sharePercent() / 100.0D;
                }
            }
            return 0.0D;
        }
    }

    /**
     * 获取生物的伤害追踪器实例。
     */
    public static DamageTrackerData get(LivingEntity entity) {
        return ((DamageTrackerAccess) entity).fairBossLoot$getDamageTracker();
    }

    public static boolean hasData(LivingEntity entity) {
        return entity instanceof DamageTrackerAccess access && access.fairBossLoot$hasDamageTracker();
    }

    public boolean isEmpty() {
        return this.contributions.isEmpty();
    }

    public void clear() {
        this.contributions.clear();
        this.frozenSettlement = null;
        this.summaryBroadcasted = false;
    }

    public ListTag saveToListTag() {
        ListTag list = new ListTag();
        for (DamageContribution entry : this.contributions.values()) {
            list.add(entry.toTag());
        }
        return list;
    }

    public void loadFromListTag(ListTag list) {
        this.contributions.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            DamageContribution entry = DamageContribution.fromTag(entryTag);
            if (entry != null) {
                this.contributions.put(entry.playerId(), entry);
            }
        }
    }

    /**
     * 在 1.20.1 LivingHurtEvent 记录扣除吸收护盾前的护盾值，用于统计打掉的吸收护盾伤害。
     */
    public void capturePreHurtAbsorption(LivingEntity entity) {
        this.preHurtAbsorption = entity.getAbsorptionAmount();
    }

    /**
     * 在 1.20.1 LivingDamageEvent（扣血前、扣护盾后）计算实际生效伤害并截断溢出。
     */
    public double consumeEffectiveDamage(LivingEntity entity, float damageAmount) {
        double absorbed = 0.0D;
        if (this.preHurtAbsorption >= 0.0F) {
            absorbed = Math.max(0.0F, this.preHurtAbsorption - entity.getAbsorptionAmount());
        }
        this.preHurtAbsorption = -1.0F;
        double healthDamage = Math.min(Math.max(0.0F, damageAmount), Math.max(0.0F, entity.getHealth()));
        return Math.max(0.0D, healthDamage + absorbed);
    }

    /**
     * 累加玩家有效伤害。
     */
    public void recordDamage(UUID playerId, String playerName, double effectiveDamage, long gameTime) {
        if (effectiveDamage <= 0.0D || this.frozenSettlement != null) {
            return;
        }
        this.contributions.compute(playerId, (id, existing) ->
                existing == null
                        ? new DamageContribution(id, playerName, effectiveDamage, gameTime)
                        : existing.add(playerName, effectiveDamage, gameTime));
    }

    /**
     * 返回按伤害降序排列的当前伤害记录副本。
     */
    public List<DamageContribution> sortedContributions() {
        List<DamageContribution> list = new ArrayList<>(this.contributions.values());
        list.sort(Comparator.comparingDouble(DamageContribution::damage).reversed());
        return list;
    }

    /**
     * 获取最近一次被玩家命中的游戏刻，无记录时返回 -1。
     */
    public long getLastHitGameTime() {
        long latest = -1L;
        for (DamageContribution entry : this.contributions.values()) {
            if (entry.lastHitGameTime() > latest) {
                latest = entry.lastHitGameTime();
            }
        }
        return latest;
    }

    @Nullable
    public SettlementResult getFrozenSettlement() {
        return this.frozenSettlement;
    }

    public boolean isSummaryBroadcasted() {
        return this.summaryBroadcasted;
    }

    public void markSummaryBroadcasted() {
        this.summaryBroadcasted = true;
    }

    /**
     * 冻结并计算本次击杀的最终获奖名单。同一实体生命周期内仅计算一次。
     */
    public SettlementResult freezeSettlement(LivingEntity entity, @Nullable AttackerResolver.ResolvedAttacker killer, long currentGameTime) {
        if (this.frozenSettlement != null) {
            return this.frozenSettlement;
        }

        double maxHealth = Math.max(1.0D, entity.getMaxHealth());
        double minDamageCfg = FairBossLootConfig.MIN_DAMAGE.get();
        double minRequiredDamage = FairBossLootConfig.MIN_DAMAGE_IS_PERCENTAGE.get()
                ? maxHealth * minDamageCfg
                : minDamageCfg;
        int windowSeconds = FairBossLootConfig.DAMAGE_WINDOW_SECONDS.get();
        long windowTicks = windowSeconds <= 0 ? Long.MAX_VALUE : windowSeconds * 20L;
        int maxPlayers = FairBossLootConfig.MAX_REWARDED_PLAYERS.get();
        KillerPolicy killerPolicy = FairBossLootConfig.KILLER_POLICY.get();

        List<DamageContribution> sorted = this.sortedContributions();
        UUID killerId = killer != null ? killer.playerId() : null;

        if (killerId != null && !this.contributions.containsKey(killerId) && killerPolicy != KillerPolicy.REQUIREMENTS) {
            sorted = new ArrayList<>(sorted);
            sorted.add(new DamageContribution(killerId, killer.displayName(), 0.0D, currentGameTime));
        }

        double totalDamage = 0.0D;
        for (DamageContribution entry : sorted) {
            totalDamage += entry.damage();
        }

        List<UUID> qualifiedOrdered = new ArrayList<>();
        Set<UUID> rewardedSet = new HashSet<>();

        for (DamageContribution entry : sorted) {
            boolean withinWindow = windowTicks == Long.MAX_VALUE || (currentGameTime - entry.lastHitGameTime()) <= windowTicks;
            boolean enoughDamage = entry.damage() + 1.0E-4D >= minRequiredDamage;
            if (withinWindow && enoughDamage) {
                if (maxPlayers <= 0 || qualifiedOrdered.size() < maxPlayers) {
                    qualifiedOrdered.add(entry.playerId());
                    rewardedSet.add(entry.playerId());
                }
            }
        }

        if (killerId != null && !rewardedSet.contains(killerId)) {
            if (killerPolicy == KillerPolicy.ALWAYS) {
                if (maxPlayers > 0 && qualifiedOrdered.size() >= maxPlayers) {
                    UUID removed = qualifiedOrdered.remove(qualifiedOrdered.size() - 1);
                    rewardedSet.remove(removed);
                }
                qualifiedOrdered.add(killerId);
                rewardedSet.add(killerId);
            } else if (killerPolicy == KillerPolicy.FILL) {
                if (maxPlayers <= 0 || qualifiedOrdered.size() < maxPlayers) {
                    qualifiedOrdered.add(killerId);
                    rewardedSet.add(killerId);
                }
            }
        }

        if (killerId != null && rewardedSet.contains(killerId)) {
            qualifiedOrdered.remove(killerId);
            qualifiedOrdered.add(0, killerId);
        }

        List<ContributorSummary> summaries = new ArrayList<>(sorted.size());
        for (DamageContribution entry : sorted) {
            double share = totalDamage > 0.0D ? (entry.damage() / totalDamage) * 100.0D : 0.0D;
            summaries.add(new ContributorSummary(
                    entry.playerId(),
                    entry.lastKnownName(),
                    entry.damage(),
                    share,
                    rewardedSet.contains(entry.playerId()),
                    entry.playerId().equals(killerId)
            ));
        }

        this.frozenSettlement = new SettlementResult(
                List.copyOf(summaries),
                List.copyOf(qualifiedOrdered),
                totalDamage,
                maxHealth
        );
        return this.frozenSettlement;
    }
}
