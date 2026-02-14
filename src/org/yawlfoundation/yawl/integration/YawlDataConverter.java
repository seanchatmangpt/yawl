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

package org.yawlfoundation.yawl.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.yawlfoundation.yawl.schema.SchemaHandler;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Data Converter for YAWL Workflow Data Structures
 *
 * Provides bidirectional conversion between XML and JSON for YAWL workflow data,
 * including complex types from order fulfillment example (PurchaseOrderType, CompanyType, etc.)
 *
 * Features:
 * - XML to JSON conversion with type preservation
 * - JSON to XML conversion with schema validation
 * - Support for nested structures and arrays
 * - Date/time format conversion (XML Schema types to ISO-8601)
 * - Currency and numeric formatting
 * - Data sanitization and validation
 * - Type coercion and transformation
 *
 * Supports YAWL complex types:
 * - PurchaseOrderType
 * - CompanyType
 * - OrderType
 * - RouteGuideType
 * - TransportationQuoteType
 * - PickupInstructionsType
 * - DeliveryInstructionsType
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlDataConverter {

    private static final Logger _log = LogManager.getLogger(YawlDataConverter.class);

    private final ObjectMapper objectMapper;
    private final Map<String, SchemaHandler> schemaCache;

    // XML Schema date/time formatters
    private static final DateTimeFormatter XS_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter XS_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;
    private static final DateTimeFormatter XS_DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Legacy date formatters for backward compatibility
    private final SimpleDateFormat xmlDateFormat;
    private final SimpleDateFormat xmlDateTimeFormat;

    // Numeric formatters with locale support
    private final Map<String, DecimalFormat> currencyFormatters;

    // XML validation patterns
    private static final Pattern XML_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_\\-\\.]*$");
    private static final Pattern SANITIZE_CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    /**
     * Construct a new YawlDataConverter with default configuration
     */
    public YawlDataConverter() {
        this.objectMapper = createObjectMapper();
        this.schemaCache = new HashMap<>();

        // XML Schema date formats
        this.xmlDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        this.xmlDateFormat.setLenient(false);

        this.xmlDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        this.xmlDateTimeFormat.setLenient(false);

        // Currency formatters
        this.currencyFormatters = createCurrencyFormatters();
    }

    /**
     * Create and configure Jackson ObjectMapper
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        return mapper;
    }

    /**
     * Create currency formatters for supported currencies
     */
    private Map<String, DecimalFormat> createCurrencyFormatters() {
        Map<String, DecimalFormat> formatters = new HashMap<>();

        DecimalFormatSymbols audSymbols = new DecimalFormatSymbols(Locale.forLanguageTag("en-AU"));
        DecimalFormat audFormatter = new DecimalFormat("#,##0.00", audSymbols);
        formatters.put("AUD", audFormatter);

        DecimalFormatSymbols usdSymbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat usdFormatter = new DecimalFormat("#,##0.00", usdSymbols);
        formatters.put("USD", usdFormatter);

        return formatters;
    }

    /**
     * Convert XML Element to JSON string
     *
     * @param xmlElement JDOM Element to convert
     * @return JSON string representation
     * @throws IOException if conversion fails
     */
    public String xmlToJson(Element xmlElement) throws IOException {
        if (xmlElement == null) {
            throw new IllegalArgumentException("XML Element cannot be null");
        }

        JsonNode jsonNode = xmlElementToJsonNode(xmlElement);
        return objectMapper.writeValueAsString(jsonNode);
    }

    /**
     * Convert XML string to JSON string
     *
     * @param xmlString XML string to convert
     * @return JSON string representation
     * @throws IOException if parsing or conversion fails
     */
    public String xmlToJson(String xmlString) throws IOException {
        if (xmlString == null || xmlString.trim().isEmpty()) {
            throw new IllegalArgumentException("XML string cannot be null or empty");
        }

        Element element = JDOMUtil.stringToElement(xmlString);
        if (element == null) {
            throw new IOException("Failed to parse XML string: " + xmlString);
        }

        return xmlToJson(element);
    }

    /**
     * Convert JSON string to XML Element
     *
     * @param jsonString JSON string to convert
     * @param rootElementName Name for the root XML element
     * @return JDOM Element
     * @throws IOException if parsing or conversion fails
     */
    public Element jsonToXml(String jsonString, String rootElementName) throws IOException {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

        if (!isValidXmlName(rootElementName)) {
            throw new IllegalArgumentException("Invalid XML element name: " + rootElementName);
        }

        JsonNode jsonNode = objectMapper.readTree(jsonString);
        return jsonNodeToXmlElement(jsonNode, rootElementName);
    }

    /**
     * Convert JSON string to XML string
     *
     * @param jsonString JSON string to convert
     * @param rootElementName Name for the root XML element
     * @return XML string representation
     * @throws IOException if parsing or conversion fails
     */
    public String jsonToXmlString(String jsonString, String rootElementName) throws IOException {
        Element element = jsonToXml(jsonString, rootElementName);
        return JDOMUtil.elementToString(element);
    }

    /**
     * Validate XML data against schema
     *
     * @param xmlData XML data to validate
     * @param schemaString XML Schema definition
     * @return true if valid, false otherwise
     */
    public boolean validateXml(String xmlData, String schemaString) {
        if (xmlData == null || xmlData.trim().isEmpty()) {
            _log.warn("Cannot validate null or empty XML data");
            return false;
        }

        if (schemaString == null || schemaString.trim().isEmpty()) {
            _log.warn("Cannot validate without schema");
            return false;
        }

        try {
            SchemaHandler handler = getOrCreateSchemaHandler(schemaString);
            return handler.validate(xmlData);
        } catch (Exception e) {
            _log.error("XML validation failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate XML Element against schema
     *
     * @param xmlElement XML element to validate
     * @param schemaString XML Schema definition
     * @return true if valid, false otherwise
     */
    public boolean validateXml(Element xmlElement, String schemaString) {
        if (xmlElement == null) {
            _log.warn("Cannot validate null XML element");
            return false;
        }

        String xmlString = JDOMUtil.elementToString(xmlElement);
        return validateXml(xmlString, schemaString);
    }

    /**
     * Get or create cached schema handler
     */
    private SchemaHandler getOrCreateSchemaHandler(String schemaString) {
        String schemaHash = String.valueOf(schemaString.hashCode());

        SchemaHandler handler = schemaCache.get(schemaHash);
        if (handler == null) {
            handler = new SchemaHandler(schemaString);
            if (!handler.compileSchema()) {
                throw new IllegalArgumentException("Failed to compile schema: " +
                    handler.getConcatenatedMessage());
            }
            schemaCache.put(schemaHash, handler);
        }

        return handler;
    }

    /**
     * Convert XML Element to JSON Node (recursive)
     */
    private JsonNode xmlElementToJsonNode(Element element) {
        if (hasOnlyTextContent(element)) {
            return convertTextToJsonValue(element.getTextTrim(), element.getName());
        }

        List<Element> children = element.getChildren();
        if (isArrayElement(children)) {
            return xmlChildrenToJsonArray(children);
        }

        ObjectNode objectNode = objectMapper.createObjectNode();

        for (Element child : children) {
            String fieldName = child.getName();
            List<Element> siblings = element.getChildren(fieldName);

            if (siblings.size() > 1) {
                if (!objectNode.has(fieldName)) {
                    ArrayNode arrayNode = objectMapper.createArrayNode();
                    for (Element sibling : siblings) {
                        arrayNode.add(xmlElementToJsonNode(sibling));
                    }
                    objectNode.set(fieldName, arrayNode);
                }
            } else {
                objectNode.set(fieldName, xmlElementToJsonNode(child));
            }
        }

        return objectNode;
    }

    /**
     * Convert XML children to JSON array
     */
    private ArrayNode xmlChildrenToJsonArray(List<Element> children) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (Element child : children) {
            arrayNode.add(xmlElementToJsonNode(child));
        }
        return arrayNode;
    }

    /**
     * Convert text content to appropriate JSON value type
     */
    private JsonNode convertTextToJsonValue(String text, String elementName) {
        if (text == null || text.isEmpty()) {
            return objectMapper.getNodeFactory().textNode("");
        }

        if (isBooleanElement(elementName) || isXmlBoolean(text)) {
            return objectMapper.getNodeFactory().booleanNode(parseBoolean(text));
        }

        if (isNumericElement(elementName) || isXmlNumeric(text)) {
            try {
                if (text.contains(".")) {
                    return objectMapper.getNodeFactory().numberNode(Double.parseDouble(text));
                } else {
                    return objectMapper.getNodeFactory().numberNode(Long.parseLong(text));
                }
            } catch (NumberFormatException e) {
                _log.debug("Not a number: " + text);
            }
        }

        return objectMapper.getNodeFactory().textNode(text);
    }

    /**
     * Convert JSON Node to XML Element (recursive)
     */
    private Element jsonNodeToXmlElement(JsonNode node, String elementName) {
        Element element = new Element(sanitizeXmlName(elementName));

        if (node.isNull()) {
            return element;
        }

        if (node.isValueNode()) {
            element.setText(formatValueForXml(node));
            return element;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode item : arrayNode) {
                Element childElement = jsonNodeToXmlElement(item, getSingularForm(elementName));
                element.addContent(childElement);
            }
            return element;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                if (fieldValue.isArray()) {
                    ArrayNode arrayNode = (ArrayNode) fieldValue;
                    for (JsonNode item : arrayNode) {
                        Element childElement = jsonNodeToXmlElement(item, fieldName);
                        element.addContent(childElement);
                    }
                } else {
                    Element childElement = jsonNodeToXmlElement(fieldValue, fieldName);
                    element.addContent(childElement);
                }
            }
        }

        return element;
    }

    /**
     * Format JSON value for XML content
     */
    private String formatValueForXml(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        }

        if (node.isNumber()) {
            if (node.isIntegralNumber()) {
                return String.valueOf(node.asLong());
            } else {
                return formatDecimal(node.asDouble());
            }
        }

        if (node.isTextual()) {
            return sanitizeXmlText(node.asText());
        }

        return node.asText();
    }

    /**
     * Map data between tasks (transform field names and values)
     *
     * @param sourceData Source data as JSON
     * @param fieldMappings Map of source field names to target field names
     * @return Mapped data as JSON
     * @throws IOException if mapping fails
     */
    public String mapData(String sourceData, Map<String, String> fieldMappings) throws IOException {
        if (sourceData == null || sourceData.trim().isEmpty()) {
            throw new IllegalArgumentException("Source data cannot be null or empty");
        }

        if (fieldMappings == null || fieldMappings.isEmpty()) {
            return sourceData;
        }

        JsonNode sourceNode = objectMapper.readTree(sourceData);
        ObjectNode targetNode = objectMapper.createObjectNode();

        mapJsonNode(sourceNode, targetNode, fieldMappings, "");

        return objectMapper.writeValueAsString(targetNode);
    }

    /**
     * Recursively map JSON nodes with field mappings
     */
    private void mapJsonNode(JsonNode source, ObjectNode target,
                             Map<String, String> mappings, String prefix) {
        if (source.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String sourceFieldName = field.getKey();
                String fullSourcePath = prefix.isEmpty() ? sourceFieldName : prefix + "." + sourceFieldName;

                String targetFieldName = mappings.getOrDefault(fullSourcePath, sourceFieldName);

                if (field.getValue().isObject()) {
                    ObjectNode nestedTarget = objectMapper.createObjectNode();
                    mapJsonNode(field.getValue(), nestedTarget, mappings, fullSourcePath);
                    target.set(targetFieldName, nestedTarget);
                } else if (field.getValue().isArray()) {
                    target.set(targetFieldName, field.getValue());
                } else {
                    target.set(targetFieldName, field.getValue());
                }
            }
        } else {
            throw new IllegalArgumentException("Source data must be a JSON object");
        }
    }

    /**
     * Type coercion: convert value to target type
     *
     * @param value Value to convert
     * @param targetType Target type (string, integer, double, boolean, date, time, dateTime)
     * @return Converted value
     */
    public Object coerceType(Object value, String targetType) {
        if (value == null) {
            return null;
        }

        String valueStr = value.toString().trim();
        if (valueStr.isEmpty()) {
            return null;
        }

        try {
            switch (targetType.toLowerCase()) {
                case "string":
                    return valueStr;

                case "integer":
                case "int":
                case "long":
                    return Long.parseLong(valueStr);

                case "double":
                case "decimal":
                case "float":
                    return Double.parseDouble(valueStr);

                case "boolean":
                case "bool":
                    return parseBoolean(valueStr);

                case "date":
                    return parseDate(valueStr);

                case "time":
                    return parseTime(valueStr);

                case "datetime":
                case "timestamp":
                    return parseDateTime(valueStr);

                default:
                    _log.warn("Unknown target type: " + targetType + ", returning as string");
                    return valueStr;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot coerce value '" + value +
                "' to type " + targetType + ": " + e.getMessage(), e);
        }
    }

    /**
     * Format currency value
     *
     * @param amount Numeric amount
     * @param currencyCode Currency code (AUD, USD, etc.)
     * @return Formatted currency string
     */
    public String formatCurrency(double amount, String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }

        String code = currencyCode.toUpperCase();
        DecimalFormat formatter = currencyFormatters.get(code);

        if (formatter == null) {
            _log.warn("No formatter for currency code: " + code + ", using default");
            formatter = new DecimalFormat("#,##0.00");
        }

        return formatter.format(amount);
    }

    /**
     * Parse currency value
     *
     * @param formattedAmount Formatted currency string
     * @param currencyCode Currency code (AUD, USD, etc.)
     * @return Numeric amount
     */
    public double parseCurrency(String formattedAmount, String currencyCode) throws ParseException {
        if (formattedAmount == null || formattedAmount.trim().isEmpty()) {
            throw new IllegalArgumentException("Formatted amount cannot be null or empty");
        }

        String code = currencyCode != null ? currencyCode.toUpperCase() : "USD";
        DecimalFormat formatter = currencyFormatters.get(code);

        if (formatter == null) {
            formatter = new DecimalFormat("#,##0.00");
        }

        String cleanAmount = formattedAmount.replaceAll("[^0-9.,\\-]", "");
        Number number = formatter.parse(cleanAmount);
        return number.doubleValue();
    }

    /**
     * Format date for XML (ISO-8601)
     */
    public String formatDateForXml(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return date.format(XS_DATE_FORMATTER);
    }

    /**
     * Format time for XML (ISO-8601)
     */
    public String formatTimeForXml(LocalTime time) {
        if (time == null) {
            throw new IllegalArgumentException("Time cannot be null");
        }
        return time.format(XS_TIME_FORMATTER);
    }

    /**
     * Format datetime for XML (ISO-8601)
     */
    public String formatDateTimeForXml(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("DateTime cannot be null");
        }
        return dateTime.format(XS_DATETIME_FORMATTER);
    }

    /**
     * Parse XML date (xs:date)
     */
    public LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        try {
            return LocalDate.parse(dateStr, XS_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + dateStr +
                " (expected: yyyy-MM-dd)", e);
        }
    }

    /**
     * Parse XML time (xs:time)
     */
    public LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }

        try {
            return LocalTime.parse(timeStr, XS_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format: " + timeStr +
                " (expected: HH:mm:ss)", e);
        }
    }

    /**
     * Parse XML datetime (xs:dateTime)
     */
    public LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("DateTime string cannot be null or empty");
        }

        try {
            return LocalDateTime.parse(dateTimeStr, XS_DATETIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid datetime format: " + dateTimeStr +
                " (expected: yyyy-MM-dd'T'HH:mm:ss)", e);
        }
    }

    /**
     * Sanitize XML text (remove control characters, encode entities)
     */
    public String sanitizeXmlText(String text) {
        if (text == null) {
            return null;
        }

        String sanitized = SANITIZE_CONTROL_CHARS.matcher(text).replaceAll("");
        return JDOMUtil.encodeEscapes(sanitized);
    }

    /**
     * Sanitize XML element name
     */
    public String sanitizeXmlName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("XML name cannot be null or empty");
        }

        String sanitized = name.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");

        if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
            sanitized = "_" + sanitized;
        }

        return sanitized;
    }

    /**
     * Check if string is a valid XML name
     */
    private boolean isValidXmlName(String name) {
        return name != null && XML_NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Check if element has only text content (no child elements)
     */
    private boolean hasOnlyTextContent(Element element) {
        return element.getChildren().isEmpty();
    }

    /**
     * Check if children represent an array (all have same name)
     */
    private boolean isArrayElement(List<Element> children) {
        if (children.isEmpty()) {
            return false;
        }

        String firstName = children.get(0).getName();
        for (int i = 1; i < children.size(); i++) {
            if (!children.get(i).getName().equals(firstName)) {
                return false;
            }
        }

        return children.size() > 1;
    }

    /**
     * Check if element name suggests boolean type
     */
    private boolean isBooleanElement(String name) {
        String lower = name.toLowerCase();
        return lower.startsWith("is") || lower.endsWith("ed") ||
               lower.equals("approved") || lower.equals("prepaid") ||
               lower.equals("invoicerequired") || lower.equals("truckload");
    }

    /**
     * Check if element name suggests numeric type
     */
    private boolean isNumericElement(String name) {
        String lower = name.toLowerCase();
        return lower.contains("cost") || lower.contains("price") ||
               lower.contains("amount") || lower.contains("quantity") ||
               lower.contains("number") || lower.contains("volume");
    }

    /**
     * Check if text represents XML boolean
     */
    private boolean isXmlBoolean(String text) {
        return "true".equals(text) || "false".equals(text) ||
               "1".equals(text) || "0".equals(text);
    }

    /**
     * Check if text is numeric
     */
    private boolean isXmlNumeric(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Parse boolean from various string formats
     */
    private boolean parseBoolean(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String lower = text.toLowerCase().trim();
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
    }

    /**
     * Format decimal number
     */
    private String formatDecimal(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }

        BigDecimal bd = new BigDecimal(value);
        bd = bd.stripTrailingZeros();
        return bd.toPlainString();
    }

    /**
     * Get singular form of element name (simple heuristic)
     */
    private String getSingularForm(String name) {
        if (name.endsWith("ies")) {
            return name.substring(0, name.length() - 3) + "y";
        } else if (name.endsWith("ses") || name.endsWith("xes") || name.endsWith("zes")) {
            return name.substring(0, name.length() - 2);
        } else if (name.endsWith("s") && !name.endsWith("ss")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    /**
     * Clear schema cache
     */
    public void clearSchemaCache() {
        schemaCache.clear();
        _log.info("Schema cache cleared");
    }

    /**
     * Get number of cached schemas
     */
    public int getCachedSchemaCount() {
        return schemaCache.size();
    }
}
