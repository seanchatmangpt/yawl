package org.yawlfoundation.yawl.stateless;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.yawlfoundation.yawl.stateless.monitor.ImportExportPerformanceTest;
import org.yawlfoundation.yawl.stateless.monitor.TestYCase;
import org.yawlfoundation.yawl.stateless.monitor.TestYCaseExporter;
import org.yawlfoundation.yawl.stateless.monitor.TestYCaseImportExportService;
import org.yawlfoundation.yawl.stateless.monitor.TestYCaseImporter;
import org.yawlfoundation.yawl.stateless.monitor.TestYCaseMonitor;

/**
 * JUnit 5 test suite for the stateless YAWL engine (YStatelessEngine).
 * Run stateless mode tests before stateful engine tests (EngineTestSuite).
 *
 * Test coverage:
 * - TestStatelessEngine: Basic launchCase, unmarshal, complete work item
 * - TestYStatelessEngineApi: Comprehensive API tests for stateless workflow execution
 * - TestYStatelessEngineExtended: Malformed XML, timeout config, multi-threaded announcements
 * - StatelessEngineCaseMonitorTest: Case monitoring, multi-threaded announcements, concurrent cases
 * - YStatelessEngineSuspendResumeTest: Suspend/resume, cancel, marshal/restore, listener management
 * - YStatelessEngineParallelLaunchTest: StructuredTaskScope parallel launch, all-or-nothing semantics
 * - TestYCaseImporter: XML unmarshal, runner restoration, identifier hierarchy, work item reunification
 * - TestYCaseExporter: Marshal runner/work items, XML structure validation, timestamp handling
 * - TestYCaseImportExportService: File export, filtering, validation, compression, concurrent ops
 * - TestYCaseMonitor: Concurrent modification, timer accuracy, event handling
 * - TestYCase: Idle timer management, marshal operations
 * - ImportExportPerformanceTest: Benchmarks, memory leak detection, large case handling
 */
@Suite
@SuiteDisplayName("Stateless Test Suite")
@SelectClasses({
    TestStatelessEngine.class,
    TestYStatelessEngineApi.class,
    TestYStatelessEngineExtended.class,
    StatelessEngineCaseMonitorTest.class,
    YStatelessEngineSuspendResumeTest.class,
    YStatelessEngineParallelLaunchTest.class,
    TestYCaseImporter.class,
    TestYCaseExporter.class,
    TestYCaseImportExportService.class,
    TestYCaseMonitor.class,
    TestYCase.class,
    ImportExportPerformanceTest.class
})
public class StatelessTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
