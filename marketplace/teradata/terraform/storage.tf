# Storage Configuration
# S3 Buckets for YAWL + Teradata Data Lake

# =============================================================================
# YAWL Data Bucket
# =============================================================================

resource "aws_s3_bucket" "yawl_data" {
  bucket        = "${replace(local.name_prefix, "-", "")}-yawl-data-${random_id.suffix.hex}"
  force_destroy = var.environment != "production"

  tags = {
    Name = "${local.name_prefix}-yawl-data"
  }
}

resource "aws_s3_bucket_versioning" "yawl_data" {
  bucket = aws_s3_bucket.yawl_data.id

  versioning_configuration {
    status = var.enable_bucket_versioning ? "Enabled" : "Suspended"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "yawl_data" {
  bucket = aws_s3_bucket.yawl_data.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.kms_key_arn != "" ? "aws:kms" : "AES256"
      kms_master_key_id = var.kms_key_arn != "" ? var.kms_key_arn : null
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "yawl_data" {
  bucket = aws_s3_bucket.yawl_data.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "yawl_data" {
  bucket = aws_s3_bucket.yawl_data.id
  policy = data.aws_iam_policy_document.s3_bucket_policy.json
}

resource "aws_s3_bucket_lifecycle_configuration" "yawl_data" {
  bucket = aws_s3_bucket.yawl_data.id

  rule {
    id     = "transition-to-intelligent-tiering"
    status = "Enabled"

    filter {}

    transition {
      days          = 90
      storage_class = "INTELLIGENT_TIERING"
    }

    noncurrent_version_transition {
      noncurrent_days = 30
      storage_class   = "GLACIER"
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  rule {
    id     = "archive-old-data"
    status = "Enabled"

    filter {
      prefix = "archive/"
    }

    transition {
      days          = 30
      storage_class = "GLACIER"
    }

    expiration {
      days = 365
    }
  }
}

# =============================================================================
# Staging Bucket
# =============================================================================

resource "aws_s3_bucket" "staging" {
  bucket        = "${replace(local.name_prefix, "-", "")}-staging-${random_id.suffix.hex}"
  force_destroy = true  # Staging can be destroyed

  tags = {
    Name = "${local.name_prefix}-staging"
  }
}

resource "aws_s3_bucket_versioning" "staging" {
  bucket = aws_s3_bucket.staging.id

  versioning_configuration {
    status = "Disabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "staging" {
  bucket = aws_s3_bucket.staging.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "staging" {
  bucket = aws_s3_bucket.staging.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "staging" {
  bucket = aws_s3_bucket.staging.id

  rule {
    id     = "delete-old-files"
    status = "Enabled"

    filter {}

    expiration {
      days = 7
    }
  }
}

# =============================================================================
# Audit Logs Bucket
# =============================================================================

resource "aws_s3_bucket" "audit_logs" {
  bucket        = "${replace(local.name_prefix, "-", "")}-audit-logs-${random_id.suffix.hex}"
  force_destroy = false

  tags = {
    Name = "${local.name_prefix}-audit-logs"
  }
}

resource "aws_s3_bucket_versioning" "audit_logs" {
  bucket = aws_s3_bucket.audit_logs.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit_logs" {
  bucket = aws_s3_bucket.audit_logs.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.kms_key_arn != "" ? "aws:kms" : "AES256"
      kms_master_key_id = var.kms_key_arn != "" ? var.kms_key_arn : null
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "audit_logs" {
  bucket = aws_s3_bucket.audit_logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_object_lock_configuration" "audit_logs" {
  bucket = aws_s3_bucket.audit_logs.id

  object_lock_enabled = "Enabled"

  rule {
    default_retention {
      mode  = "COMPLIANCE"
      days  = 365
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "audit_logs" {
  bucket = aws_s3_bucket.audit_logs.id

  rule {
    id     = "archive-after-90-days"
    status = "Enabled"

    filter {}

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 2555  # 7 years
    }
  }
}

# =============================================================================
# Backup Bucket
# =============================================================================

resource "aws_s3_bucket" "backup" {
  count         = var.enable_backup_bucket ? 1 : 0
  bucket        = "${replace(local.name_prefix, "-", "")}-backup-${random_id.suffix.hex}"
  force_destroy = false

  tags = {
    Name = "${local.name_prefix}-backup"
  }
}

resource "aws_s3_bucket_versioning" "backup" {
  count  = var.enable_backup_bucket ? 1 : 0
  bucket = aws_s3_bucket.backup[0].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  count  = var.enable_backup_bucket ? 1 : 0
  bucket = aws_s3_bucket.backup[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn != "" ? var.kms_key_arn : null
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "backup" {
  count  = var.enable_backup_bucket ? 1 : 0
  bucket = aws_s3_bucket.backup[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  count  = var.enable_backup_bucket ? 1 : 0
  bucket = aws_s3_bucket.backup[0].id

  rule {
    id     = "backup-retention"
    status = "Enabled"

    filter {}

    transition {
      days          = 30
      storage_class = "GLACIER"
    }

    transition {
      days          = 90
      storage_class = "DEEP_ARCHIVE"
    }

    expiration {
      days = 365
    }
  }
}

# =============================================================================
# S3 Access Logging Bucket
# =============================================================================

resource "aws_s3_bucket" "access_logs" {
  bucket        = "${replace(local.name_prefix, "-", "")}-access-logs-${random_id.suffix.hex}"
  force_destroy = false

  tags = {
    Name = "${local.name_prefix}-access-logs"
  }
}

resource "aws_s3_bucket_versioning" "access_logs" {
  bucket = aws_s3_bucket.access_logs.id

  versioning_configuration {
    status = "Disabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "access_logs" {
  bucket = aws_s3_bucket.access_logs.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "access_logs" {
  bucket = aws_s3_bucket.access_logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "access_logs" {
  bucket = aws_s3_bucket.access_logs.id

  rule {
    id     = "delete-old-logs"
    status = "Enabled"

    filter {}

    expiration {
      days = 90
    }
  }
}

# Enable access logging on main bucket
resource "aws_s3_bucket_logging" "yawl_data" {
  bucket = aws_s3_bucket.yawl_data.id

  target_bucket = aws_s3_bucket.access_logs.id
  target_prefix = "yawl-data/"
}
