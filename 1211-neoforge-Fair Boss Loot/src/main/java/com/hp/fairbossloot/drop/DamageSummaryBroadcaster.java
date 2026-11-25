package com.hp.fairbossloot.drop;

import com.hp.fairbossloot.config.DeliveryMode;
import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.i18n.ServerLang;
import com.hp.fairbossloot.tracking.DamageTrackerData;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 击杀受追踪目标后的聊天栏伤害贡献结算播报器。
 */
public final class DamageSummaryBroadcaster {
    private DamageSummaryBroadcaster() {
    }

    /**
     * 若配置启用且尚未播报，则向本次参战的在线玩家发送个性化语言版本的伤害贡献榜单。
     */
    public static void broadcastIfNeeded(
            ServerLevel level,
            LivingEntity entity,
            DamageTrackerData tracker,
            DamageTrackerData.SettlementResult settlement,
            DeliveryMode mode
    ) {
        if (tracker.isSummaryBroadcasted() || !FairBossLootConfig.BROADCAST_DAMAGE_SUMMARY.get()) {
            return;
        }
        List<DamageTrackerData.ContributorSummary> contributors = settlement.allContributors();
        if (contributors.isEmpty()) {
            return;
        }
        if (contributors.size() <= 1 && !FairBossLootConfig.BROADCAST_SUMMARY_FOR_SOLO.get()) {
            return;
        }

        tracker.markSummaryBroadcasted();

        MinecraftServer server = level.getServer();
        Component bossName = entity.getDisplayName();
        int maxLines = Math.min(contributors.size(), FairBossLootConfig.SUMMARY_MAX_ENTRIES.get());
        String footerKey = switch (mode) {
            case ISOLATED_DROP -> "fair_boss_loot.summary.footer.isolated";
            case DIRECT_INVENTORY -> "fair_boss_loot.summary.footer.inventory";
            case HYBRID -> "fair_boss_loot.summary.footer.hybrid";
        };

        for (DamageTrackerData.ContributorSummary recipientSummary : contributors) {
            ServerPlayer player = server.getPlayerList().getPlayer(recipientSummary.playerId());
            if (player == null) {
                continue;
            }
            player.sendSystemMessage(ServerLang.format(player, "fair_boss_loot.summary.header", bossName));
            for (int i = 0; i < maxLines; i++) {
                DamageTrackerData.ContributorSummary entry = contributors.get(i);
                String lineKey = entry.rewarded()
                        ? "fair_boss_loot.summary.entry.rewarded"
                        : "fair_boss_loot.summary.entry.not_rewarded";
                String dmgStr = formatDecimal(entry.damage());
                String pctStr = formatDecimal(entry.sharePercent());
                player.sendSystemMessage(ServerLang.format(
                        player,
                        lineKey,
                        i + 1,
                        entry.name(),
                        dmgStr,
                        pctStr
                ));
            }
            if (recipientSummary.rewarded()) {
                player.sendSystemMessage(ServerLang.format(player, footerKey));
            }
        }
    }

    public static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
