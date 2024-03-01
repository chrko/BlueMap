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
import de.bluecolored.bluemap.common.serverinterface.Server;
import de.bluecolored.bluemap.common.serverinterface.ServerEventListener;
import de.bluecolored.bluemap.common.serverinterface.ServerWorld;
import de.bluecolored.bluemap.core.MinecraftVersion;
import de.bluecolored.bluemap.core.util.Tristate;
import de.bluecolored.bluemap.core.world.World;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class VanillaServer implements Server {
    private final MinecraftVersion minecraftVersion;
    private final Path serverRoot;

    private final Collection<ServerEventListener> listeners;

    private final Set<ServerWorld> serverWorlds;

    public VanillaServer() {
        this.serverRoot = Path.of("").toAbsolutePath().normalize();

        minecraftVersion = MinecraftVersion.LATEST_SUPPORTED;
        listeners = new ArrayList<>();
        serverWorlds = new HashSet<>();
    }

    @Override
    public MinecraftVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public Path getConfigFolder() {
        return serverRoot.resolve("config");
    }

    @Override
    public Optional<Path> getModsFolder() {
        return Optional.empty();
    }

    @Override
    public Tristate isMetricsEnabled() {
        return Tristate.FALSE;
    }

    @Override
    public Collection<ServerWorld> getLoadedServerWorlds() {
        return Collections.unmodifiableCollection(serverWorlds);
    }

    @Override
    public Optional<ServerWorld> getServerWorld(World world) {
        if (world instanceof MCAWorld) {
            ServerWorld serverWorld = new DummyServerWorld((MCAWorld) world);
            serverWorlds.add(serverWorld);
            return Optional.of(serverWorld);
        }
        return Server.super.getServerWorld(world);
    }

    @Override
    public Collection<Player> getOnlinePlayers() {
        return Collections.emptyList();
    }

    @Override
    public void registerListener(ServerEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterAllListeners() {
        listeners.clear();
    }
}
