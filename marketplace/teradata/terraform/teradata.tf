# VantageCloud Configuration
# Teradata Vantage provisioning configuration (reference implementation)

# =============================================================================
# Note on VantageCloud Provisioning
# =============================================================================
# Actual VantageCloud Lake/Enterprise provisioning is done via:
# 1. AWS/Azure/GCP Marketplace subscription
# 2. Teradata Cloud Console (https://console.intellicloud.teradata.com)
# 3. Teradata Cloud API
#
# This file provides reference configuration for integration with
# the provisioning process and assumes network infrastructure is ready.
# =============================================================================

# =============================================================================
# Compute Group Configuration (SQL to be executed after provisioning)
# =============================================================================

locals {
  compute_group_sql = templatefile("${path.module}/sql/compute_groups.sql.tpl", {
    database_name      = var.teradata_database_name
    compute_clusters   = var.compute_clusters
  })

  database_init_sql = templatefile("${path.module}/sql/init_databases.sql.tpl", {
    yawl_db_name       = var.teradata_database_name
    staging_db_name    = var.teradata_staging_database_name
    analytics_db_name  = var.teradata_analytics_database_name
    permanent_space_gb = 100
    spool_space_gb     = 200
  })
}

# =============================================================================
# Secret for Teradata Credentials (stored in AWS Secrets Manager)
# =============================================================================

resource "aws_secretsmanager_secret" "teradata_credentials" {
  name                    = "${local.name_prefix}/teradata-credentials"
  description             = "Teradata Vantage credentials for YAWL integration"
  recovery_window_in_days = 30

  tags = {
    Name = "${local.name_prefix}-teradata-credentials"
  }
}

resource "aws_secretsmanager_secret_version" "teradata_credentials" {
  secret_id = aws_secretsmanager_secret.teradata_credentials.id
  secret_string = jsonencode({
    host        = "PLACEHOLDER_UPDATE_AFTER_PROVISIONING"
    port        = 1025
    database    = var.teradata_database_name
    username    = "yawl_app"
    password    = random_password.teradata.result
    jdbc_url    = "jdbc:teradata://PLACEHOLDER/DATABASE=${var.teradata_database_name},ENCRYPTDATA=true"
  })
}

resource "random_password" "teradata" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
  min_upper        = 2
  min_lower        = 2
  min_numeric      = 2
  min_special      = 2
}

# =============================================================================
# Parameter Store Configuration for YAWL Engine
# =============================================================================

resource "aws_ssm_parameter" "teradata_host" {
  name        = "/${local.name_prefix}/teradata/host"
  description = "Teradata Vantage host endpoint"
  type        = "String"
  value       = "PLACEHOLDER_UPDATE_AFTER_PROVISIONING"
  tier        = "Standard"

  tags = {
    Name = "${local.name_prefix}-teradata-host"
  }
}

resource "aws_ssm_parameter" "teradata_database" {
  name        = "/${local.name_prefix}/teradata/database"
  description = "Teradata Vantage database name"
  type        = "String"
  value       = var.teradata_database_name
  tier        = "Standard"

  tags = {
    Name = "${local.name_prefix}-teradata-database"
  }
}

resource "aws_ssm_parameter" "teradata_port" {
  name        = "/${local.name_prefix}/teradata/port"
  description = "Teradata Vantage port"
  type        = "String"
  value       = "1025"
  tier        = "Standard"

  tags = {
    Name = "${local.name_prefix}-teradata-port"
  }
}

resource "aws_ssm_parameter" "teradata_pool_size" {
  name        = "/${local.name_prefix}/teradata/pool-size"
  description = "Teradata connection pool size"
  type        = "String"
  value       = tostring(var.primary_max_instances * 10)
  tier        = "Standard"

  tags = {
    Name = "${local.name_prefix}-teradata-pool-size"
  }
}

# =============================================================================
# SQL Initialization Scripts (stored in S3)
# =============================================================================

resource "aws_s3_object" "init_databases_sql" {
  bucket = aws_s3_bucket.staging.bucket
  key    = "sql/init_databases.sql"
  content = local.database_init_sql

  tags = {
    Name = "${local.name_prefix}-init-databases-sql"
  }
}

resource "aws_s3_object" "compute_groups_sql" {
  bucket = aws_s3_bucket.staging.bucket
  key    = "sql/compute_groups.sql"
  content = local.compute_group_sql

  tags = {
    Name = "${local.name_prefix}-compute-groups-sql"
  }
}

