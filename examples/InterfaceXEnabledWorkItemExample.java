/*
 * Example of how to use the new handleEnabledWorkItemEvent method in InterfaceX
 */

import org.yawlfoundation.yawl.engine.interfce.interfaceX.InterfaceX_Service;
import org.yawlfoundation.yawl.engine.interfce.interfaceX.InterfaceX_EngineSideClient;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YSpecificationID;

/**
 * Example implementation of an exception service that handles enabled work item events.
 */
public class EnabledWorkItemExceptionHandler implements InterfaceX_Service {

    @Override
    public void handleEnabledWorkItemEvent(WorkItemRecord wir) {
        // This method is called when a work item becomes enabled
        if (wir != null) {
            System.out.println("🎯 Work Item Enabled: " + wir.getIDString());
            System.out.println("  Case: " + wir.getCaseID());
            System.out.println("  Task: " + wir.getTaskID());
            System.out.println("  Status: " + wir.getStatus());

            // Add custom logic here, for example:
            // - Send notification to resource
            // - Log to audit system
            // - Update monitoring dashboard
            // - Apply business rules
        }
    }

    // Other required methods from InterfaceX_Service interface
    @Override
    public void handleCheckCaseConstraintEvent(YSpecificationID specID, String caseID,
                                              String data, boolean precheck) {
        // Implementation for case constraint checking
    }

    @Override
    public void handleCheckWorkItemConstraintEvent(WorkItemRecord wir, String data, boolean precheck) {
        // Implementation for work item constraint checking
    }

    @Override
    public String handleWorkItemAbortException(WorkItemRecord wir, String caseData) {
        // Implementation for work item abort exceptions
        return null;
    }

    @Override
    public void handleTimeoutEvent(WorkItemRecord wir, String taskList) {
        // Implementation for timeout events
    }

    @Override
    public void handleResourceUnavailableException(String resourceID, WorkItemRecord wir,
                                                 String caseData, boolean primary) {
        // Implementation for resource unavailable exceptions
    }

    @Override
    public String handleConstraintViolationException(WorkItemRecord wir, String caseData) {
        // Implementation for constraint violations
        return null;
    }

    @Override
    public void handleCaseCancellationEvent(String caseID) {
        // Implementation for case cancellation events
    }
}

/**
 * Example of how the engine uses InterfaceX to announce enabled work items
 */
class EnabledWorkItemAnnouncementExample {

    public void demonstrateAnnouncement() {
        // This is done automatically by the engine when work items become enabled
        // But this shows how it would work manually if needed

        InterfaceX_EngineSideClient client = new InterfaceX_EngineSideClient(
            "http://exception-service.example.com");

        // The engine would do something like this automatically:
        // YWorkItem workItem = ...; // Get enabled work item
        // client.announceEnabledWorkItem(workItem);

        System.out.println("When a work item becomes enabled, the engine will:");
        System.out.println("1. Create the work item with enabled status");
        System.out.println("2. Check if InterfaceX listeners are registered");
        System.out.println("3. Call announceEnabledWorkItem() on each listener");
        System.out.println("4. The exception service's handleEnabledWorkItemEvent() will be called");
    }
}