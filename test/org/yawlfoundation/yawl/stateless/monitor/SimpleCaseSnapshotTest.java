package org.yawlfoundation.yawl.stateless.monitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;

public class SimpleCaseSnapshotTest {

    @Test
    public void testBasicSnapshotCreation() {
        String caseID = "test-case";
        String specID = "test-spec";
        String xml = "<case id=\"test-case\"></case>";
        Instant now = Instant.now();

        CaseSnapshot snapshot = new CaseSnapshot(caseID, specID, xml, now);

        assertEquals(caseID, snapshot.caseID());
        assertEquals(specID, snapshot.specID());
        assertEquals(xml, snapshot.marshalledXML());
        assertEquals(now, snapshot.capturedAt());
    }

    @Test
    public void testSnapshotFactory() {
        String caseID = "factory-test";
        String specID = "factory-spec";
        String xml = "<case id=\"factory-test\"></case>";

        CaseSnapshot snapshot = CaseSnapshot.of(caseID, specID, xml);

        assertEquals(caseID, snapshot.caseID());
        assertEquals(specID, snapshot.specID());
        assertEquals(xml, snapshot.marshalledXML());
        assertNotNull(snapshot.capturedAt());
    }
}