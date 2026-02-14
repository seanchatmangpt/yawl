# RDS Module for YAWL on ECS Fargate
# Provides PostgreSQL database instance with automated backups and multi-AZ failover

# RDS Instance
resource "aws_db_instance" "main" {
  identifier            = "${var.environment}-yawl-db"
  allocated_storage     = var.allocated_storage
  db_name              = var.database_name
  engine               = "postgres"
  engine_version       = var.engine_version
  instance_class       = var.instance_class
  username             = var.master_username
  password             = var.master_password
  parameter_group_name = aws_db_parameter_group.main.name

  db_subnet_group_name   = var.db_subnet_group_name
  vpc_security_group_ids = [var.security_group_id]
  publicly_accessible    = false

  # Backup and recovery settings
  backup_retention_period = var.backup_retention_period
  backup_window           = var.backup_window
  maintenance_window      = var.maintenance_window
  multi_az                = var.multi_az

  # Storage and performance
  storage_type          = var.storage_type
  iops                  = var.storage_type == "io1" ? var.iops : null
  storage_encrypted     = true
  kms_key_id           = var.kms_key_id

  # Automated minor version upgrades
  auto_minor_version_upgrade = true

  # Logging
  enabled_cloudwatch_logs_exports = ["postgresql"]

  # Deletion protection
  deletion_protection = var.deletion_protection

  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.environment}-yawl-db-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  tags = {
    Name        = "${var.environment}-yawl-db"
    Environment = var.environment
    Project     = "YAWL"
  }
}

# DB Parameter Group
resource "aws_db_parameter_group" "main" {
  family      = "postgres${split(".", var.engine_version)[0]}"
  name        = "${var.environment}-yawl-db-params"
  description = "Custom parameter group for ${var.environment} YAWL database"

  # Performance tuning parameters
  parameter {
    name  = "max_connections"
    value = "500"
  }

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements,pgaudit"
  }

  parameter {
    name  = "log_statement"
    value = "all"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  tags = {
    Name        = "${var.environment}-yawl-db-params"
    Environment = var.environment
  }
}

# Enhanced Monitoring Role for RDS
resource "aws_iam_role" "rds_monitoring" {
  name = "${var.environment}-rds-monitoring-role"

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

  tags = {
    Name        = "${var.environment}-rds-monitoring-role"
    Environment = var.environment
  }
}

# Attach RDS Monitoring Policy
resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# Enable RDS Enhanced Monitoring
resource "aws_db_instance" "monitoring" {
  depends_on = [aws_iam_role_policy_attachment.rds_monitoring]

  db_instance_identifier    = aws_db_instance.main.id
  monitoring_interval       = var.monitoring_interval
  monitoring_role_arn       = aws_iam_role.rds_monitoring.arn
  enable_performance_insights = var.enable_performance_insights
  performance_insights_retention_period = var.performance_insights_retention

  lifecycle {
    ignore_changes = all
  }
}

# CloudWatch Alarms for RDS

# CPU Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${var.environment}-rds-cpu-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Alert when RDS CPU utilization exceeds 80%"
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.id
  }

  alarm_actions = var.alarm_actions
}

# Database Connections Alarm
resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${var.environment}-rds-db-connections"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 400
  alarm_description   = "Alert when RDS database connections exceed 400"
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.id
  }

  alarm_actions = var.alarm_actions
}

# Read Latency Alarm
resource "aws_cloudwatch_metric_alarm" "rds_read_latency" {
  alarm_name          = "${var.environment}-rds-read-latency"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "ReadLatency"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 5
  alarm_description   = "Alert when RDS read latency exceeds 5ms"
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.id
  }

  alarm_actions = var.alarm_actions
}
