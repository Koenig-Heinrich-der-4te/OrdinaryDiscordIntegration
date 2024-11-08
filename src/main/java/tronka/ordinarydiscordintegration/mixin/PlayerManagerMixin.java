package tronka.ordinarydiscordintegration.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tronka.ordinarydiscordintegration.LinkManager;

import java.net.SocketAddress;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        if (LinkManager.canJoin(profile.getId())) { return; }

        cir.setReturnValue(Text.of(LinkManager.getJoinError(profile.getId())));
    }
}
