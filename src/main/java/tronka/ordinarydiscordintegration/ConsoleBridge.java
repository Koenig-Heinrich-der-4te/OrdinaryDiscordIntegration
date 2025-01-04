package tronka.ordinarydiscordintegration;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.minecraft.server.command.ServerCommandSource;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConsoleBridge extends ListenerAdapter {
    private final OrdinaryDiscordIntegration integration;
    private TextChannel channel;
    private Role opRole;
    private List<LogRedirect> logRedirects;
    public ConsoleBridge(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        integration.registerConfigReloadHandler(this::onConfigLoaded);
    }

    private void onConfigLoaded(Config config) {
        this.channel = Utils.getTextChannel(this.integration.getJda(), config.commands.consoleChannel);
        String opRoleId = this.integration.getConfig().commands.opRole;
        if (this.channel != null && !opRoleId.isEmpty()) {
            this.opRole = this.channel.getGuild().getRoleById(opRoleId);
        } else {
            this.opRole = null;
        }
        this.logRedirects = new ArrayList<>();
        for (Config.LogRedirectChannel logRedirectChannel : config.commands.logRedirectChannels) {
            TextChannel channel = Utils.getTextChannel(this.integration.getJda(), logRedirectChannel.channel);
            if (channel != null) {
                this.logRedirects.add(new LogRedirect(channel, logRedirectChannel.redirectPrefixes));
            } else {
                LogUtils.getLogger().info("Could not load log redirect: ID: \"{}\", redirects: [{}]", logRedirectChannel.channel, String.join(", ", logRedirectChannel.redirectPrefixes));
            }
        }
    }

    public void onCommandExecute(ServerCommandSource source, String command){
        if (this.channel == null) {
            return;
        }
        if (!this.integration.getConfig().commands.logCommandsInConsole) {
            return;
        }

        if (source.getEntity() == null && !source.getName().equals("Server") && !this.integration.getConfig().commands.logCommandBlockCommands) {
            return;
        }

        if (Utils.startsWithAny(command, this.integration.getConfig().commands.ignoredCommands)) {
            return;
        }
        TextChannel target = this.channel;
        for (LogRedirect redirect : this.logRedirects) {
            if (Utils.startsWithAny(command, redirect.prefixes)) {
                target = redirect.channel;
                break;
            }
        }
        target.sendMessage(
                this.integration.getConfig().messages.commandExecutedInfoText
                        .replace("%user%", source.getName())
                        .replace("%msg%", command)
        ).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel() != this.channel || event.getMember() == null) {
            return;
        }

        String message = event.getMessage().getContentStripped();
        if (!message.startsWith(this.integration.getConfig().commands.commandPrefix)) {
            return;
        }

        if (!PermissionUtil.checkPermission(event.getMember(), Permission.ADMINISTRATOR) && !event.getMember().getRoles().contains(this.opRole)) {
            event.getChannel().sendMessage("You don't have permission to use this command").queue();
            return;
        }
        message = message.substring(this.integration.getConfig().commands.commandPrefix.length());
        if (message.equals("help")) {
            EmbedBuilder embed = new EmbedBuilder();
            this.integration.getConfig().commands.commands.forEach(command -> embed.addField(command.commandName, command.inGameAction.replace("%args%", "<args>"), true));
            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }
        String[] commandParts = message.split(" ", 2);
        String commandName = commandParts[0].toLowerCase();
        String commandArgs = commandParts.length == 2 ? commandParts[1] : "";
        Optional<Config.BridgeCommand> commandOptional = this.integration.getConfig().commands.commands.stream().filter(cmd -> cmd.commandName.equals(commandName)).findFirst();
        if (commandOptional.isPresent()) {
            Config.BridgeCommand command = commandOptional.get();
            DiscordCommandSender commandSender = new DiscordCommandSender(this.integration.getServer(), event.getAuthor().getAsMention(), feedback -> {
                if (feedback.length() > 2000) {
                    feedback = feedback.substring(0, 2000);
                }
                event.getChannel().sendMessage(feedback).queue();
            });
            String inGameCommand = command.inGameAction.replace("%args%", commandArgs);
            try {
                this.integration.getServer().getCommandManager().getDispatcher().execute(inGameCommand, commandSender);
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            event.getChannel().sendMessage("Unknown command: \"" + commandName + "\"").queue();
        }
    }

    private record LogRedirect(TextChannel channel, List<String> prefixes) {

    }
}
