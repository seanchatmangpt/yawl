/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */

/**
 * Input validation for DSPy programs.
 *
 * <h2>Primary Class</h2>
 *
 * {@link org.yawlfoundation.yawl.dspy.otp.schema.DspySchemaValidator} validates
 * Java input maps against a JSON schema before RPC to fail fast and avoid
 * network round-trips for malformed data.
 *
 * <h2>Supported Schema Format</h2>
 *
 * A simplified JSON Schema subset with:
 * <ul>
 *   <li>{@code type}: string, integer, number, boolean, array, object</li>
 *   <li>{@code required}: array of required field names</li>
 *   <li>{@code enum}: array of allowed values</li>
 *   <li>{@code minimum}/{@code maximum}: numeric constraints</li>
 * </ul>
 *
 * <h2>Type Mapping</h2>
 *
 * <table>
 *   <tr><th>Schema Type</th><th>Java Type</th></tr>
 *   <tr><td>string</td><td>java.lang.String</td></tr>
 *   <tr><td>integer</td><td>java.lang.Number</td></tr>
 *   <tr><td>number</td><td>java.lang.Number</td></tr>
 *   <tr><td>boolean</td><td>java.lang.Boolean</td></tr>
 *   <tr><td>array</td><td>java.util.Collection</td></tr>
 *   <tr><td>object</td><td>java.util.Map</td></tr>
 * </table>
 *
 * @see org.yawlfoundation.yawl.dspy.otp.schema.DspySchemaValidator
 */
package org.yawlfoundation.yawl.dspy.otp.schema;
