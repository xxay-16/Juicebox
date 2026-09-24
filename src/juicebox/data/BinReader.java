/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2021 Broad Institute, Aiden Lab, Rice University, Baylor College of Medicine
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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.data;

import htsjdk.tribble.util.LittleEndianInputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BinReader {

    @SuppressWarnings("unchecked")
    private static void ensureRecordCapacity(LittleEndianInputStream dis, List<ContactRecord> records,
                                             int minBytesPerRecord) throws IOException {
        // type 1 blocks can hold more records than the nRecords hint in the block header;
        // presize from the remaining payload bytes so the list never grows by copying.
        // each row also carries 4 bytes of row header, so the min-bytes bound overestimates
        // by a few percent at typical densities - scale the bound down to avoid that waste
        if (records instanceof ArrayList) {
            long estimate = dis.available() / minBytesPerRecord + 8;
            ((ArrayList<ContactRecord>) records).ensureCapacity((int) Math.min(Integer.MAX_VALUE, estimate * 9 / 10));
        }
    }

    public static void handleBinType(LittleEndianInputStream dis, byte type, int binXOffset, int binYOffset,
                                     List<ContactRecord> records, boolean useShortBinX, boolean useShortBinY,
                                     boolean useShort) throws IOException {
        if (type == 1) {
            if (useShortBinX && useShortBinY) {
                handleBothShorts(dis, binXOffset, binYOffset, useShort, records);
            } else if (useShortBinX) {
                handleShortX(dis, binXOffset, binYOffset, useShort, records);
            } else if (useShortBinY) {
                handleShortY(dis, binXOffset, binYOffset, useShort, records);
            } else {
                handleBothInts(dis, binXOffset, binYOffset, useShort, records);
            }
        } else if (type == 2) {
            int nPts = dis.readInt();
            int w = dis.readShort();

            for (int i = 0; i < nPts; i++) {
                //int idx = (p.y - binOffset2) * w + (p.x - binOffset1);
                int row = i / w;
                int col = i - row * w;
                int bin1 = binXOffset + col;
                int bin2 = binYOffset + row;

                if (useShort) {
                    short counts = dis.readShort();
                    if (counts != Short.MIN_VALUE) {
                        records.add(new ContactRecord(bin1, bin2, counts));
                    }
                } else {
                    float counts = dis.readFloat();
                    if (!Float.isNaN(counts)) {
                        records.add(new ContactRecord(bin1, bin2, counts));
                    }
                }
            }
        } else {
            throw new RuntimeException("Unknown block type: " + type);
        }
    }

    private static void handleBothInts(LittleEndianInputStream dis, int binXOffset, int binYOffset, boolean useShort, List<ContactRecord> records) throws IOException {
        ensureRecordCapacity(dis, records, useShort ? 6 : 8);
        int rowCount = dis.readInt();
        for (int i = 0; i < rowCount; i++) {
            int binY = binYOffset + dis.readInt();
            int colCount = dis.readInt();
            for (int j = 0; j < colCount; j++) {
                int binX = binXOffset + dis.readInt();
                float counts = useShort ? dis.readShort() : dis.readFloat();
                records.add(new ContactRecord(binX, binY, counts));
            }
        }
    }

    private static void handleShortY(LittleEndianInputStream dis, int binXOffset, int binYOffset, boolean useShort,
                                     List<ContactRecord> records) throws IOException {
        ensureRecordCapacity(dis, records, useShort ? 6 : 8);
        int rowCount = dis.readShort();
        for (int i = 0; i < rowCount; i++) {
            int binY = binYOffset + dis.readShort();
            int colCount = dis.readInt();
            for (int j = 0; j < colCount; j++) {
                int binX = binXOffset + dis.readInt();
                float counts = useShort ? dis.readShort() : dis.readFloat();
                records.add(new ContactRecord(binX, binY, counts));
            }
        }
    }

    private static void handleShortX(LittleEndianInputStream dis, int binXOffset, int binYOffset, boolean useShort,
                                     List<ContactRecord> records) throws IOException {
        ensureRecordCapacity(dis, records, useShort ? 4 : 6);
        int rowCount = dis.readInt();
        for (int i = 0; i < rowCount; i++) {
            int binY = binYOffset + dis.readInt();
            int colCount = dis.readShort();
            for (int j = 0; j < colCount; j++) {
                int binX = binXOffset + dis.readShort();
                float counts = useShort ? dis.readShort() : dis.readFloat();
                records.add(new ContactRecord(binX, binY, counts));
            }
        }
    }

    private static void handleBothShorts(LittleEndianInputStream dis, int binXOffset, int binYOffset, boolean useShort,
                                         List<ContactRecord> records) throws IOException {
        ensureRecordCapacity(dis, records, useShort ? 4 : 6);
        int rowCount = dis.readShort();
        for (int i = 0; i < rowCount; i++) {
            int binY = binYOffset + dis.readShort();
            int colCount = dis.readShort();
            for (int j = 0; j < colCount; j++) {
                int binX = binXOffset + dis.readShort();
                float counts = useShort ? dis.readShort() : dis.readFloat();
                records.add(new ContactRecord(binX, binY, counts));
            }
        }
    }
}
