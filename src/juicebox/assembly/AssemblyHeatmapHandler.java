/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2020 Broad Institute, Aiden Lab, Rice University, Baylor College of Medicine
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
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.assembly;

import juicebox.HiCGlobals;
import juicebox.data.Block;
import juicebox.data.ContactRecord;
import juicebox.gui.SuperAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by muhammadsaadshamim on 4/17/17.
 */
public class AssemblyHeatmapHandler {

    private static SuperAdapter superAdapter;
    private static List<Scaffold> listOfOSortedAggregateScaffolds = new ArrayList<>();
    // bumped whenever the sorted scaffold list is replaced, i.e. on every assembly edit
    private static volatile int assemblyDataVersion = 0;
    //    Does not seem to offer much advantage
//    private static Scaffold guessScaffold = null;

    // bin -> altered-bin lookup table, rebuilt when (version, binSize, hicMapScale) changes;
    // turns the per-record scaffold binary search into an array read
    private static final Object tableLock = new Object();
    private static volatile AlteredBinTable alteredBinTable = null;

    private static final class AlteredBinTable {
        final int[] bins;
        final int version, binSize;
        final double mapScale;
        final int minBins;   // bins covered (bins.length, 0 for the fallback marker)

        AlteredBinTable(int[] bins, int version, int binSize, double mapScale, int minBins) {
            this.bins = bins;
            this.version = version;
            this.binSize = binSize;
            this.mapScale = mapScale;
            this.minBins = minBins;
        }
    }

    public static void setListOfOSortedAggregateScaffolds(List<Scaffold> listOfAggregateScaffolds) {
        AssemblyHeatmapHandler.listOfOSortedAggregateScaffolds = new ArrayList<>(listOfAggregateScaffolds);
        Collections.sort(listOfOSortedAggregateScaffolds, Scaffold.originalStateComparator);
        assemblyDataVersion++;
    }

    public static SuperAdapter getSuperAdapter() {
        return AssemblyHeatmapHandler.superAdapter;
    }

    public static int getAssemblyDataVersion() {
        return assemblyDataVersion;
    }

    public static void setSuperAdapter(SuperAdapter superAdapter) {
        AssemblyHeatmapHandler.superAdapter = superAdapter;
    }

    private static AlteredBinTable getAlteredBinTable(int binSize, int maxBin) {
        double mapScale = HiCGlobals.hicMapScale;
        int version = assemblyDataVersion;
        AlteredBinTable table = alteredBinTable;
        if (table != null && table.bins != null && table.version == version && table.binSize == binSize
                && table.mapScale == mapScale && table.minBins >= maxBin) {
            return table;
        }
        synchronized (tableLock) {
            table = alteredBinTable;
            if (table == null || table.bins == null || table.version != version || table.binSize != binSize
                    || table.mapScale != mapScale || table.minBins < maxBin) {
                table = buildAlteredBinTable(binSize, mapScale, maxBin);
                alteredBinTable = table;
            }
            return table;
        }
    }

    private static AlteredBinTable buildAlteredBinTable(int binSize, double mapScale, int maxBin) {
        List<Scaffold> scaffolds = listOfOSortedAggregateScaffolds;
        long lastOriginalEnd = scaffolds.get(scaffolds.size() - 1).getOriginalEnd();
        long numBins = (long) ((lastOriginalEnd - 1) / (mapScale * binSize)) + 3;
        if (maxBin + 2 > numBins) {
            // cover the full chromosome grid so trailing bins skip the per-record fallback
            numBins = maxBin + 2;
        }
        if (numBins > 100_000_000) {
            // pathological assembly or zoom; fall back to per-record lookups
            return new AlteredBinTable(null, -1, -1, Double.NaN, 0);
        }
        int[] bins = new int[(int) numBins];
        for (int bin = 0; bin < bins.length; bin++) {
            bins[bin] = computeAlteredAsmBin(bin, binSize, mapScale);
        }
        return new AlteredBinTable(bins, assemblyDataVersion, binSize, mapScale, bins.length);
    }

