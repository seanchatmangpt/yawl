package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD tests for SupervisorSpec fluent builder.
 * No mocks — all tests use real VirtualThreadRuntime and Supervisor.
 */
@DisplayName("SupervisorSpec Fluent Builder Tests")
class SupervisorSpecTest {

    private ActorRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new VirtualThreadRuntime();
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    @DisplayName("builds ONE_FOR_ONE supervisor with children")
    @Timeout(5)
    void buildsOneForOneSupervisor() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger messageCount = new AtomicInteger();

        SupervisorSpec spec = SupervisorSpec.oneForOne("top-sup")
            .maxRestarts(3)
            .withinWindow(Duration.ofMinutes(1))
            .restartDelay(Duration.ofMillis(50))
            .child(ActorSpec.named("w1")
                .behavior(self -> {
                    Object msg = self.recv();
                    messageCount.incrementAndGet();
                    latch.countDown();
                })
                .build())
            .child(ActorSpec.named("w2")
                .behavior(self -> {
                    Object msg = self.recv();
                    messageCount.incrementAndGet();
                    latch.countDown();
                })
                .build())
            .build();

        assertThat(spec.name()).isEqualTo("top-sup");
        assertThat(spec.strategy()).isEqualTo(Supervisor.SupervisorStrategy.ONE_FOR_ONE);
        assertThat(spec.children()).hasSize(2);
        assertThat(spec.maxRestarts()).isEqualTo(3);
    }

    @Test
    @DisplayName("starts supervisor tree on runtime")
    @Timeout(5)
    void startsSupervisorTree() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Supervisor sup = SupervisorSpec.oneForOne("live-sup")
            .maxRestarts(3)
            .withinWindow(Duration.ofMinutes(1))
            .restartDelay(Duration.ofMillis(50))
            .child(ActorSpec.named("worker")
                .behavior(self -> {
                    self.recv();
                    latch.countDown();
                })
                .build())
            .startOn(runtime);

        // Supervisor is running — verify it was created
        assertThat(sup).isNotNull();

        // Clean up
        sup.stop(false);
    }

    @Test
    @DisplayName("creates ONE_FOR_ALL supervisor spec")
    void createsOneForAllSpec() {
        SupervisorSpec spec = SupervisorSpec.oneForAll("all-sup")
            .child(ActorSpec.named("w1")
                .behavior(self -> self.recv())
                .build())
            .build();

        assertThat(spec.strategy()).isEqualTo(Supervisor.SupervisorStrategy.ONE_FOR_ALL);
    }

    @Test
    @DisplayName("creates REST_FOR_ONE supervisor spec")
    void createsRestForOneSpec() {
        SupervisorSpec spec = SupervisorSpec.restForOne("rest-sup")
            .child(ActorSpec.named("w1")
                .behavior(self -> self.recv())
                .build())
            .build();

        assertThat(spec.strategy()).isEqualTo(Supervisor.SupervisorStrategy.REST_FOR_ONE);
    }

    @Test
    @DisplayName("uses default restart parameters")
    void usesDefaultRestartParams() {
        SupervisorSpec spec = SupervisorSpec.oneForOne("default-sup").build();

        assertThat(spec.restartDelay()).isEqualTo(Duration.ofMillis(100));
        assertThat(spec.maxRestarts()).isEqualTo(5);
        assertThat(spec.restartWindow()).isEqualTo(Duration.ofMinutes(1));
        assertThat(spec.children()).isEmpty();
    }

    @Test
    @DisplayName("rejects maxRestarts < 1")
    void rejectsInvalidMaxRestarts() {
        assertThatThrownBy(() ->
            SupervisorSpec.oneForOne("bad").maxRestarts(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString includes strategy and child count")
    void toStringIncludesInfo() {
        SupervisorSpec spec = SupervisorSpec.oneForOne("display-sup")
            .child(ActorSpec.named("w1").behavior(self -> {}).build())
            .build();

        assertThat(spec.toString()).contains("display-sup", "ONE_FOR_ONE", "1");
    }
}
