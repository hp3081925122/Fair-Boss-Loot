package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.DropCaptureAccess;
import com.hp.fairbossloot.drop.DropCaptureState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.1 起生物穿戴装备由 Mob#dropCustomDeathLoot 掉落；精确标记这部分装备掉落，避免被误当成 Boss 自定义战利品复制。
 */
@Mixin(Mob.class)
public abstract class MobDropMixin {
    @WrapOperation(
            method = "dropCustomDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
            )
    )
    private ItemEntity fairBossLoot$markMobEquipmentDrop(Mob instance, ItemStack stack, Operation<ItemEntity> original) {
        ItemEntity spawned = original.call(instance, stack);
        if (spawned != null && instance instanceof DropCaptureAccess access) {
            DropCaptureState state = access.fairBossLoot$getDropCaptureState();
            if (state != null) {
                state.markEquipmentDrop(spawned);
            }
        }
        return spawned;
    }
}
