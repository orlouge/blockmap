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

        regionCount = Math.max(5, Math.min(100, blockMapEntries.size() / 70));
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
        while (maxDist < 0.6) {
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
            maxHoles = maxDist > 0.5 ? -1 : maxHoles + 1;
        }

        BlockMapClientMod.LOGGER.info("stitching " + sections.size());
        for (Section section : sections) {
            PriorityQueue<SectionMerge> sectionQueue = new PriorityQueue<>();
            for (Section neighbor : getNeighbors(section)) {
                sectionQueue.addAll(Section.forceMerge(section, neighbor));
            }
            if (sectionQueue.size() > 0) {
                mergeQueue.add(sectionQueue);
            }
        }

        while (sections.size() > 1) {
            BlockMapClientMod.LOGGER.info("stitching/merging " + sections.size() + "," + mergeQueue.size());
            mergeAll(maxDist, -1);

            for (Section section1 : sections) {
                PriorityQueue<SectionMerge> sectionQueue = new PriorityQueue<>();
                for (Section section2 : sections) {
                    if (section1 != section2) {
                        sectionQueue.addAll(Section.forceMerge(section1, section2));
                    }
                }
                if (sectionQueue.size() > 0) {
                    mergeQueue.add(sectionQueue);
                }
            }
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
        boolean mergeOnX = merge.x;
        Section section1 = merge.section1, section2 = merge.section2, merged = merge.resultSection;

        if (!sections.contains(section1) || !sections.contains(section2)) return false;

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
                            ? SectionMerge.concat(section1, section2, true, 0, maxDist, maxHoles)
                            : null,
                    Section.ordered(section2, section1, true)
                            ? SectionMerge.concat(section2, section1, true, 0, maxDist, maxHoles)
                            : null,
                    Section.ordered(section1, section2, false)
                            ? SectionMerge.concat(section1, section2, false, 0, maxDist, maxHoles)
                            : null,
                    Section.ordered(section2, section1, false)
                            ? SectionMerge.concat(section2, section1, false, 0, maxDist, maxHoles)
                            : null
            ).filter(sectionMerge -> sectionMerge != null)
             .collect(Collectors.toList());
        }

        public static List<SectionMerge> forceMerge(Section section1, Section section2) {
            SectionMerge mergeX = SectionMerge.concat(section1, section2, true, 1, Double.POSITIVE_INFINITY, -1);
            SectionMerge mergeY = SectionMerge.concat(section1, section2, false, 1, Double.POSITIVE_INFINITY, -1);
            if (Section.ordered(section1, section2, true)) {
                return Section.ordered(section1, section2, false) ? List.of(mergeX, mergeY) : List.of(mergeX);
            } else {
                return Section.ordered(section1, section2, false) ? List.of(mergeY) : List.of();
            }
        }
    }

    private static class SectionMerge implements Comparable {
        private final Section section1, section2, resultSection;
        private final double dist;
        private final int holes;
        private final boolean x;

        private SectionMerge(Section section1, Section section2, Section resultSection, double dist, int holes, boolean x) {
            this.section1 = section1;
            this.section2 = section2;
            this.resultSection = resultSection;
            this.dist = dist;
            this.holes = holes;
            this.x = x;
        }

        public static SectionMerge concat(Section section1, Section section2, boolean xAxis, int stride, double discardDist, int maxHoles) {
            int m1 = xAxis ? section1.width : section1.height, m2 = xAxis ? section2.width : section2.height;
            int o1 = !xAxis ? section1.width : section1.height, o2 = !xAxis ? section2.width : section2.height;
            int width = xAxis ? section1.width + section2.width + stride : Math.max(section1.width, section2.width);
            int height = !xAxis ? section1.height + section2.height + stride : Math.max(section1.height, section2.height);
            Entry[][] entries = new Entry[width][height];
            int holes = Math.abs(o2 - o1) * (m1 + m2 + stride) + stride * Math.max(o1, o2);
            int elongation = Math.abs(width - height) - Math.max(
                    Math.abs(section1.width - section1.height),
                    Math.abs(section2.width - section2.height)
            );
            elongation = elongation > 0 ? elongation * Math.max(width, height) * (1 + holes) : elongation;
            elongation = elongation == 2 ? 0 : elongation;
            holes = Math.max(0, elongation + holes);
            if (maxHoles > 0 && holes > maxHoles) return null;
            double maxDist = Double.POSITIVE_INFINITY;
            int off1 = 0, off2 = 0;
            for (int offset = 0; offset <= Math.abs(o2 - o1); offset++) {
                double currentDistance = Double.NEGATIVE_INFINITY;
                int currentOff1 = o2 > o1 ? offset : 0, currentOff2 = o2 > o1 ? 0 : offset;
                for (int j = 0; j < Math.min(o1, o2); j++) {
                    Entry entry1 = xAxis ? section1.entries[m1 - 1][j + currentOff2] : section1.entries[j + currentOff2][m1 - 1];
                    Entry entry2 = xAxis ? section2.entries[0][j + currentOff1] : section2.entries[j + currentOff1][0];
                    if (entry1 != null && entry2 != null) {
                        currentDistance = Math.max(currentDistance, entry1.color.distanceTo(entry2.color));
                    }
                }
                if (currentDistance < maxDist && currentDistance > Double.NEGATIVE_INFINITY) {
                    maxDist = currentDistance;
                    off1 = currentOff1;
                    off2 = currentOff2;
                }
            }
            if (maxDist > discardDist) return null;

            holes = 0;
            maxDist = maxDist + 0.03d * (double) holes;

            int i;
            for (i = 0; i < m1; i++) {
                for (int j = 0; j < o1; j++) {
                    if (xAxis) {
                        entries[i][j + off1] = section1.entries[i][j];
                    } else {
                        entries[j + off1][i] = section1.entries[j][i];
                    }
                }
            }
            i += stride;
            for (int i2 = 0; i2 < m2; i2++) {
                for (int j = 0; j < o2; j++) {
                    if (xAxis) {
                        entries[i + i2][j + off2] = section2.entries[i2][j];
                    } else {
                        entries[j + off2][i + i2] = section2.entries[j][i2];
                    }
                }
            }
            Set<Pair<Integer, Integer>> regionCoords = section1.regions;
            regionCoords.addAll(section2.regions);
            return new SectionMerge(
                    section1,
                    section2,
                    new Section(
                            entries,
                            Math.min(section1.minX, section2.minX),
                            Math.min(section1.minY, section2.minY),
                            Math.min(section1.maxX, section2.maxX),
                            Math.min(section1.maxY, section2.maxY),
                            regionCoords
                    ),
                    maxDist,
                    // xAxis ? (section2.minX - section1.maxX) / width + outDist(section1.minY, section1.maxY, section2.minY, section2.maxY)
                    //       : (section2.minY - section2.maxY) / height + outDist(section1.minX, section1.maxX, section2.minX, section2.maxX),
                    holes,
                    xAxis
            );
        }

        private static double outDist(double minY1, double maxY1, double minY2, double maxY2) {
            double dist = 0;
            if (minY1 <= minY2 && maxY1 <= maxY2) {

            } else if (minY1 <= minY2 && maxY1 > maxY2) {
                dist += (minY2 - minY1) + (maxY2 - maxY1);
            } else if (minY1 > minY2 && maxY1 <= maxY2) {

            } else if (minY1 > minY2 && maxY1 > maxY2) {
                dist += (minY1 - minY2) + (maxY1 - maxY2);
            }
            return dist;
        }

        public static SectionMerge singletonConcat(Section section1, Section section2, boolean x) {
            if (section1.entries.length != 1 || section2.entries.length != 1) {
                throw new IllegalArgumentException("Must have length = 1");
            }
            section1.regions.addAll(section2.regions);
            return new SectionMerge(
                    section1,
                    section2,
                    new Section(
                            x ? new Entry[][]{section1.entries[0], section2.entries[0]}
                              : new Entry[][]{{section1.entries[0][0], section2.entries[0][0]}},
                            section1.minX,
                            section1.minY,
                            section2.maxX,
                            section2.maxY,
                            section1.regions),
                    section1.entries[0][0].color.distanceTo(section2.entries[0][0].color),
                    0,
                    x
            );
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
