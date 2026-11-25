package com.hp.fairbossloot.drop;

import org.jetbrains.annotations.Nullable;

/**
 * 供普通代码读取与清理 LivingEntity 死亡掉落捕获状态的桥接接口（严禁放进 mixin 包）。
 */
public interface DropCaptureAccess {
    @Nullable
    DropCaptureState fairBossLoot$getDropCaptureState();

    void fairBossLoot$setDropCaptureState(@Nullable DropCaptureState state);
}
