package com.hp.fairbossloot.tracking;

/**
 * 供普通代码访问挂在 LivingEntity 上的伤害追踪数据（严禁放进 mixin 包）。
 */
public interface DamageTrackerAccess {
    DamageTrackerData fairBossLoot$getDamageTracker();

    boolean fairBossLoot$hasDamageTracker();
}
