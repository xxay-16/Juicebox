package juicebox.core.io;

import juicebox.core.data.Block;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence tests for the core block normalizer: per-record counts divided by
 * nv1[x] * nv2[y] with NaN results dropped, evaluated in double precision.
 */
class BlockNormalizerTest {

    @Test
    void normalizesEachRecord() {
        Block raw = new Block(0,
                new int[]{0, 1, 2},
                new int[]{0, 1, 2},
                new float[]{10f, 20f, 30f},
                3, "k");
        BlockNormalizer.IndexedDoubleArray nv1 = i -> 2.0;   // nv1[x] = 2
        BlockNormalizer.IndexedDoubleArray nv2 = i -> 5.0;   // nv2[y] = 5
        Block out = BlockNormalizer.normalize(raw, nv1, nv2, "k");

        assertThat(out.size()).isEqualTo(3);
        assertThat(out.getCountsArray()).containsExactly(1f, 2f, 3f);
        assertThat(out.getBinXArray()).containsExactly(0, 1, 2);
        assertThat(out.getBinYArray()).containsExactly(0, 1, 2);
    }

    @Test
    void dropsNaNResults() {
        Block raw = new Block(0,
                new int[]{0, 1},
                new int[]{0, 1},
                new float[]{10f, 20f},
                2, "k");
        BlockNormalizer.IndexedDoubleArray nv1 = i -> i == 1 ? 0.0 : 2.0; // denominator 0 for record 1
        BlockNormalizer.IndexedDoubleArray nv2 = i -> 5.0;
        Block out = BlockNormalizer.normalize(raw, nv1, nv2, "k");

        // record 1 divides by zero -> Infinity, not NaN, so it is kept; only NaN is dropped.
        // use a NaN-producing case: 0/0
        Block raw2 = new Block(0, new int[]{0}, new int[]{0}, new float[]{0f}, 1, "k");
        BlockNormalizer.IndexedDoubleArray zero = i -> 0.0;
        Block out2 = BlockNormalizer.normalize(raw2, zero, nv2, "k");
        assertThat(out2.size()).isEqualTo(0);   // 0/(0*5) = NaN -> dropped
    }
}
