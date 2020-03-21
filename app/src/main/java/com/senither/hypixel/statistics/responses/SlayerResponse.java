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

package com.senither.hypixel.statistics.responses;

import com.senither.hypixel.Constants;
import com.senither.hypixel.contracts.statistics.StatisticsResponse;

public class SlayerResponse extends StatisticsResponse {

    private long totalCoinsSpent = 0;
    private long totalSlayerExperience = 0;

    private SlayerStat revenant = new SlayerStat();
    private SlayerStat tarantula = new SlayerStat();
    private SlayerStat sven = new SlayerStat();

    public SlayerResponse(boolean apiEnable) {
        super(apiEnable);
    }

    public long getTotalCoinsSpent() {
        return totalCoinsSpent;
    }

    public SlayerResponse setTotalCoinsSpent(long totalCoinsSpent) {
        this.totalCoinsSpent = totalCoinsSpent;

        return this;
    }

    public long getTotalSlayerExperience() {
        return totalSlayerExperience;
    }

    public SlayerResponse setTotalSlayerExperience(long totalSlayerExperience) {
        this.totalSlayerExperience = totalSlayerExperience;

        return this;
    }

    public SlayerStat getRevenant() {
        return revenant;
    }

    public SlayerResponse setRevenant(int experience, int tier1Kills, int tier2Kills, int tier3Kills, int tier4Kills) {
        this.revenant = new SlayerStat(experience, tier1Kills, tier2Kills, tier3Kills, tier4Kills);

        return this;
    }

    public SlayerStat getTarantula() {
        return tarantula;
    }

    public SlayerResponse setTarantula(int experience, int tier1Kills, int tier2Kills, int tier3Kills, int tier4Kills) {
        this.tarantula = new SlayerStat(experience, tier1Kills, tier2Kills, tier3Kills, tier4Kills);

        return this;
    }

    public SlayerStat getSven() {
        return sven;
    }

    public SlayerResponse setSven(int experience, int tier1Kills, int tier2Kills, int tier3Kills, int tier4Kills) {
        this.sven = new SlayerStat(experience, tier1Kills, tier2Kills, tier3Kills, tier4Kills);

        return this;
    }

    public class SlayerStat {

        private final int experience;
        private final int tier1Kills;
        private final int tier2Kills;
        private final int tier3Kills;
        private final int tier4Kills;

        SlayerStat() {
            experience = 0;
            tier1Kills = 0;
            tier2Kills = 0;
            tier3Kills = 0;
            tier4Kills = 0;
        }

        SlayerStat(int experience, int tier1Kills, int tier2Kills, int tier3Kills, int tier4Kills) {
            this.experience = experience;
            this.tier1Kills = tier1Kills;
            this.tier2Kills = tier2Kills;
            this.tier3Kills = tier3Kills;
            this.tier4Kills = tier4Kills;
        }

        public int getExperience() {
            return experience;
        }

        public int getTier1Kills() {
            return tier1Kills;
        }

        public int getTier2Kills() {
            return tier2Kills;
        }

        public int getTier3Kills() {
            return tier3Kills;
        }

        public int getTier4Kills() {
            return tier4Kills;
        }

        public double getLevelFromExperience() {
            for (int level = 0; level < Constants.SLAYER_EXPERIENCE.size(); level++) {
                double requirement = Constants.SLAYER_EXPERIENCE.asList().get(level);
                if (this.experience < requirement) {
                    double lastRequirement = level == 0 ? 0D : Constants.SLAYER_EXPERIENCE.asList().get(level - 1);
                    return level + (this.experience - lastRequirement) / (requirement - lastRequirement);
                }
            }
            return 9;
        }
    }
}
