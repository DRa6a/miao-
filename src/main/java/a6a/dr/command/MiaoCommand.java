package a6a.dr.command;

import a6a.dr.config.ConfigManager;
import a6a.dr.config.ConfigWatcher;
import a6a.dr.config.MiaoConfig;
import a6a.dr.core.InsertPos;
import a6a.dr.core.MarkerParser;
import a6a.dr.core.MessageKind;
import a6a.dr.core.MessageProcessor;
import a6a.dr.core.Settings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /miao} 管理命令。
 */
public final class MiaoCommand {

    private static final SuggestionProvider<CommandSourceStack> PLAYER_KEYS = (ctx, builder) -> {
        for (String key : ConfigManager.get().players.keySet()) {
            builder.suggest(key);
        }
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            builder.suggest(player.getScoreboardName());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> POSITIONS = (ctx, builder) -> {
        builder.suggest("suffix");
        builder.suggest("prefix");
        builder.suggest("index:-1");
        builder.suggest("percent:50");
        builder.suggest("after:。");
        builder.suggest("before:！");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> KINDS = (ctx, builder) -> {
        for (MessageKind kind : MessageKind.values()) {
            builder.suggest(kind.id());
        }
        return builder.buildFuture();
    };

    private MiaoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("miao")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("test")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> test(ctx))))
                .then(Commands.literal("position")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .then(Commands.argument("expr", StringArgumentType.greedyString())
                                        .suggests(POSITIONS)
                                        .executes(ctx -> setPosition(ctx)))))
                .then(Commands.literal("insert")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> setInsert(ctx, true)))))
                .then(Commands.literal("insertadd")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> setInsert(ctx, false)))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .executes(ctx -> setEnabled(ctx, true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .executes(ctx -> setEnabled(ctx, false))))
                .then(Commands.literal("target")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests(KINDS)
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(ctx -> setTarget(ctx))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_KEYS)
                                .executes(ctx -> reset(ctx.getSource(), StringArgumentType.getString(ctx, "player"))))));
    }

    // ------------------------------------------------------------------

    private static int status(CommandSourceStack source) {
        MiaoConfig cfg = ConfigManager.get();
        source.sendSuccess(() -> Component.literal(
                "喵~ 模组状态：" + (cfg.enabled ? "已启用" : "已禁用")
                        + " | 默认插入语 " + cfg.defaults.inserts
                        + " | 位置 " + cfg.defaults.position
                        + " | 单独配置的玩家 " + cfg.players.size() + " 人"), false);
        source.sendSuccess(() -> Component.literal(
                "子命令：reload / info <玩家> / test <文本> / position / insert / insertadd / enable / disable / target / reset"), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        boolean ok = ConfigManager.load();
        ConfigWatcher.syncBaseline();
        MessageProcessor.resetCounters();
        if (!ok) {
            source.sendFailure(Component.literal("配置读取失败，已保留上一份配置，详见控制台"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("喵~ 配置已重新加载"), true);
        return 1;
    }

    private static int info(CommandSourceStack source, @Nullable String player) {
        String name = player;
        String uuid = null;
        if (name == null) {
            ServerPlayer self = source.getPlayer();
            if (self == null) {
                source.sendFailure(Component.literal("请指定玩家名"));
                return 0;
            }
            name = self.getScoreboardName();
            uuid = self.getUUID().toString();
        } else {
            ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
            if (online != null) {
                name = online.getScoreboardName();
                uuid = online.getUUID().toString();
            }
        }
        Settings settings = ConfigManager.settingsFor(uuid == null ? null : java.util.UUID.fromString(uuid), name);
        String who = name;
        source.sendSuccess(() -> Component.literal("【" + who + "】生效配置"), false);
        source.sendSuccess(() -> Component.literal(
                "  启用：" + settings.enabled()
                        + "  位置：" + settings.position()
                        + "  兜底：" + settings.fallback()
                        + "  取词：" + settings.pickMode().name().toLowerCase()), false);
        source.sendSuccess(() -> Component.literal("  插入语：" + String.join(" / ", settings.inserts())), false);
        List<String> kinds = new ArrayList<>();
        for (MessageKind kind : MessageKind.values()) {
            InsertPos pos = settings.categoryPositions().get(kind);
            kinds.add(kind.id() + (settings.targets(kind) ? "✓" : "✗") + (pos != null ? "(" + pos + ")" : ""));
        }
        source.sendSuccess(() -> Component.literal("  类别：" + String.join("  ", kinds)), false);
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> ctx) {
        String text = StringArgumentType.getString(ctx, "text");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer self = source.getPlayer();
        Settings settings = self != null
                ? ConfigManager.settingsFor(self.getUUID(), self.getScoreboardName())
                : Settings.resolve(ConfigManager.get(), null, null);
        String result = MessageProcessor.preview(text, settings, MessageKind.CHAT);
        source.sendSuccess(() -> Component.literal("原文：" + text), false);
        source.sendSuccess(() -> Component.literal("结果：" + result), false);
        return 1;
    }

    private static int setPosition(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        String expr = StringArgumentType.getString(ctx, "expr").trim();
        InsertPos pos = InsertPos.parse(expr);
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("无法解析的位置表达式：" + expr));
            return 0;
        }
        ConfigManager.profileOf(player).position = pos.toString();
        ConfigManager.touch();
        ctx.getSource().sendSuccess(() -> Component.literal("喵~ 已把 " + player + " 的插入位置设为 " + pos), true);
        return 1;
    }

    private static int setInsert(CommandContext<CommandSourceStack> ctx, boolean replace) {
        String player = StringArgumentType.getString(ctx, "player");
        String text = StringArgumentType.getString(ctx, "text");
        String cleaned = MarkerParser.sanitize(text, 64);
        if (cleaned.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("插入语不能为空"));
            return 0;
        }
        MiaoConfig.Profile profile = ConfigManager.profileOf(player);
        if (replace || profile.inserts == null) {
            profile.inserts = new ArrayList<>(List.of(cleaned));
        } else {
            List<String> list = new ArrayList<>(profile.inserts);
            list.add(cleaned);
            profile.inserts = list;
        }
        ConfigManager.touch();
        ctx.getSource().sendSuccess(() -> Component.literal("喵~ " + player + " 的插入语：" + profile.inserts), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String player = StringArgumentType.getString(ctx, "player");
        ConfigManager.profileOf(player).enabled = enabled;
        ConfigManager.touch();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "喵~ " + player + (enabled ? " 已启用" : " 已禁用") + " 插入语"), true);
        return 1;
    }

    private static int setTarget(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        String kindId = StringArgumentType.getString(ctx, "kind");
        String value = StringArgumentType.getString(ctx, "value");
        MessageKind kind = MessageKind.byId(kindId);
        if (kind == null) {
            ctx.getSource().sendFailure(Component.literal("未知类别：" + kindId));
            return 0;
        }
        boolean on = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1");
        MiaoConfig.Profile profile = ConfigManager.profileOf(player);
        if (profile.targets == null) {
            profile.targets = new java.util.LinkedHashMap<>();
        }
        profile.targets.put(kind.id(), on);
        ConfigManager.touch();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "喵~ " + player + " 的 " + kind.id() + " 类别：" + (on ? "开" : "关")), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, String player) {
        MiaoConfig.Profile removed = ConfigManager.get().players.remove(player);
        if (removed == null) {
            for (String key : new ArrayList<>(ConfigManager.get().players.keySet())) {
                if (key.equalsIgnoreCase(player)) {
                    removed = ConfigManager.get().players.remove(key);
                    break;
                }
            }
        }
        ConfigManager.touch();
        if (removed == null) {
            source.sendFailure(Component.literal("没有找到 " + player + " 的单独配置"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("喵~ 已清除 " + player + " 的单独配置，回到全局默认"), true);
        return 1;
    }
}
