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

import com.flowpowered.math.vector.Vector2f;
import com.flowpowered.math.vector.Vector3d;
import com.google.gson.reflect.TypeToken;
import de.bluecolored.bluemap.common.serverinterface.Gamemode;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.mca.MCAUtil;
import de.bluecolored.bluenbt.NBTName;
import de.bluecolored.bluenbt.NBTReader;
import de.bluecolored.bluenbt.TagType;
import de.bluecolored.bluenbt.TypeDeserializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class PlayerDataNBT {
    @NBTName("UUID")
    @SuppressWarnings("unused")
    private UUID uuid;
    @SuppressWarnings("unused")
    private Key dimension;
    @NBTName("playerGameType")
    @SuppressWarnings("unused")
    private Gamemode gamemode;
    @NBTName("Pos")
    @SuppressWarnings("unused")
    private Vector3d position;
    @SuppressWarnings("unused")
    private Vector2f rotation;

    @NBTName("active_effects")
    private final Collection<ActiveEffect> activeEffects = Collections.emptyList();

    public static class ActiveEffect {
        private Key id;
        private int duration;
        private boolean ambient;
        private byte amplifier;
        @NBTName("show_icon")
        private boolean showIcon;
        @NBTName("show_particles")
        private boolean showParticles;

        public Key getId() {
            return id;
        }

        public int getDuration() {
            return duration;
        }

        public boolean isAmbient() {
            return ambient;
        }

        public byte getAmplifier() {
            return amplifier;
        }

        public boolean isShowIcon() {
            return showIcon;
        }

        public boolean isShowParticles() {
            return showParticles;
        }
    }

    private Instant lastModified;

    public static PlayerDataNBT read(Path path) throws IOException {
        InputStream in = applyDecompression(Files.newInputStream(path));
        return MCAUtil.BLUENBT.read(in, PlayerDataNBT.class)
                .setLastModified(Files.getLastModifiedTime(path).toInstant())
                .validate();
    }

    private PlayerDataNBT setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    public PlayerDataNBT validate() {
        Objects.requireNonNull(uuid, "UUID is required");
        Objects.requireNonNull(dimension, "Dimension is required");
        Objects.requireNonNull(gamemode, "GameMode is required");
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(rotation, "Rotation is required");
        Objects.requireNonNull(lastModified, "Last modified time is required");
        return this;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Key getDimension() {
        return dimension;
    }

    public Gamemode getGamemode() {
        return gamemode;
    }

    public Vector3d getPosition() {
        return position;
    }

    public Vector2f getRotation() {
        return rotation;
    }

    public Collection<ActiveEffect> getActiveEffects() {
        return activeEffects.stream().filter(activeEffect -> activeEffect.duration == -1 ||
        lastModified.plusMillis(activeEffect.duration * 50L).isAfter(Instant.now())).collect(Collectors.toUnmodifiableList());
    }

    public Instant getLastModified() {
        return lastModified;
    }

    private static class Vector3dDeserializer implements TypeDeserializer<Vector3d> {
        @Override
        public Vector3d read(NBTReader reader) throws IOException {
            int length = reader.beginList();
            if (length != 3) throw new IllegalStateException("Vector3d needs a list with length 3");
            var v = new Vector3d(reader.nextDouble(), reader.nextDouble(), reader.nextDouble());
            reader.endList();
            return v;
        }
    }

    private static class Vector2fDeserializer implements TypeDeserializer<Vector2f> {
        @Override
        public Vector2f read(NBTReader reader) throws IOException {
            int length = reader.beginList();
            if (length != 2) throw new IllegalStateException("Vector2d needs a list with length 2");
            var v = new Vector2f(reader.nextFloat(), reader.nextFloat());

            reader.endList();
            return v;
        }
    }

    private static class UUIDDeserializer implements TypeDeserializer<UUID> {
        @Override
        public UUID read(NBTReader reader) throws IOException {
            var type = reader.peek();
            switch (type) {
                case INT_ARRAY:
                    return from(reader.nextIntArray());
                case LONG_ARRAY:
                    return from(reader.nextLongArray());
                case LIST:
                    var length = reader.beginList();
                    var listType = reader.peek();
                    if (length == 2 && listType.equals(TagType.LONG)) {
                        var uuid = from(reader.nextLong(), reader.nextLong());
                        reader.endList();
                        return uuid;
                    } else if (length == 4 && listType.equals(TagType.INT)) {
                        var uuid = from(reader.nextInt(), reader.nextInt(), reader.nextInt(), reader.nextInt());
                        reader.endList();
                        return uuid;
                    }
                    throw new IllegalStateException("Type LIST only supports exactly 4 INTs or 4 LONGs");
                case STRING:
                    return UUID.fromString(reader.nextString());
                default:
                    throw new IllegalStateException("UUID doesn't support type" + type);
            }
        }

        private static UUID from(int... ints) {
            if (ints.length != 4) {
                throw new IllegalStateException("UUID require 4 ints as array");
            }

            long mostSigBits = ((long) ints[0]) << 32 | (ints[1] & 0xffffffffL);
            long leastSigBits = ((long) ints[2]) << 32 | (ints[3] & 0xffffffffL);

            return new UUID(mostSigBits, leastSigBits);
        }

        private static UUID from(long... longs) {
            if (longs.length != 2) {
                throw new IllegalStateException("UUID require 2 longs as array");
            }
            return new UUID(longs[0], longs[1]);
        }
    }

    private static class GamemodeDeserializer implements TypeDeserializer<Gamemode> {
        @Override
        public Gamemode read(NBTReader reader) throws IOException {
            switch (reader.peek()) {
                case STRING:
                    return Gamemode.getById(reader.nextString());
                case SHORT:
                case INT:
                    return Gamemode.values()[reader.nextInt()];
                default:
                    throw new IllegalArgumentException("Gamemode needs string or short or int.");
            }
        }
    }

    static {
        MCAUtil.BLUENBT.register(TypeToken.get(Vector3d.class), new Vector3dDeserializer());
        MCAUtil.BLUENBT.register(TypeToken.get(Vector2f.class), new Vector2fDeserializer());
        MCAUtil.BLUENBT.register(TypeToken.get(UUID.class), new UUIDDeserializer());
        MCAUtil.BLUENBT.register(TypeToken.get(Gamemode.class), new GamemodeDeserializer());
    }

    private static InputStream applyDecompression(InputStream is) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(is, 2);
        int sig = (pbis.read() & 0xFF) + (pbis.read() << 8);
        pbis.unread(sig >> 8);
        pbis.unread(sig & 0xFF);
        if (sig == GZIPInputStream.GZIP_MAGIC) {
            return new GZIPInputStream(pbis);
        }
        return pbis;
    }
}
