package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.DropCaptureAccess;
import com.hp.fairbossloot.drop.DropCaptureState;
import com.hp.fairbossloot.drop.LootDistributionEngine;
import com.hp.fairbossloot.tracking.DamageTrackerAccess;
import com.hp.fairbossloot.tracking.DamageTrackerData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 Forge：持久化生物伤害追踪数据，并切分战利品表、自定义死亡掉落与装备掉落阶段，按人独立重掷战利品表。
 */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityDropMixin implements DropCaptureAccess, DamageTrackerAccess {
    @Unique
    private static final String TAG_DAMAGE_TRACKER = "fair_boss_loot_damage_tracker";

    @Shadow
    @Nullable
    protected Player lastHurtByPlayer;

    @Shadow
    protected int lastHurtByPlayerTime;

    @Shadow
    protected abstract void dropFromLootTable(DamageSource damageSource, boolean hitByPlayer);

    @Unique
    @Nullable
    private DamageTrackerData fairBossLoot$damageTracker;

    @Unique
    @Nullable
    private DropCaptureState fairBossLoot$captureState;

    @Unique
    private long fairBossLoot$seedSalt = 0L;

    @Unique
    @Nullable
    private List<ItemEntity> fairBossLoot$phaseSnapshot;

    @Override
    public DamageTrackerData fairBossLoot$getDamageTracker() {
        if (this.fairBossLoot$damageTracker == null) {
            this.fairBossLoot$damageTracker = new DamageTrackerData();
        }
        return this.fairBossLoot$damageTracker;
    }

    @Override
    public boolean fairBossLoot$hasDamageTracker() {
        return this.fairBossLoot$damageTracker != null && !this.fairBossLoot$damageTracker.isEmpty();
    }

    @Override
    @Nullable
    public DropCaptureState fairBossLoot$getDropCaptureState() {
        return this.fairBossLoot$captureState;
    }

    @Override
    public void fairBossLoot$setDropCaptureState(@Nullable DropCaptureState state) {
        this.fairBossLoot$captureState = state;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void fairBossLoot$saveDamageTracker(CompoundTag tag, CallbackInfo ci) {
        if (this.fairBossLoot$damageTracker != null && !this.fairBossLoot$damageTracker.isEmpty()) {
            tag.put(TAG_DAMAGE_TRACKER, this.fairBossLoot$damageTracker.saveToListTag());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void fairBossLoot$loadDamageTracker(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(TAG_DAMAGE_TRACKER, Tag.TAG_LIST)) {
            this.fairBossLoot$getDamageTracker().loadFromListTag(tag.getList(TAG_DAMAGE_TRACKER, Tag.TAG_COMPOUND));
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void fairBossLoot$beginCapture(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            LootDistributionEngine.beginDeathDropCapture(self, level, damageSource);
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void fairBossLoot$endCapture(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.fairBossLoot$seedSalt = 0L;
        this.fairBossLoot$phaseSnapshot = null;
        LootDistributionEngine.endDeathDropCapture(self);
    }

    @Redirect(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V"
            )
    )
    private void fairBossLoot$redirectLootTableDrops(LivingEntity instance, DamageSource damageSource, boolean hitByPlayer) {
        DropCaptureState state = this.fairBossLoot$captureState;
        if (state == null || !(instance.level() instanceof ServerLevel level)) {
            this.dropFromLootTable(damageSource, hitByPlayer);
            return;
        }

        List<UUID> rewardedIds = state.settlement().rewardedPlayerIds();
        if (rewardedIds.isEmpty()) {
            this.dropFromLootTable(damageSource, hitByPlayer);
            return;
        }

        Collection<ItemEntity> mainCapture = instance.captureDrops();
        Player savedLastPlayer = this.lastHurtByPlayer;
        int savedLastTime = this.lastHurtByPlayerTime;

        try {
            // 主玩家（索引 0）写入主捕获列表，完整保留后续 LivingDropsEvent 兼容链路
            UUID primaryId = rewardedIds.get(0);
            ServerPlayer primaryPlayer = level.getServer().getPlayerList().getPlayer(primaryId);
            List<ItemEntity> snapshotBefore = mainCapture != null ? new ArrayList<>(mainCapture) : List.of();

            if (primaryPlayer != null) {
                this.lastHurtByPlayer = primaryPlayer;
                this.lastHurtByPlayerTime = Math.max(savedLastTime, 100);
                DamageSource primarySource = level.damageSources().playerAttack(primaryPlayer);
                this.dropFromLootTable(primarySource, true);
            } else {
                this.dropFromLootTable(damageSource, hitByPlayer);
            }

            if (mainCapture != null) {
                for (ItemEntity itemEntity : mainCapture) {
                    if (!snapshotBefore.contains(itemEntity)) {
                        state.markPrimaryLootTableDrop(itemEntity);
                    }
                }
            }

            // 为其余达标玩家各自独立重掷战利品表（自动应用各自抢夺附魔与幸运值）
            for (int i = 1; i < rewardedIds.size(); i++) {
                UUID playerId = rewardedIds.get(i);
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                List<ItemEntity> tempBuffer = new ArrayList<>();
                instance.captureDrops(tempBuffer);
                this.fairBossLoot$seedSalt = playerId.getMostSignificantBits() ^ (playerId.getLeastSignificantBits() * 31L);
                try {
                    if (player != null) {
                        this.lastHurtByPlayer = player;
                        this.lastHurtByPlayerTime = Math.max(savedLastTime, 100);
                        DamageSource playerSource = level.damageSources().playerAttack(player);
                        this.dropFromLootTable(playerSource, true);
                    } else {
                        this.lastHurtByPlayer = savedLastPlayer;
                        this.lastHurtByPlayerTime = savedLastTime;
                        this.dropFromLootTable(damageSource, hitByPlayer);
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
            this.lastHurtByPlayerTime = savedLastTime;
            instance.captureDrops(mainCapture);
        }
    }

    @Redirect(
            method = "dropFromLootTable",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getLootTableSeed()J"
            )
    )
    private long fairBossLoot$perturbLootSeedPerPlayer(LivingEntity instance) {
        long seed = instance.getLootTableSeed();
        if (seed != 0L && this.fairBossLoot$seedSalt != 0L) {
            return seed ^ this.fairBossLoot$seedSalt;
        }
        return seed;
    }

    @Inject(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;IZ)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void fairBossLoot$beforeCustomDeathLoot(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Collection<ItemEntity> capture = self.captureDrops();
        this.fairBossLoot$phaseSnapshot = (this.fairBossLoot$captureState != null && capture != null)
                ? new ArrayList<>(capture)
                : null;
    }

    @Inject(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fairBossLoot$afterCustomDeathLoot(DamageSource damageSource, CallbackInfo ci) {
        DropCaptureState state = this.fairBossLoot$captureState;
        List<ItemEntity> before = this.fairBossLoot$phaseSnapshot;
        this.fairBossLoot$phaseSnapshot = null;
        LivingEntity self = (LivingEntity) (Object) this;
        Collection<ItemEntity> capture = self.captureDrops();
        if (state == null || before == null || capture == null) {
            return;
        }
        for (ItemEntity itemEntity : capture) {
            if (!before.contains(itemEntity) && !state.isEquipmentDrop(itemEntity)) {
                state.markCustomDeathDrop(itemEntity);
            }
        }
    }

    @Inject(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void fairBossLoot$beforeEquipmentDrops(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Collection<ItemEntity> capture = self.captureDrops();
        this.fairBossLoot$phaseSnapshot = (this.fairBossLoot$captureState != null && capture != null)
                ? new ArrayList<>(capture)
                : null;
    }

    @Inject(
            method = "dropAllDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment()V",
                    shift = At.Shift.AFTER
            )
    )
    private void fairBossLoot$afterEquipmentDrops(DamageSource damageSource, CallbackInfo ci) {
        DropCaptureState state = this.fairBossLoot$captureState;
        List<ItemEntity> before = this.fairBossLoot$phaseSnapshot;
        this.fairBossLoot$phaseSnapshot = null;
        LivingEntity self = (LivingEntity) (Object) this;
        Collection<ItemEntity> capture = self.captureDrops();
        if (state == null || before == null || capture == null) {
            return;
        }
        for (ItemEntity itemEntity : capture) {
            if (!before.contains(itemEntity)) {
                state.markEquipmentDrop(itemEntity);
            }
        }
    }
}
