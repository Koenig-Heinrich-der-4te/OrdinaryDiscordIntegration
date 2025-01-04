package tronka.ordinarydiscordintegration.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import net.fabricmc.loader.api.FabricLoader;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public String botToken = "";
    public String serverChatChannel = "";
    public boolean useWebHooks = true;

    public String avatarUrl = "https://minotar.net/avatar/%UUID%?randomuuid=%randomUUID%";

    public boolean unlinkOnLeave = true;
    public int maxAlts = 1;

    public JoinOptions joining = new JoinOptions();

    public boolean stackMessages = false;
    public int stackMessagesTimeoutInSec = 60;

    public boolean broadCastDeathMessages = true;
    public boolean announceAdvancements = true;

    public boolean showPlayerCountStatus = true;

    public static class JoinOptions {
        public boolean enableLinking = true;
        public long linkCodeExpireMinutes = 10;
        public List<String> requiredRoles = new ArrayList<>();
        public List<String> joinRoles = new ArrayList<>();
        public boolean disallowTimeoutMembersToJoin = true;
        public boolean renameOnJoin = true;
    }

    public ErrorStrings kickMessages = new ErrorStrings();

    public static class ErrorStrings {
        public String kickMissingRoles = "You currently don't have the permission to join the server.";
        public String kickLinkCode = "Please Link your discord account by using\n/link %s\non discord";
        public String kickUnlinked = "Your account has been unlinked, to rejoin the server please relink your account.\nIf you don't know why this happened, please ask an administrator";
        public String kickTimedOut = "You are timed out and currently can't join this server. Please retry when your discord timeout is over.";
        public String kickOnTimeOut = "You have been timed out on discord. You can rejoin after it is over.";
        public String kickOnLeave = "Your associated discord account has left the discord server.";
    }

    public MessageStrings messages = new MessageStrings();

    public static class MessageStrings {
        public int chatMessageLinkColor = 0x1a0dab;
        public String chatMessageFormat = "[<blue>Discord</blue>] <%user%> %msg% %attachments%";
        public String chatMessageFormatReply = "[<blue>Discord</blue>] <%user% replied to %userRepliedTo%> %msg% %attachments%";
        public String linkFormat = "<blue><underline><i><url:'%link%'>%link%</url></i></underline></blue>";
        public String attachmentFormat = "[<blue><url:'%link%'>%name%</url></blue>]";
        public String commandExecutedInfoText = "%user% executed ``%msg%``";
        public String playerJoinMessage = "%user% joined";
        public String playerLeaveMessage = "%user% left";
        public String advancementMessage = "%user% just made the advancement **%title%**\n*%description%*";
        public String startMessage = "Server started";
        public String stopMessage = "Server stopped";
        public String onlineCountPlural = "%d players online";
        public String onlineCountSingular = "1 player online";
        public String onlineCountZero = "Server is lonely :(";
    }

    public DiscordLinkResults linkResults = new DiscordLinkResults();

    public static class DiscordLinkResults {
        public String linkNotAllowed = "You are currently missing the required roles to link your account.";
        public String linkSuccess = "Successfully linked to %name%";
        public String failedUnknownCode = "Unknown code, did you copy it correctly?";
        public String failedTooManyLinked = "You cannot link to another account";
    }

    public ExternalIntegrations integrations = new ExternalIntegrations();

    public static class ExternalIntegrations {
        public boolean enableVanishIntegration = true;
        public boolean enableLuckPermsIntegration = true;
        public LuckPermsIntegration luckPerms = new LuckPermsIntegration();

        public static class LuckPermsIntegration {
            public List<String> altGroups = new ArrayList<>();
        }
    }

    public CommandSettings commands = new CommandSettings();

    public static class CommandSettings {
        public String consoleChannel = "";
        public boolean logCommandsInConsole = true;
        public boolean logCommandBlockCommands = false;
        public List<String> ignoredCommands = new ArrayList<>();
        public String commandPrefix = "//";
        public String opRole = "";
        public List<LogRedirectChannel> logRedirectChannels = List.of(LogRedirectChannel.of("", List.of("w", "msg", "tell")));

        public List<BridgeCommand> commands = List.of(
            BridgeCommand.of("kick", "kick %args%"),
            BridgeCommand.of("stop", "stop"),
            BridgeCommand.of("kill", "kill %args%"),
            BridgeCommand.of("ban", "ban %args%")
        );
    }

    public static class LogRedirectChannel {
        public String channel;
        public List<String> redirectPrefixes = new ArrayList<>();
        public static LogRedirectChannel of(String channel, List<String> prefixes) {
            LogRedirectChannel obj = new LogRedirectChannel();
            obj.channel = channel;
            obj.redirectPrefixes = prefixes;
            return obj;
        }
    }

    public static class BridgeCommand {
        public String commandName = "";
        public String inGameAction = "";
        public static BridgeCommand of(String name, String action) {
            BridgeCommand obj = new BridgeCommand();
            obj.commandName = name;
            obj.inGameAction = action;
            return obj;
        }
    }

    public static Config loadConfig() {
        Path configDir = OrdinaryDiscordIntegration.getConfigFolder();
        File configFile = configDir.resolve(OrdinaryDiscordIntegration.ModId + ".toml").toFile();
        if (configFile.exists()){
            return new Toml().read(configFile).to(Config.class);
        } else {
            Config config = new Config();
            try {
                new TomlWriter().write(config, configFile);
            } catch (IOException ignored) { }
            return config;
        }

    }

}
