package tronka.ordinarydiscordintegration;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

public class InGameDiscordCommand {

    private final OrdinaryDiscordIntegration integration;

    public InGameDiscordCommand(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        CommandRegistrationCallback.EVENT.register(this::register);
    }

    private void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("discord").then(CommandManager.literal("unlink").executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) {
                this.integration.getLinkManager().unlinkPlayer(player.getUuid());
                context.getSource().sendFeedback(() -> Text.literal("Unlinked!"), false);
                if (this.integration.getConfig().joining.enableLinking) {
                    player.networkHandler.disconnect(Text.literal(this.integration.getConfig().kickMessages.kickUnlinked));
                }
            } else {
                context.getSource().sendFeedback(() -> Text.literal("Player Only!"), false);
            }
            return 1;
        }).then(CommandManager.argument("player", GameProfileArgumentType.gameProfile()).requires(source -> source.hasPermissionLevel(2)).executes(context -> {
            Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
            int kickedCount = 0;
            for (GameProfile profile : profiles) {
                this.integration.getLinkManager().unlinkPlayer(profile.getId());
                ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(profile.getId());
                if (player != null) {
                    if (this.integration.getConfig().joining.enableLinking) {
                        player.networkHandler.disconnect(Text.literal(this.integration.getConfig().kickMessages.kickUnlinked));
                    }
                    kickedCount++;
                }
            }
            int finalKickedCount = kickedCount;
            context.getSource().sendFeedback(() -> Text.literal("Successfully unlinked %d player and kicked %d".formatted(profiles.size(), finalKickedCount)), false);
            return 1;
        }))).then(CommandManager.literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(context -> {
            String result = this.integration.tryReloadConfig();
            final String feedback = result.isEmpty() ? "Successfully reloaded config!" : result;
            context.getSource().sendFeedback(() -> Text.literal(feedback), result.isEmpty());
            return 1;
        })));
    }
}
