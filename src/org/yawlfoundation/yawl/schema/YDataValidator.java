/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

import org.jdom2.Element;
import org.w3c.dom.Document;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.exceptions.YDataValidationException;
import org.yawlfoundation.yawl.schema.internal.YInternalType;
import org.yawlfoundation.yawl.util.DOMUtil;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.SAXException;

/**
 * This class serves as a validation mechanism for the specification specific
 * schema and the instance data from either the net or a task. This is performed
 * by taking the data available at the various validation points and converting
 * it into conventional XML which is then validated using a SchemaHandler.
 *
 * @author Mike Fowler
 *         Date: 05-Jul-2006
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class YDataValidator {

    private static final Logger LOGGER = Logger.getLogger(YDataValidator.class.getName());

    // Object that performs the real validation on XML documents
    private SchemaHandler handler;

    /**
     * Constructs a new validator and handler. The
     * handler is not ready for use until validateSchema
     * has been called.
     * @param schema a W3C XML Schema
     */
    public YDataValidator(String schema) {
        this.handler = new SchemaHandler(schema);
    }

    /**
     * Compiles and determines the validity of the current schema
     * @return true if the schema compiled without error.
     */
    public boolean validateSchema() {
        return handler.compileSchema();
    }

    /**
     * Validates a single data variable
     *
     * @param variable to be validated
     * @param data XML representation of variable to be validated
     * @param source
     * @throws YDataValidationException if the data is not valid
     */
    public void validate(YVariable variable, Element data, String source)
            throws YDataValidationException {
        List<YVariable> vars = new ArrayList<YVariable>(1);
        vars.add(variable);
        validate(vars, data, source);
    }

    /**
     * Validates a collection of variables against the schema. This is achieved by
     * temporarily adding a schema element declaration for the data. This avoids
     * attempting to create a new schema containing only the relevant data types.
     *
     * @param vars variables to be validated
     * @param data XML representation fo the variables to be validated
     * @param source
     * @throws YDataValidationException if the data is not valid
     */
    public void validate(Collection vars, Element data, String source)
            throws YDataValidationException {
        try {
            String schema = ensurePrefixedSchema(handler.getSchema());
            Document xsd = createSecureDocument(schema);
            String ns = XMLConstants.W3C_XML_SCHEMA_NS_URI;

            //need to determine the prefix for the schema namespace
            String prefix = ensureValidPrefix(xsd.lookupPrefix(ns));
            org.w3c.dom.Element element = xsd.createElementNS(ns, prefix + "element");
            element.setAttribute("name", data.getName());
            org.w3c.dom.Element complex = xsd.createElementNS(ns, prefix + "complexType");
            org.w3c.dom.Element sequence = xsd.createElementNS(ns, prefix + "sequence");
            ArrayList varList = new ArrayList(vars);
            Collections.sort(varList);               // sort on YParameter ordering value
            for (Object obj : varList) {
                YVariable var = (YVariable) obj;
                org.w3c.dom.Element child = xsd.createElementNS(ns, prefix + "element");
                child.setAttribute("name", var.getName());
                String type = var.getDataTypeName();
                if (XSDType.isBuiltInType(type)) {
                    type = prefix + type;
                }
                else if (YInternalType.isType(type)) {
                    type = prefix + type;
                    xsd.getDocumentElement().appendChild(createSecureDocument(
                            YInternalType.valueOf(type).getSchemaString()).getDocumentElement());
                }
                child.setAttribute("type", type);
                if (var.isOptional()) {
                    child.setAttribute("minOccurs", "0");
                }

                sequence.appendChild(child);
            }

            complex.appendChild(sequence);
            element.appendChild(complex);
            xsd.getDocumentElement().appendChild(element);
            SchemaHandler handler =
                          new SchemaHandler(DOMUtil.getXMLStringFragmentFromNode(xsd));
            if (! handler.compileSchema()) {
                throw new YDataValidationException(
                    handler.getSchema(),
                    data,
                    handler.getConcatenatedMessage(),
                    source,
                    "Problem with process model.  Failed to compile schema");
            }

            if (! handler.validate(JDOMUtil.elementToString(data))) {
                throw new YDataValidationException(
                    handler.getSchema(),
                    data,
                    handler.getConcatenatedMessage(),
                    source,
                    "Problem with process model.  Schema validation failed");
            }
        }
        catch (YDataValidationException e) {
            throw e;
        }
        catch (ParserConfigurationException e) {
            LOGGER.log(Level.SEVERE, "XML parser configuration error during validation", e);
            throw new YDataValidationException(
                handler.getSchema(),
                data,
                e.getMessage(),
                source,
                "Problem with process model. XML parser configuration error");
        }
        catch (SAXException e) {
            LOGGER.log(Level.SEVERE, "XML parsing error during validation", e);
            throw new YDataValidationException(
                handler.getSchema(),
                data,
                e.getMessage(),
                source,
                "Problem with process model. XML parsing error");
        }
        catch (IOException e) {
            LOGGER.log(Level.SEVERE, "I/O error during validation", e);
            throw new YDataValidationException(
                handler.getSchema(),
                data,
                e.getMessage(),
                source,
                "Problem with process model. I/O error during validation");
        }
        catch (TransformerException e) {
            LOGGER.log(Level.SEVERE, "XML transformation error during validation", e);
            throw new YDataValidationException(
                handler.getSchema(),
                data,
                e.getMessage(),
                source,
                "Problem with process model. XML transformation error");
        }
    }

    /**
     * Creates a secure, namespace-aware Document from an XML string.
     * This method configures the DocumentBuilderFactory with security features
     * to prevent XXE (XML External Entity) attacks as required for Java 25.
     *
     * @param xml the XML string to parse
     * @return a secure Document representation of the XML
     * @throws ParserConfigurationException if the parser cannot be configured
     * @throws SAXException if the XML cannot be parsed
     * @throws IOException if an I/O error occurs
     */
    private Document createSecureDocument(String xml)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // Enable secure processing features for Java 25 security compliance
        enableSecureProcessing(factory);

        // Disallow DOCTYPE declarations to prevent XXE attacks
        disallowDoctypeDeclarations(factory);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Enables secure processing on the DocumentBuilderFactory to prevent
     * denial-of-service attacks and other security vulnerabilities.
     *
     * @param factory the DocumentBuilderFactory to configure
     * @throws ParserConfigurationException if the feature cannot be set
     */
    private void enableSecureProcessing(DocumentBuilderFactory factory)
            throws ParserConfigurationException {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.WARNING,
                "FEATURE_SECURE_PROCESSING not supported by DocumentBuilderFactory", e);
            throw e;
        }
    }

    /**
     * Disallows DOCTYPE declarations to prevent XXE (XML External Entity) attacks.
     *
     * @param factory the DocumentBuilderFactory to configure
     * @throws ParserConfigurationException if the feature cannot be set
     */
    private void disallowDoctypeDeclarations(DocumentBuilderFactory factory)
            throws ParserConfigurationException {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException e) {
            // Some parsers may not support this feature, log and continue
            LOGGER.log(Level.FINE,
                "http://apache.org/xml/features/disallow-doctype-decl not supported", e);
            // Try alternative approach for parsers without this feature
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
        }
    }

    /**
     * @return String representation of the schema
     */
    public String getSchema() {
        return handler.getSchema();
    }

    /**
     * @return the SchemaHandler for this validator
     */
    public SchemaHandler getSchemaHandler() { return handler; }

    /**
     * @return All error/warning messages relating to the last validation/compilation
     */
    public List<String> getMessages() {
        return handler.getMessages();
    }

    /**
     * @return the set of (first-level) type names defined in this schema
     */
    public Set<String> getPrimaryTypeNames() {
        return handler.getPrimaryTypeNames();
    }

    /**
     * Utility method to ensure the prefix is valid (enforces : and
     * defaults to xs:)
     *
     * @param prefix to validate
     * @return validated prefix
     */
    private String ensureValidPrefix(String prefix) {
        if (StringUtil.isNullOrEmpty(prefix)) {
            return "xs:";
        }
        else if (! prefix.endsWith(":")) {
            return prefix + ":";
        }
        return prefix;
    }

    /**
     * A schema may not have a valid prefix if a spec contains no complex types, so
     * this makes sure it gets one in that case
     * @param schema the schema string to check
     * @return a correctly (or defaultly) prefixed schema string
     */
    private String ensurePrefixedSchema(String schema) {
        if (!schema.contains(":schema")) {
            schema = schema.replaceFirst("schema xmlns", "schema xmlns:xs");
            schema = schema.replaceAll("<", "<xs:")
                           .replaceAll("<xs:/", "</xs:")
                           .replaceAll("type=\"", "type=\"xs:");
        }
        return schema ;
    }
}
