package com.hp.fairbossloot.tracking;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 单个玩家对目标生物造成的伤害贡献记录（1.20.1 NBT 持久化）。
 */
public record DamageContribution(
        UUID playerId,
        String lastKnownName,
        double damage,
        long lastHitGameTime
) {
    private static final String TAG_PLAYER_ID = "player_id";
    private static final String TAG_NAME = "name";
    private static final String TAG_DAMAGE = "damage";
    private static final String TAG_LAST_HIT = "last_hit";

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

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_PLAYER_ID, this.playerId);
        tag.putString(TAG_NAME, this.lastKnownName);
        tag.putDouble(TAG_DAMAGE, this.damage);
        tag.putLong(TAG_LAST_HIT, this.lastHitGameTime);
        return tag;
    }

    @Nullable
    public static DamageContribution fromTag(CompoundTag tag) {
        if (!tag.hasUUID(TAG_PLAYER_ID)) {
            return null;
        }
        return new DamageContribution(
                tag.getUUID(TAG_PLAYER_ID),
                tag.getString(TAG_NAME),
                tag.getDouble(TAG_DAMAGE),
                tag.getLong(TAG_LAST_HIT)
        );
    }
}
