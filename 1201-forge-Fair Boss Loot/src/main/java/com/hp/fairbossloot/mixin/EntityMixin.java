package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.IsolatedDropAccess;
import com.hp.fairbossloot.drop.PersonalXpAccess;
import com.hp.fairbossloot.storage.LootDeliveryService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 纯服务端实体可见性过滤与虚空救援（1.20.1 Forge）。
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void fairBossLoot$filterVisibility(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IsolatedDropAccess isolated && isolated.fairBossLoot$isIsolated()) {
            UUID ownerId = isolated.fairBossLoot$getOwnerId();
            if (ownerId != null && !ownerId.equals(player.getUUID())) {
                cir.setReturnValue(false);
            }
            return;
        }
        if ((Object) this instanceof PersonalXpAccess xpAccess) {
            UUID ownerId = xpAccess.fairBossLoot$getOwnerId();
            if (ownerId != null && !ownerId.equals(player.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void fairBossLoot$rescueVoidDrop(CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity itemEntity && LootDeliveryService.rescueVoidItem(itemEntity)) {
            ci.cancel();
        }
    }
}
