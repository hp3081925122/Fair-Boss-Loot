package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.DropCaptureAccess;
import com.hp.fairbossloot.drop.DropCaptureState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 中生物穿戴装备也在 Mob#dropCustomDeathLoot 内生成；仅标记 Mob 自身装备循环生成的掉落，不干扰凋灵等子类自定义战利品。
 */
@Mixin(Mob.class)
public abstract class MobDropMixin {
    @Unique
    @Nullable
    private List<ItemEntity> fairBossLoot$mobEquipSnapshot;

    @Inject(
            method = "dropCustomDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fairBossLoot$beforeMobEquipmentLoop(DamageSource source, int looting, boolean recentlyHit, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self instanceof DropCaptureAccess access && access.fairBossLoot$getDropCaptureState() != null) {
            Collection<ItemEntity> capture = self.captureDrops();
            this.fairBossLoot$mobEquipSnapshot = capture != null ? new ArrayList<>(capture) : null;
        } else {
            this.fairBossLoot$mobEquipSnapshot = null;
        }
    }

    @Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
    private void fairBossLoot$afterMobEquipmentLoop(DamageSource source, int looting, boolean recentlyHit, CallbackInfo ci) {
        List<ItemEntity> before = this.fairBossLoot$mobEquipSnapshot;
        this.fairBossLoot$mobEquipSnapshot = null;
        if (before == null) {
            return;
        }
        Mob self = (Mob) (Object) this;
        if (!(self instanceof DropCaptureAccess access)) {
            return;
        }
        DropCaptureState state = access.fairBossLoot$getDropCaptureState();
        Collection<ItemEntity> capture = self.captureDrops();
        if (state == null || capture == null) {
            return;
        }
        for (ItemEntity itemEntity : capture) {
            if (!before.contains(itemEntity)) {
                state.markEquipmentDrop(itemEntity);
            }
        }
    }
}
