package com.hp.fairbossloot.drop;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * 供普通代码访问 ExperienceOrb 专属归属状态的桥接接口（严禁放进 mixin 包）。
 */
public interface PersonalXpAccess {
    @Nullable
    UUID fairBossLoot$getOwnerId();

    void fairBossLoot$setOwnerId(@Nullable UUID ownerId);
}
