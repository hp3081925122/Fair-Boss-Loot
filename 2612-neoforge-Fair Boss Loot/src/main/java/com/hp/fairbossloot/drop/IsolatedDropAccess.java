package com.hp.fairbossloot.drop;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * 供普通代码访问 ItemEntity 隔离与保护状态的桥接接口（严禁放进 mixin 包）。
 */
public interface IsolatedDropAccess {
    boolean fairBossLoot$isIsolated();

    @Nullable
    UUID fairBossLoot$getOwnerId();

    boolean fairBossLoot$isEnvironmentProtected();

    boolean fairBossLoot$isVoidRescueEnabled();

    String fairBossLoot$getSourceName();

    void fairBossLoot$configureIsolated(
            UUID ownerId,
            boolean environmentProtected,
            boolean voidRescue,
            String sourceName
    );
}
