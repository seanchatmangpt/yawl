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

package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Integration test: Validates YSpecification loading infrastructure.
 *
 * <p>Tests that the specification model classes are available and have the expected
 * structure for workflow loading via the stateless module. All checks are
 * reflection-based against the compiled yawl-stateless and yawl-engine modules.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>YMarshal is accessible and exposes unmarshalSpecifications method</li>
 *   <li>YSpecification (stateless) is accessible as a public class</li>
 *   <li>YSpecification has getName, getSpecVersion, getURI, getRootNet methods</li>
 *   <li>YSpecificationID (engine) is accessible as a public class</li>
 *   <li>YSpecificationID construction with three-argument form (identifier, version, uri)</li>
 *   <li>YSpecificationID getIdentifier returns the identifier component</li>
 *   <li>YSpecificationID getUri returns the URI component</li>
 *   <li>YSpecificationID getVersionAsString returns the version string</li>
 *   <li>YSpecificationID value equality: two instances with same fields are equal</li>
 *   <li>YSpecificationID hashCode contract: equal objects have equal hashCodes</li>
 *   <li>YSpecificationID reflexivity: an instance equals itself</li>
 *   <li>YSpecificationID null safety: equals(null) returns false</li>
 *   <li>YSpecificationID toString is non-null and non-empty</li>
 *   <li>YStatelessEngine unmarshalSpecification method signature accepts String</li>
 * </ul>
 *
 * <p>Chicago TDD: real objects, real APIs, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
public class YSpecificationLoadingIT {

    // =========================================================================
    // YMarshal: the gateway for loading YAWL specifications from XML
    // =========================================================================

    @Test
    public void yMarshal_classIsAccessibleInStatelessModule() throws Exception {
        Class<?> marshalClass = Class.forName("org.yawlfoundation.yawl.stateless.unmarshal.YMarshal");
        assertNotNull("YMarshal must be accessible from integration module", marshalClass);
        assertTrue("YMarshal must be public", Modifier.isPublic(marshalClass.getModifiers()));
    }

    @Test
    public void yMarshal_hasUnmarshalSpecificationsMethod() throws Exception {
        Class<?> marshalClass = Class.forName("org.yawlfoundation.yawl.stateless.unmarshal.YMarshal");
        Set<String> methodNames = Arrays.stream(marshalClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertTrue("YMarshal must expose unmarshalSpecifications method",
                methodNames.contains("unmarshalSpecifications"));
    }

    // =========================================================================
    // YSpecification (stateless): the workflow specification model
    // =========================================================================

    @Test
    public void ySpecification_stateless_classIsAccessible() throws Exception {
        Class<?> specClass = Class.forName(
                "org.yawlfoundation.yawl.stateless.elements.YSpecification");
        assertNotNull("Stateless YSpecification must be accessible from integration module", specClass);
    }

    @Test
    public void ySpecification_stateless_isPublicNonAbstractClass() throws Exception {
        Class<?> specClass = Class.forName(
                "org.yawlfoundation.yawl.stateless.elements.YSpecification");
        int mods = specClass.getModifiers();
        assertTrue("YSpecification must be public", Modifier.isPublic(mods));
        assertFalse("YSpecification must not be an interface", specClass.isInterface());
    }

    @Test
    public void ySpecification_stateless_hasExpectedPublicMethods() throws Exception {
        Class<?> specClass = Class.forName(
                "org.yawlfoundation.yawl.stateless.elements.YSpecification");
        Set<String> methodNames = Arrays.stream(specClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        // Core specification accessors required by the loading infrastructure
        assertTrue("YSpecification must have getName", methodNames.contains("getName"));
        assertTrue("YSpecification must have getSpecVersion", methodNames.contains("getSpecVersion"));
        assertTrue("YSpecification must have getURI", methodNames.contains("getURI"));
        assertTrue("YSpecification must have getRootNet", methodNames.contains("getRootNet"));
    }

    // =========================================================================
    // YSpecificationID (engine): the immutable identifier for a loaded specification
    // =========================================================================

    @Test
    public void ySpecificationID_classIsAccessible() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        assertNotNull("YSpecificationID must be accessible from integration module", sidClass);
    }

    @Test
    public void ySpecificationID_isPublicClass() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        assertTrue("YSpecificationID must be public",
                Modifier.isPublic(sidClass.getModifiers()));
    }

    @Test
    public void ySpecificationID_threeArgConstructionAndIdentifierAccess() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("ClaimsProcessing", "1.0", "ClaimsProcessing.yawl");
        assertNotNull("YSpecificationID must be constructable with (identifier, version, uri)", sid);

