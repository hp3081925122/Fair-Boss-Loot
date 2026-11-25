package com.hp.fairbossloot.tracking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * 单个玩家对目标生物造成的伤害贡献记录。
 */
public record DamageContribution(
        UUID playerId,
        String lastKnownName,
        double damage,
        long lastHitGameTime
) {
    public static final Codec<DamageContribution> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player_id").forGetter(DamageContribution::playerId),
            Codec.STRING.fieldOf("name").forGetter(DamageContribution::lastKnownName),
            Codec.DOUBLE.fieldOf("damage").forGetter(DamageContribution::damage),
            Codec.LONG.fieldOf("last_hit").forGetter(DamageContribution::lastHitGameTime)
    ).apply(instance, DamageContribution::new));

    /**
     * 累加新伤害并刷新最近名称与命中时间。
     */
    public DamageContribution add(String name, double addedDamage, long gameTime) {
        String resolvedName = (name != null && !name.isBlank()) ? name : this.lastKnownName;
        return new DamageContribution(
                this.playerId,
                resolvedName,
                this.damage + addedDamage,
                Math.max(this.lastHitGameTime, gameTime)
        );
    }
}
