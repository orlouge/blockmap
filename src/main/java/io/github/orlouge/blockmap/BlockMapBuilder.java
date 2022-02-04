package io.github.orlouge.blockmap;

import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlockMapBuilder {
    private final List<Entry> entries;
    private final Set<Section> sections;
    private final PriorityQueue<PriorityQueue<SectionMerge>> mergeQueue;
    private final boolean dominant;
    private final int PC_ITERATIONS = 10;
    private final int regionCount;
    private final Set<Section>[][] regions;


    public BlockMapBuilder(List<BlockMapEntry> blockMapEntries, boolean dominant) {
        this.dominant = dominant;

        final Vec3d pc1, pc2;

        BlockMapClientMod.LOGGER.info("PC ...");
        pc1 = updatePC(new Vec3d(-0.5d, 0d, 0.5d).normalize(), null, PC_ITERATIONS, dominant, blockMapEntries);
        pc2 = updatePC(new Vec3d(0d, 1d, 0d).normalize(), pc1, PC_ITERATIONS, dominant, blockMapEntries);
        BlockMapClientMod.LOGGER.info(pc1.toString());
        BlockMapClientMod.LOGGER.info(pc2.toString());

        final double[] boundsX = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        final double[] boundsY = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        regionCount = Math.max(5, Math.min(100, blockMapEntries.size() / 75));
        regions = new Set[regionCount][regionCount];

        for (int regionX = 0; regionX < regionCount; regionX++) {
            for (int regionY = 0; regionY < regionCount; regionY++) {
                regions[regionX][regionY] = new HashSet<Section>();
            }
        }


        this.entries = blockMapEntries.stream()
                .filter(entry -> dominant ? entry.hasDominant : true)
                .map(entry -> {
                    double x = pc1.dotProduct(dominant ? entry.dominantFeatures() : entry.averageFeatures());
                    double y = pc2.dotProduct(dominant ? entry.dominantFeatures() : entry.averageFeatures());
                    boundsX[0] = Math.min(x, boundsX[0]);
                    boundsX[1] = Math.max(x, boundsX[1]);
                    boundsY[0] = Math.min(y, boundsY[0]);
                    boundsY[1] = Math.max(y, boundsY[1]);
                    return new Entry(entry, x, y,
                            dominant ? entry.dominantFeatures().multiply(3) : entry.averageFeatures().multiply(3)
                    );
                }).collect(Collectors.toList());

        this.sections = this.entries.stream().map(e -> {
            int regionX = Math.min(regionCount - 1, (int) (regionCount * (e.x - boundsX[0]) / (boundsX[1] - boundsX[0])));
            int regionY = Math.min(regionCount - 1, (int) (regionCount * (e.y - boundsY[0]) / (boundsY[1] - boundsY[0])));
            Section section = new Section(new Entry[][]{{e}}, e.x, e.y, e.x, e.y, new HashSet<>(List.of(new Pair<>(regionX, regionY))));
            regions[regionX][regionY].add(section);
            return section;
        }).collect(Collectors.toSet());
        this.mergeQueue = new PriorityQueue<>(Comparator.comparing(queue -> queue.peek()));
        HashMap<Section, Set<Section>> initialNeighbors = new HashMap<>();

        double maxDist = 0.01;

        int maxHoles = 1;
        while (maxDist < 0.5) {
            BlockMapClientMod.LOGGER.info("enqueue " + sections.size());
            for (Section section : sections) {
                Set<Section> currentNeighbors = getNeighbors(section);
                if (currentNeighbors.size() == 0) {
                    BlockMapClientMod.LOGGER.info("repopulating* " + sections.size());
                    currentNeighbors.addAll(sections);
                }
                currentNeighbors.remove(section);
                PriorityQueue<SectionMerge> sectionQueue = new PriorityQueue<>();
                for (Section neighbor : currentNeighbors) {
                    sectionQueue.addAll(Section.merge(section, neighbor, maxDist, maxHoles));
                }
                if (sectionQueue.size() > 0) {
                    mergeQueue.add(sectionQueue);
                }
            }
            BlockMapClientMod.LOGGER.info("merging " + mergeQueue.size());
            mergeAll(maxDist, maxHoles);
            maxDist *= 1.1;
            maxHoles = maxDist > 0.35 ? -1 : maxDist < 0.05 ? 1 : maxHoles + 1;
        }

        BlockMapClientMod.LOGGER.info("stitching " + sections.size());
        for (Section section : sections) {
            PriorityQueue<SectionMerge> sectionQueue = new PriorityQueue<>();
            for (Section neighbor : getNeighbors(section)) {
                sectionQueue.addAll(Section.forceMerge(section, neighbor, true));
            }
            if (sectionQueue.size() > 0) {
                mergeQueue.add(sectionQueue);
            }
        }

        boolean ordered = true;
        while (sections.size() > 1) {
            BlockMapClientMod.LOGGER.info("stitching/merging " + sections.size() + "," + mergeQueue.size());
            mergeAll(maxDist, -1);

            for (Section section1 : sections) {
                PriorityQueue<SectionMerge> sectionQueue = new PriorityQueue<>();
                for (Section section2 : sections) {
                    if (section1 != section2) {
                        sectionQueue.addAll(Section.forceMerge(section1, section2, ordered));
                    }
                }
                if (sectionQueue.size() > 0) {
                    mergeQueue.add(sectionQueue);
                }
            }
            ordered = false;
        }
    }

    @NotNull
    private Set<Section> getNeighbors(Section section) {
        Set<Section> currentNeighbors = new HashSet<>();
        for (Pair<Integer, Integer> regionCoords : section.regions) {
            int regionX = regionCoords.getLeft(), regionY = regionCoords.getRight();
            currentNeighbors.addAll(regions[regionX][regionY]);
            for (int distance = 1; distance < regionCount; distance++) {
                if (regionX < regionCount - distance) {
                    currentNeighbors.addAll(regions[regionX + distance][regionY]);
                    if (regionY >= distance) {
                        currentNeighbors.addAll(regions[regionX + distance][regionY - distance]);
                    }
                }
                if (regionY < regionCount - distance) {
                    currentNeighbors.addAll(regions[regionX][regionY + distance]);
                    if (regionX < regionCount - distance) {
                        currentNeighbors.addAll(regions[regionX + distance][regionY + distance]);
                    }
                }
                if (regionX >= distance) {
                    currentNeighbors.addAll(regions[regionX - distance][regionY]);
                    if (regionY < regionCount - distance) {
                        currentNeighbors.addAll(regions[regionX - distance][regionY + distance]);
                    }
                }
                if (regionY >= distance) {
                    currentNeighbors.addAll(regions[regionX][regionY - distance]);
                    if (regionX >= distance) {
                        currentNeighbors.addAll(regions[regionX - distance][regionY - distance]);
                    }
                }
                if (currentNeighbors.size() > 0) break;
            }
        }
        currentNeighbors.remove(section);
        return currentNeighbors;
    }

    private void mergeAll(double maxDist, int maxHoles) {
        while (!mergeQueue.isEmpty() && sections.size() > 1) {
            PriorityQueue<SectionMerge> sectionQueue = mergeQueue.poll();
            if (!tryMerge(sectionQueue.poll(), maxDist, maxHoles) && sectionQueue.size() > 0) {
                mergeQueue.add(sectionQueue);
            }
        }
    }

    public Iterator<Iterator<BlockMapEntry>> grid() {
        return Arrays.stream(sections.iterator().next().entries).map(
                row -> Arrays.stream(row).map(e -> e != null ? e.entry : null).iterator()
        ).iterator();
    }

    public int height() {
        return sections.iterator().next().height;
    }

    public int width() {
        return sections.iterator().next().width;
    }

    private boolean tryMerge(SectionMerge merge, double maxDist, int maxHoles) {
        boolean mergeOnX = merge.xAxis;
        Section section1 = merge.section1, section2 = merge.section2;

        if (!sections.contains(section1) || !sections.contains(section2)) return false;

        Section merged = merge.getMergedSection();

        for (Pair<Integer, Integer> regionCoords : section1.regions) {
            int regionX = regionCoords.getLeft(), regionY = regionCoords.getRight();
            regions[regionX][regionY].remove(section1);
        }
        for (Pair<Integer, Integer> regionCoords : section2.regions) {
            int regionX = regionCoords.getLeft(), regionY = regionCoords.getRight();
            regions[regionX][regionY].remove(section2);
        }
        for (Pair<Integer, Integer> regionCoords : merged.regions) {
            int regionX = regionCoords.getLeft(), regionY = regionCoords.getRight();
            regions[regionX][regionY].add(merged);
        }

        Set<Section> mergedNeighbors = getNeighbors(merged);

        List<SectionMerge> newMerges = mergedNeighbors.stream().flatMap(
                other -> Section.merge(merged, other, maxDist, maxHoles).stream()
        ).collect(Collectors.toList());

        if (newMerges.size() > 0) {
            mergeQueue.add(new PriorityQueue<>(newMerges));
        }

        sections.remove(section1);
        sections.remove(section2);
        sections.add(merged);

        return true;
    }

    // Fast Dimensionality Reduction and Simple PCA (Patridge et al.)
    private static Vec3d updatePC(Vec3d pc, Vec3d orthogonalTo, int iterations, boolean dominant, Collection<BlockMapEntry> entries) {
        for (int iter = 0; iter < iterations; iter++) {
            Vec3d sum = new Vec3d(0d, 0d, 0d);

            for (BlockMapEntry entry : entries) {
                Vec3d x;
                if (dominant) {
                    if (!entry.hasDominant) continue;
                    x = entry.dominantFeatures();
                } else {
                    x = entry.averageFeatures();
                }
                if (orthogonalTo != null) {
                    x = x.subtract(orthogonalTo.multiply(orthogonalTo.dotProduct(x)));
                }
                if (pc.dotProduct(x) > 0) {
                    sum = sum.add(x);
                }
            }

            pc = sum.multiply(1 / sum.length());
        }

        return pc;
    }

    private static class Section {
        private final Entry[][] entries;
        private final double minX, minY, maxX, maxY;
        public final int width, height;
        private final Set<Pair<Integer, Integer>> regions;

        private Section(Entry[][] entries, double minX, double minY, double maxX, double maxY, Set<Pair<Integer, Integer>> regions) {
            this.entries = entries;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.width = entries.length;
            this.height = entries.length > 0 ? entries[0].length : 0;
            this.regions = regions;
        }

        private static boolean ordered(Section section1, Section section2, boolean x) {
            return x ? section1.maxX <= section2.minX : section1.maxY <= section2.minY;
        }

        public static List<SectionMerge> merge(Section section1, Section section2, double maxDist, int maxHoles) {
            return Stream.of(
                    Section.ordered(section1, section2, true)
                            ? SectionMerge.concat(section1, section2, true, 0, maxDist, maxHoles, false, false)
                            : SectionMerge.concat(section1, section2, true, 0, maxDist, maxHoles, true, false),
                    Section.ordered(section2, section1, true)
                            ? SectionMerge.concat(section2, section1, true, 0, maxDist, maxHoles, false, false)
                            : SectionMerge.concat(section2, section1, true, 0, maxDist, maxHoles, true, false),
                    Section.ordered(section1, section2, false)
                            ? SectionMerge.concat(section1, section2, false, 0, maxDist, maxHoles, false, false)
                            : SectionMerge.concat(section1, section2, false, 0, maxDist, maxHoles, true, false),
                    Section.ordered(section2, section1, false)
                            ? SectionMerge.concat(section2, section1, false, 0, maxDist, maxHoles, false, false)
                            : SectionMerge.concat(section2, section1, false, 0, maxDist, maxHoles, true, false)
            ).filter(sectionMerge -> sectionMerge != null)
             .collect(Collectors.toList());
        }

        public static List<SectionMerge> forceMerge(Section section1, Section section2, boolean ordered) {
            SectionMerge mergeX = SectionMerge.concat(section1, section2, true, 1, Double.POSITIVE_INFINITY, -1, false, false);
            SectionMerge mergeY = SectionMerge.concat(section1, section2, false, 1, Double.POSITIVE_INFINITY, -1, false, false);
            SectionMerge mergeXflipped = SectionMerge.concat(section1, section2, true, 1, Double.POSITIVE_INFINITY, -1, true, false);
            SectionMerge mergeYflipped = SectionMerge.concat(section1, section2, false, 1, Double.POSITIVE_INFINITY, -1, true, false);
            if (ordered) {
                if (Section.ordered(section1, section2, true)) {
                    return Section.ordered(section1, section2, false) ? List.of(mergeX, mergeY) : List.of(mergeX, mergeYflipped);
                } else {
                    return Section.ordered(section1, section2, false) ? List.of(mergeXflipped, mergeY) : List.of(mergeXflipped, mergeYflipped);
                }
            } else {
                return List.of(mergeX, mergeY, mergeXflipped, mergeYflipped);
            }
        }
    }

    private static class SectionMerge implements Comparable {
        private final Section section1, section2;
        private final double dist;
        private final int holes;
        private final boolean xAxis;

        private final int stride;
        private final boolean flip1, flip2;
        private final int m1, m2, o1, o2, width, height, off1, off2;

        private SectionMerge(Section section1, Section section2, double dist, int holes, boolean x, int stride, boolean flip1, boolean flip2, int m1, int m2, int o1, int o2, int width, int height, int off1, int off2) {
            this.section1 = section1;
            this.section2 = section2;
            this.dist = dist;
            this.holes = holes;
            this.xAxis = x;
            this.stride = stride;
            this.flip1 = flip1;
            this.flip2 = flip2;
            this.m1 = m1;
            this.m2 = m2;
            this.o1 = o1;
            this.o2 = o2;
            this.width = width;
            this.height = height;
            this.off1 = off1;
            this.off2 = off2;
        }

        public static SectionMerge concat(Section section1, Section section2, boolean xAxis, int stride, double discardDist, int maxHoles, boolean flip1, boolean flip2) {
            int m1 = xAxis ? section1.width : section1.height, m2 = xAxis ? section2.width : section2.height;
            int o1 = !xAxis ? section1.width : section1.height, o2 = !xAxis ? section2.width : section2.height;
            int width = xAxis ? section1.width + section2.width + stride : Math.max(section1.width, section2.width);
            int height = !xAxis ? section1.height + section2.height + stride : Math.max(section1.height, section2.height);
            int holes = Math.abs(o2 - o1) * (m1 + m2 + stride) + stride * Math.max(o1, o2);
            int elongation = Math.abs(width - height) - Math.max(
                    Math.abs(section1.width - section1.height),
                    Math.abs(section2.width - section2.height)
            );
            elongation = elongation > 0 ? elongation * Math.max(width, height) * (1 + holes) : elongation;
            elongation = width * height == 2 && elongation > 0 ? 0 : elongation;
            holes = Math.max(0, elongation + holes);
            if (maxHoles > 0 && holes > maxHoles) return null;
            double maxDist = Double.POSITIVE_INFINITY;
            int off1 = 0, off2 = 0;
            double unit1 = xAxis ? (section1.maxY - section1.minY) / (double) section1.height
                                 : (section1.maxX - section1.minX) / (double) section1.width;
            double unit2 = xAxis ? (section2.maxY - section2.minY) / (double) section2.height
                                 : (section2.maxX - section2.minX) / (double) section2.width;
            for (int offset = 0; offset <= Math.abs(o2 - o1); offset++) {
                double currentDistance = Double.NEGATIVE_INFINITY;
                int currentOff1 = o2 > o1 ? offset : 0, currentOff2 = o2 > o1 ? 0 : offset;
                for (int j = 0; j < Math.min(o1, o2); j++) {
                    if (stride == 0) {
                        Entry entry1 = xAxis ? section1.entries[flip1 ? section1.width - m1 : m1 - 1][j + currentOff2]
                                             : section1.entries[j + currentOff2][flip1 ? section1.height - m1 : m1 - 1];
                        Entry entry2 = xAxis ? section2.entries[flip2 ? section2.width - 1 : 0][j + currentOff1]
                                             : section2.entries[j + currentOff1][flip2 ? section2.height - 1 : 0];
                        if (entry1 != null && entry2 != null) {
                            currentDistance = Math.max(currentDistance, entry1.color.distanceTo(entry2.color));
                        }
                    } else {
                        currentDistance = Math.max(currentDistance, Math.abs(
                                (unit1 * (j + currentOff2) + (xAxis ? section1.minY : section1.minX)) -
                                (unit2 * (j + currentOff1) + (xAxis ? section2.minY : section2.minX))
                        ));
                    }
                }
                if (currentDistance < maxDist && currentDistance > Double.NEGATIVE_INFINITY) {
                    maxDist = currentDistance;
                    off1 = currentOff1;
                    off2 = currentOff2;
                }
            }
            if (stride != 0) {
                maxDist *= 5;
                maxDist += xAxis ? ((flip2 ? section2.maxX : section2.minX) - (flip1 ? section1.minX : section1.maxX))
                                 : ((flip2 ? section2.maxY : section2.minY) - (flip1 ? section1.minY : section1.maxY));
            }
            if (maxDist > discardDist) return null;

            holes = 0;
            maxDist = maxDist + 0.01d * (double) holes;

            return new SectionMerge(
                    section1,
                    section2,
                    maxDist,
                    holes,
                    xAxis,
                    stride,
                    flip1,
                    flip2,
                    m1,
                    m2,
                    o1,
                    o2,
                    width,
                    height,
                    off1,
                    off2
            );
        }

        public Section getMergedSection() {
            Entry[][] entries = new Entry[width][height];
            int i;
            for (i = 0; i < m1; i++) {
                for (int j = 0; j < o1; j++) {
                    if (xAxis) {
                        entries[i][j + off1] = section1.entries[flip1 ? section1.width - 1 - i : i][j];
                    } else {
                        entries[j + off1][i] = section1.entries[j][flip1 ? section1.height - 1 - i : i];
                    }
                }
            }
            i += stride;
            for (int i2 = 0; i2 < m2; i2++) {
                for (int j = 0; j < o2; j++) {
                    if (xAxis) {
                        entries[i + i2][j + off2] = section2.entries[flip2 ? section2.width - 1 - i2 : i2][j];
                    } else {
                        entries[j + off2][i + i2] = section2.entries[j][flip2 ? section2.height - 1 - i2 : i2];
                    }
                }
            }
            Set<Pair<Integer, Integer>> regionCoords = section1.regions;
            regionCoords.addAll(section2.regions);
            double minX, minY, maxX, maxY;
            minX = Math.min(section1.minX, section2.minX);
            minY = Math.min(section1.minY, section2.minY);
            maxX = Math.max(section1.maxX, section2.maxX);
            maxY = Math.max(section1.maxY, section2.maxY);
            if (xAxis) {
                if (flip1) {
                    minX = Math.min(section1.maxX, section2.minX);
                    maxX = Math.max(section1.minX, section2.maxX);
                } else if (flip2) {
                    minX = Math.min(section1.minX, section2.maxX);
                    maxX = Math.max(section1.maxX, section2.minX);
                } else if (flip1 && flip2) {
                    minX = Math.min(section1.maxX, section2.maxX);
                    maxX = Math.max(section1.minX, section2.minX);
                }
            } else {
                if (flip1) {
                    minY = Math.min(section1.maxY, section2.minY);
                    maxY = Math.max(section1.minY, section2.maxY);
                } else if (flip2) {
                    minY = Math.min(section1.minY, section2.maxY);
                    maxY = Math.max(section1.maxY, section2.minY);
                } else if (flip1 && flip2) {
                    minY = Math.min(section1.maxY, section2.maxY);
                    maxY = Math.max(section1.minY, section2.minY);
                }
            }
            return new Section(entries, minX, minY, maxX, maxY, regionCoords);
        }

        @Override
        public int compareTo(@NotNull Object o) {
            SectionMerge other = ((SectionMerge) o);
            if (other.holes == this.holes) {
                return Double.compare(this.dist, other.dist);
            } else {
                return Integer.compare(this.holes, other.holes);
            }
        }
    }

    private static class Entry {
        private final BlockMapEntry entry;
        private final double x, y;
        private final Vec3d color;

        private Entry(BlockMapEntry entry, double x, double y, Vec3d color) {
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }
}
