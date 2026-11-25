package com.hp.fairbossloot.config;

/**
 * 最后一击击杀者的奖励资格兜底策略。
 */
public enum KillerPolicy {
    /**
     * 击杀者无论伤害是否达标，始终获得一份奖励。
     */
    ALWAYS,

    /**
     * 击杀者未达标时，仅在未达到最大人数上限时补位获得奖励。
     */
    FILL,

    /**
     * 击杀者同样必须严格满足伤害门槛与时间窗口条件。
     */
    REQUIREMENTS
}
