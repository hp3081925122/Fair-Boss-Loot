package com.hp.fairbossloot.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hp.fairbossloot.FairBossLoot;
import com.hp.fairbossloot.config.FairBossLootConfig;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 纯服务端多语言渲染器：按玩家客户端语言读取模组内置 lang JSON 并拼装 Component，
 * 确保未安装本模组的客户端不会看到原始翻译键，同时保留实体名与物品名的原生本地化组件。
 */
public final class ServerLang {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private static final Map<String, Map<String, String>> BUNDLES = new HashMap<>();

    static {
        loadBundle("en_us");
        loadBundle("zh_cn");
    }

    private ServerLang() {
    }

    private static void loadBundle(String locale) {
        String resourcePath = "/assets/" + FairBossLoot.MOD_ID + "/lang/" + locale + ".json";
        try (InputStream in = ServerLang.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("Fair Boss Loot server lang resource missing: {}", resourcePath);
                BUNDLES.put(locale, Collections.emptyMap());
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
                BUNDLES.put(locale, map != null ? map : Collections.emptyMap());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load Fair Boss Loot lang resource: {}", resourcePath, e);
            BUNDLES.put(locale, Collections.emptyMap());
        }
    }

    /**
     * 解析目标玩家应使用的语言代码。
     */
    public static String resolveLocale(@Nullable ServerPlayer player) {
        if (player != null) {
            String lang = player.getLanguage();
            if (lang != null && !lang.isBlank()) {
                return normalizeLocale(lang);
            }
        }
        String fallback = "en_us";
        try {
            fallback = FairBossLootConfig.FALLBACK_LANGUAGE.get();
        } catch (IllegalStateException ignored) {
            // 配置尚未加载时回退到默认英文
        }
        return normalizeLocale(fallback);
    }

    /**
     * 解析指令发送者应使用的语言代码。
     */
    public static String resolveLocale(CommandSourceStack source) {
        return resolveLocale(source.getPlayer());
    }

    private static String normalizeLocale(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("zh")) {
            return "zh_cn";
        }
        return "en_us";
    }

    /**
     * 获取指定语言键的纯文本模板。
     */
    public static String raw(@Nullable ServerPlayer player, String key) {
        return rawByLocale(resolveLocale(player), key);
    }

    public static String raw(CommandSourceStack source, String key) {
        return rawByLocale(resolveLocale(source), key);
    }

    private static String rawByLocale(String locale, String key) {
        Map<String, String> primary = BUNDLES.getOrDefault(locale, Collections.emptyMap());
        String template = primary.get(key);
        if (template != null) {
            return template;
        }
        Map<String, String> fallback = BUNDLES.getOrDefault("en_us", Collections.emptyMap());
        return fallback.getOrDefault(key, key);
    }

    /**
     * 按玩家语言构建带富文本参数的 Component。
     */
    public static MutableComponent format(@Nullable ServerPlayer player, String key, Object... args) {
        return formatTemplate(raw(player, key), args);
    }

    /**
     * 按指令源语言构建带富文本参数的 Component。
     */
    public static MutableComponent format(CommandSourceStack source, String key, Object... args) {
        return formatTemplate(raw(source, key), args);
    }

    /**
     * 将含 %s / %d / %% 占位符及旧版 § 颜色码的模板解析为 Component，保留 Component 参数的原生翻译特性。
     */
    private static MutableComponent formatTemplate(String template, Object... args) {
        MutableComponent root = Component.empty();
        StringBuilder literalBuf = new StringBuilder();
        ChatFormatting activeColor = null;
        int argIndex = 0;
        int len = template.length();

        for (int i = 0; i < len; i++) {
            char c = template.charAt(i);
            if (c == '§' && i + 1 < len) {
                char code = template.charAt(i + 1);
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (fmt == ChatFormatting.RESET) {
                        activeColor = null;
                    } else if (fmt.isColor()) {
                        activeColor = fmt;
                    }
                }
                literalBuf.append(c).append(code);
                i++;
                continue;
            }
            if (c == '%' && i + 1 < len) {
                char next = template.charAt(i + 1);
                if (next == '%') {
                    literalBuf.append('%');
                    i++;
                    continue;
                }
                if (next == 's' || next == 'd') {
                    Object arg = argIndex < args.length ? args[argIndex++] : "";
                    if (arg instanceof Component compArg) {
                        if (!literalBuf.isEmpty()) {
                            root.append(Component.literal(literalBuf.toString()));
                            literalBuf.setLength(0);
                        }
                        MutableComponent copy = compArg.copy();
                        if (activeColor != null && copy.getStyle().getColor() == null) {
                            copy.withStyle(activeColor);
                        }
                        root.append(copy);
                    } else {
                        literalBuf.append(arg == null ? "" : arg.toString());
                    }
                    i++;
                    continue;
                }
            }
            literalBuf.append(c);
        }

        if (!literalBuf.isEmpty()) {
            root.append(Component.literal(literalBuf.toString()));
        }
        return root;
    }
}
