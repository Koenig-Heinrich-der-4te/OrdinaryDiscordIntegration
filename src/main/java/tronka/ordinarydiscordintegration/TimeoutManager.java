package tronka.ordinarydiscordintegration;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import tronka.ordinarydiscordintegration.linking.PlayerData;
import tronka.ordinarydiscordintegration.linking.PlayerLink;

import java.util.Optional;

public class TimeoutManager extends ListenerAdapter {

    private final OrdinaryDiscordIntegration integration;

    public TimeoutManager(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        Member member = event.getMember();
        Optional<PlayerLink> playerLink = integration.getLinkManager().getDataOf(member.getIdLong());
        if (playerLink.isEmpty()) {
            return;
        }
        MinecraftServer server = integration.getServer();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerLink.get().getPlayerId());
        if (player != null) {
            player.networkHandler.disconnect(Text.of(integration.getConfig().kickMessages.kickOnTimeOut));
        }

        for (PlayerData alt : playerLink.get().getAlts()) {
            ServerPlayerEntity altPlayer = server.getPlayerManager().getPlayer(alt.getId());
            if (altPlayer != null) {
                altPlayer.networkHandler.disconnect(Text.of(integration.getConfig().kickMessages.kickOnTimeOut));
            }
        }
    }
}
