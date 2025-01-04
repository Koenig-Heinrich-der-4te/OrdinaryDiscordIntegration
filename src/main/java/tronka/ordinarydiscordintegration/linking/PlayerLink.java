package tronka.ordinarydiscordintegration.linking;

import com.google.common.collect.ImmutableList;
import net.minecraft.util.Uuids;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.Utils;

import java.util.*;

public class PlayerLink {
    private UUID playerId;
    private long discordId;
    private List<PlayerData> alts;
    private transient LinkData dataObj;

    public PlayerLink() {

    }

    public PlayerLink(LinkRequest request, long discordId) {
        this(request.getPlayerId(), discordId);
    }

    public PlayerLink(UUID playerId, long discordId) {
        this.playerId = playerId;
        this.discordId = discordId;
        this.alts = new ArrayList<>();
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getPlayerName() {
        return Utils.getPlayerName(this.playerId);
    }

    public long getDiscordId() {
        return this.discordId;
    }

    public void addAlt(PlayerData data) {
        this.alts.add(data);
        this.dataObj.updatePlayerLink(this);
    }

    public void removeAlt(UUID uuid) {
        this.alts.removeIf(data -> data.getId().equals(uuid));
        this.dataObj.updatePlayerLink(this);
    }

    public void removeAlt(PlayerData data) {
        this.alts.remove(data);
        this.dataObj.updatePlayerLink(this);
    }

    public boolean hasAlt(UUID uuid) {
        return this.alts.stream().map(PlayerData::getId).anyMatch(uuid::equals);
    }

    public int altCount() {
        return this.alts.size();
    }

    public ImmutableList<PlayerData> getAlts() {
        return ImmutableList.copyOf(this.alts);
    }

    public void setDataObj(LinkData dataObj) {
        this.dataObj = dataObj;
    }

    @Override
    public String toString() {
        return "PlayerLink{" +
                "playerId=" + this.playerId +
                ", discordId=" + this.discordId +
                ", alts=" + this.alts +
                ", dataObj=" + this.dataObj +
                '}';
    }
}
