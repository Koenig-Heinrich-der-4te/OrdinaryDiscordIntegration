package tronka.ordinarydiscordintegration;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mojang.logging.LogUtils;
import eu.pb4.placeholders.api.node.TextNode;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChatBridge extends ListenerAdapter {
    private final OrdinaryDiscordIntegration integration;
    private TextChannel channel;
    private Webhook webhook;
    private static final String webhookId = "odi-bridge-hook";
    private boolean stopped = false;
    private ServerPlayerEntity lastMessageSender;
    private String lastMessageContent;
    private long lastMessageId;
    private int lastMessageCount = 0;
    private long lastMessageTimestamp;
    private JDAWebhookClient webhookClient;

    public ChatBridge(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        ServerMessageEvents.CHAT_MESSAGE.register(this::onMcChatMessage);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        integration.registerConfigReloadHandler(this::onConfigLoaded);
        channel.sendMessage(integration.getConfig().messages.startMessage).queue();
    }

    private void onConfigLoaded(Config config) {
        channel = Utils.getTextChannel(integration.getJda(), config.serverChatChannel);
        setWebhook(null);
        if (integration.getConfig().useWebHooks) {
            channel.retrieveWebhooks().onSuccess((webhooks -> {
                Optional<Webhook> hook = webhooks.stream().filter(w -> w.getOwner() == this.integration.getGuild().getSelfMember()).findFirst();
                if (hook.isPresent()) {
                    setWebhook(hook.get());
                } else {
                    channel.createWebhook(webhookId).onSuccess(this::setWebhook).queue();
                }
            })).queue();
        }

    }

    private void setWebhook(Webhook webhook) {
        this.webhook = webhook;
        if (webhookClient != null) {
            webhookClient.close();
            webhookClient = null;
        }
        if (webhook != null) {
            webhookClient = JDAWebhookClient.from(webhook);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // discord message
        if (event.getChannel() != channel) {
            return;
        }
        if (event.getMember() == null || event.getAuthor().isBot()) {
            return;
        }

        Message repliedMessage = event.getMessage().getReferencedMessage();

        String baseText = repliedMessage == null ? integration.getConfig().messages.chatMessageFormat : integration.getConfig().messages.chatMessageFormatReply;

        TextNode attachmentInfo;
        if (!event.getMessage().getAttachments().isEmpty()) {
            List<TextNode> attachments = new ArrayList<>(List.of(TextNode.of("\nAttachments:")));
            for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                attachments.add(TextReplacer.create()
                        .replace("link", attachment.getUrl())
                        .replace("name", attachment.getFileName())
                        .applyNode(integration.getConfig().messages.attachmentFormat));
            }
            attachmentInfo = TextNode.wrap(attachments);
        } else {
            attachmentInfo = TextNode.empty();
        }

        String replyUser = repliedMessage == null ? "%userRepliedTo%" : (repliedMessage.getMember() == null ? repliedMessage.getAuthor().getEffectiveName() : repliedMessage.getMember().getEffectiveName());
        sendMcChatMessage(TextReplacer.create()
                .replace("msg", Utils.parseUrls(event.getMessage().getContentDisplay(), integration.getConfig()))
                .replace("user",  event.getMember().getEffectiveName())
                .replace("userRepliedTo", replyUser)
                .replace("attachments", attachmentInfo)
                .apply(baseText));
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        sendStackedMessage(
                integration.getConfig().messages.playerJoinMessage
                        .replace("%user%", player.getName().getString()),
                null
        );
        updateRichPresence(1);
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (stopped) {
            return;
        }

        sendStackedMessage(integration.getConfig().messages.playerLeaveMessage
                .replace("%user%", player.getName().getString()),
                null);
        updateRichPresence(-1);
    }

    private void updateRichPresence(int modifier) {
        if (!integration.getConfig().showPlayerCountStatus) {
            return;
        }
        long playerCount = integration.getServer().getPlayerManager().getPlayerList().stream()
                .filter(p -> !integration.getVanishIntegration().isVanished(p)).count() + modifier;
        integration.getJda().getPresence().setPresence(
                Activity.playing(switch ((int) playerCount) {
                    case 0 -> integration.getConfig().messages.onlineCountZero;
                    case 1 -> integration.getConfig().messages.onlineCountSingular;
                    default -> integration.getConfig().messages.onlineCountPlural.formatted(playerCount);
                }),
                false);
    }

    public void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        if (integration.getConfig().broadCastDeathMessages) {
            String message = source.getDeathMessage(player).getString();
            sendStackedMessage(message, null);
        }
    }

    public void onReceiveAdvancement(ServerPlayerEntity player, AdvancementDisplay advancement){
        if(integration.getConfig().announceAdvancements) {
            sendStackedMessage(
                    integration.getConfig().messages.advancementMessage
                            .replace("%user%", player.getName().getString())
                            .replace("%title%", advancement.getTitle().getString())
                            .replace("%description%", advancement.getDescription().getString()),
                    null);
        }
    }

    public void sendMcChatMessage(Text message) {
        integration.getServer().getPlayerManager().broadcast(message, false);
    }

    private void onServerStopping(MinecraftServer minecraftServer) {
        channel.sendMessage(integration.getConfig().messages.stopMessage).queue();
        stopped = true;
    }


    private void onMcChatMessage(SignedMessage signedMessage, ServerPlayerEntity player, MessageType.Parameters parameters) {
        String message = signedMessage.getContent().getLiteralString();
        sendStackedMessage(message, player);
    }

    private void sendStackedMessage(String message, ServerPlayerEntity sender) {
        boolean shouldDelay = false;
        if (this.integration.getConfig().stackMessages){
            if (this.lastMessageSender == sender
                    && message.equals(this.lastMessageContent)
                    && System.currentTimeMillis() - this.lastMessageTimestamp < this.integration.getConfig().stackMessagesTimeoutInSec * 1000L
            ) {
                this.lastMessageCount++;
                String displayCounter = " (" + this.lastMessageCount + ")";
                String updatedLastMessage = this.lastMessageContent + displayCounter;
                editChatMessageToDiscord(updatedLastMessage, this.lastMessageSender, this.lastMessageId);
                return;
            }
        }
        sendChatMessageToDiscord(message, sender, shouldDelay);
        this.lastMessageSender = sender;
        this.lastMessageContent = message;
        this.lastMessageCount = 1;
        this.lastMessageTimestamp = System.currentTimeMillis();
    }

    private void sendChatMessageToDiscord(String message, ServerPlayerEntity sender, boolean shouldDelay) {
        if (sender == null) {
            sendMiscMessageToDiscord(message, shouldDelay);
            return;
        }
        sendPlayerMessageToDiscord(message, sender, shouldDelay);
    }

    private void editChatMessageToDiscord(String message, ServerPlayerEntity sender, long messageId) {
        if (sender == null) {
            editMiscMessageToDiscord(message, messageId);
            return;
        }
        editPlayerMessageToDiscord(message, sender, messageId);
    }

    private void sendMiscMessageToDiscord(String message, boolean shouldDelay) {
        CompletableFuture<Message> messageCompletableFuture = channel.sendMessage(message).submit();
        try {
            this.lastMessageId = messageCompletableFuture.get().getIdLong();
        } catch (Exception e) {
            LogUtils.getLogger().error("message return not successful: {}", String.valueOf(e));
        }
    }

    private void editMiscMessageToDiscord(String message, long messageId) {
        channel.editMessageById(messageId, message).queue();
    }

    private void sendPlayerMessageToDiscord(String message, ServerPlayerEntity sender, boolean shouldDelay) {
        if (webhook != null) {
            sendAsWebhook(message, sender);
        } else {
            String formattedMessage = sender.getName() + ": " + message;
            CompletableFuture<Message> messageCompletableFuture = channel.sendMessage(formattedMessage).submit();
            try {
                this.lastMessageId = messageCompletableFuture.get().getIdLong();
            } catch (Exception e) {
                LogUtils.getLogger().error("message return not successful: {}", String.valueOf(e));
            }
        }
    }

    private void editPlayerMessageToDiscord(String message, ServerPlayerEntity sender, long messageId) {
        if (this.webhook != null) {
            editAsWebhook(this.lastMessageId, message);
        } else {
            String formattedMessage = sender.getName() + ": " + message;
            channel.editMessageById(messageId, formattedMessage).queue();
        }
    }


    private String getAvatarUrl(ServerPlayerEntity player) {
        return integration.getConfig().avatarUrl
                .replace("%UUID%", player.getUuid().toString())
                .replace("%randomUUID%", UUID.randomUUID().toString());
    }

    private void sendAsWebhook(String message, ServerPlayerEntity player) {
        String avatarUrl = getAvatarUrl(player);
        WebhookMessage msg = new WebhookMessageBuilder()
                .setUsername(player.getName().getLiteralString())
                .setAvatarUrl(avatarUrl)
                .setContent(message)
                .build();
        CompletableFuture<ReadonlyMessage> readonlyMessageCompletableFuture = webhookClient.send(msg);
        try {
            this.lastMessageId = readonlyMessageCompletableFuture.get().getId();
        } catch (Exception e) {
            LogUtils.getLogger().error("message return not successful: {}", String.valueOf(e));
        }
    }

    private void editAsWebhook(long messageId, String message) {
        this.webhookClient.edit(messageId, message);
    }
}
