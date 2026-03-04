package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;
import org.yawlfoundation.yawl.ggen.validation.model.MethodInfo;
import org.yawlfoundation.yawl.ggen.validation.model.CommentInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Simple test implementation of GuardChecker for validation purposes.
 */
public class TestGuardChecker implements GuardChecker {

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        // Test implementation - return empty list for now
        return Collections.emptyList();
    }

    @Override
    public String patternName() {
        return "H_TEST";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }
}