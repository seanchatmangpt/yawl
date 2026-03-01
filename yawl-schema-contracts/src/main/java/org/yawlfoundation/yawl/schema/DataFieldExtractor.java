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

package org.yawlfoundation.yawl.schema;

import org.jdom2.Document;
import org.jdom2.Element;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts YAWL task data fields as a simple name-to-value string map.
 *
 * <p>YAWL task data is represented as a JDOM {@link Element} tree. Each direct child
 * element of the root data element is treated as a field whose name is the element's
 * local name and whose value is the element's text content.</p>
 *
 * <p>Nested elements (depth > 1) are not expanded — only the immediate children of the
 * root element are extracted. Complex nested structures are represented by their
 * concatenated text content.</p>
 *
 * @since 6.0.0
 */
public final class DataFieldExtractor {

    private DataFieldExtractor() {}

    /**
     * Extracts fields from a task data {@link Element}.
     *
     * @param dataElement the work item's data element (from {@code YWorkItem.getDataElement()});
     *                    may be {@code null} if the task has no data mapping
     * @return immutable map of field names to their text values; empty map if element is null
     */
    public static Map<String, String> fromElement(Element dataElement) {
        if (dataElement == null) {
            return Collections.emptyMap();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (Element child : dataElement.getChildren()) {
            fields.put(child.getName(), child.getValue());
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Extracts fields from a task output {@link Document}.
     *
     * @param outputDocument the output data document (from task completion);
     *                       may be {@code null} if the task produced no output
     * @return immutable map of field names to their text values; empty map if document is null
     */
    public static Map<String, String> fromDocument(Document outputDocument) {
        if (outputDocument == null) {
            return Collections.emptyMap();
        }
        return fromElement(outputDocument.getRootElement());
    }
}