resource "aws_s3_object" "yawl_schema_sql" {
  bucket = aws_s3_bucket.staging.bucket
  key    = "sql/yawl_schema.sql"
  content = templatefile("${path.module}/sql/yawl_schema.sql.tpl", {
    database_name = var.teradata_database_name
  })

  tags = {
    Name = "${local.name_prefix}-yawl-schema-sql"
  }
}

# =============================================================================
# Lambda Function for Post-Provisioning Setup
# =============================================================================

data "archive_file" "init_function" {
  type        = "zip"
  output_path = "${path.module}/function.zip"

  source {
    content  = <<-PYTHON
import json
import boto3
import logging
import os

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.client('s3')
secretsmanager = boto3.client('secretsmanager')
ssm = boto3.client('ssm')

def lambda_handler(event, context):
    """
    Lambda function to initialize Teradata Vantage after provisioning.
    This function is triggered by CloudWatch Events when the
    VantageCloud instance becomes available.
    """
    logger.info(f"Received event: {json.dumps(event)}")

    # Extract instance details from event
    instance_host = event.get('detail', {}).get('host')
    instance_id = event.get('detail', {}).get('instanceId')

    if not instance_host:
        logger.error("No instance host in event")
        return {'statusCode': 400, 'body': 'Missing instance host'}

    # Update SSM parameters
    try:
        ssm.put_parameter(
            Name='/${local.name_prefix}/teradata/host',
            Value=instance_host,
            Type='String',
            Overwrite=True
        )
        logger.info(f"Updated SSM parameter with host: {instance_host}")
    except Exception as e:
        logger.error(f"Failed to update SSM parameter: {e}")
        return {'statusCode': 500, 'body': str(e)}

    # Update Secrets Manager secret
    try:
        current_secret = secretsmanager.get_secret_value(
            SecretId='${aws_secretsmanager_secret.teradata_credentials.id}'
        )
        secret_data = json.loads(current_secret['SecretString'])
        secret_data['host'] = instance_host
        secret_data['jdbc_url'] = f"jdbc:teradata://{instance_host}/DATABASE=${var.teradata_database_name},ENCRYPTDATA=true"

        secretsmanager.put_secret_value(
            SecretId='${aws_secretsmanager_secret.teradata_credentials.id}',
            SecretString=json.dumps(secret_data)
        )
        logger.info("Updated Secrets Manager secret with host")
    except Exception as e:
        logger.error(f"Failed to update secret: {e}")
        return {'statusCode': 500, 'body': str(e)}

    return {
        'statusCode': 200,
        'body': json.dumps({
            'message': 'Initialization complete',
            'host': instance_host,
            'instanceId': instance_id
        })
    }
    PYTHON
    filename = "lambda_function.py"
  }
}

resource "aws_lambda_function" "init_teradata" {
  function_name = "${local.name_prefix}-init-teradata"
  role          = aws_iam_role.automation.arn
  handler       = "lambda_function.lambda_handler"
  runtime       = "python3.11"
  timeout       = 60

  filename         = data.archive_file.init_function.output_path
  source_code_hash = data.archive_file.init_function.output_base64sha256

  environment {
    variables = {
      SECRET_ID     = aws_secretsmanager_secret.teradata_credentials.id
      SSM_PREFIX    = "/${local.name_prefix}"
      DATABASE_NAME = var.teradata_database_name
    }
  }

  tags = {
    Name = "${local.name_prefix}-init-teradata-function"
  }
}

# =============================================================================
# CloudWatch Event Rule for Triggering Initialization
# =============================================================================

resource "aws_cloudwatch_event_rule" "teradata_ready" {
  name           = "${local.name_prefix}-teradata-ready"
  description    = "Trigger when Teradata Vantage instance is ready"
  event_pattern = jsonencode({
    source      = ["aws.teradata"]
    detail-type = ["Vantage Instance Ready"]
    detail = {
      status = ["AVAILABLE"]
    }
  })

  tags = {
    Name = "${local.name_prefix}-teradata-ready-rule"
  }
}

resource "aws_cloudwatch_event_target" "init_function" {
  rule      = aws_cloudwatch_event_rule.teradata_ready.name
  target_id = "InitTeradataFunction"
  arn       = aws_lambda_function.init_teradata.arn
}

resource "aws_lambda_permission" "cloudwatch_events" {
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.init_teradata.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.teradata_ready.arn
}
