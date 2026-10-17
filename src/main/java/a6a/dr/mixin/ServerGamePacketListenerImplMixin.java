package a6a.dr.mixin;

import a6a.dr.core.MessageKind;
import a6a.dr.core.MiaoContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 退服提示的上下文标记。原版在 {@code removePlayerFromWorld} 里广播"xxx 离开了游戏"。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "removePlayerFromWorld", at = @At("HEAD"))
    private void miao$beforeQuit(CallbackInfo ci) {
        MiaoContext.push(MessageKind.QUIT, this.player);
    }

    @Inject(method = "removePlayerFromWorld", at = @At("RETURN"))
    private void miao$afterQuit(CallbackInfo ci) {
        MiaoContext.pop();
    }
}
