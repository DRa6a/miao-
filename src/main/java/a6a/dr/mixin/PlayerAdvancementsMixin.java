package a6a.dr.mixin;

import a6a.dr.core.MessageKind;
import a6a.dr.core.MiaoContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进度/成就提示的上下文标记。
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"))
    private void miao$beforeAward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        MiaoContext.push(MessageKind.ADVANCEMENT, this.player);
    }

    @Inject(method = "award", at = @At("RETURN"))
    private void miao$afterAward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        MiaoContext.pop();
    }
}
