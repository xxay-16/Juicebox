package juicebox.core.assembly;

import juicebox.core.data.Block;

import java.util.List;

/**
 * UI-free assembly coordinate transform. Extracted from the desktop
 * AssemblyHeatmapHandler so the algorithm can be unit-tested without any Swing
 * or application state. Operates on the columnar Block arrays directly.
 *
 * Semantics are bit-identical to the original implementation: the same
 * (long) casts over the binSize*mapScale product, the same comparator-equal
 * boundary behavior in the scaffold binary search, and the same -1 fallback
 * for bins that do not map to any scaffold.
 */
public final class AssemblyTransform {

    private final List<ScaffoldData> sortedScaffolds;   // sorted by originalStart (comparator order)
    private final int binSize;
    private final double mapScale;
    private final int[] table;   // bin -> altered bin, or null if fallback

    public AssemblyTransform(List<ScaffoldData> sortedScaffolds, int binSize, double mapScale, int maxBin) {
        this.sortedScaffolds = sortedScaffolds;
        this.binSize = binSize;
        this.mapScale = mapScale;
        this.table = buildTable(maxBin);
    }

    public static AssemblyTransform forWholeGenome(List<ScaffoldData> sortedScaffolds, int binSize,
                                                   double mapScale, int maxBin, boolean allByAll) {
        return new AssemblyTransform(sortedScaffolds, allByAll ? 1000 * binSize : binSize, mapScale, maxBin);
    }

    public int mapBin(int binValue) {
        if (table != null && binValue >= 0 && binValue < table.length) {
            return table[binValue];
        }
        return computeAlteredAsmBin(binValue);
    }

    public Block apply(Block block, String key) {
        if (sortedScaffolds.isEmpty()) {
            return block;
        }
        int n = block.size();
        int[] srcBinX = block.getBinXArray();
        int[] srcBinY = block.getBinYArray();
        float[] srcCounts = block.getCountsArray();
        int[] outBinX = new int[n];
        int[] outBinY = new int[n];
        float[] outCounts = new float[n];
        int out = 0;
        for (int i = 0; i < n; i++) {
            int binX = srcBinX[i];
            int binY = srcBinY[i];
            int alteredAsmBinX = mapBin(binX);
            int alteredAsmBinY = mapBin(binY);

            if (alteredAsmBinX == -1 || alteredAsmBinY == -1) {
                outBinX[out] = binX;
                outBinY[out] = binY;
                outCounts[out] = srcCounts[i];
                out++;
            } else if (alteredAsmBinX == binX && alteredAsmBinY == binY) {
                outBinX[out] = binX;
                outBinY[out] = binY;
                outCounts[out] = srcCounts[i];
                out++;
            } else {
                if (alteredAsmBinX > alteredAsmBinY) {
                    outBinX[out] = alteredAsmBinY;
                    outBinY[out] = alteredAsmBinX;
                } else {
                    outBinX[out] = alteredAsmBinX;
                    outBinY[out] = alteredAsmBinY;
                }
                outCounts[out] = srcCounts[i];
                out++;
            }
        }
        return new Block(block.getNumber(), outBinX, outBinY, outCounts, out, key);
    }

    private int[] buildTable(int maxBin) {
        if (sortedScaffolds.isEmpty()) {
            return null;
        }
        long lastOriginalEnd = sortedScaffolds.get(sortedScaffolds.size() - 1).originalEnd;
        long numBins = (long) ((lastOriginalEnd - 1) / (mapScale * binSize)) + 3;
        if (maxBin + 2 > numBins) {
            numBins = maxBin + 2;
        }
        if (numBins > 100_000_000) {
            return null;
        }
        int[] bins = new int[(int) numBins];
        for (int bin = 0; bin < bins.length; bin++) {
            bins[bin] = computeAlteredAsmBin(bin);
        }
        return bins;
    }

    private int computeAlteredAsmBin(int binValue) {
        long originalFirstNucleotide = (long) (binValue * mapScale * binSize + 1);
        long currentFirstNucleotide;
        ScaffoldData scaffold = lookUpOriginalAggregateScaffold(originalFirstNucleotide);

        if (scaffold != null) {
            if (!scaffold.invertedVsInitial) {
                currentFirstNucleotide = (scaffold.currentStart + originalFirstNucleotide - scaffold.originalStart);
            } else {
                currentFirstNucleotide = (scaffold.currentEnd - originalFirstNucleotide + 2 - (long) (mapScale * binSize) + scaffold.originalStart);
            }
            return (int) ((currentFirstNucleotide - 1) / (mapScale * binSize));
        }
        return -1;
    }

    private ScaffoldData lookUpOriginalAggregateScaffold(long genomicPos) {
        // allocation-free equivalent of Collections.binarySearch with a probe of
        // (originalStart = genomicPos, length = 1): a comparator-equal hit yields null,
        // otherwise the scaffold just before the insertion point is returned
        List<ScaffoldData> list = sortedScaffolds;
        int lo = 0, hi = list.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long start = list.get(mid).originalStart;
            if (start < genomicPos) {
                lo = mid + 1;
            } else if (start > genomicPos) {
                hi = mid - 1;
            } else {
                while (mid + 1 < list.size() && list.get(mid + 1).originalStart == genomicPos) {
                    mid++;
                }
                return list.get(mid).length == 1 ? null : list.get(mid);
            }
        }
        return hi >= 0 ? list.get(hi) : null;
    }
}
