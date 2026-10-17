package a6a.dr.core;

import a6a.dr.config.MiaoConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 聊天内联标记解析。
 *
 * <p>玩家可以在聊天里直接"携带"插入语，语法与配置完全一致：</p>
 * <pre>
 *   你好[miao:喵]                 -> 你好喵            （用服务器给该玩家配置的位置）
 *   你好[miao:喵@after:你好]      -> 你好喵
 *   喵[miao:喵@prefix]你好        -> 喵喵你好
 *   你好[miao:off]                -> 你好              （本条不插语）
 *   你好[miao]                    -> 你好喵            （内容与位置都走服务器配置）
 * </pre>
 *
 * <p>只要消息里出现了有效标记，服务器就不会再追加自己的插入语——即客户端标记优先级更高。</p>
 */
public final class MarkerParser {

    /** 解析结果。 */
    public record Result(String text, List<Insertion> insertions, boolean suppress, int found) {
        public boolean hasMarker() {
            return found > 0;
        }
    }

    private MarkerParser() {
    }

    public static Result parse(String raw, MiaoConfig.Marker cfg, String defaultContent, InsertPos defaultPos) {
        if (raw == null || raw.isEmpty() || cfg == null || !cfg.enabled) {
            return new Result(raw, List.of(), false, 0);
        }
        String prefix = cfg.prefix == null || cfg.prefix.isBlank() ? "[miao:" : cfg.prefix;
        // 支持裸写法 [miao]：把前缀末尾的 ':' 去掉后作为"短前缀"再匹配一次
        String shortPrefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
        int maxPerMessage = Math.max(0, cfg.maxPerMessage);
        int maxLength = Math.max(1, cfg.maxLength);

        StringBuilder out = new StringBuilder(raw.length());
        List<Insertion> insertions = new ArrayList<>();
        boolean suppress = false;
        int found = 0;

        int i = 0;
        while (i < raw.length()) {
            int start = indexOfIgnoreCase(raw, prefix, i);
            int shortStart = indexOfIgnoreCase(raw, shortPrefix + "]", i);
            if (shortStart >= 0 && (start < 0 || shortStart < start)) {
                // 裸标记 [miao]
                out.append(raw, i, shortStart);
                int end = shortStart + shortPrefix.length() + 1;
                i = end;
                found++;
                if (found > maxPerMessage) {
                    if (!cfg.stripExcess) {
                        out.append(raw, shortStart, end);
                    }
                } else {
                    insertions.add(new Insertion(sanitize(defaultContent, maxLength), defaultPos));
                }
                continue;
            }
            if (start < 0) {
                out.append(raw, i, raw.length());
                break;
            }
            int close = raw.indexOf(']', start + prefix.length());
            if (close < 0) {
                out.append(raw, i, raw.length());
                break;
            }
            String body = raw.substring(start + prefix.length(), close);
            Parsed parsed = parseBody(body, cfg, defaultContent, defaultPos, maxLength);
            if (parsed == null) {
                // 非法标记：原样保留，不做任何处理
                out.append(raw, i, close + 1);
                i = close + 1;
                continue;
            }
            out.append(raw, i, start);
            i = close + 1;
            found++;
            if (parsed.suppress()) {
                suppress = true;
                continue;
            }
            if (found > maxPerMessage) {
                if (!cfg.stripExcess) {
                    out.append(raw, start, close + 1);
                }
                continue;
            }
            insertions.add(new Insertion(parsed.content(), parsed.pos()));
        }
        return new Result(out.toString(), insertions, suppress, found);
    }

    private record Parsed(String content, InsertPos pos, boolean suppress) {
    }

    @Nullable
    private static Parsed parseBody(String body, MiaoConfig.Marker cfg, String defaultContent,
                                    InsertPos defaultPos, int maxLength) {
        String trimmed = body.trim();
        if (cfg.allowDisable && isOffKeyword(trimmed)) {
            return new Parsed("", defaultPos, true);
        }
        String content = trimmed;
        String posPart = null;
        int at = trimmed.indexOf('@');
        if (at >= 0) {
            content = trimmed.substring(0, at).trim();
            posPart = trimmed.substring(at + 1).trim();
        }
        if (content.isEmpty()) {
            content = defaultContent;
        }
        if (content == null || content.isEmpty()) {
            return null;
        }
        // 内容里不允许再出现标记起始串，避免嵌套歧义
        if (cfg.prefix != null && !cfg.prefix.isBlank()
                && content.toLowerCase(Locale.ROOT).contains(cfg.prefix.toLowerCase(Locale.ROOT))) {
            return null;
        }
        content = sanitize(content, maxLength);
        if (content.isEmpty()) {
            return null;
        }
        InsertPos pos = defaultPos;
        if (posPart != null && !posPart.isEmpty() && cfg.allowPosition) {
            InsertPos parsed = InsertPos.parse(posPart);
            if (parsed != null) {
                pos = parsed;
            }
        }
        return new Parsed(content, pos, false);
    }

    private static boolean isOffKeyword(String s) {
        String v = s.toLowerCase(Locale.ROOT);
        return v.equals("off") || v.equals("no") || v.equals("!") || v.equals("none") || v.equals("无");
    }

    /** 去掉格式化符号与控制字符，并限制长度（按字符计）。 */
    public static String sanitize(String s, int maxLength) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (cp == '§' || cp == '\n' || cp == '\r' || cp == '\t') {
                return;
            }
            if (Character.isISOControl(cp)) {
                return;
            }
            sb.appendCodePoint(cp);
        });
        String cleaned = sb.toString().trim();
        if (cleaned.codePointCount(0, cleaned.length()) > maxLength) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, maxLength));
        }
        return cleaned;
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        if (needle.isEmpty()) {
            return -1;
        }
        int limit = haystack.length() - needle.length();
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }
}
