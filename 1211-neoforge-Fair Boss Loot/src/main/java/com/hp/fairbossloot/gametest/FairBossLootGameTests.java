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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fair Boss Loot 服务端自动化 GameTest 套件，覆盖核心掉落分配、伤害门槛、召唤物归属、保护隔离、暂存库与经验球隔离。
 */
@GameTestHolder(FairBossLoot.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FairBossLootGameTests {
    private FairBossLootGameTests() {
    }

    private static ServerPlayer createTestPlayer(GameTestHelper helper, String name, String locale) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ClientInformation info = new ClientInformation(locale, 8, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, true);
        CommonListenerCookie cookie = new CommonListenerCookie(new GameProfile(UUID.randomUUID(), name), 0, info, false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // 模板环境附带 Jade 辅助模组，其登录事件会发送 jade:server_ping_v1，给模拟连接声明该通道以避免握手检查报错
        ChannelAttributes.getOrCreateCommonChannels(connection, ConnectionProtocol.PLAY)
                .add(ResourceLocation.fromNamespaceAndPath("jade", "server_ping_v1"));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
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
        WitherBoss wither = EntityType.WITHER.create(level);
        if (wither == null) {
            throw new IllegalStateException("Failed to create WitherBoss");
        }
        wither.setNoAi(true);
        wither.setInvulnerableTicks(0);
        Vec3 pos = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        wither.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(wither);
        return wither;
    }

    /**
     * 测试 1：两名达标玩家共同击杀凋灵，每人各得一份受隔离的下界之星，且互相不可见、不可拾取。
     */
    @GameTest(template = "empty")
    public static void twoPlayersBossIsolatedDrops(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "TesterA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "TesterB", "en_us");
        try {
            WitherBoss wither = spawnTestWither(helper);
            // 凋灵最大生命 300，5% 门槛为 15；两人各打超过 20 伤害后由 A 击杀
            wither.hurt(level.damageSources().playerAttack(playerA), 30.0F);
            wither.invulnerableTime = 0;
            wither.hurt(level.damageSources().playerAttack(playerB), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurt(level.damageSources().playerAttack(playerA), 500.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> stars = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.NETHER_STAR));
            helper.assertTrue(stars.size() == 2, "应生成 2 份独立下界之星，实际=" + stars.size());

            ItemEntity starA = stars.stream().filter(e -> playerA.getUUID().equals(e.getTarget())).findFirst().orElse(null);
            ItemEntity starB = stars.stream().filter(e -> playerB.getUUID().equals(e.getTarget())).findFirst().orElse(null);
            helper.assertTrue(starA != null && starB != null, "两名玩家应各自拥有归属下界之星");

            // 可见性隔离校验
            helper.assertTrue(starA.broadcastToPlayer(playerA), "玩家 A 应能看到自己的掉落物");
            helper.assertTrue(!starA.broadcastToPlayer(playerB), "玩家 B 不应看到玩家 A 的隔离掉落物");
            helper.assertTrue(starB.broadcastToPlayer(playerB), "玩家 B 应能看到自己的掉落物");
            helper.assertTrue(!starB.broadcastToPlayer(playerA), "玩家 A 不应看到玩家 B 的隔离掉落物");

            // 拾取事件拦截校验
            ItemEntityPickupEvent.Pre wrongPickup = new ItemEntityPickupEvent.Pre(playerB, starA);
            NeoForge.EVENT_BUS.post(wrongPickup);
            helper.assertTrue(wrongPickup.canPickup() == TriState.FALSE, "非归属玩家拾取事件应被强制拒绝");

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    /**
     * 测试 2：未达到 5% 伤害门槛的蹭伤害玩家不应获得战利品。
     */
    @GameTest(template = "empty")
    public static void belowThresholdNoReward(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "CarryA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "LeechB", "en_us");
        try {
            WitherBoss wither = spawnTestWither(helper);
            // B 只打 5 点伤害（< 300 * 5% = 15），A 打足伤害并击杀
            wither.hurt(level.damageSources().playerAttack(playerB), 5.0F);
            wither.invulnerableTime = 0;
            wither.hurt(level.damageSources().playerAttack(playerA), 500.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> stars = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.NETHER_STAR));
            helper.assertTrue(stars.size() == 1, "未达标玩家不应分到下界之星，实际=" + stars.size());
            helper.assertTrue(playerA.getUUID().equals(stars.getFirst().getTarget()), "唯一一份下界之星应归属玩家 A");

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    /**
     * 测试 3：投射物与驯服宠物造成的伤害应自动归属给主人，并正确结算两份奖励。
     */
    @GameTest(template = "empty")
    public static void summonAndProjectileAttribution(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "ArcherA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "TamerB", "en_us");
        try {
            WitherBoss wither = spawnTestWither(helper);
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(playerA);
            wither.hurt(level.damageSources().arrow(arrow, playerA), 30.0F);
            wither.invulnerableTime = 0;

            Wolf wolf = EntityType.WOLF.create(level);
            helper.assertTrue(wolf != null, "创建驯服狼失败");
            wolf.tame(playerB);
            wither.hurt(level.damageSources().mobAttack(wolf), 30.0F);
            wither.invulnerableTime = 0;

            wither.hurt(level.damageSources().playerAttack(playerA), 500.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> stars = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.NETHER_STAR));
            helper.assertTrue(stars.size() == 2, "弓箭与驯服狼主人应各自获得下界之星，实际=" + stars.size());
            boolean hasA = stars.stream().anyMatch(e -> playerA.getUUID().equals(e.getTarget()));
            boolean hasB = stars.stream().anyMatch(e -> playerB.getUUID().equals(e.getTarget()));
            helper.assertTrue(hasA && hasB, "归属 UUID 必须同时包含 ArcherA 与 TamerB");

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    /**
     * 测试 4：全兼容混合管线校验——战利品表独立掉落 + 第三方 LivingDropsEvent 额外掉落按人发放 + 穿戴装备绝不复制。
     */
    @GameTest(template = "empty")
    public static void modEventExtraDropsAndNoEquipmentDupe(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "ModA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "ModB", "en_us");

        // 模拟第三方模组在 LivingDropsEvent 中追加自定义战利品（绿宝石块）
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
            Warden warden = EntityType.WARDEN.create(level);
            helper.assertTrue(warden != null, "创建监守者失败");
            warden.setNoAi(true);
            warden.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            warden.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            Vec3 pos = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
            warden.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            level.addFreshEntity(warden);

            // 监守者最大生命 500，5% 门槛 25；两人各打 60 后击杀
            warden.hurt(level.damageSources().playerAttack(playerA), 60.0F);
            warden.invulnerableTime = 0;
            warden.hurt(level.damageSources().playerAttack(playerB), 60.0F);
            warden.invulnerableTime = 0;
            warden.hurt(level.damageSources().playerAttack(playerA), 800.0F);

            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
            List<ItemEntity> catalysts = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.SCULK_CATALYST));
            List<ItemEntity> emeralds = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.EMERALD_BLOCK));
            List<ItemEntity> swords = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.DIAMOND_SWORD));

            helper.assertTrue(catalysts.size() == 2, "两名玩家应各自通过战利品表获得幽匿催发体，实际=" + catalysts.size());
            helper.assertTrue(emeralds.size() == 2, "两名玩家应各自获得第三方 LivingDropsEvent 额外掉落，实际=" + emeralds.size());
            helper.assertTrue(swords.size() == 1, "生物穿戴装备绝不应被复制，实际=" + swords.size());
            helper.assertTrue(swords.getFirst().getTarget() == null, "未复制的装备掉落应保持公共可拾取");

            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(fakeThirdPartyMod);
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    /**
     * 测试 5：隔离掉落物的火/岩浆/仙人掌/爆炸免疫、漏斗防偷取以及虚空自动救援进暂存库。
     */
    @GameTest(template = "empty")
    public static void protectionEnvironmentHopperAndVoidRescue(GameTestHelper helper) {
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

            helper.assertTrue(protectedDrop instanceof IsolatedDropAccess access && access.fairBossLoot$isIsolated(), "掉落物应已标记为隔离状态");
            helper.assertTrue(protectedDrop.fireImmune(), "受保护的隔离掉落物应对火焰免疫");
            helper.assertTrue(!protectedDrop.hurt(level.damageSources().lava(), 50.0F), "岩浆伤害应被阻断");
            helper.assertTrue(!protectedDrop.hurt(level.damageSources().cactus(), 50.0F), "仙人掌伤害应被阻断");
            helper.assertTrue(!protectedDrop.hurt(level.damageSources().explosion(null), 50.0F), "爆炸伤害应被阻断");
            helper.assertTrue(protectedDrop.isAlive(), "经历环境伤害后掉落物仍应存活");

            // 漏斗防偷取校验
            BlockPos hopperPos = helper.absolutePos(new BlockPos(0, 1, 0));
            level.setBlockAndUpdate(hopperPos, Blocks.HOPPER.defaultBlockState());
            HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(hopperPos);
            helper.assertTrue(hopper != null, "创建漏斗方块实体失败");
            boolean sucked = HopperBlockEntity.addItem(hopper, protectedDrop);
            helper.assertTrue(!sucked && protectedDrop.isAlive(), "漏斗不应吸走个人隔离掉落物");

            // 虚空救援校验
            boolean rescued = LootDeliveryService.rescueVoidItem(protectedDrop);
            helper.assertTrue(rescued && protectedDrop.isRemoved(), "虚空救援应移除原掉落实体并转入暂存库");
            PendingLootVault vault = PendingLootVault.get(level.getServer());
            helper.assertTrue(vault.getPendingCount(playerA.getUUID()) == 1, "玩家 A 暂存库应保存 1 组被救援的下界之星");

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA);
        }
    }

    /**
     * 测试 6：DIRECT_INVENTORY 模式直发背包、满背包入暂存库以及 /fairbossloot claim 领取指令。
     */
    @GameTest(template = "empty")
    public static void directInventoryVaultOverflowAndClaimCommand(GameTestHelper helper) {
        resetDefaults();
        FairBossLootConfig.DELIVERY_MODE.set(DeliveryMode.DIRECT_INVENTORY);
        ServerLevel level = helper.getLevel();
        ServerPlayer fullPlayer = createTestPlayer(helper, "FullBagA", "zh_cn");
        ServerPlayer emptyPlayer = createTestPlayer(helper, "EmptyBagB", "en_us");
        try {
            // 填满 fullPlayer 的全部 36 格主背包
            for (int slot = 0; slot < 36; slot++) {
                fullPlayer.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
            }

            WitherBoss wither = spawnTestWither(helper);
            wither.hurt(level.damageSources().playerAttack(fullPlayer), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurt(level.damageSources().playerAttack(emptyPlayer), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurt(level.damageSources().playerAttack(fullPlayer), 500.0F);

            int emptyStars = emptyPlayer.getInventory().countItem(Items.NETHER_STAR);
            helper.assertTrue(emptyStars == 1, "空背包玩家应直接获得 1 颗下界之星，实际=" + emptyStars);

            PendingLootVault vault = PendingLootVault.get(level.getServer());
            helper.assertTrue(vault.getPendingCount(fullPlayer.getUUID()) >= 1, "满背包玩家的下界之星应存入暂存库");

            // 清出 1 个背包空位并执行 /fairbossloot claim
            fullPlayer.getInventory().items.set(0, ItemStack.EMPTY);
            level.getServer().getCommands().performPrefixedCommand(fullPlayer.createCommandSourceStack(), "fairbossloot claim");

            int claimedStars = fullPlayer.getInventory().countItem(Items.NETHER_STAR);
            helper.assertTrue(claimedStars == 1, "执行 /fairbossloot claim 后玩家背包应拿到下界之星，实际=" + claimedStars);
            helper.assertTrue(vault.getPendingCount(fullPlayer.getUUID()) == 0, "领取完毕后暂存库应为空");

            helper.succeed();
        } finally {
            resetDefaults();
            cleanupPlayers(helper, fullPlayer, emptyPlayer);
        }
    }

    /**
     * 测试 7：经验球按人独立发放，且非归属玩家不可见、不可拾取。
     */
    @GameTest(template = "empty")
    public static void personalExperienceOrbsIsolation(GameTestHelper helper) {
        resetDefaults();
        ServerLevel level = helper.getLevel();
        ServerPlayer playerA = createTestPlayer(helper, "XpA", "zh_cn");
        ServerPlayer playerB = createTestPlayer(helper, "XpB", "en_us");
        try {
            // 将玩家暂时移开，避免经验球生成瞬间立刻被吸走
            Vec3 farPos = helper.absoluteVec(new Vec3(15.0D, 2.0D, 15.0D));
            playerA.teleportTo(farPos.x, farPos.y, farPos.z);
            playerB.teleportTo(farPos.x, farPos.y, farPos.z);

            WitherBoss wither = spawnTestWither(helper);
            wither.hurt(level.damageSources().playerAttack(playerA), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurt(level.damageSources().playerAttack(playerB), 40.0F);
            wither.invulnerableTime = 0;
            wither.hurt(level.damageSources().playerAttack(playerA), 500.0F);

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

            helper.assertTrue(xpForA == 50 && xpForB == 50, "两名玩家应各得 50 点专属经验，实际 A=" + xpForA + ", B=" + xpForB);
            helper.assertTrue(sampleA != null, "应找到玩家 A 的专属经验球");
            helper.assertTrue(sampleA.broadcastToPlayer(playerA) && !sampleA.broadcastToPlayer(playerB), "专属经验球应仅对归属玩家可见");

            PlayerXpEvent.PickupXp wrongXpPickup = new PlayerXpEvent.PickupXp(playerB, sampleA);
            NeoForge.EVENT_BUS.post(wrongXpPickup);
            helper.assertTrue(wrongXpPickup.isCanceled(), "非归属玩家拾取专属经验球应被取消");

            helper.succeed();
        } finally {
            cleanupPlayers(helper, playerA, playerB);
        }
    }

    /**
     * 测试 8：纯服务端多语言渲染器能按玩家语言输出中文/英文且不暴露原始语言键。
     */
    @GameTest(template = "empty")
    public static void serverSideLocalizationNoRawKeys(GameTestHelper helper) {
        ServerPlayer zhPlayer = createTestPlayer(helper, "ZhUser", "zh_cn");
        ServerPlayer enPlayer = createTestPlayer(helper, "EnUser", "en_us");
        try {
            Component bossName = Component.translatable("entity.minecraft.wither");
            Component zhMsg = ServerLang.format(zhPlayer, "fair_boss_loot.summary.header", bossName);
            Component enMsg = ServerLang.format(enPlayer, "fair_boss_loot.summary.header", bossName);

            String zhText = zhMsg.getString();
            String enText = enMsg.getString();
            helper.assertTrue(zhText.contains("战利品分配") && !zhText.contains("fair_boss_loot."), "中文客户端应收到中文文案，实际=" + zhText);
            helper.assertTrue(enText.contains("Fair Boss Loot") && !enText.contains("fair_boss_loot."), "英文客户端应收到英文文案，实际=" + enText);

            helper.succeed();
        } finally {
            cleanupPlayers(helper, zhPlayer, enPlayer);
        }
    }
}
