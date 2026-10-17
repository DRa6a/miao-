package a6a.dr.mixin;

import a6a.dr.core.MessageKind;
import a6a.dr.core.MessageProcessor;
import a6a.dr.core.MiaoContext;
import net.minecraft.network.Connection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截服务端广播出口。
 *
 * <ul>
 *   <li>聊天：改写 {@link PlayerChatMessage} 的无签名内容（保留聊天栏排版与签名校验）</li>
 *   <li>系统消息：改写 {@link Component}</li>
 *   <li>进服：在 {@code placeNewPlayer} 期间打上 JOIN 上下文</li>
 * </ul>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @ModifyVariable(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private PlayerChatMessage miao$modifyChat(PlayerChatMessage message, PlayerChatMessage original,
                                              ServerPlayer sender, ChatType.Bound chatType) {
        return MessageProcessor.processChat(message, sender);
    }

    @ModifyVariable(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private PlayerChatMessage miao$modifyCommandChat(PlayerChatMessage message, PlayerChatMessage original,
                                                     CommandSourceStack source, ChatType.Bound chatType) {
        return MessageProcessor.processCommandChat(message, source);
    }

    @ModifyVariable(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private Component miao$modifySystemMessage(Component message) {
        MiaoContext.Frame frame = MiaoContext.peek();
        return MessageProcessor.processSystem(
                message,
                frame == null ? null : frame.kind(),
                frame == null ? null : frame.uuid(),
                frame == null ? null : frame.name());
    }

    @ModifyVariable(
            method = "broadcastSystemToTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2)
    private Component miao$modifyTeamMessage(Component message, Player player, Component original) {
        MiaoContext.Frame frame = MiaoContext.peek();
        MessageKind kind = frame != null ? frame.kind() : MessageKind.DEATH;
        return MessageProcessor.processSystem(message, kind, player.getUUID(), player.getScoreboardName());
    }

    @ModifyVariable(
            method = "broadcastSystemToAllExceptTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2)
    private Component miao$modifyExceptTeamMessage(Component message, Player player, Component original) {
        MiaoContext.Frame frame = MiaoContext.peek();
        MessageKind kind = frame != null ? frame.kind() : MessageKind.DEATH;
        return MessageProcessor.processSystem(message, kind, player.getUUID(), player.getScoreboardName());
    }

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void miao$beforeJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        MiaoContext.push(MessageKind.JOIN, player);
    }

    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void miao$afterJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        MiaoContext.pop();
    }
}
