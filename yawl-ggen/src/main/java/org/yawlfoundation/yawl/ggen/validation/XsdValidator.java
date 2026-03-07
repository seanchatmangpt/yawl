/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.ggen.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XSD schema validator for YAWL XML specifications.
 *
 * <p>Validates YAWL XML against the official YAWL XSD schema.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class XsdValidator {

    private static final Logger log = LoggerFactory.getLogger(XsdValidator.class);

    private static final String YAWL_XSD_PATH = "/xsd/YAWL_Schema.xsd";

    private final Validator validator;

    /**
     * Create a new XsdValidator.
     */
    public XsdValidator() {
        this.validator = createValidator();
    }

    /**
     * Validate YAWL XML against XSD schema.
     *
     * @param yawlXml YAWL XML content
     * @return XsdValidationResult with errors if any
     */
    public XsdValidationResult validate(String yawlXml) {
        if (yawlXml == null || yawlXml.isEmpty()) {
            return new XsdValidationResult(false, List.of("YAWL XML is empty"));
        }

        List<String> errors = new ArrayList<>();

        try {
            InputStream xmlStream = new ByteArrayInputStream(
                yawlXml.getBytes(StandardCharsets.UTF_8));

            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    log.warn("XSD warning at line {}: {}", e.getLineNumber(), e.getMessage());
                }

                @Override
                public void error(SAXParseException e) {
                    String msg = String.format("Line %d: %s", e.getLineNumber(), e.getMessage());
                    errors.add(msg);
                    log.error("XSD error: {}", msg);
                }

                @Override
                public void fatalError(SAXParseException e) {
                    String msg = String.format("Line %d: %s", e.getLineNumber(), e.getMessage());
                    errors.add(msg);
                    log.error("XSD fatal error: {}", msg);
                }
            });

            validator.validate(new StreamSource(xmlStream));

        } catch (SAXException e) {
            errors.add("SAX parsing error: " + e.getMessage());
            log.error("SAX exception during validation", e);
        } catch (IOException e) {
            errors.add("IO error: " + e.getMessage());
            log.error("IO exception during validation", e);
        }

        return new XsdValidationResult(errors.isEmpty(), errors);
    }

    private Validator createValidator() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            InputStream xsdStream = getClass().getResourceAsStream(YAWL_XSD_PATH);
            if (xsdStream == null) {
                log.warn("YAWL XSD not found at {}, validation disabled", YAWL_XSD_PATH);
                return null;
            }

            Schema schema = factory.newSchema(new StreamSource(xsdStream));
            return schema.newValidator();

        } catch (SAXException e) {
            log.error("Failed to load YAWL XSD schema", e);
            return null;
        }
    }

    /**
     * Result of XSD validation.
     */
    public record XsdValidationResult(boolean valid, List<String> errors) {
        public XsdValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
        }
    }
}
