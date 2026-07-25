/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.protection.regions;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtectedPolygonalRegionTest {

    private static ProtectedPolygonalRegion polygon(int minY, int maxY, int... coordinates) {
        List<BlockVector2> points = new ArrayList<>(coordinates.length / 2);
        for (int i = 0; i < coordinates.length; i += 2) {
            points.add(BlockVector2.at(coordinates[i], coordinates[i + 1]));
        }
        return new ProtectedPolygonalRegion("polygon", points, minY, maxY);
    }

    /**
     * Count the blocks of one horizontal slice of a region by asking {@link ProtectedRegion#contains}
     * about every position of its bounding box.
     */
    private static long countContainedBlocks(ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        long count = 0;
        for (int x = min.x(); x <= max.x(); x++)
            for (int z = min.z(); z <= max.z(); z++)
                if (region.contains(BlockVector3.at(x, min.y(), z)))
                    count++;
        return count;
    }

    private static void assertVolumeMatchesContains(ProtectedPolygonalRegion region, String description) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        long expected = countContainedBlocks(region) * (max.y() - min.y() + 1);

        assertEquals(expected, region.volume(), "Volume of " + description + " " + region.getPoints());
    }

    @Test
    public void testVolumeOfSquareMatchesCuboid() {
        ProtectedPolygonalRegion square = polygon(0, 9, 10, 10, 20, 10, 20, 20, 10, 20);
        ProtectedCuboidRegion cuboid = new ProtectedCuboidRegion("cuboid",
                BlockVector3.at(10, 0, 10), BlockVector3.at(20, 9, 20));

        assertEquals(11 * 11 * 10, square.volume());
        assertEquals(cuboid.volume(), square.volume());
        assertVolumeMatchesContains(square, "square");
    }

    @Test
    public void testVolumeCountsOutermostEdge() {
        ProtectedPolygonalRegion region = polygon(0, 0, 0, 0, 1, 0, 1, 1, 0, 1);

        assertEquals(4, region.volume());
        assertVolumeMatchesContains(region, "2x2 polygon");
    }

    @Test
    public void testVolumeOfSinglePointAndLine() {
        assertEquals(1, polygon(0, 0, 5, 5, 5, 5, 5, 5).volume());
        assertEquals(7, polygon(0, 0, 5, 5, 11, 5, 5, 5).volume());
        assertEquals(7 * 4, polygon(0, 3, 5, 5, 5, 11, 5, 5).volume());
    }

    @Test
    public void testVolumeUsesFullHeight() {
        ProtectedPolygonalRegion region = polygon(-64, 319, 0, 0, 10, 0, 10, 10, 0, 10);

        assertEquals(11 * 11 * 384, region.volume());
        assertVolumeMatchesContains(region, "full height polygon");
    }

    @Test
    public void testVolumeMatchesContainsForShapes() {
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 10, 3, 4, 12), "triangle");
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 10, 0, 10, 10, 5, 5, 0, 10), "concave polygon");
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 10, 0, 10, 4, 3, 4, 3, 8, 10, 8, 10, 12, 0, 12), "comb");
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 20, 0, 0, 20, 20, 20), "self intersecting bowtie");
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 5, 0, 10, 0, 10, 10, 5, 10, 0, 10), "collinear points");
        assertVolumeMatchesContains(polygon(0, 0, -8, -8, 8, -8, 8, 8, -8, 8), "polygon around the origin");
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 7, 3, 14, 0, 11, 9, 3, 9), "irregular polygon");
        assertVolumeMatchesContains(polygon(0, 0, -1000, -1000, -990, -1000, -995, -991), "negative coordinates");
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 1, 100, 2, 0), "steep sliver");
        assertVolumeMatchesContains(polygon(0, 0, 0, 0, 100, 1, 0, 2), "flat sliver");
    }

    @Test
    public void testVolumeMatchesContainsForRandomPolygons() {
        Random random = new Random(1537);

        for (int iteration = 0; iteration < 400; iteration++) {
            int numPoints = 3 + random.nextInt(8);
            int[] coordinates = new int[numPoints * 2];
            for (int i = 0; i < coordinates.length; i++)
                coordinates[i] = random.nextInt(41) - 20;

            assertVolumeMatchesContains(polygon(0, random.nextInt(4), coordinates), "random polygon");
        }
    }

    @Test
    public void testVolumeIsCachedConsistently() {
        ProtectedPolygonalRegion region = polygon(0, 0, 0, 0, 10, 0, 10, 10, 0, 10);

        assertEquals(region.volume(), region.volume());
    }

    @Test
    public void testIntersectionCoversEveryChunkTheRegionReachesInto() {
        ProtectedPolygonalRegion region = polygon(0, 255, -1, -1, 0, -1, 0, 0, -1, 0);

        for (int chunkX = -1; chunkX <= 0; chunkX++)
            for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
                ProtectedCuboidRegion chunk = new ProtectedCuboidRegion("chunk",
                        BlockVector3.at(chunkX * 16, 0, chunkZ * 16),
                        BlockVector3.at(chunkX * 16 + 15, 255, chunkZ * 16 + 15));

                assertTrue(chunk.getIntersectingRegions(List.of(region)).contains(region),
                        "region not found in chunk " + chunkX + ", " + chunkZ);
                assertTrue(region.getIntersectingRegions(List.<ProtectedRegion>of(chunk)).contains(chunk),
                        "chunk " + chunkX + ", " + chunkZ + " not found from the region");
            }
    }

    @Test
    public void testIntersectionMatchesContainsAtSharedEdges() {
        ProtectedPolygonalRegion left = polygon(0, 10, 0, 0, 5, 0, 5, 5, 0, 5);
        ProtectedPolygonalRegion touching = polygon(0, 10, 5, 0, 10, 0, 10, 5, 5, 5);
        assertEquals(1, left.getIntersectingRegions(List.<ProtectedRegion>of(touching)).size());
        assertTrue(touching.contains(BlockVector3.at(5, 0, 0)) && left.contains(BlockVector3.at(5, 0, 0)));

        ProtectedPolygonalRegion apart = polygon(0, 10, 6, 0, 10, 0, 10, 5, 6, 5);
        assertEquals(0, left.getIntersectingRegions(List.<ProtectedRegion>of(apart)).size());
    }

    @Test
    public void testIntersectionWithCuboidMatchesContains() {
        ProtectedCuboidRegion cuboid = new ProtectedCuboidRegion("cuboid",
                BlockVector3.at(0, 0, 0), BlockVector3.at(15, 10, 15));

        ProtectedPolygonalRegion corner = polygon(0, 10, 15, 15, 25, 15, 25, 25, 15, 25);
        assertTrue(cuboid.contains(BlockVector3.at(15, 0, 15)) && corner.contains(BlockVector3.at(15, 0, 15)));
        assertEquals(1, cuboid.getIntersectingRegions(List.<ProtectedRegion>of(corner)).size());
        assertEquals(1, corner.getIntersectingRegions(List.<ProtectedRegion>of(cuboid)).size());

        ProtectedPolygonalRegion beyond = polygon(0, 10, 16, 16, 25, 16, 25, 25, 16, 25);
        assertEquals(0, cuboid.getIntersectingRegions(List.<ProtectedRegion>of(beyond)).size());
        assertEquals(0, beyond.getIntersectingRegions(List.<ProtectedRegion>of(cuboid)).size());
    }

    @Test
    public void testIntersectionFindsEveryOverlappingPolygon() {
        Random random = new Random(1244);
        ProtectedPolygonalRegion reference = polygon(0, 4, 0, 0, 12, 2, 14, 12, 4, 8);

        for (int iteration = 0; iteration < 200; iteration++) {
            int[] coordinates = new int[8];
            for (int i = 0; i < coordinates.length; i++)
                coordinates[i] = random.nextInt(25) - 6;
            ProtectedPolygonalRegion other = polygon(0, 4, coordinates);

            if (!sharesBlock(reference, other)) continue;

            assertEquals(1, reference.getIntersectingRegions(List.<ProtectedRegion>of(other)).size(),
                    "missed overlap with " + other.getPoints());
            assertEquals(1, other.getIntersectingRegions(List.<ProtectedRegion>of(reference)).size(),
                    "missed overlap with " + other.getPoints());
        }
    }

    private static boolean sharesBlock(ProtectedRegion first, ProtectedRegion second) {
        BlockVector3 min = first.getMinimumPoint();
        BlockVector3 max = first.getMaximumPoint();

        for (int x = min.x(); x <= max.x(); x++)
            for (int z = min.z(); z <= max.z(); z++) {
                BlockVector3 position = BlockVector3.at(x, min.y(), z);
                if (first.contains(position) && second.contains(position)) return true;
            }
        return false;
    }

    @Test
    public void testHugeRegionSaturatesInsteadOfOverflowing() {
        ProtectedPolygonalRegion region = polygon(0, 255,
                -30000000, -30000000, 30000000, -30000000, 30000000, 30000000, -30000000, 30000000);

        assertEquals(Integer.MAX_VALUE, region.volume());
    }

    @Test
    public void testPointsAreUnchanged() {
        List<BlockVector2> points = Arrays.asList(
                BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(10, 10));
        ProtectedPolygonalRegion region = new ProtectedPolygonalRegion("polygon", points, 0, 10);

        assertEquals(points, region.getPoints());
    }
}
