package com.hp.fairbossloot.config;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Fair Boss Loot 1.20.1 Forge 服务端/通用配置。
 */
public final class FairBossLootConfig {
    public static final ForgeConfigSpec SPEC;

    // 目标生物范围配置
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue APPLY_TO_ALL_LIVING;
    public static final ForgeConfigSpec.DoubleValue AUTO_TRACK_MIN_MAX_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;

    // 伤害资格与防白嫖配置
    public static final ForgeConfigSpec.DoubleValue MIN_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue MIN_DAMAGE_IS_PERCENTAGE;
    public static final ForgeConfigSpec.IntValue DAMAGE_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_REWARDED_PLAYERS;
    public static final ForgeConfigSpec.EnumValue<KillerPolicy> KILLER_POLICY;
    public static final ForgeConfigSpec.BooleanValue TRACK_SUMMON_AND_PET_OWNERS;
    public static final ForgeConfigSpec.BooleanValue RESET_ON_FULL_HEAL;

    // 掉落与经验分配配置
    public static final ForgeConfigSpec.EnumValue<DeliveryMode> DELIVERY_MODE;
    public static final ForgeConfigSpec.BooleanValue PROTECT_SOLO_KILLS;
    public static final ForgeConfigSpec.BooleanValue DUPLICATE_CUSTOM_DEATH_DROPS;
    public static final ForgeConfigSpec.BooleanValue DUPLICATE_MOD_EVENT_DROPS;
    public static final ForgeConfigSpec.BooleanValue DUPLICATE_EQUIPMENT_DROPS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NON_DUPLICABLE_ITEMS;
    public static final ForgeConfigSpec.EnumValue<ExperienceMode> EXPERIENCE_MODE;

    // 隔离掉落物保护配置
    public static final ForgeConfigSpec.BooleanValue PROTECT_FROM_ENVIRONMENT;
    public static final ForgeConfigSpec.BooleanValue RESCUE_FROM_VOID;
    public static final ForgeConfigSpec.BooleanValue BLOCK_HOPPER_PICKUP;
    public static final ForgeConfigSpec.BooleanValue GLOWING_ISOLATED_DROPS;
    public static final ForgeConfigSpec.IntValue ISOLATED_DROP_LIFESPAN_TICKS;

