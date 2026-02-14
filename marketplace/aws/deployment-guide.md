# YAWL Workflow Engine - AWS Marketplace Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying YAWL Workflow Engine on AWS through the AWS Marketplace. Follow these instructions to set up a production-ready, highly available YAWL environment.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Architecture Overview](#2-architecture-overview)
3. [Deployment Steps](#3-deployment-steps)
4. [Post-Deployment Configuration](#4-post-deployment-configuration)
5. [Verification and Testing](#5-verification-and-testing)
6. [Operational Procedures](#6-operational-procedures)
7. [Troubleshooting](#7-troubleshooting)
8. [Cost Estimation](#8-cost-estimation)

---

## 1. Prerequisites

### 1.1 AWS Account Requirements

| Requirement | Details |
|-------------|---------|
| AWS Account | Active account in good standing |
| IAM Permissions | Administrator or PowerUser access for deployment |
| Service Quotas | Sufficient quotas for EKS, RDS, and ALB |
| Region Support | us-east-1, us-west-2, eu-west-1, eu-central-1, ap-southeast-1, ap-northeast-1 |

### 1.2 Required Tools

```bash
# Install AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Configure AWS CLI
aws configure

# Install kubectl
curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Install eksctl
curl --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# Install helm
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
chmod 700 get_helm.sh
./get_helm.sh
```

### 1.3 Network Requirements

Ensure your VPC has the following:
- At least 2 public subnets in different Availability Zones
- At least 2 private subnets in different Availability Zones
- NAT Gateway for private subnet internet access
- DNS resolution enabled
- DHCP options set with domain name

---

## 2. Architecture Overview

### 2.1 High-Level Architecture

```
                                    +-------------------+
                                    |   Route 53 /      |
                                    |   CloudFront      |
                                    |   (Optional)      |
                                    +--------+----------+
                                             |
                                             | HTTPS
                                             v
+-------------------+                +-------+--------+
|   AWS WAF         |<---------------| Application    |
|   (Protection)    |                | Load Balancer  |
+-------------------+                +-------+--------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +--------+-------+       +--------+-------+       +--------+-------+
           |  EKS Node 1     |       |  EKS Node 2     |       |  EKS Node N     |
           |  (AZ-1)         |       |  (AZ-2)         |       |  (AZ-N)         |
           +--------+-------+       +--------+-------+       +--------+-------+
                    |                        |                        |
                    +------------------------+------------------------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +--------+-------+       +--------+-------+       +--------+-------+
           | YAWL Engine     |       | YAWL Resource   |       | YAWL Worklet    |
           | Pod             |       | Service Pod     |       | Service Pod     |
           +--------+-------+       +--------+-------+       +--------+-------+
                    |                        |                        |
                    +------------------------+------------------------+
                                             |
                                             v
                                    +--------+-------+
                                    |  RDS            |
                                    |  PostgreSQL     |
                                    |  (Multi-AZ)     |
                                    +--------+-------+
                                             |
                                             v
                                    +--------+-------+
                                    |  S3 Bucket      |
                                    |  (Persistence)  |
                                    +----------------+
```

### 2.2 Component Responsibilities

| Component | Purpose | Scaling |
|-----------|---------|---------|
| Application Load Balancer | SSL termination, traffic distribution | AWS Managed |
| EKS Cluster | Container orchestration | 2-10 nodes (auto-scaling) |
| YAWL Engine | Core workflow execution | 2-5 pods (HPA) |
| YAWL Resource Service | Resource allocation | 2-3 pods |
| YAWL Worklet Service | Dynamic adaptation | 2-3 pods |
| RDS PostgreSQL | State persistence | Multi-AZ (2 instances) |
| S3 Bucket | File storage, specs, logs | AWS Managed |

---

## 3. Deployment Steps

### 3.1 Step 1: Subscribe via AWS Marketplace

1. Navigate to AWS Marketplace
2. Search for "YAWL Workflow Engine"
3. Select the appropriate pricing tier
4. Click "Subscribe"
5. You will be redirected to the YAWL registration page

### 3.2 Step 2: Create VPC and Network Infrastructure

```bash
# Set environment variables
export AWS_REGION="us-east-1"
export STACK_NAME="yawl-network"
export VPC_CIDR="10.0.0.0/16"

# Deploy network stack
aws cloudformation create-stack \
  --stack-name ${STACK_NAME} \
  --template-body file://cloudformation/network.yaml \
  --parameters \
      ParameterKey=VpcCidr,ParameterValue=${VPC_CIDR} \
      ParameterKey=Environment,ParameterValue=production \
  --capabilities CAPABILITY_IAM \
  --region ${AWS_REGION}

# Wait for stack completion
aws cloudformation wait stack-create-complete \
  --stack-name ${STACK_NAME} \
  --region ${AWS_REGION}

# Export outputs
VPC_ID=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --query 'Stacks[0].Outputs[?OutputKey==`VpcId`].OutputValue' \
  --output text \
  --region ${AWS_REGION})

PUBLIC_SUBNETS=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --query 'Stacks[0].Outputs[?OutputKey==`PublicSubnetIds`].OutputValue' \
  --output text \
  --region ${AWS_REGION})

PRIVATE_SUBNETS=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --query 'Stacks[0].Outputs[?OutputKey==`PrivateSubnetIds`].OutputValue' \
  --output text \
  --region ${AWS_REGION})
```

### 3.3 Step 3: Deploy IAM Roles and Policies

```bash
# Deploy IAM stack
aws cloudformation create-stack \
  --stack-name yawl-iam \
  --template-body file://cloudformation/yawl-iam-policies.yaml \
  --parameters \
      ParameterKey=ProjectName,ParameterValue=yawl \
      ParameterKey=Environment,ParameterValue=production \
      ParameterKey=ProductCode,ParameterValue=${MARKETPLACE_PRODUCT_CODE} \
  --capabilities CAPABILITY_NAMED_IAM \
  --region ${AWS_REGION}
```

### 3.4 Step 4: Deploy EKS Cluster

```bash
# Deploy EKS cluster
aws cloudformation create-stack \
  --stack-name yawl-eks \
  --template-body file://cloudformation/yawl-eks-cluster.yaml \
  --parameters \
      ParameterKey=ClusterName,ParameterValue=yawl-cluster \
      ParameterKey=KubernetesVersion,ParameterValue=1.28 \
      ParameterKey=VpcId,ParameterValue=${VPC_ID} \
      ParameterKey=PrivateSubnetIds,ParameterValue="${PRIVATE_SUBNETS}" \
      ParameterKey=NodeInstanceType,ParameterValue=m6i.xlarge \
      ParameterKey=NodeGroupMinSize,ParameterValue=2 \
      ParameterKey=NodeGroupMaxSize,ParameterValue=10 \
      ParameterKey=NodeGroupDesiredSize,ParameterValue=3 \
      ParameterKey=KeyPairName,ParameterValue=your-key-pair \
  --capabilities CAPABILITY_IAM \
  --region ${AWS_REGION}

# Wait for EKS cluster (takes 15-20 minutes)
aws cloudformation wait stack-create-complete \
  --stack-name yawl-eks \
  --region ${AWS_REGION}

# Update kubectl config
aws eks update-kubeconfig \
  --name yawl-cluster \
  --region ${AWS_REGION}

# Verify cluster
kubectl get nodes
```

### 3.5 Step 5: Deploy RDS PostgreSQL

```bash
# Generate secure password
DB_PASSWORD=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24)

# Store password in parameter store temporarily
aws ssm put-parameter \
  --name "/yawl/db-password-temp" \
  --value "${DB_PASSWORD}" \
  --type SecureString \
  --region ${AWS_REGION}

# Get database subnet IDs
DB_SUBNETS=$(aws cloudformation describe-stacks \
  --stack-name yawl-network \
  --query 'Stacks[0].Outputs[?OutputKey==`DatabaseSubnetIds`].OutputValue' \
  --output text \
  --region ${AWS_REGION})

# Deploy RDS stack
aws cloudformation create-stack \
  --stack-name yawl-rds \
  --template-body file://cloudformation/yawl-rds-database.yaml \
  --parameters \
      ParameterKey=DatabaseInstanceIdentifier,ParameterValue=yawl-database \
      ParameterKey=DatabaseEngine,ParameterValue=postgres \
      ParameterKey=DatabaseEngineVersion,ParameterValue=15.7 \
      ParameterKey=DatabaseInstanceClass,ParameterValue=db.r6g.xlarge \
      ParameterKey=DatabaseStorageType,ParameterValue=gp3 \
      ParameterKey=DatabaseAllocatedStorage,ParameterValue=100 \
      ParameterKey=DatabaseMasterUsername,ParameterValue=yawldbadmin \
      ParameterKey=DatabaseMasterPassword,ParameterValue=${DB_PASSWORD} \
      ParameterKey=VpcId,ParameterValue=${VPC_ID} \
      ParameterKey=DatabaseSubnetIds,ParameterValue="${DB_SUBNETS}" \
      ParameterKey=MultiAZ,ParameterValue=true \
  --capabilities CAPABILITY_IAM \
  --region ${AWS_REGION}

# Wait for RDS (takes 10-15 minutes)
aws cloudformation wait stack-create-complete \
  --stack-name yawl-rds \
  --region ${AWS_REGION}
```

### 3.6 Step 6: Deploy Application Load Balancer

```bash
# Get ALB subnet IDs (public subnets)
ALB_SUBNETS="${PUBLIC_SUBNETS}"

# Get certificate ARN (create one if needed)
CERT_ARN=$(aws acm list-certificates \
  --query 'CertificateSummaryList[0].CertificateArn' \
  --output text \
  --region ${AWS_REGION})

# Deploy ALB stack
aws cloudformation create-stack \
  --stack-name yawl-alb \
  --template-body file://cloudformation/yawl-alb-loadbalancer.yaml \
  --parameters \
      ParameterKey=LoadBalancerName,ParameterValue=yawl-alb \
      ParameterKey=Scheme,ParameterValue=internet-facing \
      ParameterKey=VpcId,ParameterValue=${VPC_ID} \
      ParameterKey=PublicSubnetIds,ParameterValue="${ALB_SUBNETS}" \
      ParameterKey=CertificateArn,ParameterValue=${CERT_ARN} \
      ParameterKey=DomainName,ParameterValue=yawl.yourcompany.com \
  --capabilities CAPABILITY_IAM \
  --region ${AWS_REGION}

# Wait for ALB
aws cloudformation wait stack-create-complete \
  --stack-name yawl-alb \
  --region ${AWS_REGION}
```

### 3.7 Step 7: Install AWS Load Balancer Controller

```bash
# Add EKS chart repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=yawl-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set region=${AWS_REGION} \
  --set vpcId=${VPC_ID}

# Verify installation
kubectl get deployment -n kube-system aws-load-balancer-controller
```

### 3.8 Step 8: Deploy YAWL Application

```bash
# Create YAWL namespace
kubectl create namespace yawl

# Create Kubernetes secrets
kubectl create secret generic yawl-db-credentials \
  --from-literal=username=yawldbadmin \
  --from-literal=password=${DB_PASSWORD} \
  --namespace=yawl

# Get connection strings
DB_ENDPOINT=$(aws cloudformation describe-stacks \
  --stack-name yawl-rds \
  --query 'Stacks[0].Outputs[?OutputKey==`DatabaseEndpoint`].OutputValue' \
  --output text \
  --region ${AWS_REGION})

# Create ConfigMap for YAWL
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl
data:
  DATABASE_HOST: "${DB_ENDPOINT}"
  DATABASE_PORT: "5432"
  DATABASE_NAME: "yawl"
  LOG_LEVEL: "INFO"
  JAVA_OPTS: "-Xms1g -Xmx2g"
EOF

# Deploy YAWL using Helm
helm repo add yawl https://yawlfoundation.github.io/yawl/helm
helm repo update

helm install yawl yawl/yawl \
  --namespace yawl \
  --set image.tag=5.2.0 \
  --set replicaCount=2 \
  --set resources.requests.cpu=500m \
  --set resources.requests.memory=1Gi \
  --set resources.limits.cpu=2000m \
  --set resources.limits.memory=4Gi \
  --set ingress.enabled=true \
  --set ingress.className=alb \
  --set ingress.annotations."alb\.ingress\.kubernetes\.io/scheme"=internet-facing \
  --set ingress.annotations."alb\.ingress\.kubernetes\.io/target-type"=ip \
  --set ingress.annotations."alb\.ingress\.kubernetes\.io/healthcheck-path"=/yawl/health

# Verify deployment
kubectl get pods -n yawl
kubectl get services -n yawl
kubectl get ingress -n yawl
```

---

## 4. Post-Deployment Configuration

### 4.1 Configure Autoscaling

```bash
# Install Metrics Server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Create Horizontal Pod Autoscaler
kubectl autoscale deployment yawl-engine \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n yawl

# Verify HPA
kubectl get hpa -n yawl
```

### 4.2 Configure CloudWatch Logging

```bash
# Install Fluent Bit for logging
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml

# Verify logging
kubectl logs -n amazon-cloudwatch -l app.kubernetes.io/name=fluent-bit
```

### 4.3 Initialize YAWL Database

```bash
# Get YAWL engine pod
YAWL_POD=$(kubectl get pods -n yawl -l app=yawl-engine -o jsonpath='{.items[0].metadata.name}')

# Run database initialization
kubectl exec -n yawl ${YAWL_POD} -- /opt/yawl/bin/init-database.sh

# Verify database
kubectl exec -n yawl ${YAWL_POD} -- /opt/yawl/bin/check-database.sh
```

### 4.4 Configure S3 for Persistence

```bash
# Get bucket name
BUCKET_NAME=$(aws cloudformation describe-stacks \
  --stack-name yawl-eks \
  --query 'Stacks[0].Outputs[?OutputKey==`DataBucketName`].OutputValue' \
  --output text \
  --region ${AWS_REGION})

# Create directory structure
aws s3api put-object --bucket ${BUCKET_NAME} --key specifications/
aws s3api put-object --bucket ${BUCKET_NAME} --key logs/
aws s3api put-object --bucket ${BUCKET_NAME} --key exports/
aws s3api put-object --bucket ${BUCKET_NAME} --key worklets/

# Update YAWL configuration
kubectl set env deployment/yawl-engine \
  YAWL_S3_BUCKET=${BUCKET_NAME} \
  -n yawl
```

---

## 5. Verification and Testing

### 5.1 Health Check Verification

```bash
# Get ALB DNS name
ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name yawl-alb \
  --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerDNSName`].OutputValue' \
  --output text \
  --region ${AWS_REGION})

# Test health endpoint
curl -k https://${ALB_DNS}/yawl/health

# Expected response
# {"status":"healthy","version":"5.2.0","timestamp":"2025-01-15T10:00:00Z"}
```

### 5.2 API Endpoint Verification

```bash
# Test API Interface B (Client)
curl -k -X GET "https://${ALB_DNS}/yawl/ws/interfaceB/1.0" \
  -H "Content-Type: application/xml"

# Test Engine Status
curl -k -X GET "https://${ALB_DNS}/yawl/engine/status"
```

### 5.3 Database Connectivity Test

```bash
# Get database secret
DB_SECRET_ARN=$(aws cloudformation describe-stacks \
  --stack-name yawl-rds \
  --query 'Stacks[0].Outputs[?OutputKey==`DatabaseSecretArn`].OutputValue' \
  --output text \
  --region ${AWS_REGION})

# Test connection from pod
kubectl run pg-test --rm -it --image=postgres:15 --restart=Never -n yawl -- \
  psql "postgresql://yawldbadmin:${DB_PASSWORD}@${DB_ENDPOINT}:5432/yawl" -c "SELECT version();"
```

### 5.4 Workflow Execution Test

```bash
# Upload test specification
kubectl exec -n yawl ${YAWL_POD} -- \
  curl -X POST "http://localhost:8080/yawl/ws/interfaceA/1.0" \
  -H "Content-Type: application/xml" \
  -d @/opt/yawl/samples/OrderFulfillment.yawl

# Launch test case
kubectl exec -n yawl ${YAWL_POD} -- \
  curl -X POST "http://localhost:8080/yawl/ws/interfaceB/1.0" \
  -H "Content-Type: application/xml" \
  -d '<launchCase xmlns="http://www.yawlfoundation.org/yawl"><specID>OrderFulfillment</specID></launchCase>'
```

---

## 6. Operational Procedures

### 6.1 Scaling the Cluster

```bash
# Scale node group
aws eks update-nodegroup-config \
  --cluster-name yawl-cluster \
  --nodegroup-name yawl-workers \
  --scaling-config desiredSize=5,minSize=2,maxSize=10 \
  --region ${AWS_REGION}

# Scale YAWL pods
kubectl scale deployment yawl-engine --replicas=5 -n yawl
```

### 6.2 Backup Procedures

```bash
# Create RDS snapshot
aws rds create-db-snapshot \
  --db-instance-identifier yawl-database \
  --db-snapshot-identifier yawl-backup-$(date +%Y%m%d) \
  --region ${AWS_REGION}

# Backup S3 data
aws s3 sync s3://${BUCKET_NAME} s3://${BUCKET_NAME}-backup/$(date +%Y%m%d)/
```

### 6.3 Log Collection

```bash
# Collect application logs
kubectl logs -n yawl -l app=yawl-engine --since=1h > yawl-engine-logs.txt

# Collect CloudWatch logs
aws logs filter-log-events \
  --log-group-name /aws/eks/yawl-cluster/cluster \
  --start-time $(date -d '1 hour ago' +%s)000 \
  --region ${AWS_REGION}
```

### 6.4 Monitoring Dashboard

Access CloudWatch dashboard:
```
https://console.aws.amazon.com/cloudwatch/home?region=${AWS_REGION}#dashboards:name=yawl-dashboard
```

---

## 7. Troubleshooting

### 7.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| Pods not starting | ImagePullBackOff | Check ECR permissions, verify image exists |
| Database connection failed | Connection refused | Verify security groups, check credentials |
| ALB health checks failing | 502 errors | Check pod health, verify target registration |
| High latency | Slow API responses | Check pod resources, verify RDS performance |

### 7.2 Diagnostic Commands

```bash
# Check pod status
kubectl describe pod -n yawl -l app=yawl-engine

# Check pod logs
kubectl logs -n yawl -l app=yawl-engine --tail=100

# Check events
kubectl get events -n yawl --sort-by='.lastTimestamp'

# Check ALB targets
aws elbv2 describe-target-health \
  --target-group-arn ${TARGET_GROUP_ARN} \
  --region ${AWS_REGION}

# Check RDS status
aws rds describe-db-instances \
  --db-instance-identifier yawl-database \
  --region ${AWS_REGION}
```

### 7.3 Support Contacts

- **Technical Support**: support@yawlfoundation.org
- **AWS Marketplace Support**: aws-marketplace-seller-ops@amazon.com
- **Documentation**: https://yawlfoundation.github.io/yawl/

---

## 8. Cost Estimation

### 8.1 Monthly Cost Breakdown (Production)

| Component | Configuration | Estimated Monthly Cost |
|-----------|--------------|----------------------|
| EKS Cluster | 1 control plane | $73.00 |
| EKS Nodes | 3 x m6i.xlarge | $350.00 |
| RDS Multi-AZ | db.r6g.xlarge | $420.00 |
| RDS Storage | 100 GB GP3 | $23.00 |
| Application Load Balancer | 1 ALB | $25.00 |
| Data Transfer | 500 GB | $45.00 |
| S3 Storage | 100 GB | $2.30 |
| CloudWatch Logs | 10 GB | $5.00 |
| **Total** | | **~$943/month** |

### 8.2 Cost Optimization Tips

1. Use Reserved Instances for steady-state workloads
2. Enable S3 Intelligent-Tiering for log storage
3. Use Spot Instances for non-critical workloads
4. Right-size instances based on actual usage
5. Enable Cost Explorer for cost tracking

---

## Appendix A: Quick Reference Commands

```bash
# Get all stack outputs
aws cloudformation describe-stacks --query 'Stacks[?starts_with(StackName, `yawl`)]'

# Update kubeconfig
aws eks update-kubeconfig --name yawl-cluster --region ${AWS_REGION}

# View all YAWL resources
kubectl get all -n yawl

# Port forward for local testing
kubectl port-forward -n yawl svc/yawl-engine 8080:8080

# Emergency scale down
kubectl scale deployment -n yawl --replicas=1 --all

# Emergency scale up
kubectl scale deployment -n yawl --replicas=5 yawl-engine
```

---

*Document Version: 1.0*
*Last Updated: February 2025*
