package a6a.dr.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组配置的数据模型（直接映射 config/miao.json）。
 *
 * <p>配置分为三层：全局开关 -> {@code defaults} 全局默认档 -> {@code players} 每玩家覆盖档。
 * 覆盖档中的字段为 {@code null} 时表示"沿用上一层"，因此玩家只需要写自己关心的几项。</p>
 */
public final class MiaoConfig {

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 全局总开关，关闭后本模组完全不介入任何消息。 */
    public boolean enabled = true;

    /** 调试日志：把每条被处理的消息、匹配到的位置打到控制台。 */
    public boolean debug = false;

    /** 配置文件被外部修改后是否自动热加载。 */
    public boolean autoReload = true;

    /** 全局默认档。 */
    public Profile defaults = Profile.defaults();

    /** 每玩家覆盖档，key 可以是玩家名，也可以是 UUID（UUID 优先匹配）。 */
    public Map<String, Profile> players = new LinkedHashMap<>();

    /** 聊天内联标记（客户端携带插入语）相关设置。 */
    public Marker marker = new Marker();

    /**
     * 一份"档位"：既可以作为全局默认值（字段全部填好），
     * 也可以作为玩家覆盖值（只填需要覆盖的字段）。
     */
    public static final class Profile {
        /** 是否对该玩家启用。 */
        public Boolean enabled;

        /** 插入语候选池，每条消息从中按 pickMode 取一个。 */
        public List<String> inserts;

        /** 取值方式：random（默认，随机）/ first（永远取第一个）/ cycle（轮流）。 */
        public String pickMode;

        /** 插入位置表达式，见 {@code InsertPos}。 */
        public String position;

        /** 位置表达式解析失败或锚点找不到时的兜底位置。 */
        public String fallback;

        /** 按消息类别单独指定插入位置，优先级高于 {@link #position}。 */
        public Map<String, String> categoryPositions;

        /** 按消息类别开关。 */
        public Map<String, Boolean> targets;

        public static Profile defaults() {
            Profile p = new Profile();
            p.enabled = Boolean.TRUE;
            p.inserts = new ArrayList<>(List.of("喵"));
            p.pickMode = "random";
            p.position = "suffix";
            p.fallback = "suffix";
            p.categoryPositions = new LinkedHashMap<>();
            p.targets = new LinkedHashMap<>();
            p.targets.put("chat", true);
            p.targets.put("death", true);
            p.targets.put("join", true);
            p.targets.put("quit", true);
            p.targets.put("advancement", true);
            p.targets.put("other", false);
            return p;
        }

        public static Profile empty() {
            return new Profile();
        }
    }

    /** 聊天内联标记设置。 */
    public static final class Marker {
        /** 是否解析并剥离内联标记。 */
        public boolean enabled = true;

        /** 标记起始串，大小写不敏感。 */
        public String prefix = "[miao:";

        /** 插入语内容的最大长度（按字符计）。 */
        public int maxLength = 32;

        /** 单条消息最多生效几个标记。 */
        public int maxPerMessage = 3;

        /** 是否允许标记自带位置表达式。 */
        public boolean allowPosition = true;

        /** 是否允许 {@code [miao:off]} 之类的"本条不插语"指令。 */
        public boolean allowDisable = true;

        /** 标记超出数量上限时，多余的标记是剥离（true）还是原样保留（false）。 */
        public boolean stripExcess = true;
    }

    /** 补齐缺失字段，避免手写配置漏项导致 NPE。 */
    public void normalize() {
        if (defaults == null) {
            defaults = Profile.defaults();
        }
        if (players == null) {
            players = new LinkedHashMap<>();
        }
        if (marker == null) {
            marker = new Marker();
        }
        normalize(defaults, true);
        players.replaceAll((k, v) -> {
            if (v == null) {
                v = Profile.empty();
            }
            normalize(v, false);
            return v;
        });
        players.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank());
    }

    private static void normalize(Profile p, boolean isDefault) {
        if (p.inserts != null) {
            p.inserts.removeIf(s -> s == null || s.isEmpty());
            if (p.inserts.isEmpty()) {
                p.inserts = null;
            }
        }
        if (isDefault && p.inserts == null) {
            p.inserts = new ArrayList<>(List.of("喵"));
        }
        if (p.categoryPositions == null) {
            p.categoryPositions = new LinkedHashMap<>();
        }
        if (p.targets == null) {
            p.targets = new LinkedHashMap<>();
        }
        if (isDefault) {
            for (String key : List.of("chat", "death", "join", "quit", "advancement")) {
                p.targets.putIfAbsent(key, Boolean.TRUE);
            }
            p.targets.putIfAbsent("other", Boolean.FALSE);
        }
    }
}
