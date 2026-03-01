package org.yawlfoundation.yawl.engine.agent.core;

/**
 * Sentinel message used by ActorRef.injectException() to cause an actor's
 * recv() call to throw the specified RuntimeException.
 *
 * Internal use only. Never sent by user code directly.
 */
record ExceptionTrigger(RuntimeException cause) {}
