package tronka.ordinarydiscordintegration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class DiscordCommandSender extends ServerCommandSource {
    private final Consumer<String> feedbackConsumer;
    private final String sender;
    public DiscordCommandSender(MinecraftServer server, String sender, Consumer<String> feedback) {
        super(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, server.getOverworld(), 4, "OrdinaryDiscordIntegration", Text.of("Ordinary Discord Integration"), server, null);
        this.feedbackConsumer = feedback;
        this.sender = sender;
    }

    @Override
    public void sendFeedback(Supplier<Text> feedbackSupplier, boolean broadcastToOps) {
        String message = feedbackSupplier.get().getString();
        this.feedbackConsumer.accept(message);
    }

    @Override
    public void sendError(Text message) {
        this.feedbackConsumer.accept(message.getString());
    }

    public String getSender() {
        return this.sender;
    }
}