    public static Block modifyBlock(Block block, String key, int binSize, int chr1Idx, int chr2Idx) {
        return modifyBlock(block, key, binSize, chr1Idx, chr2Idx, -1);
    }

    public static Block modifyBlock(Block block, String key, int binSize, int chr1Idx, int chr2Idx, int maxBin) {
        //temp fix for AllByAll. TODO: trace this!
        if (chr1Idx == 0 && chr2Idx == 0) {
            binSize = 1000 * binSize; // AllByAll is measured in kb
        }

        // without aggregate scaffolds every bin lookup misses and records pass through unchanged
        if (listOfOSortedAggregateScaffolds.isEmpty()) {
            return block;
        }

        List<ContactRecord> alteredContacts = null;
        int[] table = null;
        if (!listOfOSortedAggregateScaffolds.isEmpty()) {
            AlteredBinTable alteredBinTable = getAlteredBinTable(binSize, maxBin);
            if (alteredBinTable.bins != null) {
                table = alteredBinTable.bins;
            }
        }

        // columnar transform: read and write the primitive arrays directly, no per-record objects
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
            int alteredAsmBinX = lookupAlteredBin(binX, binSize, table);
            int alteredAsmBinY = lookupAlteredBin(binY, binSize, table);

            if (alteredAsmBinX == -1 || alteredAsmBinY == -1) {
                outBinX[out] = binX;
                outBinY[out] = binY;
                outCounts[out] = srcCounts[i];
                out++;
            } else if (alteredAsmBinX == binX && alteredAsmBinY == binY) {
                // identity mapping (scaffolds that were not moved or inverted)
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
        block = new Block(block.getNumber(), outBinX, outBinY, outCounts, out, key);
        return block;
    }

    private static int lookupAlteredBin(int binValue, int binSize, int[] table) {
        if (table != null && binValue >= 0 && binValue < table.length) {
            return table[binValue];
        }
        return getAlteredAsmBin(binValue, binSize);
    }



    private static int getAlteredAsmBin(int binValue, int binSize) {
        return computeAlteredAsmBin(binValue, binSize, HiCGlobals.hicMapScale);
    }

    private static int computeAlteredAsmBin(int binValue, int binSize, double mapScale) {

        long originalFirstNucleotide = (long) (binValue * mapScale * binSize + 1);
        long currentFirstNucleotide;
        Scaffold aggregateScaffold = lookUpOriginalAggregateScaffold(originalFirstNucleotide);

        if (aggregateScaffold != null) {
            if (!aggregateScaffold.getInvertedVsInitial()) {
                currentFirstNucleotide = (aggregateScaffold.getCurrentStart() + originalFirstNucleotide - aggregateScaffold.getOriginalStart());
            } else {
                currentFirstNucleotide = (aggregateScaffold.getCurrentEnd() - originalFirstNucleotide + 2 - (long) (mapScale * binSize) + aggregateScaffold.getOriginalStart());
            }

            return (int) ((currentFirstNucleotide - 1) / (mapScale * binSize));
        }
        return -1;
    }

    private static Scaffold lookUpOriginalAggregateScaffold(long genomicPos) {
        // allocation-free equivalent of Collections.binarySearch with a probe Scaffold
        // (originalStart = genomicPos, length = 1) and the original -idx - 2 mapping:
        // a comparator-equal hit (equal start and length 1) yields null, otherwise the
        // scaffold just before the insertion point is returned
        List<Scaffold> list = listOfOSortedAggregateScaffolds;
        int lo = 0, hi = list.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long start = list.get(mid).getOriginalStart();
            if (start < genomicPos) {
                lo = mid + 1;
            } else if (start > genomicPos) {
                hi = mid - 1;
            } else {
                // equal starts are sorted by descending length, so the run's last element
                // is the insertion point - 1 for the length-1 probe
                while (mid + 1 < list.size() && list.get(mid + 1).getOriginalStart() == genomicPos) {
                    mid++;
                }
                return list.get(mid).getLength() == 1 ? null : list.get(mid);
            }
        }
        return hi >= 0 ? list.get(hi) : null;
    }
}