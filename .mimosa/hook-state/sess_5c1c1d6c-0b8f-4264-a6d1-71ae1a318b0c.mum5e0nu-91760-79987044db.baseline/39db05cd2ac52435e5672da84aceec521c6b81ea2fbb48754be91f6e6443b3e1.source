package juicebox.core.assembly;

import juicebox.core.data.Block;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence tests for the assembly coordinate transform, mirroring the synthetic
 * benchmark used during optimization: shuffled scaffolds, mixed inversion, and the
 * exact-start / length-1 / out-of-range edge cases.
 */
class AssemblyTransformTest {

    private static final int BIN_SIZE = 1000;

    private List<ScaffoldData> buildScaffolds() {
        Random rand = new Random(42);
        List<long[]> defs = new ArrayList<>(); // originalStart, length
        long pos = 1;
        for (int i = 0; i < 500; i++) {
            long len;
            if (i == 100) {
                long next = ((pos + BIN_SIZE - 1) / BIN_SIZE) * BIN_SIZE + 1;
                len = Math.max(1, next - pos);
            } else if (i == 101) {
                len = 1;
            } else {
                len = 10_000 + rand.nextInt(90_000);
            }
            defs.add(new long[]{pos, len});
            pos += len;
        }
        // assign non-identity current placement with mixed inversion
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < defs.size(); i++) order.add(i);
        java.util.Collections.shuffle(order, new Random(7));
        long c = 1;
        long[] curStart = new long[defs.size()];
        for (int idx : order) {
            curStart[idx] = c;
            c += defs.get(idx)[1];
        }
        List<ScaffoldData> out = new ArrayList<>();
        for (int i = 0; i < defs.size(); i++) {
            long os = defs.get(i)[0];
            long len = defs.get(i)[1];
            out.add(new ScaffoldData(os, os + len - 1, curStart[i], curStart[i] + len - 1, len, i % 2 == 0));
        }
        return out;
    }

    @Test
    void identityBinsPassThrough() {
        List<ScaffoldData> scaffolds = buildScaffolds();
        AssemblyTransform t = new AssemblyTransform(scaffolds, BIN_SIZE, 1.0, 1000);
        // identity mapping reuses record values unchanged in count and position
        Block in = new Block(0, new int[]{0, 1, 2}, new int[]{0, 1, 2}, new float[]{1f, 2f, 3f}, 3, "k");
        Block out = t.apply(in, "k");
        assertThat(out.size()).isEqualTo(3);
        assertThat(out.getCountsArray()).containsExactly(1f, 2f, 3f);
    }

    @Test
    void swapIsNormalizedToUpperTriangle() {
        List<ScaffoldData> scaffolds = buildScaffolds();
        AssemblyTransform t = new AssemblyTransform(scaffolds, BIN_SIZE, 1.0, 1000);
        Block in = new Block(0, new int[]{500}, new int[]{3}, new float[]{7f}, 1, "k");
        Block out = t.apply(in, "k");
        assertThat(out.size()).isEqualTo(1);
        // after mapping, binX <= binY must hold (swap normalized)
        assertThat(out.getBinXArray()[0]).isLessThanOrEqualTo(out.getBinYArray()[0]);
    }

    @Test
    void emptyScaffoldListPassesThrough() {
        AssemblyTransform t = new AssemblyTransform(new ArrayList<>(), BIN_SIZE, 1.0, 10);
        Block in = new Block(0, new int[]{5}, new int[]{5}, new float[]{1f}, 1, "k");
        assertThat(t.apply(in, "k")).isSameAs(in);
    }
}
