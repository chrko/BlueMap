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

import de.bluecolored.bluemap.common.plugin.Plugin;
import de.bluecolored.bluemap.common.rendermanager.RenderTask;
import de.bluecolored.bluemap.core.BlueMap;
import de.bluecolored.bluemap.core.logger.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

public class FakePluginCLI {
    private static final Duration RENDER_MANAGER_PROGRESS_INTERVAL = Duration.ofSeconds(5);

    public static void main(String... args) {

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(createOptions(), args, false);

            if (cmd.hasOption("b")) {
                Logger.global.clear();
                Logger.global.put(Logger.stdOut(true));
            }

            if (cmd.hasOption("h")) {
                printHelp();
                return;
            }

            if (cmd.hasOption("V")) {
                printVersion();
                return;
            }

            var server = new VanillaServer();
            var plugin = new Plugin("vanilla", server);

            Timer timer = new Timer("BlueMap-CLI-Timer", true);
            TimerTask updateInfoTask = new TimerTask() {
                @Override
                public void run() {
                    if (plugin.getRenderManager() == null) return;
                    RenderTask task = plugin.getRenderManager().getCurrentRenderTask();
                    if (task == null) return;

                    double progress = task.estimateProgress();
                    long etaMs = plugin.getRenderManager().estimateCurrentRenderTaskTimeRemaining();

                    String eta = "";
                    if (etaMs > 0) {
                        String etrDurationString = DurationFormatUtils.formatDuration(etaMs, "HH:mm:ss");
                        eta = " (ETA: " + etrDurationString + ")";
                    }
                    Logger.global.logInfo(task.getDescription() + ": " + (Math.round(progress * 100000) / 1000.0) + "%" + eta);
                }
            };
            timer.scheduleAtFixedRate(updateInfoTask, RENDER_MANAGER_PROGRESS_INTERVAL.toMillis(), RENDER_MANAGER_PROGRESS_INTERVAL.toMillis());

            Runnable shutdown = () -> {
                Logger.global.logInfo("Stopping...");
                updateInfoTask.cancel();
                plugin.unload(false);
            };

            Thread shutdownHook = new Thread(shutdown, "BlueMap-CLI-ShutdownHook");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            plugin.load();

            return;
        } catch (ParseException e) {
            Logger.global.logError("Failed to parse provided arguments!", e);
            printHelp();
            System.exit(1);
        } catch (IOException e) {
            Logger.global.logError("Something failed…", e);
        }

        throw new RuntimeException("Logic error");
    }

    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();

        String command = getCliCommand();

        formatter.printHelp(command + " [options]", createOptions());
    }

    private static Options createOptions() {
        Options options = new Options();

        options.addOption("h", "help", false, "Shows usage instructions");

        options.addOption("b", "verbose", false, "Causes the web-server to log requests to the console");

        options.addOption("V", "version", false, "Print the current version of this tool");

        return options;
    }

    private static String getCliCommand() {
        String filename = "bluemap-cli.jar";
        try {
            File file = new File(FakePluginCLI.class.getProtectionDomain().getCodeSource().getLocation().getPath());

            if (file.isFile()) {
                try {
                    filename = "." + File.separator + new File("").getCanonicalFile().toPath().relativize(file.toPath());
                } catch (IllegalArgumentException ex) {
                    filename = file.getAbsolutePath();
                }
            }
        } catch (IOException ignore) {
        }
        return "java -jar " + filename;
    }

    private static void printVersion() {
        System.out.printf("%s\n%s\n", BlueMap.VERSION, BlueMap.GIT_HASH);
    }
}
