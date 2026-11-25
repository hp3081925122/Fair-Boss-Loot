package com.hp.fairbossloot.command;

import com.hp.fairbossloot.drop.DamageSummaryBroadcaster;
import com.hp.fairbossloot.i18n.ServerLang;
import com.hp.fairbossloot.storage.LootDeliveryService;
import com.hp.fairbossloot.storage.PendingLootVault;
import com.hp.fairbossloot.tracking.DamageContribution;
import com.hp.fairbossloot.tracking.DamageTrackerData;
import com.hp.fairbossloot.tracking.ModAttachments;
import com.hp.fairbossloot.tracking.TargetMatcher;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 玩家待领战利品与管理员伤害追踪排查指令：/fairbossloot（短别名 /fbl）。
 */
public final class FairBossLootCommands {
    private static final int LIST_PREVIEW_LIMIT = 20;

    private FairBossLootCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot("fairbossloot"));
        dispatcher.register(buildRoot("fbl"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String literal) {
        return Commands.literal(literal)
                .then(Commands.literal("claim")
                        .executes(ctx -> executeClaim(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> executeList(ctx.getSource())))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeClear(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("inspect")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> executeInspect(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))));
    }

    private static int executeClaim(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PendingLootVault vault = PendingLootVault.get(player.server);
        int before = vault.getPendingCount(player.getUUID());
        if (before <= 0) {
            source.sendSuccess(() -> ServerLang.format(player, "fair_boss_loot.command.claim.empty"), false);
            return 0;
        }

        int claimed = vault.claimIntoInventory(player.getUUID(), stack -> LootDeliveryService.insertIntoInventorySafely(player, stack));
        int remaining = vault.getPendingCount(player.getUUID());

        if (claimed <= 0) {
            source.sendFailure(ServerLang.format(player, "fair_boss_loot.command.claim.inventory_full", remaining));
            return 0;
        }
        source.sendSuccess(() -> ServerLang.format(player, "fair_boss_loot.command.claim.success", claimed, remaining), false);
        return claimed;
    }

    private static int executeList(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PendingLootVault vault = PendingLootVault.get(player.server);
        List<PendingLootVault.PendingLootEntry> entries = vault.getEntries(player.getUUID());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> ServerLang.format(player, "fair_boss_loot.command.claim.empty"), false);
            return 0;
        }

        source.sendSuccess(() -> ServerLang.format(player, "fair_boss_loot.command.list.header", entries.size()), false);
        int shown = Math.min(entries.size(), LIST_PREVIEW_LIMIT);
        for (int i = 0; i < shown; i++) {
            PendingLootVault.PendingLootEntry entry = entries.get(i);
            source.sendSuccess(() -> ServerLang.format(
                    player,
                    "fair_boss_loot.command.list.entry",
                    entry.stack().getDisplayName(),
                    entry.stack().getCount(),
                    entry.sourceName()
            ), false);
        }
        if (entries.size() > shown) {
            int hidden = entries.size() - shown;
            source.sendSuccess(() -> ServerLang.format(player, "fair_boss_loot.command.list.more", hidden), false);
        }
        return entries.size();
    }

    private static int executeClear(CommandSourceStack source, ServerPlayer target) {
        PendingLootVault vault = PendingLootVault.get(source.getServer());
        int removed = vault.clear(target.getUUID());
        source.sendSuccess(() -> ServerLang.format(
                source,
                "fair_boss_loot.command.clear.success",
                target.getGameProfile().getName(),
                removed
        ), true);
        return removed;
    }

    private static int executeInspect(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(ServerLang.format(source, "fair_boss_loot.command.inspect.not_living"));
            return 0;
        }
        boolean tracked = TargetMatcher.shouldTrack(living);
        String trackedText = ServerLang.raw(source, tracked ? "fair_boss_loot.command.bool.yes" : "fair_boss_loot.command.bool.no");

        if (!living.hasData(ModAttachments.DAMAGE_TRACKER)) {
            source.sendSuccess(() -> ServerLang.format(
                    source,
                    "fair_boss_loot.command.inspect.empty",
                    living.getDisplayName(),
                    trackedText
            ), false);
            return 0;
        }

        DamageTrackerData tracker = living.getData(ModAttachments.DAMAGE_TRACKER);
        List<DamageContribution> sorted = tracker.sortedContributions();
        if (sorted.isEmpty()) {
            source.sendSuccess(() -> ServerLang.format(
                    source,
                    "fair_boss_loot.command.inspect.empty",
                    living.getDisplayName(),
                    trackedText
            ), false);
            return 0;
        }

        double total = 0.0D;
        for (DamageContribution c : sorted) {
            total += c.damage();
        }
        long now = living.level() instanceof ServerLevel serverLevel ? serverLevel.getGameTime() : 0L;

        source.sendSuccess(() -> ServerLang.format(
                source,
                "fair_boss_loot.command.inspect.header",
                living.getDisplayName(),
                trackedText,
                DamageSummaryBroadcaster.formatDecimal(living.getHealth()),
                DamageSummaryBroadcaster.formatDecimal(living.getMaxHealth())
        ), false);

        double finalTotal = total;
        for (DamageContribution entry : sorted) {
            double pct = finalTotal > 0.0D ? (entry.damage() / finalTotal) * 100.0D : 0.0D;
            long secondsAgo = Math.max(0L, (now - entry.lastHitGameTime()) / 20L);
            source.sendSuccess(() -> ServerLang.format(
                    source,
                    "fair_boss_loot.command.inspect.entry",
                    entry.lastKnownName(),
                    DamageSummaryBroadcaster.formatDecimal(entry.damage()),
                    DamageSummaryBroadcaster.formatDecimal(pct),
                    secondsAgo
            ), false);
        }
        return sorted.size();
    }
}
