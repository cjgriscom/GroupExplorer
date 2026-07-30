package io.chandler.gap.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

public class CanonicalGraphHashTest {

    @Test
    void parseRoundTripTracesAndNauty() {
        String t = "[T41186f80 12ed979 2a061bec]";
        String n = "[N465fc7fa 1c46de86 6812455a]";
        CanonicalGraphHash ht = CanonicalGraphHash.parse(t);
        CanonicalGraphHash hn = CanonicalGraphHash.parse(n);
        assertEquals(0x41186f80, ht.w0());
        assertEquals(0x012ed979, ht.w1());
        assertEquals(t, ht.toString().replace("?", "T"));
        assertEquals(n, hn.toString().replace("?", "N"));
        assertFalse(ht.equals(hn));
    }

    @Test
    void setDedupAndPackedSerialization() throws Exception {
        CanonicalGraphHashSet set = new CanonicalGraphHashSet();
        assertTrue(set.add("[T41186f80 12ed979 2a061bec]"));
        assertFalse(set.add(CanonicalGraphHash.parse("[T41186f80 12ed979 2a061bec]")));
        assertTrue(set.add("[N465fc7fa 1c46de86 6812455a]"));
        assertEquals(2, set.size());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(set);
        }
        CanonicalGraphHashSet roundTrip;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            roundTrip = (CanonicalGraphHashSet) ois.readObject();
        }
        assertEquals(2, roundTrip.size());
        assertTrue(roundTrip.contains("[T41186f80 12ed979 2a061bec]"));
        assertTrue(roundTrip.contains("[N465fc7fa 1c46de86 6812455a]"));
    }
}
