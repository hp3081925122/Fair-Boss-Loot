package com.hp.fairbossloot.tracking;

import com.hp.fairbossloot.config.FairBossLootConfig;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 26.1.2 伤害归属解析器：支持真实玩家、投射物、药水云、TNT、驯服宠物、OwnableEntity 及常见模组召唤物的主人追溯。
 */
public final class AttackerResolver {
    private static final int MAX_DEPTH = 4;
    private static final String[] REFLECT_OWNER_ENTITY_METHODS = {"getOwner", "getSummoner", "getCaster"};
    private static final String[] REFLECT_OWNER_UUID_METHODS = {"getOwnerUUID", "getSummonerUUID", "getOwnerUuid"};

    private static final Map<Class<?>, Optional<Method>> ENTITY_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> UUID_METHOD_CACHE = new ConcurrentHashMap<>();

    public record ResolvedAttacker(UUID playerId, String displayName, @Nullable ServerPlayer onlinePlayer) {
    }

    private AttackerResolver() {
    }

    /**
     * 从 DamageSource 解析归属的真实玩家（自动排除假人 FakePlayer）。
     */
    @Nullable
    public static ResolvedAttacker resolve(ServerLevel level, @Nullable DamageSource source) {
        if (source == null) {
            return null;
        }
        ResolvedAttacker fromEntity = resolveEntity(level, source.getEntity(), 0);
        if (fromEntity != null) {
            return fromEntity;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null && direct != source.getEntity()) {
            return resolveEntity(level, direct, 0);
        }
        return null;
    }

    @Nullable
    public static ResolvedAttacker resolveEntity(ServerLevel level, @Nullable Entity entity, int depth) {
        if (entity == null || depth > MAX_DEPTH) {
            return null;
        }

        if (entity instanceof ServerPlayer serverPlayer) {
            if (serverPlayer instanceof FakePlayer) {
                return null;
            }
            return new ResolvedAttacker(serverPlayer.getUUID(), serverPlayer.getGameProfile().name(), serverPlayer);
        }

        if (!FairBossLootConfig.TRACK_SUMMON_AND_PET_OWNERS.get()) {
            return null;
        }

        if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            if (owner != null && owner != entity) {
                ResolvedAttacker resolved = resolveEntity(level, owner, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        if (entity instanceof OwnableEntity ownable) {
            LivingEntity owner = ownable.getOwner();
            if (owner != null && owner != entity) {
                ResolvedAttacker resolved = resolveEntity(level, owner, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
            EntityReference<LivingEntity> ownerRef = ownable.getOwnerReference();
            if (ownerRef != null) {
                return resolveByUuid(level, ownerRef.getUUID());
            }
        }

        Entity reflectedOwner = invokeOwnerEntityMethod(entity);
        if (reflectedOwner != null && reflectedOwner != entity) {
            ResolvedAttacker resolved = resolveEntity(level, reflectedOwner, depth + 1);
            if (resolved != null) {
                return resolved;
            }
        }

        UUID reflectedUuid = invokeOwnerUuidMethod(entity);
        if (reflectedUuid != null) {
            return resolveByUuid(level, reflectedUuid);
        }

        return null;
    }

    @Nullable
    private static ResolvedAttacker resolveByUuid(ServerLevel level, UUID uuid) {
        MinecraftServer server = level.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            if (online instanceof FakePlayer) {
                return null;
            }
            return new ResolvedAttacker(online.getUUID(), online.getGameProfile().name(), online);
        }
        String cachedName = server.services().nameToIdCache().get(uuid).map(NameAndId::name).orElse(null);
        if (cachedName != null) {
            return new ResolvedAttacker(uuid, cachedName, null);
        }
        return null;
    }

    @Nullable
    private static Entity invokeOwnerEntityMethod(Entity entity) {
        Optional<Method> method = ENTITY_METHOD_CACHE.computeIfAbsent(entity.getClass(), clazz -> {
            for (String name : REFLECT_OWNER_ENTITY_METHODS) {
                try {
                    Method m = clazz.getMethod(name);
                    if (Entity.class.isAssignableFrom(m.getReturnType())) {
                        return Optional.of(m);
                    }
                } catch (NoSuchMethodException ignored) {
                    // 继续尝试下一个常见方法名
                }
            }
            return Optional.empty();
        });
        if (method.isEmpty()) {
            return null;
        }
        try {
            Object result = method.get().invoke(entity);
            return result instanceof Entity e ? e : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static UUID invokeOwnerUuidMethod(Entity entity) {
        Optional<Method> method = UUID_METHOD_CACHE.computeIfAbsent(entity.getClass(), clazz -> {
            for (String name : REFLECT_OWNER_UUID_METHODS) {
                try {
                    Method m = clazz.getMethod(name);
                    if (UUID.class.isAssignableFrom(m.getReturnType())) {
                        return Optional.of(m);
                    }
                } catch (NoSuchMethodException ignored) {
                    // 继续尝试下一个常见方法名
                }
            }
            return Optional.empty();
        });
        if (method.isEmpty()) {
            return null;
        }
        try {
            Object result = method.get().invoke(entity);
            return result instanceof UUID id ? id : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
