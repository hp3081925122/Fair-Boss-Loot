package com.hp.fairbossloot.tracking;

import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.config.KillerPolicy;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 挂载在生物上的伤害贡献追踪数据（支持服务端持久化保存）。
 */
public final class DamageTrackerData {
    public static final Codec<DamageTrackerData> CODEC = DamageContribution.CODEC.listOf().xmap(
            list -> {
                DamageTrackerData data = new DamageTrackerData();
                for (DamageContribution entry : list) {
                    data.contributions.put(entry.playerId(), entry);
                }
                return data;
            },
            data -> new ArrayList<>(data.contributions.values())
    );

    private final Map<UUID, DamageContribution> contributions = new HashMap<>();

    // 以下为单次受击/死亡周期内的瞬态字段，不写入存档
    private float preDamageHealth = -1.0F;
    private float preDamageAbsorption = 0.0F;
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
            return this.rewardedPlayerIds.isEmpty() ? null : this.rewardedPlayerIds.getFirst();
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

    public boolean isEmpty() {
        return this.contributions.isEmpty();
    }

    public void clear() {
        this.contributions.clear();
        this.frozenSettlement = null;
        this.summaryBroadcasted = false;
    }

    /**
     * 在 LivingDamageEvent.Pre 记录受击前血量与吸收护盾，用于精确截断溢出伤害。
     */
    public void capturePreDamageState(LivingEntity entity) {
        this.preDamageHealth = entity.getHealth();
        this.preDamageAbsorption = entity.getAbsorptionAmount();
    }

    /**
     * 计算本次受击实际生效的伤害（含打破的吸收护盾，截断溢出部分），并重置受击前快照。
     */
    public double consumeEffectiveDamage(LivingEntity entity, float rawPostDamage, float absorbedDamage) {
        double effective;
        if (this.preDamageHealth >= 0.0F) {
            float healthLost = Math.max(0.0F, this.preDamageHealth - entity.getHealth());
            float absorptionLost = Math.max(0.0F, this.preDamageAbsorption - entity.getAbsorptionAmount());
            float actualAbsorbed = Math.max(absorbedDamage, absorptionLost);
            effective = Math.min(rawPostDamage, healthLost) + actualAbsorbed;
            if (effective <= 0.0D && rawPostDamage > 0.0F && healthLost > 0.0F) {
                effective = healthLost;
            }
        } else {
            effective = Math.min(rawPostDamage, entity.getMaxHealth());
        }
        this.preDamageHealth = -1.0F;
        this.preDamageAbsorption = 0.0F;
        return Math.max(0.0D, effective);
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

        // 若击杀者不在记录中（例如 0 伤害触发机制击杀）且策略允许兜底，也补入展示列表
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

        // 先按伤害降序筛选满足伤害门槛与时间窗口的玩家
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

        // 再处理最后一击击杀者策略
        if (killerId != null && !rewardedSet.contains(killerId)) {
            if (killerPolicy == KillerPolicy.ALWAYS) {
                if (maxPlayers > 0 && qualifiedOrdered.size() >= maxPlayers) {
                    UUID removed = qualifiedOrdered.removeLast();
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

        // 将击杀者（若获奖）置于主玩家位（索引 0），保证主掉落事件上下文与原版最一致
        if (killerId != null && rewardedSet.contains(killerId)) {
            qualifiedOrdered.remove(killerId);
            qualifiedOrdered.addFirst(killerId);
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
