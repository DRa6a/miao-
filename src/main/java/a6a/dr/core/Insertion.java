package a6a.dr.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 一次插入：插入什么内容、插在哪里。
 */
public record Insertion(String content, InsertPos pos) {

    /**
     * 把若干插入动作应用到一段纯文本上。
     *
     * <p>所有插入点都基于<b>原始文本</b>计算，因此多个插入不会互相影响；
     * 同一位置的多个插入按出现顺序排列。</p>
     *
     * @param text      原始文本
     * @param insertions 插入动作
     * @param fallback  锚点找不到时的兜底位置，可为 {@code null}（视为末尾）
     */
    public static String apply(String text, List<Insertion> insertions, InsertPos fallback) {
        if (text == null || text.isEmpty() || insertions == null || insertions.isEmpty()) {
            return text;
        }
        InsertPos fb = fallback == null ? InsertPos.SUFFIX : fallback;
        List<Spot> spots = new ArrayList<>(insertions.size());
        for (int order = 0; order < insertions.size(); order++) {
            Insertion ins = insertions.get(order);
            if (ins == null || ins.content() == null || ins.content().isEmpty()) {
                continue;
            }
            int idx = ins.pos() == null ? -1 : ins.pos().resolve(text);
            if (idx < 0) {
                idx = fb.resolve(text);
            }
            if (idx < 0) {
                idx = text.length();
            }
            spots.add(new Spot(safe(text, idx), order, ins.content()));
        }
        if (spots.isEmpty()) {
            return text;
        }
        spots.sort(Comparator.comparingInt(Spot::index).thenComparingInt(Spot::order));

        StringBuilder sb = new StringBuilder(text.length() + 16);
        int cursor = 0;
        for (Spot spot : spots) {
            if (spot.index() > cursor) {
                sb.append(text, cursor, spot.index());
                cursor = spot.index();
            }
            sb.append(spot.content());
        }
        if (cursor < text.length()) {
            sb.append(text, cursor, text.length());
        }
        return sb.toString();
    }

    /** 避免把插入点切在代理对（emoji 等）中间。 */
    private static int safe(String text, int idx) {
        int len = text.length();
        if (idx <= 0) {
            return 0;
        }
        if (idx >= len) {
            return len;
        }
        if (Character.isLowSurrogate(text.charAt(idx)) && Character.isHighSurrogate(text.charAt(idx - 1))) {
            return idx - 1;
        }
        return idx;
    }

    private record Spot(int index, int order, String content) {
    }
}
