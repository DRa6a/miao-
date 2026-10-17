package a6a.dr.core;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * 用栈记录"当前正在产生哪类消息、和哪个玩家有关"。
 *
 * <p>原版很多消息（死亡、进服、退服、进度）都是通过
 * {@code PlayerList#broadcastSystemMessage} 统一广播的，光看 Component 无法可靠地
 * 反推出关联玩家，所以在源头打一个上下文标记，广播时再取出来用。</p>
 */
public final class MiaoContext {

    public record Frame(MessageKind kind, @Nullable UUID uuid, @Nullable String name) {
    }

    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private MiaoContext() {
    }

    public static void push(MessageKind kind, @Nullable ServerPlayer player) {
        push(kind, player == null ? null : player.getUUID(), player == null ? null : player.getScoreboardName());
    }

    public static void push(MessageKind kind, @Nullable UUID uuid, @Nullable String name) {
        Deque<Frame> stack = STACK.get();
        // 极端情况下（异常导致 pop 没执行）别让栈无限增长
        if (stack.size() > 32) {
            stack.clear();
        }
        stack.push(new Frame(kind, uuid, name));
    }

    public static void pop() {
        Deque<Frame> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    @Nullable
    public static Frame peek() {
        Deque<Frame> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /** 保险：异常路径下把栈清干净。 */
    public static void clear() {
        STACK.remove();
    }
}
