package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD tests for ActorSpec fluent builder.
 * No mocks — all tests use real VirtualThreadRuntime.
 */
@DisplayName("ActorSpec Fluent Builder Tests")
class ActorSpecTest {

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
    @DisplayName("builds actor spec with name and behavior")
    @Timeout(5)
    void buildsActorSpec() {
        ActorSpec spec = ActorSpec.named("worker")
            .behavior(self -> {
                Object msg = self.recv();
            })
            .build();

        assertThat(spec.name()).isEqualTo("worker");
        assertThat(spec.isBounded()).isFalse();
    }

    @Test
    @DisplayName("builds bounded actor spec")
    @Timeout(5)
    void buildsBoundedActorSpec() {
        ActorSpec spec = ActorSpec.named("bounded-worker")
            .behavior(self -> {
                Object msg = self.recv();
            })
            .boundedMailbox(256)
            .build();

        assertThat(spec.name()).isEqualTo("bounded-worker");
        assertThat(spec.isBounded()).isTrue();
        assertThat(spec.mailboxCapacity()).isEqualTo(256);
    }

    @Test
    @DisplayName("spawns actor on runtime via spawnOn")
    @Timeout(5)
    void spawnsActorOnRuntime() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> received = new AtomicReference<>();

        ActorSpec spec = ActorSpec.named("echo")
            .behavior(self -> {
                Object msg = self.recv();
                received.set(msg);
                latch.countDown();
            })
            .build();

        ActorRef ref = spec.spawnOn(runtime);
        ref.tell("hello");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("rejects build without behavior")
    void rejectsBuildWithoutBehavior() {
        assertThatThrownBy(() -> ActorSpec.named("no-behavior").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("behavior must be set");
    }

    @Test
    @DisplayName("rejects null name")
    void rejectsNullName() {
        assertThatThrownBy(() -> ActorSpec.named(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects zero capacity for bounded mailbox")
    void rejectsZeroCapacity() {
        assertThatThrownBy(() ->
            ActorSpec.named("bad").behavior(self -> {}).boundedMailbox(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString includes name and bounded status")
    void toStringIncludesNameAndBounded() {
        ActorSpec spec = ActorSpec.named("w1")
            .behavior(self -> {})
            .boundedMailbox(64)
            .build();

        assertThat(spec.toString()).contains("w1", "bounded=true", "64");
    }
}
