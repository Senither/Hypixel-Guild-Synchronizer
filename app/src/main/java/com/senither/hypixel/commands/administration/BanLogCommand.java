package com.senither.hypixel.commands.administration;

import com.senither.hypixel.Constants;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.chat.PlaceholderMessage;
import com.senither.hypixel.chat.SimplePaginator;
import com.senither.hypixel.contracts.commands.Command;
import com.senither.hypixel.database.collection.Collection;
import com.senither.hypixel.database.collection.DataRow;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.utils.NumberUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.sql.SQLException;
import java.util.*;

public class BanLogCommand extends Command {

    public BanLogCommand(SkyblockAssistant app) {
        super(app);
    }

    @Override
    public String getName() {
        return "Ban Log Command";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
            "This command can be used to view, add, or remove people from the ban log,",
            "the ban log allows you to view people who are banned, and for what",
            "reason across the entire bot."
        );
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command add <user> <reason>` - Adds the user to the ban log.",
            "`:command remove <user> <id>` - Removes the entry with the given ID from the user in the ban log.",
            "`:command view <user>` - Views a users ban log status."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command add Senither Being a dumb dumb`",
            "`:command remove Senither 9`",
            "`:command view Senither`"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("ban-log", "banlog", "log", "bl");
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

        if (!guildEntry.isBanLogEnabled()) {
            MessageFactory.makeError(event.getMessage(),
                "The ban-log feature have not yet been enabled for the server, you must setup "
                    + "the feature before being able to use this command, you can enable the "
                    + "feature by running:"
                    + "\n```h!settings ban-log <role>```"
            ).setTitle("Ban-Log is not setup").queue();
            return;
        }

        if (!isOfficerInGuildOrHasBanLogRole(event, guildEntry)) {
            MessageFactory.makeError(event.getMessage(),
                "You must be the guild master of the **:name** guild to use this command!"
            ).set("name", guildEntry.getName()).setTitle("Missing argument").queue();
            return;
        }

        if (args.length == 0) {
            MessageFactory.makeError(event.getMessage(),
                "Missing the `name` of the action you wish to preform!"
            ).setTitle("Missing argument").queue();
            return;
        }

