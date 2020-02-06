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

package com.senither.hypixel.commands.administration;

import com.senither.hypixel.Constants;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.chat.PlaceholderMessage;
import com.senither.hypixel.contracts.commands.SkillCommand;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.exceptions.FriendlyException;
import com.senither.hypixel.rank.RankCheckResponse;
import com.senither.hypixel.rank.RankRequirementType;
import com.senither.hypixel.rank.items.PowerOrb;
import com.senither.hypixel.utils.NumberUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.hypixel.api.reply.GuildReply;
import net.hypixel.api.reply.PlayerReply;
import net.hypixel.api.reply.skyblock.SkyBlockProfileReply;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class RankCheckCommand extends SkillCommand {

    public RankCheckCommand(SkyblockAssistant app) {
        super(app, "Rank Check");
    }

    @Override
    public String getName() {
        return "Rank Check";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
            "This command can be used in combination with the `:commandrank-requirement` command to check",
            "what rank a given user falls under using their currently fairy soul, gear, armor,",
            "slayer, power orb, and average skill level stats."
        );
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <username> [profile]` - Check what rank the given user meets the requirements for."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList(
            "`:command Senither`"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("rank-check", "rankcheck");
    }

    @Override
    protected boolean prepareCommand(MessageReceivedEvent event, String username) {
        GuildController.GuildEntry guildEntry = GuildController.getGuildById(app.getDatabaseManager(), event.getGuild().getIdLong());
        if (guildEntry == null) {
            MessageFactory.makeError(event.getMessage(),
                "The server is not currently setup with a guild, you must setup "
                    + "the server with a guild before you can use this command!"
            ).setTitle("Server is not setup").queue();
            return false;
        }

        if (guildEntry.getRankRequirements().isEmpty()) {
            MessageFactory.makeError(event.getMessage(),
                "There are currently no rank requirements setup for the server, you "
                    + "must first setup the requirements for the different guild ranks you want "
                    + "to be able to check for using `:command`."
            )
                .set("command", Constants.COMMAND_PREFIX + "rank-requirement")
                .setTitle("Rank Requirements is not setup")
                .queue();
            return false;
        }

        if (!isGuildMasterOrOfficerOfServerGuild(event, guildEntry)) {
            MessageFactory.makeError(event.getMessage(),
                "You must be the guild master or an officer of the **:name** guild to use this command!"
            ).set("name", guildEntry.getName()).setTitle("Missing permissions").queue();
            return false;
        }

        return true;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    protected void handleSkyblockProfile(Message message, SkyBlockProfileReply profileReply, PlayerReply playerReply) {
        GuildController.GuildEntry guildEntry = GuildController.getGuildById(app.getDatabaseManager(), message.getGuild().getIdLong());
        GuildReply guildReply = app.getHypixel().getGson().fromJson(guildEntry.getData(), GuildReply.class);
        String uuidAsString = playerReply.getPlayer().get("uuid").getAsString();

        UUID uuid = UUID.fromString(
            uuidAsString.substring(0, 8) + "-" +
                uuidAsString.substring(8, 12) + "-" +
                uuidAsString.substring(12, 16) + "-" +
                uuidAsString.substring(16, 20) + "-" +
                uuidAsString.substring(20, 32)
        );

        PlaceholderMessage placeholderMessage = MessageFactory.makeSuccess(message, "**:user** has **:amount** out of **190** fairy souls!")
            .setTitle(playerReply.getPlayer().get("displayname").getAsString() + "'s Rank Check")
            .set("user", playerReply.getPlayer().get("displayname").getAsString());

        RankCheckResponse rankCheckResponse = RankRequirementType.FAIRY_SOULS.getChecker().getRankForUser(guildEntry, guildReply, profileReply, uuid);
        placeholderMessage.set("amount", rankCheckResponse.getMetric().getOrDefault("amount", 0));
        placeholderMessage
            .addField(RankRequirementType.AVERAGE_SKILLS.getName(), getRankForType(
                RankRequirementType.AVERAGE_SKILLS, guildEntry, guildReply, profileReply, uuid, response -> {
                    return formatRank(response) + NumberUtil.formatNicelyWithDecimals(
                        (Double) response.getMetric().getOrDefault("amount", 0D)
                    ) + " Average Skill";
                }
            ), true)
            .addField(RankRequirementType.SLAYER.getName(), getRankForType(
                RankRequirementType.SLAYER, guildEntry, guildReply, profileReply, uuid, response -> {
                    return formatRank(response) + NumberUtil.formatNicely(
                        (Long) response.getMetric().getOrDefault("amount", 0)
                    ) + " Total XP";
                }
            ), true)
            .addField(RankRequirementType.TALISMANS.getName(), getRankForType(
                RankRequirementType.TALISMANS, guildEntry, guildReply, profileReply, uuid, response -> {
                    int legendaries = (int) response.getMetric().get("legendaries");
                    int epics = (int) response.getMetric().get("epics");

                    return formatRank(response) + String.format("%s Legendaries & %s Epics", legendaries, epics);
                }
            ), true)
            .addField(RankRequirementType.POWER_ORBS.getName(), getRankForType(
                RankRequirementType.POWER_ORBS, guildEntry, guildReply, profileReply, uuid, response -> {
                    PowerOrb powerOrb = (PowerOrb) response.getMetric().get("item");

                    return formatRank(response) + powerOrb.getName();
                }
            ), true)
            .addField(RankRequirementType.ARMOR.getName(), getRankForType(
                RankRequirementType.ARMOR, guildEntry, guildReply, profileReply, uuid, this::formatRank
            ), true)
            .addField(RankRequirementType.WEAPONS.getName(), getRankForType(
                RankRequirementType.WEAPONS, guildEntry, guildReply, profileReply, uuid, this::formatRank
            ), true);

        message.editMessage(placeholderMessage.buildEmbed()).queue();
    }

    private String formatRank(RankCheckResponse response) {
        return "__" + response.getRank().getName() + "__\n";
    }

    private String getRankForType(
        RankRequirementType rankRequirementType,
        GuildController.GuildEntry guildEntry,
        GuildReply guildReply,
        SkyBlockProfileReply profileReply,
        UUID playerUUID,
        Function<RankCheckResponse, String> metricsCallback
    ) {
        try {
            RankCheckResponse response = rankRequirementType.getChecker().getRankForUser(
                guildEntry, guildReply, profileReply, playerUUID
            );

            if (response == null || response.getRank() == null) {
                return "_Unranked_";
            }

            String metricMessage = metricsCallback.apply(response);
            if (metricMessage == null) {
                return response.getRank().getName();
            }
            return metricMessage;
        } catch (FriendlyException e) {
            return "API is Disabled!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Unknown error occurred!";
        }
    }
}
