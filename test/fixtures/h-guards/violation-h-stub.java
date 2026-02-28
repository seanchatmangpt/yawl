/**
 * H-Guards Violation: H_STUB Pattern
 *
 * This fixture contains empty returns like "", 0, null, Collections.empty*
 * Expected: FAIL with pattern H_STUB
 */
package org.yawlfoundation.yawl.test.guards;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ViolationHStub {

    public String getData() {
        return "";
    }

    public int getCount() {
        return 0;
    }

    public String getName() {
        return null;
    }

    public List<String> getItems() {
        return Collections.emptyList();
    }

    public Map<String, String> getConfig() {
        return Collections.emptyMap();
    }

    public boolean isValid() {
        return false;
    }

    public String getDescription() {
        return Collections.EMPTY_LIST.toString();
    }
}
