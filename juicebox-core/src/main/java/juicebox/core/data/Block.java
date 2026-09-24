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
 *  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package juicebox.core.data;

//import java.awt.*;
//import java.util.List;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * A block of contact records stored in columnar (structure-of-arrays) form.
 * The binX, binY, and counts values live in parallel primitive arrays so a block
 * costs ~12 bytes/record instead of ~32 for a List of boxed ContactRecord objects.
 * {@link #getContactRecords()} still returns a List for compatibility, but it is a
 * lazily-materialized view: the hot display paths use the array accessors instead
 * and never pay the per-record object cost.
 *
 * @author jrobinso
 * @since Aug 10, 2010
 */
public class Block {

    private final int number;
    private final String uniqueRegionID;

    private int[] binX;
    private int[] binY;
    private float[] counts;
    private int size;

    // lazily built compatibility view over the arrays
    private List<ContactRecord> recordsView;

    public Block(int number, String regionID) {
        this.number = number;
        this.binX = new int[16];
        this.binY = new int[16];
        this.counts = new float[16];
        this.size = 0;
        this.uniqueRegionID = regionID + "_" + number;
    }

    public Block(int number, List<ContactRecord> records, String regionID) {
        this.number = number;
        int n = records.size();
        this.binX = new int[n];
        this.binY = new int[n];
        this.counts = new float[n];
        this.size = n;
        int i = 0;
        for (ContactRecord rec : records) {
            this.binX[i] = rec.getBinX();
            this.binY[i] = rec.getBinY();
            this.counts[i] = rec.getCounts();
            i++;
        }
        this.uniqueRegionID = regionID + "_" + number;
    }

    public Block(int number, int[] binX, int[] binY, float[] counts, int size, String regionID) {
        this.number = number;
        this.binX = binX;
        this.binY = binY;
        this.counts = counts;
        this.size = size;
        this.uniqueRegionID = regionID + "_" + number;
    }

    public int getNumber() {
        return number;
    }

    public String getUniqueRegionID() {
        return uniqueRegionID;
    }

    public int size() {
        return size;
    }

    public int[] getBinXArray() {
        return binX;
    }

    public int[] getBinYArray() {
        return binY;
    }

    public float[] getCountsArray() {
        return counts;
    }

    /**
     * Key for the i-th record, identical to ContactRecord.getKey(normalizationType),
     * generated straight from the arrays so callers do not need to materialize a record.
     */
    public String getKey(int i, Object normalizationType) {
        return binX[i] + "_" + binY[i] + "_" + normalizationType;
    }

    /**
     * Compatibility accessor. Returns a lazily-created list view over the columnar
     * storage; creating the view materializes one ContactRecord per record, so hot
     * paths should use the array accessors instead. The view is live (reflects the
     * arrays) but appending through it is not supported.
     */
    public List<ContactRecord> getContactRecords() {
        if (recordsView == null) {
            recordsView = new AbstractList<ContactRecord>() {
                @Override
                public ContactRecord get(int index) {
                    return new ContactRecord(binX[index], binY[index], counts[index]);
                }

                @Override
                public int size() {
                    return size;
                }
            };
        }
        return recordsView;
    }

    public List<ContactRecord> getContactRecords(double subsampleFraction, Random randomSubsampleGenerator) {
        List<ContactRecord> newRecords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int newBinX = binX[i];
            int newBinY = binY[i];
            int newCounts = 0;
            for (int j = 0; j < (int) counts[i]; j++) {
                if (subsampleFraction <= 1 && subsampleFraction > 0 && randomSubsampleGenerator.nextDouble() <= subsampleFraction) {
                    newCounts += 1;
                }
            }
            newRecords.add(new ContactRecord(newBinX, newBinY, (float) newCounts));
        }
        return newRecords;
    }

    public void clear() {
        size = 0;
    }
}
