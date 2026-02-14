#############################################################################
# AWS Cross-Region Backup Infrastructure
# Terraform Configuration for Multi-Region Disaster Recovery
# RTO: 15-30 minutes | RPO: 5-15 minutes
#############################################################################

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "terraform-state-backup"
    key            = "backup-recovery/aws/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-locks"
  }
}

#############################################################################
# Provider Configuration
#############################################################################

provider "aws" {
  alias  = "primary"
  region = var.primary_region

  default_tags {
    tags = {
      Environment   = var.environment
      ManagedBy     = "Terraform"
      BackupStrategy = "Cross-Region"
    }
  }
}

provider "aws" {
  alias  = "backup"
  region = var.backup_region

  default_tags {
    tags = {
      Environment   = var.environment
      ManagedBy     = "Terraform"
      BackupStrategy = "Cross-Region"
    }
  }
}

#############################################################################
# Variables
#############################################################################

variable "primary_region" {
  type        = string
  description = "Primary AWS region for production"
  default     = "us-east-1"
}

variable "backup_region" {
  type        = string
  description = "Backup AWS region for disaster recovery"
  default     = "us-west-2"
}

variable "environment" {
  type        = string
  description = "Environment name"
  default     = "production"
}

variable "backup_retention_days" {
  type        = number
  description = "Number of days to retain backups"
  default     = 30
}

variable "rds_instance_id" {
  type        = string
  description = "RDS instance ID to backup"
  default     = "production-db"
}

variable "s3_bucket_name" {
  type        = string
  description = "S3 bucket to backup"
  default     = "production-data"
}

variable "enable_cross_account_backup" {
  type        = bool
  description = "Enable cross-account backup for additional security"
  default     = true
}

#############################################################################
# Data Sources
#############################################################################

data "aws_caller_identity" "current" {
  provider = aws.primary
}

data "aws_availability_zones" "primary" {
  provider = aws.primary
  state    = "available"
}

data "aws_availability_zones" "backup" {
  provider = aws.backup
  state    = "available"
}

#############################################################################
# Primary Region: RDS Backup Configuration
# RTO: 15 minutes | RPO: 5 minutes
#############################################################################

# Primary RDS Instance
resource "aws_db_instance" "primary" {
  provider                = aws.primary
  identifier              = var.rds_instance_id
  allocated_storage       = 100
  storage_type            = "gp3"
  engine                  = "mysql"
  engine_version          = "8.0"
  instance_class          = "db.r6i.2xlarge"
  db_name                 = "production"
  username                = "admin"
  password                = random_password.rds_password.result
  parameter_group_name    = aws_db_parameter_group.primary.name
  skip_final_snapshot     = false
  final_snapshot_identifier = "${var.rds_instance_id}-final-snapshot-${formatdate("YYYYMMDD-hhmm", timestamp())}"

  # Backup Configuration (RTO/RPO Optimization)
  backup_retention_period      = var.backup_retention_days
  backup_window                = "03:00-04:00"
  copy_tags_to_snapshot        = true
  multi_az                     = true
  enabled_cloudwatch_logs_exports = ["error", "general", "slowquery"]

  # Enhanced Monitoring
  monitoring_interval     = 60
  monitoring_role_arn    = aws_iam_role.rds_monitoring.arn
  enable_iam_database_authentication = true

  # Performance Insights
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  # Encryption
  kms_key_id = aws_kms_key.rds_encryption.arn
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.primary.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  deletion_protection = true
  tags = {
    Name = "production-db-primary"
    BackupType = "RDS"
  }
}

# RDS Parameter Group
resource "aws_db_parameter_group" "primary" {
  provider    = aws.primary
  name        = "${var.rds_instance_id}-params"
  family      = "mysql8.0"
  description = "Parameter group for production RDS with backup optimization"

  parameter {
    name  = "slow_query_log"
    value = "1"
  }

  parameter {
    name  = "long_query_time"
    value = "2"
  }

  parameter {
    name  = "log_queries_not_using_indexes"
    value = "1"
  }

  tags = {
    Name = "production-db-params"
  }
}

# RDS Subnet Group
resource "aws_db_subnet_group" "primary" {
  provider    = aws.primary
  name        = "${var.rds_instance_id}-subnet-group"
  subnet_ids  = aws_subnet.primary_private[*].id
  description = "Subnet group for production RDS"

  tags = {
    Name = "production-db-subnet-group"
  }
}

# Security Group for RDS
resource "aws_security_group" "rds" {
  provider    = aws.primary
  name        = "${var.rds_instance_id}-sg"
  description = "Security group for production RDS"
  vpc_id      = aws_vpc.primary.id

  ingress {
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "production-rds-sg"
  }
}

# KMS Key for RDS Encryption
resource "aws_kms_key" "rds_encryption" {
  provider                = aws.primary
  description             = "KMS key for RDS encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "rds-encryption-key"
  }
}

