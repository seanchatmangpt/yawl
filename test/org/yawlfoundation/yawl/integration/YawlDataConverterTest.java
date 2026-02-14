package org.yawlfoundation.yawl.integration;

import org.jdom2.Element;
import org.junit.Before;
import org.junit.Test;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test suite for YawlDataConverter
 *
 * Tests XML to JSON and JSON to XML conversions for YAWL workflow data structures,
 * including complex types from order fulfillment example.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlDataConverterTest {

    private YawlDataConverter converter;

    @Before
    public void setUp() {
        converter = new YawlDataConverter();
    }

    @Test
    public void testSimpleXmlToJson() throws Exception {
        String xml = "<OrderNumber>PO-12345</OrderNumber>";
        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain order number", json.contains("PO-12345"));
    }

    @Test
    public void testSimpleJsonToXml() throws Exception {
        String json = "{\"OrderNumber\":\"PO-12345\"}";
        Element element = converter.jsonToXml(json, "Order");

        assertNotNull("Element should not be null", element);
        assertEquals("Order", element.getName());
        assertEquals("PO-12345", element.getChildText("OrderNumber"));
    }

    @Test
    public void testCompanyTypeXmlToJson() throws Exception {
        String xml = "<Company>" +
                "<Name>Acme Corporation</Name>" +
                "<Address>123 Main Street</Address>" +
                "<City>Brisbane</City>" +
                "<State>QLD</State>" +
                "<PostCode>4000</PostCode>" +
                "<Phone>+61-7-1234-5678</Phone>" +
                "<Fax>+61-7-1234-5679</Fax>" +
                "<BusinessNumber>ABN-12345678</BusinessNumber>" +
                "</Company>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain company name", json.contains("Acme Corporation"));
        assertTrue("JSON should contain address", json.contains("123 Main Street"));
        assertTrue("JSON should contain city", json.contains("Brisbane"));
        assertTrue("JSON should contain business number", json.contains("ABN-12345678"));
    }

    @Test
    public void testCompanyTypeJsonToXml() throws Exception {
        String json = "{" +
                "\"Name\":\"Acme Corporation\"," +
                "\"Address\":\"123 Main Street\"," +
                "\"City\":\"Brisbane\"," +
                "\"State\":\"QLD\"," +
                "\"PostCode\":\"4000\"," +
                "\"Phone\":\"+61-7-1234-5678\"," +
                "\"Fax\":\"+61-7-1234-5679\"," +
                "\"BusinessNumber\":\"ABN-12345678\"" +
                "}";

        Element element = converter.jsonToXml(json, "Company");

        assertNotNull("Element should not be null", element);
        assertEquals("Company", element.getName());
        assertEquals("Acme Corporation", element.getChildText("Name"));
        assertEquals("123 Main Street", element.getChildText("Address"));
        assertEquals("Brisbane", element.getChildText("City"));
        assertEquals("QLD", element.getChildText("State"));
        assertEquals("4000", element.getChildText("PostCode"));
    }

    @Test
    public void testTransportationQuoteTypeXmlToJson() throws Exception {
        String xml = "<TransportationQuote>" +
                "<OrderNumber>PO-12345</OrderNumber>" +
                "<NumberOfPackages>5</NumberOfPackages>" +
                "<TotalVolume>500</TotalVolume>" +
                "<ShipmentNumber>SH-98765</ShipmentNumber>" +
                "<ShipmentCost>1250.50</ShipmentCost>" +
                "</TransportationQuote>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain order number", json.contains("PO-12345"));
        assertTrue("JSON should contain package count", json.contains("5"));
        assertTrue("JSON should contain cost", json.contains("1250.5"));
    }

    @Test
    public void testBooleanConversion() throws Exception {
        String xml = "<PurchaseOrder>" +
                "<InvoiceRequired>true</InvoiceRequired>" +
                "<PrePaid>false</PrePaid>" +
                "</PurchaseOrder>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain true boolean", json.contains("true"));
        assertTrue("JSON should contain false boolean", json.contains("false"));

        String jsonBack = "{\"InvoiceRequired\":true,\"PrePaid\":false}";
        Element element = converter.jsonToXml(jsonBack, "PurchaseOrder");

        assertEquals("true", element.getChildText("InvoiceRequired"));
        assertEquals("false", element.getChildText("PrePaid"));
    }

    @Test
    public void testNumericConversion() throws Exception {
        String xml = "<Order>" +
                "<FreightCost>1234.56</FreightCost>" +
                "<RevisionNumber>0</RevisionNumber>" +
                "</Order>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain freight cost", json.contains("1234.56"));
        assertTrue("JSON should contain revision number", json.contains("0"));
    }

    @Test
    public void testArrayConversionXmlToJson() throws Exception {
        String xml = "<OrderLines>" +
                "<Line><LineNumber>1</LineNumber><UnitCode>WIDGET-A</UnitCode></Line>" +
                "<Line><LineNumber>2</LineNumber><UnitCode>WIDGET-B</UnitCode></Line>" +
                "<Line><LineNumber>3</LineNumber><UnitCode>WIDGET-C</UnitCode></Line>" +
                "</OrderLines>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain array structure", json.contains("["));
        assertTrue("JSON should contain WIDGET-A", json.contains("WIDGET-A"));
        assertTrue("JSON should contain WIDGET-B", json.contains("WIDGET-B"));
        assertTrue("JSON should contain WIDGET-C", json.contains("WIDGET-C"));
    }

    @Test
    public void testArrayConversionJsonToXml() throws Exception {
        String json = "{\"Line\":[" +
                "{\"LineNumber\":1,\"UnitCode\":\"WIDGET-A\"}," +
                "{\"LineNumber\":2,\"UnitCode\":\"WIDGET-B\"}," +
                "{\"LineNumber\":3,\"UnitCode\":\"WIDGET-C\"}" +
                "]}";

        Element element = converter.jsonToXml(json, "OrderLines");

        assertNotNull("Element should not be null", element);
        assertEquals("OrderLines", element.getName());
        assertEquals(3, element.getChildren("Line").size());
    }

    @Test
    public void testNestedStructures() throws Exception {
        String xml = "<PurchaseOrder>" +
                "<Company>" +
                "  <Name>Acme Corp</Name>" +
                "  <City>Brisbane</City>" +
                "</Company>" +
                "<Order>" +
                "  <OrderNumber>PO-123</OrderNumber>" +
                "  <OrderDate>2024-01-15</OrderDate>" +
                "</Order>" +
                "<FreightCost>500.00</FreightCost>" +
                "</PurchaseOrder>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain Acme Corp", json.contains("Acme Corp"));
        assertTrue("JSON should contain Brisbane", json.contains("Brisbane"));
        assertTrue("JSON should contain PO-123", json.contains("PO-123"));
        assertTrue("JSON should contain date", json.contains("2024-01-15"));
    }

    @Test
    public void testDataMapping() throws Exception {
        String sourceJson = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"emailAddress\":\"john@example.com\"}";

        Map<String, String> mappings = new HashMap<>();
        mappings.put("firstName", "givenName");
        mappings.put("lastName", "familyName");
        mappings.put("emailAddress", "email");

        String mappedJson = converter.mapData(sourceJson, mappings);

        assertNotNull("Mapped JSON should not be null", mappedJson);
        assertTrue("Should contain givenName", mappedJson.contains("givenName"));
        assertTrue("Should contain familyName", mappedJson.contains("familyName"));
        assertTrue("Should contain email", mappedJson.contains("email"));
        assertFalse("Should not contain firstName", mappedJson.contains("firstName"));
    }

    @Test
    public void testTypeCoercion() {
        assertEquals("String coercion", "12345", converter.coerceType("12345", "string"));
        assertEquals("Integer coercion", 12345L, converter.coerceType("12345", "integer"));
        assertEquals("Double coercion", 123.45, (Double) converter.coerceType("123.45", "double"), 0.001);
        assertEquals("Boolean coercion true", true, converter.coerceType("true", "boolean"));
        assertEquals("Boolean coercion false", false, converter.coerceType("false", "boolean"));
    }

    @Test
    public void testCurrencyFormatting() {
        String formatted = converter.formatCurrency(1234.56, "AUD");
        assertNotNull("Formatted currency should not be null", formatted);
        assertTrue("Should contain formatted number", formatted.contains("1,234.56") || formatted.contains("1234.56"));
    }

    @Test
    public void testCurrencyParsing() throws Exception {
        double parsed = converter.parseCurrency("1,234.56", "USD");
        assertEquals("Should parse currency correctly", 1234.56, parsed, 0.001);
    }

    @Test
    public void testDateFormatting() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        String formatted = converter.formatDateForXml(date);
        assertEquals("2024-01-15", formatted);
    }

    @Test
    public void testTimeFormatting() {
        LocalTime time = LocalTime.of(14, 30, 0);
        String formatted = converter.formatTimeForXml(time);
        assertEquals("14:30:00", formatted);
    }

    @Test
    public void testDateTimeFormatting() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 30, 0);
        String formatted = converter.formatDateTimeForXml(dateTime);
        assertEquals("2024-01-15T14:30:00", formatted);
    }

    @Test
    public void testDateParsing() {
        LocalDate date = converter.parseDate("2024-01-15");
        assertEquals(2024, date.getYear());
        assertEquals(1, date.getMonthValue());
        assertEquals(15, date.getDayOfMonth());
    }

    @Test
    public void testTimeParsing() {
        LocalTime time = converter.parseTime("14:30:00");
        assertEquals(14, time.getHour());
        assertEquals(30, time.getMinute());
        assertEquals(0, time.getSecond());
    }

    @Test
    public void testDateTimeParsing() {
        LocalDateTime dateTime = converter.parseDateTime("2024-01-15T14:30:00");
        assertEquals(2024, dateTime.getYear());
        assertEquals(1, dateTime.getMonthValue());
        assertEquals(15, dateTime.getDayOfMonth());
        assertEquals(14, dateTime.getHour());
        assertEquals(30, dateTime.getMinute());
    }

    @Test
    public void testXmlTextSanitization() {
        String dirty = "Hello\u0000World\u0001Test";
        String clean = converter.sanitizeXmlText(dirty);
        assertNotNull("Sanitized text should not be null", clean);
        assertFalse("Should not contain control characters", clean.contains("\u0000"));
    }

    @Test
    public void testXmlNameSanitization() {
        assertEquals("_123Test", converter.sanitizeXmlName("123Test"));
        assertEquals("Test_Name", converter.sanitizeXmlName("Test Name"));
        assertEquals("Valid_Name", converter.sanitizeXmlName("Valid-Name"));
    }

    @Test
    public void testPickupInstructionsType() throws Exception {
        String xml = "<PickupInstructions>" +
                "<ShipmentNumber>SH-12345</ShipmentNumber>" +
                "<PickupDate>2024-02-01</PickupDate>" +
                "<PickupInstructions>Use loading dock B</PickupInstructions>" +
                "<PickupSpot>Warehouse 3</PickupSpot>" +
                "</PickupInstructions>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("Should contain shipment number", json.contains("SH-12345"));
        assertTrue("Should contain pickup date", json.contains("2024-02-01"));
        assertTrue("Should contain instructions", json.contains("Use loading dock B"));

        Element element = converter.jsonToXml(json, "PickupInstructions");
        assertEquals("SH-12345", element.getChildText("ShipmentNumber"));
        assertEquals("2024-02-01", element.getChildText("PickupDate"));
    }

    @Test
    public void testDeliveryInstructionsType() throws Exception {
        String xml = "<DeliveryInstructions>" +
                "<ShipmentNumber>SH-12345</ShipmentNumber>" +
                "<DeliveryDate>2024-02-05</DeliveryDate>" +
                "<DeliveryInstructions>Ring doorbell twice</DeliveryInstructions>" +
                "<DeliveryLocation>123 Main St, Brisbane</DeliveryLocation>" +
                "</DeliveryInstructions>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("Should contain shipment number", json.contains("SH-12345"));
        assertTrue("Should contain delivery date", json.contains("2024-02-05"));

        Element element = converter.jsonToXml(json, "DeliveryInstructions");
        assertEquals("SH-12345", element.getChildText("ShipmentNumber"));
        assertEquals("2024-02-05", element.getChildText("DeliveryDate"));
    }

    @Test
    public void testRouteGuideType() throws Exception {
        String xml = "<RouteGuide>" +
                "<OrderNumber>PO-12345</OrderNumber>" +
                "<DeliveryLocation>Brisbane Depot</DeliveryLocation>" +
                "<Trackpoints>" +
                "  <Trackpoint>Sydney</Trackpoint>" +
                "  <Trackpoint>Newcastle</Trackpoint>" +
                "  <Trackpoint>Brisbane</Trackpoint>" +
                "</Trackpoints>" +
                "</RouteGuide>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("Should contain order number", json.contains("PO-12345"));
        assertTrue("Should contain trackpoints", json.contains("Sydney"));
        assertTrue("Should contain trackpoints", json.contains("Newcastle"));
        assertTrue("Should contain trackpoints", json.contains("Brisbane"));
    }

    @Test
    public void testComplexPurchaseOrder() throws Exception {
        String xml = "<PurchaseOrder>" +
                "<Company>" +
                "  <Name>Tech Solutions Pty Ltd</Name>" +
                "  <Address>456 Innovation Way</Address>" +
                "  <City>Sydney</City>" +
                "  <State>NSW</State>" +
                "  <PostCode>2000</PostCode>" +
                "  <Phone>+61-2-9876-5432</Phone>" +
                "  <Fax>+61-2-9876-5433</Fax>" +
                "  <BusinessNumber>ABN-98765432</BusinessNumber>" +
                "</Company>" +
                "<Order>" +
                "  <OrderNumber>PO-2024-001</OrderNumber>" +
                "  <OrderDate>2024-01-15</OrderDate>" +
                "  <Currency>AUD</Currency>" +
                "  <OrderTerms>Net 30</OrderTerms>" +
                "  <RevisionNumber>0</RevisionNumber>" +
                "  <Remarks>Urgent delivery required</Remarks>" +
                "</Order>" +
                "<FreightCost>250.75</FreightCost>" +
                "<DeliveryLocation>Sydney CBD</DeliveryLocation>" +
                "<InvoiceRequired>true</InvoiceRequired>" +
                "<PrePaid>false</PrePaid>" +
                "</PurchaseOrder>";

        String json = converter.xmlToJson(xml);

        assertNotNull("JSON should not be null", json);
        assertTrue("Should contain company name", json.contains("Tech Solutions Pty Ltd"));
        assertTrue("Should contain order number", json.contains("PO-2024-001"));
        assertTrue("Should contain currency", json.contains("AUD"));
        assertTrue("Should contain freight cost", json.contains("250.75"));

        Element element = converter.jsonToXml(json, "PurchaseOrder");
        assertNotNull("Element should not be null", element);
        assertEquals("PurchaseOrder", element.getName());

        Element company = element.getChild("Company");
        assertNotNull("Company element should exist", company);
        assertEquals("Tech Solutions Pty Ltd", company.getChildText("Name"));

        Element order = element.getChild("Order");
        assertNotNull("Order element should exist", order);
        assertEquals("PO-2024-001", order.getChildText("OrderNumber"));
    }

    @Test
    public void testRoundTripConversion() throws Exception {
        String originalXml = "<Order>" +
                "<OrderNumber>PO-12345</OrderNumber>" +
                "<OrderDate>2024-01-15</OrderDate>" +
                "<FreightCost>100.50</FreightCost>" +
                "<InvoiceRequired>true</InvoiceRequired>" +
                "</Order>";

        String json = converter.xmlToJson(originalXml);
        Element element = converter.jsonToXml(json, "Order");
        String finalXml = JDOMUtil.elementToString(element);

        assertNotNull("Final XML should not be null", finalXml);
        assertTrue("Should contain order number", finalXml.contains("PO-12345"));
        assertTrue("Should contain order date", finalXml.contains("2024-01-15"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullXmlToJson() throws Exception {
        converter.xmlToJson((Element) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullJsonToXml() throws Exception {
        converter.jsonToXml(null, "Root");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidXmlElementName() throws Exception {
        converter.jsonToXml("{\"test\":\"value\"}", "123Invalid");
    }

    @Test
    public void testSchemaCacheManagement() {
        int initialCount = converter.getCachedSchemaCount();
        assertEquals("Initial cache should be empty", 0, initialCount);

        converter.clearSchemaCache();
        assertEquals("Cache should be empty after clear", 0, converter.getCachedSchemaCount());
    }
}
