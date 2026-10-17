package a6a.dr.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

/**
 * 消息类别。既用于配置里的 targets 开关，也用于按类别指定插入位置。
 */
public enum MessageKind {
    /** 玩家主动发送的聊天（含 /me 表情）。 */
    CHAT("chat"),
    /** 死亡提示。 */
    DEATH("death"),
    /** 加入服务器提示。 */
    JOIN("join"),
    /** 离开服务器提示。 */
    QUIT("quit"),
    /** 进度 / 成就提示。 */
    ADVANCEMENT("advancement"),
    /** 其它全服广播（默认不处理）。 */
    OTHER("other");

    private final String id;

    MessageKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Nullable
    public static MessageKind byId(String id) {
        if (id == null) {
            return null;
        }
        for (MessageKind kind : values()) {
            if (kind.id.equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * 按翻译键推断类别，用于没有上下文标记时的兜底判断。
     */
    public static MessageKind guess(@Nullable Component component) {
        String key = translationKey(component);
        if (key == null) {
            return OTHER;
        }
        if (key.startsWith("death.")) {
            return DEATH;
        }
        if (key.startsWith("multiplayer.player.joined")) {
            return JOIN;
        }
        if (key.startsWith("multiplayer.player.left")) {
            return QUIT;
        }
        if (key.startsWith("chat.type.advancement.")) {
            return ADVANCEMENT;
        }
        if (key.equals("chat.type.text") || key.equals("chat.type.emote") || key.equals("chat.type.announcement")) {
            return CHAT;
        }
        return OTHER;
    }

    @Nullable
    public static String translationKey(@Nullable Component component) {
        if (component == null) {
            return null;
        }
        if (component.getContents() instanceof TranslatableContents tc) {
            return tc.getKey();
        }
        return null;
    }
}