resource "aws_kms_alias" "rds_encryption" {
  provider      = aws.primary
  name          = "alias/rds-encryption"
  target_key_id = aws_kms_key.rds_encryption.key_id
}

# IAM Role for RDS Enhanced Monitoring
resource "aws_iam_role" "rds_monitoring" {
  provider = aws.primary
  name     = "${var.rds_instance_id}-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  provider       = aws.primary
  role           = aws_iam_role.rds_monitoring.name
  policy_arn     = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# RDS Backup to Cross-Region
# This uses automated backups and cross-region snapshot copies
resource "aws_db_instance_automated_backups_replication" "primary" {
  provider               = aws.primary
  source_db_instance_identifier = aws_db_instance.primary.identifier
  backup_retention_period        = var.backup_retention_days
}

# Cross-Region Snapshot Copy
resource "aws_db_snapshot_copy_automation" "cross_region" {
  provider                = aws.primary
  source_db_snapshot_identifier = aws_db_instance.primary.id
  target_region          = var.backup_region
  retention_days         = var.backup_retention_days
  tags = {
    Name = "cross-region-snapshot-${var.backup_region}"
  }
}

#############################################################################
# Backup Region: RDS Read Replica
# Serves as warm standby for faster recovery
# RTO: 10 minutes from read replica promotion
#############################################################################

resource "aws_db_instance" "backup_replica" {
  provider            = aws.backup
  identifier          = "${var.rds_instance_id}-replica-${var.backup_region}"
  replicate_source_db = aws_db_instance.primary.identifier

  storage_encrypted               = true
  kms_key_id                     = aws_kms_key.rds_encryption_backup.arn
  publicly_accessible            = false
  auto_minor_version_upgrade     = true

  db_subnet_group_name   = aws_db_subnet_group.backup.name
  vpc_security_group_ids = [aws_security_group.rds_backup.id]

  deletion_protection = true

  tags = {
    Name = "production-db-replica-${var.backup_region}"
    BackupType = "RDS-ReadReplica"
  }
}

# KMS Key for Backup Region RDS Encryption
resource "aws_kms_key" "rds_encryption_backup" {
  provider                = aws.backup
  description             = "KMS key for backup region RDS encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "rds-encryption-key-backup"
  }
}

resource "aws_kms_alias" "rds_encryption_backup" {
  provider      = aws.backup
  name          = "alias/rds-encryption-backup"
  target_key_id = aws_kms_key.rds_encryption_backup.key_id
}

# DB Subnet Group for Backup Region
resource "aws_db_subnet_group" "backup" {
  provider    = aws.backup
  name        = "${var.rds_instance_id}-backup-subnet-group"
  subnet_ids  = aws_subnet.backup_private[*].id
  description = "Subnet group for backup region RDS"

  tags = {
    Name = "backup-db-subnet-group"
  }
}

# Security Group for Backup RDS
resource "aws_security_group" "rds_backup" {
  provider    = aws.backup
  name        = "${var.rds_instance_id}-backup-sg"
  description = "Security group for backup region RDS"
  vpc_id      = aws_vpc.backup.id

  ingress {
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "backup-rds-sg"
  }
}

#############################################################################
# S3 Cross-Region Replication
# RTO: 30 minutes | RPO: 15 minutes
#############################################################################

# Primary S3 Bucket
resource "aws_s3_bucket" "primary_data" {
  provider = aws.primary
  bucket   = "${var.s3_bucket_name}-${data.aws_caller_identity.current.account_id}-primary"

  tags = {
    Name = "production-data-primary"
    BackupType = "S3"
  }
}

# Enable Versioning on Primary Bucket
resource "aws_s3_bucket_versioning" "primary_data" {
  provider = aws.primary
  bucket   = aws_s3_bucket.primary_data.id

  versioning_configuration {
    status     = "Enabled"
    mfa_delete = "Disabled"
  }
}

# Enable Server-Side Encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "primary_data" {
  provider = aws.primary
  bucket   = aws_s3_bucket.primary_data.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kmaster_key_id   = aws_kms_key.s3_encryption.arn
    }
    bucket_key_enabled = true
  }
}

# KMS Key for S3 Encryption
resource "aws_kms_key" "s3_encryption" {
  provider                = aws.primary
  description             = "KMS key for S3 bucket encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "s3-encryption-key"
  }
}

resource "aws_kms_alias" "s3_encryption" {
  provider      = aws.primary
  name          = "alias/s3-encryption"
  target_key_id = aws_kms_key.s3_encryption.key_id
}

# Backup S3 Bucket
resource "aws_s3_bucket" "backup_data" {
  provider = aws.backup
  bucket   = "${var.s3_bucket_name}-${data.aws_caller_identity.current.account_id}-backup"

  tags = {
    Name = "production-data-backup"
    BackupType = "S3-Replica"
  }
}

