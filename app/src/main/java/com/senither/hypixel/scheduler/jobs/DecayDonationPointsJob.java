/*
 * Copyright (c) 2020.
 *
 * This file is part of Hypixel Skyblock Assistant.
 *
 * Hypixel Guild Synchronizer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hypixel Guild Synchronizer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hypixel Guild Synchronizer.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.senither.hypixel.scheduler.jobs;

import com.mysql.cj.ServerVersion;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.chat.MessageType;
import com.senither.hypixel.contracts.scheduler.Job;
import com.senither.hypixel.database.collection.Collection;
import com.senither.hypixel.database.collection.DataRow;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.time.Carbon;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.api.reply.GuildReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class DecayDonationPointsJob extends Job {

    private static final Logger log = LoggerFactory.getLogger(DecayDonationPointsJob.class);

    public DecayDonationPointsJob(SkyblockAssistant app) {
        super(app, 1, 15, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        try {
            for (DataRow dataRow : app.getDatabaseManager().query(
                "SELECT `discord_id` FROM `guilds` WHERE `donation_time` IS NOT NULL AND `donation_points` IS NOT NULL;"
            )) {
                GuildController.GuildEntry guild = GuildController.getGuildById(app.getDatabaseManager(), dataRow.getLong("discord_id"));
                if (guild == null) {
                    continue;
                }

                GuildReply guildReply = app.getHypixel().getGson().fromJson(guild.getData(), GuildReply.class);
                if (guildReply == null) {
                    continue;
                }

                TextChannel notificationChannel = null;
                if (guild.getDonationNotificationChannel() != null) {
                    notificationChannel = app.getShardManager().getTextChannelById(guild.getDonationNotificationChannel());
                }

                HashSet<String> memberIds = new HashSet<>();
                for (GuildReply.Guild.Member member : guildReply.getGuild().getMembers()) {
                    memberIds.add(member.getUuid().toString());
                }

                Carbon time = Carbon.now().subHours(guild.getDonationTime());
                Collection updatePlayers = app.getDatabaseManager().query(String.format(
                    "SELECT\n" +
                        "    `donation_points`.`uuid`,\n" +
                        "    `donation_points`.`points`,\n" +
                        "    `donation_points`.`last_donated_at`,\n" +
                        "    `uuids`.`discord_id`,\n" +
                        "    `uuids`.`username`\n" +
                        "FROM\n" +
                        "    `donation_points`\n" +
                        "LEFT JOIN `uuids` ON `donation_points`.`uuid` = `uuids`.`uuid`\n" +
                        "WHERE `donation_points`.`discord_id` = ?\n" +
                        "  AND `donation_points`.`last_checked_at` < ?\n" +
                        "  AND `donation_points`.`uuid` IN (%s)",
                    "'" + String.join("', '", memberIds) + "'"
                ), guild.getDiscordId(), time);

                if (updatePlayers.isEmpty()) {
                    log.debug("Found no players that should be updated in {}, skipping!", guild.getDiscordId());
                    continue;
                }

                memberIds.clear();
                for (DataRow player : updatePlayers) {
                    long points = player.getLong("points");
                    if (points > 0 && points - guild.getDonationPoints() <= 0) {
                        notifyPlayer(app, guild, notificationChannel, player);
                    }
                    memberIds.add(player.getString("uuid"));
                }
                log.debug("Updating {} players donation points in {}", memberIds.size(), guild.getDiscordId());

                app.getDatabaseManager().queryUpdate(String.format(
                    "UPDATE `donation_points` SET `points` = `points` - ?, `last_checked_at` = ? WHERE `discord_id` = ? AND `last_checked_at` < ? AND `uuid` IN (%s)",
                    "'" + String.join("', '", memberIds) + "'"
                ), guild.getDonationPoints(), Carbon.now(), guild.getDiscordId(), time);
            }
        } catch (SQLException e) {
            log.error("An SQL exception where thrown while trying to update donation points: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Something went wrong in the decay donation points job: {}", e.getMessage(), e);
        } finally {
            try {
                app.getDatabaseManager().queryUpdate("UPDATE `donation_points` SET `points` = 0 WHERE `points` < 0");
            } catch (SQLException e) {
                log.error("An SQL exception where thrown while trying to reset donation points back to zero: {}", e.getMessage(), e);
            }
        }
    }

    private void notifyPlayer(SkyblockAssistant app, GuildController.GuildEntry guild, TextChannel notificationChannel, DataRow player) {
        if (notificationChannel != null) {
            MessageFactory.makeEmbeddedMessage(notificationChannel)
                .setColor(MessageType.WARNING.getColor())
                .setTitle(player.getString("username") + " has no points left!")
                .setDescription("**:name** is now at zero donation points!\nThey last donated :time.")
                .setTimestamp(Carbon.now().getTime().toInstant())
                .set("name", player.getString("username"))
                .set("time", player.getTimestamp("last_donated_at").diffForHumans())
                .queue();
        }

        long discordId = player.getLong("discord_id");
        if (discordId == 0L) {
            return;
        }

        User userById = app.getShardManager().getUserById(discordId);
        if (userById == null) {
            return;
        }

        userById.openPrivateChannel().queue(privateChannel -> {
            privateChannel.sendMessage(MessageFactory.createEmbeddedBuilder()
                .setColor(MessageType.WARNING.getColor())
                .setTitle("You're now at zero points!")
                .setDescription(String.format(String.join("\n",
                    "You're now at zero donation points in the **%s** guild!",
                    "Make sure you donate soon so you don't run the risk of getting kicked!",
                    "",
                    "You last donated %s."
                ), guild.getName(), player.getTimestamp("last_donated_at").diffForHumans()))
                .build()
            ).queue();
        }, null);
    }
}
