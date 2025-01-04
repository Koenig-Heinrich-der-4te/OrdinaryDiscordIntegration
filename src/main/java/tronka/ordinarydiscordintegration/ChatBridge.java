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
        this.channel.sendMessage(integration.getConfig().messages.startMessage).queue();
    }

    private void onConfigLoaded(Config config) {
        this.channel = Utils.getTextChannel(this.integration.getJda(), config.serverChatChannel);
        setWebhook(null);
        if (this.integration.getConfig().useWebHooks) {
            this.channel.retrieveWebhooks().onSuccess((webhooks -> {
                Optional<Webhook> hook = webhooks.stream().filter(w -> w.getOwner() == this.integration.getGuild().getSelfMember()).findFirst();
                if (hook.isPresent()) {
                    setWebhook(hook.get());
                } else {
                    this.channel.createWebhook(webhookId).onSuccess(this::setWebhook).queue();
                }
            })).queue();
        }

    }

    private void setWebhook(Webhook webhook) {
        this.webhook = webhook;
        if (this.webhookClient != null) {
            this.webhookClient.close();
            this.webhookClient = null;
        }
        if (webhook != null) {
            this.webhookClient = JDAWebhookClient.from(webhook);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // discord message
        if (event.getChannel() != this.channel) {
            return;
        }
        if (event.getMember() == null || event.getAuthor().isBot()) {
            return;
        }

        Message repliedMessage = event.getMessage().getReferencedMessage();

        String baseText = repliedMessage == null ? this.integration.getConfig().messages.chatMessageFormat : this.integration.getConfig().messages.chatMessageFormatReply;

        TextNode attachmentInfo;
        if (!event.getMessage().getAttachments().isEmpty()) {
            List<TextNode> attachments = new ArrayList<>(List.of(TextNode.of("\nAttachments:")));
            for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                attachments.add(TextReplacer.create()
                        .replace("link", attachment.getUrl())
                        .replace("name", attachment.getFileName())
                        .applyNode(this.integration.getConfig().messages.attachmentFormat));
            }
            attachmentInfo = TextNode.wrap(attachments);
        } else {
            attachmentInfo = TextNode.empty();
        }

        String replyUser = repliedMessage == null ? "%userRepliedTo%" : (repliedMessage.getMember() == null ? repliedMessage.getAuthor().getEffectiveName() : repliedMessage.getMember().getEffectiveName());
        sendMcChatMessage(TextReplacer.create()
                .replace("msg", Utils.parseUrls(event.getMessage().getContentDisplay(), this.integration.getConfig()))
                .replace("user",  event.getMember().getEffectiveName())
                .replace("userRepliedTo", replyUser)
                .replace("attachments", attachmentInfo)
                .apply(baseText));
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        sendStackedMessage(
                this.integration.getConfig().messages.playerJoinMessage
                        .replace("%user%", player.getName().getString()),
                null
        );
        updateRichPresence(1);
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.stopped) {
            return;
        }

        sendStackedMessage(this.integration.getConfig().messages.playerLeaveMessage
                .replace("%user%", player.getName().getString()),
                null);
        updateRichPresence(-1);
    }

    private void updateRichPresence(int modifier) {
        if (!this.integration.getConfig().showPlayerCountStatus) {
            return;
        }
        long playerCount = this.integration.getServer().getPlayerManager().getPlayerList().stream()
                .filter(p -> !this.integration.getVanishIntegration().isVanished(p)).count() + modifier;
        this.integration.getJda().getPresence().setPresence(
                Activity.playing(switch ((int) playerCount) {
                    case 0 -> this.integration.getConfig().messages.onlineCountZero;
                    case 1 -> this.integration.getConfig().messages.onlineCountSingular;
                    default -> this.integration.getConfig().messages.onlineCountPlural.formatted(playerCount);
                }),
                false);
    }

    public void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        if (this.integration.getConfig().broadCastDeathMessages) {
            String message = source.getDeathMessage(player).getString();
            sendStackedMessage(message, null);
        }
    }

    public void onReceiveAdvancement(ServerPlayerEntity player, AdvancementDisplay advancement){
        if(this.integration.getConfig().announceAdvancements) {
            sendStackedMessage(
                    this.integration.getConfig().messages.advancementMessage
                            .replace("%user%", player.getName().getString())
                            .replace("%title%", advancement.getTitle().getString())
                            .replace("%description%", advancement.getDescription().getString()),
                    null);
        }
    }

    public void sendMcChatMessage(Text message) {
        this.integration.getServer().getPlayerManager().broadcast(message, false);
    }

    private void onServerStopping(MinecraftServer minecraftServer) {
        this.channel.sendMessage(this.integration.getConfig().messages.stopMessage).queue();
        this.stopped = true;
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
        CompletableFuture<Message> messageCompletableFuture = this.channel.sendMessage(message).submit();
        try {
            this.lastMessageId = messageCompletableFuture.get().getIdLong();
        } catch (Exception e) {
            LogUtils.getLogger().error("message return not successful: {}", String.valueOf(e));
        }
    }

    private void editMiscMessageToDiscord(String message, long messageId) {
        this.channel.editMessageById(messageId, message).queue();
    }

    private void sendPlayerMessageToDiscord(String message, ServerPlayerEntity sender, boolean shouldDelay) {
        if (this.webhook != null) {
            sendAsWebhook(message, sender);
        } else {
            String formattedMessage = sender.getName() + ": " + message;
            CompletableFuture<Message> messageCompletableFuture = this.channel.sendMessage(formattedMessage).submit();
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
            this.channel.editMessageById(messageId, formattedMessage).queue();
        }
    }


    private String getAvatarUrl(ServerPlayerEntity player) {
        return this.integration.getConfig().avatarUrl
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
        CompletableFuture<ReadonlyMessage> readonlyMessageCompletableFuture = this.webhookClient.send(msg);
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
