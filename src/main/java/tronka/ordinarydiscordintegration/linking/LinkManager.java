package tronka.ordinarydiscordintegration.linking;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.Utils;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.*;
import java.util.stream.Collectors;

public class LinkManager extends ListenerAdapter {
    private static final int PURGE_LIMIT = 30;
    private static final Random RANDOM = new Random();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, LinkRequest> linkRequests = new HashMap<>();
    private LinkData linkData;
    private final OrdinaryDiscordIntegration integration;
    private List<Role> requiredRoles;
    private List<Role> joinRoles;


    public LinkManager(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        integration.registerConfigReloadHandler(this::onConfigLoaded);
    }

    private void onConfigLoaded(Config config) {
        this.linkData = JsonLinkData.from(OrdinaryDiscordIntegration.getConfigFolder().resolve(OrdinaryDiscordIntegration.ModId + ".player-links.json").toFile());
        this.requiredRoles = Utils.parseRoleList(this.integration.getGuild(), this.integration.getConfig().joining.requiredRoles);
        this.joinRoles = Utils.parseRoleList(this.integration.getGuild(), this.integration.getConfig().joining.joinRoles);
    }

    public Optional<Member> getDiscordOf(UUID playerId) {
        Optional<PlayerLink> link = this.linkData.getPlayerLink(playerId);
        if (link.isPresent()) {
           return getDiscordOf(link.get());
        }
        return Optional.empty();
    }

    public boolean isAllowedToJoin(Member member) {
        if (!this.integration.getConfig().joining.enableLinking) {return true;}
        return Set.copyOf(member.getRoles()).containsAll(this.requiredRoles);
    }

    public boolean isAllowedToJoin(long discordId) {
        return isAllowedToJoin(this.integration.getGuild().getMemberById(discordId));
    }

    public Optional<Member> getDiscordOf(PlayerLink link) {
        Member member = this.integration.getGuild().getMemberById(link.getDiscordId());
        if (member != null) {
            return Optional.of(member);
        }
        return Optional.empty();
    }

    public Optional<PlayerLink> getDataOf(long discordId) {
        return this.linkData.getPlayerLink(discordId);
    }

    public Optional<PlayerLink> getDataOf(UUID playerId) {
        return this.linkData.getPlayerLink(playerId);
    }

    public boolean canJoin(UUID playerId) {
        if (!this.integration.getConfig().joining.enableLinking) {return true;}
        Optional<Member> member = getDiscordOf(playerId);
        if (member.isEmpty()) {return false;}
        if (this.integration.getConfig().joining.disallowTimeoutMembersToJoin && member.get().isTimedOut()) {return false;}
        return Set.copyOf(member.get().getRoles()).containsAll(this.requiredRoles);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        Optional<PlayerLink> dataOptional = this.linkData.getPlayerLink(player.getUuid());
        if (dataOptional.isEmpty()) {
            return;
        }
        PlayerLink data = dataOptional.get();
        Optional<Member> memberOptional = getDiscordOf(data);
        if (memberOptional.isEmpty()) {
            return;
        }
        Member member = memberOptional.get();
        if (!PermissionUtil.canInteract(this.integration.getGuild().getSelfMember(), member)) {
            return;
        }

        if (data.getPlayerId().equals(player.getUuid())
                && this.integration.getConfig().joining.renameOnJoin
                && PermissionUtil.checkPermission(this.integration.getGuild().getSelfMember(), Permission.NICKNAME_MANAGE)) {
            member.modifyNickname(player.getName().getString()).queue();
        }
        if (PermissionUtil.checkPermission(this.integration.getGuild().getSelfMember(), Permission.MANAGE_ROLES)) {
            member.getGuild().modifyMemberRoles(member, this.joinRoles, Collections.emptyList()).queue();
        }
    }

    public String getJoinError(GameProfile profile) {
        Optional<Member> member = getDiscordOf(profile.getId());
        if (member.isEmpty()) {
            String code = generateLinkCode(profile);
            return this.integration.getConfig().kickMessages.kickLinkCode.formatted(code);
        }
        if (member.get().isTimedOut()) {
            return this.integration.getConfig().kickMessages.kickTimedOut;
        }
        return this.integration.getConfig().kickMessages.kickMissingRoles;
    }

