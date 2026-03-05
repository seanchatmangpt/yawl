/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.security;

import java.io.ObjectInputFilter;

/**
 * Security configuration for safe Java deserialization.
 *
 * <p>This class provides allowlist-based ObjectInputFilter configurations to prevent
 * gadget chain attacks through unsafe deserialization. All ObjectInputStream usage
 * in YAWL must use these filters to prevent Remote Code Execution (RCE) vulnerabilities.</p>
 *
 * <h2>Security Context</h2>
 * <p>Unsafe deserialization is a critical vulnerability (CWE-502) that allows attackers
 * to execute arbitrary code by crafting malicious serialized objects. This is mitigated
 * by restricting deserialization to only known-safe classes.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ObjectInputStream ois = new ObjectInputStream(inputStream);
 * ois.setObjectInputFilter(ObjectInputStreamConfig.createYAWLAllowlist());
 * Object safeObject = ois.readObject();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see <a href="https://owasp.org/www-community/vulnerabilities/Deserialization_of_untrusted_data">OWASP: Deserialization of Untrusted Data</a>
 */
public class ObjectInputStreamConfig {

    /**
     * Creates a safe ObjectInputFilter that only allows whitelisted YAWL classes.
     *
     * <p>This filter prevents gadget chain attacks by rejecting all classes except:</p>
     * <ul>
     *   <li>java.lang.* (String, Integer, etc.)</li>
     *   <li>java.util.* (ArrayList, HashMap, etc.)</li>
     *   <li>java.io.* (Serializable primitives)</li>
     *   <li>org.yawlfoundation.yawl.* (YAWL classes)</li>
     * </ul>
     *
     * <p>All other classes including dangerous gadget chain classes (e.g.,
     * org.apache.commons.collections.*) are explicitly rejected.</p>
     *
     * @return ObjectInputFilter configured with YAWL allowlist
     */
    public static ObjectInputFilter createYAWLAllowlist() {
        return ObjectInputFilter.Config.createFilter(
            "java.lang.*;" +
            "java.util.*;" +
            "java.io.Serializable;" +
            "org.yawlfoundation.yawl.*;" +
            "!*"  // Deny all others (including Apache Commons Collections, Spring, etc.)
        );
    }

    /**
     * Creates a strict ObjectInputFilter for deep copy operations.
     *
     * <p>This filter is more restrictive and only allows basic Java types and
     * YAWL domain objects. Use this for internal deep copy operations where
     * you control the serialized content.</p>
     *
     * @return ObjectInputFilter configured for deep copy operations
     */
    public static ObjectInputFilter createDeepCopyFilter() {
        return ObjectInputFilter.Config.createFilter(
            "java.lang.String;" +
            "java.lang.Integer;" +
            "java.lang.Long;" +
            "java.lang.Double;" +
            "java.lang.Boolean;" +
            "java.util.ArrayList;" +
            "java.util.HashMap;" +
            "java.util.HashSet;" +
            "java.util.Date;" +
            "org.yawlfoundation.yawl.*;" +
            "!*"
        );
    }

    /**
     * Validates that a class is safe for deserialization based on YAWL allowlist.
     *
     * <p>Use this method to programmatically check if a class can be safely
     * deserialized before attempting deserialization.</p>
     *
     * @param clazz the class to validate
     * @return true if the class is on the YAWL allowlist, false otherwise
     */
    public static boolean isSafeClass(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        String name = clazz.getName();
        return name.startsWith("org.yawlfoundation.yawl.") ||
               name.startsWith("java.lang.") ||
               name.startsWith("java.util.") ||
               name.equals("java.io.Serializable");
    }

    /**
     * Creates a custom ObjectInputFilter with additional allowed packages.
     *
     * <p>Use this only when you need to deserialize third-party classes that
     * have been security-audited and deemed safe. Always prefer the default
     * {@link #createYAWLAllowlist()} when possible.</p>
     *
     * @param additionalPackages semicolon-separated list of additional packages to allow
     *                          (e.g., "com.example.safe.*;org.vendor.secure.*")
     * @return ObjectInputFilter with YAWL allowlist + additional packages
     */
    public static ObjectInputFilter createCustomAllowlist(String additionalPackages) {
        if (additionalPackages == null || additionalPackages.trim().isEmpty()) {
            return createYAWLAllowlist();
        }

        return ObjectInputFilter.Config.createFilter(
            "java.lang.*;" +
            "java.util.*;" +
            "java.io.Serializable;" +
            "org.yawlfoundation.yawl.*;" +
            additionalPackages + ";" +
            "!*"
        );
    }
}
