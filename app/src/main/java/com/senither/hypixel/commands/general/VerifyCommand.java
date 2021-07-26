/*
 * Copyright (c) 2019.
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

package com.senither.hypixel.commands.general;

import com.google.gson.JsonObject;
import com.senither.hypixel.Constants;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.chat.MessageType;
import com.senither.hypixel.chat.PlaceholderMessage;
import com.senither.hypixel.commands.middlewares.VerificationMiddleware;
import com.senither.hypixel.contracts.commands.Command;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.exceptions.FriendlyException;
import com.senither.hypixel.hypixel.HypixelRank;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.hypixel.api.reply.GuildReply;
import net.hypixel.api.reply.PlayerReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;

public class VerifyCommand extends Command {

    private static final Logger log = LoggerFactory.getLogger(VerifyCommand.class);

    public VerifyCommand(SkyblockAssistant app) {
        super(app, false);

        setThrottleContainer(2, 5);
    }

    @Override
    public String getName() {
        return "Account Verification";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
            "This command is used to verify your in-game Minecraft username with the bot,",
            "this is done by comparing your Hypixel profiles Discord social link with the",
            "account that ran the command, if they both match then your account will,",
            "be successfully verified."
        );
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <username>` - Verifies your Discord account by checking if the given Minecraft username is linked to your Discord account."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command Senither`");
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("verify");
    }

    @Override
    public void onCommand(MessageReceivedEvent event, String[] args) {
        if (args.length == 0) {
            MessageFactory.makeError(event.getMessage(), String.join("\n", Arrays.asList(
                "You must include your in-game Minecraft username that is linked with your Discord account on Hypixel!",
                "",
                "If your account isn't linked you can set it up by logging into `mc.hypixel.net`, then going to your profile, followed by the social button, and then setting your Discord account up there. Make sure your Discord social link is set to `:user` before attempting to verify again.",
                "Please note it might take up to a minute before the bot is able to see the changes.",
                "",
                "Try again using `:prefixverify <username>`"
            )))
                .set("prefix", Constants.COMMAND_PREFIX)
                .set("user", event.getAuthor().getAsTag())
                .setTitle("Missing username")
                .queue();
            return;
        }

        if (!app.getHypixel().isValidMinecraftUsername(args[0])) {
            MessageFactory.makeError(event.getMessage(), "Invalid Minecraft username given! You must give a valid username to be verified.")
                .queue();
            return;
        }

        PlaceholderMessage embedBuilder = MessageFactory.makeInfo(event.getMessage(), "Loading Hypixel player data for :name!")
            .set("name", args[0])
            .setTitle("Verifying Account");

        embedBuilder.queue(message -> app.getHypixel().getPlayerByName(args[0], true)
            .whenCompleteAsync(((playerResponse, throwable) -> {
                if (throwable == null) {
                    try {
                        handleResponse(event, message, playerResponse, embedBuilder);
                    } catch (Exception e) {
                        log.error("Unknown exception was caught while trying to handle verifying {}, error: {}",
                            event.getAuthor().getAsTag(), e.getMessage(), e
                        );
                    }
                    return;
                }

                String errorMessage = (throwable instanceof FriendlyException)
                    ? throwable.getMessage()
                    : "Something went wrong: " + throwable.getMessage();

                message.editMessage(embedBuilder
                    .setDescription(errorMessage)
                    .setColor(MessageType.ERROR.getColor())
                    .buildEmbed()
                ).queue();
            }))
        );
    }

    private void handleResponse(MessageReceivedEvent event, Message message, PlayerReply playerResponse, PlaceholderMessage embedBuilder) {
        if (playerResponse.getPlayer() == null) {
            message.editMessage(embedBuilder
                .setDescription("Found no Hypixel profile with the given username!")
                .setColor(MessageType.ERROR.getColor())
                .buildEmbed()
            ).queue();
            return;
        }

        JsonObject player = playerResponse.getPlayer();
        if (!hasDiscordLinked(player)) {
            sendNoSocialLinksMessage(message, event.getAuthor(), embedBuilder, player);
            return;
        }

        if (!player.getAsJsonObject("socialMedia").getAsJsonObject("links").get("DISCORD").getAsString().equals(event.getAuthor().getAsTag())) {
            sendNoSocialLinksMessage(message, event.getAuthor(), embedBuilder, player);
            return;
        }

        UUID uuid = null;
        try {
            uuid = app.getHypixel().getUUIDFromName(player.get("displayname").getAsString());
            if (uuid != null) {
                app.getDatabaseManager().queryUpdate("UPDATE `uuids` SET `discord_id` = NULL WHERE `discord_id` = ?",
                    event.getAuthor().getIdLong()
                );

                app.getDatabaseManager().queryUpdate("UPDATE `uuids` SET `discord_id` = ?, `username` = ? WHERE `uuid` = ?",
                    event.getAuthor().getIdLong(), player.get("displayname").getAsString(), uuid.toString()
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        clearUsernameCacheFor(event.getAuthor());
        VerificationMiddleware.clearVerificationCacheFor(event.getAuthor());

        message.editMessage(embedBuilder
            .setDescription("Your account has now been verified!")
            .setColor(MessageType.SUCCESS.getColor())
            .buildEmbed()
        ).queue();

        handleAutomaticAssignments(event, uuid, playerResponse);
    }

    private void handleAutomaticAssignments(MessageReceivedEvent event, UUID uuid, PlayerReply playerReply) {
        if (uuid == null || event.getMember() == null) {
            return;
        }

        if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();

        List<Role> verifiedRoles = event.getGuild().getRolesByName(Constants.VERIFY_ROLE, true);
        if (!verifiedRoles.isEmpty() && selfMember.canInteract(verifiedRoles.get(0))) {
            if (!hasRole(event.getMember(), verifiedRoles.get(0))) {
                log.debug("Adding {} to {}", verifiedRoles.get(0).getName(), event.getAuthor().getAsTag());

                event.getGuild().addRoleToMember(event.getMember(), verifiedRoles.get(0)).queue();
            }
        }

        HypixelRank rank = app.getHypixel().getRankFromPlayer(playerReply);
        if (!rank.isDefault()) {
            List<Role> hypixelRankRoles = event.getGuild().getRolesByName(rank.getName(), true);
            if (!hypixelRankRoles.isEmpty() && selfMember.canInteract(hypixelRankRoles.get(0))) {
                if (!hasRole(event.getMember(), hypixelRankRoles.get(0))) {
                    log.debug("Adding {} to {}", hypixelRankRoles.get(0).getName(), event.getAuthor().getAsTag());

                    event.getGuild().addRoleToMember(event.getMember(), hypixelRankRoles.get(0)).queue();
                }
            }
        }

        for (HypixelRank hypixelRank : HypixelRank.values()) {
            if (hypixelRank.isDefault() || hypixelRank == rank) {
                continue;
            }

            List<Role> rankRolesByName = event.getGuild().getRolesByName(hypixelRank.getName(), true);
            if (!rankRolesByName.isEmpty() && selfMember.canInteract(rankRolesByName.get(0))) {
                if (hasRole(event.getMember(), rankRolesByName.get(0))) {
                    log.debug("Removing {} from {}", rankRolesByName.get(0).getName(), event.getAuthor().getAsTag());

                    event.getGuild().removeRoleFromMember(event.getMember(), rankRolesByName.get(0)).queue();
                }
            }
        }

        GuildController.GuildEntry guildEntry = GuildController.getGuildById(app.getDatabaseManager(), event.getGuild().getIdLong());
        if (guildEntry == null) {
            return;
        }

        final String username = playerReply.getPlayer().get("displayname").getAsString();

        if (guildEntry.isAutoRename()) {
            //noinspection ConstantConditions
            if (!event.getMember().getEffectiveName().equalsIgnoreCase(username)) {
                event.getGuild().modifyNickname(event.getMember(), username).queue(null, null);
            }
        }

        GuildReply guildReply = app.getHypixel().getGson().fromJson(guildEntry.getData(), GuildReply.class);
        if (guildReply == null || guildReply.getGuild() == null) {
            return;
        }

        GuildReply.Guild.Member member = guildReply.getGuild().getMembers().stream()
            .filter(guildMember -> guildMember.getUuid().equals(uuid))
            .findFirst().orElse(null);

        if (member == null) {
            return;
        }

        // Adds the role that matches the users rank in-game if there are no guild member role setup
        if (guildEntry.getGuildMemberRole() == null) {
            HashMap<String, Role> discordRoles = new HashMap<>();
            for (GuildReply.Guild.Member guildMember : guildReply.getGuild().getMembers()) {
                if ("Guild Master".equalsIgnoreCase(guildMember.getRank())) {
                    continue;
                }

                if (discordRoles.containsKey(guildMember.getRank())) {
                    continue;
                }

                List<Role> rolesByName = event.getGuild().getRolesByName(guildMember.getRank(), false);
                if (rolesByName.isEmpty() || !selfMember.canInteract(rolesByName.get(0))) {
                    continue;
                }

                discordRoles.put(guildMember.getRank(), rolesByName.get(0));
            }

            Role role = discordRoles.getOrDefault(member.getRank(), null);
            if (role != null && selfMember.canInteract(role) && !hasRole(event.getMember(), role)) {
                log.debug("Adding {} to {}", role.getName(), event.getAuthor().getAsTag());

                event.getGuild().addRoleToMember(event.getMember(), role).queue();

                discordRoles.values().stream()
                    .filter(filteringRole -> filteringRole.getIdLong() != role.getIdLong())
                    .filter(filteringRole -> hasRole(event.getMember(), filteringRole))
                    .filter(selfMember::canInteract)
                    .forEach(removeRole -> {
                        log.debug("Removing {} from {}", removeRole.getName(), event.getAuthor().getAsTag());

                        event.getGuild().removeRoleFromMember(event.getMember(), removeRole).queue();
                    });
            }

            if (guildEntry.getDefaultRole() != null) {
                Role defaultRole = event.getGuild().getRoleById(guildEntry.getDefaultRole());
                if (defaultRole != null && selfMember.canInteract(defaultRole) && hasRole(event.getMember(), defaultRole)) {
                    log.debug("Removing {} from {}", defaultRole.getName(), event.getAuthor().getAsTag());

                    event.getGuild().removeRoleFromMember(event.getMember(), defaultRole).queue();
                }
            }
        }
        // Adds the guild member role to the user instead of the role matching their in-game
        // guild rank, since there is a default guild member role setup for the server.
        else {
            Role roleById = event.getGuild().getRoleById(guildEntry.getGuildMemberRole());
            if (roleById != null && selfMember.canInteract(roleById) && !hasRole(event.getMember(), roleById)) {
                log.debug("Adding {} to {}", roleById.getName(), event.getAuthor().getAsTag());

                event.getGuild().addRoleToMember(event.getMember(), roleById).queue();
            }
        }
    }

    private void sendNoSocialLinksMessage(Message message, User user, PlaceholderMessage embedBuilder, JsonObject player) {
        message.editMessage(embedBuilder
            .setDescription(String.join("\n", Arrays.asList(
                "Found no Discord social link that matches your Discord user for " + player.get("displayname").getAsString() + "!",
                String.format("Please make sure your Discord social link is set to `%s` before trying to verify again", user.getAsTag())
            )))
            .setColor(MessageType.ERROR.getColor())
            .buildEmbed()
        ).queue();
    }

    private boolean hasRole(Member member, Role role) {
        for (Role memberRole : member.getRoles()) {
            if (memberRole.getId().equals(role.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDiscordLinked(JsonObject json) {
        try {
            return json.getAsJsonObject("socialMedia").getAsJsonObject("links").has("DISCORD");
        } catch (Exception e) {
            return false;
        }
    }
}
