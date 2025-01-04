package tronka.ordinarydiscordintegration.compat;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import org.slf4j.Logger;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

import java.util.UUID;

public class LuckPermsIntegration {
    private final OrdinaryDiscordIntegration integration;
    private LuckPerms luckPerms;
    private boolean loaded = false;
//    private LuckPerms luckPerms = null;
    private static final Logger LOGGER = LogUtils.getLogger();
    public LuckPermsIntegration(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        if (!integration.getConfig().integrations.enableLuckPermsIntegration) { return; }
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) { return; }
        try {
            this.luckPerms = LuckPermsProvider.get();
            this.loaded = true;
        } catch (Exception ignored) {
            LOGGER.error("Luck-perms not loaded, disabling integration");
        }
    }

    public void setAlt(UUID uuid) {
        if (!this.loaded) { return; }
        this.luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
            for (String group : this.integration.getConfig().integrations.luckPerms.altGroups) {
                user.data().add(LuckPermsHelper.getNode(group));
            }
            this.luckPerms.getUserManager().saveUser(user);
        });
    }
}
