package com.hp.fairbossloot.config;

/**
 * 个人专属战利品的发放方式。
 */
public enum DeliveryMode {
    /**
     * 地面个人隔离掉落物：仅对应玩家可见且仅其可拾取。
     */
    ISOLATED_DROP,

    /**
     * 直接发放进背包：背包已满或玩家离线时存入待领暂存库。
     */
    DIRECT_INVENTORY,

    /**
     * 混合模式：优先直接进背包，背包满则在玩家脚下生成受保护的隔离掉落物，离线时存入暂存库。
     */
    HYBRID
}
