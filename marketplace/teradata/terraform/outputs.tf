# Terraform Outputs
# Exported values for Teradata Vantage Deployment

# =============================================================================
# Network Outputs
# =============================================================================

output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.teradata.id
}

output "vpc_cidr_block" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.teradata.cidr_block
}

output "primary_subnet_id" {
  description = "ID of the primary subnet"
  value       = aws_subnet.primary.id
}

output "primary_subnet_cidr" {
  description = "CIDR block of the primary subnet"
  value       = aws_subnet.primary.cidr_block
}

output "primary_subnet_availability_zone" {
  description = "Availability zone of the primary subnet"
  value       = aws_subnet.primary.availability_zone
}

output "secondary_subnet_id" {
  description = "ID of the secondary subnet"
  value       = aws_subnet.secondary.id
}

output "secondary_subnet_cidr" {
  description = "CIDR block of the secondary subnet"
  value       = aws_subnet.secondary.cidr_block
}

output "secondary_subnet_availability_zone" {
  description = "Availability zone of the secondary subnet"
  value       = aws_subnet.secondary.availability_zone
}

output "public_subnet_id" {
  description = "ID of the public subnet (if created)"
  value       = var.enable_bastion ? aws_subnet.public[0].id : null
}

# =============================================================================
# Security Group Outputs
# =============================================================================

output "teradata_security_group_id" {
  description = "ID of the Teradata security group"
  value       = aws_security_group.teradata.id
}

output "yawl_engine_security_group_id" {
  description = "ID of the YAWL engine security group"
  value       = aws_security_group.yawl_engine.id
}

output "bastion_security_group_id" {
  description = "ID of the bastion security group (if created)"
  value       = var.enable_bastion ? aws_security_group.bastion[0].id : null
}

# =============================================================================
# Storage Outputs
# =============================================================================

output "yawl_data_bucket_name" {
  description = "Name of the YAWL data S3 bucket"
  value       = aws_s3_bucket.yawl_data.bucket
}

output "yawl_data_bucket_arn" {
  description = "ARN of the YAWL data S3 bucket"
  value       = aws_s3_bucket.yawl_data.arn
}

output "staging_bucket_name" {
  description = "Name of the staging S3 bucket"
  value       = aws_s3_bucket.staging.bucket
}

output "staging_bucket_arn" {
  description = "ARN of the staging S3 bucket"
  value       = aws_s3_bucket.staging.arn
}

output "audit_logs_bucket_name" {
  description = "Name of the audit logs S3 bucket"
  value       = aws_s3_bucket.audit_logs.bucket
}

output "audit_logs_bucket_arn" {
  description = "ARN of the audit logs S3 bucket"
  value       = aws_s3_bucket.audit_logs.arn
}

output "backup_bucket_name" {
  description = "Name of the backup S3 bucket (if created)"
  value       = var.enable_backup_bucket ? aws_s3_bucket.backup[0].bucket : null
}

# =============================================================================
# IAM Outputs
# =============================================================================

output "teradata_role_arn" {
  description = "ARN of the Teradata IAM role"
  value       = aws_iam_role.teradata_vantage.arn
}

output "teradata_role_name" {
  description = "Name of the Teradata IAM role"
  value       = aws_iam_role.teradata_vantage.name
}

output "teradata_instance_profile_name" {
  description = "Name of the Teradata instance profile"
  value       = aws_iam_instance_profile.teradata.name
}

output "yawl_engine_role_arn" {
  description = "ARN of the YAWL engine IAM role"
  value       = aws_iam_role.yawl_engine.arn
}

output "automation_role_arn" {
  description = "ARN of the automation IAM role"
  value       = aws_iam_role.automation.arn
}

# =============================================================================
# Monitoring Outputs
# =============================================================================

output "cloudwatch_log_group_teradata" {
  description = "Name of the Teradata CloudWatch log group"
  value       = aws_cloudwatch_log_group.teradata.name
}

output "cloudwatch_log_group_yawl_engine" {
  description = "Name of the YAWL engine CloudWatch log group"
  value       = aws_cloudwatch_log_group.yawl_engine.name
}

output "cloudwatch_dashboard_name" {
  description = "Name of the CloudWatch dashboard"
  value       = aws_cloudwatch_dashboard.main.dashboard_name
}

output "sns_alerts_topic_arn" {
  description = "ARN of the SNS alerts topic"
  value       = aws_sns_topic.alerts.arn
}

# =============================================================================
# Connection Strings (for YAWL Engine Configuration)
# =============================================================================

output "teradata_jdbc_url_template" {
  description = "Template for Teradata JDBC connection URL"
  value       = "jdbc:teradata://${var.teradata_console_url}/DATABASE=${var.teradata_database_name},ENCRYPTDATA=true"
  sensitive   = true
}

output "yawl_database_name" {
  description = "Name of the YAWL database"
  value       = var.teradata_database_name
}

output "yawl_staging_database_name" {
  description = "Name of the YAWL staging database"
  value       = var.teradata_staging_database_name
}

output "yawl_analytics_database_name" {
  description = "Name of the YAWL analytics database"
  value       = var.teradata_analytics_database_name
}

# =============================================================================
# Configuration Summary
# =============================================================================

output "deployment_summary" {
  description = "Summary of the deployment configuration"
  value = {
    environment         = var.environment
    aws_region          = var.aws_region
    vantage_package     = var.vantage_package
    cluster_size        = var.primary_cluster_size
    min_instances       = var.primary_min_instances
    max_instances       = var.primary_max_instances
    block_storage_tb    = var.block_storage_tb
    object_storage_tb   = var.object_storage_tb
    compute_clusters    = var.compute_clusters
  }
}
