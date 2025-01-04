package tronka.ordinarydiscordintegration.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

@Mixin(PlayerAdvancementTracker.class)
public class AdvancementMixin {
    @Shadow private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancement/PlayerAdvancementTracker;onStatusUpdate(Lnet/minecraft/advancement/AdvancementEntry;)V"))
    private void receiveAdvancement(AdvancementEntry advancementEntry, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        Advancement advancement = advancementEntry.value();

        if (advancement != null && advancement.display().isPresent() && advancement.display().get().shouldAnnounceToChat()) {
            OrdinaryDiscordIntegration.getInstance().getChatBridge().onReceiveAdvancement(this.owner, advancement.display().get());
        }
    }
}
