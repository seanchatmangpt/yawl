package org.yawlfoundation.yawl.engine;

import java.util.Iterator;
import java.util.Set;

import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YStateException;

/**
 * @author Lachlan Aldred
 * Date: 26/11/2004
 * Time: 17:42:40
 *
 * Enhanced for Phase 3: Thread-local isolation support
 * When running tests in parallel with thread-local YEngine instances,
 * EngineClearer.clear() now routes cleanup through ThreadLocalYEngineManager
 * to ensure each thread only clears its own instance.
 */
public class EngineClearer {
    /**
     * Clears all specifications and running cases from the provided engine instance.
     *
     * If thread-local isolation is enabled (via system property
     * yawl.test.threadlocal.isolation=true), this delegates to
     * ThreadLocalYEngineManager to ensure thread-local cleanup.
     *
     * Otherwise, performs traditional cleanup on the provided engine instance.
     *
     * Operation:
     * 1. Iterate over all loaded specifications
     * 2. For each specification: cancel all running cases
     * 3. Unload the specification from the engine
     *
     * This is idempotent and can be safely called multiple times.
     *
     * @param engine the YEngine instance to clear
     * @throws YPersistenceException if case cancellation fails
     * @throws YEngineStateException if specification unloading fails
     */
    public static void clear(YEngine engine) throws YPersistenceException, YEngineStateException {
        // Route through ThreadLocalYEngineManager if isolation is enabled
        if (ThreadLocalYEngineManager.isIsolationEnabled()) {
            ThreadLocalYEngineManager.clearCurrentThread();
            return;
        }

        // Original implementation for sequential/backward-compatible mode
        clearEngine(engine);
    }

    /**
     * Internal method that performs the actual engine cleanup.
     * Extracted to allow both sequential and thread-local modes to use it.
     *
     * @param engine the YEngine instance to clear
     * @throws YPersistenceException if case cancellation fails
     * @throws YEngineStateException if specification unloading fails
     */
    private static void clearEngine(YEngine engine)
            throws YPersistenceException, YEngineStateException {
        while (engine.getLoadedSpecificationIDs().iterator().hasNext()) {
            YSpecificationID specID = engine.getLoadedSpecificationIDs().iterator().next();
            Set caseIDs = engine.getCasesForSpecification(specID);
            for (Iterator iterator2 = caseIDs.iterator(); iterator2.hasNext();) {
                YIdentifier identifier = (YIdentifier) iterator2.next();
                engine.cancelCase(identifier);
            }
            try {
                engine.unloadSpecification(specID);
            } catch (YStateException e) {
                e.printStackTrace();
            }
        }
    }
}
