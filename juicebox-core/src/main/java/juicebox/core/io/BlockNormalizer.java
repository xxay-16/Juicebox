package juicebox.core.io;

import juicebox.core.data.Block;

/**
 * UI-free normalization of a raw contact block against a pair of normalization
 * vectors. Extracted from DatasetReaderV2 so the per-record math can be tested
 * without the .hic reader, the dataset, or any Swing state.
 *
 * Semantics are bit-identical to the original: counts / (nv1[x] * nv2[y]),
 * NaN results dropped, evaluated as a double before the float cast.
 */
public final class BlockNormalizer {

    private BlockNormalizer() {
    }

    /**
     * @param rawBlock the unnormalized block
     * @param nv1Data  normalization vector values indexed by binX
     * @param nv2Data  normalization vector values indexed by binY
     * @param key      region key for the output block
     */
    public static Block normalize(Block rawBlock, IndexedDoubleArray nv1Data, IndexedDoubleArray nv2Data, String key) {
        int n = rawBlock.size();
        int[] srcBinX = rawBlock.getBinXArray();
        int[] srcBinY = rawBlock.getBinYArray();
        float[] srcCounts = rawBlock.getCountsArray();
        int[] normBinX = new int[n];
        int[] normBinY = new int[n];
        float[] normCounts = new float[n];
        int out = 0;
        for (int i = 0; i < n; i++) {
            int x = srcBinX[i];
            int y = srcBinY[i];
            double denominator = nv1Data.get(x) * nv2Data.get(y);
            float counts = (float) (srcCounts[i] / denominator);
            if (!Float.isNaN(counts)) {
                normBinX[out] = x;
                normBinY[out] = y;
                normCounts[out] = counts;
                out++;
            }
        }
        return new Block(rawBlock.getNumber(), normBinX, normBinY, normCounts, out, key);
    }

    /**
     * Minimal indexed access to a normalization vector, so the core does not
     * depend on the concrete ListOfDoubleArrays type from the app layer.
     */
    public interface IndexedDoubleArray {
        double get(int index);
    }
}
