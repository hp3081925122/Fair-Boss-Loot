package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.DropCaptureAccess;
import com.hp.fairbossloot.drop.DropCaptureState;
import com.hp.fairbossloot.drop.LootDistributionEngine;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2 切分 LivingEntity 死亡掉落的战利品表、自定义死亡掉落与装备掉落三个阶段，并为每位达标玩家独立重掷战利品表。
 */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityDropMixin implements DropCaptureAccess {
    @Shadow
    @Nullable
    protected EntityReference<Player> lastHurtByPlayer;

    @Shadow
    protected int lastHurtByPlayerMemoryTime;

    @Unique
    @Nullable
    private DropCaptureState fairBossLoot$captureState;

    @Unique
    private long fairBossLoot$seedSalt = 0L;

    @Override
    @Nullable
    public DropCaptureState fairBossLoot$getDropCaptureState() {
        return this.fairBossLoot$captureState;
    }

    @Override
    public void fairBossLoot$setDropCaptureState(@Nullable DropCaptureState state) {
        this.fairBossLoot$captureState = state;
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void fairBossLoot$beginCapture(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        LootDistributionEngine.beginDeathDropCapture(self, level, damageSource);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void fairBossLoot$endCapture(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.fairBossLoot$seedSalt = 0L;
        LootDistributionEngine.endDeathDropCapture(self);
    }

    @WrapOperation(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V"
            )
    )
    private void fairBossLoot$wrapLootTableDrops(
            LivingEntity instance,
            ServerLevel level,
            DamageSource damageSource,
            boolean hitByPlayer,
            Operation<Void> original
    ) {
        DropCaptureState state = this.fairBossLoot$captureState;
        if (state == null) {
            original.call(instance, level, damageSource, hitByPlayer);
            return;
        }

        List<UUID> rewardedIds = state.settlement().rewardedPlayerIds();
        if (rewardedIds.isEmpty()) {
            original.call(instance, level, damageSource, hitByPlayer);
            return;
        }

        Collection<ItemEntity> mainCapture = instance.captureDrops();
        EntityReference<Player> savedLastPlayer = this.lastHurtByPlayer;
        int savedLastTime = this.lastHurtByPlayerMemoryTime;

        try {
            UUID primaryId = rewardedIds.getFirst();
            ServerPlayer primaryPlayer = level.getServer().getPlayerList().getPlayer(primaryId);
            List<ItemEntity> snapshotBefore = mainCapture != null ? new ArrayList<>(mainCapture) : List.of();

            if (primaryPlayer != null) {
                this.lastHurtByPlayer = EntityReference.of(primaryPlayer);
                this.lastHurtByPlayerMemoryTime = Math.max(savedLastTime, 100);
                DamageSource primarySource = level.damageSources().playerAttack(primaryPlayer);
                original.call(instance, level, primarySource, true);
            } else {
                original.call(instance, level, damageSource, hitByPlayer);
            }

            if (mainCapture != null) {
                for (ItemEntity itemEntity : mainCapture) {
                    if (!snapshotBefore.contains(itemEntity)) {
                        state.markPrimaryLootTableDrop(itemEntity);
                    }
                }
            }

            for (int i = 1; i < rewardedIds.size(); i++) {
                UUID playerId = rewardedIds.get(i);
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                List<ItemEntity> tempBuffer = new ArrayList<>();
                instance.captureDrops(tempBuffer);
                this.fairBossLoot$seedSalt = playerId.getMostSignificantBits() ^ (playerId.getLeastSignificantBits() * 31L);
                try {
                    if (player != null) {
                        this.lastHurtByPlayer = EntityReference.of(player);
                        this.lastHurtByPlayerMemoryTime = Math.max(savedLastTime, 100);
                        DamageSource playerSource = level.damageSources().playerAttack(player);
                        original.call(instance, level, playerSource, true);
                    } else {
                        this.lastHurtByPlayer = savedLastPlayer;
                        this.lastHurtByPlayerMemoryTime = savedLastTime;
                        original.call(instance, level, damageSource, hitByPlayer);
                    }
                } finally {
                    this.fairBossLoot$seedSalt = 0L;
                    instance.captureDrops(mainCapture);
                }

                List<ItemStack> rolledStacks = new ArrayList<>(tempBuffer.size());
                for (ItemEntity itemEntity : tempBuffer) {
                    if (!itemEntity.getItem().isEmpty()) {
                        rolledStacks.add(itemEntity.getItem().copy());
                    }
                }
                state.addExtraPlayerLootTableDrops(playerId, rolledStacks);
            }
        } finally {
            this.fairBossLoot$seedSalt = 0L;
            this.lastHurtByPlayer = savedLastPlayer;
            this.lastHurtByPlayerMemoryTime = savedLastTime;
            instance.captureDrops(mainCapture);
        }
    }

    @WrapOperation(
            method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getLootTableSeed()J"
            )
    )
    private long fairBossLoot$perturbLootSeedPerPlayer(LivingEntity instance, Operation<Long> original) {
        long seed = original.call(instance);
        if (seed != 0L && this.fairBossLoot$seedSalt != 0L) {
            return seed ^ this.fairBossLoot$seedSalt;
        }
        return seed;
    }

    @WrapOperation(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V"
            )
    )
    private void fairBossLoot$wrapCustomDeathLoot(
            LivingEntity instance,
            ServerLevel level,
            DamageSource damageSource,
            boolean hitByPlayer,
            Operation<Void> original
    ) {
        DropCaptureState state = this.fairBossLoot$captureState;
        Collection<ItemEntity> mainCapture = instance.captureDrops();
        if (state == null || mainCapture == null) {
            original.call(instance, level, damageSource, hitByPlayer);
            return;
        }
        List<ItemEntity> before = new ArrayList<>(mainCapture);
        original.call(instance, level, damageSource, hitByPlayer);
        for (ItemEntity itemEntity : mainCapture) {
            if (!before.contains(itemEntity) && !state.isEquipmentDrop(itemEntity)) {
                state.markCustomDeathDrop(itemEntity);
            }
        }
    }

    @WrapOperation(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V"
            )
    )
    private void fairBossLoot$wrapEquipmentDrops(LivingEntity instance, ServerLevel level, Operation<Void> original) {
        DropCaptureState state = this.fairBossLoot$captureState;
        Collection<ItemEntity> mainCapture = instance.captureDrops();
        if (state == null || mainCapture == null) {
            original.call(instance, level);
            return;
        }
        List<ItemEntity> before = new ArrayList<>(mainCapture);
        original.call(instance, level);
        for (ItemEntity itemEntity : mainCapture) {
            if (!before.contains(itemEntity)) {
                state.markEquipmentDrop(itemEntity);
            }
        }
    }
}
