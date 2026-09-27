package juicebox.core.io;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Zlib (Inflater) decompression that writes straight into a preallocated output
 * buffer, avoiding the extra copy through ByteArrayOutputStream that
 * org.broad.igv.util.CompressionUtils.decompress performs. The caller passes an
 * initial capacity estimate; if the data does not fit the buffer is grown once.
 */
public final class FastInflater {

    private FastInflater() {
    }

    /**
     * Decompresses {@code compressed} into a fresh buffer.
     *
     * @param compressed       zlib-compressed input
     * @param initialCapacity  estimate of the decompressed size (compressed.length * ~8
     *                         matches the observed ratio for .hic blocks); if the real
     *                         output is larger the buffer is grown and decompression
     *                         continues, so the result is always complete
     * @return the decompressed bytes (exact length, no padding)
     */
    public static byte[] decompress(byte[] compressed, int initialCapacity) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] out = new byte[Math.max(initialCapacity, 64)];
            int total = 0;
            while (!inflater.finished()) {
                if (total == out.length) {
                    byte[] bigger = new byte[out.length * 2];
                    System.arraycopy(out, 0, bigger, 0, total);
                    out = bigger;
                }
                int wrote;
                try {
                    wrote = inflater.inflate(out, total, out.length - total);
                } catch (DataFormatException e) {
                    throw new RuntimeException("Block decompression error: " + e.getMessage(), e);
                }
                if (wrote == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;   // truncated input; return what we have (caller validates)
                    }
                }
                total += wrote;
            }
            byte[] result = new byte[total];
            System.arraycopy(out, 0, result, 0, total);
            return result;
        } finally {
            inflater.end();
        }
    }
}
