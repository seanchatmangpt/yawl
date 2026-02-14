# Terraform Code Structure and Architecture

Complete documentation of the modular Terraform infrastructure for YAWL on ECS Fargate.

## Directory Structure

```
terraform-aws/
├── main.tf                    # Root module orchestrating all sub-modules
├── providers.tf               # Terraform provider configuration
├── variables.tf               # Root-level input variables
├── outputs.tf                 # Root-level outputs
├── terraform.tfvars.example   # Example configuration template
├── quickstart.sh              # Automated setup script
├── README.md                  # Main documentation
├── DEPLOYMENT.md              # Detailed deployment guide
├── STRUCTURE.md               # This file
│
├── vpc/                       # VPC Networking Module
│   ├── main.tf               # VPC, subnets, NAT, security groups
│   ├── variables.tf          # VPC module variables
│   └── outputs.tf            # VPC module outputs
│
├── rds/                       # Database Module
│   ├── main.tf               # RDS instance, parameters, monitoring
│   ├── variables.tf          # RDS module variables
│   └── outputs.tf            # RDS module outputs
│
├── ecs/                       # Container Orchestration Module
│   ├── main.tf               # ECS cluster, tasks, service, auto-scaling
│   ├── variables.tf          # ECS module variables
│   └── outputs.tf            # ECS module outputs
│
├── alb/                       # Load Balancer Module
│   ├── main.tf               # ALB, target groups, listeners
│   ├── variables.tf          # ALB module variables
│   └── outputs.tf            # ALB module outputs
│
└── monitoring/                # Monitoring and Alerts Module
    ├── main.tf               # CloudWatch dashboards, alarms, SNS
    ├── variables.tf          # Monitoring module variables
    └── outputs.tf            # Monitoring module outputs
```

## Module Dependencies

```
┌─────────────────────────────────────────────────────────┐
│                    Root Module (main.tf)                │
└─────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
    ┌────────┐          ┌─────────┐          ┌──────┐
    │  VPC   │          │   RDS   │          │ ALB  │
    │ Module │◄─────────┤ Module  │          │Module│
    └────────┘          └─────────┘          └──────┘
        │                   ▲                   ▲
        │                   │                   │
        ▼                   │                   │
    ┌──────────────────────────────────────────────────┐
    │           ECS Module                             │
    │  (depends on VPC, RDS, ALB)                      │
    └──────────────────────────────────────────────────┘
                            │
                            ▼
    ┌──────────────────────────────────────────────────┐
    │       Monitoring Module (Optional)               │
    │  (depends on VPC, RDS, ECS, ALB)                 │
    └──────────────────────────────────────────────────┘
```

## Detailed Module Description

### 1. VPC Module (`vpc/`)

**Purpose**: Establish networking foundation for the entire infrastructure

**Resources Created**:
- AWS VPC with configurable CIDR block
- Internet Gateway for public subnet routing
- Public subnets (3 AZs by default)
- Private subnets (3 AZs by default)
- NAT Gateways for private subnet outbound access
- Public route tables with IGW routes
- Private route tables with NAT routes
- Security groups for:
  - ALB (HTTP/HTTPS ingress from anywhere)
  - ECS Tasks (TCP from ALB and within VPC)
  - RDS (PostgreSQL from ECS tasks only)
- DB subnet group for RDS multi-AZ deployment

**Key Outputs**:
- VPC ID and CIDR
- Public and private subnet IDs
- Security group IDs (for other modules)
- NAT Gateway IPs (for firewall whitelisting)
- DB subnet group name

**Variables**:
- `vpc_cidr`: VPC CIDR block (default: 10.0.0.0/16)
- `availability_zones`: List of AZs to use
- `public_subnets`: CIDR blocks for public subnets
- `private_subnets`: CIDR blocks for private subnets
- `environment`: Environment name for tagging

**Configuration Example**:
```hcl
module "vpc" {
  source = "./vpc"

  vpc_cidr           = "10.0.0.0/16"
  availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]
  public_subnets    = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  private_subnets   = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]
  environment        = "prod"
}
```

### 2. RDS Module (`rds/`)

**Purpose**: Deploy and manage PostgreSQL database with high availability

**Resources Created**:
- RDS DB instance (PostgreSQL)
- Multi-AZ failover capability
- DB parameter group with optimizations
- CloudWatch alarms for:
  - CPU utilization
  - Database connections
  - Read latency
- IAM role for Enhanced Monitoring
- Performance Insights and Enhanced Monitoring

