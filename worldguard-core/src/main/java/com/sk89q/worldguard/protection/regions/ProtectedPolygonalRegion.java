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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;

import java.awt.Polygon;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProtectedPolygonalRegion extends ProtectedRegion {

    private final ImmutableList<BlockVector2> points;
    private final int minY;
    private final int maxY;
    private volatile int volumeCache = -1;

    /**
     * Construct a new instance of this polygonal region.<br>
     * Equivalent to {@link #ProtectedPolygonalRegion(String, boolean, List, int, int)
     * ProtectedPolygonalRegion(id, false, points, minY, maxY)}<br>
     * <code>transientRegion</code> will be set to false, and this region can be saved.
     *
     * @param id the region id
     * @param points a {@link List} of points that this region should contain
     * @param minY the minimum y coordinate
     * @param maxY the maximum y coordinate
     */
    public ProtectedPolygonalRegion(String id, List<BlockVector2> points, int minY, int maxY) {
        this(id, false, points, minY, maxY);
    }

    /**
     * Construct a new instance of this polygonal region.
     *
     * @param id the region id
     * @param transientRegion whether this region should only be kept in memory and not be saved
     * @param points a {@link List} of points that this region should contain
     * @param minY the minimum y coordinate
     * @param maxY the maximum y coordinate
     */
    public ProtectedPolygonalRegion(String id, boolean transientRegion, List<BlockVector2> points, int minY, int maxY) {
        super(id, transientRegion);
        ImmutableList<BlockVector2> immutablePoints = ImmutableList.copyOf(points);
        setMinMaxPoints(immutablePoints, minY, maxY);
        this.points = immutablePoints;
        this.minY = min.y();
        this.maxY = max.y();
    }

    /**
     * Sets the min and max points from all the 2d points and the min/max Y values
     *
     * @param points2D A {@link List} of points that this region should contain
     * @param minY The minimum y coordinate
     * @param maxY The maximum y coordinate
     */
    private void setMinMaxPoints(List<BlockVector2> points2D, int minY, int maxY) {
        checkNotNull(points2D);

        List<BlockVector3> points = new ArrayList<>();
        int y = minY;
        for (BlockVector2 point2D : points2D) {
            points.add(BlockVector3.at(point2D.x(), y, point2D.z()));
            y = maxY;
        }
        setMinMaxPoints(points);
    }

    @Override
    public boolean isPhysicalArea() {
        return true;
    }

    @Override
    public List<BlockVector2> getPoints() {
        return points;
    }

    @Override
    public boolean contains(BlockVector3 position) {
        checkNotNull(position);

        int targetX = position.x(); // Width
        int targetY = position.y(); // Height
        int targetZ = position.z(); // Depth

        if (targetY < minY || targetY > maxY) {
            return false;
        }
        //Quick and dirty check.
        if (targetX < min.x() || targetX > max.x() || targetZ < min.z() || targetZ > max.z()) {
            return false;
        }
        boolean inside = false;
        int npoints = points.size();
        int xNew, zNew;
        int xOld, zOld;
        int x1, z1;
        int x2, z2;
        long crossproduct;
        int i;

        xOld = points.get(npoints - 1).x();
        zOld = points.get(npoints - 1).z();

        for (i = 0; i < npoints; i++) {
            xNew = points.get(i).x();
            zNew = points.get(i).z();
            //Check for corner
            if (xNew == targetX && zNew == targetZ) {
                return true;
            }
            if (xNew > xOld) {
                x1 = xOld;
                x2 = xNew;
                z1 = zOld;
                z2 = zNew;
            } else {
                x1 = xNew;
                x2 = xOld;
                z1 = zNew;
                z2 = zOld;
            }
            if (x1 <= targetX && targetX <= x2) {
                crossproduct = ((long) targetZ - (long) z1) * (long) (x2 - x1)
                    - ((long) z2 - (long) z1) * (long) (targetX - x1);
                if (crossproduct == 0) {
                    if ((z1 <= targetZ) == (targetZ <= z2)) return true; // on edge
                } else if (crossproduct < 0 && (x1 != targetX)) {
                    inside = !inside;
                }
            }
            xOld = xNew;
            zOld = zNew;
        }

        return inside;
    }

    @Override
    public RegionType getType() {
        return RegionType.POLYGON;
    }

    @Override
    Area toArea() {
        int numPoints = points.size();
        int[] xCoords = new int[numPoints];
        int[] zCoords = new int[numPoints];

        int i = 0;
        for (BlockVector2 point : points) {
            xCoords[i] = point.x();
            zCoords[i] = point.z();
            i++;
        }

        Area area = new Area(new Polygon(xCoords, zCoords, numPoints));
        BlockVector2 previous = points.get(numPoints - 1);
        for (BlockVector2 point : points) {
            area.add(new Area(sweepEdge(previous, point)));
            previous = point;
        }

        return area;
    }

    /**
     * Sweep the unit square along the given edge, producing the area covered by the blocks that the
     * edge passes through.
     *
     * @param from one end of the edge
     * @param to the other end of the edge
     * @return the swept area, as the convex hull of the unit squares at both ends
     */
    private static Polygon sweepEdge(BlockVector2 from, BlockVector2 to) {
        if (to.x() < from.x() || (to.x() == from.x() && to.z() < from.z())) {
            BlockVector2 swap = from;
            from = to;
            to = swap;
        }

        int x1 = from.x();
        int z1 = from.z();
        int x2 = to.x();
        int z2 = to.z();

        if (z2 >= z1)
            return new Polygon(
                    new int[] { x1, x1 + 1, x2 + 1, x2 + 1, x2, x1 },
                    new int[] { z1, z1, z2, z2 + 1, z2 + 1, z1 + 1 }, 6);
        else
            return new Polygon(
                    new int[] { x1, x2, x2 + 1, x2 + 1, x1 + 1, x1 },
                    new int[] { z1, z2, z2, z2 + 1, z1 + 1, z1 + 1 }, 6);
    }

    @Override
    public int volume() {
        int volume = volumeCache;
        if (volume < 0) {
            volume = calculateVolume();
            volumeCache = volume;
        }
        return volume;
    }

    private int calculateVolume() {
        if ((long) max.x() - min.x() >= Integer.MAX_VALUE || (long) max.z() - min.z() >= Integer.MAX_VALUE)
            return Integer.MAX_VALUE;

        long height = (long) maxY - minY + 1;
        long limit = Integer.MAX_VALUE / height + 1;

        long volume = Math.min(countContainedColumns(limit), limit) * height;
        return volume > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) volume;
    }

    /**
     * Count the blocks of a single horizontal slice of this region, that is, the number of positions
     * for which {@link #contains(BlockVector3)} returns true at any one Y level.
     *
     * <p>This walks the bounding box column by column and derives the contained Z ranges of each
     * column directly from the edges, using the exact same rules as {@link #contains(BlockVector3)},
     * so that the two can never disagree.</p>
     *
     * @param limit the count at which to stop early
     * @return the number of blocks in a horizontal slice of this region, capped at roughly {@code limit}
     */
    private long countContainedColumns(long limit) {
        int numPoints = points.size();
        int minZ = min.z();
        int maxZ = max.z();

        int[] edgeX1 = new int[numPoints];
        int[] edgeZ1 = new int[numPoints];
        int[] edgeX2 = new int[numPoints];
        int[] edgeZ2 = new int[numPoints];

        BlockVector2 previous = points.get(numPoints - 1);
        for (int i = 0; i < numPoints; i++) {
            BlockVector2 point = points.get(i);
            if (point.x() > previous.x()) {
                edgeX1[i] = previous.x();
                edgeZ1[i] = previous.z();
                edgeX2[i] = point.x();
                edgeZ2[i] = point.z();
            } else {
                edgeX1[i] = point.x();
                edgeZ1[i] = point.z();
                edgeX2[i] = previous.x();
                edgeZ2[i] = previous.z();
            }
            previous = point;
        }

        int[] crossings = new int[numPoints];
        long[] ranges = new long[numPoints * 2 + 1];

        long count = 0;

        for (int x = min.x(); x <= max.x(); x++) {
            int numCrossings = 0;
            int numRanges = 0;

            for (int i = 0; i < numPoints; i++) {
                int x1 = edgeX1[i];
                int x2 = edgeX2[i];
                if (x < x1 || x > x2) continue;

                int z1 = edgeZ1[i];
                int z2 = edgeZ2[i];
                int deltaX = x2 - x1;

                if (deltaX == 0) {
                    ranges[numRanges++] = encodeRange(Math.min(z1, z2), Math.max(z1, z2), minZ, maxZ);
                    continue;
                }

                long numerator = (long) z1 * deltaX + (long) (z2 - z1) * (x - x1);

                if (numerator % deltaX == 0) {
                    int z = (int) (numerator / deltaX);
                    ranges[numRanges++] = encodeRange(z, z, minZ, maxZ);
                }

                if (x > x1)
                    crossings[numCrossings++] = (int) Math.floorDiv(numerator - 1, deltaX);
            }

            Arrays.sort(crossings, 0, numCrossings);

            for (int i = 0; i <= numCrossings; i++) {
                if (((numCrossings - i) & 1) == 0) continue;
                int start = i == 0 ? minZ : crossings[i - 1] + 1;
                int end = crossings[i];
                if (start <= end)
                    ranges[numRanges++] = encodeRange(start, end, minZ, maxZ);
            }

            count += countRanges(ranges, numRanges);

            if (count >= limit) return count;
        }

        return count;
    }

    /**
     * Encode a Z range clamped to the bounding box of this region, or {@code -1} if it falls outside.
     */
    private static long encodeRange(int start, int end, int minZ, int maxZ) {
        start = Math.max(start, minZ);
        end = Math.min(end, maxZ);
        if (start > end) {
            return -1;
        }
        return (long) (start - minZ) << 32 | (long) (end - minZ);
    }

    /**
     * Count the blocks covered by the union of the given encoded ranges. The array is sorted in place.
     */
    private static long countRanges(long[] ranges, int numRanges) {
        Arrays.sort(ranges, 0, numRanges);

        long count = 0;
        long currentStart = 0;
        long currentEnd = -1;

        for (int i = 0; i < numRanges; i++) {
            if (ranges[i] == -1) continue;
            long start = ranges[i] >>> 32;
            long end = ranges[i] & 0xFFFFFFFFL;

            if (currentEnd < 0) {
                currentStart = start;
                currentEnd = end;
            } else if (start > currentEnd + 1) {
                count += currentEnd - currentStart + 1;
                currentStart = start;
                currentEnd = end;
            } else if (end > currentEnd)
                currentEnd = end;
        }

        if (currentEnd >= 0)
            count += currentEnd - currentStart + 1;

        return count;
    }

}
