package a6a.dr.core;

import a6a.dr.config.MiaoConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 合并全局默认档与玩家覆盖档之后得到的最终生效配置。
 */
public record Settings(
        boolean enabled,
        List<String> inserts,
        PickMode pickMode,
        InsertPos position,
        InsertPos fallback,
        Map<MessageKind, InsertPos> categoryPositions,
        Map<MessageKind, Boolean> targets
) {

    public enum PickMode {
        RANDOM, FIRST, CYCLE;

        @Nullable
        public static PickMode parse(@Nullable String s) {
            if (s == null) {
                return null;
            }
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "random", "rand", "随机" -> RANDOM;
                case "first", "固定", "首个" -> FIRST;
                case "cycle", "rotate", "轮流", "轮换" -> CYCLE;
                default -> null;
            };
        }
    }

    public boolean targets(MessageKind kind) {
        Boolean b = targets.get(kind);
        return b != null && b;
    }

    public InsertPos positionFor(MessageKind kind) {
        InsertPos pos = categoryPositions.get(kind);
        return pos != null ? pos : position;
    }

    public static Settings resolve(MiaoConfig cfg, @Nullable String uuid, @Nullable String name) {
        MiaoConfig.Profile base = cfg.defaults != null ? cfg.defaults : MiaoConfig.Profile.defaults();
        MiaoConfig.Profile over = findProfile(cfg, uuid, name);
        return merge(base, over);
    }

    @Nullable
    private static MiaoConfig.Profile findProfile(MiaoConfig cfg, @Nullable String uuid, @Nullable String name) {
        if (cfg.players == null || cfg.players.isEmpty()) {
            return null;
        }
        if (uuid != null) {
            MiaoConfig.Profile p = cfg.players.get(uuid);
            if (p != null) {
                return p;
            }
            // UUID 允许写成带/不带连字符两种形式
            String noDash = uuid.replace("-", "");
            for (Map.Entry<String, MiaoConfig.Profile> e : cfg.players.entrySet()) {
                if (e.getKey().replace("-", "").equalsIgnoreCase(noDash)) {
                    return e.getValue();
                }
            }
        }
        if (name != null) {
            for (Map.Entry<String, MiaoConfig.Profile> e : cfg.players.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static Settings merge(MiaoConfig.Profile base, @Nullable MiaoConfig.Profile over) {
        boolean enabled = pick(base.enabled, over == null ? null : over.enabled, true);

        List<String> inserts;
        List<String> overInserts = over == null ? null : over.inserts;
        if (overInserts != null && !overInserts.isEmpty()) {
            inserts = new ArrayList<>(overInserts);
        } else if (base.inserts != null && !base.inserts.isEmpty()) {
            inserts = new ArrayList<>(base.inserts);
        } else {
            inserts = new ArrayList<>(List.of("喵"));
        }

        PickMode pickMode = firstNonNull(
                over == null ? null : PickMode.parse(over.pickMode),
                PickMode.parse(base.pickMode),
                PickMode.RANDOM);

        InsertPos position = firstNonNull(
                over == null ? null : InsertPos.parse(over.position),
                InsertPos.parse(base.position),
                InsertPos.SUFFIX);

        InsertPos fallback = firstNonNull(
                over == null ? null : InsertPos.parse(over.fallback),
                InsertPos.parse(base.fallback),
                InsertPos.SUFFIX);

        Map<MessageKind, InsertPos> categoryPositions = new EnumMap<>(MessageKind.class);
        collectPositions(categoryPositions, base.categoryPositions);
        if (over != null) {
            collectPositions(categoryPositions, over.categoryPositions);
        }

        Map<MessageKind, Boolean> targets = new EnumMap<>(MessageKind.class);
        for (MessageKind kind : MessageKind.values()) {
            targets.put(kind, false);
        }
        collectTargets(targets, base.targets);
        if (over != null) {
            collectTargets(targets, over.targets);
        }
        return new Settings(enabled, List.copyOf(inserts), pickMode, position, fallback,
                Map.copyOf(categoryPositions), Map.copyOf(targets));
    }

    private static void collectPositions(Map<MessageKind, InsertPos> out, @Nullable Map<String, String> src) {
        if (src == null) {
            return;
        }
        for (Map.Entry<String, String> e : src.entrySet()) {
            MessageKind kind = MessageKind.byId(e.getKey());
            InsertPos pos = InsertPos.parse(e.getValue());
            if (kind != null && pos != null) {
                out.put(kind, pos);
            }
        }
    }

    private static void collectTargets(Map<MessageKind, Boolean> out, @Nullable Map<String, Boolean> src) {
        if (src == null) {
            return;
        }
        for (Map.Entry<String, Boolean> e : src.entrySet()) {
            MessageKind kind = MessageKind.byId(e.getKey());
            if (kind != null && e.getValue() != null) {
                out.put(kind, e.getValue());
            }
        }
    }

    private static boolean pick(Boolean a, Boolean b, boolean def) {
        if (b != null) {
            return b;
        }
        if (a != null) {
            return a;
        }
        return def;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        throw new IllegalArgumentException("all null");
    }
}