    // 暂存库与聊天播报配置
    public static final ForgeConfigSpec.IntValue MAX_PENDING_STACKS_PER_PLAYER;
    public static final ForgeConfigSpec.BooleanValue LOGIN_PENDING_REMINDER;
    public static final ForgeConfigSpec.BooleanValue BROADCAST_DAMAGE_SUMMARY;
    public static final ForgeConfigSpec.BooleanValue BROADCAST_SUMMARY_FOR_SOLO;
    public static final ForgeConfigSpec.IntValue SUMMARY_MAX_ENTRIES;
    public static final ForgeConfigSpec.ConfigValue<String> FALLBACK_LANGUAGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "目标生物筛选配置 / Target entity matching settings"
        ).push("targets");

        ENABLED = builder
                .comment("是否启用本模组功能 / Master switch for Fair Boss Loot")
                .define("enabled", true);

        APPLY_TO_ALL_LIVING = builder
                .comment("是否对所有生物（排除玩家与黑名单）生效；关闭时仅对标签 #fair_boss_loot:eligible_entities、白名单及达标高血量生物生效 / Apply to all living entities (except players & blacklist). When false, only entities in #fair_boss_loot:eligible_entities, whitelist, or meeting autoTrackMinMaxHealth are processed.")
                .define("applyToAllLiving", false);

        AUTO_TRACK_MIN_MAX_HEALTH = builder
                .comment("按最大生命值自动纳入追踪的阈值（设为 0 禁用，例如设 100 则血量 >=100 的精英怪也自动生效） / Auto-track any living entity whose max health is >= this value (0 to disable).")
                .defineInRange("autoTrackMinMaxHealth", 0.0D, 0.0D, 1000000.0D);

        ENTITY_WHITELIST = builder
                .comment("额外启用的生物 ID 或标签（支持 modid:entity_id、modid:* 或 #tag:id） / Extra entity IDs or tags to enable (supports modid:entity_id, modid:*, or #tag:id).")
                .defineListAllowEmpty("entityWhitelist", List.of(), FairBossLootConfig::isValidStringEntry);

        ENTITY_BLACKLIST = builder
                .comment("强制排除的生物 ID 或标签（优先级高于白名单与全生物开关） / Entity IDs or tags to always exclude (overrides whitelist and applyToAllLiving).")
                .defineListAllowEmpty("entityBlacklist", List.of(), FairBossLootConfig::isValidStringEntry);

        builder.pop();

        builder.comment(
                "伤害贡献统计与拿奖门槛配置 / Damage tracking and reward eligibility settings"
        ).push("eligibility");

        MIN_DAMAGE = builder
                .comment("获得战利品所需的最低伤害；当 minDamageIsPercentage=true 时按最大生命值比例计算（0.05 = 5%），否则为固定伤害值 / Minimum damage required to earn loot. Fraction of max health if minDamageIsPercentage=true (0.05 = 5%), otherwise flat damage.")
                .defineInRange("minDamage", 0.05D, 0.0D, 1000000.0D);

        MIN_DAMAGE_IS_PERCENTAGE = builder
                .comment("minDamage 是否按目标最大生命值的百分比计算 / Whether minDamage is treated as a fraction of the entity's max health.")
                .define("minDamageIsPercentage", true);

        DAMAGE_WINDOW_SECONDS = builder
                .comment("伤害贡献的有效时间窗口（秒），超过此时间未再次攻击则不计入资格；设为 0 表示整场战斗永久有效 / Time window in seconds within which a player must have damaged the entity before death (0 = no expiration).")
                .defineInRange("damageWindowSeconds", 60, 0, 86400);

        MAX_REWARDED_PLAYERS = builder
                .comment("单次击杀最多可获得独立战利品的玩家人数（按伤害从高到低排序，0 表示不限制） / Maximum number of players rewarded per kill, sorted by damage descending (0 = unlimited).")
                .defineInRange("maxRewardedPlayers", 0, 0, 256);

        KILLER_POLICY = builder
                .comment("最后一击击杀者的资格策略：ALWAYS=始终获奖，FILL=未满人数上限时补位，REQUIREMENTS=严格按伤害门槛判定 / Killer eligibility policy: ALWAYS, FILL, or REQUIREMENTS.")
                .defineEnum("killerPolicy", KillerPolicy.FILL);

        TRACK_SUMMON_AND_PET_OWNERS = builder
                .comment("是否自动将投射物、驯服宠物、召唤物（OwnableEntity 等）的伤害归属给其玩家主人 / Automatically attribute damage from projectiles, tamed pets, and OwnableEntity summons to their player owner.")
                .define("trackSummonAndPetOwners", true);

        RESET_ON_FULL_HEAL = builder
                .comment("目标生物完全脱战并恢复满血时，是否清空历史伤害记录 / Clear stored damage history if the entity heals back to full health outside recent combat.")
                .define("resetOnFullHeal", false);

        builder.pop();

        builder.comment(
                "战利品与经验分配配置 / Loot and experience distribution settings"
        ).push("distribution");

        DELIVERY_MODE = builder
                .comment("奖励发放模式：ISOLATED_DROP=地面个人隔离掉落物，DIRECT_INVENTORY=直发背包（满/离线进暂存库），HYBRID=优先进背包溢出落脚下受保护 / Delivery mode: ISOLATED_DROP, DIRECT_INVENTORY, or HYBRID.")
                .defineEnum("deliveryMode", DeliveryMode.ISOLATED_DROP);

        PROTECT_SOLO_KILLS = builder
                .comment("单人击杀时是否同样应用个人隔离/保护与选定发放模式（不会复制多份，仅防旁观者抢走或烧毁） / Apply personal isolation, protection, and delivery mode even on solo kills (never duplicates drops).")
                .define("protectSoloKills", true);

        DUPLICATE_CUSTOM_DEATH_DROPS = builder
                .comment("是否为每位达标玩家复制一份生物自定义死亡掉落（如凋灵下界之星、模组 Boss 覆写 dropCustomDeathLoot 的掉落） / Give each eligible player a copy of drops produced in dropCustomDeathLoot (e.g. Wither Nether Star and modded bosses).")
                .define("duplicateCustomDeathDrops", true);

        DUPLICATE_MOD_EVENT_DROPS = builder
                .comment("是否为每位达标玩家复制一份其他模组通过 LivingDropsEvent 追加的额外掉落 / Give each eligible player a copy of extra drops added by other mods via LivingDropsEvent.")
                .define("duplicateModEventDrops", true);

        DUPLICATE_EQUIPMENT_DROPS = builder
                .comment("是否复制生物身上穿戴/捡起的装备掉落（强烈建议保持 false 以防止扔装备给怪刷复制漏洞） / Duplicate equipment dropped via dropEquipment (strongly recommended false to prevent gear-duplication exploits).")
                .define("duplicateEquipmentDrops", false);

        NON_DUPLICABLE_ITEMS = builder
                .comment("禁止按人复制的物品 ID 或标签（支持 modid:item_id、modid:* 或 #tag:id；同时也会读取标签 #fair_boss_loot:non_duplicable） / Item IDs or tags that should never be duplicated for extra players (supports modid:item_id, modid:*, #tag:id; also respects #fair_boss_loot:non_duplicable).")
                .defineListAllowEmpty("nonDuplicableItems", List.of(), FairBossLootConfig::isValidStringEntry);

        EXPERIENCE_MODE = builder
                .comment("经验分配模式：FULL_PER_PLAYER=每人一份完整专属经验球，PROPORTIONAL=按伤害占比拆分，VANILLA=保持原版 / Experience distribution mode: FULL_PER_PLAYER, PROPORTIONAL, or VANILLA.")
                .defineEnum("experienceMode", ExperienceMode.FULL_PER_PLAYER);

        builder.pop();

        builder.comment(
                "个人隔离掉落物保护配置 / Protection settings for personal isolated drops"
        ).push("protection");

        PROTECT_FROM_ENVIRONMENT = builder
                .comment("保护个人隔离掉落物免受火焰、岩浆、仙人掌、爆炸等环境伤害销毁 / Protect personal isolated drops from fire, lava, cactus, explosions, and environmental damage.")
                .define("protectFromEnvironment", true);

        RESCUE_FROM_VOID = builder
                .comment("个人隔离掉落物落入虚空时自动救援并存入该玩家的待领暂存库 / Rescue personal isolated drops falling into the void into the owner's pending vault.")
                .define("rescueFromVoid", true);

        BLOCK_HOPPER_PICKUP = builder
                .comment("禁止漏斗吸走个人隔离掉落物 / Prevent hoppers from sucking up personal isolated drops.")
                .define("blockHopperPickup", true);

        GLOWING_ISOLATED_DROPS = builder
                .comment("是否让个人隔离掉落物发光以便寻找 / Apply glowing outline to personal isolated drops.")
                .define("glowingIsolatedDrops", true);

        ISOLATED_DROP_LIFESPAN_TICKS = builder
                .comment("个人隔离掉落物存活时间（tick，原版为 6000=5分钟，默认 12000=10分钟） / Lifespan of personal isolated drops in ticks (vanilla 6000 = 5 min, default 12000 = 10 min).")
                .defineInRange("isolatedDropLifespanTicks", 12000, 1200, 72000);

        builder.pop();

        builder.comment(
                "待领暂存库与结算播报配置 / Pending vault and damage summary broadcast settings"
        ).push("notifications");

        MAX_PENDING_STACKS_PER_PLAYER = builder
                .comment("每位玩家待领暂存库最多保存的物品组数，超出后将转为地面受保护掉落物 / Maximum pending stacks stored per player in the vault before overflowing to protected ground drops.")
                .defineInRange("maxPendingStacksPerPlayer", 256, 16, 4096);

        LOGIN_PENDING_REMINDER = builder
                .comment("玩家登录时若有未领取的暂存战利品，是否发送带可点击按钮的提醒 / Remind players on login when they have unclaimed loot in the pending vault.")
                .define("loginPendingReminder", true);

        BROADCAST_DAMAGE_SUMMARY = builder
                .comment("击败受追踪的目标后，是否向参战玩家播报伤害贡献结算单 / Send a damage contribution summary to participating players after defeating a tracked entity.")
                .define("broadcastDamageSummary", true);

        BROADCAST_SUMMARY_FOR_SOLO = builder
                .comment("仅有一名玩家参与击杀时是否也发送伤害结算播报 / Also send the damage summary when only a single player damaged the target.")
                .define("broadcastSummaryForSolo", false);

        SUMMARY_MAX_ENTRIES = builder
                .comment("伤害结算单最多展示多少名玩家 / Maximum number of players shown in the damage summary message.")
                .defineInRange("summaryMaxEntries", 10, 1, 50);

        FALLBACK_LANGUAGE = builder
                .comment("控制台或无法读取客户端语言时的默认服务端文本语言（zh_cn 或 en_us） / Fallback server-side language for console or unknown client locales (zh_cn or en_us).")
                .define("fallbackLanguage", "en_us");

        builder.pop();

        SPEC = builder.build();
    }

    private FairBossLootConfig() {
    }

    private static boolean isValidStringEntry(Object value) {
        return value instanceof String s && !s.isBlank();
    }
}
