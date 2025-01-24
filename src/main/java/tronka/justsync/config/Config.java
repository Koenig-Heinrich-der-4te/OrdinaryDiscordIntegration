package tronka.justsync.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlComment;
import com.moandjiezana.toml.TomlWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tronka.justsync.JustSyncApplication;

public class Config {

    @TomlComment("Discord bot token, required to connect to discord")
    public String botToken = "";
    @TomlComment("Channel to sync with the minecraft chat (required)")
    public String serverChatChannel = "";
    @TomlComment("Whether a webhook should be used to send chat messages to discord")
    public boolean useWebHooks = true;
    @TomlComment("Url to use for the pfp of chat messages sent through the webhook")
    public String avatarUrl = "https://minotar.net/avatar/%UUID%?randomuuid=%randomUUID%";


    @TomlComment("When the same message is sent multiple times only one message will be sent to discord")
    public boolean stackMessages = false;
    @TomlComment("After how many seconds of no messages, a new message should be sent")
    public int stackMessagesTimeoutInSec = 60;

    @TomlComment("Format shared waypoints")
    public boolean formatWaypoints = true;
    @TomlComment({"Base URL of online map (this will add a hyperlink for overworld waypoints)",
                    "keep empty if none, example formatting:",
                    "https://map.example.com/#world:%x%:%y%:%z%:500:0:0:0:0:perspective",
                    "Placeholders: ",
                    "%x%, %y%, %z%: coordinates"})
    public String waypointURL = "";

    @TomlComment("Send death messages to discord")
    public boolean broadCastDeathMessages = true;
    @TomlComment("Send advancement notifications to discord")
    public boolean announceAdvancements = true;

    @TomlComment("Show the online player count as the bots status")
    public boolean showPlayerCountStatus = true;

    public MessageStrings messages = new MessageStrings();
    public LinkingOptions linking = new LinkingOptions();
    public DiscordLinkResults linkResults = new DiscordLinkResults();
    public ErrorStrings kickMessages = new ErrorStrings();
    public CommandSettings commands = new CommandSettings();
    public ExternalIntegrations integrations = new ExternalIntegrations();

    public static Config loadConfig() {
        Path configDir = JustSyncApplication.getConfigFolder();
        File configFile = configDir.resolve(JustSyncApplication.ModId + ".toml").toFile();
        Config instance;
        if (configFile.exists()) {
            instance = new Toml().read(configFile).to(Config.class);
        } else {
            instance = new Config();
        }
        try {
            Files.createDirectories(configDir);
            new TomlWriter().write(instance, configFile);
        } catch (IOException ignored) {
        }
        return instance;
    }

    public static class LinkingOptions {

        @TomlComment("Should players be required to link to a discord account")
        public boolean enableLinking = true;
        @TomlComment("Should players be unlinked from their discord account if they leave the discord server")
        public boolean unlinkOnLeave = true;
        @TomlComment("Should linking and unlinking be logged to a discord channel")
        public boolean logLinking = false;
        @TomlComment("channel in which linking/unlinking should be logged (only if logLinking set to true)")
        public String linkingLogChannel = "";
        @TomlComment("What roles a player must have on discord to join the minecraft server")
        public List<String> requiredRoles = new ArrayList<>();
        @TomlComment("What roles should be assigned to a players discord account when they join")
        public List<String> joinRoles = new ArrayList<>();
        @TomlComment("Players discord nickname will be set to their ingame name")
        public boolean renameOnJoin = true;
        @TomlComment("Block discord users with an ongoing timeout from joining")
        public boolean disallowTimeoutMembersToJoin = true;
        @TomlComment({"How many alt accounts should a player be able to link to a single discord account",
            "If maxAlts is set to 1 will be able to link their main account as well as a single alt account"})
        public int maxAlts = 1;
        @TomlComment("How many minutes a code for linking should be valid")
        public long linkCodeExpireMinutes = 10;
    }

    public static class ErrorStrings {

