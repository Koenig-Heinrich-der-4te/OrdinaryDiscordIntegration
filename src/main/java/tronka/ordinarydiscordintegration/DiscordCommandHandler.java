package tronka.ordinarydiscordintegration;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import tronka.ordinarydiscordintegration.linking.PlayerData;
import tronka.ordinarydiscordintegration.linking.PlayerLink;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;


public class DiscordCommandHandler extends ListenerAdapter {
    OrdinaryDiscordIntegration integration;
    public DiscordCommandHandler(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        integration.getJda().updateCommands().queue();
        integration.getGuild().updateCommands()
                .addCommands(
                        Commands.slash("link", "Link your minecraft with the code you got when joining")
                                .addOption(OptionType.STRING, "code", "Link code", true),
                        Commands.slash("linking", "Misc linking stuff")
                                .addSubcommands(new SubcommandData("get", "Retrieve linking information")
                                                .addOption(OptionType.USER, "user", "whose data to get")
                                                .addOption(OptionType.STRING, "mc-name", "minecraft username to get data"),
                                        new SubcommandData("unlink", "Unlink your account")
                                                .addOption(OptionType.USER, "user", "user to unlink")
                                                .addOption(OptionType.STRING, "mc-name", "minecraft account to unlink")
                                        ),
                        Commands.slash("list", "List the currently online players"),
                        Commands.slash("reload", "Reload the config file")
                ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getMember().getGuild() != this.integration.getGuild()) { return; }
        switch (event.getName()) {
            case "link" -> {
                String code = event.getOptions().getFirst().getAsString();
                String message = this.integration.getLinkManager().confirmLink(event.getUser().getIdLong(), code);
                event.reply(message).setEphemeral(true).queue();
            }
            case "list" -> {
                Stream<String> names = this.integration.getServer().getPlayerManager()
                        .getPlayerList().stream()
                        .filter(player -> !this.integration.getVanishIntegration().isVanished(player))
                        .map(player -> player.getName().getLiteralString());
                String message = String.join(", ", names.toList());
                if (message.isEmpty()) {
                    message = "There are currently no players online";
                }
                event.reply(message).setEphemeral(true).queue();
            }
            case "linking" -> linkingCommand(event);
            case "reload" -> {
                if (!PermissionUtil.checkPermission(event.getMember(), Permission.ADMINISTRATOR)) {
                    event.reply("Insufficient permissions").setEphemeral(true).queue();
                    return;
                }
                String result = this.integration.tryReloadConfig();
                if (result.isEmpty()) {
                    result = "Reloaded config!";
                }
                event.reply(result).setEphemeral(true).queue();
            }
        }
    }

    private void linkingCommand(SlashCommandInteractionEvent event) {
        if (event.getMember() == null) {
            event.reply("Invalid Context").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("user", OptionMapping::getAsMember);
        String minecraftName = event.getOption("mc-name", OptionMapping::getAsString);

        if (minecraftName != null) {
            if (PermissionUtil.checkPermission(event.getMember(), Permission.MODERATE_MEMBERS)) {
                event.deferReply().setEphemeral(true).queue();
                GameProfile profile = Utils.fetchProfile(minecraftName);
                linkingWithPlayer(event.getSubcommandName(), event.getHook(), profile);
                return;
            } else {
                event.reply("Insufficient permissions").setEphemeral(true).queue();
            }
            return;
        } else if (target == null) {
            target = event.getMember();
        }
        boolean isSelf = event.getMember().equals(target);
        if (!isSelf && !PermissionUtil.checkPermission(event.getMember(), Permission.MODERATE_MEMBERS)) {
            event.reply("Insufficient permissions").setEphemeral(true).queue();
            return;
        }
        linkingWithMember(event, target);
    }

    // run with checked permissions
    private void linkingWithMember(SlashCommandInteractionEvent event, Member target) {
        Optional<PlayerLink> dataOptional = this.integration.getLinkManager().getDataOf(target.getIdLong());
        if (dataOptional.isEmpty()) {
            event.reply(target.getAsMention() + " is not linked to any player").setEphemeral(true).queue();
            return;
        }
        PlayerLink data = dataOptional.get();
        if (Objects.equals(event.getSubcommandName(), "get")) {
            event.deferReply().setEphemeral(true).queue();
            String text = target.getAsMention() + " is linked to " + data.getPlayerName();
            if (data.altCount() != 0) {
                text += "\nwith " + data.altCount() + " alts: " + String.join(", ", data.getAlts().stream().map(PlayerData::getName).toList());
            }
            event.getHook().editOriginal(text).queue();
        } else if (Objects.equals(event.getSubcommandName(), "unlink")) {
            Optional<PlayerLink> playerLink = this.integration.getLinkManager().getDataOf(target.getIdLong());
            if (playerLink.isPresent()) {
                MinecraftServer server = this.integration.getServer();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerLink.get().getPlayerId());
                if (player != null) {
                    player.networkHandler.disconnect(Text.of(this.integration.getConfig().kickMessages.kickUnlinked));
                }
                for (PlayerData alt : playerLink.get().getAlts()) {
                    ServerPlayerEntity altPlayer = server.getPlayerManager().getPlayer(alt.getId());
                    if (altPlayer != null) {
                        altPlayer.networkHandler.disconnect(Text.of(this.integration.getConfig().kickMessages.kickUnlinked));
                    }
                }
            }
            this.integration.getLinkManager().unlinkPlayer(target.getIdLong());
            event.reply("Successfully unlinked").setEphemeral(true).queue();
        }
    }

    // run with checked permissions
    private void linkingWithPlayer(String subCommand, InteractionHook hook, GameProfile profile) {
        if (profile == null) {
            hook.editOriginal("Could not find a player with that name").queue();
            return;
        }
        Optional<PlayerLink> data = this.integration.getLinkManager().getDataOf(profile.getId());
        if (data.isEmpty()) {
            hook.editOriginal("Could not find a linked account").queue();
            return;
        }
        if (Objects.equals(subCommand, "get")) {
            Optional<Member> member = this.integration.getLinkManager().getDiscordOf(data.get());
            if (member.isPresent()) {
                hook.editOriginal(profile.getName() + " is linked to " + member.get().getAsMention()).queue();
            } else {
                hook.editOriginal("Could not find a linked discord account").queue();
            }
        } else if (Objects.equals(subCommand, "unlink")) {
            this.integration.getLinkManager().unlinkPlayer(data.get());
            hook.editOriginal("Successfully unlinked").queue();
        }
    }
}
