/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.cli;

import de.bluecolored.bluemap.common.serverinterface.Player;
import de.bluecolored.bluemap.common.serverinterface.ServerEventListener;
import de.bluecolored.bluemap.core.logger.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PlayerProvider {
    private static final Duration completeReloadInterval = Duration.ofDays(1);
    private final Path userCacheFile;
    private final Path playerDataFolder;

    private final Map<UUID, VanillaPlayer> players;
    private final ServerEventListener listener;

    private final Timer timer = new Timer();

    private TimerTask completeReloadTask;

    public PlayerProvider(ServerEventListener listener, Path userCacheFile, Path playerDataFolder) {
        this.listener = listener;
        if (!Files.isRegularFile(userCacheFile) || !Files.isReadable(userCacheFile)) {
            throw new IllegalArgumentException("User cache file is not a file or not readable: " + userCacheFile);
        }
        if (!Files.isDirectory(playerDataFolder) || !Files.isReadable(playerDataFolder)) {
            throw new IllegalArgumentException("Player data folder is not a folder or not readable: " + playerDataFolder);
        }

        this.userCacheFile = userCacheFile;
        this.playerDataFolder = playerDataFolder;

        this.players = new ConcurrentHashMap<>();

        completeReloadTask = new TimerTask() {
            @Override
            public void run() {
                loadPlayers();
            }
        };

        timer.scheduleAtFixedRate(completeReloadTask, TimeUnit.SECONDS.toMillis(30), completeReloadInterval.toMillis());
    }

    private void loadPlayers() {
        try {
            Collection<UserCache.User> userCache = UserCache.read(Files.newBufferedReader(userCacheFile, StandardCharsets.UTF_8));
            Logger.global.logDebug("Found " + userCache.size() + " users in user cache");

            for (UserCache.User user : userCache) {
                PlayerDataNBT playerDataNBT = PlayerDataNBT.read(playerDataFolder.resolve(user.getUuid().toString() + ".dat"));
                var player = new VanillaPlayer(user, playerDataNBT);
                player.isUpToDate();
                players.put(player.getUuid(), player);
            }
        } catch (IOException e) {
            Logger.global.logError("Error reloading all players", e);
            return;
        }
        notifyListener();
    }

    private void notifyListener() {
        for (Player player : getActivePlayers()) {
            listener.onPlayerJoin(player.getUuid());
        }
    }

    public Collection<Player> getActivePlayers() {
        return players.values()
                .stream()
                .filter(VanillaPlayer::isUpToDate)
                .collect(Collectors.toUnmodifiableSet());
    }
}