# Enable Versioning on Backup Bucket
resource "aws_s3_bucket_versioning" "backup_data" {
  provider = aws.backup
  bucket   = aws_s3_bucket.backup_data.id

  versioning_configuration {
    status = "Enabled"
  }
}

# S3 Cross-Region Replication Configuration
resource "aws_s3_bucket_replication_configuration" "primary_to_backup" {
  provider = aws.primary
  bucket   = aws_s3_bucket.primary_data.id
  role     = aws_iam_role.s3_replication.arn

  rule {
    id     = "replicate-all-objects"
    status = "Enabled"

    destination {
      bucket       = aws_s3_bucket.backup_data.arn
      storage_class = "GLACIER"
      replication_time {
        status = "Enabled"
        time {
          minutes = 15
        }
      }
      metrics {
        status = "Enabled"
        event_threshold {
          minutes = 15
        }
      }
    }
  }

  depends_on = [aws_s3_bucket_versioning.primary_data, aws_s3_bucket_versioning.backup_data]
}

# IAM Role for S3 Replication
resource "aws_iam_role" "s3_replication" {
  provider = aws.primary
  name     = "s3-cross-region-replication-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "s3.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "s3_replication" {
  provider = aws.primary
  name     = "s3-cross-region-replication-policy"
  role     = aws_iam_role.s3_replication.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetReplicationConfiguration",
          "s3:ListBucket"
        ]
        Resource = aws_s3_bucket.primary_data.arn
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObjectVersionForReplication",
          "s3:GetObjectVersionAcl"
        ]
        Resource = "${aws_s3_bucket.primary_data.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:ReplicateObject",
          "s3:ReplicateDelete"
        ]
        Resource = "${aws_s3_bucket.backup_data.arn}/*"
      }
    ]
  })
}

#############################################################################
# EBS Snapshot Management
# RTO: 20 minutes | RPO: 10 minutes
#############################################################################

resource "aws_dlm_lifecycle_policy" "ebs_backup" {
  provider           = aws.primary
  execution_role_arn = aws_iam_role.dlm.arn
  description        = "Daily EBS snapshot policy for disaster recovery"
  state               = "ENABLED"

  policy_details {
    policy_type = "EBS_SNAPSHOT_MANAGEMENT"

    resource_types = ["INSTANCE"]

    target_tags = {
      BackupEnabled = "true"
    }

    schedules {
      name = "Daily Snapshots"

      create_rule {
        interval      = 24
        interval_unit = "HOURS"
        times         = ["03:00"]
      }

      retain_rule {
        count = var.backup_retention_days
      }

      tags_to_add = {
        Name              = "automated-snapshot"
        BackupStrategy    = "DLM"
        CreatedTime       = "03:00"
      }

      copy_tags = true

      cross_region_copy_rules {
        target_region = var.backup_region
        encrypted     = true
        cmk_key_arn   = aws_kms_key.ebs_encryption.arn
        copy_tags     = true
        retain_rule {
          interval      = 7
          interval_unit = "DAYS"
        }
      }
    }
  }
}

# IAM Role for DLM
resource "aws_iam_role" "dlm" {
  provider = aws.primary
  name     = "dlm-lifecycle-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "dlm.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "dlm" {
  provider       = aws.primary
  role           = aws_iam_role.dlm.name
  policy_arn     = "arn:aws:iam::aws:policy/service-role/AWSDataLifecycleManagerServiceRole"
}

# KMS Key for EBS Encryption
resource "aws_kms_key" "ebs_encryption" {
  provider                = aws.primary
  description             = "KMS key for EBS encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "ebs-encryption-key"
  }
}

resource "aws_kms_alias" "ebs_encryption" {
  provider      = aws.primary
  name          = "alias/ebs-encryption"
  target_key_id = aws_kms_key.ebs_encryption.key_id
}

#############################################################################
# Backup Vault for Cross-Region Backup
# AWS Backup service for centralized management
#############################################################################

resource "aws_backup_vault" "primary" {
  provider    = aws.primary
  name        = "primary-backup-vault-${var.primary_region}"
  description = "Backup vault for ${var.primary_region}"
  kms_key_arn = aws_kms_key.backup_encryption.arn

  tags = {
    Name = "primary-backup-vault"
  }
}

resource "aws_backup_vault" "backup" {
  provider    = aws.backup
  name        = "backup-vault-${var.backup_region}"
  description = "Backup vault for ${var.backup_region}"
  kms_key_arn = aws_kms_key.backup_encryption_backup.arn

  tags = {
    Name = "backup-vault"
  }
}

# KMS Key for Backup Vault Encryption
resource "aws_kms_key" "backup_encryption" {
  provider                = aws.primary
  description             = "KMS key for AWS Backup vault encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "backup-vault-encryption-key"
  }
}

