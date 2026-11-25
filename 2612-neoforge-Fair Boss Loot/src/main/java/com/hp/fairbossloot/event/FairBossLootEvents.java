package com.hp.fairbossloot.event;

import com.hp.fairbossloot.command.FairBossLootCommands;
import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.drop.IsolatedDropAccess;
import com.hp.fairbossloot.drop.LootDistributionEngine;
import com.hp.fairbossloot.drop.PersonalXpAccess;
import com.hp.fairbossloot.i18n.ServerLang;
import com.hp.fairbossloot.storage.LootDeliveryService;
import com.hp.fairbossloot.storage.PendingLootVault;
import com.hp.fairbossloot.tracking.AttackerResolver;
import com.hp.fairbossloot.tracking.DamageTrackerData;
import com.hp.fairbossloot.tracking.ModAttachments;
import com.hp.fairbossloot.tracking.TargetMatcher;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.TriState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.entity.XpOrbTargetingEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * 26.1.2 NeoForge 游戏事件总线监听：串联伤害追踪、掉落与经验分配、拾取隔离、登录提醒和指令注册。
 */
public final class FairBossLootEvents {
    private FairBossLootEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || !TargetMatcher.shouldTrack(entity)) {
            return;
        }
        entity.getData(ModAttachments.DAMAGE_TRACKER).capturePreDamageState(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !TargetMatcher.shouldTrack(entity)) {
            return;
        }
        DamageTrackerData tracker = entity.getData(ModAttachments.DAMAGE_TRACKER);
        float absorbed = event.getReduction(DamageContainer.Reduction.ABSORPTION);
        double effective = tracker.consumeEffectiveDamage(entity, event.getHealthDamage(), absorbed);
        if (effective <= 0.0D) {
            return;
        }

        AttackerResolver.ResolvedAttacker attacker = AttackerResolver.resolve(level, event.getSource());
        if (attacker == null) {
            return;
        }
        tracker.recordDamage(attacker.playerId(), attacker.displayName(), effective, level.getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (!FairBossLootConfig.RESET_ON_FULL_HEAL.get()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.hasData(ModAttachments.DAMAGE_TRACKER)) {
            return;
        }
        if (entity.getHealth() + event.getAmount() + 1.0E-4F < entity.getMaxHealth()) {
            return;
        }
        DamageTrackerData tracker = entity.getData(ModAttachments.DAMAGE_TRACKER);
        if (tracker.isEmpty() || tracker.getFrozenSettlement() != null) {
            return;
        }
        int windowSec = FairBossLootConfig.DAMAGE_WINDOW_SECONDS.get();
        long quietTicks = windowSec > 0 ? windowSec * 20L : 600L;
        long lastHit = tracker.getLastHitGameTime();
        if (lastHit >= 0 && (level.getGameTime() - lastHit) >= quietTicks) {
            tracker.clear();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        LootDistributionEngine.handleLivingDropsLowest(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        LootDistributionEngine.handleExperienceDrop(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        ItemEntity itemEntity = event.getItemEntity();
        if (!(itemEntity instanceof IsolatedDropAccess access) || !access.fairBossLoot$isIsolated()) {
            return;
        }
        UUID ownerId = access.fairBossLoot$getOwnerId();
        if (ownerId != null && !ownerId.equals(event.getPlayer().getUUID())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXpOrbTargeting(XpOrbTargetingEvent event) {
        ExperienceOrb orb = event.getXpOrb();
        if (!(orb instanceof PersonalXpAccess access)) {
            return;
        }
        UUID ownerId = access.fairBossLoot$getOwnerId();
        if (ownerId == null) {
            return;
        }
        if (!(orb.level() instanceof ServerLevel level)) {
            event.setFollowingPlayer(null);
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        double maxDistSqr = event.getScanDistance() * event.getScanDistance();
        if (owner != null && owner.level() == level && !owner.isSpectator() && owner.isAlive()
                && owner.distanceToSqr(orb) <= maxDistSqr) {
            event.setFollowingPlayer(owner);
        } else {
            event.setFollowingPlayer(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        ExperienceOrb orb = event.getOrb();
        if (!(orb instanceof PersonalXpAccess access)) {
            return;
        }
        UUID ownerId = access.fairBossLoot$getOwnerId();
        if (ownerId != null && !ownerId.equals(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!FairBossLootConfig.LOGIN_PENDING_REMINDER.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int pending = PendingLootVault.get(player.level().getServer()).getPendingCount(player.getUUID());
        if (pending <= 0) {
            return;
        }
        MutableComponent msg = ServerLang.format(player, "fair_boss_loot.vault.login_reminder", pending);
        msg.append(Component.literal(" ")).append(LootDeliveryService.createClaimButton(player));
        player.sendSystemMessage(msg);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FairBossLootCommands.register(event.getDispatcher());
    }
}
