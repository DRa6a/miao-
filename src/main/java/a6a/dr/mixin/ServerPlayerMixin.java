package a6a.dr.mixin;

import a6a.dr.core.MessageKind;
import a6a.dr.core.MiaoContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 死亡提示的上下文标记：原版在 {@code die} 内部广播死亡消息，
 * 这里把"是谁死了"记下来，供广播出口按玩家配置处理。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void miao$beforeDie(DamageSource source, CallbackInfo ci) {
        MiaoContext.push(MessageKind.DEATH, (ServerPlayer) (Object) this);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void miao$afterDie(DamageSource source, CallbackInfo ci) {
        MiaoContext.pop();
    }
}
