package com.hp.fairbossloot;

import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.event.FairBossLootEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkConstants;

/**
 * Fair Boss Loot 1.20.1 Forge 模组主入口：声明纯服务端握手兼容，注册配置与游戏事件监听。
 */
@Mod(FairBossLoot.MOD_ID)
public final class FairBossLoot {
    public static final String MOD_ID = "fair_boss_loot";

    public FairBossLoot() {
        // 允许未安装本模组的原版/Forge 客户端直接进入安装了本模组的服务端
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true)
        );
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FairBossLootConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(FairBossLootEvents.class);
    }
}
