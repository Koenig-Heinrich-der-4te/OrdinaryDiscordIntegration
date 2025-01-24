package tronka.justsync;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import eu.pb4.placeholders.api.node.TextNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import tronka.justsync.chat.TextReplacer;
import tronka.justsync.config.Config;

public class Utils {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson gson = new Gson();
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b[-a-zA-Z0-9()@:%_+.~#?&/=]*");

    private static final Pattern SHARED_LOCATION_PATTERN =
            Pattern.compile("^\\[x:(-?\\d+), y:(-?\\d+), z:(-?\\d+)]$");
    private static final Pattern SHARED_WAYPOINT_PATTERN =
            Pattern.compile("^\\[name:(\\w+), x:(-?\\d+), y:(-?\\d+), z:(-?\\d+), dim:minecraft:(?:\\w+_)?(\\w+)(?:, icon:\\w+)?\\]$");
    private static final Map<RegistryKey<World>, String> DIMENSION_MAP =
            Map.of(
                World.OVERWORLD, "Overworld",
                World.NETHER, "Nether",
                World.END, "End"
            );



    public static List<Role> parseRoleList(Guild guild, List<String> roleIds) {
        List<Role> roles = new ArrayList<>();
        if (roleIds == null) {
            return roles;
        }
        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                Optional<Role> namedRole = guild.getRoles().stream()
                    .filter(r -> r.getName().equals(roleId))
                    .findFirst();
                if (namedRole.isEmpty()) {
                    LOGGER.warn("Could not find role with id \"{}\"", roleId);
                    continue;
                }
                role = namedRole.get();
            }
            roles.add(role);
        }
        return roles;
    }

    public static TextChannel getTextChannel(JDA jda, String id) {
        if (id == null || id.length() < 5) {
            return null;
        }
        return jda.getTextChannelById(id);
    }

    public static String getPlayerName(UUID uuid) {
        ProfileResult result = JustSyncApplication.getInstance().getServer()
            .getSessionService()
            .fetchProfile(uuid, false);
        if (result == null) {
            return "unknown";
        }
        return result.profile().getName();
    }

    public static GameProfile fetchProfile(String name) {
        try {
            return fetchProfileData("https://api.mojang.com/users/profiles/minecraft/" + name);
        } catch (IOException ignored) {}
        try {
            return fetchProfileData("https://api.minetools.eu/uuid/" + name);
        } catch (IOException e){
            return null;
        }
    }

    private static GameProfile fetchProfileData(String urlLink) throws IOException {
        URL url = URI.create(urlLink).toURL();
        URLConnection connection = url.openConnection();
        connection.addRequestProperty("User-Agent", "DiscordJS");
        connection.addRequestProperty("Accept", "application/json");
        connection.connect();
        BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
        String data = reader.lines().collect(Collectors.joining());
        if (data.endsWith("\"ERR\"}")) {
            return null;
        }
        // fix uuid format
        String fixed = data.replaceFirst(
            "\"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)\"", "$1-$2-$3-$4-$5");
        return gson.fromJson(fixed, GameProfile.class);
    }

    public static boolean startsWithAny(String string, List<String> starts) {
        for (String s : starts) {
            if (string.startsWith(s)) {
                return true;
            }
        }
        return false;
    }

    public static TextNode parseUrls(String text, Config config) {
        List<TextNode> nodes = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            nodes.add(TextNode.of(text.substring(lastEnd, matcher.start())));
            nodes.add(TextReplacer.create()
                .replace("link", matcher.group())
                .applyNode(config.messages.linkFormat));
            lastEnd = matcher.end();
        }
        nodes.add(TextNode.of(text.substring(lastEnd)));
        return TextNode.wrap(nodes);
    }

    public static String escapeUnderscores(String username) {
        if (username == null) {
            return null;
        }
        return username.replaceAll("_", "\\_");
    }

    public static String formatVoxel(
            String message, Config config, ServerPlayerEntity player) {
        if (!message.startsWith("[x:") && !message.startsWith("[name:")) {
            return message;
        }

        Matcher sharedLocationMatcher = SHARED_LOCATION_PATTERN.matcher(message);
        if (sharedLocationMatcher.find()) {
            return formatSharedLocationVoxel(sharedLocationMatcher, config, player);
        }

        Matcher sharedWaypointMatcher = SHARED_WAYPOINT_PATTERN.matcher(message);
        if (sharedWaypointMatcher.find()) {
            return formatSharedWaypointVoxel(sharedWaypointMatcher, config);
        }

        return message;
    }

    private static String formatSharedLocationVoxel(
            Matcher matcher, Config config, ServerPlayerEntity player) {
        String x = matcher.group(1);
        String y = matcher.group(2);
        String z = matcher.group(3);
        String dim = DIMENSION_MAP.getOrDefault(
                        player.getWorld().getRegistryKey(), "Unknown");

        String returnMessage = config.messages.waypointFormat;
        String name = "Shared Location";
        if (dim.equals("Overworld") && !config.waypointURL.isEmpty()) {
            name = String.format("[Shared Location](<%s>)", config.waypointURL);
        }

        return returnMessage.replace("%name%", name)
                            .replace("%abbr%", "S")
                            .replace("%dimension%", dim)
                            .replaceAll("%x%", x)
                            .replaceAll("%y%", y)
                            .replaceAll("%z%", z);
    }

    private static String formatSharedWaypointVoxel(
            Matcher matcher, Config config) {
        String name = matcher.group(1);
        String x = matcher.group(2);
        String y = matcher.group(3);
        String z = matcher.group(4);
        String dim = StringUtils.capitalize(matcher.group(5));

        String returnMessage = config.messages.waypointFormat;
        if (dim.equals("Overworld") && !config.waypointURL.isEmpty()) {
            name = String.format("[%s](<%s>)", name, config.waypointURL);
        }

        return returnMessage.replace("%name%", name)
                            .replace("%dimension%", dim)
                            .replace("%abbr%", name.substring(1, 2).toUpperCase())
                            .replaceAll("%x%", x)
                            .replaceAll("%y%", y)
                            .replaceAll("%z%", z);
    }

    public static String formatXaero(String message, Config config) {
        if (!message.startsWith("xaero-waypoint:")) {
            return message;
        }

        List<String> messageParts = List.of(message.split(":"));
        if (messageParts.size() != 10) {
            return message;
        }

        int x, y, z;
        try {
            x = Integer.parseInt(messageParts.get(3));
            y = parseIntWithDefault(messageParts.get(4), 64);
            z = Integer.parseInt(messageParts.get(5));
        } catch (NumberFormatException e) {
            return message;
        }

        String dimension = messageParts.get(9).contains("overworld") ? "Overworld" :
                           messageParts.get(9).contains("nether") ? "Nether" : "End";

        String returnMessage = config.messages.waypointFormat;
        String name = messageParts.get(1);
        if (!config.waypointURL.isEmpty() && messageParts.get(9).contains("overworld")) {
            name = String.format("[%s](<%s>)", name, config.waypointURL);
        }

        return returnMessage.replace("%name%", name)
                            .replace("%abbr%", messageParts.get(2))
                            .replaceAll("%x%", Integer.toString(x))
                            .replaceAll("%y%", Integer.toString(y))
                            .replaceAll("%z%", Integer.toString(z))
                            .replace("%dimension%", dimension);
    }

    private static int parseIntWithDefault(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
