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
package de.bluecolored.bluemap.bukkit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import de.bluecolored.bluemap.common.plugin.Plugin;
import de.bluecolored.bluemap.common.plugin.serverinterface.Player;
import de.bluecolored.bluemap.common.plugin.serverinterface.ServerEventListener;
import de.bluecolored.bluemap.common.plugin.serverinterface.ServerInterface;
import de.bluecolored.bluemap.common.plugin.serverinterface.ServerWorld;
import de.bluecolored.bluemap.core.BlueMap;
import de.bluecolored.bluemap.core.MinecraftVersion;
import de.bluecolored.bluemap.core.logger.Logger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BukkitPlugin extends JavaPlugin implements ServerInterface, Listener {

    private static BukkitPlugin instance;

    private final Plugin pluginInstance;
    private final EventForwarder eventForwarder;
    private final BukkitCommands commands;
    private final MinecraftVersion minecraftVersion;

    private int playerUpdateIndex = 0;
    private final Map<UUID, Player> onlinePlayerMap;
    private final List<BukkitPlayer> onlinePlayerList;

    private final LoadingCache<World, ServerWorld> worlds;

    public BukkitPlugin() {
        Logger.global = new JavaLogger(getLogger());

        //try to get best matching minecraft-version
        MinecraftVersion version = MinecraftVersion.LATEST_SUPPORTED;
        try {
            String versionString = getServer().getBukkitVersion();
            Matcher versionMatcher = Pattern.compile("(\\d+(?:\\.\\d+){1,2})[-_].*").matcher(versionString);
            if (!versionMatcher.matches()) throw new IllegalArgumentException();
            version = MinecraftVersion.of(versionMatcher.group(1));
        } catch (IllegalArgumentException e) {
            Logger.global.logWarning("Failed to detect the minecraft version of this server! Using latest version: " + version.getVersionString());
        }
        this.minecraftVersion = version;

        this.onlinePlayerMap = new ConcurrentHashMap<>();
        this.onlinePlayerList = Collections.synchronizedList(new ArrayList<>());

        this.eventForwarder = new EventForwarder();
        this.pluginInstance = new Plugin("bukkit", this);
        this.commands = new BukkitCommands(this.pluginInstance);

        this.worlds = Caffeine.newBuilder()
                .executor(BlueMap.THREAD_POOL)
                .weakKeys()
                .maximumSize(1000)
                .build(BukkitWorld::new);

        BukkitPlugin.instance = this;
    }

    @Override
    public void onEnable() {

        //save world so the level.dat is present on new worlds
        Logger.global.logInfo("Saving all worlds once, to make sure the level.dat is present...");
        for (World world : getServer().getWorlds()) {
            world.save();
        }

        //register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(eventForwarder, this);

        //register commands
        try {
            final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");

            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

            for (BukkitCommand command : commands.getRootCommands()) {
                commandMap.register(command.getLabel(), command);
            }
        } catch(NoSuchFieldException | SecurityException | IllegalAccessException e) {
            Logger.global.logError("Failed to register commands!", e);
        }

        //tab completions
        getServer().getPluginManager().registerEvents(commands, this);

        //update online-player collections
        this.onlinePlayerList.clear();
        this.onlinePlayerMap.clear();
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            BukkitPlayer bukkitPlayer = new BukkitPlayer(player.getUniqueId());
            onlinePlayerMap.put(player.getUniqueId(), bukkitPlayer);
            onlinePlayerList.add(bukkitPlayer);
        }

        //start updating players
        getServer().getScheduler().runTaskTimer(this, this::updateSomePlayers, 1, 1);

        //load bluemap
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Logger.global.logInfo("Loading...");
                this.pluginInstance.load();
                if (pluginInstance.isLoaded()) Logger.global.logInfo("Loaded!");
            } catch (IOException | RuntimeException e) {
                Logger.global.logError("Failed to load!", e);
                this.pluginInstance.unload();
            }
        });

        //bstats
        new Metrics(this, 5912);
    }

    @Override
    public void onDisable() {
        Logger.global.logInfo("Stopping...");
        getServer().getScheduler().cancelTasks(this);
        pluginInstance.unload();
        Logger.global.logInfo("Saved and stopped!");
    }

    @Override
    public MinecraftVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public void registerListener(ServerEventListener listener) {
        eventForwarder.addListener(listener);
    }

    @Override
    public void unregisterAllListeners() {
        eventForwarder.removeAllListeners();
    }

    @Override
    public Collection<ServerWorld> getLoadedWorlds() {
        Collection<ServerWorld> loadedWorlds = new ArrayList<>(3);
        for (World world : Bukkit.getWorlds()) {
            loadedWorlds.add(worlds.get(world));
        }
        return loadedWorlds;
    }

    public ServerWorld getWorld(World world) {
        return worlds.get(world);
    }

    @Override
    public Path getConfigFolder() {
        return getDataFolder().toPath();
    }

    @Override
    public Optional<Path> getModsFolder() {
        return Optional.of(Path.of("mods")); // in case this is a Bukkit/Forge hybrid
    }

    public Plugin getPlugin() {
        return pluginInstance;
    }

    public static BukkitPlugin getInstance() {
        return instance;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent evt) {
        BukkitPlayer player = new BukkitPlayer(evt.getPlayer().getUniqueId());
        onlinePlayerMap.put(evt.getPlayer().getUniqueId(), player);
        onlinePlayerList.add(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeave(PlayerQuitEvent evt) {
        UUID playerUUID = evt.getPlayer().getUniqueId();
        onlinePlayerMap.remove(playerUUID);
        synchronized (onlinePlayerList) {
            onlinePlayerList.removeIf(p -> p.getUuid().equals(playerUUID));
        }
    }

    @Override
    public Collection<Player> getOnlinePlayers() {
        return onlinePlayerMap.values();
    }

    @Override
    public Optional<Player> getPlayer(UUID uuid) {
        return Optional.ofNullable(onlinePlayerMap.get(uuid));
    }

    /**
     * Only update some of the online players each tick to minimize performance impact on the server-thread.
     * Only call this method on the server-thread.
     */
    private void updateSomePlayers() {
        int onlinePlayerCount = onlinePlayerList.size();
        if (onlinePlayerCount == 0) return;

        int playersToBeUpdated = onlinePlayerCount / 20; //with 20 tps, each player is updated once a second
        if (playersToBeUpdated == 0) playersToBeUpdated = 1;

        for (int i = 0; i < playersToBeUpdated; i++) {
            playerUpdateIndex++;
            if (playerUpdateIndex >= 20 && playerUpdateIndex >= onlinePlayerCount) playerUpdateIndex = 0;

            if (playerUpdateIndex < onlinePlayerCount) {
                onlinePlayerList.get(playerUpdateIndex).update();
            }
        }
    }

}