        if (args.length == 1) {
            MessageFactory.makeError(event.getMessage(),
                "Missing the `username` of the user you wish to use the ban log action on."
            ).setTitle("Missing argument").queue();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "add":
            case "adds":
                addUser(event, args[1], Arrays.copyOfRange(args, 2, args.length));
                break;

            case "show":
            case "view":
                showUser(event, args[1], Arrays.copyOfRange(args, 2, args.length));
                break;

            case "remove":
            case "delete":
                removeUser(event, args[1], Arrays.copyOfRange(args, 2, args.length));
                break;

            default:
                MessageFactory.makeWarning(event.getMessage(),
                    "Invalid ban log action given! `:type` is not a valid ban log action."
                ).set("type", args[0]).queue();
        }
    }

    private void addUser(MessageReceivedEvent event, String username, String[] args) {
        UUID uuid = getUuidFromUsername(username);
        if (uuid == null) {
            MessageFactory.makeWarning(event.getMessage(),
                "Found no Minecraft user account with the name `:name`, please make sure you have entered the username correctly!"
            ).set("name", username).queue();
            return;
        }

        UUID userUUID = getCurrentUserUUID(event);
        if (userUUID == null) {
            MessageFactory.makeError(event.getMessage(),
                "Something went wrong while trying to retrieve your user UUID, please try again in a minute."
            ).queue();
            return;
        }

        String reason = null;
        if (args.length > 0) {
            reason = "base64:" + new String(
                Base64.getEncoder().encode(
                    String.join(" ", args).getBytes()
                )
            );
        }

        try {
            app.getDatabaseManager().queryInsert(
                "INSERT INTO `ban_log` SET `discord_id` = ?, `added_by` = ?, `uuid` = ?, `reason` = ?;",
                event.getGuild().getIdLong(), userUUID, uuid, reason
            );

            MessageFactory.makeSuccess(event.getMessage(),
                "**:name** have been added to the ban-log, :reason"
            ).set("name", app.getHypixel().getUsernameFromUuid(uuid)).set("reason", reason == null
                ? "with *no reason* given"
                : "for \"" + String.join(" ", args) + "\""
            ).queue();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showUser(MessageReceivedEvent event, String username, String[] args) {
        UUID uuid = getUuidFromUsername(username);
        if (uuid == null) {
            MessageFactory.makeWarning(event.getMessage(),
                "Found no Minecraft user account with the name `:name`, please make sure you have entered the username correctly!"
            ).set("name", username).queue();
            return;
        }

        try {
            Collection result = app.getDatabaseManager().query(
                "SELECT `ban_log`.*, `guilds`.`name` FROM `ban_log`" +
                    "LEFT JOIN `guilds` ON `guilds`.`discord_id` = `ban_log`.`discord_id`" +
                    "WHERE `uuid` = ?" +
                    "ORDER BY `created_at` DESC",
                uuid
            );

            if (result.isEmpty()) {
                MessageFactory.makeWarning(event.getMessage(),
                    "**:name** has no entries in the ban-log."
                ).set("name", app.getHypixel().getUsernameFromUuid(uuid)).queue();
                return;
            }

            List<MessageEmbed.Field> fields = new ArrayList<>();
            for (DataRow row : result) {
                if (row.getString("name") == null) {
                    continue;
                }

                fields.add(new MessageEmbed.Field(String.format(
                    "#%d - Added by %s from %s",
                    row.getLong("id"),
                    app.getHypixel().getUsernameFromUuid(UUID.fromString(row.getString("added_by"))),
                    row.getString("name")
                ), (row.getString("reason") == null
                    ? "_No reason was given_"
                    : row.getString("reason")
                ), false
                ));
            }

            SimplePaginator<MessageEmbed.Field> paginator = new SimplePaginator<>(fields, 5);
            if (args.length > 0) {
                paginator.setCurrentPage(NumberUtil.parseInt(args[0], 1));
            }

            PlaceholderMessage message = MessageFactory.makeInfo(event.getMessage(), "")
                .setTitle("Ban log for " + app.getHypixel().getUsernameFromUuid(uuid));

            paginator.forEach((index, key, val) -> message.addField(val));

            message.addField(" ", paginator.generateFooter(String.format("%s%s %s",
                Constants.COMMAND_PREFIX, getTriggers().get(0), username
            )), false).queue();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void removeUser(MessageReceivedEvent event, String username, String[] args) {
        if (args.length == 0) {
            MessageFactory.makeError(event.getMessage(),
                "Missing the ban-log entry ID that should be removed from the log."
            ).queue();
            return;
        }

        int id = NumberUtil.parseInt(args[0], -1);
        if (id < 1) {
            MessageFactory.makeWarning(event.getMessage(),
                "Invalid ban-log entry ID given, the ID must be a valid number!"
            ).queue();
            return;
        }

        UUID uuid = getUuidFromUsername(username);
        if (uuid == null) {
            MessageFactory.makeWarning(event.getMessage(),
                "Found no Minecraft user account with the name `:name`, please make sure you have entered the username correctly!"
            ).set("name", username).queue();
            return;
        }

        try {
            boolean result = app.getDatabaseManager().queryUpdate(
                "DELETE FROM `ban_log` WHERE `discord_id` = ? AND `uuid` = ? AND `id` = ?",
                event.getGuild().getIdLong(), uuid, id
            );

            if (!result) {
                MessageFactory.makeWarning(event.getMessage(),
                    "Found no ban-log entries with an ID of **:id** belonging to **:name** that was created on this server."
                ).set("id", id).set("name", app.getHypixel().getUsernameFromUuid(uuid)).queue();
                return;
            }

            MessageFactory.makeSuccess(event.getMessage(),
                "The ban-log entry with an ID of **:id** for **:name** have been successfully deleted!"
            ).set("id", id).set("name", app.getHypixel().getUsernameFromUuid(uuid)).queue();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private UUID getCurrentUserUUID(MessageReceivedEvent event) {
        try {
            return app.getHypixel().getUUIDFromUser(event.getAuthor());
        } catch (SQLException ignored) {
            return null;
        }
    }

    private UUID getUuidFromUsername(String username) {
        try {
            return app.getHypixel().getUUIDFromName(username);
        } catch (SQLException e) {
            return null;
        }
    }

    private boolean isOfficerInGuildOrHasBanLogRole(MessageReceivedEvent event, GuildController.GuildEntry guildEntry) {
        if (event.getMember() != null) {
            for (Role role : event.getMember().getRoles()) {
                if (role.getIdLong() == guildEntry.getBanLogRole()) {
                    return true;
                }
            }
        }
        return isGuildMasterOrOfficerOfServerGuild(event, guildEntry);
    }
}
