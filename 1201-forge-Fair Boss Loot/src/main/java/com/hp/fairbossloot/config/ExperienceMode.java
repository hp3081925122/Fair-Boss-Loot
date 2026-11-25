package com.hp.fairbossloot.config;

/**
 * 经验值分配模式。
 */
public enum ExperienceMode {
    /**
     * 每位达标玩家各自获得一份完整经验（通过专属经验球发放，原生支持经验修补）。
     */
    FULL_PER_PLAYER,

    /**
     * 按伤害占比拆分原始经验给每位达标玩家（至少 1 点）。
     */
    PROPORTIONAL,

    /**
     * 保持原版经验球行为不做处理。
     */
    VANILLA
}
