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

package com.senither.hypixel.commands;

public enum CategoryIcons {

    ADMINISTRATION("\u2699"),
    CALCULATORS("\uD83C\uDFB2"),
    GENERAL("\uD83D\uDEE0"),
    MISC("\uD83D\uDD0E"),
    STATISTICS("\uD83D\uDEE1");

    private final String icon;

    CategoryIcons(String icon) {
        this.icon = icon;
    }

    public static CategoryIcons fromName(String name) {
        for (CategoryIcons icon : values()) {
            if (icon.name().equalsIgnoreCase(name)) {
                return icon;
            }
        }
        return null;
    }

    public String getIcon() {
        return icon;
    }
}
