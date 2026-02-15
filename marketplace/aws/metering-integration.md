# YAWL Workflow Engine - AWS Marketplace Metering Integration

## Overview

This document provides comprehensive guidance for integrating YAWL Workflow Engine with the AWS Marketplace Metering Service for usage-based billing.

---

## Table of Contents

1. [Integration Architecture](#1-integration-architecture)
2. [API Reference](#2-api-reference)
3. [Implementation Guide](#3-implementation-guide)
4. [Java SDK Integration](#4-java-sdk-integration)
5. [Lambda Functions](#5-lambda-functions)
6. [Testing and Validation](#6-testing-and-validation)
7. [Error Handling](#7-error-handling)
8. [Best Practices](#8-best-practices)

---

## 1. Integration Architecture

### 1.1 High-Level Flow

```
+----------------+     +------------------+     +--------------------+
|   AWS          |     |   YAWL           |     |   AWS Marketplace  |
|   Marketplace  |---->|   Application    |---->|   Metering Service |
+----------------+     +------------------+     +--------------------+
        |                      |                         |
        | Subscribe            | Meter Usage             |
        | (x-amzn-             | (BatchMeterUsage)       |
        |  marketplace-token)  |                         |
        v                      v                         v
+----------------+     +------------------+     +--------------------+
|   Resolve      |     |   DynamoDB       |     |   Customer         |
|   Customer     |     |   (Metering      |     |   Billing          |
|   API          |     |    Records)      |     |                    |
+----------------+     +------------------+     +--------------------+
```

### 1.2 Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| Registration Handler | Process new subscriptions | API Gateway + Lambda |
| Entitlement Service | Verify subscription status | Lambda + DynamoDB |
| Metering Collector | Aggregate usage data | YAWL Application |
| Metering Publisher | Submit hourly metering | Lambda + SQS |
| SNS Handler | Process subscription events | Lambda + SNS |

### 1.3 Data Flow

```
1. Customer subscribes on AWS Marketplace
2. Redirect to YAWL with x-amzn-marketplace-token
3. Call ResolveCustomer to get CustomerIdentifier
4. Call GetEntitlements to verify entitlement
5. Store customer in DynamoDB
6. YAWL tracks usage in application
7. Hourly job aggregates usage
8. Call BatchMeterUsage to report to AWS
```

---

## 2. API Reference

### 2.1 ResolveCustomer API

**Purpose:** Exchange marketplace token for customer identifier

**Endpoint:** `meteringmarketplace.us-east-1.amazonaws.com`

**Request:**
```json
{
  "RegistrationToken": "x-amzn-marketplace-token-value"
}
```

**Response:**
```json
{
  "CustomerIdentifier": "ifAPi5AcF3",
  "CustomerAWSAccountId": "123456789012",
  "ProductCode": "abc123def456"
}
```

### 2.2 GetEntitlements API

**Purpose:** Retrieve customer entitlements for contract verification

**Endpoint:** `entitlement.marketplace.us-east-1.amazonaws.com`

**Request:**
```json
{
  "ProductCode": "abc123def456",
  "Filter": {
    "CUSTOMER_IDENTIFIER": ["ifAPi5AcF3"]
  }
}
```

**Response:**
```json
{
  "Entitlements": [
    {
      "ProductCode": "abc123def456",
      "Dimension": "professional_tier",
      "CustomerIdentifier": "ifAPi5AcF3",
      "Value": {
        "IntegerValue": 1
      },
      "ExpirationDate": "2025-12-31T23:59:59Z"
    }
  ]
}
```

### 2.3 BatchMeterUsage API

**Purpose:** Submit metering records for billing

**Endpoint:** `meteringmarketplace.us-east-1.amazonaws.com`

**Request:**
```json
{
  "ProductCode": "abc123def456",
  "UsageRecords": [
    {
      "Timestamp": "2025-01-15T10:00:00Z",
      "CustomerIdentifier": "ifAPi5AcF3",
      "Dimension": "workflow_executions",
      "Quantity": 150
    },
    {
      "Timestamp": "2025-01-15T10:00:00Z",
      "CustomerIdentifier": "ifAPi5AcF3",
      "Dimension": "active_users",
      "Quantity": 25
    }
  ]
}
```

**Response:**
```json
{
  "Results": [
    {
      "UsageRecord": {
        "Timestamp": "2025-01-15T10:00:00Z",
        "CustomerIdentifier": "ifAPi5AcF3",
        "Dimension": "workflow_executions",
        "Quantity": 150
      },
      "MeteringRecordId": "35155d37-56cb-423f-8554-5c4f3e3ff56d",
      "Status": "Success"
    }
  ],
  "UnprocessedRecords": []
}
```

---

## 3. Implementation Guide

### 3.1 DynamoDB Schema

#### Subscribers Table

```json
{
  "TableName": "yawl-subscribers",
  "KeySchema": [
    {
      "AttributeName": "customerIdentifier",
      "KeyType": "HASH"
    }
  ],
  "AttributeDefinitions": [
    {
      "AttributeName": "customerIdentifier",
      "AttributeType": "S"
    },
    {
      "AttributeName": "customerAWSAccountId",
      "AttributeType": "S"
    }
  ],
  "GlobalSecondaryIndexes": [
    {
      "IndexName": "AWSAccountIndex",
      "KeySchema": [
        {
          "AttributeName": "customerAWSAccountId",
          "KeyType": "HASH"
        }
      ],
      "Projection": {
        "ProjectionType": "ALL"
      }
    }
  ],
  "BillingMode": "PAY_PER_REQUEST"
}
```

**Item Structure:**
```json
{
  "customerIdentifier": "ifAPi5AcF3",
  "customerAWSAccountId": "123456789012",
  "productCode": "abc123def456",
  "email": "user@example.com",
  "company": "Acme Corp",
  "subscriptionStatus": "active",
  "tier": "professional_tier",
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-01-15T10:00:00Z",
  "entitlement": {
    "dimension": "professional_tier",
    "expirationDate": "2025-12-31T23:59:59Z"
  }
}
```

#### Metering Records Table

```json
{
  "TableName": "yawl-metering-records",
  "KeySchema": [
    {
      "AttributeName": "customerIdentifier",
      "KeyType": "HASH"
    },
    {
      "AttributeName": "timestamp",
      "KeyType": "RANGE"
    }
  ],
  "AttributeDefinitions": [
    {
      "AttributeName": "customerIdentifier",
      "AttributeType": "S"
    },
    {
      "AttributeName": "timestamp",
      "AttributeType": "N"
    },
    {
      "AttributeName": "meteringPending",
      "AttributeType": "S"
    }
  ],
  "GlobalSecondaryIndexes": [
    {
      "IndexName": "PendingMeteringRecordsIndex",
      "KeySchema": [
        {
          "AttributeName": "meteringPending",
          "KeyType": "HASH"
        },
        {
          "AttributeName": "timestamp",
          "KeyType": "RANGE"
        }
      ],
      "Projection": {
        "ProjectionType": "ALL"
      }
    }
  ],
  "BillingMode": "PAY_PER_REQUEST"
}
```

**Item Structure:**
```json
{
  "customerIdentifier": "ifAPi5AcF3",
  "timestamp": 1705312800,
  "dimensionUsage": [
    {
      "dimension": "workflow_executions",
      "value": 150
    },
    {
      "dimension": "active_users",
      "value": 25
    },
    {
      "dimension": "data_processed_gb",
      "value": 15
    }
  ],
  "meteringPending": "true",
  "meteringFailed": false,
  "meteringResponse": null,
  "createdAt": "2025-01-15T10:00:00Z"
}
```

---

## 4. Java SDK Integration

### 4.1 Maven Dependencies

```xml
<dependencies>
  <!-- AWS SDK for Java 2.x -->
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>marketplacemetering</artifactId>
    <version>2.21.0</version>
  </dependency>
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>marketplaceentitlement</artifactId>
    <version>2.21.0</version>
  </dependency>
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>dynamodb</artifactId>
    <version>2.21.0</version>
  </dependency>
</dependencies>
```

### 4.2 MarketplaceClient Class

```java
package org.yawlfoundation.yawl.marketplace;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacemetering.model.*;
import software.amazon.awssdk.services.marketplaceentitlement.MarketplaceEntitlementClient;
import software.amazon.awssdk.services.marketplaceentitlement.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AWS Marketplace integration client for YAWL.
 * Handles customer registration, entitlement verification, and usage metering.
 */
public class YAWLMarketplaceClient {

    private final MarketplaceMeteringClient meteringClient;
    private final MarketplaceEntitlementClient entitlementClient;
    private final String productCode;

    public YAWLMarketplaceClient(String productCode, Region region) {
        this.productCode = productCode;
        this.meteringClient = MarketplaceMeteringClient.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
        this.entitlementClient = MarketplaceEntitlementClient.builder()
            .region(Region.US_EAST_1) // Entitlement service only in us-east-1
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    /**
     * Resolve customer from marketplace token.
     * Called during registration flow.
     *
     * @param token The x-amzn-marketplace-token from registration
     * @return CustomerInfo containing identifiers
     */
    public CustomerInfo resolveCustomer(String token) {
        ResolveCustomerRequest request = ResolveCustomerRequest.builder()
            .registrationToken(token)
            .build();

        ResolveCustomerResponse response = meteringClient.resolveCustomer(request);

        return new CustomerInfo(
            response.customerIdentifier(),
            response.customerAWSAccountId(),
            response.productCode()
        );
    }

    /**
     * Get customer entitlements.
     * Verifies subscription status and tier.
     *
     * @param customerIdentifier The customer identifier
     * @return List of entitlements
     */
    public List<Entitlement> getEntitlements(String customerIdentifier) {
        GetEntitlementsRequest request = GetEntitlementsRequest.builder()
            .productCode(productCode)
            .filter(Map.of(
                "CUSTOMER_IDENTIFIER", List.of(customerIdentifier)
            ))
            .build();

        GetEntitlementsResponse response = entitlementClient.getEntitlements(request);
        return response.entitlements();
    }

    /**
     * Submit metering records to AWS Marketplace.
     * Should be called hourly for each dimension.
     *
     * @param records List of usage records to submit
     * @return BatchMeterUsageResult with results
     */
    public BatchMeterUsageResult meterUsage(List<UsageRecord> records) {
        BatchMeterUsageRequest request = BatchMeterUsageRequest.builder()
            .productCode(productCode)
            .usageRecords(records)
            .build();

        BatchMeterUsageResponse response = meteringClient.batchMeterUsage(request);

        List<MeteringResult> results = new ArrayList<>();
        for (UsageRecordResult result : response.results()) {
            results.add(new MeteringResult(
                result.usageRecord(),
                result.meteringRecordId(),
                result.statusAsString(),
                null // No error for success
            ));
        }

        return new BatchMeterUsageResult(results, response.unprocessedRecords());
    }

    /**
     * Create a usage record for metering.
     *
     * @param customerIdentifier Customer identifier
     * @param dimension Usage dimension name
     * @param quantity Usage quantity
     * @param timestamp Timestamp for the usage (hourly bucket)
     * @return UsageRecord ready for submission
     */
    public UsageRecord createUsageRecord(
            String customerIdentifier,
            String dimension,
            Integer quantity,
            Instant timestamp) {

        return UsageRecord.builder()
            .customerIdentifier(customerIdentifier)
            .dimension(dimension)
            .quantity(quantity)
            .timestamp(timestamp)
            .build();
    }

    // Inner classes for data transfer
    public record CustomerInfo(
        String customerIdentifier,
        String customerAWSAccountId,
        String productCode
    ) {}

    public record MeteringResult(
        UsageRecord usageRecord,
        String meteringRecordId,
        String status,
        String error
    ) {}

    public record BatchMeterUsageResult(
        List<MeteringResult> results,
        List<UsageRecord> unprocessedRecords
    ) {}
}
```

### 4.3 Usage Tracker Class

```java
package org.yawlfoundation.yawl.marketplace;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.time.Instant;
import java.util.*;

/**
 * Tracks usage for metering purposes.
 * Stores usage records in DynamoDB for batch submission.
 */
public class YAWLUsageTracker {

    private final DYNAMODB_CLIENT DynamoDbClient;
    private final String tableName;
    private final String meteringRecordsTable;

    public YAWLUsageTracker(DynamoDbClient dynamoDbClient,
                            String subscribersTable,
                            String meteringRecordsTable) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = subscribersTable;
        this.meteringRecordsTable = meteringRecordsTable;
    }

    /**
     * Record workflow execution usage.
     * Call this for each workflow instance executed.
     *
     * @param customerIdentifier Customer who executed the workflow
     */
    public void recordWorkflowExecution(String customerIdentifier) {
        recordUsage(customerIdentifier, "workflow_executions", 1);
    }

    /**
     * Record active user count.
     * Call this for each unique user session.
     *
     * @param customerIdentifier Customer identifier
     * @param userCount Number of active users
     */
    public void recordActiveUsers(String customerIdentifier, int userCount) {
        recordUsage(customerIdentifier, "active_users", userCount);
    }

    /**
     * Record data processed.
     *
     * @param customerIdentifier Customer identifier
     * @param gigabytes Data processed in GB
     */
    public void recordDataProcessed(String customerIdentifier, double gigabytes) {
        recordUsage(customerIdentifier, "data_processed_gb", (int) Math.ceil(gigabytes));
    }

    /**
     * Record API calls.
     *
     * @param customerIdentifier Customer identifier
     * @param callCount Number of API calls
     */
    public void recordApiCalls(String customerIdentifier, int callCount) {
        recordUsage(customerIdentifier, "api_calls", callCount / 1000); // Per 1000 calls
    }

    /**
     * Record worklet invocations.
     *
     * @param customerIdentifier Customer identifier
     * @param invocationCount Number of invocations
     */
    public void recordWorkletInvocations(String customerIdentifier, int invocationCount) {
        recordUsage(customerIdentifier, "worklet_invocations", invocationCount);
    }

    private void recordUsage(String customerIdentifier, String dimension, int quantity) {
        // Round timestamp to current hour
        Instant hourTimestamp = Instant.ofEpochSecond(
            (System.currentTimeMillis() / 3600000) * 3600000 / 1000
        );

        // Update DynamoDB record
        Map<String, AttributeValue> key = Map.of(
            "customerIdentifier", AttributeValue.builder().s(customerIdentifier).build(),
            "timestamp", AttributeValue.builder().n(String.valueOf(hourTimestamp.getEpochSecond())).build()
        );

        // Use atomic update to increment usage
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
            .tableName(meteringRecordsTable)
            .key(key)
            .updateExpression(
                "SET dimensionUsage = list_append(if_not_exists(dimensionUsage, :empty_list), :usage), " +
                "meteringPending = :pending, " +
                "createdAt = if_not_exists(createdAt, :now)"
            )
            .expressionAttributeValues(Map.of(
                ":empty_list", AttributeValue.builder().l(Collections.emptyList()).build(),
                ":usage", AttributeValue.builder().l(List.of(
                    AttributeValue.builder().m(Map.of(
                        "dimension", AttributeValue.builder().s(dimension).build(),
                        "value", AttributeValue.builder().n(String.valueOf(quantity)).build()
                    )).build()
                )).build(),
                ":pending", AttributeValue.builder().s("true").build(),
                ":now", AttributeValue.builder().s(Instant.now().toString()).build()
            ))
            .build();

        dynamoDbClient.updateItem(updateRequest);
    }

    /**
     * Get all pending metering records.
     *
     * @return List of pending records
     */
    public List<MeteringRecord> getPendingRecords() {
        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(meteringRecordsTable)
            .indexName("PendingMeteringRecordsIndex")
            .keyConditionExpression("meteringPending = :pending")
            .expressionAttributeValues(Map.of(
                ":pending", AttributeValue.builder().s("true").build()
            ))
            .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        return response.items().stream()
            .map(this::mapToMeteringRecord)
            .toList();
    }

    /**
     * Mark record as submitted after successful metering.
     *
     * @param customerIdentifier Customer identifier
     * @param timestamp Timestamp of the record
     * @param response Response from metering service
     */
    public void markRecordSubmitted(String customerIdentifier,
                                     Instant timestamp,
                                     String response) {
        Map<String, AttributeValue> key = Map.of(
            "customerIdentifier", AttributeValue.builder().s(customerIdentifier).build(),
            "timestamp", AttributeValue.builder().n(String.valueOf(timestamp.getEpochSecond())).build()
        );

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
            .tableName(meteringRecordsTable)
            .key(key)
            .updateExpression(
                "SET meteringPending = :submitted, " +
                "meteringFailed = :false, " +
                "meteringResponse = :response"
            )
            .expressionAttributeValues(Map.of(
                ":submitted", AttributeValue.builder().s("false").build(),
                ":false", AttributeValue.builder().bool(false).build(),
                ":response", AttributeValue.builder().s(response).build()
            ))
            .build();

        dynamoDbClient.updateItem(updateRequest);
    }

    private MeteringRecord mapToMeteringRecord(Map<String, AttributeValue> item) {
        List<DimensionUsage> dimensionUsage = item.get("dimensionUsage").l().stream()
            .map(attr -> {
                Map<String, AttributeValue> m = attr.m();
                return new DimensionUsage(
                    m.get("dimension").s(),
                    Integer.parseInt(m.get("value").n())
                );
            })
            .toList();

        return new MeteringRecord(
            item.get("customerIdentifier").s(),
            Instant.ofEpochSecond(Long.parseLong(item.get("timestamp").n())),
            dimensionUsage,
            item.get("meteringPending").s()
        );
    }

    // Inner records
    public record DimensionUsage(String dimension, int value) {}
    public record MeteringRecord(
        String customerIdentifier,
        Instant timestamp,
        List<DimensionUsage> dimensionUsage,
        String meteringPending
    ) {}
}
```

---

## 5. Lambda Functions

### 5.1 Registration Handler

```python
# registration_handler.py
import json
import boto3
import os
from datetime import datetime

metering = boto3.client('meteringmarketplace', region_name='us-east-1')
dynamodb = boto3.resource('dynamodb')
subscribers_table = dynamodb.Table(os.environ['SUBSCRIBERS_TABLE'])

def lambda_handler(event, context):
    """
    Handle new subscriber registration from AWS Marketplace.
    """
    try:
        # Get token from request
        token = event.get('token') or event.get('queryStringParameters', {}).get('x-amzn-marketplace-token')

        if not token:
            return {
                'statusCode': 400,
                'body': json.dumps({'error': 'Missing marketplace token'})
            }

        # Resolve customer
        response = metering.resolve_customer(RegistrationToken=token)

        customer_id = response['CustomerIdentifier']
        aws_account_id = response['CustomerAWSAccountId']
        product_code = response['ProductCode']

        # Get additional registration data
        email = event.get('email', '')
        company = event.get('company', '')
        name = event.get('name', '')

        # Store in DynamoDB
        subscribers_table.put_item(
            Item={
                'customerIdentifier': customer_id,
                'customerAWSAccountId': aws_account_id,
                'productCode': product_code,
                'email': email,
                'company': company,
                'name': name,
                'subscriptionStatus': 'pending',
                'createdAt': datetime.utcnow().isoformat(),
                'updatedAt': datetime.utcnow().isoformat()
            }
        )

        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': 'Registration successful',
                'customerId': customer_id
            })
        }

    except Exception as e:
        print(f'Error: {str(e)}')
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }
```

### 5.2 Entitlement Handler

```python
# entitlement_handler.py
import json
import boto3
import os
from datetime import datetime

entitlement = boto3.client('entitlement.marketplace', region_name='us-east-1')
dynamodb = boto3.resource('dynamodb')
subscribers_table = dynamodb.Table(os.environ['SUBSCRIBERS_TABLE'])

PRODUCT_CODE = os.environ['PRODUCT_CODE']

def lambda_handler(event, context):
    """
    Handle entitlement SNS notifications from AWS Marketplace.
    """
    try:
        # Parse SNS message
        message = json.loads(event['Records'][0]['Sns']['Message'])

        customer_id = message.get('customer-identifier')
        action = message.get('action')

        # Get current entitlements
        response = entitlement.get_entitlements(
            ProductCode=PRODUCT_CODE,
            Filter={'CUSTOMER_IDENTIFIER': [customer_id]}
        )

        entitlements = response.get('Entitlements', [])

        # Update subscriber record
        if entitlements:
            ent = entitlements[0]
            subscribers_table.update_item(
                Key={'customerIdentifier': customer_id},
                UpdateExpression='SET #status = :status, '
                               'tier = :tier, '
                               'entitlementExpiration = :expiry, '
                               'updatedAt = :now',
                ExpressionAttributeNames={'#status': 'subscriptionStatus'},
                ExpressionAttributeValues={
                    ':status': 'active' if action != 'entitlement-expired' else 'expired',
                    ':tier': ent.get('Dimension', 'unknown'),
                    ':expiry': ent.get('ExpirationDate', ''),
                    ':now': datetime.utcnow().isoformat()
                }
            )

        return {
            'statusCode': 200,
            'body': json.dumps({'message': 'Entitlement processed'})
        }

    except Exception as e:
        print(f'Error: {str(e)}')
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }
```

### 5.3 Metering Publisher

```python
# metering_publisher.py
import json
import boto3
import os
from datetime import datetime, timezone
from collections import defaultdict

metering = boto3.client('meteringmarketplace', region_name='us-east-1')
dynamodb = boto3.resource('dynamodb')
metering_table = dynamodb.Table(os.environ['METERING_RECORDS_TABLE'])

PRODUCT_CODE = os.environ['PRODUCT_CODE']
BATCH_SIZE = 100

def lambda_handler(event, context):
    """
    Hourly metering job - publish pending records to AWS Marketplace.
    """
    try:
        # Query pending records
        response = metering_table.query(
            IndexName='PendingMeteringRecordsIndex',
            KeyConditionExpression=boto3.dynamodb.conditions.Key('meteringPending').eq('true'),
            Limit=1000
        )

        records = response.get('Items', [])

        if not records:
            print('No pending records to meter')
            return {'statusCode': 200, 'body': json.dumps({'message': 'No records to process'})}

        # Aggregate by customer and dimension
        aggregated = defaultdict(lambda: defaultdict(int))

        for record in records:
            customer_id = record['customerIdentifier']
            for usage in record.get('dimensionUsage', []):
                dimension = usage['dimension']
                value = usage['value']
                aggregated[customer_id][dimension] += value

        # Create usage records for batch submission
        timestamp = datetime.now(timezone.utc)
        usage_records = []

        for customer_id, dimensions in aggregated.items():
            for dimension, quantity in dimensions.items():
                usage_records.append({
                    'Timestamp': timestamp.isoformat(),
                    'CustomerIdentifier': customer_id,
                    'Dimension': dimension,
                    'Quantity': quantity
                })

        # Submit in batches
        results = []
        for i in range(0, len(usage_records), BATCH_SIZE):
            batch = usage_records[i:i+BATCH_SIZE]

            try:
                response = metering.batch_meter_usage(
                    ProductCode=PRODUCT_CODE,
                    UsageRecords=batch
                )

                # Process results
                for result in response.get('Results', []):
                    if result.get('Status') == 'Success':
                        # Mark as submitted
                        customer_id = result['UsageRecord']['CustomerIdentifier']
                        mark_record_submitted(customer_id, timestamp, result)
                        results.append({
                            'customerId': customer_id,
                            'status': 'success',
                            'recordId': result.get('MeteringRecordId')
                        })
                    else:
                        results.append({
                            'customerId': result['UsageRecord']['CustomerIdentifier'],
                            'status': 'failed',
                            'error': str(result)
                        })

                # Handle unprocessed records
                for unprocessed in response.get('UnprocessedRecords', []):
                    print(f'Unprocessed record: {unprocessed}')
                    results.append({
                        'customerId': unprocessed.get('CustomerIdentifier'),
                        'status': 'unprocessed',
                        'error': str(unprocessed)
                    })

            except Exception as e:
                print(f'Error submitting batch: {str(e)}')
                # Mark records for retry
                for record in batch:
                    mark_record_failed(record['CustomerIdentifier'], timestamp, str(e))

        return {
            'statusCode': 200,
            'body': json.dumps({
                'processed': len(results),
                'results': results
            })
        }

    except Exception as e:
        print(f'Error: {str(e)}')
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }

def mark_record_submitted(customer_id, timestamp, response):
    """Mark metering record as successfully submitted."""
    metering_table.update_item(
        Key={
            'customerIdentifier': customer_id,
            'timestamp': str(int(timestamp.timestamp()))
        },
        UpdateExpression='SET meteringPending = :pending, '
                       'meteringFailed = :failed, '
                       'meteringResponse = :response',
        ExpressionAttributeValues={
            ':pending': 'false',
            ':failed': False,
            ':response': json.dumps(response, default=str)
        }
    )

def mark_record_failed(customer_id, timestamp, error):
    """Mark metering record as failed for retry."""
    metering_table.update_item(
        Key={
            'customerIdentifier': customer_id,
            'timestamp': str(int(timestamp.timestamp()))
        },
        UpdateExpression='SET meteringFailed = :failed, '
                       'meteringResponse = :error',
        ExpressionAttributeValues={
            ':failed': True,
            ':error': error
        }
    )
```

### 5.4 Subscription Handler

```python
# subscription_handler.py
import json
import boto3
import os
from datetime import datetime

dynamodb = boto3.resource('dynamodb')
subscribers_table = dynamodb.Table(os.environ['SUBSCRIBERS_TABLE'])

def lambda_handler(event, context):
    """
    Handle subscription SNS notifications from AWS Marketplace.
    """
    try:
        # Parse SNS message
        message = json.loads(event['Records'][0]['Sns']['Message'])

        customer_id = message.get('customer-identifier')
        action = message.get('action')

        status_map = {
            'subscribe-success': 'active',
            'subscribe-fail': 'failed',
            'unsubscribe-pending': 'pending_cancellation',
            'unsubscribe-success': 'cancelled'
        }

        new_status = status_map.get(action, 'unknown')

        # Update subscriber status
        subscribers_table.update_item(
            Key={'customerIdentifier': customer_id},
            UpdateExpression='SET #status = :status, '
                           'updatedAt = :now, '
                           'subscriptionAction = :action',
            ExpressionAttributeNames={'#status': 'subscriptionStatus'},
            ExpressionAttributeValues={
                ':status': new_status,
                ':now': datetime.utcnow().isoformat(),
                ':action': action
            }
        )

        # Handle cancellation - send final metering
        if action == 'unsubscribe-pending':
            # Trigger final metering
            print(f'Customer {customer_id} is unsubscribing - send final metering')

        # Handle successful cancellation - revoke access
        if action == 'unsubscribe-success':
            print(f'Customer {customer_id} cancelled - revoke access')
            # Implement access revocation logic

        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': f'Subscription action {action} processed',
                'customerId': customer_id
            })
        }

    except Exception as e:
        print(f'Error: {str(e)}')
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }
```

---

## 6. Testing and Validation

### 6.1 Local Testing

```bash
# Set environment variables
export PRODUCT_CODE="your-product-code"
export AWS_REGION="us-east-1"
export SUBSCRIBERS_TABLE="yawl-subscribers"
export METERING_RECORDS_TABLE="yawl-metering-records"

# Test ResolveCustomer
aws marketplacecommerceanalytics start-support-data-export \
  --export-type "test-resolve-customer" \
  --customer-identifiers "test-token"

# Test GetEntitlements
aws entitlement.marketplace get-entitlements \
  --product-code $PRODUCT_CODE \
  --filter CUSTOMER_IDENTIFIER=test-customer-id

# Test BatchMeterUsage
aws meteringmarketplace batch-meter-usage \
  --product-code $PRODUCT_CODE \
  --usage-records '[{
    "Timestamp": "2025-01-15T10:00:00Z",
    "CustomerIdentifier": "test-customer-id",
    "Dimension": "workflow_executions",
    "Quantity": 10
  }]'
```

### 6.2 Integration Test Script

```python
#!/usr/bin/env python3
"""
Integration test for AWS Marketplace metering integration.
"""

import boto3
import json
import time
from datetime import datetime, timezone

def test_marketplace_integration():
    """Test complete marketplace integration flow."""

    metering = boto3.client('meteringmarketplace', region_name='us-east-1')
    entitlement = boto3.client('entitlement.marketplace', region_name='us-east-1')
    dynamodb = boto3.resource('dynamodb')

    PRODUCT_CODE = 'your-product-code'
    TEST_TOKEN = 'test-registration-token'

    print("Testing AWS Marketplace Integration...")
    print("-" * 50)

    # Test 1: ResolveCustomer
    print("\n1. Testing ResolveCustomer...")
    try:
        # Note: This will only work with a valid test token from AWS
        # response = metering.resolve_customer(RegistrationToken=TEST_TOKEN)
        # print(f"   Customer ID: {response['CustomerIdentifier']}")
        print("   ResolveCustomer: SKIPPED (requires valid test token)")
    except Exception as e:
        print(f"   ResolveCustomer: ERROR - {str(e)}")

    # Test 2: GetEntitlements
    print("\n2. Testing GetEntitlements...")
    try:
        response = entitlement.get_entitlements(
            ProductCode=PRODUCT_CODE,
            Filter={'CUSTOMER_IDENTIFIER': ['test-customer']}
        )
        print(f"   Entitlements found: {len(response.get('Entitlements', []))}")
    except Exception as e:
        print(f"   GetEntitlements: ERROR - {str(e)}")

    # Test 3: BatchMeterUsage
    print("\n3. Testing BatchMeterUsage...")
    try:
        response = metering.batch_meter_usage(
            ProductCode=PRODUCT_CODE,
            UsageRecords=[{
                'Timestamp': datetime.now(timezone.utc).isoformat(),
                'CustomerIdentifier': 'test-customer',
                'Dimension': 'workflow_executions',
                'Quantity': 10
            }]
        )
        print(f"   Results: {len(response.get('Results', []))}")
        print(f"   Unprocessed: {len(response.get('UnprocessedRecords', []))}")
    except Exception as e:
        print(f"   BatchMeterUsage: ERROR - {str(e)}")

    print("\n" + "-" * 50)
    print("Integration test complete!")

if __name__ == '__main__':
    test_marketplace_integration()
```

---

## 7. Error Handling

### 7.1 Common Errors

| Error Code | Description | Resolution |
|------------|-------------|------------|
| `InvalidTokenException` | Token expired or invalid | Prompt user to re-subscribe |
| `InvalidCustomerIdentifierException` | Customer ID not found | Verify ResolveCustomer was called |
| `InvalidProductCodeException` | Wrong product code | Check configuration |
| `InvalidUsageDimensionException` | Dimension not in product | Verify dimension name |
| `TimestampOutOfBoundsException` | Timestamp > 1 hour in past | Use current time |
| `ThrottlingException` | Rate limit exceeded | Implement exponential backoff |
| `InternalServiceErrorException` | AWS service error | Retry with backoff |

### 7.2 Retry Strategy

```python
import time
import random
from functools import wraps

def retry_with_backoff(max_retries=5, base_delay=1, max_delay=60):
    """
    Decorator for retrying with exponential backoff.
    """
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            retries = 0
            while retries < max_retries:
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    retries += 1
                    if retries >= max_retries:
                        raise

                    # Exponential backoff with jitter
                    delay = min(base_delay * (2 ** retries), max_delay)
                    delay = delay * (0.5 + random.random())

                    print(f"Retry {retries}/{max_retries} after {delay:.2f}s: {str(e)}")
                    time.sleep(delay)

            return None
        return wrapper
    return decorator

@retry_with_backoff(max_retries=5, base_delay=1)
def submit_metering(records):
    """Submit metering with retry logic."""
    return metering.batch_meter_usage(
        ProductCode=PRODUCT_CODE,
        UsageRecords=records
    )
```

---

## 8. Best Practices

### 8.1 Metering Best Practices

1. **Send metering hourly** - Even with zero usage
2. **Use idempotent records** - Same timestamp/dimension/customer = deduplicated
3. **Batch submissions** - Up to 100 records per call
4. **Implement retry logic** - With exponential backoff up to 30 minutes
5. **Log all submissions** - For audit and debugging
6. **Monitor for failures** - Set up CloudWatch alarms

### 8.2 Security Best Practices

1. **Use IAM roles** - Never hardcode credentials
2. **Least privilege** - Only required API permissions
3. **Encrypt tokens** - Never log or expose marketplace tokens
4. **Validate input** - Sanitize all customer data
5. **Audit access** - Enable CloudTrail logging

### 8.3 Operational Best Practices

1. **Dead letter queue** - For failed metering records
2. **Distributed tracing** - Use X-Ray for debugging
3. **Health checks** - Monitor Lambda function health
4. **Backup records** - Archive metering data
5. **Capacity planning** - Monitor DynamoDB capacity

---

## Checklist for Production

- [ ] Product code configured
- [ ] IAM roles created with least privilege
- [ ] DynamoDB tables created
- [ ] SNS subscriptions confirmed
- [ ] Lambda functions deployed
- [ ] CloudWatch alarms configured
- [ ] Dead letter queues set up
- [ ] Retry logic implemented
- [ ] Logging enabled
- [ ] Integration testing complete
- [ ] AWS Marketplace Seller Ops notified

---

*Document Version: 1.0*
*Last Updated: February 2025*
