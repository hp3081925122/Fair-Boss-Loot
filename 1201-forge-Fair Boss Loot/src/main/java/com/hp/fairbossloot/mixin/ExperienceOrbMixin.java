package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.PersonalXpAccess;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 持久化专属经验球的归属玩家 UUID，隔离跨归属合并，并锁定 1.20.1 经验球仅向归属玩家吸引。
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin implements PersonalXpAccess {
    @Unique
    private static final String TAG_XP_OWNER = "fair_boss_loot_xp_owner";
    @Unique
    private static final ThreadLocal<Boolean> FAIR_BOSS_LOOT_PUBLIC_TRY_MERGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Shadow
    @Nullable
    private Player followingPlayer;

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

    @Inject(method = "scanForEntities", at = @At("RETURN"))
    private void fairBossLoot$lockOwnerTargeting(CallbackInfo ci) {
        if (this.fairBossLoot$ownerId == null) {
            return;
        }
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            this.followingPlayer = null;
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.fairBossLoot$ownerId);
        if (owner != null && owner.serverLevel() == level && !owner.isSpectator() && owner.isAlive()
                && owner.distanceToSqr(self) <= 64.0D) {
            this.followingPlayer = owner;
        } else {
            this.followingPlayer = null;
        }
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

    @Inject(method = "tryMergeToExisting", at = @At("HEAD"))
    private static void fairBossLoot$enterPublicTryMerge(ServerLevel level, Vec3 pos, int value, CallbackInfoReturnable<Boolean> cir) {
        FAIR_BOSS_LOOT_PUBLIC_TRY_MERGE.set(Boolean.TRUE);
    }

    @Inject(method = "tryMergeToExisting", at = @At("RETURN"))
    private static void fairBossLoot$exitPublicTryMerge(ServerLevel level, Vec3 pos, int value, CallbackInfoReturnable<Boolean> cir) {
        FAIR_BOSS_LOOT_PUBLIC_TRY_MERGE.set(Boolean.FALSE);
    }

    @Inject(
            method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void fairBossLoot$skipOwnedOrbsOnPublicAward(ExperienceOrb orb, int id, int value, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(FAIR_BOSS_LOOT_PUBLIC_TRY_MERGE.get())
                && orb instanceof PersonalXpAccess access
                && access.fairBossLoot$getOwnerId() != null) {
            cir.setReturnValue(false);
        }
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
