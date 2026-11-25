package com.hp.fairbossloot.tracking;

import com.hp.fairbossloot.FairBossLoot;
import com.hp.fairbossloot.config.FairBossLootConfig;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 26.1.2 目标生物与不可复制物品匹配器，同时支持数据包标签与 TOML 配置文件规则。
 */
public final class TargetMatcher {
    public static final TagKey<EntityType<?>> ELIGIBLE_ENTITIES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(FairBossLoot.MOD_ID, "eligible_entities")
    );

    public static final TagKey<EntityType<?>> EXCLUDED_ENTITIES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(FairBossLoot.MOD_ID, "excluded_entities")
    );

    public static final TagKey<Item> NON_DUPLICABLE_ITEMS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(FairBossLoot.MOD_ID, "non_duplicable")
    );

    private TargetMatcher() {
    }

    /**
     * 判断指定生物是否启用伤害追踪与独立战利品分配。
     */
    public static boolean shouldTrack(LivingEntity entity) {
        if (!FairBossLootConfig.ENABLED.get() || entity instanceof Player) {
            return false;
        }

        EntityType<?> type = entity.getType();
        if (type.builtInRegistryHolder().is(EXCLUDED_ENTITIES) || matchesEntityRules(type, FairBossLootConfig.ENTITY_BLACKLIST.get())) {
            return false;
        }

        if (FairBossLootConfig.APPLY_TO_ALL_LIVING.get()) {
            return true;
        }

        if (type.builtInRegistryHolder().is(ELIGIBLE_ENTITIES) || matchesEntityRules(type, FairBossLootConfig.ENTITY_WHITELIST.get())) {
            return true;
        }

        double autoHealthThreshold = FairBossLootConfig.AUTO_TRACK_MIN_MAX_HEALTH.get();
        return autoHealthThreshold > 0.0D && entity.getMaxHealth() >= autoHealthThreshold;
    }

    /**
     * 判断指定物品是否被禁止按人复制。
     */
    public static boolean isNonDuplicable(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (stack.is(NON_DUPLICABLE_ITEMS)) {
            return true;
        }
        return matchesItemRules(stack, FairBossLootConfig.NON_DUPLICABLE_ITEMS.get());
    }

    private static boolean matchesEntityRules(EntityType<?> type, List<? extends String> rules) {
        if (rules.isEmpty()) {
            return false;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        for (String raw : rules) {
            String rule = raw.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (rule.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(rule.substring(1));
                if (tagId != null && type.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, tagId))) {
                    return true;
                }
            } else if (rule.endsWith(":*")) {
                String namespace = rule.substring(0, rule.length() - 2);
                if (key.getNamespace().equals(namespace)) {
                    return true;
                }
            } else {
                Identifier id = Identifier.tryParse(rule);
                if (id != null && key.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesItemRules(ItemStack stack, List<? extends String> rules) {
        if (rules.isEmpty()) {
            return false;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String raw : rules) {
            String rule = raw.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (rule.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(rule.substring(1));
                if (tagId != null && stack.is(TagKey.create(Registries.ITEM, tagId))) {
                    return true;
                }
            } else if (rule.endsWith(":*")) {
                String namespace = rule.substring(0, rule.length() - 2);
                if (key.getNamespace().equals(namespace)) {
                    return true;
                }
            } else {
                Identifier id = Identifier.tryParse(rule);
                if (id != null && key.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }
}
