package a6a.dr.core;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 插入位置表达式。
 *
 * <p>配置与聊天内联标记共用同一套语法（大小写不敏感）：</p>
 * <pre>
 *   suffix            末尾（默认）        "你好喵"
 *   prefix            开头                "喵你好"
 *   index:N           第 N 个字符处（0 起，负数从末尾算，-1 表示末尾）
 *   percent:P         按比例插入，P 为 0~100 的数字
 *   after:文本        在"文本"第一次出现之后
 *   afterLast:文本    在"文本"最后一次出现之后
 *   before:文本       在"文本"第一次出现之前
 *   beforeLast:文本   在"文本"最后一次出现之前
 * </pre>
 */
public record InsertPos(Kind kind, int index, double percent, String anchor) {

    public enum Kind {
        SUFFIX, PREFIX, INDEX, PERCENT, AFTER, AFTER_LAST, BEFORE, BEFORE_LAST
    }

    public static final InsertPos SUFFIX = new InsertPos(Kind.SUFFIX, 0, 0.0D, "");
    public static final InsertPos PREFIX = new InsertPos(Kind.PREFIX, 0, 0.0D, "");

    /**
     * 解析表达式；无法解析时返回 {@code null}。
     */
    @Nullable
    public static InsertPos parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "suffix", "end", "tail", "后", "末尾" -> {
                return SUFFIX;
            }
            case "prefix", "start", "head", "前", "开头" -> {
                return PREFIX;
            }
            default -> {
                // fall through
            }
        }
        // 注意顺序：先匹配更长的关键字，避免 afterLast: 被 after: 抢走
        if (lower.startsWith("afterlast:") || lower.startsWith("after_last:")) {
            return new InsertPos(Kind.AFTER_LAST, 0, 0.0D, cut(s, s.indexOf(':') + 1));
        }
        if (lower.startsWith("beforelast:") || lower.startsWith("before_last:")) {
            return new InsertPos(Kind.BEFORE_LAST, 0, 0.0D, cut(s, s.indexOf(':') + 1));
        }
        if (lower.startsWith("after:")) {
            return new InsertPos(Kind.AFTER, 0, 0.0D, cut(s, 6));
        }
        if (lower.startsWith("before:")) {
            return new InsertPos(Kind.BEFORE, 0, 0.0D, cut(s, 7));
        }
        if (lower.startsWith("index:")) {
            try {
                return new InsertPos(Kind.INDEX, Integer.parseInt(cut(s, 6).trim()), 0.0D, "");
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (lower.startsWith("percent:") || lower.startsWith("pct:")) {
            String value = cut(s, s.indexOf(':') + 1).trim();
            if (value.endsWith("%")) {
                value = value.substring(0, value.length() - 1).trim();
            }
            try {
                double p = Double.parseDouble(value);
                return new InsertPos(Kind.PERCENT, 0, Math.max(0.0D, Math.min(100.0D, p)), "");
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (lower.endsWith("%")) {
            try {
                double p = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return new InsertPos(Kind.PERCENT, 0, Math.max(0.0D, Math.min(100.0D, p)), "");
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        // 纯数字：等价于 index:N
        try {
            return new InsertPos(Kind.INDEX, Integer.parseInt(s), 0.0D, "");
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String cut(String s, int from) {
        return from >= s.length() ? "" : s.substring(from);
    }

    /**
     * 计算插入点（字符下标）。锚点找不到时返回 -1。
     */
    public int resolve(String text) {
        int len = text.length();
        return switch (kind) {
            case SUFFIX -> len;
            case PREFIX -> 0;
            case INDEX -> {
                int i = index;
                if (i < 0) {
                    i = len + i + 1;
                }
                yield clamp(i, len);
            }
            case PERCENT -> clamp((int) Math.round(len * percent / 100.0D), len);
            case AFTER -> {
                int i = anchor.isEmpty() ? -1 : text.indexOf(anchor);
                yield i < 0 ? -1 : i + anchor.length();
            }
            case AFTER_LAST -> {
                int i = anchor.isEmpty() ? -1 : text.lastIndexOf(anchor);
                yield i < 0 ? -1 : i + anchor.length();
            }
            case BEFORE -> anchor.isEmpty() ? -1 : text.indexOf(anchor);
            case BEFORE_LAST -> anchor.isEmpty() ? -1 : text.lastIndexOf(anchor);
        };
    }

    private static int clamp(int i, int len) {
        return Math.max(0, Math.min(len, i));
    }

    /** 是否可以在不破坏翻译的前提下直接以"追加兄弟节点"的方式实现。 */
    public boolean isEdge() {
        return kind == Kind.SUFFIX || kind == Kind.PREFIX;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case SUFFIX -> "suffix";
            case PREFIX -> "prefix";
            case INDEX -> "index:" + index;
            case PERCENT -> "percent:" + percent;
            case AFTER -> "after:" + anchor;
            case AFTER_LAST -> "afterLast:" + anchor;
            case BEFORE -> "before:" + anchor;
            case BEFORE_LAST -> "beforeLast:" + anchor;
        };
    }
}
