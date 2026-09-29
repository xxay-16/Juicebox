package juicebox.core.data;

import java.util.Arrays;

/**
 * Growable columnar buffer for contact records. The block parser writes binX,
 * binY, and counts straight into parallel primitive arrays instead of boxing a
 * ContactRecord per record and re-copying it into a Block afterwards - so the
 * parse -> block path allocates the arrays once.
 */
public final class ContactRecordBuffer {

    private int[] binX;
    private int[] binY;
    private float[] counts;
    private int size;

    public ContactRecordBuffer(int initialCapacity) {
        int cap = Math.max(initialCapacity, 16);
        this.binX = new int[cap];
        this.binY = new int[cap];
        this.counts = new float[cap];
        this.size = 0;
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > binX.length) {
            int newCap = Math.max(minCapacity, binX.length + (binX.length >> 1));
            binX = Arrays.copyOf(binX, newCap);
            binY = Arrays.copyOf(binY, newCap);
            counts = Arrays.copyOf(counts, newCap);
        }
    }

    public void add(int x, int y, float c) {
        ensureCapacity(size + 1);
        binX[size] = x;
        binY[size] = y;
        counts[size] = c;
        size++;
    }

    public int size() {
        return size;
    }

    /** Builds a Block, trimming the arrays to the exact record count. */
    public Block toBlock(int blockNumber, String regionID) {
        return new Block(blockNumber,
                Arrays.copyOf(binX, size),
                Arrays.copyOf(binY, size),
                Arrays.copyOf(counts, size),
                size, regionID);
    }
}
