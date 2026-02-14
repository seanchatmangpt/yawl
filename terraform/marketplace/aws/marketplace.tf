#------------------------------------------------------------------------------
# YAWL AWS Marketplace Module
# Version: 1.0.0
#
# This module handles AWS Marketplace deployment for YAWL
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# AWS Marketplace Subscription
#------------------------------------------------------------------------------

# Note: AWS Marketplace subscriptions are typically done through the console
# This module creates the infrastructure to support marketplace deployments

resource "aws_cloudformation_stack" "yawl_marketplace" {
  name = "${local.name_prefix}-marketplace"

  template_url = "https://s3.amazonaws.com/yawl-marketplace/${var.offer_id}/${var.plan_id}/template.yaml"

  parameters = {
    ClusterName       = "${local.name_prefix}-cluster"
    ClusterEndpoint   = var.cluster_endpoint
    Environment       = var.environment
    ProductCode       = var.product_code
  }

  capabilities = ["CAPABILITY_IAM", "CAPABILITY_NAMED_IAM"]

  tags = merge(var.common_tags, {
    MarketplaceSubscription = "true"
    PlanId                  = var.plan_id
    OfferId                 = var.offer_id
    Publisher               = var.publisher
  })
}

#------------------------------------------------------------------------------
# IAM Role for Marketplace
#------------------------------------------------------------------------------

resource "aws_iam_role" "marketplace" {
  name = "${local.name_prefix}-marketplace-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "marketplace.amazonaws.com"
        }
      },
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "cloudformation.amazonaws.com"
        }
      }
    ]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy" "marketplace" {
  name = "${local.name_prefix}-marketplace-policy"
  role = aws_iam_role.marketplace.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "eks:*",
          "rds:*",
          "s3:*",
          "ec2:*",
          "elasticloadbalancing:*",
          "iam:PassRole",
          "secretsmanager:*",
          "cloudwatch:*",
          "logs:*"
        ]
        Resource = "*"
      }
    ]
  })
}

#------------------------------------------------------------------------------
# Secrets Manager for Marketplace Configuration
#------------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "marketplace_config" {
  name = "${local.name_prefix}-marketplace-config"

  tags = var.common_tags
}

resource "aws_secretsmanager_secret_version" "marketplace_config" {
  secret_id = aws_secretsmanager_secret.marketplace_config.id
  secret_string = jsonencode({
    cluster_endpoint = var.cluster_endpoint
    plan_id          = var.plan_id
    offer_id         = var.offer_id
    publisher        = var.publisher
    product_code     = var.product_code
    deployment_time  = timestamp()
  })
}

#------------------------------------------------------------------------------
# Variables
#------------------------------------------------------------------------------

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "cluster_endpoint" {
  type = string
}

variable "plan_id" {
  type    = string
  default = "standard"
}

variable "offer_id" {
  type    = string
  default = "yawl-workflow-engine"
}

variable "publisher" {
  type    = string
  default = "yawlfoundation"
}

variable "product_code" {
  type    = string
  default = ""
}

variable "common_tags" {
  type    = map(string)
  default = {}
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "stack_name" {
  value = aws_cloudformation_stack.yawl_marketplace.name
}

output "stack_id" {
  value = aws_cloudformation_stack.yawl_marketplace.id
}

output "role_arn" {
  value = aws_iam_role.marketplace.arn
}

output "secret_arn" {
  value = aws_secretsmanager_secret.marketplace_config.arn
}
