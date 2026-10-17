package a6a.dr.core;

import a6a.dr.Miao;
import a6a.dr.config.ConfigManager;
import a6a.dr.config.MiaoConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息处理核心：把"原始文本 + 生效配置"变成"插入后的文本"。
 */
public final class MessageProcessor {

    /** 用于 cycle 模式的轮转计数，按生效配置实例区分。 */
    private static final Map<Settings, AtomicInteger> COUNTERS = new WeakHashMap<>();

    private MessageProcessor() {
    }

    /** 处理结果：要么只替换文本，要么可以走"保留翻译的追加"快路径。 */
    public record Outcome(String text, Mode mode, String insert) {
        public enum Mode {
            /** 无需改动。 */
            UNCHANGED,
            /** 在开头插入（可保留原 Component 的翻译与样式）。 */
            PREFIX,
            /** 在末尾插入（可保留原 Component 的翻译与样式）。 */
            SUFFIX,
            /** 中间插入/多处插入，只能重建文本。 */
            FLATTEN,
            /** 玩家要求本条不插语。 */
            SUPPRESS
        }
    }

    // ------------------------------------------------------------------
    // 聊天
    // ------------------------------------------------------------------

    public static PlayerChatMessage processChat(PlayerChatMessage message, ServerPlayer sender) {
        if (message == null || sender == null || !ConfigManager.get().enabled) {
            return message;
        }
        if (message.isSystem() || message.isFullyFiltered()) {
            return message;
        }
        try {
            String raw = message.decoratedContent().getString();
            if (raw.isEmpty()) {
                return message;
            }
            Settings settings = ConfigManager.settingsFor(sender.getUUID(), sender.getScoreboardName());
            Outcome outcome = transform(raw, settings, MessageKind.CHAT);
            if (outcome.mode() == Outcome.Mode.UNCHANGED || outcome.text().equals(raw)) {
                return message;
            }
            if (ConfigManager.get().debug) {
                Miao.LOGGER.info("[debug] chat {}: {} -> {}", sender.getScoreboardName(), raw, outcome.text());
            }
            return message.withUnsignedContent(Component.literal(outcome.text()));
        } catch (Exception e) {
            Miao.LOGGER.warn("处理聊天消息时出错，已跳过", e);
            return message;
        }
    }

    /**
     * 处理 {@code /say} 一类"命令产生的聊天消息"（走的是 CommandSourceStack 重载）。
     */
    public static PlayerChatMessage processCommandChat(PlayerChatMessage message, net.minecraft.commands.CommandSourceStack source) {
        if (message == null || source == null || !ConfigManager.get().enabled || !message.isSystem()) {
            return message;
        }
        try {
            String raw = message.decoratedContent().getString();
            if (raw.isEmpty()) {
                return message;
            }
            ServerPlayer player = source.getPlayer();
            Settings settings = player != null
                    ? ConfigManager.settingsFor(player.getUUID(), player.getScoreboardName())
                    : ConfigManager.settingsFor(null, null);
            Outcome outcome = transform(raw, settings, MessageKind.CHAT);
            if (outcome.mode() == Outcome.Mode.UNCHANGED || outcome.text().equals(raw)) {
                return message;
            }
            if (ConfigManager.get().debug) {
                Miao.LOGGER.info("[debug] command-chat {}: {} -> {}", source.getTextName(), raw, outcome.text());
            }
            return PlayerChatMessage.system(outcome.text());
        } catch (Exception e) {
            Miao.LOGGER.warn("处理命令聊天消息时出错，已跳过", e);
            return message;
        }
    }

    // ------------------------------------------------------------------
    // 系统广播（死亡、进出服、进度……）
    // ------------------------------------------------------------------

