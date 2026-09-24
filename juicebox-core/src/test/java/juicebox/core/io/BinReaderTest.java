package juicebox.core.io;

import htsjdk.tribble.util.LittleEndianInputStream;
import juicebox.core.data.ContactRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests for the sparse block parser. Block bodies are little-endian,
 * so the synthesizer below writes raw little-endian bytes rather than using
 * DataOutputStream (which is big-endian).
 */
class BinReaderTest {

    private static void putShort(ByteArrayOutputStream baos, int v) {
        baos.write(v & 0xff);
        baos.write((v >> 8) & 0xff);
    }

    private static void putFloat(ByteArrayOutputStream baos, float f) {
        int bits = Float.floatToIntBits(f);
        baos.write(bits & 0xff);
        baos.write((bits >> 8) & 0xff);
        baos.write((bits >> 16) & 0xff);
        baos.write((bits >> 24) & 0xff);
    }

    private LittleEndianInputStream stream(byte[] body) {
        return new LittleEndianInputStream(new ByteArrayInputStream(body));
    }

    @Test
    void handleBothShortsDecodesAllRecords() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        putShort(baos, 2);          // rowCount
        // row 0: binY=5, colCount=2
        putShort(baos, 5);
        putShort(baos, 2);
        putShort(baos, 10);
        putShort(baos, 100);        // counts as short (useShort=true)
        putShort(baos, 11);
        putShort(baos, 200);
        // row 1: binY=7, colCount=1
        putShort(baos, 7);
        putShort(baos, 1);
        putShort(baos, 20);
        putShort(baos, 50);

        List<ContactRecord> records = new ArrayList<>();
        BinReader.handleBinType(stream(baos.toByteArray()), (byte) 1, 1000, 2000, records, true, true, true);

        assertThat(records).hasSize(3);
        assertThat(records.get(0).getBinX()).isEqualTo(1000 + 10);
        assertThat(records.get(0).getBinY()).isEqualTo(2000 + 5);
        assertThat(records.get(0).getCounts()).isEqualTo(100f);
        assertThat(records.get(1).getCounts()).isEqualTo(200f);
        assertThat(records.get(2).getBinX()).isEqualTo(1000 + 20);
        assertThat(records.get(2).getBinY()).isEqualTo(2000 + 7);
    }

    @Test
    void handleBothShortsFloatCounts() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        putShort(baos, 1);          // rowCount
        putShort(baos, 3);          // binY
        putShort(baos, 2);          // colCount
        putShort(baos, 1);
        putFloat(baos, 1.5f);
        putShort(baos, 2);
        putFloat(baos, -0.25f);

        List<ContactRecord> records = new ArrayList<>();
        BinReader.handleBinType(stream(baos.toByteArray()), (byte) 1, 0, 0, records, true, true, false);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getCounts()).isEqualTo(1.5f);
        assertThat(records.get(1).getCounts()).isEqualTo(-0.25f);
    }
}
