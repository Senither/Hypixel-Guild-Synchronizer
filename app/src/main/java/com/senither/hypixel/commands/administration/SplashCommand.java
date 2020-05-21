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

import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.contracts.commands.Command;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.time.Carbon;
import com.senither.hypixel.utils.NumberUtil;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplashCommand extends Command {

    private static final Logger log = LoggerFactory.getLogger(SplashCommand.class);

    private final Pattern timeRegEx = Pattern.compile("([0-9]+[w|d|h|m|s])");

    public SplashCommand(SkyblockAssistant app) {
        super(app);
    }

    @Override
    public String getName() {
        return "Splash Command";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList("");
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList("");
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList("");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("splash", "splashes");
    }

    @Override
    public void onCommand(MessageReceivedEvent event, String[] args) {
        GuildController.GuildEntry guildEntry = GuildController.getGuildById(app.getDatabaseManager(), event.getGuild().getIdLong());
        if (guildEntry == null) {
            MessageFactory.makeError(event.getMessage(),
                "The server is not currently setup with a guild, you must setup "
                    + "the server with a guild before you can use this command!"
            ).setTitle("Server is not setup").queue();
            return;
        }

        if (!guildEntry.isSplashTrackerEnabled()) {
            MessageFactory.makeError(event.getMessage(),
                "The splash tracker feature have not yet been enabled for the server, you "
                    + "must setup the feature before being able to use this command, you can enable the "
                    + "feature by running:"
                    + "\n```h!settings splash <channel> <role>```"
            ).setTitle("Splash tracker is not setup").queue();
            return;
        }

        if (args.length == 0) {
            MessageFactory.makeError(event.getMessage(), "Some error")
                .queue();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                showLeaderboard(guildEntry, event, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "remove":
            case "revoke":
                removeSplash(guildEntry, event, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "edit":
                editSplash(guildEntry, event, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "show":
            case "look":
            case "view":
            case "lookup":
            case "overview":
                lookupSplashForPlayer(guildEntry, event, Arrays.copyOfRange(args, 1, args.length));
                break;

            default:
                createSplash(guildEntry, event, args);
        }
    }

    private void showLeaderboard(GuildController.GuildEntry guildEntry, MessageReceivedEvent event, String[] args) {

    }

    private void removeSplash(GuildController.GuildEntry guildEntry, MessageReceivedEvent event, String[] args) {

    }

    private void editSplash(GuildController.GuildEntry guildEntry, MessageReceivedEvent event, String[] args) {
        if (args.length == 0) {
            log.info("Splash (No Args): {}", app.getSplashManager().getEarliestSplashFromUser(event.getMember().getIdLong()).getNote());
        } else {
            log.info("Splash (With Args): {}", app.getSplashManager().getPendingSplashById(Long.parseLong(args[0])).getNote());
        }
    }

    private void lookupSplashForPlayer(GuildController.GuildEntry guildEntry, MessageReceivedEvent event, String[] args) {

    }

    private void createSplash(GuildController.GuildEntry guildEntry, MessageReceivedEvent event, String[] args) {
        TextChannel splashChannel = app.getShardManager().getTextChannelById(guildEntry.getSplashChannel());
        if (splashChannel == null) {
            MessageFactory.makeError(event.getMessage(), "The splash channel does not appear to exist, have it been deleted?")
                .queue();

            return;
        }

        Carbon time = parseTime(args[0]);
        if (time == null) {
            MessageFactory.makeError(event.getMessage(), "Invalid time error").queue();
            return;
        }

        String note = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (note.isEmpty()) {
            MessageFactory.makeWarning(event.getMessage(), "You must include a note for the splash, something like the location, or what is being splashed.")
                .setTitle("Missing splash note")
                .queue();
            return;
        }

        try {
            app.getSplashManager().createSplash(
                splashChannel,
                event.getAuthor(),
                time,
                String.join(" ", Arrays.copyOfRange(args, 1, args.length))
            ).get();

            MessageFactory.makeInfo(event.getMessage(),
                "The splash have been registered successfully!"
            )
                .setTitle("Splash has been created!")
                .setFooter("Splasher: " + event.getAuthor().getAsTag())
                .queue();

            event.getMessage().delete().queue();
        } catch (InterruptedException | ExecutionException e) {
            MessageFactory.makeError(event.getMessage(),
                "Something went wrong while trying to register the splash, error: " + e.getMessage()
            ).queue();

            event.getMessage().delete().queue();

            log.error("Something went wrong while trying to register splash, error: {}", e.getMessage(), e);
        }
    }

    private Carbon parseTime(String string) {
        if ("now".equalsIgnoreCase(string)) {
            return Carbon.now();
        }

        Matcher matcher = timeRegEx.matcher(string);
        if (!matcher.find()) {
            return null;
        }

        Carbon time = Carbon.now();
        do {
            String group = matcher.group();

            String type = group.substring(group.length() - 1, group.length());
            int timeToAdd = NumberUtil.parseInt(group.substring(0, group.length() - 1), 0);

            switch (type.toLowerCase()) {
                case "w":
                    time.addWeeks(timeToAdd);
                    break;

                case "d":
                    time.addDays(timeToAdd);
                    break;

                case "h":
                    time.addHours(timeToAdd);
                    break;

                case "m":
                    time.addMinutes(timeToAdd);
                    break;

                case "s":
                    time.addSeconds(timeToAdd);
                    break;
            }
        } while (matcher.find());

        return time;
    }
}