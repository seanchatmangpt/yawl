/*
 * Test for the newly implemented handleEnabledWorkItemEvent method in InterfaceX
 */

import org.yawlfoundation.yawl.engine.interfce.interfaceX.InterfaceX_Service;
import org.yawlfoundation.yawl.engine.interfce.interfaceX.InterfaceX_ServiceSideServer;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Test implementation of InterfaceX_Service to verify handleEnabledWorkItemEvent
 */
public class TestInterfaceXService implements InterfaceX_Service {

    private static boolean enabledWorkItemEventCalled = false;
    private static WorkItemRecord lastEnabledWorkItem = null;

    public static void reset() {
        enabledWorkItemEventCalled = false;
        lastEnabledWorkItem = null;
    }

    public static boolean wasEnabledWorkItemEventCalled() {
        return enabledWorkItemEventCalled;
    }

    public static WorkItemRecord getLastEnabledWorkItem() {
        return lastEnabledWorkItem;
    }

    @Override
    public void handleCheckCaseConstraintEvent(YSpecificationID specID, String caseID,
                                              String data, boolean precheck) {
        // Not implemented for this test
    }

    @Override
    public void handleCheckWorkItemConstraintEvent(WorkItemRecord wir, String data, boolean precheck) {
        // Not implemented for this test
    }

    @Override
    public String handleWorkItemAbortException(WorkItemRecord wir, String caseData) {
        // Not implemented for this test
        return null;
    }

    @Override
    public void handleTimeoutEvent(WorkItemRecord wir, String taskList) {
        // Not implemented for this test
    }

    @Override
    public void handleResourceUnavailableException(String resourceID, WorkItemRecord wir,
                                                 String caseData, boolean primary) {
        // Not implemented for this test
    }

    @Override
    public String handleConstraintViolationException(WorkItemRecord wir, String caseData) {
        // Not implemented for this test
        return null;
    }

    @Override
    public void handleCaseCancellationEvent(String caseID) {
        // Not implemented for this test
    }

    /**
     * NEW METHOD: Test implementation of handleEnabledWorkItemEvent
     */
    @Override
    public void handleEnabledWorkItemEvent(WorkItemRecord wir) {
        System.out.println("✅ handleEnabledWorkItemEvent called with work item: " +
                         (wir != null ? wir.getIDString() : "null"));
        enabledWorkItemEventCalled = true;
        lastEnabledWorkItem = wir;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        // Not implemented for this test
        throw new UnsupportedOperationException("doGet not implemented");
    }
}