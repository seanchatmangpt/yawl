import java.util.*;
import java.util.concurrent.*;

public class SimpleWorkflowBenchmark {
    
    public static void main(String[] args) {
        System.out.println("Workflow Execution Benchmark");
        System.out.println("===========================");
        
        runCaseCreationBenchmark();
        runTaskExecutionBenchmark();
        runEventProcessingBenchmark();
        
        System.out.println("\nWorkflow execution benchmark completed!");
    }
    
    private static void runCaseCreationBenchmark() {
        System.out.println("\n--- Case Creation Benchmark ---");
        
        int[] caseCounts = {100, 500, 1000};
        
        for (int cases : caseCounts) {
            long creationTime = testCaseCreation(cases);
            long avgTimePerCase = creationTime / cases;
            
            System.out.printf("Cases: %,d | Total: %,dms | Avg: %dms/case%n",
                cases, creationTime, avgTimePerCase);
        }
    }
    
    private static long testCaseCreation(int cases) {
        long start = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(cases);
        
        for (int i = 0; i < cases; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    // Simulate case creation
                    WorkflowCase workflowCase = new WorkflowCase("case-" + caseId);
                    workflowCase.addTask("initial");
                    
                    // Simulate some processing time
                    Thread.sleep(1);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static void runTaskExecutionBenchmark() {
        System.out.println("\n--- Task Execution Benchmark ---");
        
        int[] taskCounts = {1000, 5000, 10000};
        
        for (int tasks : taskCounts) {
            long executionTime = testTaskExecution(tasks);
            double tasksPerSec = tasks / (executionTime / 1000.0);
            
            System.out.printf("Tasks: %,d | Total: %,dms | Throughput: %,.0f tasks/sec%n",
                tasks, executionTime, tasksPerSec);
        }
    }
    
    private static long testTaskExecution(int tasks) {
        long start = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(tasks);
        
        for (int i = 0; i < tasks; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // Simulate task execution
                    TaskExecution.execute(taskId);
                    Thread.sleep(2);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static void runEventProcessingBenchmark() {
        System.out.println("\n--- Event Processing Benchmark ---");
        
        int[] eventCounts = {1000, 5000, 10000};
        
        for (int events : eventCounts) {
            long processingTime = testEventProcessing(events);
            double eventsPerSec = events / (processingTime / 1000.0);
            
            System.out.printf("Events: %,d | Total: %,dms | Throughput: %,.0f events/sec%n",
                events, processingTime, eventsPerSec);
        }
    }
    
    private static long testEventProcessing(int events) {
        long start = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(events);
        
        for (int i = 0; i < events; i++) {
            final int eventId = i;
            executor.submit(() -> {
                try {
                    // Simulate event processing
                    EventProcessor.process(eventId);
                    Thread.sleep(1);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    // Helper classes
    static class WorkflowCase {
        String caseId;
        List<String> tasks;
        
        public WorkflowCase(String caseId) {
            this.caseId = caseId;
            this.tasks = new ArrayList<>();
        }
        
        public void addTask(String task) {
            tasks.add(task);
        }
    }
    
    static class TaskExecution {
        public static void execute(int taskId) {
            // Simulate task execution
            double result = 0;
            for (int i = 0; i < 100; i++) {
                result += Math.sqrt(taskId + i);
            }
        }
    }
    
    static class EventProcessor {
        public static void process(int eventId) {
            // Simulate event processing
            Map<String, Object> event = new HashMap<>();
            event.put("id", eventId);
            event.put("timestamp", System.currentTimeMillis());
            event.put("type", "workflow-event");
            
            // Process event
            String eventType = (String) event.get("type");
            switch (eventType) {
                case "workflow-event":
                    // Handle workflow event
                    break;
                case "task-event":
                    // Handle task event
                    break;
            }
        }
    }
}
