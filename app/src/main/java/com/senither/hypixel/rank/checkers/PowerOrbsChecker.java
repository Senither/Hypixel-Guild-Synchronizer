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

package com.senither.hypixel.rank.checkers;

import com.google.gson.JsonObject;
import com.senither.hypixel.contracts.rank.RankRequirementChecker;
import com.senither.hypixel.database.controller.GuildController;
import com.senither.hypixel.exceptions.FriendlyException;
import com.senither.hypixel.inventory.Item;
import com.senither.hypixel.rank.RankCheckResponse;
import com.senither.hypixel.rank.items.PowerOrb;
import net.hypixel.api.reply.GuildReply;
import net.hypixel.api.reply.skyblock.SkyBlockProfileReply;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PowerOrbsChecker extends RankRequirementChecker {

    public PowerOrbsChecker() {
        super("Power Orb");
    }

    @Override
    public String getRankRequirementNote(GuildController.GuildEntry.RankRequirement requirement) {
        if (!hasRequirementsSetup(requirement)) {
            return "No Power Orb requirement";
        }
        return String.format("Must have at least %s ", requirement.getPowerOrb().getName());
    }

    @Override
    public boolean hasRequirementsSetup(GuildController.GuildEntry.RankRequirement requirement) {
        return requirement.getPowerOrb() != null;
    }

    @Override
    public RankCheckResponse handleGetRankForUser(GuildController.GuildEntry guildEntry, GuildReply guildReply, SkyBlockProfileReply profileReply, UUID playerUUID) {
        JsonObject member = getProfileMemberFromUUID(profileReply, playerUUID);

        if (!isInventoryApiEnabled(member)) {
            throw new FriendlyException("Inventory API is disabled, unable to look for power orbs");
        }

        try {
            List<Item> items = new ArrayList<>();
            items.addAll(buildInventoryForPlayer(member, "ender_chest_contents").getItems());
            items.addAll(buildInventoryForPlayer(member, "inv_contents").getItems());

            PowerOrb powerOrb = null;
            for (Item item : items) {
                for (PowerOrb orb : PowerOrb.values()) {
                    if (!item.getName().endsWith(orb.getName())) {
                        continue;
                    }

                    if (powerOrb != null && powerOrb.getId() > orb.getId()) {
                        continue;
                    }

                    powerOrb = orb;
                }
            }

            if (powerOrb == null) {
                return null;
            }

            for (GuildReply.Guild.Rank rank : getSortedRanksFromGuild(guildReply)) {
                if (!guildEntry.getRankRequirements().containsKey(rank.getName())) {
                    continue;
                }

                GuildController.GuildEntry.RankRequirement requirement = guildEntry.getRankRequirements().get(rank.getName());
                if (requirement.getPowerOrb() != null && requirement.getPowerOrb().getId() <= powerOrb.getId()) {
                    return createResponse(rank, powerOrb);
                }
            }
            return createResponse(null, powerOrb);
        } catch (IOException ignored) {
        }
        return createResponse(null, null);
    }

    private RankCheckResponse createResponse(GuildReply.Guild.Rank rank, PowerOrb powerOrb) {
        return new RankCheckResponse(rank, new HashMap<String, Object>() {{
            put("item", powerOrb);
        }});
    }

    private boolean isInventoryApiEnabled(JsonObject json) {
        return json.has("ender_chest_contents")
            && json.has("inv_contents");
    }
}