    public String confirmLink(long discordId, String code) {
        if (!isAllowedToJoin(discordId)) {
            return this.integration.getConfig().linkResults.linkNotAllowed;
        }
        Optional<LinkRequest> linkRequest = getPlayerLinkFromCode(code);
        if (linkRequest.isEmpty()) { return this.integration.getConfig().linkResults.failedUnknownCode; }
        Optional<PlayerLink> existing = this.linkData.getPlayerLink(discordId);
        if (existing.isPresent()) {
            PlayerLink link = existing.get();
            if (link.altCount() >= this.integration.getConfig().maxAlts) {
                return this.integration.getConfig().linkResults.failedTooManyLinked;
            }
            link.addAlt(PlayerData.from(linkRequest.get()));
            this.integration.getLuckPermsIntegration().setAlt(linkRequest.get().getPlayerId());
        } else {
            this.linkData.addPlayerLink(new PlayerLink(linkRequest.get(), discordId));
        }
        return this.integration.getConfig().linkResults.linkSuccess
                .replace("%name%", linkRequest.get().getName());
    }

    private Optional<LinkRequest> getPlayerLinkFromCode(String code) {
        if (!this.linkRequests.containsKey(code)) {
            return Optional.empty();
        }
        LinkRequest request = this.linkRequests.remove(code);
        if (request.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public String generateLinkCode(GameProfile profile) {
        if (this.linkRequests.size() >= PURGE_LIMIT) {
            purgeCodes();
        }

        long expiryTime = System.currentTimeMillis() + this.integration.getConfig().joining.linkCodeExpireMinutes * 60 * 1000;
        String code;
        do {
            code = String.valueOf(RANDOM.nextInt(100000, 1000000));  // 6-digit code
        }
        while (this.linkRequests.containsKey(code));
        this.linkRequests.put(code, new LinkRequest(profile.getId(), profile.getName(), expiryTime));
        return code;
    }

    private void purgeCodes() {
        this.linkRequests.entrySet().removeIf(request -> request.getValue().isExpired());
    }

    public void unlinkPlayers(List<Member> members) {
        if (!this.integration.getConfig().unlinkOnLeave) {
            return;
        }
        Set<Long> memberSet = members.stream().map(Member::getIdLong).collect(Collectors.toSet());
        List<PlayerLink> toRemove = this.linkData.getPlayerLinks().filter(link -> !memberSet.contains(link.getDiscordId())).toList();
        toRemove.forEach(this.linkData::removePlayerLink);
        if (!toRemove.isEmpty()) {
            LOGGER.info("Purged {} linked players", toRemove.size());
        }
    }

    public void unlinkPlayer(long id) {
        this.linkData.getPlayerLink(id).ifPresent(this.linkData::removePlayerLink);
    }

    public void unlinkPlayer(UUID uuid) {
        Optional<PlayerLink> dataOptional = this.linkData.getPlayerLink(uuid);
        if (dataOptional.isEmpty()) { return; }
        PlayerLink data = dataOptional.get();
        if (data.getPlayerId().equals(uuid)) {
            this.linkData.removePlayerLink(data);
        } else {
            data.removeAlt(uuid);
        }
    }

    public void unlinkPlayer(PlayerLink link) {
        this.linkData.removePlayerLink(link);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Member member = event.getMember();
        if (member == null) { return; }

        kickAccounts(member, this.integration.getConfig().kickMessages.kickOnLeave);
        unlinkPlayer(member.getIdLong());
        LOGGER.info("Removed link of \"{}\" because they left the guild.", member.getEffectiveName());
    }

    public void kickAccounts(Member member, String reason) {
        Optional<PlayerLink> playerLink = this.integration.getLinkManager().getDataOf(member.getIdLong());
        if (playerLink.isEmpty()) {
            return;
        }
        MinecraftServer server = this.integration.getServer();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerLink.get().getPlayerId());
        if (player != null) {
            player.networkHandler.disconnect(Text.of(reason));
        }

        for (PlayerData alt : playerLink.get().getAlts()) {
            ServerPlayerEntity altPlayer = server.getPlayerManager().getPlayer(alt.getId());
            if (altPlayer != null) {
                altPlayer.networkHandler.disconnect(Text.of(reason));
            }
        }
    }
}
