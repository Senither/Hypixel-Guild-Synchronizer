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

package com.senither.hypixel.contracts.rank;

import com.google.gson.JsonObject;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.inventory.Inventory;
import com.senither.hypixel.rank.RankCheckResponse;
import net.hypixel.api.reply.GuildReply;
import net.hypixel.api.reply.skyblock.SkyBlockProfileReply;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class RankRequirementChecker {

    public RankCheckResponse getRankForUser(GuildController.GuildEntry guildEntry, GuildReply guildReply, SkyBlockProfileReply profileReply, UUID playerUUID) {
        throw new UnsupportedOperationException("Getting rank for the given user is not supported by this requirement type!");
    }

    public String getRankRequirementNote(GuildController.GuildEntry.RankRequirement requirement) {
        throw new UnsupportedOperationException("Getting rank note for the given rank is not supported by this requirement type!");
    }

    protected final List<GuildReply.Guild.Rank> getSortedRanksFromGuild(GuildReply guild) {
        return guild.getGuild().getRanks().stream()
            .sorted((o1, o2) -> o2.getPriority() - o1.getPriority())
            .collect(Collectors.toList());
    }

    protected final Inventory buildInventoryForPlayer(JsonObject member, String inventoryName) throws IOException {
        return new Inventory(member.get(inventoryName).getAsJsonObject().get("data").getAsString());
    }
}