resource "aws_kms_alias" "backup_encryption" {
  provider      = aws.primary
  name          = "alias/backup-vault-encryption"
  target_key_id = aws_kms_key.backup_encryption.key_id
}

resource "aws_kms_key" "backup_encryption_backup" {
  provider                = aws.backup
  description             = "KMS key for backup region backup vault encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "backup-vault-encryption-key-backup"
  }
}

resource "aws_kms_alias" "backup_encryption_backup" {
  provider      = aws.backup
  name          = "alias/backup-vault-encryption-backup"
  target_key_id = aws_kms_key.backup_encryption_backup.key_id
}

# Backup Plan for RDS
resource "aws_backup_plan" "rds_backup" {
  provider = aws.primary
  name     = "rds-backup-plan"

  rule {
    rule_name         = "daily_backup"
    target_backup_vault_name = aws_backup_vault.primary.name
    schedule          = "cron(0 3 ? * * *)"  # 3 AM UTC daily
    start_window      = 60
    completion_window = 120

    lifecycle {
      delete_after = var.backup_retention_days
      cold_storage_after = 7
    }

    recovery_point_tags = {
      Environment = var.environment
      BackupType  = "RDS"
    }

    copy_action {
      destination_vault_arn = aws_backup_vault.backup.arn

      lifecycle {
        delete_after       = var.backup_retention_days
        cold_storage_after = 7
      }
    }
  }

  tags = {
    Name = "rds-backup-plan"
  }
}

# Backup Plan for EBS
resource "aws_backup_plan" "ebs_backup" {
  provider = aws.primary
  name     = "ebs-backup-plan"

  rule {
    rule_name         = "hourly_backup"
    target_backup_vault_name = aws_backup_vault.primary.name
    schedule          = "cron(0 * ? * * *)"  # Every hour
    start_window      = 30
    completion_window = 60

    lifecycle {
      delete_after = var.backup_retention_days
      cold_storage_after = 14
    }

    copy_action {
      destination_vault_arn = aws_backup_vault.backup.arn

      lifecycle {
        delete_after       = var.backup_retention_days
        cold_storage_after = 14
      }
    }
  }

  tags = {
    Name = "ebs-backup-plan"
  }
}

#############################################################################
# VPC Configuration for Primary Region
#############################################################################

resource "aws_vpc" "primary" {
  provider           = aws.primary
  cidr_block         = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    Name = "production-vpc-${var.primary_region}"
  }
}

resource "aws_subnet" "primary_private" {
  provider            = aws.primary
  count               = length(data.aws_availability_zones.primary.names)
  vpc_id              = aws_vpc.primary.id
  cidr_block          = "10.0.${100 + count.index}.0/24"
  availability_zone   = data.aws_availability_zones.primary.names[count.index]

  tags = {
    Name = "primary-private-subnet-${count.index + 1}"
  }
}

#############################################################################
# VPC Configuration for Backup Region
#############################################################################

resource "aws_vpc" "backup" {
  provider           = aws.backup
  cidr_block         = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    Name = "backup-vpc-${var.backup_region}"
  }
}

resource "aws_subnet" "backup_private" {
  provider            = aws.backup
  count               = length(data.aws_availability_zones.backup.names)
  vpc_id              = aws_vpc.backup.id
  cidr_block          = "10.0.${100 + count.index}.0/24"
  availability_zone   = data.aws_availability_zones.backup.names[count.index]

  tags = {
    Name = "backup-private-subnet-${count.index + 1}"
  }
}

#############################################################################
# Random Password for RDS
#############################################################################

resource "random_password" "rds_password" {
  length  = 32
  special = true
}

#############################################################################
# Outputs
#############################################################################

output "primary_db_endpoint" {
  value       = aws_db_instance.primary.endpoint
  description = "Primary RDS endpoint"
}

output "backup_db_endpoint" {
  value       = aws_db_instance.backup_replica.endpoint
  description = "Backup RDS replica endpoint"
}

output "primary_bucket_name" {
  value       = aws_s3_bucket.primary_data.id
  description = "Primary S3 bucket name"
}

output "backup_bucket_name" {
  value       = aws_s3_bucket.backup_data.id
  description = "Backup S3 bucket name"
}

output "rto_estimate" {
  value       = "15-30 minutes (RTO: Database failover to backup region)"
  description = "Recovery Time Objective"
}

output "rpo_estimate" {
  value       = "5-15 minutes (RPO: Maximum acceptable data loss)"
  description = "Recovery Point Objective"
}

output "backup_vault_primary_arn" {
  value       = aws_backup_vault.primary.arn
  description = "Primary backup vault ARN"
}

output "backup_vault_backup_arn" {
  value       = aws_backup_vault.backup.arn
  description = "Backup region vault ARN"
}
