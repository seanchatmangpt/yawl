# Teradata Vantage Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying Teradata Vantage on AWS, Azure, and GCP for YAWL workflow engine integration.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [AWS Deployment](#aws-deployment)
3. [Azure Deployment](#azure-deployment)
4. [GCP Deployment](#gcp-deployment)
5. [Multi-Cloud Configuration](#multi-cloud-configuration)
6. [Post-Deployment Setup](#post-deployment-setup)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| Terraform | 1.5+ | Infrastructure as Code |
| AWS CLI | 2.0+ | AWS resource management |
| Azure CLI | 2.50+ | Azure resource management |
| gcloud CLI | 400+ | GCP resource management |
| kubectl | 1.28+ | Kubernetes management (optional) |

### Account Requirements

| Provider | Requirement |
|----------|-------------|
| AWS | AWS account with Marketplace subscription access |
| Azure | Azure subscription with contributor access |
| GCP | GCP project with Compute Engine API enabled |

### Network Requirements

```yaml
# Common network requirements across all providers
network:
  vpc_cidr: "10.0.0.0/16"
  subnets:
    - name: "teradata-primary"
      cidr: "10.0.1.0/24"
    - name: "teradata-secondary"
      cidr: "10.0.2.0/24"

  ports:
    - port: 1025
      protocol: TCP
      purpose: "Teradata JDBC/SQL"
    - port: 443
      protocol: HTTPS
      purpose: "REST API/Console"
    - port: 9047
      protocol: TCP
      purpose: "Teradata Studio"
```

---

## AWS Deployment

### Option 1: AWS Marketplace (SaaS)

1. **Navigate to AWS Marketplace**
   ```
   https://aws.amazon.com/marketplace/pp/prodview-zhvzzxw2q5rqa
   ```

2. **Select Contract Options**
   - Choose 12-month contract
   - Select compute tier (Base/Advanced/Enterprise)
   - Specify storage requirements

3. **Configure Deployment**
   - Select region (e.g., us-east-1)
   - Configure VPC settings
   - Set up security groups

4. **Review and Subscribe**

### Option 2: Terraform Deployment

#### Main Configuration

```hcl
# terraform/main.tf
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "s3" {
    bucket         = "yawl-terraform-state"
    key            = "teradata/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-locks"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "YAWL"
      ManagedBy   = "Terraform"
      Environment = var.environment
    }
  }
}
```

#### Network Configuration

```hcl
# terraform/network.tf
resource "aws_vpc" "teradata" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${var.project_name}-vpc"
  }
}

resource "aws_subnet" "primary" {
  vpc_id                  = aws_vpc.teradata.id
  cidr_block              = var.primary_subnet_cidr
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = false

  tags = {
    Name = "${var.project_name}-primary-subnet"
  }
}

resource "aws_subnet" "secondary" {
  vpc_id                  = aws_vpc.teradata.id
  cidr_block              = var.secondary_subnet_cidr
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = false

  tags = {
    Name = "${var.project_name}-secondary-subnet"
  }
}

resource "aws_security_group" "teradata" {
  name        = "${var.project_name}-teradata-sg"
  description = "Security group for Teradata Vantage"
  vpc_id      = aws_vpc.teradata.id

  # JDBC access from YAWL engine
  ingress {
    description     = "Teradata JDBC"
    from_port       = 1025
    to_port         = 1025
    protocol        = "tcp"
    security_groups = [aws_security_group.yawl_engine.id]
  }

  # HTTPS for console/API
  ingress {
    description = "HTTPS Console"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.allowed_cidr_blocks]
  }

  # Teradata Studio
  ingress {
    description     = "Teradata Studio"
    from_port       = 9047
    to_port         = 9047
    protocol        = "tcp"
    security_groups = [aws_security_group.admin_access.id]
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-teradata-sg"
  }
}
```

#### VantageCloud Lake Configuration

```hcl
# terraform/vantagecloud.tf
# Note: This uses the Teradata Cloud Console API for provisioning
# For marketplace deployments, use the AWS Marketplace subscription

resource "null_resource" "vantagecloud_lake" {
  # This is a placeholder for VantageCloud Lake provisioning
  # Actual provisioning is done via Teradata Console or API

  provisioner "local-exec" {
    command = <<-EOT
      # Provision VantageCloud Lake using Teradata API
      curl -X POST "${var.teradata_console_url}/api/v1/systems" \
        -H "Authorization: Bearer ${var.teradata_api_token}" \
        -H "Content-Type: application/json" \
        -d '{
          "name": "${var.project_name}-lake",
          "cloud_provider": "aws",
          "region": "${var.aws_region}",
          "package": "${var.vantage_package}",
          "compute": {
            "primary_cluster": {
              "size": "${var.primary_cluster_size}",
              "min_instances": ${var.primary_min_instances},
              "max_instances": ${var.primary_max_instances}
            }
          },
          "storage": {
            "block_storage_tb": ${var.block_storage_tb},
            "object_storage_tb": ${var.object_storage_tb}
          },
          "network": {
            "vpc_id": "${aws_vpc.teradata.id}",
            "subnet_ids": ["${aws_subnet.primary.id}", "${aws_subnet.secondary.id}"]
          }
        }'
    EOT
  }

  depends_on = [
    aws_vpc.teradata,
    aws_subnet.primary,
    aws_subnet.secondary,
    aws_security_group.teradata
  ]
}
```

#### IAM Configuration

```hcl
# terraform/iam.tf
resource "aws_iam_role" "teradata_vantage" {
  name = "${var.project_name}-teradata-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "teradata_s3_access" {
  name = "${var.project_name}-s3-access"
  role = aws_iam_role.teradata_vantage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.yawl_data.arn,
          "${aws_s3_bucket.yawl_data.arn}/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy" "teradata_cloudwatch" {
  name = "${var.project_name}-cloudwatch"
  role = aws_iam_role.teradata_vantage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_s3_bucket" "yawl_data" {
  bucket = "${var.project_name}-yawl-data-${random_id.bucket_suffix.hex}"

  tags = {
    Name = "${var.project_name}-yawl-data"
  }
}

resource "random_id" "bucket_suffix" {
  byte_length = 4
}
```

#### Variables

```hcl
# terraform/variables.tf
variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "production"

  validation {
    condition     = contains(["development", "staging", "production"], var.environment)
    error_message = "Environment must be development, staging, or production."
  }
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "yawl-teradata"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "primary_subnet_cidr" {
  description = "CIDR block for primary subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "secondary_subnet_cidr" {
  description = "CIDR block for secondary subnet"
  type        = string
  default     = "10.0.2.0/24"
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access Teradata console"
  type        = list(string)
  default     = ["10.0.0.0/8"]
}

variable "vantage_package" {
  description = "VantageCloud Lake package tier"
  type        = string
  default     = "lake"

  validation {
    condition     = contains(["standard", "lake", "lake_plus"], var.vantage_package)
    error_message = "Package must be standard, lake, or lake_plus."
  }
}

variable "primary_cluster_size" {
  description = "Primary cluster size"
  type        = string
  default     = "TD_COMPUTE_SMALL"
}

variable "primary_min_instances" {
  description = "Minimum primary cluster instances"
  type        = number
  default     = 2
}

variable "primary_max_instances" {
  description = "Maximum primary cluster instances"
  type        = number
  default     = 8
}

variable "block_storage_tb" {
  description = "Block storage in TB"
  type        = number
  default     = 1
}

variable "object_storage_tb" {
  description = "Object storage in TB"
  type        = number
  default     = 10
}

variable "teradata_console_url" {
  description = "Teradata Cloud Console URL"
  type        = string
  default     = "https://console.intellicloud.teradata.com"
}

variable "teradata_api_token" {
  description = "Teradata API token"
  type        = string
  sensitive   = true
}
```

#### Outputs

```hcl
# terraform/outputs.tf
output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.teradata.id
}

output "primary_subnet_id" {
  description = "ID of the primary subnet"
  value       = aws_subnet.primary.id
}

output "secondary_subnet_id" {
  description = "ID of the secondary subnet"
  value       = aws_subnet.secondary.id
}

output "security_group_id" {
  description = "ID of the Teradata security group"
  value       = aws_security_group.teradata.id
}

output "s3_bucket_name" {
  description = "Name of the S3 bucket for YAWL data"
  value       = aws_s3_bucket.yawl_data.bucket
}

output "iam_role_arn" {
  description = "ARN of the Teradata IAM role"
  value       = aws_iam_role.teradata_vantage.arn
}
```

---

## Azure Deployment

### Option 1: Azure Marketplace

1. **Navigate to Azure Marketplace**
   ```
   https://marketplace.microsoft.com/en-us/product/saas/teradata.teradata-vantage-saas
   ```

2. **Select Plan**
   - Choose VantageCloud Enterprise or Lake
   - Select region and pricing tier

3. **Configure Subscription**
   - Link to existing Azure subscription
   - Configure resource group
   - Set up networking

4. **Complete Purchase**
   - Review terms
   - Subscribe

### Option 2: Terraform Deployment

```hcl
# terraform/azure/main.tf
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.80"
    }
  }
}

provider "azurerm" {
  features {}

  subscription_id = var.azure_subscription_id
  tenant_id       = var.azure_tenant_id
}

# Resource Group
resource "azurerm_resource_group" "teradata" {
  name     = "${var.project_name}-rg"
  location = var.azure_region
}

# Virtual Network
resource "azurerm_virtual_network" "teradata" {
  name                = "${var.project_name}-vnet"
  address_space       = [var.vpc_cidr]
  location            = azurerm_resource_group.teradata.location
  resource_group_name = azurerm_resource_group.teradata.name
}

# Subnets
resource "azurerm_subnet" "primary" {
  name                 = "${var.project_name}-primary-subnet"
  resource_group_name  = azurerm_resource_group.teradata.name
  virtual_network_name = azurerm_virtual_network.teradata.name
  address_prefixes     = [var.primary_subnet_cidr]

  service_endpoints = [
    "Microsoft.Storage",
    "Microsoft.Sql"
  ]
}

resource "azurerm_subnet" "secondary" {
  name                 = "${var.project_name}-secondary-subnet"
  resource_group_name  = azurerm_resource_group.teradata.name
  virtual_network_name = azurerm_virtual_network.teradata.name
  address_prefixes     = [var.secondary_subnet_cidr]
}

# Network Security Group
resource "azurerm_network_security_group" "teradata" {
  name                = "${var.project_name}-nsg"
  location            = azurerm_resource_group.teradata.location
  resource_group_name = azurerm_resource_group.teradata.name

  security_rule {
    name                       = "JDBC"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "1025"
    source_address_prefix      = "VirtualNetwork"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "HTTPS"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "TeradataStudio"
    priority                   = 120
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "9047"
    source_address_prefix      = "VirtualNetwork"
    destination_address_prefix = "*"
  }
}

# Associate NSG with subnets
resource "azurerm_subnet_network_security_group_association" "primary" {
  subnet_id                 = azurerm_subnet.primary.id
  network_security_group_id = azurerm_network_security_group.teradata.id
}

# Storage Account for data lake
resource "azurerm_storage_account" "yawl_data" {
  name                     = "${replace(var.project_name, "-", "")}data"
  resource_group_name      = azurerm_resource_group.teradata.name
  location                 = azurerm_resource_group.teradata.location
  account_tier             = "Standard"
  account_replication_type = "GRS"
  account_kind             = "StorageV2"
  is_hns_enabled           = true

  network_rules {
    default_action             = "Deny"
    virtual_network_subnet_ids = [azurerm_subnet.primary.id]
  }
}
```

---

## GCP Deployment

### Option 1: Google Cloud Marketplace

1. **Navigate to Google Cloud Marketplace**
   ```
   https://console.cloud.google.com/marketplace/product/teradata-public/teradata-vantage
   ```

2. **Configure Deployment**
   - Select project
   - Choose region and zone
   - Configure machine type
   - Set up networking

### Option 2: Terraform Deployment

```hcl
# terraform/gcp/main.tf
terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

# VPC Network
resource "google_compute_network" "teradata" {
  name                    = "${var.project_name}-vpc"
  auto_create_subnetworks = false
}

# Subnets
resource "google_compute_subnetwork" "primary" {
  name          = "${var.project_name}-primary-subnet"
  ip_cidr_range = var.primary_subnet_cidr
  region        = var.gcp_region
  network       = google_compute_network.teradata.id

  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = "10.100.0.0/14"
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.104.0.0/20"
  }
}

# Firewall Rules
resource "google_compute_firewall" "teradata_jdbc" {
  name    = "${var.project_name}-jdbc"
  network = google_compute_network.teradata.name

  allow {
    protocol = "tcp"
    ports    = ["1025"]
  }

  source_ranges = [var.vpc_cidr]
  target_tags   = ["teradata"]
}

resource "google_compute_firewall" "teradata_https" {
  name    = "${var.project_name}-https"
  network = google_compute_network.teradata.name

  allow {
    protocol = "tcp"
    ports    = ["443"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["teradata"]
}

resource "google_compute_firewall" "teradata_studio" {
  name    = "${var.project_name}-studio"
  network = google_compute_network.teradata.name

  allow {
    protocol = "tcp"
    ports    = ["9047"]
  }

  source_ranges = [var.vpc_cidr]
  target_tags   = ["teradata"]
}

# Cloud Storage bucket for data
resource "google_storage_bucket" "yawl_data" {
  name          = "${var.project_name}-yawl-data"
  location      = var.gcp_region
  force_destroy = false

  uniform_bucket_level_access = true

  lifecycle_rule {
    action {
      type = "SetStorageClass"
      storage_class = "NEARLINE"
    }
    condition {
      age = 90
    }
  }
}
```

---

## Multi-Cloud Configuration

### QueryGrid Setup

For multi-cloud data federation between YAWL deployments on different clouds:

```yaml
# querygrid-config.yaml
querygrid:
  version: "3.0"

  systems:
    - name: "yawl-aws"
      type: "vantagecloud-lake"
      provider: "aws"
      region: "us-east-1"
      database: "yawl"
      host: "${AWS_VANTAGE_HOST}"

    - name: "yawl-azure"
      type: "vantagecloud-enterprise"
      provider: "azure"
      region: "eastus"
      database: "yawl"
      host: "${AZURE_VANTAGE_HOST}"

  links:
    - name: "aws-to-azure"
      source: "yawl-aws"
      target: "yawl-azure"
      properties:
        authentication: "kerberos"
        encryption: true
        pushdown: "full"

  foreign_servers:
    - name: "aws_workflow_data"
      system: "yawl-aws"
      database: "yawl"
      tables:
        - "workflow_executions"
        - "task_queue"
        - "audit_log"
```

### Cross-Cloud DNS Resolution

```hcl
# terraform/multicloud/dns.tf
# Private DNS zone for cross-cloud resolution

# AWS Route53
resource "aws_route53_zone" "teradata_private" {
  name = "teradata.yawl.internal"

  vpc {
    vpc_id = aws_vpc.teradata.id
  }
}

# Azure Private DNS Zone
resource "azurerm_private_dns_zone" "teradata" {
  name                = "teradata.yawl.internal"
  resource_group_name = azurerm_resource_group.teradata.name
}

# GCP Cloud DNS
resource "google_dns_managed_zone" "teradata_private" {
  name        = "teradata-private"
  dns_name    = "teradata.yawl.internal."
  description = "Private DNS zone for Teradata"
  visibility  = "private"

  private_visibility_config {
    networks {
      network_url = google_compute_network.teradata.id
    }
  }
}
```

---

## Post-Deployment Setup

### 1. Initialize YAWL Schema

```sql
-- Connect to Teradata and run initialization script
-- File: sql/init_yawl_schema.sql

-- Create YAWL database
CREATE DATABASE yawl
    AS PERMANENT = 100e9,
       SPOOL = 200e9,
       DEFAULT MAP = TD_MAP1;

-- Create staging database
CREATE DATABASE yawl_staging
    AS PERMANENT = 50e9,
       SPOOL = 100e9;

-- Create analytics database
CREATE DATABASE yawl_analytics
    AS PERMANENT = 75e9,
       SPOOL = 150e9;

-- Create application user
CREATE USER yawl_app
    AS PERMANENT = 10e9,
       SPOOL = 20e9,
       PASSWORD = "${YAWL_APP_PASSWORD}";

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE
    ON yawl TO yawl_app;

GRANT SELECT
    ON yawl_analytics TO yawl_app;
```

### 2. Configure Compute Clusters

```sql
-- Create compute group for YAWL workloads
CREATE COMPUTE GROUP CG_YAWL_PRODUCTION
    USING QUERY_STRATEGY('STANDARD');

-- Create compute profiles for different workload patterns
CREATE COMPUTE PROFILE CP_YAWL_BATCH
    IN COMPUTE GROUP CG_YAWL_PRODUCTION
    ,INSTANCE = TD_COMPUTE_MEDIUM
    ,INSTANCE TYPE = STANDARD
    USING
        MIN_COMPUTE_COUNT (2)
        MAX_COMPUTE_COUNT (8)
        SCALING_POLICY ('STANDARD')
        START_TIME ('0 6 * * MON-FRI')
        END_TIME ('0 22 * * MON-FRI')
        COOLDOWN_PERIOD (30);

CREATE COMPUTE PROFILE CP_YAWL_REALTIME
    IN COMPUTE GROUP CG_YAWL_PRODUCTION
    ,INSTANCE = TD_COMPUTE_SMALL
    ,INSTANCE TYPE = STANDARD
    USING
        MIN_COMPUTE_COUNT (1)
        MAX_COMPUTE_COUNT (4)
        SCALING_POLICY ('STANDARD');

-- Grant compute group access to YAWL user
GRANT COMPUTE GROUP CG_YAWL_PRODUCTION TO yawl_app;
```

### 3. Set Up Monitoring

```yaml
# monitoring/prometheus.yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'teradata-vantage'
    static_configs:
      - targets:
          - '${TERADATA_HOST}:9090'
    metrics_path: /metrics
    scheme: https
    tls_config:
      ca_file: /etc/ssl/certs/teradata-ca.pem
    basic_auth:
      username: ${TERADATA_MONITOR_USER}
      password: ${TERADATA_MONITOR_PASSWORD}
```

### 4. Configure YAWL Engine Connection

Update the YAWL engine configuration:

```xml
<!-- yawl-engine-config.xml -->
<datasources>
    <datasource name="teradata-vantage">
        <driver>com.teradata.jdbc.TeraDriver</driver>
        <url>jdbc:teradata://${TERADATA_HOST}/DATABASE=yawl,ENCRYPTDATA=true</url>
        <username>${TERADATA_USER}</username>
        <password>${TERADATA_PASSWORD}</password>
        <pool>
            <maxActive>20</maxActive>
            <maxIdle>10</maxIdle>
            <minIdle>5</minIdle>
            <maxWait>30000</maxWait>
            <validationQuery>SELECT 1</validationQuery>
            <testOnBorrow>true</testOnBorrow>
            <testWhileIdle>true</testWhileIdle>
        </pool>
        <properties>
            <property name="LOGIN_TIMEOUT" value="60"/>
            <property name="RESPONSE_BUFFER_SIZE" value="65536"/>
            <property name="CHARSET" value="UTF8"/>
        </properties>
    </datasource>
</datasources>
```

---

## Troubleshooting

### Common Issues

#### Connection Timeout

```bash
# Check network connectivity
telnet ${TERADATA_HOST} 1025

# Check security group rules
aws ec2 describe-security-groups --group-ids sg-xxxxx

# Verify SSL certificate
openssl s_client -connect ${TERADATA_HOST}:443 -showcerts
```

#### Authentication Failure

```sql
-- Verify user exists and is not locked
SELECT * FROM DBC.UsersV WHERE UserName = 'yawl_app';

-- Reset password if needed
MODIFY USER yawl_app AS PASSWORD = "${NEW_PASSWORD}";

-- Check access rights
SELECT * FROM DBC.AccessRightsV WHERE UserName = 'yawl_app';
```

#### Performance Issues

```sql
-- Check query performance
SELECT
    QueryID,
    QueryText,
    ElapseTime,
    TotalIOCount,
    AMPCPUTime
FROM Dbc.DBQLogTbl
WHERE StartTime >= CURRENT_TIMESTAMP - INTERVAL '1' HOUR
ORDER BY ElapseTime DESC;

-- Check for table skew
SELECT
    DATABASENAME,
    TABLENAME,
    SUM(CURRENTPERM) / 1024 / 1024 / 1024 AS size_gb,
    COUNT(DISTINCT VPROC) AS amp_count
FROM Dbc.TableSizeV
WHERE DATABASENAME = 'yawl'
GROUP BY DATABASENAME, TABLENAME;
```

### Health Check Script

```bash
#!/bin/bash
# scripts/healthcheck.sh

set -e

HOST="${TERADATA_HOST}"
USER="${TERADATA_USER}"
PASSWORD="${TERADATA_PASSWORD}"

# Check JDBC connectivity
echo "Checking JDBC connectivity..."
java -cp "lib/*" com.teradata.jdbc.TeraDriver \
    "jdbc:teradata://${HOST}/DATABASE=yawl" \
    "${USER}" "${PASSWORD}" \
    -e "SELECT 1"

# Check query performance
echo "Checking query performance..."
bteq <<EOF
.logon ${HOST}/${USER},${PASSWORD}
SELECT COUNT(*) FROM yawl.workflow_executions WHERE processing_date = CURRENT_DATE;
.logoff
EOF

echo "Health check completed successfully."
```

---

## Deployment Checklist

### Pre-Deployment

- [ ] Verify cloud provider account access
- [ ] Confirm network CIDR ranges don't conflict
- [ ] Set up Terraform state backend
- [ ] Configure environment variables
- [ ] Review pricing and commit to contract

### Deployment

- [ ] Run Terraform plan and review changes
- [ ] Apply Terraform configuration
- [ ] Verify VantageCloud instance is running
- [ ] Configure compute clusters
- [ ] Set up YAWL database schema

### Post-Deployment

- [ ] Test JDBC connectivity from YAWL engine
- [ ] Verify security group rules
- [ ] Configure monitoring and alerting
- [ ] Set up backup policies
- [ ] Document connection details
- [ ] Schedule regular health checks
