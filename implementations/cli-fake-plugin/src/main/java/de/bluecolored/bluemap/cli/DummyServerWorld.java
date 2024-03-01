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

import de.bluecolored.bluemap.common.serverinterface.ServerWorld;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;

import java.nio.file.Path;
import java.util.Objects;

public class DummyServerWorld implements ServerWorld {
    private final Path worldFolder;
    private final Key dimension;

    public DummyServerWorld(MCAWorld mcaWorld) {
        worldFolder = mcaWorld.getWorldFolder();
        dimension = mcaWorld.getDimension();
    }

    public DummyServerWorld(Path worldFolder, Key dimension) {
        this.worldFolder = worldFolder;
        this.dimension = dimension;
    }

    @Override
    public Path getWorldFolder() {
        return worldFolder;
    }

    @Override
    public Key getDimension() {
        return dimension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DummyServerWorld that = (DummyServerWorld) o;
        return Objects.equals(worldFolder, that.worldFolder) && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldFolder, dimension);
    }
}
