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

package com.senither.hypixel.hypixel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.database.collection.Collection;
import com.senither.hypixel.exceptions.FriendlyException;
import com.senither.hypixel.time.Carbon;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.adapters.DateTimeTypeAdapter;
import net.hypixel.api.adapters.UUIDTypeAdapter;
import net.hypixel.api.reply.AbstractReply;
import net.hypixel.api.reply.GuildReply;
import net.hypixel.api.reply.PlayerReply;
import net.hypixel.api.reply.skyblock.SkyBlockProfileReply;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class Hypixel {

    private static final Logger log = LoggerFactory.getLogger(Hypixel.class);

    private static final Cache<String, UUID> uuidCache = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();

    private static final Cache<UUID, String> usernameCache = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();

    private static final Cache<String, AbstractReply> replyCache = CacheBuilder.newBuilder()
        .expireAfterWrite(90, TimeUnit.SECONDS)
        .build();

    private static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
        .registerTypeAdapter(ZonedDateTime.class, new DateTimeTypeAdapter())
        .create();

    private static final Pattern minecraftUsernameRegex = Pattern.compile("^\\w+$", Pattern.CASE_INSENSITIVE);

    private final SkyblockAssistant app;
    private final HypixelAPI hypixelAPI;
    private final HttpClient httpClient;

    public Hypixel(SkyblockAssistant app) {
        this.app = app;

        this.httpClient = HttpClientBuilder.create().build();
        this.hypixelAPI = new HypixelAPI(UUID.fromString(app.getConfiguration().getHypixelToken()));
    }

    public boolean isValidMinecraftUsername(@Nonnull String username) {
        return username.length() > 2 && username.length() < 17 && minecraftUsernameRegex.matcher(username).find();
    }

    public HypixelAPI getAPI() {
        return hypixelAPI;
    }

    public Gson getGson() {
        return gson;
    }

    public CompletableFuture<PlayerReply> getPlayerByName(String name) {
        return getPlayerByName(name, false);
    }

    public CompletableFuture<PlayerReply> getPlayerByName(String name, boolean ignoreDatabaseCache) {
        CompletableFuture<PlayerReply> future = new CompletableFuture<>();

        final String cacheKey = "player-name-" + name.toLowerCase();
        try {
            UUID uuid = getUUIDFromName(name);
            if (uuid == null) {
                future.completeExceptionally(new FriendlyException("Failed to find a valid UUID for the given username!"));
                return future;
            }

            AbstractReply cachedPlayerProfile = replyCache.getIfPresent(cacheKey);
            if (cachedPlayerProfile != null && cachedPlayerProfile instanceof PlayerReply) {
                log.debug("Found player profile for {} using the in-memory cache (ID: {})", name, uuid.toString());

                future.complete((PlayerReply) cachedPlayerProfile);
                return future;
            }

            final boolean[] hasDatabaseEntry = {false};

            if (!ignoreDatabaseCache) {
                Collection result = app.getDatabaseManager().query("SELECT `data`, `last_updated_at` FROM `players` WHERE `uuid` = ?", uuid.toString());
                if (!result.isEmpty()) {
                    Carbon lastUpdatedAt = result.first().getTimestamp("last_updated_at");
                    if (lastUpdatedAt.addMinutes(30).isFuture()) {
                        PlayerReply playerReply = gson.fromJson(result.get(0).getString("data"), PlayerReply.class);
                        if (playerReply != null && playerReply.getPlayer() != null) {
                            log.debug("Found player profile for {} using the database cache (ID: {})", name, uuid);

                            replyCache.put(cacheKey, playerReply);
                            future.complete(playerReply);

                            return future;
                        }
                    }
                    hasDatabaseEntry[0] = true;
                }
            }

            log.debug("Requesting for player profile for \"{}\" using the API", name);

            getAPI().getPlayerByUuid(uuid).whenCompleteAsync((playerReply, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                    return;
                }

                replyCache.put(cacheKey, playerReply);

                try {
                    if (ignoreDatabaseCache) {
                        hasDatabaseEntry[0] = !app.getDatabaseManager().query(
                            "SELECT `data` FROM `players` WHERE `uuid` = ?", uuid.toString()
                        ).isEmpty();
                    }

                    if (hasDatabaseEntry[0]) {
                        app.getDatabaseManager().queryUpdate(
                            "UPDATE `players` SET `data` = ?, `last_updated_at` = ? WHERE `uuid` = ?",
                            gson.toJson(playerReply), Carbon.now(), uuid.toString()
                        );
                    } else {
                        app.getDatabaseManager().queryInsert(
                            "INSERT INTO `players` SET `uuid` = ?, `data` = ?, `created_at` = NOW(), `last_updated_at` = ?",
                            uuid.toString(), gson.toJson(playerReply), Carbon.now()
                        );
                    }
                } catch (Exception e) {
                    log.error("Failed to create/update player data for {}, error: {}",
                        uuid.toString(), e.getMessage(), e);
                }

                future.complete(playerReply);
            });
        } catch (SQLException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public CompletableFuture<SkyBlockProfileReply> getSelectedSkyBlockProfileFromUsername(String name) {
        CompletableFuture<SkyBlockProfileReply> future = new CompletableFuture<>();

        getPlayerByName(name).whenComplete((playerReply, throwable) -> {
            if (throwable != null) {
                log.debug("Failed to get selected skyblock profile for \"{}\" due to an exception while getting Hypixel profile.", name);
                future.completeExceptionally(throwable);
                return;
            }

            if (playerReply.getPlayer() == null) {
                future.completeExceptionally(new FriendlyException("Failed to find any valid SkyBlock profiles!"));
                return;
            }

            try {
                JsonObject profiles = playerReply.getPlayer().getAsJsonObject("stats").getAsJsonObject("SkyBlock").getAsJsonObject("profiles");

                List<SkyBlockProfileReply> skyBlockProfileReplies = new ArrayList<>();
                for (Map.Entry<String, JsonElement> profileEntry : profiles.entrySet()) {
                    try {
                        SkyBlockProfileReply profileReply = getSkyBlockProfile(profileEntry.getKey()).get(5, TimeUnit.SECONDS);
                        if (!profileReply.isSuccess()) {
                            continue;
                        }

                        profileReply.getProfile().add("cute_name", profileEntry.getValue().getAsJsonObject().get("cute_name"));

                        skyBlockProfileReplies.add(profileReply);
                    } catch (Exception e) {
                        log.error("Failed to get selected profile for {}, error: {}",
                            name, e.getMessage(), e);
                    }
                }

                if (skyBlockProfileReplies.isEmpty()) {
                    log.debug("Failed to get selected skyblock profile for \"{}\" due to having found no valid profiles.", name);

                    future.completeExceptionally(new FriendlyException("Failed to find any valid SkyBlock profiles!"));
                    return;
                }

                //noinspection ConstantConditions
                SkyBlockProfileReply skyBlockProfileReply = skyBlockProfileReplies.stream()
                    .sorted((profileOne, profileTwo) -> {
                        return getLastSaveFromMember(profileOne.getProfile().getAsJsonObject("members").getAsJsonObject(
                            playerReply.getPlayer().get("uuid").getAsString()
                        )) < getLastSaveFromMember(profileTwo.getProfile().getAsJsonObject("members").getAsJsonObject(
                            playerReply.getPlayer().get("uuid").getAsString()
                        )) ? 1 : -1;
                    }).findFirst().get();

                log.debug("Found selected SkyBlock profile for \"{}\" it was \"{}\" with UUID \"{}\"",
                    name, skyBlockProfileReply.getProfile().get("cute_name").getAsString(), skyBlockProfileReply.getProfile().get("profile_id").getAsString()
                );

                future.complete(skyBlockProfileReply);
            } catch (NullPointerException e) {
                future.completeExceptionally(new FriendlyException("Found no SkyBlock profiles for " + name));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public CompletableFuture<SkyBlockProfileReply> getSkyBlockProfile(String name) {
        CompletableFuture<SkyBlockProfileReply> future = new CompletableFuture<>();

        final String cacheKey = "skyblock-profile-" + name;

        AbstractReply cachedSkyBlockProfile = replyCache.getIfPresent(cacheKey);
        if (cachedSkyBlockProfile != null && cachedSkyBlockProfile instanceof SkyBlockProfileReply) {
            log.debug("Found SkyBlock profile {} using the in-memory cache", name);

            future.complete((SkyBlockProfileReply) cachedSkyBlockProfile);
            return future;
        }

        boolean hasDatabaseEntry = false;

        try {
            Collection result = app.getDatabaseManager().query("SELECT `data`, `last_updated_at` FROM `profiles` WHERE `uuid` = ?", name);
            if (!result.isEmpty()) {
                Carbon lastUpdatedAt = result.first().getTimestamp("last_updated_at");
                if (lastUpdatedAt.addMinutes(30).isFuture()) {
                    SkyBlockProfileReply skyblockProfile = gson.fromJson(result.get(0).getString("data"), SkyBlockProfileReply.class);
                    if (skyblockProfile != null && skyblockProfile.getProfile() != null) {
                        log.debug("Found SkyBlock profile for {} using the database cache", name);

                        replyCache.put(cacheKey, skyblockProfile);
                        future.complete(skyblockProfile);

                        return future;
                    }
                }
                hasDatabaseEntry = true;
            }
        } catch (SQLException e) {
            log.error("An exception were thrown while trying to get the SkyBlock profile from the database cache, error: {}",
                e.getMessage(), e
            );
        }

        log.debug("Requesting for SkyBlock profile with an ID of {} from the API", name);

        boolean finalHasDatabaseEntry = hasDatabaseEntry;
        hypixelAPI.getSkyBlockProfile(name).whenComplete((skyBlockProfileReply, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            replyCache.put(cacheKey, skyBlockProfileReply);

            try {
                if (finalHasDatabaseEntry) {
                    app.getDatabaseManager().queryUpdate("UPDATE `profiles` SET `data` = ?, `last_updated_at` = ? WHERE `uuid` = ?",
                        gson.toJson(skyBlockProfileReply), Carbon.now(), name
                    );
                } else {
                    app.getDatabaseManager().queryInsert("INSERT INTO `profiles` SET `uuid` = ?, `data` = ?, `last_updated_at` = ?",
                        name, gson.toJson(skyBlockProfileReply), Carbon.now()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to create/update profile data for {}, error: {}",
                    name, e.getMessage(), e
                );
            }

            future.complete(skyBlockProfileReply);
        });

        return future;
    }

    public CompletableFuture<GuildReply> getGuildByName(String name) {
        CompletableFuture<GuildReply> future = new CompletableFuture<>();

        final String cacheKey = "skyblock-guild-" + name.trim().toLowerCase();

        AbstractReply cachedSkyBlockGuild = replyCache.getIfPresent(cacheKey);
        if (cachedSkyBlockGuild != null && cachedSkyBlockGuild instanceof GuildReply) {
            log.debug("Found SkyBlock Guild {} using the in-memory cache", name);

            future.complete((GuildReply) cachedSkyBlockGuild);
            return future;
        }

        try {
            Collection result = app.getDatabaseManager().query("SELECT `data` FROM `guilds` WHERE `name` = ?", name);
            if (!result.isEmpty()) {
                GuildReply skyblockGuild = gson.fromJson(result.get(0).getString("data"), GuildReply.class);
                if (skyblockGuild != null && skyblockGuild.getGuild() != null) {
                    log.debug("Found SkyBlock Guild for {} using the database cache", name);

                    replyCache.put(cacheKey, skyblockGuild);
                    future.complete(skyblockGuild);

                    return future;
                }
            }
        } catch (SQLException e) {
            log.error("An exception were thrown while trying to get the SkyBlock profile from the database cache, error: {}",
                e.getMessage(), e
            );
        }

        log.debug("Requesting for SkyBlock Guild with a name of {} from the API", name);

        hypixelAPI.getGuildByName(name).whenComplete((skyBlockGuildReply, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            replyCache.put(cacheKey, skyBlockGuildReply);

            future.complete(skyBlockGuildReply);
        });

        return future;
    }

    public UUID getUUIDFromName(String name) throws SQLException {
        UUID cachedUUID = uuidCache.getIfPresent(name.toLowerCase());
        if (cachedUUID != null) {
            log.debug("Found UUID for {} using the in-memory cache (ID: {})", name, cachedUUID);
            return cachedUUID;
        }

        Collection result = app.getDatabaseManager().query("SELECT `uuid` FROM `uuids` WHERE `username` = ?", name);
        if (!result.isEmpty()) {
            UUID uuid = UUID.fromString(result.get(0).getString("uuid"));
            uuidCache.put(name.toLowerCase(), uuid);
            log.debug("Found UUID for {} using the database cache (ID: {})", name, uuid);

            return uuid;
        }

        try {
            MojangPlayerUUID mojangPlayer = httpClient.execute(new HttpGet("https://api.mojang.com/users/profiles/minecraft/" + name), obj -> {
                String content = EntityUtils.toString(obj.getEntity(), "UTF-8");
                return gson.fromJson(content, MojangPlayerUUID.class);
            });

            if (mojangPlayer == null || mojangPlayer.getUUID() == null) {
                return null;
            }

            log.debug("Found UUID for {} using the Mojang API (ID: {})", name, mojangPlayer.getUUID());

            try {
                app.getDatabaseManager().queryInsert("INSERT INTO `uuids` SET `uuid` = ?, `username` = ?",
                    mojangPlayer.getUUID().toString(), name
                );
            } catch (Exception e) {
                log.error("Failed to get UUID for player {}, error: {}",
                    name, e.getMessage(), e
                );
            }

            return mojangPlayer.getUUID();
        } catch (IOException e) {
            log.error("Failed to fetch UUID for {} using the Mojang API, error: {}", name, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // We can ignore this exception since it should only be thrown if
            // the Hypixel API returns null due to the player not existing.
        }

        return null;
    }

    public void forgetUsernameCacheEntry(UUID uuid) {
        usernameCache.invalidate(uuid);
    }

    public String getUsernameFromUuid(UUID uuid) throws SQLException {
        String cachedUsername = usernameCache.getIfPresent(uuid);
        if (cachedUsername != null) {
            log.debug("Found Username for {} using the in-memory cache (Username: {})", uuid, cachedUsername);
            return cachedUsername;
        }

        Collection result = app.getDatabaseManager().query("SELECT `username` FROM `uuids` WHERE `uuid` = ?", uuid.toString());
        if (!result.isEmpty()) {
            String username = result.get(0).getString("username");
            usernameCache.put(uuid, username);
            log.debug("Found Username for {} using the database cache (Username: {})", uuid, username);

            return username;
        }

        try {
            PlayerReply playerReply = getAPI().getPlayerByUuid(uuid).get(10, TimeUnit.SECONDS);

            if (playerReply == null || playerReply.getPlayer() == null) {
                return null;
            }

            String username = playerReply.getPlayer().get("displayname").getAsString();

            usernameCache.put(uuid, username);

            log.debug("Found Username for {} using the Hypixel API (Username: {})", uuid, username);

            try {
                app.getDatabaseManager().queryInsert("INSERT INTO `uuids` SET `uuid` = ?, `username` = ?",
                    uuid, username
                );

                app.getDatabaseManager().queryInsert("INSERT INTO `players` SET `uuid` = ?, `data` = ?",
                    uuid, getGson().toJson(playerReply)
                );
            } catch (Exception e) {
                log.error("Failed to get Username from UUID for player {}, error: {}",
                    uuid.toString(), e.getMessage(), e
                );
            }

            return username;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Failed to fetch Username for {} using the Hypixel API, error: {}", uuid, e.getMessage(), e);
        }

        return null;
    }

    public HypixelRank getRankFromPlayer(PlayerReply playerReply) {
        if (playerReply == null || playerReply.getPlayer() == null) {
            return HypixelRank.getDefaultRank();
        }

        JsonObject player = playerReply.getPlayer();
        if (player.has("monthlyPackageRank") && !player.get("monthlyPackageRank").getAsString().equals("NONE")) {
            return HypixelRank.getFromType(player.get("monthlyPackageRank").getAsString());
        } else if (player.has("newPackageRank")) {
            return HypixelRank.getFromType(player.get("newPackageRank").getAsString());
        } else if (player.has("packageRank")) {
            return HypixelRank.getFromType(player.get("packageRank").getAsString());
        }

        return HypixelRank.DEFAULT;
    }

    private long getLastSaveFromMember(JsonObject object) {
        return object.has("last_save") ? object.get("last_save").getAsLong() : Long.MIN_VALUE;
    }
}
