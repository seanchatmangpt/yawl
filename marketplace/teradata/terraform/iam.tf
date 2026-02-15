# IAM Configuration
# Roles, Policies, and Instance Profiles for Teradata Vantage

# =============================================================================
# IAM Role for Teradata Vantage
# =============================================================================

resource "aws_iam_role" "teradata_vantage" {
  name = "${local.name_prefix}-teradata-role"

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

  tags = {
    Name = "${local.name_prefix}-teradata-role"
  }
}

# =============================================================================
# IAM Policies
# =============================================================================

# S3 access for data lake integration
resource "aws_iam_role_policy" "teradata_s3_access" {
  name = "${local.name_prefix}-s3-access"
  role = aws_iam_role.teradata_vantage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadWriteYAWLDataBucket"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket",
          "s3:GetBucketLocation"
        ]
        Resource = [
          aws_s3_bucket.yawl_data.arn,
          "${aws_s3_bucket.yawl_data.arn}/*"
        ]
      },
      {
        Sid    = "ReadWriteStagingBucket"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket",
          "s3:GetBucketLocation"
        ]
        Resource = [
          aws_s3_bucket.staging.arn,
          "${aws_s3_bucket.staging.arn}/*"
        ]
      }
    ]
  })
}

# CloudWatch logging
resource "aws_iam_role_policy" "teradata_cloudwatch" {
  name = "${local.name_prefix}-cloudwatch"
  role = aws_iam_role.teradata_vantage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
          "logs:DescribeLogGroups"
        ]
        Resource = "*"
      }
    ]
  })
}

# KMS access for encryption
resource "aws_iam_role_policy" "teradata_kms" {
  name = "${local.name_prefix}-kms"
  role = aws_iam_role.teradata_vantage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "KMSAccess"
        Effect = "Allow"
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Resource = var.kms_key_arn != "" ? var.kms_key_arn : "*"
      }
    ]
  })
}

# Secrets Manager access for credentials
resource "aws_iam_role_policy" "teradata_secrets" {
  name = "${local.name_prefix}-secrets"
  role = aws_iam_role.teradata_vantage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SecretsManagerRead"
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = var.secrets_manager_secret_arn != "" ? var.secrets_manager_secret_arn : "*"
      }
    ]
  })
}

# =============================================================================
# Instance Profile
# =============================================================================

resource "aws_iam_instance_profile" "teradata" {
  name = "${local.name_prefix}-teradata-profile"
  role = aws_iam_role.teradata_vantage.name

  tags = {
    Name = "${local.name_prefix}-teradata-profile"
  }
}

# =============================================================================
# IAM Role for YAWL Engine
# =============================================================================

resource "aws_iam_role" "yawl_engine" {
  name = "${local.name_prefix}-yawl-engine-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      },
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-yawl-engine-role"
  }
}

resource "aws_iam_role_policy" "yawl_engine_s3" {
  name = "${local.name_prefix}-yawl-s3"
  role = aws_iam_role.yawl_engine.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadWriteYAWLData"
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

resource "aws_iam_role_policy" "yawl_engine_secrets" {
  name = "${local.name_prefix}-yawl-secrets"
  role = aws_iam_role.yawl_engine.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadTeradataCredentials"
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = var.secrets_manager_secret_arn != "" ? var.secrets_manager_secret_arn : "*"
      }
    ]
  })
}

# =============================================================================
# IAM Role for Lambda (Automation)
# =============================================================================

resource "aws_iam_role" "automation" {
  name = "${local.name_prefix}-automation-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-automation-role"
  }
}

resource "aws_iam_role_policy" "automation" {
  name = "${local.name_prefix}-automation-policy"
  role = aws_iam_role.automation.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "*"
      },
      {
        Sid    = "EC2Describe"
        Effect = "Allow"
        Action = [
          "ec2:DescribeInstances",
          "ec2:DescribeVpcs",
          "ec2:DescribeSubnets",
          "ec2:DescribeSecurityGroups"
        ]
        Resource = "*"
      },
      {
        Sid    = "S3ReadWrite"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
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

# =============================================================================
# IAM Policy Documents (Data Source)
# =============================================================================

data "aws_iam_policy_document" "s3_bucket_policy" {
  statement {
    sid     = "EnforceTLS"
    effect  = "Deny"
    principals {
      type        = "AWS"
      identifiers = ["*"]
    }
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.yawl_data.arn,
      "${aws_s3_bucket.yawl_data.arn}/*"
    ]
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  statement {
    sid    = "AllowTeradataRole"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.teradata_vantage.arn]
    }
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket"
    ]
    resources = [
      aws_s3_bucket.yawl_data.arn,
      "${aws_s3_bucket.yawl_data.arn}/*"
    ]
  }

  statement {
    sid    = "AllowYAWLEngineRole"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.yawl_engine.arn]
    }
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket"
    ]
    resources = [
      aws_s3_bucket.yawl_data.arn,
      "${aws_s3_bucket.yawl_data.arn}/*"
    ]
  }
}
