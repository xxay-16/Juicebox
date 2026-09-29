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
import juicebox.core.assembly.AssemblyTransform;
import juicebox.core.assembly.ScaffoldData;
import juicebox.core.data.Block;
import juicebox.core.data.ContactRecord;
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

    // core transform cache, rebuilt on (assembly version, binSize, maxBin) change
    private static final Object tableLock = new Object();
    private static volatile AssemblyTransform cachedTransform = null;
    private static volatile int cachedTransformBinSize = -1;
    private static volatile int cachedTransformMaxBin = -1;
    private static volatile int cachedTransformVersion = -1;

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

        // delegate to the core transform (bit-identical algorithm), cached per
        // (assembly version, binSize, maxBin)
        AssemblyTransform transform = getTransform(binSize, maxBin);
        return transform.apply(block, key);
    }

    private static AssemblyTransform getTransform(int binSize, int maxBin) {
        int version = assemblyDataVersion;
        AssemblyTransform t = cachedTransform;
        if (t != null && cachedTransformVersion == version && cachedTransformBinSize == binSize
                && cachedTransformMaxBin >= maxBin) {
            return t;
        }
        synchronized (tableLock) {
            t = cachedTransform;
            if (t == null || cachedTransformVersion != version || cachedTransformBinSize != binSize
                    || cachedTransformMaxBin < maxBin) {
                List<ScaffoldData> scaffoldData = new ArrayList<>(listOfOSortedAggregateScaffolds.size());
                for (Scaffold scaffold : listOfOSortedAggregateScaffolds) {
                    scaffoldData.add(scaffold.toScaffoldData());
                }
                t = new AssemblyTransform(scaffoldData, binSize, HiCGlobals.hicMapScale, maxBin);
                cachedTransform = t;
                cachedTransformVersion = version;
                cachedTransformBinSize = binSize;
                cachedTransformMaxBin = maxBin;
            }
            return t;
        }
    }

}