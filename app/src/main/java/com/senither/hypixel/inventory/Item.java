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

package com.senither.hypixel.inventory;

import com.github.steveice10.opennbt.tag.builtin.ByteArrayTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.senither.hypixel.contracts.inventory.InventoryDecoder;

import java.io.IOException;

public class Item implements InventoryDecoder {

    private final String name;
    private final int tagId;
    private final ItemRarity rarity;
    private final ItemType type;

    private final CompoundTag compoundTag;

    Item(Inventory inventory, CompoundTag compoundTag) throws IOException {
        if (!compoundTag.contains("tag")) {
            throw new IOException("Missing tag named \"tag\", unable to create item");
        }

        this.compoundTag = compoundTag;

        CompoundTag tag = compoundTag.get("tag");
        tagId = ((IntTag) tag.get("HideFlags")).getValue();

        if (tag.contains("ExtraAttributes")) {
            ByteArrayTag backpackData = getInventoryDataFromAttributes(tag.get("ExtraAttributes"));
            if (backpackData != null) {
                inventory.addBackpackContent(backpackData);
            }
        }
        if (!tag.contains("display")) {
            throw new IOException("Missing display tag, unable to create item");
        }

        CompoundTag display = tag.get("display");
        this.name = display.get("Name").getValue().toString().replaceAll("([!|#|%]?[§]+[a-f|0-9])", "");

        ListTag lore = display.get("Lore");

        String metaString = lore.get(lore.size() - 1)
            .getValue()
            .toString()
            .replaceAll("([!|#|%]?[§]+[a-f|0-9])", "");

        String[] itemMeta = metaString.split(" ");

        this.rarity = ItemRarity.fromName(itemMeta[0].substring(2, itemMeta[0].length()));
        this.type = ItemType.fromName(itemMeta.length == 1 ? null : itemMeta[1]);
    }

    public String getName() {
        return name;
    }

    public int getTagId() {
        return tagId;
    }

    public ItemRarity getRarity() {
        return rarity;
    }

    public ItemType getType() {
        return type;
    }

    public CompoundTag getRawCompoundTag() {
        return compoundTag;
    }
}
