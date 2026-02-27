/*
 * Debug version of timer race test
 */

public class TimerRaceDebug {
    public static void main(String[] args) {
        System.out.println("=== Timer Race Debug ===");
        
        for (int i = 0; i < 3; i++) {
            System.out.println("\nTest run " + i);
            boolean result = executeTimerRace(i);
            System.out.println("Result: " + (result ? "PASS" : "FAIL"));
        }
    }
    
    private static boolean executeTimerRace(int runId) {
        YawlWorkItem workItem = new YawlWorkItem("timer-test-" + runId);
        workItem.setStatus(WorkItemStatus.EXECUTING);
        
        System.out.println("Initial status: " + workItem.getStatus());
        
        // Just external completion
        try {
            workItem.setStatus(WorkItemStatus.COMPLETE);
            System.out.println("External completion succeeded: " + workItem.getStatus());
            return true;
        } catch (Exception e) {
            System.out.println("External completion failed: " + e.getMessage());
            return false;
        }
    }
    
    static class YawlWorkItem {
        private volatile WorkItemStatus status;
        private final String id;
        
        public YawlWorkItem(String id) {
            this.id = id;
            this.status = WorkItemStatus.ENABLED;
        }
        
        public synchronized void setStatus(WorkItemStatus newStatus) {
            System.out.println("Setting " + id + " from " + status + " to " + newStatus);
            
            if (newStatus == null) {
                throw new IllegalArgumentException("Status cannot be null");
            }
            
            if (isTerminal(status) && !isTerminal(newStatus)) {
                System.out.println("ERROR: Cannot transition from terminal " + status + " to " + newStatus);
                throw new IllegalStateException("Cannot transition from terminal state");
            }
            
            if (isTerminal(status) && isTerminal(newStatus) && status != newStatus) {
                System.out.println("ERROR: Cannot change from terminal " + status + " to " + newStatus);
                throw new IllegalStateException("Cannot change terminal state");
            }
            
            this.status = newStatus;
        }
        
        public WorkItemStatus getStatus() {
            return status;
        }
        
        private boolean isTerminal(WorkItemStatus status) {
            return status == WorkItemStatus.COMPLETE || 
                   status == WorkItemStatus.FAILED ||
                   status == WorkItemStatus.CANCELLED_BY_CASE ||
                   status == WorkItemStatus.DELETED ||
                   status == WorkItemStatus.WITHDRAWN;
        }
    }
    
    enum WorkItemStatus {
        ENABLED, FIRED, EXECUTING, COMPLETE, FAILED, CANCELLED_BY_CASE, DELETED, WITHDRAWN
    }
}
