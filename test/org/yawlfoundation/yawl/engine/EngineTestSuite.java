package org.yawlfoundation.yawl.engine;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:57:52
 *
 * JUnit 5 Test Suite for Engine package
 */
@Suite
@SuiteDisplayName("Engine Test Suite")
@SelectClasses({
    // Core engine tests
    TestYEngineInit.class,
    TestCaseCancellation.class,
    TestEngineSystem2.class,
    TestImproperCompletion.class,
    TestOrJoin.class,
    TestRestServiceMethods.class,
    TestSimpleExecutionUseCases.class,
    TestYNetRunner.class,
    TestYWorkItem.class,
    TestYWorkItemID.class,
    TestYWorkItemRepository.class,
    TestYWorkItemStates.class,
    // Phase 1 - New comprehensive tests
    TestYAnnouncer.class,
    TestObserverGateway.class,
    TestObserverGatewayController.class,
    TestWorkItemCompletion.class,
    TestYWorkItemStatus.class,
    TestYNetRunnerRepository.class,
    TestYCaseNbrStore.class,
    TestYProblemEvent.class,
    TestYProblemHandler.class,
    TestYNetData.class,
    TestYSpecificationTable.class,
    TestYDefClientsLoader.class,
    TestYEngineEvent.class,
    TestAnnouncementContext.class
})
public class EngineTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