        @TomlComment("Kick message for missing roles on discord, related to linking.requiredRoles")
        public String kickMissingRoles = "You currently don't have the permission to join the server.";
        @TomlComment("Kick message telling a player to link their account on discord")
        public String kickLinkCode = "Please Link your discord account by using\n/link %s\non discord";
        @TomlComment("Kick message to use when a player is unlinked while online")
        public String kickUnlinked = "Your account has been unlinked, to rejoin the server please relink your account.\nIf you don't know why this happened, please ask an administrator";
        @TomlComment("Kick message to use when a player has a timeout on discord, related to linking.disallowTimeoutMembersToJoin")
        public String kickTimedOut = "You are timed out and currently can't join this server. Please retry when your discord timeout is over.";
        @TomlComment("Kick message to use when the player receives a timeout on discord while being online, related to linking.disallowTimeoutMembersToJoin")
        public String kickOnTimeOut = "You have been timed out on discord. You can rejoin after it is over.";
        @TomlComment("Kick message to use when a player left the discord server while being online in minecraft")
        public String kickOnLeave = "Your associated discord account has left the discord server.";
    }

    public static class MessageStrings {

        @TomlComment({"How a normal discord chat message sent in the serverChatChannel should be displayed ingame",
            "Use https://placeholders.pb4.eu/user/text-format/ for more information on formatting",
            "Placeholders: ",
            "%user%: User who sent the message",
            "%msg%: the message",
            "%attachments%: optional attachments such as images and files"})
        public String chatMessageFormat = "[<blue>Discord</blue>] <%user%> %msg% %attachments%";
        @TomlComment({"How a reply to a message sent in the serverChatChannel should be displayed ingame",
            "Use https://placeholders.pb4.eu/user/text-format/ for more information on formatting",
            "Placeholders: ",
            "%user%: User who sent the message",
            "%msg%: The message",
            "%userRepliedTo%: The user whose message was replied to",
            "%attachments%: Optional attachments such as images and files"})
        public String chatMessageFormatReply = " [<blue>Discord</blue>] <%user% replied to %userRepliedTo%> %msg% %attachments%";
        @TomlComment({"The formatting to use for links/urls",
            "Placeholder: %link%: The url"})
        public String linkFormat = "<blue><underline><i><url:'%link%'>%link%</url></i></underline></blue>";
        @TomlComment({"The formatting to use for attachments",
            "Placeholders",
            "%link%: The url of the attached file",
            "%name%: THe name of the attachment"})
        public String attachmentFormat = "[<blue><url:'%link%'>%name%</url></blue>]";
        @TomlComment({"The formatting to use for commands sent to the console channel",
            "Placeholders:",
            "%user%: The user who executed the command",
            "%cmd%: The command the user executed"})
        public String commandExecutedInfoText = "%user% executed ``%cmd%``";
        @TomlComment({"The message to display in discord when a player joins",
            "Placeholder: %user%: The player name of whoever joined"})
        public String playerJoinMessage = "%user% joined";
        @TomlComment({"The message to display in discord when a player leaves",
            "Placeholder: %user%: The player name of whoever joined"})
        public String playerLeaveMessage = "%user% left";
        @TomlComment({"The formatting to use for commands sent to the console channel",
            "Placeholders:",
            "%user%: The user who received the advancement",
            "%title%: Advancement title",
            "%description%: Advancement description"})
        public String advancementMessage = "%user% just made the advancement **%title%**\n*%description%*";
        public String startMessage = "Server started";
        public String stopMessage = "Server stopped";
        @TomlComment({
            "Formatting to use for the online player count status if there is more than 1 player online, related to showPlayerCountStatus",
            "Placeholder: %d: Player count"})
        public String onlineCountPlural = "%d players online";
        @TomlComment("Formatting to use for the online player count status if there is 1 player online, related to showPlayerCountStatus")
        public String onlineCountSingular = "1 player online";
        @TomlComment("Formatting to use for the online player count status if there is no player online, related to showPlayerCountStatus")
        public String onlineCountZero = "Server is lonely :(";

