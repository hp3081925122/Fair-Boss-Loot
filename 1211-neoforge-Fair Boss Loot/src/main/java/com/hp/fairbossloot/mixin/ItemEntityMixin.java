package com.hp.fairbossloot.mixin;

import com.hp.fairbossloot.drop.IsolatedDropAccess;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 持久化个人隔离掉落物标记，并提供火焰、岩浆、仙人掌与爆炸等环境销毁保护。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements IsolatedDropAccess {
    @Unique
    private static final String TAG_ISOLATED = "fair_boss_loot_isolated";
    @Unique
    private static final String TAG_ENV_PROTECTED = "fair_boss_loot_env_protected";
    @Unique
    private static final String TAG_VOID_RESCUE = "fair_boss_loot_void_rescue";
    @Unique
    private static final String TAG_SOURCE = "fair_boss_loot_source";

    @Shadow
    @Nullable
    private UUID target;

    @Shadow
    public abstract void setTarget(@Nullable UUID target);

    @Unique
    private boolean fairBossLoot$isolated;
    @Unique
    private boolean fairBossLoot$environmentProtected;
    @Unique
    private boolean fairBossLoot$voidRescue;
    @Unique
    private String fairBossLoot$sourceName = "";

    @Override
    public boolean fairBossLoot$isIsolated() {
        return this.fairBossLoot$isolated;
    }

    @Override
    @Nullable
    public UUID fairBossLoot$getOwnerId() {
        return this.target;
    }

    @Override
    public boolean fairBossLoot$isEnvironmentProtected() {
        return this.fairBossLoot$environmentProtected;
    }

    @Override
    public boolean fairBossLoot$isVoidRescueEnabled() {
        return this.fairBossLoot$voidRescue;
    }

    @Override
    public String fairBossLoot$getSourceName() {
        return this.fairBossLoot$sourceName;
    }

    @Override
    public void fairBossLoot$configureIsolated(
            UUID ownerId,
            boolean environmentProtected,
            boolean voidRescue,
            String sourceName
    ) {
        this.setTarget(ownerId);
        this.fairBossLoot$isolated = true;
        this.fairBossLoot$environmentProtected = environmentProtected;
        this.fairBossLoot$voidRescue = voidRescue;
        this.fairBossLoot$sourceName = sourceName == null ? "" : sourceName;
    }

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void fairBossLoot$fireImmune(CallbackInfoReturnable<Boolean> cir) {
        if (this.fairBossLoot$isolated && this.fairBossLoot$environmentProtected) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void fairBossLoot$protectFromDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.fairBossLoot$isolated && this.fairBossLoot$environmentProtected) {
            // 仍允许管理员 /kill（绕过无敌标记）清理实体，阻断火、岩浆、仙人掌、爆炸、落雷等环境销毁
            if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void fairBossLoot$saveData(CompoundTag tag, CallbackInfo ci) {
        if (this.fairBossLoot$isolated) {
            tag.putBoolean(TAG_ISOLATED, true);
            tag.putBoolean(TAG_ENV_PROTECTED, this.fairBossLoot$environmentProtected);
            tag.putBoolean(TAG_VOID_RESCUE, this.fairBossLoot$voidRescue);
            if (!this.fairBossLoot$sourceName.isEmpty()) {
                tag.putString(TAG_SOURCE, this.fairBossLoot$sourceName);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void fairBossLoot$loadData(CompoundTag tag, CallbackInfo ci) {
        if (tag.getBoolean(TAG_ISOLATED)) {
            this.fairBossLoot$isolated = true;
            this.fairBossLoot$environmentProtected = tag.getBoolean(TAG_ENV_PROTECTED);
            this.fairBossLoot$voidRescue = tag.getBoolean(TAG_VOID_RESCUE);
            this.fairBossLoot$sourceName = tag.getString(TAG_SOURCE);
        }
    }
}
