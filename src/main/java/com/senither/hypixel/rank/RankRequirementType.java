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

package com.senither.hypixel.rank;

import com.senither.hypixel.contracts.rank.RankRequirementChecker;
import com.senither.hypixel.rank.checkers.*;

public enum RankRequirementType {

    TALISMANS("Talismans", new TalismansChecker()),
    AVERAGE_SKILLS("Average Skills", new AverageSkillsChecker()),
    ARMOR("Armor", new ArmorChecker()),
    POWER_ORBS("Power Orbs", new PowerOrbsChecker()),
    SLAYER("Slayer", new SlayerChecker()),
    WEAPONS("Weapons", new WeaponsChecker()),
    FAIRY_SOULS("Fairy Souls", new FairySoulsChecker());

    private final String name;
    private final RankRequirementChecker checker;

    RankRequirementType(String name, RankRequirementChecker checker) {
        this.name = name;
        this.checker = checker;
    }

    public String getName() {
        return name;
    }

    public RankRequirementChecker getChecker() {
        return checker;
    }
}