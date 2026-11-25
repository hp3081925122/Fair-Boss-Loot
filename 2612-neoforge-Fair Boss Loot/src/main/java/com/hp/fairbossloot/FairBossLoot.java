package com.hp.fairbossloot;

import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.event.FairBossLootEvents;
import com.hp.fairbossloot.tracking.ModAttachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fair Boss Loot 模组主入口：注册配置、实体附件与游戏事件总线监听。
 */
@Mod(FairBossLoot.MOD_ID)
public final class FairBossLoot {
    public static final String MOD_ID = "fair_boss_loot";

    public FairBossLoot(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, FairBossLootConfig.SPEC);
        ModAttachments.register(modEventBus);
        NeoForge.EVENT_BUS.register(FairBossLootEvents.class);
    }
}
