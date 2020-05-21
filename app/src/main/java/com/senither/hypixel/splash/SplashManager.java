package com.senither.hypixel.splash;

import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.chat.PlaceholderMessage;
import com.senither.hypixel.database.collection.DataRow;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.time.Carbon;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SplashManager {

    private static final Logger log = LoggerFactory.getLogger(SplashManager.class);
    private static final int endingSoonTimer = 300;

    private final SkyblockAssistant app;
    private final Set<SplashContainer> splashes;

    public SplashManager(SkyblockAssistant app) {
        this.app = app;
        this.splashes = new HashSet<>();

        try {
            for (DataRow row : app.getDatabaseManager().query("SELECT * FROM `splashes` WHERE `splash_at` > ?", Carbon.now())) {
                splashes.add(new SplashContainer(
                    row.getLong("id"),
                    row.getLong("discord_id"),
                    row.getLong("user_id"),
                    row.getLong("message_id"),
                    row.getTimestamp("splash_at"),
                    row.getString("note")
                ));
            }
        } catch (SQLException e) {
            log.error("A SQL exception were thrown while loading splashes from the database, error: {}", e.getMessage(), e);
        }
    }

    public static int getEndingSoonTimer() {
        return endingSoonTimer;
    }

    public Set<SplashContainer> getSplashes() {
        return splashes;
    }

    public SplashContainer getPendingSplashById(long id) {
        for (SplashContainer splash : getSplashes()) {
            if (splash.getId() == id) {
                return splash;
            }
        }
        return null;
    }

    public SplashContainer getEarliestSplashFromUser(long userId) {
        return getSplashes().stream().filter(splashContainer -> {
            return splashContainer.getUserId() == userId;
        }).min((o1, o2) -> {
            return Math.toIntExact(o1.getTime().getTimestamp() - o2.getTime().getTimestamp());
        }).orElse(null);
    }

    public void updateSplashFor(SplashContainer splash) {
        GuildController.GuildEntry guild = GuildController.getGuildById(app.getDatabaseManager(), splash.getDiscordId());
        if (guild == null || !guild.isSplashTrackerEnabled()) {
            return;
        }

        User userById = app.getShardManager().getUserById(splash.getUserId());
        if (userById == null) {
            return;
        }

        TextChannel channelById = app.getShardManager().getTextChannelById(guild.getSplashChannel());
        if (channelById == null) {
            return;
        }

        if (splash.isEndingSoon() && splash.getTime().getTimestamp() - splash.getLastUpdatedAt() > endingSoonTimer) {
            channelById.deleteMessageById(splash.getMessageId()).queue(null, null);

            channelById.sendMessage(buildSplashMessage(
                userById, splash.getTime(), splash.getNote(), splash.getId()
            )).queue(message -> {
                try {
                    splash.setMessageId(message.getIdLong());

                    app.getDatabaseManager().queryUpdate("UPDATE `splashes` SET `message_id` = ? WHERE `discord_id` = ? and `message_id` = ?",
                        message.getIdLong(), splash.getDiscordId(), splash.getMessageId()
                    );
                } catch (SQLException e) {
                    log.error("Something went wrong while trying to send \"ending soon\" splash message, error: {}", e.getMessage(), e);
                }
            });
        } else {
            channelById.editMessageById(splash.getMessageId(), buildSplashMessage(
                userById, splash.getTime(), splash.getNote(), splash.getId()
            )).queue(null, null);
        }
    }

    public CompletableFuture<Void> createSplash(TextChannel channel, User author, Carbon time, String note) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        final boolean isNow = time.diffInSeconds(Carbon.now()) <= 5;

        channel.sendMessage(buildSplashMessage(author, time, note, null)).queue(message -> {
            try {
                String encodedNote = "base64:" + new String(Base64.getEncoder().encode(note.getBytes()));
                Set<Long> ids = app.getDatabaseManager().queryInsert(
                    "INSERT INTO `splashes` SET `discord_id` = ?, `user_id` = ?, `message_id` = ?, `note` = ?, `splash_at` = ?",
                    channel.getGuild().getIdLong(),
                    author.getIdLong(),
                    message.getIdLong(),
                    encodedNote,
                    time
                );

                Long splashEntryId = ids.iterator().next();

                if (!isNow) {
                    splashes.add(new SplashContainer(
                        splashEntryId,
                        channel.getGuild().getIdLong(),
                        author.getIdLong(),
                        message.getIdLong(),
                        time, note
                    ));
                }

                message.editMessage(buildSplashMessage(author, time, note, splashEntryId)).queue();

                future.complete(null);
            } catch (SQLException throwable) {
                future.completeExceptionally(throwable);
            }
        }, future::completeExceptionally);

        return future;
    }

    private Message buildSplashMessage(User author, Carbon time, String note, Long id) {
        String description = ":user is splashing :time!";
        if (note != null && note.trim().length() > 0) {
            description += "\n\n> :note";
        }

        PlaceholderMessage embedMessage = MessageFactory.makeEmbeddedMessage(null)
            .setTimestamp(time.getTime().toInstant())
            .setDescription(description)
            .set("user", author.getAsMention())
            .set("note", note)
            .set("time", time.diffInSeconds(Carbon.now()) > 5
                ? "in " + time.diffForHumans()
                : "now"
            );

        if (id != null) {
            embedMessage.setFooter("Splash ID: " + id);
        }

        MessageBuilder builder = new MessageBuilder()
            .setEmbed(embedMessage.buildEmbed());

        if (time.diffInSeconds(Carbon.now()) <= endingSoonTimer) {
            builder.setContent("@everyone");
        }

        return builder.build();
    }
}