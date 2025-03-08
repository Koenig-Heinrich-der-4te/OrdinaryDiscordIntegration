package tronka.justsync.mixin;

import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.message.LastSeenMessageList;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tronka.justsync.JustSyncApplication;

@Mixin(ServerPlayNetworkHandler.class)
public class NetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void onPlayerLeave(DisconnectionInfo info, CallbackInfo ci) {
        if (JustSyncApplication.getInstance().getVanishIntegration().isVanished(this.player)) {
            return;
        }
        if (!info.reason().toString().contains("disconnect.timeout")) {
            JustSyncApplication.getInstance().getChatBridge().onPlayerLeave(this.player);
        } else {
            JustSyncApplication.getInstance().getChatBridge().onPlayerTimeOut(this.player);
        }
    }

    @Inject(method = "getSignedMessage", at = @At("RETURN"))
    private void onMessageValidated(ChatMessageC2SPacket packet, LastSeenMessageList lastSeenMessages,
        CallbackInfoReturnable<SignedMessage> cir) {
        JustSyncApplication.getInstance().getChatBridge().onMcChatMessage(packet.chatMessage(), player);
    }

}