    public static Component processSystem(Component original, @Nullable MessageKind forcedKind,
                                          @Nullable java.util.UUID uuid, @Nullable String name) {
        if (original == null || !ConfigManager.get().enabled) {
            return original;
        }
        try {
            MessageKind kind = forcedKind != null ? forcedKind : MessageKind.guess(original);
            Settings settings = ConfigManager.settingsFor(uuid, name);
            String raw = original.getString();
            if (raw.isEmpty()) {
                return original;
            }
            Outcome outcome = transform(raw, settings, kind);
            if (outcome.mode() == Outcome.Mode.UNCHANGED) {
                return original;
            }
            if (ConfigManager.get().debug) {
                Miao.LOGGER.info("[debug] {} ({}): {} -> {}", kind.id(), name, raw, outcome.text());
            }
            return switch (outcome.mode()) {
                case PREFIX -> Component.literal(outcome.insert()).append(original.copy());
                case SUFFIX -> original.copy().append(Component.literal(outcome.insert()));
                case SUPPRESS, FLATTEN -> rebuild(original, outcome.text());
                case UNCHANGED -> original;
            };
        } catch (Exception e) {
            Miao.LOGGER.warn("处理系统消息时出错，已跳过", e);
            return original;
        }
    }

    private static Component rebuild(Component original, String text) {
        MutableComponent rebuilt = Component.literal(text);
        if (!original.getStyle().isEmpty()) {
            rebuilt.setStyle(original.getStyle());
        }
        return rebuilt;
    }

    // ------------------------------------------------------------------
    // 文本层面的核心逻辑
    // ------------------------------------------------------------------

    public static Outcome transform(String raw, Settings settings, MessageKind kind) {
        MiaoConfig cfg = ConfigManager.get();
        String insert = pickInsert(settings);
        InsertPos position = settings.positionFor(kind);

        MarkerParser.Result markers = MarkerParser.parse(raw, cfg.marker, insert, position);

        // 1. 玩家明确要求本条不插语
        if (markers.suppress()) {
            return new Outcome(markers.text(), Outcome.Mode.SUPPRESS, "");
        }

        // 2. 玩家自带插入语：优先级高于服务器配置
        if (markers.hasMarker() && !markers.insertions().isEmpty()) {
            String text = Insertion.apply(markers.text(), markers.insertions(), settings.fallback());
            return new Outcome(text, Outcome.Mode.FLATTEN, "");
        }

        // 3. 玩家把标记写了但没生效（比如超出数量上限被剥离）
        String base = markers.text();
        if (!settings.enabled() || !settings.targets(kind)) {
            return base.equals(raw)
                    ? new Outcome(raw, Outcome.Mode.UNCHANGED, "")
                    : new Outcome(base, Outcome.Mode.SUPPRESS, "");
        }

        // 4. 服务器配置的插入语
        if (insert.isEmpty()) {
            return new Outcome(base, base.equals(raw) ? Outcome.Mode.UNCHANGED : Outcome.Mode.SUPPRESS, "");
        }
        if (position.isEdge() && base.equals(raw)) {
            return new Outcome(
                    position.kind() == InsertPos.Kind.PREFIX ? insert + raw : raw + insert,
                    position.kind() == InsertPos.Kind.PREFIX ? Outcome.Mode.PREFIX : Outcome.Mode.SUFFIX,
                    insert);
        }
        String text = Insertion.apply(base, List.of(new Insertion(insert, position)), settings.fallback());
        return new Outcome(text, text.equals(raw) ? Outcome.Mode.UNCHANGED : Outcome.Mode.FLATTEN, insert);
    }

    /** 供命令预览使用：只返回文本结果。 */
    public static String preview(String raw, Settings settings, MessageKind kind) {
        return transform(raw, settings, kind).text();
    }

    public static String pickInsert(Settings settings) {
        List<String> pool = settings.inserts();
        if (pool == null || pool.isEmpty()) {
            return "喵";
        }
        if (pool.size() == 1 || settings.pickMode() == Settings.PickMode.FIRST) {
            return pool.get(0);
        }
        return switch (settings.pickMode()) {
            case CYCLE -> {
                int idx;
                synchronized (COUNTERS) {
                    idx = COUNTERS.computeIfAbsent(settings, k -> new AtomicInteger()).getAndIncrement();
                }
                yield pool.get(Math.floorMod(idx, pool.size()));
            }
            default -> pool.get((int) (Math.random() * pool.size()));
        };
    }

    public static void resetCounters() {
        synchronized (COUNTERS) {
            COUNTERS.clear();
        }
    }
}
