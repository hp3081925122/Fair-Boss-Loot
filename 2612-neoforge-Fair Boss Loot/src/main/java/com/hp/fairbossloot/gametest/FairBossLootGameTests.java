package com.hp.fairbossloot.gametest;

import com.hp.fairbossloot.FairBossLoot;
import com.hp.fairbossloot.config.DeliveryMode;
import com.hp.fairbossloot.config.ExperienceMode;
import com.hp.fairbossloot.config.FairBossLootConfig;
import com.hp.fairbossloot.config.KillerPolicy;
import com.hp.fairbossloot.drop.IsolatedDropAccess;
import com.hp.fairbossloot.drop.PersonalXpAccess;
import com.hp.fairbossloot.i18n.ServerLang;
import com.hp.fairbossloot.storage.LootDeliveryService;
import com.hp.fairbossloot.storage.PendingLootVault;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.TriState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Fair Boss Loot 26.1.2 NeoForge 服务端自动化 GameTest 套件（全部 8 项与 1.21.1 保持 1:1 一致）。
 */
@EventBusSubscriber(modid = FairBossLoot.MOD_ID)
public final class FairBossLootGameTests {
    private static final Identifier EMPTY_STRUCTURE = Identifier.fromNamespaceAndPath(FairBossLoot.MOD_ID, "empty");
    private static final Identifier ENV_ID = Identifier.fromNamespaceAndPath(FairBossLoot.MOD_ID, "default");

    private static final Identifier FN_TWO_PLAYERS = id("two_players_boss_isolated_drops");
    private static final Identifier FN_BELOW_THRESHOLD = id("below_threshold_no_reward");
    private static final Identifier FN_SUMMON_PROJECTILE = id("summon_and_projectile_attribution");
    private static final Identifier FN_MOD_EVENT_NO_DUPE = id("mod_event_extra_drops_and_no_equipment_dupe");
    private static final Identifier FN_PROTECTION_VOID = id("protection_environment_hopper_and_void_rescue");
    private static final Identifier FN_DIRECT_CLAIM = id("direct_inventory_vault_overflow_and_claim_command");
    private static final Identifier FN_XP_ISOLATION = id("personal_experience_orbs_isolation");
    private static final Identifier FN_SERVER_LANG = id("server_side_localization_no_raw_keys");

