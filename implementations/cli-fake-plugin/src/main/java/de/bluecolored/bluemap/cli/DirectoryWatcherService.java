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

import de.bluecolored.bluemap.core.logger.Logger;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Function;

public class DirectoryWatcherService extends Thread {
    private final Path watchPath;
    private final Consumer<Path> consumer;
    private final Function<Path, Object> keyMapper;
    private final Duration delay;

    private final WatchService watchService;
    private volatile boolean closed;

    protected Timer delayTimer;
    private final Map<Object, TimerTask> updateTasks = new HashMap<>();

    public DirectoryWatcherService(Path watchPath, Consumer<Path> consumer, Function<Path, Object> keyMapper, Duration delay) throws IOException {
        this.watchPath = watchPath.toAbsolutePath().normalize();
        this.consumer = Objects.requireNonNull(consumer);
        this.keyMapper = Objects.requireNonNull(keyMapper);
        this.delay = Objects.requireNonNull(delay);

        watchService = this.watchPath.getFileSystem().newWatchService();
        this.watchPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
    }

    @Override
    public void run() {
        if (delayTimer == null) delayTimer = new Timer("DirectoryWatcher-DelayTimer", true);

        Logger.global.logDebug("Started watching folder '" + watchPath + "' for updates...");

        try {
            while (!closed) {
                WatchKey key = this.watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Object fileObject = event.context();
                    if (!(fileObject instanceof Path)) continue;
                    Path file = (Path) fileObject;

                    scheduleDelayedUpdate(file);
                }

                if (!key.reset()) return;
            }
        } catch (ClosedWatchServiceException ignore) {
        } catch (InterruptedException iex) {
            Thread.currentThread().interrupt();
        } finally {
            Logger.global.logDebug("Stopped watching folder '" + watchPath + "' for updates.");
            if (!closed) {
                Logger.global.logWarning("Watching folder '" + watchPath + "' stopped unexpectedly!");
            }
        }
    }

    void scheduleDelayedUpdate(Path file) {
        synchronized (updateTasks) {
            final Path normalizedFile = file.toAbsolutePath().normalize();
            Logger.global.logDebug("Trying to schedule update for '" + file + "'");
            Object key = keyMapper.apply(normalizedFile);
            if (updateTasks.containsKey(key)) return;

            var task = new TimerTask() {
                @Override
                public void run() {
                    synchronized (updateTasks) {
                        try {
                            consumer.accept(normalizedFile);
                        } catch (Exception e) {
                            Logger.global.logError("Exception during file watch consumer execution", e);
                        } finally {
                            updateTasks.remove(key);
                            Logger.global.logDebug("Update completed for '" + file + "'");
                        }
                    }
                }
            };

            updateTasks.put(key, task);
            delayTimer.schedule(task, delay.toMillis());
            Logger.global.logDebug("Update scheduled for '" + file + "'");
        }
    }

    public void close() {
        this.closed = true;
        this.interrupt();

        if (this.delayTimer != null) this.delayTimer.cancel();

        try {
            this.watchService.close();
        } catch (IOException ex) {
            Logger.global.logError("Exception while trying to close WatchService!", ex);
        }
    }
}