**Key Outputs**:
- DB endpoint address and port
- Database name and master username
- Connection string (sensitive)
- DB instance ARN

**Variables**:
- `allocated_storage`: Storage in GB (default: 100)
- `instance_class`: DB instance type (default: db.t3.medium)
- `engine_version`: PostgreSQL version (default: 15.3)
- `master_username`: Root user (default: yawlroot)
- `master_password`: Root password (required, min 8 chars)
- `multi_az`: Enable failover (default: true)
- `backup_retention_period`: Days to retain backups (default: 7)
- `deletion_protection`: Prevent accidental deletion (default: true)

**Configuration Example**:
```hcl
module "rds" {
  source = "./rds"

  environment           = "prod"
  db_subnet_group_name  = module.vpc.db_subnet_group_name
  security_group_id     = module.vpc.rds_security_group_id
  allocated_storage     = 100
  instance_class        = "db.t3.medium"
  engine_version        = "15.3"
  master_username       = "yawlroot"
  master_password       = "SecurePassword123"
  multi_az              = true
  alarm_actions         = [module.monitoring[0].sns_topic_arn]
}
```

### 3. ALB Module (`alb/`)

**Purpose**: Distribute traffic to ECS tasks with load balancing

**Resources Created**:
- Application Load Balancer in public subnets
- Target group for ECS tasks
- HTTP listener (with optional redirect to HTTPS)
- HTTPS listener (if SSL certificate provided)
- CloudWatch alarms for:
  - Unhealthy hosts
  - Response time
  - 5XX errors
  - Request count anomalies
- Optional S3 access logs
- Optional listener rules for path-based routing

**Key Outputs**:
- ALB DNS name
- ALB ARN and Zone ID
- Target group ARN and name
- HTTP/HTTPS listener ARNs

**Variables**:
- `vpc_id`: VPC for ALB
- `public_subnet_ids`: Subnets for ALB
- `alb_security_group_id`: ALB security group
- `target_port`: Port for targets (default: 8080)
- `health_check_path`: Health check endpoint (default: /)
- `health_check_matcher`: HTTP codes for healthy (default: 200-299)
- `certificate_arn`: SSL certificate (optional)
- `ssl_policy`: SSL policy for HTTPS (default: TLS 1.2)
- `listener_rules`: Path-based routing rules (default: empty)

**Configuration Example**:
```hcl
module "alb" {
  source = "./alb"

  environment            = "prod"
  vpc_id                 = module.vpc.vpc_id
  public_subnet_ids      = module.vpc.public_subnet_ids
  alb_security_group_id  = module.vpc.alb_security_group_id
  target_port            = 8080
  health_check_path      = "/"
  certificate_arn        = "arn:aws:acm:us-east-1:123456789012:certificate/12345"
}
```

### 4. ECS Module (`ecs/`)

**Purpose**: Orchestrate containerized YAWL application with auto-scaling

**Resources Created**:
- ECS Fargate cluster with Container Insights
- ECS task definition with:
  - Container image configuration
  - Log driver (CloudWatch)
  - Environment variables
  - Secrets from Secrets Manager
  - Resource limits (CPU, memory)
- ECS service with:
  - ALB integration
  - Private subnet placement
  - Service discovery
- CloudWatch log group
- Auto-scaling target and policies:
  - CPU-based scaling
  - Memory-based scaling
- IAM roles:
  - Task execution role (ECR, logs, secrets)
  - Task role (application permissions)
- CloudWatch alarms for:
  - CPU utilization
  - Memory utilization
  - Running task count

**Key Outputs**:
- Cluster ID, name, ARN
- Service ID, name, ARN
- Task definition ARN
- CloudWatch log group name

**Variables**:
- `container_image`: Docker image URI (required)
- `container_port`: Container port (default: 8080)
- `task_cpu`: CPU units (default: 1024)
- `task_memory`: Memory MB (default: 2048)
- `desired_count`: Initial task count (default: 2)
- `min_capacity`: Minimum tasks (default: 2)
- `max_capacity`: Maximum tasks (default: 10)
- `target_cpu_utilization`: Scale target (default: 70%)
- `target_memory_utilization`: Scale target (default: 80%)
- `environment_variables`: App environment vars
- `secrets`: Secrets Manager references
- `private_subnet_ids`: Subnets for tasks
- `ecs_security_group_id`: Task security group
- `target_group_arn`: ALB target group

