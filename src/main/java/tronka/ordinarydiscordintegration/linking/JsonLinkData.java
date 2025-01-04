package tronka.ordinarydiscordintegration.linking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class JsonLinkData implements LinkData {
    private List<PlayerLink> links;
    private final File file;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    private JsonLinkData(File file) {
        this.file = file;
        if (!file.exists()) {
            this.links = new ArrayList<>();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            this.links = gson.fromJson(reader, new TypeToken<ArrayList<PlayerLink>>() { });
        } catch (Exception e) {
            throw new RuntimeException("Cannot load player links (%s)".formatted(file.getAbsolutePath()), e);
        }
        if (this.links == null) {
            // gson failed to parse a valid list
            this.links = new ArrayList<>();
        }
        this.links.forEach(link -> link.setDataObj(this));
        System.out.println("Loaded " + this.links.size() + " player links");
    }

    @Override
    public Optional<PlayerLink> getPlayerLink(UUID playerId) {
        return this.links.stream().filter(link -> playerId.equals(link.getPlayerId()) || link.hasAlt(playerId)).findFirst();
    }

    @Override
    public Optional<PlayerLink> getPlayerLink(long discordId) {
        return this.links.stream().filter(link -> discordId == link.getDiscordId()).findFirst();
    }

    @Override
    public void addPlayerLink(PlayerLink playerLink) {
        this.links.add(playerLink);
        playerLink.setDataObj(this);
        onUpdated();
    }

    @Override
    public void removePlayerLink(PlayerLink playerLink) {
        this.links.remove(playerLink);
        onUpdated();
    }

    @Override
    public void updatePlayerLink(PlayerLink playerLink) {
        onUpdated();
    }

    private void onUpdated() {
        try (FileWriter writer = new FileWriter(this.file)) {
            gson.toJson(this.links, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save link data to file {}", this.file.getAbsolutePath(), e);
        }
    }

    public static LinkData from(File file) {
        return new JsonLinkData(file);
    }

    @Override
    public Stream<PlayerLink> getPlayerLinks() {
        return this.links.stream();
    }
}
