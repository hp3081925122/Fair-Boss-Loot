package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.PersonalXpAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 持久化专属经验球的归属玩家 UUID，并隔离不同归属经验球之间的合并。
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin implements PersonalXpAccess {
    @Unique
    private static final String TAG_XP_OWNER = "fair_boss_loot_xp_owner";

    @Unique
    @Nullable
    private UUID fairBossLoot$ownerId;

    @Override
    @Nullable
    public UUID fairBossLoot$getOwnerId() {
        return this.fairBossLoot$ownerId;
    }

    @Override
    public void fairBossLoot$setOwnerId(@Nullable UUID ownerId) {
        this.fairBossLoot$ownerId = ownerId;
    }

    @Inject(
            method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fairBossLoot$preventCrossOwnerMerge(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
        UUID otherOwner = other instanceof PersonalXpAccess access ? access.fairBossLoot$getOwnerId() : null;
        if (!Objects.equals(this.fairBossLoot$ownerId, otherOwner)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(
            method = "tryMergeToExisting",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private static List<ExperienceOrb> fairBossLoot$skipOwnedOrbsOnPublicAward(
            ServerLevel level,
            EntityTypeTest<Entity, ExperienceOrb> typeTest,
            AABB area,
            Predicate<? super ExperienceOrb> predicate,
            Operation<List<ExperienceOrb>> original
    ) {
        Predicate<ExperienceOrb> filtered = orb -> {
            if (orb instanceof PersonalXpAccess access && access.fairBossLoot$getOwnerId() != null) {
                return false;
            }
            return predicate.test(orb);
        };
        return original.call(level, typeTest, area, filtered);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void fairBossLoot$saveOwner(CompoundTag tag, CallbackInfo ci) {
        if (this.fairBossLoot$ownerId != null) {
            tag.putUUID(TAG_XP_OWNER, this.fairBossLoot$ownerId);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void fairBossLoot$loadOwner(CompoundTag tag, CallbackInfo ci) {
        if (tag.hasUUID(TAG_XP_OWNER)) {
            this.fairBossLoot$ownerId = tag.getUUID(TAG_XP_OWNER);
        }
    }
}