**Configuration Example**:
```hcl
module "ecs" {
  source = "./ecs"

  environment            = "prod"
  aws_region             = "us-east-1"
  container_image        = "123456789012.dkr.ecr.us-east-1.amazonaws.com/yawl:latest"
  container_port         = 8080
  task_cpu               = 1024
  task_memory            = 2048
  desired_count          = 2
  private_subnet_ids     = module.vpc.private_subnet_ids
  ecs_security_group_id  = module.vpc.ecs_tasks_security_group_id
  target_group_arn       = module.alb.target_group_arn

  environment_variables = {
    LOG_LEVEL = "INFO"
  }
}
```

### 5. Monitoring Module (`monitoring/`)

**Purpose**: Provide observability and alerting for the entire infrastructure

**Resources Created**:
- SNS topic for alarm notifications
- SNS subscriptions for:
  - Email
  - Slack (webhook)
- CloudWatch dashboard with:
  - ALB metrics
  - ECS metrics
  - RDS metrics
  - Application logs
- CloudWatch log groups for:
  - Application errors
  - Custom metrics
- Log group metric filters for error counting
- Composite health alarm aggregating all resources
- ALB anomaly detector for response time
- CloudWatch alarms for:
  - Application errors
  - ALB health and performance
  - ECS resource utilization
  - RDS database performance

**Key Outputs**:
- SNS topic ARN and name
- CloudWatch dashboard URL
- Composite alarm ARN
- Error log group details
- Custom metrics log group

**Variables**:
- `alarm_email`: Email for notifications
- `slack_webhook_url`: Slack webhook URL
- `log_retention_days`: Log retention (default: 7)
- `error_threshold`: Error count threshold (default: 10)
- `include_alb_alarms`: Include ALB in composite (default: true)
- `include_ecs_alarms`: Include ECS in composite (default: true)
- `include_rds_alarms`: Include RDS in composite (default: true)

**Configuration Example**:
```hcl
module "monitoring" {
  count  = var.enable_monitoring ? 1 : 0
  source = "./monitoring"

  environment      = "prod"
  aws_region       = "us-east-1"
  aws_account_id   = data.aws_caller_identity.current.account_id
  alarm_email      = "ops@example.com"
  slack_webhook_url = "https://hooks.slack.com/services/..."
  error_threshold  = 10
}
```

## Data Flow and Communication

### Network Communication

```
Internet
  │
  ▼
[Internet Gateway]
  │
  ▼
[ALB - Public Subnets]
  │
  ├──────────────────────┐
  ▼                      │
[ECS Tasks] ◄────────────┘
  │
  ▼
[RDS - Private Subnets]
```

### Monitoring Data Flow

```
[ECS Tasks]
  │
  ├──────────────────────┬──────────────────────┐
  ▼                      ▼                      ▼
[CloudWatch Logs] [CloudWatch Metrics] [Custom Metrics]
  │                      │                      │
  └──────────────────────┼──────────────────────┘
                         ▼
              [CloudWatch Alarms]
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
      [Email]         [SNS]            [Slack]
```

## Variable Hierarchy

### Global Variables (root `variables.tf`)
- AWS region and account ID
- Environment name
- VPC CIDR and subnets
- RDS configuration
- ECS configuration
- ALB configuration
- Monitoring settings

### Module-Specific Variables
Each module has its own `variables.tf` accepting inputs from:
1. Root module (main.tf)
2. Root variables (variables.tf)
3. Module-specific requirements

### Variable Passing Example

```hcl
# Root variables.tf
variable "environment" { type = string }
variable "vpc_cidr" { type = string, default = "10.0.0.0/16" }
variable "container_image" { type = string }

# main.tf - VPC Module
module "vpc" {
  source = "./vpc"
  vpc_cidr    = var.vpc_cidr           # Pass from root
  environment = var.environment        # Pass from root
}

# main.tf - ECS Module
module "ecs" {
  source           = "./ecs"
  container_image  = var.container_image        # From root vars
  private_subnet_ids = module.vpc.private_subnet_ids  # From VPC output
  ecs_security_group_id = module.vpc.ecs_tasks_security_group_id  # From VPC
}
```

## Resource Naming Convention

All resources follow a consistent naming pattern for easy identification:

```
${environment}-${service}-${resource_type}
```

**Examples**:
- `prod-yawl-vpc`: VPC in prod environment
- `prod-yawl-cluster`: ECS cluster
- `prod-yawl-db`: RDS instance
- `prod-yawl-alb`: Application Load Balancer
- `prod-yawl-alarms`: SNS topic for alarms

