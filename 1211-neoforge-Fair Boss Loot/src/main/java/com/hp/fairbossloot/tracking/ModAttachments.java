package com.hp.fairbossloot.tracking;

import com.hp.fairbossloot.FairBossLoot;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 服务端专用的实体数据附件注册（NeoForge ATTACHMENT_TYPES 默认不同步客户端，兼容未装模组的客户端）。
 */
public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FairBossLoot.MOD_ID);

    public static final Supplier<AttachmentType<DamageTrackerData>> DAMAGE_TRACKER = ATTACHMENTS.register(
            "damage_tracker",
            () -> AttachmentType.builder(DamageTrackerData::new)
                    .serialize(DamageTrackerData.CODEC, data -> !data.isEmpty())
                    .build()
    );

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
