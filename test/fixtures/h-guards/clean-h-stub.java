/**
 * H-Guards Clean: H_STUB Pattern - PASS
 *
 * This fixture returns real values or throws exceptions (no empty stubs).
 * Expected: PASS (no H_STUB violations)
 */
package org.yawlfoundation.yawl.test.guards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CleanHStub {

    public String getData() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "getData requires real database implementation"
        );
    }

    public int getCount() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "getCount requires query engine"
        );
    }

    public String getName() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "getName requires entity resolution"
        );
    }

    public List<String> getItems() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "getItems requires database query implementation"
        );
    }

    public Map<String, String> getConfig() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "getConfig requires configuration loader"
        );
    }

    public boolean isValid() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "isValid requires validation logic"
        );
    }

    public String getDescription() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "getDescription requires metadata loader"
        );
    }
}
