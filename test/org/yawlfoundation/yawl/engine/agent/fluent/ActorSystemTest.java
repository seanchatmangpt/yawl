package org.yawlfoundation.yawl.engine.agent.fluent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.Msg;
import org.yawlfoundation.yawl.engine.agent.core.Supervisor;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ActorSystemTest {

    @Test
    @Timeout(5)
    void builderCreatesSystemWithNamedActors() {
        try (var system = ActorSystem.builder()
                .runtime(new VirtualThreadRuntime())
                .supervisor(s -> s
                    .strategy(Supervisor.SupervisorStrategy.ONE_FOR_ONE)
                    .maxRestarts(3)
                    .within(Duration.ofMinutes(1)))
                .actor("worker-a", self -> {
                    while (true) { self.recv(); }
                })
                .actor("worker-b", self -> {
                    while (true) { self.recv(); }
                })
                .build()) {

            system.start();

            assertEquals(2, system.size());
            assertTrue(system.actorNames().contains("worker-a"));
            assertTrue(system.actorNames().contains("worker-b"));
            assertTrue(system.lookup("worker-a").isPresent());
            assertTrue(system.lookup("nonexistent").isEmpty());
        }
    }

    @Test
    @Timeout(5)
    void tellDeliversMessageToNamedActor() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<Object> captured = new AtomicReference<>();

        try (var system = ActorSystem.builder()
                .actor("echo", self -> {
                    Object msg = self.recv();
                    if (msg instanceof Msg.Command cmd) {
                        captured.set(cmd.payload());
                        received.countDown();
                    }
                })
                .build()) {

            system.start();
            system.tell("echo", new Msg.Command("TEST", "hello-fluent"));

            assertTrue(received.await(2, TimeUnit.SECONDS),
                "Actor must receive message within 2 seconds");
            assertEquals("hello-fluent", captured.get());
        }
    }

    @Test
    @Timeout(5)
    void tellThrowsForUnknownActor() {
        try (var system = ActorSystem.builder()
                .actor("only-one", self -> { while (true) { self.recv(); } })
                .build()) {

            system.start();

            assertThrows(IllegalArgumentException.class, () ->
                system.tell("nonexistent", "msg"));
        }
    }

    @Test
    @Timeout(5)
    void requireThrowsForMissingActor() {
        try (var system = ActorSystem.builder()
                .actor("present", self -> { while (true) { self.recv(); } })
                .build()) {

            assertNotNull(system.require("present"));
            assertThrows(IllegalArgumentException.class, () ->
                system.require("absent"));
        }
    }

    @Test
    @Timeout(5)
    void duplicateActorNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            ActorSystem.builder()
                .actor("same", self -> { while (true) { self.recv(); } })
                .actor("same", self -> { while (true) { self.recv(); } })
                .build());
    }

    @Test
    @Timeout(5)
    void defaultsApplyWhenNotConfigured() {
        try (var system = ActorSystem.builder()
                .actor("default-worker", self -> { while (true) { self.recv(); } })
                .build()) {

            assertNotNull(system.runtime());
            assertNotNull(system.supervisor());
            assertEquals(1, system.size());
        }
    }

    @Test
    @Timeout(5)
    void closeStopsAllActors() throws InterruptedException {
        var system = ActorSystem.builder()
            .actor("ephemeral", self -> { while (true) { self.recv(); } })
            .build();

        system.start();
        var ref = system.require("ephemeral");
        assertTrue(ref.isAlive());

        system.close();

        // Give virtual thread time to terminate
        Thread.sleep(100);
        assertFalse(ref.isAlive());
    }

    @Test
    @Timeout(5)
    void isAliveReflectsActorState() {
        try (var system = ActorSystem.builder()
                .actor("mortal", self -> {
                    self.recv(); // receive one message then exit
                })
                .build()) {

            system.start();

            // Initially alive
            assertTrue(system.isAlive("mortal"));
            assertFalse(system.isAlive("nonexistent"));
        }
    }
}
