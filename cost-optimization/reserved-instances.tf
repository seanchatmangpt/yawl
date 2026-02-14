# YAWL Cost Optimization - Reserved Instances Configuration
# This Terraform configuration manages AWS Reserved Instances to optimize costs

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "us-east-1"
}

variable "enable_reserved_instances" {
  description = "Enable reserved instance purchases"
  type        = bool
  default     = true
}

variable "reserved_instance_term" {
  description = "RI term in months (12 or 36)"
  type        = number
  default     = 12
  validation {
    condition     = contains([12, 36], var.reserved_instance_term)
    error_message = "RI term must be 12 or 36 months."
  }
}

# Data source to get available Reserved Instance offerings
data "aws_ec2_reserved_instance_offerings" "compute" {
  count             = var.enable_reserved_instances ? 1 : 0
  filter {
    name   = "instance-type"
    values = ["t3.xlarge", "c5.2xlarge", "r5.xlarge", "m5.xlarge"]
  }

  filter {
    name   = "location"
    values = [var.aws_region]
  }

  filter {
    name   = "offering-type"
    values = ["All Upfront"]
  }

  filter {
    name   = "duration"
    values = [tostring(var.reserved_instance_term * 3600 * 730)]
  }
}

# Purchase Reserved Instances for API servers
resource "aws_ec2_reserved_instances" "api_servers" {
  count              = var.enable_reserved_instances ? 1 : 0
  instance_type      = "t3.xlarge"
  offering_id        = try(data.aws_ec2_reserved_instance_offerings.compute[0].offerings[0].offering_id, "")
  quantity           = 2
  instance_family    = "t3"
  availability_zone  = "${var.aws_region}a"
  offering_type      = "All Upfront"

  tags = {
    Name        = "yawl-api-servers-ri"
    Environment = var.environment
    Purpose     = "cost-optimization"
    Component   = "api-server"
  }
}

# Purchase Reserved Instances for worker nodes
resource "aws_ec2_reserved_instances" "workers" {
  count              = var.enable_reserved_instances ? 1 : 0
  instance_type      = "c5.2xlarge"
  offering_id        = try(data.aws_ec2_reserved_instance_offerings.compute[0].offerings[1].offering_id, "")
  quantity           = 3
  instance_family    = "c5"
  availability_zone  = "${var.aws_region}a"
  offering_type      = "All Upfront"

  tags = {
    Name        = "yawl-workers-ri"
    Environment = var.environment
    Purpose     = "cost-optimization"
    Component   = "worker"
  }
}

# Purchase Reserved Instances for database nodes
resource "aws_ec2_reserved_instances" "database" {
  count              = var.enable_reserved_instances ? 1 : 0
  instance_type      = "r5.xlarge"
  offering_id        = try(data.aws_ec2_reserved_instance_offerings.compute[0].offerings[2].offering_id, "")
  quantity           = 2
  instance_family    = "r5"
  availability_zone  = "${var.aws_region}a"
  offering_type      = "All Upfront"

  tags = {
    Name        = "yawl-database-ri"
    Environment = var.environment
    Purpose     = "cost-optimization"
    Component   = "database"
  }
}

# Savings Plans for additional flexibility
resource "aws_ec2_reserved_instances" "flex_compute" {
  count              = var.enable_reserved_instances ? 1 : 0
  instance_type      = "m5.xlarge"
  offering_id        = try(data.aws_ec2_reserved_instance_offerings.compute[0].offerings[3].offering_id, "")
  quantity           = 2
  instance_family    = "m5"
  availability_zone  = "${var.aws_region}a"
  offering_type      = "All Upfront"

  tags = {
    Name        = "yawl-flex-compute-ri"
    Environment = var.environment
    Purpose     = "cost-optimization"
    Component   = "flexible"
  }
}

# Auto Scaling Group for EC2 instances using Reserved Instances
resource "aws_launch_template" "yawl_api" {
  name_prefix   = "yawl-api-"
  image_id      = data.aws_ami.ubuntu.id
  instance_type = "t3.xlarge"

  monitoring {
    enabled = true
  }

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name        = "yawl-api-instance"
      Environment = var.environment
    }
  }
}

resource "aws_autoscaling_group" "yawl_api" {
  name                = "yawl-api-asg"
  vpc_zone_identifier = [aws_subnet.main.id]
  launch_template {
    id      = aws_launch_template.yawl_api.id
    version = "$Latest"
  }

  min_size         = 1
  max_size         = 5
  desired_capacity = 2

  tag {
    key                 = "Name"
    value               = "yawl-api-asg-instance"
    propagate_at_launch = true
  }

  tag {
    key                 = "Environment"
    value               = var.environment
    propagate_at_launch = true
  }
}

# Data source for latest Ubuntu AMI
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}

# Placeholder VPC and Subnet (should be sourced from main infrastructure)
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "yawl-vpc"
    Environment = var.environment
  }
}

resource "aws_subnet" "main" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true

  tags = {
    Name        = "yawl-subnet"
    Environment = var.environment
  }
}

# CloudWatch alarms to monitor RI utilization
resource "aws_cloudwatch_metric_alarm" "ri_underutilized" {
  alarm_name          = "yawl-ri-underutilized"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 3600
  statistic           = "Average"
  threshold           = 20
  alarm_description   = "Alert when reserved instances are underutilized"
  treat_missing_data  = "notBreaching"

  tags = {
    Environment = var.environment
    Purpose     = "cost-optimization"
  }
}

# Output summary
output "reserved_instances_summary" {
  description = "Summary of purchased reserved instances"
  value = var.enable_reserved_instances ? {
    api_servers = try(aws_ec2_reserved_instances.api_servers[0].id, null)
    workers     = try(aws_ec2_reserved_instances.workers[0].id, null)
    database    = try(aws_ec2_reserved_instances.database[0].id, null)
    flex        = try(aws_ec2_reserved_instances.flex_compute[0].id, null)
  } : {}
}

output "estimated_monthly_savings" {
  description = "Estimated monthly savings from RIs (approximate)"
  value       = var.enable_reserved_instances ? "~$2,500 USD" : "RIs not enabled"
}

output "autoscaling_group_info" {
  description = "Auto Scaling Group information"
  value = {
    name              = aws_autoscaling_group.yawl_api.name
    min_size          = aws_autoscaling_group.yawl_api.min_size
    max_size          = aws_autoscaling_group.yawl_api.max_size
    desired_capacity  = aws_autoscaling_group.yawl_api.desired_capacity
  }
}
