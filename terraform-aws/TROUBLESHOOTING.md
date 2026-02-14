# Troubleshooting Guide for YAWL ECS Fargate Infrastructure

Comprehensive troubleshooting guide for common issues and their solutions.

## Table of Contents

1. [Terraform Issues](#terraform-issues)
2. [VPC and Networking](#vpc-and-networking)
3. [RDS Database](#rds-database)
4. [ECS Containers](#ecs-containers)
5. [Load Balancer](#load-balancer)
6. [Monitoring and Logs](#monitoring-and-logs)
7. [Performance Issues](#performance-issues)
8. [Security Issues](#security-issues)

## Terraform Issues

### Issue: "terraform init" fails

**Symptoms**:
```
Error: Failed to query available provider packages
```

**Solutions**:

1. Check internet connectivity:
```bash
ping registry.terraform.io
```

2. Check Terraform version:
```bash
terraform version
# Should be >= 1.0
```

3. Clear Terraform cache:
```bash
rm -rf .terraform
terraform init
```

4. Force provider download:
```bash
terraform init -upgrade
```

### Issue: "terraform plan" shows unexpected resource changes

**Symptoms**:
```
Plan: 5 to add, 3 to change, 2 to destroy
# When expecting no changes
```

**Solutions**:

1. Check for drift in state:
```bash
terraform refresh
terraform plan
```

2. Compare actual vs planned state:
```bash
terraform plan -out=tfplan
terraform show tfplan
```

3. Investigate specific resource:
```bash
terraform state show 'module.ecs.aws_ecs_service.main'
```

4. Refresh remote state:
```bash
terraform state pull > backup.tfstate
terraform state push backup.tfstate
```

### Issue: "terraform apply" hangs or times out

**Symptoms**:
```
# Apply running for > 30 minutes
```

**Solutions**:

1. Check AWS API rate limits:
```bash
# Monitor in CloudTrail or CloudWatch
aws cloudtrail lookup-events --max-results 10
```

2. Increase operation timeout:
```bash
# In provider block
provider "aws" {
  max_retries = 5
  skip_requesting_account_id = false
}
```

3. Apply specific module instead:
```bash
terraform apply -target=module.vpc
```

4. Check for locked resources:
```bash
# If using remote state with DynamoDB locks
aws dynamodb scan --table-name terraform-locks
```

### Issue: "error: resource already exists"

**Symptoms**:
```
Error: error creating security group: InvalidGroup.Duplicate
```

**Solutions**:

1. Import existing resource:
```bash
terraform import aws_security_group.existing sg-12345
```

2. Check for orphaned resources:
```bash
aws ec2 describe-security-groups --filters "Name=group-name,Values=*yawl*"
```

3. Update state to reference existing:
```bash
terraform state rm 'module.vpc.aws_security_group.alb'
terraform import 'module.vpc.aws_security_group.alb' sg-12345
```

### Issue: "InvalidParameterValue" errors

**Symptoms**:
```
Error: Error creating RDS instance: InvalidParameterValue: Invalid value
```

**Solutions**:

1. Validate variable values:
```bash
terraform validate
```

2. Check parameter compatibility:
```bash
# For RDS, check CPU/memory combinations
# For ECS, check valid instance types
aws ec2 describe-instance-types --query 'InstanceTypes[0]'
```

3. Review Terraform documentation for resource:
```bash
# Check AWS provider docs for valid values
```

## VPC and Networking

### Issue: "No route to host" errors

**Symptoms**:
```
Cannot reach RDS from ECS task
ECS service cannot reach ALB
```

**Solutions**:

1. Check route tables:
```bash
# List route tables
aws ec2 describe-route-tables \
  --filters "Name=vpc-id,Values=$(terraform output -raw vpc_id)"

# Check routes
aws ec2 describe-route-tables --route-table-ids rtb-12345 \
  --query 'RouteTables[0].Routes'
```

2. Verify NAT Gateway status:
```bash
aws ec2 describe-nat-gateways \
  --filter "Name=state,Values=available"
```

3. Check security group rules:
```bash
# ALB security group should allow 80/443
aws ec2 describe-security-groups \
  --group-ids sg-12345 \
  --query 'SecurityGroups[0].IpPermissions'

# ECS security group should allow from ALB
aws ec2 describe-security-groups \
  --group-ids sg-ecs \
  --query 'SecurityGroups[0].IpPermissions'
```

4. Test connectivity:
```bash
# From ECS task, test DNS resolution
nslookup rds.example.amazonaws.com

# Test port connectivity
nc -zv rds.example.amazonaws.com 5432
```

### Issue: NAT Gateway not working

**Symptoms**:
```
ECS tasks cannot reach internet
Error downloading container images
```

**Solutions**:

1. Check NAT Gateway status:
```bash
aws ec2 describe-nat-gateways \
  --query 'NatGateways[*].[NatGatewayId,State]'
```

2. Verify Elastic IP is attached:
```bash
aws ec2 describe-nat-gateways \
  --nat-gateway-ids ngw-12345 \
  --query 'NatGateways[0].NatGatewayAddresses'
```

3. Check route table associations:
```bash
aws ec2 describe-route-table-associations \
  --filters "Name=subnet-id,Values=subnet-12345"
```

4. Recreate NAT Gateway if needed:
```bash
terraform destroy -target=module.vpc.aws_nat_gateway.main
terraform apply -target=module.vpc.aws_nat_gateway.main
```

### Issue: Subnet cannot communicate with each other

**Symptoms**:
```
Cross-AZ communication failures
```

**Solutions**:

1. Check Network ACLs:
```bash
aws ec2 describe-network-acls \
  --filters "Name=vpc-id,Values=$(terraform output -raw vpc_id)" \
  --query 'NetworkAcls[*].Entries'
```

2. Verify subnet CIDR blocks don't overlap:
```bash
terraform output public_subnet_ids
terraform output private_subnet_ids
```

3. Check VPC peering/endpoints:
```bash
aws ec2 describe-vpc-peering-connections
aws ec2 describe-vpc-endpoints
```

## RDS Database

### Issue: "cannot connect to database"

**Symptoms**:
```
Error: connection refused
psql: error: could not translate host name
```

**Solutions**:

1. Verify RDS instance is running:
```bash
aws rds describe-db-instances \
  --db-instance-identifier $(terraform output -raw db_instance_id) \
  --query 'DBInstances[0].[DBInstanceStatus,Endpoint]'
```

2. Test DNS resolution:
```bash
nslookup $(terraform output -raw rds_address)
```

3. Verify security group rules:
```bash
SG_ID=$(terraform output -raw rds_security_group_id)
aws ec2 describe-security-groups --group-ids $SG_ID \
  --query 'SecurityGroups[0].IpPermissions'
```

4. Check from ECS task:
```bash
TASK_ID=$(aws ecs list-tasks --cluster $(terraform output -raw cluster_name) \
  --query 'taskArns[0]' --output text | cut -d'/' -f3)

aws ecs execute-command \
  --cluster $(terraform output -raw cluster_name) \
  --task $TASK_ID \
  --container yawl \
  --interactive \
  --command "/bin/sh -c 'apt-get update && apt-get install -y postgresql-client && psql -h <RDS_HOST> -U yawlroot -c \"SELECT 1\"'"
```

### Issue: "too many connections" error

**Symptoms**:
```
error: remaining connection slots are reserved
```

**Solutions**:

1. Check connection count:
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name DatabaseConnections \
  --dimensions Name=DBInstanceIdentifier,Value=$(terraform output -raw db_instance_id) \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum
```

2. Increase max connections:
```bash
# Update parameter group
aws rds modify-db-parameter-group \
  --db-parameter-group-name $(terraform output -raw db_parameter_group_id) \
  --parameters "ParameterName=max_connections,ParameterValue=1000,ApplyMethod=immediate"
```

3. Configure connection pooling in application:
```hcl
# In ecs_environment_variables
DB_POOL_SIZE = "20"
DB_POOL_TIMEOUT = "30"
```

4. Restart RDS instance:
```bash
aws rds reboot-db-instance \
  --db-instance-identifier $(terraform output -raw db_instance_id)
```

### Issue: "database disk is full"

**Symptoms**:
```
Error: no space left on device
Cannot write to database
```

**Solutions**:

1. Check disk usage:
```bash
aws rds describe-db-instances \
  --db-instance-identifier $(terraform output -raw db_instance_id) \
  --query 'DBInstances[0].[AllocatedStorage,FreeStorageSpace]'
```

2. Increase allocated storage:
```bash
terraform apply -var="rds_allocated_storage=200"
```

3. Clean up old logs and backups:
```bash
# Connect to database and run maintenance
# VACUUM ANALYZE;
# REINDEX DATABASE;
```

### Issue: Backup or snapshot failures

**Symptoms**:
```
Backup failed: insufficient storage
```

**Solutions**:

1. Check backup status:
```bash
aws rds describe-db-snapshots \
  --db-instance-identifier $(terraform output -raw db_instance_id) \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,Status,CreateTime]'
```

2. Verify backup window:
```bash
terraform output -raw deployment_info | grep -A5 "database"
```

3. Check storage:
```bash
aws rds describe-db-instances \
  --db-instance-identifier $(terraform output -raw db_instance_id) \
  --query 'DBInstances[0].[StorageType,AllocatedStorage]'
```

## ECS Containers

### Issue: "Tasks stuck in PROVISIONING state"

**Symptoms**:
```
Service shows desiredCount != runningCount
Tasks not transitioning to RUNNING
```

**Solutions**:

1. Check task details:
```bash
TASK_ID=$(aws ecs list-tasks --cluster $(terraform output -raw cluster_name) \
  --query 'taskArns[0]' --output text | cut -d'/' -f3)

aws ecs describe-tasks \
  --cluster $(terraform output -raw cluster_name) \
  --tasks $TASK_ID \
  --query 'tasks[0].[taskArn,lastStatus,stoppedCode,stoppedReason,containers[0].lastStatus]'
```

2. Check CloudWatch logs:
```bash
aws logs tail $(terraform output -raw cloudwatch_log_group_name) --follow
```

3. Increase task memory/CPU:
```bash
terraform apply -var="task_memory=4096" -var="task_cpu=2048"
```

4. Check for insufficient capacity:
```bash
aws ecs describe-services \
  --cluster $(terraform output -raw cluster_name) \
  --services $(terraform output -raw service_name) \
  --query 'services[0].events[0:5]'
```

### Issue: "Image pull error"

**Symptoms**:
```
CannotPullContainerImage
Failed to download image
```

**Solutions**:

1. Verify image exists in ECR:
```bash
aws ecr describe-images \
  --repository-name yawl \
  --query 'imageDetails[*].[imageTags,imageSizeInBytes]'
```

2. Check IAM permissions for ECR:
```bash
# Verify task execution role has ECR permissions
aws iam get-role-policy \
  --role-name $(terraform output -raw ecs_task_execution_role_arn | rev | cut -d'/' -f1 | rev) \
  --policy-name "ecrPullPolicy"
```

3. Verify image URI in task definition:
```bash
aws ecs describe-task-definition \
  --task-definition $(terraform output -raw task_definition_family) \
  --query 'taskDefinition.containerDefinitions[0].image'
```

4. Check ECR credentials:
```bash
# Verify task role can access ECR
aws ecr get-authorization-token --region us-east-1
```

### Issue: "Out of memory" errors

**Symptoms**:
```
Container exit code 137
OOMKilled: true
```

**Solutions**:

1. Increase task memory:
```bash
terraform apply -var="task_memory=4096"
```

2. Check memory usage:
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ServiceName,Value=$(terraform output -raw service_name) \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum
```

3. Optimize application memory usage:
```bash
# Add to ecs_environment_variables
JAVA_OPTS = "-Xms512m -Xmx1024m"
```

### Issue: "Application crashing immediately"

**Symptoms**:
```
Rapid task restarts
lastStatus keeps changing from RUNNING to STOPPED
```

**Solutions**:

1. Check application logs:
```bash
aws logs tail $(terraform output -raw cloudwatch_log_group_name) \
  --follow \
  --format short
```

2. Check container command/entrypoint:
```bash
aws ecs describe-task-definition \
  --task-definition $(terraform output -raw task_definition_family) \
  --query 'taskDefinition.containerDefinitions[0].[command,entryPoint]'
```

3. Add health check:
```bash
# Verify health check is responsive
curl -v http://localhost:8080/
```

4. Check environment variables:
```bash
aws ecs describe-task-definition \
  --task-definition $(terraform output -raw task_definition_family) \
  --query 'taskDefinition.containerDefinitions[0].environment'
```

## Load Balancer

### Issue: "Targets showing unhealthy"

**Symptoms**:
```
TargetHealth.State = unhealthy
TargetHealth.Reason = "Target.ResponseCodeMismatch"
```

**Solutions**:

1. Check target health:
```bash
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw target_group_arn) \
  --query 'TargetHealthDescriptions[*].[Target.Id,TargetHealth.State,TargetHealth.Reason]'
```

2. Verify health check configuration:
```bash
aws elbv2 describe-target-groups \
  --target-group-arns $(terraform output -raw target_group_arn) \
  --query 'TargetGroups[0].[HealthCheckPath,HealthCheckProtocol,HealthCheckIntervalSeconds,HealthCheckTimeoutSeconds,HealthyThresholdCount]'
```

3. Test endpoint manually:
```bash
curl -v http://TASK_IP:8080/
```

4. Check security groups allow ALB:
```bash
aws ec2 describe-security-groups \
  --group-ids $(terraform output -raw ecs_tasks_security_group_id) \
  --query 'SecurityGroups[0].IpPermissions' | grep -A5 "8080"
```

5. Update health check settings:
```bash
terraform apply -var="health_check_matcher=200-399"
```

### Issue: "503 Service Unavailable"

**Symptoms**:
```
All requests return 503
No available targets
```

**Solutions**:

1. Check registered targets:
```bash
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw target_group_arn)
```

2. Verify ECS service is running:
```bash
aws ecs describe-services \
  --cluster $(terraform output -raw cluster_name) \
  --services $(terraform output -raw service_name) \
  --query 'services[0].[desiredCount,runningCount,deployments]'
```

3. Check ALB health:
```bash
aws elbv2 describe-load-balancers \
  --load-balancer-arns $(terraform output -raw alb_arn) \
  --query 'LoadBalancers[0].State'
```

4. Scale up ECS service:
```bash
terraform apply -var="desired_count=3"
```

### Issue: "Slow response times"

**Symptoms**:
```
Requests taking > 5 seconds
High latency from ALB
```

**Solutions**:

1. Check response time metrics:
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name TargetResponseTime \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,p99 \
  --dimensions Name=LoadBalancer,Value=$(terraform output -raw alb_arn | cut -d':' -f6)
```

2. Check ECS CPU/Memory:
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=$(terraform output -raw service_name) \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum
```

3. Check database performance:
```bash
aws rds describe-db-instances \
  --db-instance-identifier $(terraform output -raw db_instance_id) \
  --query 'DBInstances[0].[LatestRestorableTime,PreferredBackupWindow]'
```

4. Scale horizontally:
```bash
terraform apply -var="desired_count=5"
```

## Monitoring and Logs

### Issue: "No logs appearing"

**Symptoms**:
```
CloudWatch log group is empty
No application output
```

**Solutions**:

1. Verify log group exists:
```bash
aws logs describe-log-groups \
  --log-group-name-prefix /ecs/
```

2. Check log configuration in task definition:
```bash
aws ecs describe-task-definition \
  --task-definition $(terraform output -raw task_definition_family) \
  --query 'taskDefinition.containerDefinitions[0].logConfiguration'
```

3. Verify IAM permissions:
```bash
# Task execution role should have logs:CreateLogGroup and logs:PutLogEvents
aws iam get-role-policy \
  --role-name $(terraform output -raw ecs_task_execution_role_arn | rev | cut -d'/' -f1 | rev) \
  --policy-name "cloudwatchLogPolicy"
```

4. Force log group recreation:
```bash
terraform destroy -target=module.ecs.aws_cloudwatch_log_group.ecs
terraform apply
```

### Issue: "Alarms not triggering"

**Symptoms**:
```
High CPU but alarm not firing
Alarm state is INSUFFICIENT_DATA
```

**Solutions**:

1. Check alarm state:
```bash
aws cloudwatch describe-alarms \
  --alarm-names $(terraform output -raw cluster_name)-ecs-cpu-utilization
```

2. Verify metrics are being published:
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=$(terraform output -raw service_name) \
  --start-time $(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 \
  --statistics Average
```

3. Check SNS topic subscriptions:
```bash
aws sns list-subscriptions-by-topic \
  --topic-arn $(terraform output -raw sns_topic_arn)
```

4. Send test notification:
```bash
aws sns publish \
  --topic-arn $(terraform output -raw sns_topic_arn) \
  --subject "Test Alarm" \
  --message "This is a test message"
```

### Issue: "Dashboard not loading"

**Symptoms**:
```
CloudWatch dashboard shows no data
404 when accessing dashboard URL
```

**Solutions**:

1. Verify dashboard exists:
```bash
aws cloudwatch list-dashboards | grep yawl
```

2. Describe dashboard:
```bash
aws cloudwatch get-dashboard \
  --dashboard-name $(terraform output -raw environment)-yawl-dashboard
```

3. Check metrics namespace:
```bash
aws cloudwatch list-metrics --namespace "AWS/ECS"
```

4. Recreate dashboard:
```bash
terraform destroy -target=module.monitoring.aws_cloudwatch_dashboard.main
terraform apply -target=module.monitoring.aws_cloudwatch_dashboard.main
```

## Performance Issues

### Issue: "Slow Terraform operations"

**Symptoms**:
```
terraform plan takes > 5 minutes
terraform apply takes > 20 minutes
```

**Solutions**:

1. Check AWS API throttling:
```bash
# Add logging to Terraform
TF_LOG=DEBUG terraform plan
```

2. Use targeted operations:
```bash
terraform plan -target=module.vpc
terraform plan -target=module.rds
```

3. Increase parallelism:
```bash
terraform apply -parallelism=20
```

4. Check network connectivity:
```bash
ping registry.terraform.io
traceroute api.us-east-1.amazonaws.com
```

### Issue: "Application startup time increasing"

**Symptoms**:
```
Cold start taking > 60 seconds
Health checks timing out
```

**Solutions**:

1. Check task startup logs:
```bash
aws logs tail $(terraform output -raw cloudwatch_log_group_name) \
  --follow \
  --start-time "5 minutes ago"
```

2. Increase health check timeout:
```bash
terraform apply -var="health_check_timeout=30"
```

3. Pre-warm connections:
```bash
# Add connection pooling to application
# Add database init scripts
```

4. Check container image size:
```bash
aws ecr describe-images \
  --repository-name yawl \
  --query 'imageDetails[0].imageSizeInBytes'
```

## Security Issues

### Issue: "Cannot pull from ECR"

**Symptoms**:
```
AccessDenied when pulling image
Unauthorized: authentication required
```

**Solutions**:

1. Check IAM policy:
```bash
aws iam list-role-policies \
  --role-name $(terraform output -raw ecs_task_execution_role_arn | rev | cut -d'/' -f1 | rev)
```

2. Verify repository policy:
```bash
aws ecr get-repository-policy --repository-name yawl
```

3. Add necessary permissions:
```bash
# Ensure AmazonECSTaskExecutionRolePolicy is attached
aws iam attach-role-policy \
  --role-name $(terraform output -raw ecs_task_execution_role_arn | rev | cut -d'/' -f1 | rev) \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

### Issue: "Database password exposure"

**Symptoms**:
```
Password in logs or error messages
Password in Terraform state
```

**Solutions**:

1. Rotate password:
```bash
aws rds modify-db-instance \
  --db-instance-identifier $(terraform output -raw db_instance_id) \
  --master-user-password "NewSecurePassword123" \
  --apply-immediately
```

2. Use Secrets Manager:
```bash
# Store password in AWS Secrets Manager
aws secretsmanager create-secret \
  --name yawl-rds-password \
  --secret-string "NewSecurePassword123"
```

3. Protect state file:
```bash
# Use S3 with encryption and versioning
# Use DynamoDB for state locking
```

---

For additional help, refer to:
- AWS CloudWatch Logs Insights
- AWS Systems Manager Session Manager for troubleshooting
- AWS X-Ray for tracing
- CloudTrail for audit logs
