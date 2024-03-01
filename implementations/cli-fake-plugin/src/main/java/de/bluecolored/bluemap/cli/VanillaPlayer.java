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

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.common.plugin.text.Text;
import de.bluecolored.bluemap.common.serverinterface.Gamemode;
import de.bluecolored.bluemap.common.serverinterface.Player;
import de.bluecolored.bluemap.common.serverinterface.ServerWorld;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

class VanillaPlayer implements Player {
    private static final Duration MAX_AGE = Duration.ofDays(1);

    private final UUID uuid;

    private UserCache.User user;
    private PlayerDataNBT playerDataNBT;

    public VanillaPlayer(UserCache.User user, PlayerDataNBT playerDataNBT) {
        this.uuid = user.getUuid();
        setUser(user);
        setPlayerDataNBT(playerDataNBT);
    }

    public void setUser(UserCache.User user) {
        user.validate();
        if (!uuid.equals(user.getUuid())) {
            throw new IllegalArgumentException("UUID do not match");
        }
        this.user = user;
    }

    public void setPlayerDataNBT(PlayerDataNBT playerDataNBT) {
        playerDataNBT.validate();
        if (!uuid.equals(playerDataNBT.getUuid())) {
            throw new IllegalArgumentException("UUID do not match");
        }
        this.playerDataNBT = playerDataNBT;
    }

    public boolean isUpToDate() {
        Instant now = Instant.now();
        return now.isBefore(user.getExpiresOn()) && now.minus(MAX_AGE).isBefore(playerDataNBT.getLastModified());
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public Text getName() {
        return Text.of(user.getName());
    }

    @Override
    public ServerWorld getWorld() {
        return null;
    }

    @Override
    public Vector3d getPosition() {
        return playerDataNBT.getPosition();
    }

    @Override
    public Vector3d getRotation() {
        return new Vector3d(
                playerDataNBT.getRotation().getY(),
                playerDataNBT.getRotation().getX(),
                0
        );
    }

    @Override
    public int getSkyLight() {
        return 0;
    }

    @Override
    public int getBlockLight() {
        return 0;
    }

    @Override
    public boolean isSneaking() {
        return false;
    }

    @Override
    public boolean isInvisible() {
        return playerDataNBT.getActiveEffects()
                .stream()
                .anyMatch(
                        activeEffect ->
                                activeEffect
                                        .getId()
                                        .getValue()
                                        .equals("invisibility")
                );
    }

    @Override
    public Gamemode getGamemode() {
        return playerDataNBT.getGamemode();
    }
}
