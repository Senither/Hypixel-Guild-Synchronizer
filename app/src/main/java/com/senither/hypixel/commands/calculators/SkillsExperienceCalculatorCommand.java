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

package com.senither.hypixel.commands.calculators;

import com.google.common.collect.ImmutableMultiset;
import com.google.gson.JsonObject;
import com.senither.hypixel.Constants;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.chat.MessageType;
import com.senither.hypixel.contracts.commands.CalculatorCommand;
import com.senither.hypixel.statistics.StatisticsChecker;
import com.senither.hypixel.statistics.responses.DungeonResponse;
import com.senither.hypixel.statistics.responses.SkillsResponse;
import com.senither.hypixel.statistics.responses.SlayerResponse;
import com.senither.hypixel.statistics.weight.SlayerWeight;
import com.senither.hypixel.time.Carbon;
import com.senither.hypixel.utils.NumberUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.hypixel.api.reply.PlayerReply;
import net.hypixel.api.reply.skyblock.SkyBlockProfileReply;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SkillsExperienceCalculatorCommand extends CalculatorCommand {

    public SkillsExperienceCalculatorCommand(SkyblockAssistant app) {
        super(app);
    }

    @Override
    public String getName() {
        return "Experience Calculator";
    }

    @Override
    public List<String> getDescription() {
        return Collections.singletonList(
            "Calculates the level you'll get to after gaining the given amount of XP for any given skill, dungeon class, or slayer type."
        );
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <type> <xp>` - Calculates the level you'll get after gaining the given amount of XP."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command combat 75000` - Calculates your level after getting 75,000 XP.",
            "`:command enchanting 20k` - Calculates your level after getting 20,000 XP.",
            "`:command farming 1.5m` - Calculates your level after getting 1,500,000 XP.",
            "`:command mage 50m` - Calculates your level after getting 50,000,000 XP.",
            "`:command sven 10m` - Calculates your level after getting 10,000,000 XP."
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("calcxp", "xp");
    }

    @Override
    public void onCommand(MessageReceivedEvent event, String[] args) {
        if (args.length == 0) {
            MessageFactory.makeError(event.getMessage(),
                "You must specify the skill or slayer type you want the calculate the level XP for."
            ).setTitle("Missing argument").queue();
            return;
        }

        if (args.length == 1) {
            MessageFactory.makeError(event.getMessage(),
                "You must specify the amount of XP want to use in the calculation."
            ).setTitle("Missing argument").queue();
            return;
        }

        String username = getUsernameFromUser(event.getAuthor());
        if (username == null) {
            MessageFactory.makeError(event.getMessage(), "Failed to load your minecraft username, please try again later.").queue();
            return;
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle("Loading Skyblock profile data for " + username + "!")
            .setDescription("Loading Skyblock profile data for " + username + "!")
            .setColor(MessageType.INFO.getColor());

        event.getChannel().sendMessage(embedBuilder.build()).queue(message -> {
            try {
                PlayerReply playerReply = app.getHypixel().getPlayerByName(username).get(5, TimeUnit.SECONDS);
                if (playerReply == null || playerReply.getPlayer() == null) {
                    message.editMessage(new EmbedBuilder()
                        .setColor(MessageType.ERROR.getColor())
                        .setDescription(String.format("Failed to load player data for **%s**, found no valid player data, does the user exists?",
                            username
                        ))
                        .build()
                    ).queue();
                    return;
                }

                SkyBlockProfileReply profileReply = app.getHypixel().getSelectedSkyBlockProfileFromUsername(username).get(5, TimeUnit.SECONDS);
                JsonObject member = profileReply.getProfile().getAsJsonObject("members").getAsJsonObject(playerReply.getPlayer().get("uuid").getAsString());

                SkillsResponse skillsResponse = StatisticsChecker.SKILLS.checkUser(playerReply, profileReply, member);
                DungeonResponse dungeonResponse = StatisticsChecker.DUNGEON.checkUser(playerReply, profileReply, member);

                //noinspection rawtypes
                CalculatorCommand.SkillType type = getSkillTypeFromName(args[0], skillsResponse, dungeonResponse);

                if (type == null) {
                    type = getSlayerTypeFromName(args[0], StatisticsChecker.SLAYER.checkUser(playerReply, profileReply, member));
                }

                if (type == null) {
                    message.editMessage(embedBuilder
                        .setColor(MessageType.WARNING.getColor())
                        .setTitle("Invalid skill or slayer type given!")
                        .setDescription(String.join("\n", Arrays.asList(
                            "Invalid skill type provided, the skill type must be one of the following:",
                            "`mining`, `foraging`, `enchanting`, `farming`, `combat`, `fishing`, `alchemy`, "
                                + "`taming`, `catacomb`, `healer`, `mage`, `berserk`, `archer`, `tank`, "
                                + "`sven`, `tara`, or `rev`"
                        )))
                        .build()
                    ).queue();
                    return;
                }

                if (type.getStat().getLevel() >= type.getExperienceList().size()) {
                    EmbedBuilder builder = embedBuilder
                        .setColor(MessageType.INFO.getColor())
                        .setTitle("Already max level")
                        .setDescription(String.format(
                            "You have already reached the max level in the **%s** %s.",
                            type.getName(),
                            type.getType().equals(SkillCalculationType.SLAYERS) ? "slayer" : "skill"
                        ));

                    if (!type.getType().equals(SkillCalculationType.RUNECRAFTING)) {
                        double experience = getExperienceFromString(args[1]);

                        builder.addField("Weight Change", String.format(
                            "You'll get the following weight changes after getting **%s** %s XP!\n`%s` \uD83E\uDC52 `%s`",
                            NumberUtil.formatNicely(experience),
                            type.getName(),
                            type.calculateWeight(type.getStat().getExperience()),
                            type.calculateWeight(type.getStat().getExperience() + experience)
                        ), true);
                    }

                    message.editMessage(builder.build()).queue();
                    return;
                }

                double experience = getExperienceFromString(args[1]);
                double skillXp = (type.getStat().getExperience() == -1
                    ? getExperienceForLevel(type.getExperienceList(), (int) type.getStat().getLevel())
                    : type.getStat().getExperience());

                double combinedXp = experience + skillXp;
                double newLevel = Math.min(
                    type.getSkillType() == null ? type.getExperienceList().size() : type.getSkillType().getMaxLevel(),
                    getLevelFromExperience(type, type.getExperienceList(), combinedXp)
                );

                String note = newLevel >= type.getExperienceList().size() ?
                    String.format("You'll reach level **%s** and max out your %s %s after gaining **%s** XP!",
                        type.getExperienceList().size(), type.getName(),
                        type.getType().equals(SkillCalculationType.SLAYERS) ? "slayer" : "skill",
                        NumberUtil.formatNicelyWithDecimals(experience)
                    ) :
                    String.format("You'll reach level **%s** after gaining **%s** %s XP!",
                        NumberUtil.formatNicelyWithDecimals(newLevel),
                        NumberUtil.formatNicely(experience),
                        type.getName()
                    );

                if (type.getType().equals(SkillCalculationType.GENERAL)) {
                    double previousAverage = skillsResponse.getAverageSkillLevel();

                    //noinspection unchecked
                    SkillsResponse newResponse = (SkillsResponse) type.setLevelAndExperience(skillsResponse, newLevel, combinedXp);

                    embedBuilder.addField("Average Change", String.format("`%s` \uD83E\uDC52 `%s`",
                        NumberUtil.formatNicelyWithDecimals(previousAverage),
                        NumberUtil.formatNicelyWithDecimals(newResponse.getAverageSkillLevel())
                    ), true);
                }

                if (!type.getType().equals(SkillCalculationType.RUNECRAFTING)) {
                    embedBuilder.addField("Weight Change", String.format("`%s` \uD83E\uDC52 `%s`",
                        type.calculateWeight(skillXp),
                        type.calculateWeight(combinedXp)
                    ), true);
                }

                message.editMessage(embedBuilder
                    .setTitle(type.getName() + " XP Calculation for " + username)
                    .setDescription(String.format("You're currently %s level **%s** with **%s** XP!\n" + note,
                        type.getName(),
                        NumberUtil.formatNicelyWithDecimals(type.getStat().getLevel()),
                        NumberUtil.formatNicelyWithDecimals(skillXp)
                    ))
                    .setColor(MessageType.SUCCESS.getColor())
                    .setFooter(String.format("Profile: %s", profileReply.getProfile().get("cute_name").getAsString()))
                    .setTimestamp(Carbon.now().setTimestamp(member.get("last_save").getAsLong() / 1000L).getTime().toInstant())
                    .build()
                ).queue();
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                sendExceptionMessage(message, embedBuilder, e);
            }
        });
    }

    private SkillType getSlayerTypeFromName(String name, SlayerResponse slayerResponse) {
        switch (name.toLowerCase()) {
            case "rev":
            case "zombie":
            case "zombies":
            case "revenant":
            case "revenants":
                return new SkillType<SlayerResponse>(
                    "Revenant",
                    slayerResponse.getRevenant(),
                    SkillCalculationType.SLAYERS,
                    (response, level, experience) -> {
                        response.setRevenant(
                            (int) experience,
                            response.getRevenant().getTier1Kills(),
                            response.getRevenant().getTier2Kills(),
                            response.getRevenant().getTier3Kills(),
                            response.getRevenant().getTier4Kills(),
                            response.getRevenant().getTier5Kills()
                        );

                        return response;
                    },
                    SlayerWeight.REVENANT::calculateSkillWeight
                );

            case "tara":
            case "spider":
            case "tarantula":
                return new SkillType<SlayerResponse>(
                    "Tarantula",
                    slayerResponse.getTarantula(),
                    SkillCalculationType.SLAYERS,
                    (response, level, experience) -> {
                        response.setTarantula(
                            (int) experience,
                            response.getTarantula().getTier1Kills(),
                            response.getTarantula().getTier2Kills(),
                            response.getTarantula().getTier3Kills(),
                            response.getTarantula().getTier4Kills(),
                            response.getTarantula().getTier5Kills()
                        );

                        return response;
                    },
                    SlayerWeight.TARANTULA::calculateSkillWeight
                );

            case "dog":
            case "wolf":
            case "sven":
                return new SkillType<SlayerResponse>(
                    "Sven",
                    slayerResponse.getSven(),
                    SkillCalculationType.SLAYERS,
                    (response, level, experience) -> {
                        response.setSven(
                            (int) experience,
                            response.getSven().getTier1Kills(),
                            response.getSven().getTier2Kills(),
                            response.getSven().getTier3Kills(),
                            response.getSven().getTier4Kills(),
                            response.getSven().getTier5Kills()
                        );

                        return response;
                    },
                    SlayerWeight.SVEN::calculateSkillWeight
                );

            case "ender":
            case "enderman":
                return new SkillType<SlayerResponse>(
                    "Enderman",
                    slayerResponse.getEnderman(),
                    SkillCalculationType.SLAYERS,
                    (response, level, experience) -> {
                        response.setEnderman(
                            (int) experience,
                            response.getEnderman().getTier1Kills(),
                            response.getEnderman().getTier2Kills(),
                            response.getEnderman().getTier3Kills(),
                            response.getEnderman().getTier4Kills(),
                            response.getEnderman().getTier5Kills()
                        );

                        return response;
                    },
                    SlayerWeight.ENDERMAN::calculateSkillWeight
                );
        }
        return null;
    }

    private double getExperienceFromString(String experience) {
        try {
            return Double.max(Double.parseDouble(experience), 0);
        } catch (NumberFormatException e) {
            if (experience.toLowerCase().endsWith("m")) {
                return Double.max(NumberUtil.parseDouble(experience.substring(0, experience.length() - 1), 0) * 1000000, 0);
            } else if (experience.toLowerCase().endsWith("k")) {
                return Double.max(NumberUtil.parseDouble(experience.substring(0, experience.length() - 1), 0) * 1000, 0);
            } else {
                return 0D;
            }
        }
    }

    private double getExperienceForLevel(ImmutableMultiset<Integer> experience, int level) {
        double totalRequiredExperience = 0;
        for (int i = 0; i < Math.min(level, experience.size()); i++) {
            totalRequiredExperience += experience.asList().get(i);
        }
        return totalRequiredExperience;
    }

    private double getLevelFromExperience(SkillType type, ImmutableMultiset<Integer> experience, double exp) {
        if (type.getType().equals(SkillCalculationType.SLAYERS)) {
            for (int level = 0; level < experience.size(); level++) {
                double requirement = experience.asList().get(level);
                if (exp < requirement) {
                    double lastRequirement = level == 0 ? 0D : Constants.SLAYER_EXPERIENCE.asList().get(level - 1);
                    return level + (exp - lastRequirement) / (requirement - lastRequirement);
                }
            }
            return 9D;
        }

        int level = 0;
        for (int toRemove : experience) {
            exp -= toRemove;
            if (exp < 0) {
                return level + (1D - (exp * -1) / (double) toRemove);
            }
            level++;
        }
        return level;
    }
}