        @TomlComment({"Format for shared waypoints",
                        "Placeholders:",
                        "%abbr%: abbreviation of waypoint (voxel defaults to first letter)",
                        "%name%: name of waypoint",
                        "%x%, %y%, %z%: coordinates",
                        "%dimension%: dimension"})
        public String waypointFormat = "Waypoint: (%abbr%)  %name% `%x% %y% %z%` in %dimension%";
    }

    public static class DiscordLinkResults {

        @TomlComment("Placeholder: %name%: Player name")
        public String linkSuccess = "Successfully linked to %name%";
        @TomlComment("If the user does not have all of linking.requiredRoles to join")
        public String linkNotAllowed = "You are currently missing the required roles to link your account.";
        @TomlComment("If the provided code was invalid")
        public String failedUnknownCode = "Unknown code, did you copy it correctly?";
        @TomlComment("If the user already has reached the linking.maxAlts limit")
        public String failedTooManyLinked = "You cannot link to another account";
    }

    public static class ExternalIntegrations {

        @TomlComment("Vanish https://modrinth.com/mod/vanish")
        public boolean enableVanishIntegration = true;
        @TomlComment("Luck Perms https://modrinth.com/plugin/luckperms")
        public boolean enableLuckPermsIntegration = true;
        public LuckPermsIntegration luckPerms = new LuckPermsIntegration();

        public static class LuckPermsIntegration {

            @TomlComment("Groups to assign to minecraft accounts that are alts")
            public List<String> altGroups = new ArrayList<>();
        }
    }

    public static class CommandSettings {

        @TomlComment("A channel to send player run commands to and run specific configured commands from")
        public String consoleChannel = "";
        @TomlComment("Whether all commands should be logged")
        public boolean logCommandsInConsole = true;
        @TomlComment("Should commands from commands blocks be logged")
        public boolean logCommandBlockCommands = false;
        @TomlComment("List of commands to ignore/not send to the consoleChannel")
        public List<String> ignoredCommands = new ArrayList<>();
        @TomlComment("Prefix to use in front of commands on discord")
        public String commandPrefix = "//";
        @TomlComment("A role that is required to be able to run commands")
        public String opRole = "";
        @TomlComment("logRedirectChannels: Specific channels to send certain commands to instead of to the consoleChannel")
        public List<LogRedirectChannel> logRedirectChannels = List.of(
            LogRedirectChannel.of("", List.of("w ", "msg ", "tell ")));
        @TomlComment("commandList: List of commands that can be run from the consoleChannel by sending a message starting with the commandPrefix")
        public List<BridgeCommand> commandList = List.of(
            BridgeCommand.of("kick", "kick %args%"),
            BridgeCommand.of("stop", "stop"),
            BridgeCommand.of("kill", "kill %args%"),
            BridgeCommand.of("ban", "ban %args%")
        );
    }

    public static class LogRedirectChannel {

        public String channel;
        @TomlComment("Command prefixes to redirect to this channel." +
            "IMPORTANT: add a space behind the command name to block other commands starting with the same String from being selected e.g: \"w \" to not select whitelist too")
        public List<String> redirectPrefixes = new ArrayList<>();

        public static LogRedirectChannel of(String channel, List<String> prefixes) {
            LogRedirectChannel obj = new LogRedirectChannel();
            obj.channel = channel;
            obj.redirectPrefixes = prefixes;
            return obj;
        }
    }

    public static class BridgeCommand {

        @TomlComment("Name of the command on discord")
        public String commandName = "";
        @TomlComment("Command to run ingame, use Placeholder %args% for args")
        public String inGameAction = "";

        public static BridgeCommand of(String name, String action) {
            BridgeCommand obj = new BridgeCommand();
            obj.commandName = name;
            obj.inGameAction = action;
            return obj;
        }
    }

}
