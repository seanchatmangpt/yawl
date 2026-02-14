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

import org.jdom2.Element;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Example usage of YawlDataConverter for YAWL workflow data transformations
 *
 * This class demonstrates real-world usage patterns for converting between
 * XML and JSON formats in YAWL workflows, including the complex types from
 * the order fulfillment example specification.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlDataConverterExample {

    /**
     * Example 1: Convert Company data from XML to JSON
     */
    public static void convertCompanyToJson() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String companyXml = "<Company>" +
                "<Name>Acme Corporation</Name>" +
                "<Address>123 Main Street</Address>" +
                "<City>Brisbane</City>" +
                "<State>QLD</State>" +
                "<PostCode>4000</PostCode>" +
                "<Phone>+61-7-1234-5678</Phone>" +
                "<Fax>+61-7-1234-5679</Fax>" +
                "<BusinessNumber>ABN-12345678</BusinessNumber>" +
                "</Company>";

        String json = converter.xmlToJson(companyXml);
        System.out.println("Company as JSON: " + json);
    }

    /**
     * Example 2: Convert Purchase Order from JSON to XML
     */
    public static void convertPurchaseOrderToXml() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String purchaseOrderJson = "{" +
                "\"Company\":{" +
                "  \"Name\":\"Tech Solutions Pty Ltd\"," +
                "  \"Address\":\"456 Innovation Way\"," +
                "  \"City\":\"Sydney\"," +
                "  \"State\":\"NSW\"," +
                "  \"PostCode\":\"2000\"," +
                "  \"Phone\":\"+61-2-9876-5432\"," +
                "  \"Fax\":\"+61-2-9876-5433\"," +
                "  \"BusinessNumber\":\"ABN-98765432\"" +
                "}," +
                "\"Order\":{" +
                "  \"OrderNumber\":\"PO-2024-001\"," +
                "  \"OrderDate\":\"2024-01-15\"," +
                "  \"Currency\":\"AUD\"," +
                "  \"OrderTerms\":\"Net 30\"," +
                "  \"RevisionNumber\":0," +
                "  \"Remarks\":\"Urgent delivery\"" +
                "}," +
                "\"FreightCost\":250.75," +
                "\"DeliveryLocation\":\"Sydney CBD\"," +
                "\"InvoiceRequired\":true," +
                "\"PrePaid\":false" +
                "}";

        Element xmlElement = converter.jsonToXml(purchaseOrderJson, "PurchaseOrder");
        System.out.println("Purchase Order as XML: " + xmlElement);
    }

    /**
     * Example 3: Convert Transportation Quote with numeric and string data
     */
    public static void convertTransportationQuote() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String quoteXml = "<TransportationQuote>" +
                "<OrderNumber>PO-12345</OrderNumber>" +
                "<NumberOfPackages>5</NumberOfPackages>" +
                "<TotalVolume>500</TotalVolume>" +
                "<ShipmentNumber>SH-98765</ShipmentNumber>" +
                "<ShipmentCost>1250.50</ShipmentCost>" +
                "</TransportationQuote>";

        String json = converter.xmlToJson(quoteXml);
        System.out.println("Transportation Quote as JSON: " + json);

        Element reconstructed = converter.jsonToXml(json, "TransportationQuote");
        System.out.println("Reconstructed XML: " + reconstructed);
    }

    /**
     * Example 4: Map data between different task formats
     */
    public static void mapTaskData() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String sourceJson = "{" +
                "\"customerFirstName\":\"John\"," +
                "\"customerLastName\":\"Doe\"," +
                "\"emailAddr\":\"john.doe@example.com\"," +
                "\"phoneNum\":\"+61-7-1111-2222\"" +
                "}";

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("customerFirstName", "firstName");
        fieldMappings.put("customerLastName", "lastName");
        fieldMappings.put("emailAddr", "email");
        fieldMappings.put("phoneNum", "phone");

        String mappedJson = converter.mapData(sourceJson, fieldMappings);
        System.out.println("Mapped data: " + mappedJson);
    }

    /**
     * Example 5: Handle array data (Order Lines)
     */
    public static void handleOrderLines() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String orderLinesXml = "<OrderLines>" +
                "<Line>" +
                "  <LineNumber>1</LineNumber>" +
                "  <UnitCode>WIDGET-A</UnitCode>" +
                "  <UnitDescription>Premium Widget Type A</UnitDescription>" +
                "  <UnitQuantity>10</UnitQuantity>" +
                "  <Action>Added</Action>" +
                "</Line>" +
                "<Line>" +
                "  <LineNumber>2</LineNumber>" +
                "  <UnitCode>WIDGET-B</UnitCode>" +
                "  <UnitDescription>Standard Widget Type B</UnitDescription>" +
                "  <UnitQuantity>25</UnitQuantity>" +
                "  <Action>Modified</Action>" +
                "</Line>" +
                "<Line>" +
                "  <LineNumber>3</LineNumber>" +
                "  <UnitCode>WIDGET-C</UnitCode>" +
                "  <UnitDescription>Budget Widget Type C</UnitDescription>" +
                "  <UnitQuantity>50</UnitQuantity>" +
                "  <Action></Action>" +
                "</Line>" +
                "</OrderLines>";

        String json = converter.xmlToJson(orderLinesXml);
        System.out.println("Order Lines as JSON array: " + json);
    }

    /**
     * Example 6: Type coercion for data transformation
     */
    public static void demonstrateTypeCoercion() {
        YawlDataConverter converter = new YawlDataConverter();

        Object stringValue = converter.coerceType("12345", "string");
        System.out.println("String value: " + stringValue);

        Object integerValue = converter.coerceType("12345", "integer");
        System.out.println("Integer value: " + integerValue);

        Object doubleValue = converter.coerceType("123.45", "double");
        System.out.println("Double value: " + doubleValue);

        Object booleanValue = converter.coerceType("true", "boolean");
        System.out.println("Boolean value: " + booleanValue);

        Object dateValue = converter.coerceType("2024-01-15", "date");
        System.out.println("Date value: " + dateValue);
    }

    /**
     * Example 7: Currency formatting and parsing
     */
    public static void handleCurrencyData() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        double freightCost = 1234.56;
        String formattedAUD = converter.formatCurrency(freightCost, "AUD");
        String formattedUSD = converter.formatCurrency(freightCost, "USD");

        System.out.println("Freight cost in AUD: " + formattedAUD);
        System.out.println("Freight cost in USD: " + formattedUSD);

        double parsedAmount = converter.parseCurrency("1,234.56", "AUD");
        System.out.println("Parsed amount: " + parsedAmount);
    }

    /**
     * Example 8: Date and time formatting
     */
    public static void handleDateTimeData() {
        YawlDataConverter converter = new YawlDataConverter();

        LocalDate orderDate = LocalDate.of(2024, 1, 15);
        String formattedDate = converter.formatDateForXml(orderDate);
        System.out.println("Order date: " + formattedDate);

        LocalDateTime pickupDateTime = LocalDateTime.of(2024, 2, 1, 14, 30, 0);
        String formattedDateTime = converter.formatDateTimeForXml(pickupDateTime);
        System.out.println("Pickup date/time: " + formattedDateTime);

        LocalDate parsedDate = converter.parseDate("2024-02-05");
        System.out.println("Parsed delivery date: " + parsedDate);
    }

    /**
     * Example 9: Pickup Instructions with date handling
     */
    public static void handlePickupInstructions() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String pickupXml = "<PickupInstructions>" +
                "<ShipmentNumber>SH-12345</ShipmentNumber>" +
                "<PickupDate>2024-02-01</PickupDate>" +
                "<PickupInstructions>Use loading dock B. Call 30 minutes before arrival.</PickupInstructions>" +
                "<PickupSpot>Warehouse 3, Bay 7</PickupSpot>" +
                "</PickupInstructions>";

        String json = converter.xmlToJson(pickupXml);
        System.out.println("Pickup Instructions as JSON: " + json);

        Element reconstructed = converter.jsonToXml(json, "PickupInstructions");
        System.out.println("Pickup date from XML: " + reconstructed.getChildText("PickupDate"));
    }

    /**
     * Example 10: Delivery Instructions with nested structure
     */
    public static void handleDeliveryInstructions() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String deliveryJson = "{" +
                "\"ShipmentNumber\":\"SH-12345\"," +
                "\"DeliveryDate\":\"2024-02-05\"," +
                "\"DeliveryInstructions\":\"Ring doorbell twice. Leave at reception if no answer.\"," +
                "\"DeliveryLocation\":\"123 Main St, Brisbane QLD 4000\"" +
                "}";

        Element deliveryXml = converter.jsonToXml(deliveryJson, "DeliveryInstructions");
        System.out.println("Delivery Instructions as XML element");

        String xmlString = converter.jsonToXmlString(deliveryJson, "DeliveryInstructions");
        System.out.println("Delivery Instructions as XML string: " + xmlString);
    }

    /**
     * Example 11: Route Guide with array of trackpoints
     */
    public static void handleRouteGuide() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String routeXml = "<RouteGuide>" +
                "<OrderNumber>PO-12345</OrderNumber>" +
                "<DeliveryLocation>Brisbane Depot</DeliveryLocation>" +
                "<Trackpoints>" +
                "  <Trackpoint>Sydney Warehouse</Trackpoint>" +
                "  <Trackpoint>Newcastle Distribution Center</Trackpoint>" +
                "  <Trackpoint>Gold Coast Hub</Trackpoint>" +
                "  <Trackpoint>Brisbane Depot</Trackpoint>" +
                "</Trackpoints>" +
                "</RouteGuide>";

        String json = converter.xmlToJson(routeXml);
        System.out.println("Route Guide with trackpoints: " + json);
    }

    /**
     * Example 12: Complex nested Purchase Order (full workflow data)
     */
    public static void handleCompleteWorkflowData() throws Exception {
        YawlDataConverter converter = new YawlDataConverter();

        String fullPurchaseOrderXml = "<PurchaseOrder>" +
                "<Company>" +
                "  <Name>Global Enterprises Ltd</Name>" +
                "  <Address>789 Commerce Boulevard</Address>" +
                "  <City>Melbourne</City>" +
                "  <State>VIC</State>" +
                "  <PostCode>3000</PostCode>" +
                "  <Phone>+61-3-9999-8888</Phone>" +
                "  <Fax>+61-3-9999-8889</Fax>" +
                "  <BusinessNumber>ABN-11223344</BusinessNumber>" +
                "</Company>" +
                "<Order>" +
                "  <OrderNumber>PO-2024-Q1-0042</OrderNumber>" +
                "  <OrderDate>2024-01-20</OrderDate>" +
                "  <Currency>AUD</Currency>" +
                "  <OrderTerms>Net 30 days from invoice date</OrderTerms>" +
                "  <RevisionNumber>2</RevisionNumber>" +
                "  <Remarks>Expedited shipping required. Fragile items - handle with care.</Remarks>" +
                "  <OrderLines>" +
                "    <Line>" +
                "      <LineNumber>1</LineNumber>" +
                "      <UnitCode>COMP-SRV-001</UnitCode>" +
                "      <UnitDescription>Server Rack Unit - 42U</UnitDescription>" +
                "      <UnitQuantity>5</UnitQuantity>" +
                "      <Action>Added</Action>" +
                "    </Line>" +
                "    <Line>" +
                "      <LineNumber>2</LineNumber>" +
                "      <UnitCode>NET-SW-G24</UnitCode>" +
                "      <UnitDescription>Network Switch - 24 Port Gigabit</UnitDescription>" +
                "      <UnitQuantity>10</UnitQuantity>" +
                "      <Action>Modified</Action>" +
                "    </Line>" +
                "  </OrderLines>" +
                "</Order>" +
                "<FreightCost>875.50</FreightCost>" +
                "<DeliveryLocation>Melbourne Data Center - Building 7</DeliveryLocation>" +
                "<InvoiceRequired>true</InvoiceRequired>" +
                "<PrePaid>false</PrePaid>" +
                "</PurchaseOrder>";

        String json = converter.xmlToJson(fullPurchaseOrderXml);
        System.out.println("Complete Purchase Order workflow data as JSON:");
        System.out.println(json);

        Element reconstructedOrder = converter.jsonToXml(json, "PurchaseOrder");
        System.out.println("\nReconstructed order number: " +
                reconstructedOrder.getChild("Order").getChildText("OrderNumber"));
        System.out.println("Company name: " +
                reconstructedOrder.getChild("Company").getChildText("Name"));
        System.out.println("Freight cost: " +
                reconstructedOrder.getChildText("FreightCost"));
    }

    /**
     * Example 13: Data sanitization
     */
    public static void demonstrateSanitization() {
        YawlDataConverter converter = new YawlDataConverter();

        String dirtyText = "Hello\u0000World\u0001with<control>chars&entities";
        String cleanText = converter.sanitizeXmlText(dirtyText);
        System.out.println("Sanitized text: " + cleanText);

        String invalidName = "123-Invalid Name!";
        String validName = converter.sanitizeXmlName(invalidName);
        System.out.println("Sanitized XML name: " + validName);
    }

    /**
     * Main method to run all examples
     */
    public static void main(String[] args) {
        try {
            System.out.println("=== YAWL Data Converter Examples ===\n");

            System.out.println("\n--- Example 1: Company XML to JSON ---");
            convertCompanyToJson();

            System.out.println("\n--- Example 2: Purchase Order JSON to XML ---");
            convertPurchaseOrderToXml();

            System.out.println("\n--- Example 3: Transportation Quote Round-trip ---");
            convertTransportationQuote();

            System.out.println("\n--- Example 4: Data Field Mapping ---");
            mapTaskData();

            System.out.println("\n--- Example 5: Array Handling (Order Lines) ---");
            handleOrderLines();

            System.out.println("\n--- Example 6: Type Coercion ---");
            demonstrateTypeCoercion();

            System.out.println("\n--- Example 7: Currency Formatting ---");
            handleCurrencyData();

            System.out.println("\n--- Example 8: Date/Time Formatting ---");
            handleDateTimeData();

            System.out.println("\n--- Example 9: Pickup Instructions ---");
            handlePickupInstructions();

            System.out.println("\n--- Example 10: Delivery Instructions ---");
            handleDeliveryInstructions();

            System.out.println("\n--- Example 11: Route Guide with Trackpoints ---");
            handleRouteGuide();

            System.out.println("\n--- Example 12: Complete Workflow Data ---");
            handleCompleteWorkflowData();

            System.out.println("\n--- Example 13: Data Sanitization ---");
            demonstrateSanitization();

            System.out.println("\n=== All examples completed successfully ===");

        } catch (Exception e) {
            System.err.println("Error running examples: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
