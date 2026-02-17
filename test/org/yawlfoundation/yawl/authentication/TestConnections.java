package org.yawlfoundation.yawl.authentication;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.exceptions.YAuthenticationException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 *
 * @author Lachlan Aldred
 * Date: 29/01/2004
 * Time: 16:17:58
 *
 */
class TestConnections {
    private YSessionCache _sessionCache;
    private YEngine _engine;

    @BeforeEach
    void setUp() throws YAuthenticationException {
        _engine = YEngine.getInstance();
        _sessionCache = _engine.getSessionCache();
    }

    @Test
    void testConnect() throws YPersistenceException {
        clearUsers();
        try {
            _engine.addExternalClient(new YExternalClient("fred", "head", "doco"));
        } catch (YPersistenceException e) {
            fail(e.getMessage());
        }
        boolean valid = _sessionCache.checkConnection("gobbledey gook");
        assertFalse(valid);

        String outcome = _sessionCache.connect("fred", "1234", -1);
        assertTrue(outcome.startsWith("<fail"));

        outcome = _sessionCache.connect("fred", "head", 3600);
        assertFalse(outcome.startsWith("<fail"));

        _engine.addExternalClient(new YExternalClient("derf", "wert", null));
        _engine.removeExternalClient("derf");
    }

    private void clearUsers()  {
        _sessionCache.clear();
    }

    @Test
    void testUnbreakable() throws YAuthenticationException {
        clearUsers();

        boolean valid;
        valid = _sessionCache.checkConnection(null);
        assertFalse(valid);

        valid = _sessionCache.checkConnection("123");
        assertFalse(valid);
    }

    @Test
    void testRobust() throws YPersistenceException {
        clearUsers();
        boolean added = _engine.addExternalClient(new YExternalClient(null, null, null));
        assertFalse(added);

        String outcome = _sessionCache.connect(null, null, -1);
        assertTrue(outcome.startsWith("<fail"));
    }

    @Test
    void testRemoveUser() throws YPersistenceException {
        clearUsers();
        _engine.addExternalClient(new YExternalClient("fred", "head", "doco"));

        String handle = _sessionCache.connect("fred", "head", 1200);

        _engine.addExternalClient(new YExternalClient("derf", "wert", null));
        _engine.removeExternalClient("fred");

        boolean valid = _sessionCache.checkConnection(handle);
        assertFalse(valid);
        _engine.removeExternalClient("derf");
    }
}
