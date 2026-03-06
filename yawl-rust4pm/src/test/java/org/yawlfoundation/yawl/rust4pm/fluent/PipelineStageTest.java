package org.yawlfoundation.yawl.rust4pm.fluent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PipelineStage} sealed hierarchy.
 */
class PipelineStageTest {

    @Test
    void parseOcel2StoresJson() {
        var stage = new PipelineStage.ParseOcel2("{\"events\":[]}");
        assertEquals("{\"events\":[]}", stage.json());
    }

    @Test
    void parseOcel2RejectsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineStage.ParseOcel2(null));
    }

    @Test
    void parseOcel2RejectsBlank() {
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineStage.ParseOcel2("   "));
    }

    @Test
    void checkConformanceStoresPnml() {
        var stage = new PipelineStage.CheckConformance("<pnml/>");
        assertEquals("<pnml/>", stage.pnmlXml());
    }

    @Test
    void checkConformanceRejectsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineStage.CheckConformance(null));
    }

    @Test
    void checkConformanceRejectsBlank() {
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineStage.CheckConformance(""));
    }

    @Test
    void discoverDfgIsValue() {
        var a = new PipelineStage.DiscoverDfg();
        var b = new PipelineStage.DiscoverDfg();
        assertEquals(a, b);
    }

    @Test
    void computeStatsIsValue() {
        var a = new PipelineStage.ComputeStats();
        var b = new PipelineStage.ComputeStats();
        assertEquals(a, b);
    }

    @Test
    void exhaustiveSwitchCoversAllStages() {
        PipelineStage[] stages = {
            new PipelineStage.ParseOcel2("{\"events\":[]}"),
            new PipelineStage.DiscoverDfg(),
            new PipelineStage.CheckConformance("<pnml/>"),
            new PipelineStage.ComputeStats()
        };

        for (PipelineStage stage : stages) {
            String name = switch (stage) {
                case PipelineStage.ParseOcel2 p -> "parse";
                case PipelineStage.DiscoverDfg d -> "dfg";
                case PipelineStage.CheckConformance c -> "conformance";
                case PipelineStage.ComputeStats s -> "stats";
            };
            assertNotNull(name);
        }
    }
}
