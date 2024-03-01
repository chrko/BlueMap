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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.leangen.geantyref.TypeToken;

import java.io.Reader;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public class UserCache {
    public static class User {
        private String name;
        private UUID uuid;
        private Instant expiresOn;

        public String getName() {
            return name;
        }

        public UUID getUuid() {
            return uuid;
        }

        public Instant getExpiresOn() {
            return expiresOn;
        }

        public User validate() {
            Objects.requireNonNull(name, "Name required");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Name must not be empty");
            }
            Objects.requireNonNull(uuid, "UUID required");
            Objects.requireNonNull(expiresOn, "Expires on required");
            return this;
        }

        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", uuid=" + uuid +
                    ", expiresOn=" + expiresOn +
                    '}';
        }
    }

    public static Collection<User> read(Reader r) {
        return GSON.fromJson(
                r,
                collectionType.getType()
        );
    }

    private static final TypeToken<Collection<User>> collectionType = new TypeToken<>() {
    };
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Instant.class, new ExpiresOnGson()).create();

    private static class ExpiresOnGson implements JsonDeserializer<Instant> {
        private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss x");

        @Override
        public Instant deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return dateTimeFormatter.parse(jsonElement.getAsString(), Instant::from);
        }
    }

    private UserCache() {
    }
}
