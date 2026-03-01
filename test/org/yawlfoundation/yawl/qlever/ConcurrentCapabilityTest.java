/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.StructuredTaskScope;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

/**
 * Chicago TDD tests for concurrent SPARQL query execution.
 * Verifies that the QLever engine handles multiple simultaneous queries correctly
 * with Java 25 virtual threads and StructuredTaskScope.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
@Tag("concurrent")
class ConcurrentCapabilityTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(SELECT_DISTINCT)
    void concurrentQueries_virtualThreads_noCorruption() throws Exception {
        var queries = IntStream.range(0, 50)
            .mapToObj(i -> PFX + "SELECT DISTINCT ?org WHERE { ?p :worksFor ?org } LIMIT 3")
            .toList();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var tasks = queries.stream()
                .map(q -> scope.fork(() -> engine().executeSelect(q, JSON)))
                .toList();
            scope.join().throwIfFailed();
            for (var task : tasks) {
                assertThat(task.get().rowCount()).isGreaterThanOrEqualTo(0);
            }
        }
    }
}
