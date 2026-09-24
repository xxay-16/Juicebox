package juicebox.core.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence tests for the columnar Block storage: the array view and the
 * compatibility list view must agree, and the list-based constructor must
 * preserve every record bit-for-bit.
 */
class BlockTest {

    @Test
    void listConstructorPreservesAllRecords() {
        List<ContactRecord> input = new ArrayList<>();
        input.add(new ContactRecord(3, 5, 1.5f));
        input.add(new ContactRecord(100, 100, -2f));
        input.add(new ContactRecord(0, 7, Float.NaN));
        Block block = new Block(42, input, "region");

        assertThat(block.size()).isEqualTo(3);
        assertThat(block.getBinXArray()).containsExactly(3, 100, 0);
        assertThat(block.getBinYArray()).containsExactly(5, 100, 7);
        // compare counts via raw bits so NaN matches NaN
        assertThat(Float.floatToIntBits(block.getCountsArray()[0])).isEqualTo(Float.floatToIntBits(1.5f));
        assertThat(Float.floatToIntBits(block.getCountsArray()[1])).isEqualTo(Float.floatToIntBits(-2f));
        assertThat(Float.isNaN(block.getCountsArray()[2])).isTrue();

        List<ContactRecord> view = block.getContactRecords();
        assertThat(view).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(view.get(i).getBinX()).isEqualTo(input.get(i).getBinX());
            assertThat(view.get(i).getBinY()).isEqualTo(input.get(i).getBinY());
            assertThat(Float.floatToIntBits(view.get(i).getCounts()))
                    .isEqualTo(Float.floatToIntBits(input.get(i).getCounts()));
        }
    }

    @Test
    void arrayConstructorPreservesAllRecords() {
        int[] binX = {1, 2, 3};
        int[] binY = {4, 5, 6};
        float[] counts = {0.5f, 2.5f, 9f};
        Block block = new Block(7, binX, binY, counts, 3, "region");

        assertThat(block.size()).isEqualTo(3);
        assertThat(block.getContactRecords().get(2).getCounts()).isEqualTo(9f);
    }

    @Test
    void getKeyMatchesContactRecordKeyFormat() {
        Block block = new Block(1, new int[]{12}, new int[]{34}, new float[]{1f}, 1, "region");
        assertThat(block.getKey(0, "NONE")).isEqualTo("12_34_NONE");
        assertThat(new ContactRecord(12, 34, 1f).getKey("NONE")).isEqualTo("12_34_NONE");
    }

    @Test
    void emptyBlockHasZeroSize() {
        Block block = new Block(0, "region");
        assertThat(block.size()).isEqualTo(0);
        assertThat(block.getContactRecords()).isEmpty();
    }
}
