package org.yawlfoundation.yawl.engine.agent.core;

/**
 * Functional interface for actor behavior that may call recv() (which throws InterruptedException).
 *
 * Unlike Consumer<ActorRef>, this interface declares throws InterruptedException,
 * allowing behavior lambdas to call ActorRef.recv() without wrapping in try-catch.
 */
@FunctionalInterface
public interface ActorBehavior {
    void run(ActorRef self) throws InterruptedException;
}
