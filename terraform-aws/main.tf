# YAWL ECS Fargate Infrastructure
# Main Terraform configuration file that orchestrates all modules

terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Uncomment the following block to configure remote state storage
  # backend "s3" {
  #   bucket         = "yawl-terraform-state"
  #   key            = "prod/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "terraform-locks"
  # }
}

# AWS Provider Configuration
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = var.environment
      Project     = var.project_name
      ManagedBy   = "Terraform"
      CreatedAt   = timestamp()
    }
  }
}

# Local values for common configuration
locals {
  common_tags = merge(
    var.tags,
    {
      Environment = var.environment
      Project     = var.project_name
      ManagedBy   = "Terraform"
    }
  )
}

# VPC Module
module "vpc" {
  source = "./vpc"

  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
  public_subnets    = var.public_subnets
  private_subnets   = var.private_subnets
  environment        = var.environment
}

# RDS Module
module "rds" {
  source = "./rds"

  environment           = var.environment
  db_subnet_group_name  = module.vpc.db_subnet_group_name
  security_group_id     = module.vpc.rds_security_group_id
  allocated_storage     = var.rds_allocated_storage
  instance_class        = var.rds_instance_class
  engine_version        = var.rds_engine_version
  master_username       = var.rds_master_username
  master_password       = var.rds_master_password
  multi_az              = var.rds_multi_az
  backup_retention_period = var.rds_backup_retention_period
  deletion_protection   = var.rds_deletion_protection
  alarm_actions         = var.enable_monitoring ? [module.monitoring[0].sns_topic_arn] : []

  depends_on = [module.vpc]
}

# ALB Module
module "alb" {
  source = "./alb"

  environment              = var.environment
  vpc_id                   = module.vpc.vpc_id
  public_subnet_ids        = module.vpc.public_subnet_ids
  alb_security_group_id    = module.vpc.alb_security_group_id
  target_port              = var.alb_target_port
  health_check_path        = var.health_check_path
  health_check_matcher     = var.health_check_matcher
  enable_deletion_protection = var.alb_enable_deletion_protection
  enable_access_logs       = var.alb_enable_access_logs
  access_logs_bucket       = var.alb_access_logs_bucket
  certificate_arn          = var.ssl_certificate_arn
  listener_rules           = []
  alarm_actions            = var.enable_monitoring ? [module.monitoring[0].sns_topic_arn] : []

  depends_on = [module.vpc]
}

# ECS Module
module "ecs" {
  source = "./ecs"

  environment              = var.environment
  aws_region               = var.aws_region
  container_image          = var.container_image
  container_port           = var.container_port
  task_cpu                 = var.task_cpu
  task_memory              = var.task_memory
  desired_count            = var.desired_count
  min_capacity             = var.min_capacity
  max_capacity             = var.max_capacity
  target_cpu_utilization   = var.target_cpu_utilization
  target_memory_utilization = var.target_memory_utilization
  private_subnet_ids       = module.vpc.private_subnet_ids
  ecs_security_group_id    = module.vpc.ecs_tasks_security_group_id
  target_group_arn         = module.alb.target_group_arn
  environment_variables    = var.ecs_environment_variables
  secrets                  = var.ecs_secrets
  s3_bucket_arn            = var.s3_bucket_name != "" ? "arn:aws:s3:::${var.s3_bucket_name}" : "arn:aws:s3:::yawl-bucket"
  alarm_actions            = var.enable_monitoring ? [module.monitoring[0].sns_topic_arn] : []

  depends_on = [module.vpc, module.alb, module.rds]
}

# Monitoring Module
module "monitoring" {
  count  = var.enable_monitoring ? 1 : 0
  source = "./monitoring"

  environment          = var.environment
  aws_region           = var.aws_region
  aws_account_id       = var.aws_account_id
  alarm_email          = var.alarm_email
  slack_webhook_url    = var.slack_webhook_url
  log_retention_days   = var.monitoring_log_retention_days
  error_threshold      = var.error_alarm_threshold
  include_alb_alarms   = true
  include_ecs_alarms   = true
  include_rds_alarms   = true

  depends_on = [module.vpc, module.rds, module.ecs, module.alb]
}