    private FairBossLootGameTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FairBossLoot.MOD_ID, path);
    }

    @SubscribeEvent
    public static void onRegisterTestFunctions(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
            event.register(Registries.TEST_FUNCTION, helper -> {
                helper.register(FN_TWO_PLAYERS, FairBossLootGameTests::twoPlayersBossIsolatedDrops);
                helper.register(FN_BELOW_THRESHOLD, FairBossLootGameTests::belowThresholdNoReward);
                helper.register(FN_SUMMON_PROJECTILE, FairBossLootGameTests::summonAndProjectileAttribution);
                helper.register(FN_MOD_EVENT_NO_DUPE, FairBossLootGameTests::modEventExtraDropsAndNoEquipmentDupe);
                helper.register(FN_PROTECTION_VOID, FairBossLootGameTests::protectionEnvironmentHopperAndVoidRescue);
                helper.register(FN_DIRECT_CLAIM, FairBossLootGameTests::directInventoryVaultOverflowAndClaimCommand);
                helper.register(FN_XP_ISOLATION, FairBossLootGameTests::personalExperienceOrbsIsolation);
                helper.register(FN_SERVER_LANG, FairBossLootGameTests::serverSideLocalizationNoRawKeys);
            });
        }
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(ENV_ID, new TestEnvironmentDefinition.AllOf(List.of()));
        registerInstance(event, env, FN_TWO_PLAYERS);
        registerInstance(event, env, FN_BELOW_THRESHOLD);
        registerInstance(event, env, FN_SUMMON_PROJECTILE);
        registerInstance(event, env, FN_MOD_EVENT_NO_DUPE);
        registerInstance(event, env, FN_PROTECTION_VOID);
        registerInstance(event, env, FN_DIRECT_CLAIM);
        registerInstance(event, env, FN_XP_ISOLATION);
        registerInstance(event, env, FN_SERVER_LANG);
    }

    private static void registerInstance(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> env, Identifier testId) {
        ResourceKey<Consumer<GameTestHelper>> fnKey = ResourceKey.create(Registries.TEST_FUNCTION, testId);
        // 26.1.2 中 empty 结构尺寸为 1x1x1 且默认列距仅 5 格，配置 10 格 padding 保证相邻测试的 8 格扫描盒彻底隔离
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                env, EMPTY_STRUCTURE, 60, 0, true, Rotation.NONE, false, 1, 1, false, 10
        );
        event.registerTest(testId, new FunctionGameTestInstance(fnKey, data));
    }

    private static ServerPlayer createTestPlayer(GameTestHelper helper, String name, String locale) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ClientInformation info = new ClientInformation(
                locale, 8, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, true, ParticleStatus.ALL
        );
        CommonListenerCookie cookie = new CommonListenerCookie(new GameProfile(UUID.randomUUID(), name), 0, info, false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        ChannelAttributes.getOrCreateCommonChannels(connection, ConnectionProtocol.PLAY)
                .add(Identifier.fromNamespaceAndPath("jade", "server_ping_v1"));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        player.teleportTo(pos.x, pos.y, pos.z);
        return player;
    }

    private static void cleanupPlayers(GameTestHelper helper, ServerPlayer... players) {
        MinecraftServer server = helper.getLevel().getServer();
        for (ServerPlayer player : players) {
            if (player != null) {
                PendingLootVault.get(server).clear(player.getUUID());
                server.getPlayerList().remove(player);
            }
        }
    }

    private static void resetDefaults() {
        FairBossLootConfig.ENABLED.set(true);
        FairBossLootConfig.APPLY_TO_ALL_LIVING.set(false);
        FairBossLootConfig.MIN_DAMAGE.set(0.05D);
        FairBossLootConfig.MIN_DAMAGE_IS_PERCENTAGE.set(true);
        FairBossLootConfig.DAMAGE_WINDOW_SECONDS.set(60);
        FairBossLootConfig.MAX_REWARDED_PLAYERS.set(0);
        FairBossLootConfig.KILLER_POLICY.set(KillerPolicy.FILL);
        FairBossLootConfig.TRACK_SUMMON_AND_PET_OWNERS.set(true);
        FairBossLootConfig.DELIVERY_MODE.set(DeliveryMode.ISOLATED_DROP);
        FairBossLootConfig.PROTECT_SOLO_KILLS.set(true);
        FairBossLootConfig.DUPLICATE_CUSTOM_DEATH_DROPS.set(true);
        FairBossLootConfig.DUPLICATE_MOD_EVENT_DROPS.set(true);
        FairBossLootConfig.DUPLICATE_EQUIPMENT_DROPS.set(false);
        FairBossLootConfig.EXPERIENCE_MODE.set(ExperienceMode.FULL_PER_PLAYER);
        FairBossLootConfig.PROTECT_FROM_ENVIRONMENT.set(true);
        FairBossLootConfig.RESCUE_FROM_VOID.set(true);
        FairBossLootConfig.BLOCK_HOPPER_PICKUP.set(true);
    }

    private static WitherBoss spawnTestWither(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WitherBoss wither = EntityType.WITHER.create(level, EntitySpawnReason.EVENT);
        if (wither == null) {
            throw new IllegalStateException("Failed to create WitherBoss");
        }
        wither.setNoAi(true);
        wither.setInvulnerableTicks(0);
        Vec3 pos = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        wither.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(wither);
        return wither;
    }

    private static void twoPlayersBossIsolatedDrops(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "TesterA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "TesterB", "en_us");
        try {
            WitherBoss wither = spawnTestWither(helper);
            wither.hurtServer(level, level.damageSources().playerAttack(playerA), 30.0F);
            wither.invulnerableTime = 0;
            wither.hurtServer(level, level.damageSources().playerAttack(playerB), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurtServer(level, level.damageSources().playerAttack(playerA), 500.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> stars = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.NETHER_STAR));
            helper.assertTrue(stars.size() == 2, Component.literal("应生成 2 份独立下界之星，实际=" + stars.size()));

            ItemEntity starA = stars.stream().filter(e -> playerA.getUUID().equals(e.getTarget())).findFirst().orElse(null);
            ItemEntity starB = stars.stream().filter(e -> playerB.getUUID().equals(e.getTarget())).findFirst().orElse(null);
            helper.assertTrue(starA != null && starB != null, Component.literal("两名玩家应各自拥有归属下界之星"));

            helper.assertTrue(starA.broadcastToPlayer(playerA), Component.literal("玩家 A 应能看到自己的掉落物"));
            helper.assertTrue(!starA.broadcastToPlayer(playerB), Component.literal("玩家 B 不应看到玩家 A 的隔离掉落物"));
            helper.assertTrue(starB.broadcastToPlayer(playerB), Component.literal("玩家 B 应能看到自己的掉落物"));
            helper.assertTrue(!starB.broadcastToPlayer(playerA), Component.literal("玩家 A 不应看到玩家 B 的隔离掉落物"));

            ItemEntityPickupEvent.Pre wrongPickup = new ItemEntityPickupEvent.Pre(playerB, starA);
            NeoForge.EVENT_BUS.post(wrongPickup);
            helper.assertTrue(wrongPickup.canPickup() == TriState.FALSE, Component.literal("非归属玩家拾取事件应被强制拒绝"));

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    private static void belowThresholdNoReward(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "CarryA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "LeechB", "en_us");
        try {
            WitherBoss wither = spawnTestWither(helper);
            wither.hurtServer(level, level.damageSources().playerAttack(playerB), 5.0F);
            wither.invulnerableTime = 0;
            wither.hurtServer(level, level.damageSources().playerAttack(playerA), 500.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> stars = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.NETHER_STAR));
            helper.assertTrue(stars.size() == 1, Component.literal("未达标玩家不应分到下界之星，实际=" + stars.size()));
            helper.assertTrue(playerA.getUUID().equals(stars.getFirst().getTarget()), Component.literal("唯一一份下界之星应归属玩家 A"));

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    private static void summonAndProjectileAttribution(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "ArcherA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "TamerB", "en_us");
        try {
            WitherBoss wither = spawnTestWither(helper);
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(playerA);
            wither.hurtServer(level, level.damageSources().arrow(arrow, playerA), 30.0F);
            wither.invulnerableTime = 0;

            Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.EVENT);
            helper.assertTrue(wolf != null, Component.literal("创建驯服狼失败"));
            wolf.tame(playerB);
            wither.hurtServer(level, level.damageSources().mobAttack(wolf), 30.0F);
            wither.invulnerableTime = 0;

            wither.hurtServer(level, level.damageSources().playerAttack(playerA), 500.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> stars = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.NETHER_STAR));
            helper.assertTrue(stars.size() == 2, Component.literal("弓箭与驯服狼主人应各自获得下界之星，实际=" + stars.size()));
            boolean hasA = stars.stream().anyMatch(e -> playerA.getUUID().equals(e.getTarget()));
            boolean hasB = stars.stream().anyMatch(e -> playerB.getUUID().equals(e.getTarget()));
            helper.assertTrue(hasA && hasB, Component.literal("归属 UUID 必须同时包含 ArcherA 与 TamerB"));

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    private static void modEventExtraDropsAndNoEquipmentDupe(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "ModA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "ModB", "en_us");

        Consumer<LivingDropsEvent> fakeThirdPartyMod = event -> {
            if (event.getEntity() instanceof Warden) {
                ItemEntity extra = new ItemEntity(
                        event.getEntity().level(),
                        event.getEntity().getX(),
                        event.getEntity().getY(),
                        event.getEntity().getZ(),
                        new ItemStack(Items.EMERALD_BLOCK)
                );
                event.getDrops().add(extra);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDropsEvent.class, fakeThirdPartyMod);
        try {
            Warden warden = EntityType.WARDEN.create(level, EntitySpawnReason.EVENT);
            helper.assertTrue(warden != null, Component.literal("创建监守者失败"));
            warden.setNoAi(true);
            warden.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            warden.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            Vec3 pos = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
            warden.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            level.addFreshEntity(warden);

            warden.hurtServer(level, level.damageSources().playerAttack(playerA), 60.0F);
            warden.invulnerableTime = 0;
            warden.hurtServer(level, level.damageSources().playerAttack(playerB), 60.0F);
            warden.invulnerableTime = 0;
            warden.hurtServer(level, level.damageSources().playerAttack(playerA), 800.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> catalysts = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.SCULK_CATALYST));
            List<ItemEntity> emeralds = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.EMERALD_BLOCK));
            List<ItemEntity> swords = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.DIAMOND_SWORD));

            helper.assertTrue(catalysts.size() == 2, Component.literal("两名玩家应各自通过战利品表获得幽匿催发体，实际=" + catalysts.size()));
            helper.assertTrue(emeralds.size() == 2, Component.literal("两名玩家应各自获得第三方 LivingDropsEvent 额外掉落，实际=" + emeralds.size()));
            helper.assertTrue(swords.size() == 1, Component.literal("生物穿戴装备绝不应被复制，实际=" + swords.size()));
            helper.assertTrue(swords.getFirst().getTarget() == null, Component.literal("未复制的装备掉落应保持公共可拾取"));

            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(fakeThirdPartyMod);
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    private static void protectionEnvironmentHopperAndVoidRescue(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "ProtectA", "zh_cn");
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
            ItemEntity protectedDrop = LootDeliveryService.spawnIsolatedItem(
                    level,
                    pos,
                    new ItemStack(Items.NETHER_STAR),
                    playerA.getUUID(),
                    "Wither"
            );

            helper.assertTrue(protectedDrop instanceof IsolatedDropAccess access && access.fairBossLoot$isIsolated(), Component.literal("掉落物应已标记为隔离状态"));
            helper.assertTrue(protectedDrop.fireImmune(), Component.literal("受保护的隔离掉落物应对火焰免疫"));
            helper.assertTrue(!protectedDrop.hurtServer(level, level.damageSources().lava(), 50.0F), Component.literal("岩浆伤害应被阻断"));
            helper.assertTrue(!protectedDrop.hurtServer(level, level.damageSources().cactus(), 50.0F), Component.literal("仙人掌伤害应被阻断"));
            helper.assertTrue(!protectedDrop.hurtServer(level, level.damageSources().explosion(null), 50.0F), Component.literal("爆炸伤害应被阻断"));
            helper.assertTrue(protectedDrop.isAlive(), Component.literal("经历环境伤害后掉落物仍应存活"));

            BlockPos hopperPos = helper.absolutePos(new BlockPos(0, 1, 0));
            level.setBlockAndUpdate(hopperPos, Blocks.HOPPER.defaultBlockState());
            HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(hopperPos);
            helper.assertTrue(hopper != null, Component.literal("创建漏斗方块实体失败"));
            boolean sucked = HopperBlockEntity.addItem(hopper, protectedDrop);
            helper.assertTrue(!sucked && protectedDrop.isAlive(), Component.literal("漏斗不应吸走个人隔离掉落物"));

            boolean rescued = LootDeliveryService.rescueVoidItem(protectedDrop);
            helper.assertTrue(rescued && protectedDrop.isRemoved(), Component.literal("虚空救援应移除原掉落实体并转入暂存库"));
            PendingLootVault vault = PendingLootVault.get(level.getServer());
            helper.assertTrue(vault.getPendingCount(playerA.getUUID()) == 1, Component.literal("玩家 A 暂存库应保存 1 组被救援的下界之星"));

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA);
        }
    }

    private static void directInventoryVaultOverflowAndClaimCommand(GameTestHelper helper) {
        resetDefaults();
        FairBossLootConfig.DELIVERY_MODE.set(DeliveryMode.DIRECT_INVENTORY);
        ServerLevel level = helper.getLevel();
        ServerPlayer fullPlayer = createTestPlayer(helper, "FullBagA", "zh_cn");
        ServerPlayer emptyPlayer = createTestPlayer(helper, "EmptyBagB", "en_us");
        try {
            var fullItems = fullPlayer.getInventory().getNonEquipmentItems();
            for (int slot = 0; slot < fullItems.size(); slot++) {
                fullItems.set(slot, new ItemStack(Items.COBBLESTONE, 64));
            }

            WitherBoss wither = spawnTestWither(helper);
            wither.hurtServer(level, level.damageSources().playerAttack(fullPlayer), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurtServer(level, level.damageSources().playerAttack(emptyPlayer), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurtServer(level, level.damageSources().playerAttack(fullPlayer), 500.0F);

            int emptyStars = emptyPlayer.getInventory().countItem(Items.NETHER_STAR);
            helper.assertTrue(emptyStars == 1, Component.literal("空背包玩家应直接获得 1 颗下界之星，实际=" + emptyStars));

            PendingLootVault vault = PendingLootVault.get(level.getServer());
            helper.assertTrue(vault.getPendingCount(fullPlayer.getUUID()) >= 1, Component.literal("满背包玩家的下界之星应存入暂存库"));

            fullItems.set(0, ItemStack.EMPTY);
            level.getServer().getCommands().performPrefixedCommand(fullPlayer.createCommandSourceStack(), "fairbossloot claim");

            int claimedStars = fullPlayer.getInventory().countItem(Items.NETHER_STAR);
            helper.assertTrue(claimedStars == 1, Component.literal("执行 /fairbossloot claim 后玩家背包应拿到下界之星，实际=" + claimedStars));
            helper.assertTrue(vault.getPendingCount(fullPlayer.getUUID()) == 0, Component.literal("领取完毕后暂存库应为空"));

            helper.succeed();
        } finally {
            resetDefaults();
            cleanupPlayers(helper, fullPlayer, emptyPlayer);
        }
    }

    private static void personalExperienceOrbsIsolation(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "XpA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "XpB", "en_us");
        try {
            Vec3 farPos = helper.absoluteVec(new Vec3(15.0D, 2.0D, 15.0D));
            playerA.teleportTo(farPos.x, farPos.y, farPos.z);
            playerB.teleportTo(farPos.x, farPos.y, farPos.z);

            WitherBoss wither = spawnTestWither(helper);
            wither.hurtServer(level, level.damageSources().playerAttack(playerA), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurtServer(level, level.damageSources().playerAttack(playerB), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurtServer(level, level.damageSources().playerAttack(playerA), 500.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box);
            int xpForA = 0;
            int xpForB = 0;
            ExperienceOrb sampleA = null;
            for (ExperienceOrb orb : orbs) {
                if (orb instanceof PersonalXpAccess access) {
                    if (playerA.getUUID().equals(access.fairBossLoot$getOwnerId())) {
                        xpForA += orb.getValue();
                        sampleA = orb;
                    } else if (playerB.getUUID().equals(access.fairBossLoot$getOwnerId())) {
                        xpForB += orb.getValue();
                    }
                }
            }

            helper.assertTrue(xpForA == 50 && xpForB == 50, Component.literal("两名玩家应各得 50 点专属经验，实际 A=" + xpForA + ", B=" + xpForB));
            helper.assertTrue(sampleA != null, Component.literal("应找到玩家 A 的专属经验球"));
            helper.assertTrue(sampleA.broadcastToPlayer(playerA) && !sampleA.broadcastToPlayer(playerB), Component.literal("专属经验球应仅对归属玩家可见"));

            PlayerXpEvent.PickupXp wrongXpPickup = new PlayerXpEvent.PickupXp(playerB, sampleA);
            NeoForge.EVENT_BUS.post(wrongXpPickup);
            helper.assertTrue(wrongXpPickup.isCanceled(), Component.literal("非归属玩家拾取专属经验球应被取消"));

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    private static void serverSideLocalizationNoRawKeys(GameTestHelper helper) {
        ServerPlayer zhPlayer = createTestPlayer(helper, "ZhUser", "zh_cn");
        ServerPlayer enPlayer = createTestPlayer(helper, "EnUser", "en_us");
        try {
            Component bossName = Component.translatable("entity.minecraft.wither");
            Component zhMsg = ServerLang.format(zhPlayer, "fair_boss_loot.summary.header", bossName);
            Component enMsg = ServerLang.format(enPlayer, "fair_boss_loot.summary.header", bossName);

            String zhText = zhMsg.getString();
            String enText = enMsg.getString();
            helper.assertTrue(zhText.contains("战利品分配") && !zhText.contains("fair_boss_loot."), Component.literal("中文客户端应收到中文文案，实际=" + zhText));
            helper.assertTrue(enText.contains("Fair Boss Loot") && !enText.contains("fair_boss_loot."), Component.literal("英文客户端应收到英文文案，实际=" + enText));

            helper.succeed();
        } finally {
            cleanupPlayers(helper, zhPlayer, enPlayer);
        }
    }
}
