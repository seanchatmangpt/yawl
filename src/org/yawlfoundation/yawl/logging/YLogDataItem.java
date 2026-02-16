/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.logging;

import org.jdom2.Element;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.Serializable;

/**
 * Immutable logging data item record.
 * Converted to Java 25 record for improved immutability and type safety.
 *
 * @author Michael Adams
 * @author YAWL Foundation (Java 25 conversion)
 * @since 2.0
 * @version 5.2
 *
 * @param name Name of the log data item
 * @param value Value of the log data item
 * @param dataTypeName Data type name
 * @param dataTypeDefinition Full data type definition
 * @param descriptor Meaningful string describing the class, category or group
 */
public record YLogDataItem(
    String name,
    String value,
    String dataTypeName,
    String dataTypeDefinition,
    String descriptor
) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor (no data).
     */
    public YLogDataItem() {
        this(null, null, null, null, null);
    }

    /**
     * Constructor with basic fields (dataTypeDefinition defaults to dataTypeName).
     */
    public YLogDataItem(String descriptor, String name, String value, String dataTypeName) {
        this(name, value, dataTypeName, dataTypeName, descriptor);
    }

    /**
     * Constructor from XML string.
     */
    public YLogDataItem(String xml) {
        this(JDOMUtil.stringToElement(xml));
    }

    /**
     * Constructor from XML Element.
     */
    public YLogDataItem(Element xml) {
        this(
            xml != null ? JDOMUtil.decodeEscapes(xml.getChildText("name")) : null,
            xml != null ? JDOMUtil.decodeEscapes(xml.getChildText("value")) : null,
            xml != null ? JDOMUtil.decodeEscapes(xml.getChildText("datatype")) : null,
            xml != null ? JDOMUtil.decodeEscapes(xml.getChildText("datatypedefinition")) : null,
            xml != null ? JDOMUtil.decodeEscapes(xml.getChildText("descriptor")) : null
        );
    }

    /**
     * Legacy getter for name (maintains backward compatibility).
     * @deprecated Use name() accessor instead
     */
    @Deprecated
    public String getName() {
        return name;
    }

    /**
     * Legacy getter for value (maintains backward compatibility).
     * @deprecated Use value() accessor instead
     */
    @Deprecated
    public String getValue() {
        return value;
    }

    /**
     * Legacy getter for dataTypeName (maintains backward compatibility).
     * @deprecated Use dataTypeName() accessor instead
     */
    @Deprecated
    public String getDataTypeName() {
        return dataTypeName;
    }

    /**
     * Legacy getter for dataTypeDefinition (maintains backward compatibility).
     * @deprecated Use dataTypeDefinition() accessor instead
     */
    @Deprecated
    public String getDataTypeDefinition() {
        return dataTypeDefinition;
    }

    /**
     * Legacy getter for descriptor (maintains backward compatibility).
     * @deprecated Use descriptor() accessor instead
     */
    @Deprecated
    public String getDescriptor() {
        return descriptor;
    }

    /**
     * Creates a new YLogDataItem with updated value.
     * @param newValue the new value
     * @return a new YLogDataItem instance
     */
    public YLogDataItem withValue(String newValue) {
        return new YLogDataItem(name, newValue, dataTypeName, dataTypeDefinition, descriptor);
    }

    /**
     * Creates a new YLogDataItem with updated value from an Object.
     * @param newValue the new value object
     * @return a new YLogDataItem instance
     */
    public YLogDataItem withValue(Object newValue) {
        return withValue(String.valueOf(newValue));
    }

    /**
     * Creates a new YLogDataItem with updated name.
     * @param newName the new name
     * @return a new YLogDataItem instance
     */
    public YLogDataItem withName(String newName) {
        return new YLogDataItem(newName, value, dataTypeName, dataTypeDefinition, descriptor);
    }

    /**
     * Creates a new YLogDataItem with updated descriptor.
     * @param newDescriptor the new descriptor
     * @return a new YLogDataItem instance
     */
    public YLogDataItem withDescriptor(String newDescriptor) {
        return new YLogDataItem(name, value, dataTypeName, dataTypeDefinition, newDescriptor);
    }

    /**
     * Creates a new YLogDataItem with updated data type name.
     * @param newDataTypeName the new data type name
     * @return a new YLogDataItem instance
     */
    public YLogDataItem withDataTypeName(String newDataTypeName) {
        return new YLogDataItem(name, value, newDataTypeName, dataTypeDefinition, descriptor);
    }

    /**
     * Creates a new YLogDataItem with updated data type definition.
     * @param newDataTypeDefinition the new data type definition
     * @return a new YLogDataItem instance
     */
    public YLogDataItem withDataTypeDefinition(String newDataTypeDefinition) {
        return new YLogDataItem(name, value, dataTypeName, newDataTypeDefinition, descriptor);
    }

    /**
     * Converts to full XML representation.
     * @return XML string
     */
    public String toXML() {
        return """
            <logdataitem>%s%s%s</logdataitem>""".formatted(
                toXMLShort(),
                StringUtil.wrapEscaped(dataTypeName, "datatype"),
                StringUtil.wrapEscaped(dataTypeDefinition, "datatypedefinition")
        );
    }

    /**
     * Converts to short XML representation (without data types).
     * @return short XML string
     */
    public String toXMLShort() {
        return "%s%s%s".formatted(
            StringUtil.wrapEscaped(name, "name"),
            StringUtil.wrapEscaped(value, "value"),
            StringUtil.wrapEscaped(descriptor, "descriptor")
        );
    }
}
