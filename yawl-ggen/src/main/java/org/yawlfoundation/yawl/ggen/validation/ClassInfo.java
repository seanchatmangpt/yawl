package org.yawlfoundation.yawl.ggen.validation;

import java.util.List;

import org.yawlfoundation.yawl.ggen.validation.model.MethodInfo;
import org.yawlfoundation.yawl.ggen.validation.model.FieldInfo;

/**
 * Represents class information extracted from AST analysis.
 */
public class ClassInfo {
    private final String name;
    private final int lineNumber;
    private final List<MethodInfo> methods;
    private final List<FieldInfo> fields;
    private final String packageName;

    public ClassInfo(String name, int lineNumber, List<MethodInfo> methods, List<FieldInfo> fields, String packageName) {
        this.name = name;
        this.lineNumber = lineNumber;
        this.methods = methods;
        this.fields = fields;
        this.packageName = packageName;
    }

    // Getters
    public String getName() { return name; }
    public int getLineNumber() { return lineNumber; }
    public List<MethodInfo> getMethods() { return methods; }
    public List<FieldInfo> getFields() { return fields; }
    public String getPackageName() { return packageName; }
}