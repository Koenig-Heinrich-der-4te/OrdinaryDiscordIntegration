package tronka.ordinarydiscordintegration.linking;

import java.util.UUID;

public class LinkRequest {
    private final UUID playerId;
    private final String name;
    private final long expiresAt;

    public LinkRequest(UUID playerId, String name, long expiresAt) {
        this.playerId = playerId;
        this.name = name;
        this.expiresAt = expiresAt;
    }

    public UUID getPlayerId() {
        return this.playerId;
    }
    public String getName() {
        return this.name;
    }
    public boolean isExpired() {
        return this.expiresAt < System.currentTimeMillis();
    }
}
