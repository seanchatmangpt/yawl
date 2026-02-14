# Root-level Outputs for YAWL ECS Fargate Infrastructure

# VPC Outputs
output "vpc_id" {
  description = "ID of the VPC"
  value       = module.vpc.vpc_id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC"
  value       = module.vpc.vpc_cidr
}

output "public_subnet_ids" {
  description = "IDs of public subnets"
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "IDs of private subnets"
  value       = module.vpc.private_subnet_ids
}

output "nat_gateway_ips" {
  description = "Elastic IPs of NAT Gateways"
  value       = module.vpc.nat_gateway_ips
}

# RDS Outputs
output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = module.rds.db_instance_endpoint
  sensitive   = true
}

output "rds_address" {
  description = "RDS instance address"
  value       = module.rds.db_instance_address
}

output "rds_port" {
  description = "RDS instance port"
  value       = module.rds.db_instance_port
}

output "rds_database_name" {
  description = "RDS database name"
  value       = module.rds.db_name
}

output "rds_connection_string" {
  description = "Database connection string"
  value       = module.rds.db_connection_string
  sensitive   = true
}

# ECS Outputs
output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.ecs.cluster_name
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN"
  value       = module.ecs.cluster_arn
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = module.ecs.service_name
}

output "ecs_service_arn" {
  description = "ECS service ARN"
  value       = module.ecs.service_arn
}

output "ecs_task_definition_arn" {
  description = "ECS task definition ARN"
  value       = module.ecs.task_definition_arn
}

output "ecs_log_group_name" {
  description = "ECS CloudWatch log group name"
  value       = module.ecs.cloudwatch_log_group_name
}

# ALB Outputs
output "alb_dns_name" {
  description = "DNS name of the load balancer"
  value       = module.alb.alb_dns_name
}

output "alb_arn" {
  description = "ARN of the load balancer"
  value       = module.alb.alb_arn
}

output "alb_zone_id" {
  description = "Zone ID of the load balancer"
  value       = module.alb.alb_zone_id
}

output "target_group_arn" {
  description = "ARN of the target group"
  value       = module.alb.target_group_arn
}

output "target_group_name" {
  description = "Name of the target group"
  value       = module.alb.target_group_name
}

# Monitoring Outputs
output "sns_topic_arn" {
  description = "ARN of the SNS topic for alarms"
  value       = var.enable_monitoring ? module.monitoring[0].sns_topic_arn : null
}

output "cloudwatch_dashboard_url" {
  description = "URL to the CloudWatch dashboard"
  value       = var.enable_monitoring ? module.monitoring[0].cloudwatch_dashboard_url : null
}

output "composite_alarm_arn" {
  description = "ARN of the composite health alarm"
  value       = var.enable_monitoring ? module.monitoring[0].composite_alarm_arn : null
}

# Application Access Information
output "application_url" {
  description = "URL to access the YAWL application"
  value       = "http://${module.alb.alb_dns_name}"
}

output "application_url_https" {
  description = "HTTPS URL to access the YAWL application (if SSL certificate is configured)"
  value       = var.ssl_certificate_arn != null ? "https://${module.alb.alb_dns_name}" : null
}

# Useful Information
output "deployment_info" {
  description = "Summary of deployment information"
  value = {
    environment = var.environment
    region      = var.aws_region
    vpc_id      = module.vpc.vpc_id
    cluster_name = module.ecs.cluster_name
    service_name = module.ecs.service_name
    alb_dns_name = module.alb.alb_dns_name
    database_endpoint = module.rds.db_instance_address
    logs_group   = module.ecs.cloudwatch_log_group_name
  }
}