## Output Hierarchy

### Module Outputs
Each module provides specific outputs for consumption by other modules:
- VPC outputs: IDs, subnet IDs, security group IDs
- RDS outputs: Endpoint, port, database name
- ALB outputs: DNS name, target group ARN
- ECS outputs: Cluster ARN, service ARN
- Monitoring outputs: SNS topic ARN, dashboard URL

### Root Outputs
The root `outputs.tf` aggregates key information for end users:
- Application URL (HTTP/HTTPS)
- Database connection information
- Cluster and service details
- Monitoring dashboard URL
- Complete deployment summary

## State Management

### Local State (Development)
```bash
# Simple local state file
terraform.tfstate
terraform.tfstate.backup
```

### Remote State (Recommended for Production)
```hcl
# main.tf or providers.tf
backend "s3" {
  bucket         = "yawl-terraform-state"
  key            = "prod/terraform.tfstate"
  region         = "us-east-1"
  encrypt        = true
  dynamodb_table = "terraform-locks"
}
```

## Default Values and Configurations

### Development Environment
```hcl
rds_instance_class = "db.t3.micro"
rds_multi_az       = false
desired_count      = 1
max_capacity       = 3
```

### Production Environment
```hcl
rds_instance_class = "db.t3.large"
rds_multi_az       = true
desired_count      = 3
max_capacity       = 20
rds_deletion_protection = true
```

## Reusability and Modularity

All modules are designed for maximum reusability:

1. **Independent Modules**: Each module can be deployed separately
2. **Configurable Resources**: All variables have sensible defaults
3. **Flexible Scaling**: Easy adjustment of capacity and thresholds
4. **Multi-Environment Support**: Support for dev, staging, prod
5. **Output-Based Integration**: Modules communicate via outputs

### Using Modules Independently

```hcl
# Deploy only VPC
module "vpc" {
  source = "./vpc"
  environment = "dev"
}

# Deploy only RDS (requires existing VPC)
module "rds" {
  source = "./rds"
  environment = "dev"
  db_subnet_group_name = aws_db_subnet_group.main.name
}
```

## Best Practices Implemented

1. **Security Groups**: Principle of least privilege with specific rules
2. **IAM Roles**: Separate execution and application roles
3. **Monitoring**: Comprehensive CloudWatch integration
4. **Auto-Scaling**: CPU and memory-based scaling
5. **High Availability**: Multi-AZ deployment
6. **Disaster Recovery**: Automated backups with retention
7. **Logging**: CloudWatch logs for all components
8. **Tagging**: Consistent tagging strategy for cost tracking
9. **Modular Design**: Loosely coupled, highly cohesive modules
10. **Documentation**: Comprehensive inline comments

## Extending the Infrastructure

### Adding a New Module

1. Create new directory: `new-service/`
2. Create files: `main.tf`, `variables.tf`, `outputs.tf`
3. Define module call in root `main.tf`
4. Add outputs to root `outputs.tf`
5. Update documentation

### Adding CloudFront CDN

```hcl
module "cdn" {
  source = "./cdn"

  alb_domain_name = module.alb.alb_dns_name
  environment     = var.environment
}
```

### Adding RDS Read Replica

```hcl
resource "aws_db_instance" "read_replica" {
  identifier          = "${var.environment}-yawl-db-replica"
  replicate_source_db = module.rds.db_instance_id
  # ... additional configuration
}
```

## Maintenance and Updates

### Updating Terraform Version

1. Update `required_version` in `providers.tf`
2. Run `terraform init`
3. Run `terraform plan` to check compatibility
4. Apply updates carefully

### Updating AWS Provider

1. Update provider version in `providers.tf`
2. Test in development first
3. Review breaking changes
4. Apply in production after validation

## Performance Considerations

- **ECS Task Startup**: Cold start ~30 seconds
- **RDS Multi-AZ**: Failover takes 1-2 minutes
- **ALB Health Check**: Interval 30 seconds, threshold 2
- **Auto-Scaling**: Scale up ~2 min, scale down ~5 min
- **Terraform Operations**: Init 30-60 sec, plan 1-2 min, apply 5-15 min

## Cost Optimization Strategies

1. Use Fargate Spot for non-critical workloads
2. Right-size RDS instance type for workload
3. Enable log retention to manage storage
4. Use health check efficiently
5. Implement auto-scaling with appropriate thresholds

---

For detailed information on each module, see the individual module README files and main documentation.