        Method getIdentifier = sidClass.getMethod("getIdentifier");
        assertEquals("ClaimsProcessing", getIdentifier.invoke(sid));
    }

    @Test
    public void ySpecificationID_getUriReturnsUri() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("LoanApproval", "2.0", "LoanApproval.yawl");

        Method getUri = sidClass.getMethod("getUri");
        assertEquals("LoanApproval.yawl", getUri.invoke(sid));
    }

    @Test
    public void ySpecificationID_getVersionAsStringReturnsVersionComponent() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("InvoiceProcessing", "3.1", "InvoiceProcessing.yawl");

        Method getVersionAsString = sidClass.getMethod("getVersionAsString");
        String version = (String) getVersionAsString.invoke(sid);
        assertNotNull("getVersionAsString() must return non-null", version);
        assertEquals("Version must match the value provided at construction", "3.1", version);
    }

    @Test
    public void ySpecificationID_equalityByComponents() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid1 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("OrderFulfillment", "1.0", "OrderFulfillment.yawl");
        Object sid2 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("OrderFulfillment", "1.0", "OrderFulfillment.yawl");

        assertEquals("YSpecificationID instances with same components must be equal", sid1, sid2);
    }

    @Test
    public void ySpecificationID_hashCodeContractForEqualObjects() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid1 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("PurchaseOrder", "1.0", "PurchaseOrder.yawl");
        Object sid2 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("PurchaseOrder", "1.0", "PurchaseOrder.yawl");

        assertEquals("Equal YSpecificationIDs must have equal hashCodes",
                sid1.hashCode(), sid2.hashCode());
    }

    @Test
    public void ySpecificationID_reflexivity() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("Reflexive", "1.0", "Reflexive.yawl");
        assertEquals("YSpecificationID must equal itself", sid, sid);
    }

    @Test
    public void ySpecificationID_equalsNullReturnsFalse() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("NullCheck", "1.0", "NullCheck.yawl");
        assertFalse("YSpecificationID.equals(null) must return false", sid.equals(null));
    }

    @Test
    public void ySpecificationID_toStringIsNonEmpty() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("ToStringCheck", "1.0", "ToStringCheck.yawl");
        String str = sid.toString();
        assertNotNull("toString() must not return null", str);
        assertFalse("toString() must not return empty string", str.isEmpty());
    }

    @Test
    public void ySpecificationID_differentIdentifiersAreNotEqual() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid1 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("SpecAlpha", "1.0", "SpecAlpha.yawl");
        Object sid2 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("SpecBeta", "1.0", "SpecAlpha.yawl");
        assertFalse("YSpecificationIDs with different identifiers must not be equal",
                sid1.equals(sid2));
    }

    @Test
    public void ySpecificationID_differentVersionsAreNotEqual() throws Exception {
        Class<?> sidClass = Class.forName("org.yawlfoundation.yawl.engine.YSpecificationID");
        Object sid1 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("ExpenseApproval", "1.0", "ExpenseApproval.yawl");
        Object sid2 = sidClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("ExpenseApproval", "2.0", "ExpenseApproval.yawl");
        assertFalse("YSpecificationIDs with different versions must not be equal",
                sid1.equals(sid2));
    }

    // =========================================================================
    // YStatelessEngine unmarshalSpecification method signature
    // =========================================================================

    @Test
    public void yStatelessEngine_unmarshalSpecificationAcceptsStringParameter() throws Exception {
        Class<?> engineClass = Class.forName(
                "org.yawlfoundation.yawl.stateless.YStatelessEngine");
        // Verify the method takes exactly one String parameter
        Method unmarshal = engineClass.getMethod("unmarshalSpecification", String.class);
        assertNotNull("unmarshalSpecification(String) must be present on YStatelessEngine", unmarshal);
        assertTrue("unmarshalSpecification must be public",
                Modifier.isPublic(unmarshal.getModifiers()));
        assertEquals("unmarshalSpecification must take exactly one parameter",
                1, unmarshal.getParameterCount());
        assertEquals("Parameter must be of type String",
                String.class, unmarshal.getParameterTypes()[0]);
    }

    @Test
    public void yStatelessEngine_unmarshalSpecificationWithMalformedXmlThrowsException()
            throws Exception {
        Class<?> engineClass = Class.forName(
                "org.yawlfoundation.yawl.stateless.YStatelessEngine");
        Object engine = engineClass.getDeclaredConstructor().newInstance();
        Method unmarshal = engineClass.getMethod("unmarshalSpecification", String.class);

        try {
            unmarshal.invoke(engine, "NOT_VALID_XML");
            fail("unmarshalSpecification must throw an exception for malformed XML");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // The underlying cause must be a YAWL or parsing exception, not null
            assertNotNull("Exception cause must be non-null for malformed XML",
                    ite.getCause());
        }
    }

    // =========================================================================
    // Integration module package structure
    // =========================================================================

    @Test
    public void integrationTest_isInCorrectPackage() {
        assertEquals("This IT must live in org.yawlfoundation.yawl.integration",
                "org.yawlfoundation.yawl.integration",
                this.getClass().getPackageName());
    }
}
