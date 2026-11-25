package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.drop.IsolatedDropAccess;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止漏斗吸走个人隔离掉落物（1.20.1 Forge）。
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void fairBossLoot$blockIsolatedPickup(
            Container destination,
            ItemEntity itemEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (FairBossLootConfig.BLOCK_HOPPER_PICKUP.get()
                && itemEntity instanceof IsolatedDropAccess access
                && access.fairBossLoot$isIsolated()) {
            cir.setReturnValue(false);
        }
    }
}
