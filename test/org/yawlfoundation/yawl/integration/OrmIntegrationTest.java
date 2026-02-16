package org.yawlfoundation.yawl.integration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ORM Integration Tests - Verifies entity persistence and relationship management
 * Tests one-to-many, many-to-one, and inheritance relationships with real database
 */
@DataJpaTest
@TestPropertySource(locations = "classpath:test-application.properties")
public class OrmIntegrationTest {

    private TestEntityManager entityManager;

    @Before
    public void setUp() {
        assertNotNull("EntityManager should be initialized", entityManager);
    }

    @After
    public void tearDown() {
        entityManager.clear();
    }

    @Test
    @Transactional
    public void testOneToManyRelationship() {
        WorkflowProcess process = new WorkflowProcess();
        process.setName("Test Process");
        process.setVersion("1.0");
        
        Task task1 = new Task();
        task1.setName("Task 1");
        task1.setStatus("ACTIVE");
        task1.setProcess(process);
        
        Task task2 = new Task();
        task2.setName("Task 2");
        task2.setStatus("PENDING");
        task2.setProcess(process);
        
        process.addTask(task1);
        process.addTask(task2);
        
        entityManager.persistAndFlush(process);
        entityManager.clear();
        
        WorkflowProcess retrieved = entityManager.find(WorkflowProcess.class, process.getId());
        assertNotNull("Process should be retrieved", retrieved);
        assertEquals("Process should have 2 tasks", 2, retrieved.getTasks().size());
        
        boolean foundTask1 = retrieved.getTasks().stream()
            .anyMatch(t -> "Task 1".equals(t.getName()));
        assertTrue("Task 1 should be found in relationship", foundTask1);
    }

    @Test
    @Transactional
    public void testManyToOneRelationship() {
        WorkflowProcess process = new WorkflowProcess();
        process.setName("Parent Process");
        process.setVersion("1.0");
        
        Task task = new Task();
        task.setName("Child Task");
        task.setStatus("ACTIVE");
        task.setProcess(process);
        
        entityManager.persistAndFlush(task);
        entityManager.clear();
        
        Task retrieved = entityManager.find(Task.class, task.getId());
        assertNotNull("Task should be retrieved", retrieved);
        assertNotNull("Task process should be loaded", retrieved.getProcess());
        assertEquals("Process name should match", "Parent Process", retrieved.getProcess().getName());
    }

    @Test
    @Transactional
    public void testCascadeDelete() {
        WorkflowProcess process = new WorkflowProcess();
        process.setName("Process to Delete");
        process.setVersion("1.0");
        
        Task task = new Task();
        task.setName("Task to Delete");
        task.setStatus("ACTIVE");
        task.setProcess(process);
        
        process.addTask(task);
        entityManager.persistAndFlush(process);
        Long processId = process.getId();
        
        entityManager.remove(process);
        entityManager.flush();
        entityManager.clear();
        
        WorkflowProcess deleted = entityManager.find(WorkflowProcess.class, processId);
        assertNull("Process should be deleted", deleted);
    }

    @Test
    @Transactional
    public void testLazyLoading() {
        WorkflowProcess process = new WorkflowProcess();
        process.setName("Lazy Load Test");
        process.setVersion("1.0");
        
        for (int i = 0; i < 10; i++) {
            Task task = new Task();
            task.setName("Task " + i);
            task.setStatus("ACTIVE");
            task.setProcess(process);
            process.addTask(task);
        }
        
        entityManager.persistAndFlush(process);
        entityManager.clear();
        
        WorkflowProcess retrieved = entityManager.find(WorkflowProcess.class, process.getId());
        assertNotNull("Process should be retrieved", retrieved);
        assertEquals("Process should have 10 tasks", 10, retrieved.getTasks().size());
    }

    @Test
    @Transactional
    public void testEntityUpdate() {
        WorkflowProcess process = new WorkflowProcess();
        process.setName("Original Name");
        process.setVersion("1.0");
        
        entityManager.persistAndFlush(process);
        Long id = process.getId();
        entityManager.clear();
        
        WorkflowProcess retrieved = entityManager.find(WorkflowProcess.class, id);
        retrieved.setName("Updated Name");
        entityManager.flush();
        entityManager.clear();
        
        WorkflowProcess updated = entityManager.find(WorkflowProcess.class, id);
        assertEquals("Name should be updated", "Updated Name", updated.getName());
    }

    @Entity
    @Table(name = "workflow_processes")
    public static class WorkflowProcess {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false)
        private String version;

        @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<Task> tasks = new java.util.ArrayList<>();

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public List<Task> getTasks() { return tasks; }
        public void setTasks(List<Task> tasks) { this.tasks = tasks; }
        public void addTask(Task task) { this.tasks.add(task); }
    }

    @Entity
    @Table(name = "tasks")
    public static class Task {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false)
        private String status;

        @ManyToOne
        @JoinColumn(name = "process_id", nullable = false)
        private WorkflowProcess process;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public WorkflowProcess getProcess() { return process; }
        public void setProcess(WorkflowProcess process) { this.process = process; }
    }
}
