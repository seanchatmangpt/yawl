# YAWL Workflow Engine - AWS Security Requirements

## Overview

This document outlines the security requirements and controls for YAWL Workflow Engine on AWS Marketplace, aligned with the AWS Well-Architected Framework Security Pillar.

---

## Table of Contents

1. [Security Foundations](#1-security-foundations)
2. [Identity and Access Management](#2-identity-and-access-management)
3. [Detection and Monitoring](#3-detection-and-monitoring)
4. [Infrastructure Protection](#4-infrastructure-protection)
5. [Data Protection](#5-data-protection)
6. [Incident Response](#6-incident-response)
7. [Application Security](#7-application-security)
8. [Compliance Requirements](#8-compliance-requirements)

---

## 1. Security Foundations

### 1.1 Shared Responsibility Model

| AWS Responsibilities | YAWL Responsibilities |
|---------------------|----------------------|
| Physical security | Application security |
| Hypervisor security | Data classification |
| Network infrastructure | Identity management |
| AWS service security | Encryption configuration |
| Compliance (infrastructure) | Compliance (application) |

### 1.2 Security Governance

#### Security Account Structure

```
AWS Organization
+-- Management Account (Root)
|   +-- Security Account (Audit, Security Hub)
|   +-- Log Archive Account
+-- Production OU
|   +-- YAWL Production Account
+-- Development OU
    +-- YAWL Development Account
```

#### Security Controls

| Control | Implementation | Status |
|---------|---------------|--------|
| AWS Organizations | Service control policies (SCPs) | Required |
| AWS Control Tower | Guardrails for compliance | Recommended |
| AWS Security Hub | Centralized security findings | Required |
| AWS Config | Configuration compliance | Required |
| AWS CloudTrail | API activity logging | Required |

### 1.3 Account Security

#### Root Account Protection

```bash
# Enable MFA on root account
# Delete root access keys
# Use root only for initial setup

# Verify root MFA
aws iam get-account-summary --query 'SummaryMap.Users'
```

#### Account Separation

- Separate accounts for production and non-production
- Use AWS Organizations for centralized management
- Implement Service Control Policies (SCPs)

---

## 2. Identity and Access Management

### 2.1 Identity Management

#### SEC02-BP01: Use Strong Sign-in Mechanisms

| Requirement | Implementation |
|-------------|----------------|
| MFA | Required for all IAM users |
| Password Policy | Min 14 chars, complexity required |
| Session Duration | Max 12 hours for console, 1 hour for API |

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "RequireMFA",
      "Effect": "Deny",
      "Action": "*",
      "Resource": "*",
      "Condition": {
        "BoolIfExists": {
          "aws:MultiFactorAuthPresent": "false"
        }
      }
    }
  ]
}
```

#### SEC02-BP02: Use Temporary Credentials

- Use AWS STS for temporary credentials
- Implement IAM roles for EC2/EKS workloads
- Use OIDC federation for Kubernetes service accounts

```yaml
# EKS Pod Identity with OIDC
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine
  namespace: yawl
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/YAWLEngineRole
```

#### SEC02-BP03: Store and Use Secrets Securely

| Secret Type | Storage Solution | Rotation |
|-------------|-----------------|----------|
| Database credentials | AWS Secrets Manager | 30 days |
| API keys | AWS Secrets Manager | 90 days |
| TLS certificates | AWS Certificate Manager | Auto-renewal |
| SSH keys | AWS EC2 Key Pairs | Manual |
| Application secrets | AWS Systems Manager Parameter Store | As needed |

```bash
# Create database secret with rotation
aws secretsmanager create-secret \
  --name yawl/database/production \
  --description "YAWL database credentials" \
  --secret-string '{"username":"yawl_app","password":"SECURE_PASSWORD"}'

# Enable automatic rotation
aws secretsmanager rotate-secret \
  --secret-id yawl/database/production \
  --rotation-lambda-arn arn:aws:lambda:REGION:ACCOUNT:function:SecretRotation \
  --rotation-rules AutomaticallyAfterDays=30
```

### 2.2 Permissions Management

#### SEC03-BP02: Grant Least Privilege Access

##### YAWL Application Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowS3Access",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::yawl-data-${ACCOUNT}",
        "arn:aws:s3:::yawl-data-${ACCOUNT}/*"
      ]
    },
    {
      "Sid": "AllowSecretsAccess",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:*:${ACCOUNT}:secret:yawl/*"
    },
    {
      "Sid": "AllowKMSDecrypt",
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt",
        "kms:GenerateDataKey"
      ],
      "Resource": "arn:aws:kms:*:${ACCOUNT}:key/*",
      "Condition": {
        "StringEquals": {
          "kms:ViaService": [
            "s3.*.amazonaws.com",
            "secretsmanager.*.amazonaws.com"
          ]
        }
      }
    }
  ]
}
```

##### AWS Marketplace Integration Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowMeteringService",
      "Effect": "Allow",
      "Action": [
        "aws-marketplace:BatchMeterUsage",
        "aws-marketplace:MeterUsage",
        "aws-marketplace:ResolveCustomer"
      ],
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "aws-marketplace:ProductCode": "${PRODUCT_CODE}"
        }
      }
    },
    {
      "Sid": "AllowEntitlementService",
      "Effect": "Allow",
      "Action": [
        "aws-marketplace:GetEntitlements"
      ],
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "aws-marketplace:ProductCode": "${PRODUCT_CODE}"
        }
      }
    }
  ]
}
```

#### SEC03-BP05: Define Permission Guardrails

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DenyAllExceptRequired",
      "Effect": "Deny",
      "NotAction": [
        "s3:GetObject",
        "s3:PutObject",
        "secretsmanager:GetSecretValue",
        "kms:Decrypt",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "*"
    }
  ]
}
```

---

## 3. Detection and Monitoring

### 3.1 SEC04-BP01: Configure Service and Application Logging

#### CloudTrail Configuration

```bash
# Create multi-region trail
aws cloudtrail create-trail \
  --name yawl-security-trail \
  --s3-bucket-name yawl-audit-logs \
  --include-global-service-events \
  --is-multi-region-trail \
  --enable-log-file-validation

# Enable data events for S3
aws cloudtrail put-event-selectors \
  --trail-name yawl-security-trail \
  --event-selectors '[{
    "ReadWriteType": "All",
    "IncludeManagementEvents": true,
    "DataResources": [{
      "Type": "AWS::S3::Object",
      "Values": ["arn:aws:s3:::yawl-data-*/*"]
    }]
  }]'
```

#### CloudWatch Logs Configuration

```yaml
# Kubernetes Fluent Bit configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
  namespace: amazon-cloudwatch
data:
  fluent-bit.conf: |
    [SERVICE]
      Flush         5
      Log_Level     info
      Daemon        off

    [INPUT]
      Name              tail
      Tag               application.*
      Path              /var/log/containers/*.log
      Docker_Mode       On
      Docker_Mode_Flush 5

    [FILTER]
      Name                kubernetes
      Match               application.*
      Kube_URL            https://kubernetes.default.svc:443

    [OUTPUT]
      Name                cloudwatch_logs
      Match               application.*
      region              ${AWS_REGION}
      log_group_name      /aws/eks/yawl-cluster/application
      log_stream_prefix   yawl-
```

### 3.2 SEC04-BP02: Capture Security Findings

#### Security Hub Configuration

```bash
# Enable Security Hub
aws securityhub enable-security-hub \
  --enable-default-standards

# Enable standards
aws securityhub batch-enable-standards \
  --standards-subscription-requests '[{
    "StandardsArn": "arn:aws:securityhub:::standards/aws-foundational-security-best-practices/v/1.0.0"
  }, {
    "StandardsArn": "arn:aws:securityhub:::standards/pci-dss/v/3.2.1"
  }]'
```

#### AWS Config Rules

```yaml
# CloudFormation snippet for Config Rules
Resources:
  # Ensure RDS encryption
  RDSEncryptionConfigRule:
    Type: AWS::Config::ConfigRule
    Properties:
      ConfigRuleName: yawl-rds-encrypted
      Description: Check if RDS instances are encrypted
      Source:
        Owner: AWS
        SourceIdentifier: RDS_STORAGE_ENCRYPTED
      Scope:
        ComplianceResourceTypes:
          - AWS::RDS::DBInstance

  # Ensure S3 bucket encryption
  S3EncryptionConfigRule:
    Type: AWS::Config::ConfigRule
    Properties:
      ConfigRuleName: yawl-s3-encrypted
      Description: Check if S3 buckets have encryption enabled
      Source:
        Owner: AWS
        SourceIdentifier: S3_BUCKET_SERVER_SIDE_ENCRYPTION_ENABLED
      Scope:
        ComplianceResourceTypes:
          - AWS::S3::Bucket
```

### 3.3 CloudWatch Alarms

```yaml
# Security-focused CloudWatch Alarms
Resources:
  # Unauthorized API calls
  UnauthorizedApiAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: yawl-unauthorized-api-calls
      AlarmDescription: Detect unauthorized API calls
      MetricName: UnauthorizedAPICalls
      Namespace: CloudTrailMetrics
      Statistic: Sum
      Period: 300
      EvaluationPeriods: 1
      Threshold: 1
      ComparisonOperator: GreaterThanOrEqualToThreshold
      TreatMissingData: notBreaching

  # Root account usage
  RootAccountUsageAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: yawl-root-account-usage
      AlarmDescription: Alert on root account usage
      MetricName: RootAccountUsage
      Namespace: CloudTrailMetrics
      Statistic: Sum
      Period: 300
      EvaluationPeriods: 1
      Threshold: 1
      ComparisonOperator: GreaterThanOrEqualToThreshold
      TreatMissingData: notBreaching

  # IAM policy changes
  IamPolicyChangeAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: yawl-iam-policy-changes
      AlarmDescription: Alert on IAM policy changes
      MetricName: IAMPolicyChanges
      Namespace: CloudTrailMetrics
      Statistic: Sum
      Period: 300
      EvaluationPeriods: 1
      Threshold: 1
      ComparisonOperator: GreaterThanOrEqualToThreshold
      TreatMissingData: notBreaching
```

---

## 4. Infrastructure Protection

### 4.1 SEC05-BP01: Create Network Layers

#### VPC Architecture

```
VPC (10.0.0.0/16)
+-- Public Tier (10.0.0.0/20)
|   +-- ALB Subnets (AZ-a, AZ-b)
|   +-- NAT Gateway (AZ-a, AZ-b)
|
+-- Application Tier (10.0.64.0/18)
|   +-- EKS Node Subnets (AZ-a, AZ-b, AZ-c)
|
+-- Data Tier (10.0.128.0/20)
|   +-- RDS Subnets (AZ-a, AZ-b)
|
+-- Management Tier (10.0.192.0/20)
    +-- Bastion Subnets (if needed)
```

#### Security Group Rules

```yaml
# ALB Security Group
ALBSecurityGroup:
  Type: AWS::EC2::SecurityGroup
  Properties:
    GroupDescription: ALB Security Group
    SecurityGroupIngress:
      - IpProtocol: tcp
        FromPort: 443
        ToPort: 443
        CidrIp: 0.0.0.0/0
        Description: HTTPS from internet
      - IpProtocol: tcp
        FromPort: 80
        ToPort: 80
        CidrIp: 0.0.0.0/0
        Description: HTTP redirect to HTTPS

# EKS Node Security Group
NodeSecurityGroup:
  Type: AWS::EC2::SecurityGroup
  Properties:
    GroupDescription: EKS Node Security Group
    SecurityGroupIngress:
      - IpProtocol: tcp
        FromPort: 1025
        ToPort: 65535
        SourceSecurityGroupId: !Ref ALBSecurityGroup
        Description: Node ports from ALB
      - IpProtocol: tcp
        FromPort: 443
        ToPort: 443
        SourceSecurityGroupId: !Ref ClusterSecurityGroup
        Description: HTTPS from cluster

# RDS Security Group
RDSSecurityGroup:
  Type: AWS::EC2::SecurityGroup
  Properties:
    GroupDescription: RDS Security Group
    SecurityGroupIngress:
      - IpProtocol: tcp
        FromPort: 5432
        ToPort: 5432
        SourceSecurityGroupId: !Ref NodeSecurityGroup
        Description: PostgreSQL from EKS nodes
```

### 4.2 SEC06-BP01: Perform Vulnerability Management

#### EKS Security Best Practices

```bash
# Install AWS EKS Security Best Practices
kubectl apply -f https://raw.githubusercontent.com/aws/aws-eks-best-practices/master/checks/eks-security-checks.yaml

# Run security scanner
kubectl run security-scanner --image=aquasec/trivy:latest --rm -it --restart=Never -- \
  trivy image --severity HIGH,CRITICAL yawl/engine:5.2.0
```

#### Amazon Inspector

```bash
# Enable Amazon Inspector
aws inspector2 enable \
  --account-ids ${AWS_ACCOUNT_ID} \
  --resource-types EC2 ECR LAMBDA

# List findings
aws inspector2 list-findings \
  --filter-criteria '{"severity": [{"comparison": "EQUALS", "value": "HIGH"}]}'
```

---

## 5. Data Protection

### 5.1 SEC07-BP01: Data Classification

| Classification | Examples | Controls |
|---------------|----------|----------|
| **Confidential** | Customer PII, credentials | Encrypt at rest and in transit, strict access |
| **Internal** | Workflow specifications, logs | Encrypt at rest, role-based access |
| **Public** | Documentation, examples | No encryption required |

### 5.2 SEC08-BP01: Secure Key Management

#### KMS Key Configuration

```yaml
Resources:
  YAWLEncryptionKey:
    Type: AWS::KMS::Key
    Properties:
      Description: YAWL encryption key
      EnableKeyRotation: true
      KeyPolicy:
        Version: '2012-10-17'
        Statement:
          - Sid: Enable IAM User Permissions
            Effect: Allow
            Principal:
              AWS: !Sub 'arn:aws:iam::${AWS::AccountId}:root'
            Action: 'kms:*'
            Resource: '*'
          - Sid: Allow Service Roles
            Effect: Allow
            Principal:
              AWS:
                - !GetAtt EKSClusterRole.Arn
                - !GetAtt RDSRole.Arn
            Action:
              - kms:Decrypt
              - kms:Encrypt
              - kms:GenerateDataKey*
              - kms:DescribeKey
            Resource: '*'
```

### 5.3 SEC08-BP02: Enforce Encryption at Rest

| Service | Encryption Method | Key Type |
|---------|------------------|----------|
| RDS | AES-256 | Customer Managed Key (CMK) |
| S3 | SSE-KMS | Customer Managed Key (CMK) |
| EBS | AES-256 | Customer Managed Key (CMK) |
| EKS Secrets | envelope encryption | Customer Managed Key (CMK) |
| CloudWatch Logs | SSE-KMS | Customer Managed Key (CMK) |

### 5.4 SEC09-BP02: Enforce Encryption in Transit

#### TLS Configuration

```yaml
# ALB HTTPS Listener with TLS 1.2+
HTTPSListener:
  Type: AWS::ElasticLoadBalancingV2::Listener
  Properties:
    Protocol: HTTPS
    Port: 443
    SslPolicy: ELBSecurityPolicy-TLS13-1-2-2021-06
    Certificates:
      - CertificateArn: !Ref CertificateArn
```

#### RDS SSL Configuration

```properties
# YAWL database connection configuration
database.ssl=true
database.ssl.mode=verify-full
database.ssl.root.cert=/opt/yawl/certs/rds-ca-cert.pem
```

---

## 6. Incident Response

### 6.1 SEC10-BP02: Develop Incident Management Plans

#### Incident Response Playbook

| Incident Type | Severity | Response Time | Escalation |
|--------------|----------|---------------|------------|
| Data breach | Critical | 15 min | CISO, Legal |
| Unauthorized access | High | 1 hour | Security Team Lead |
| Malware detection | High | 1 hour | Security Team |
| Vulnerability disclosure | Medium | 4 hours | Development Team |
| Policy violation | Low | 24 hours | Compliance Team |

### 6.2 SEC10-BP03: Prepare Forensic Capabilities

```bash
# Enable forensic capabilities
# 1. Preserve CloudTrail logs
aws cloudtrail start-logging --name yawl-security-trail

# 2. Create EBS snapshot for forensics
aws ec2 create-snapshot \
  --volume-id vol-12345678 \
  --description "Forensic snapshot - $(date +%Y%m%d)"

# 3. Capture memory (if needed)
# Use EC2 Serial Console or Systems Manager

# 4. Isolate compromised resources
aws ec2 modify-instance-attribute \
  --instance-id i-12345678 \
  --groups sg-isolated
```

### 6.3 Incident Response Automation

```yaml
# Lambda function for automated response
Resources:
  SecurityIncidentFunction:
    Type: AWS::Lambda::Function
    Properties:
      FunctionName: yawl-security-incident-response
      Handler: index.handler
      Runtime: python3.11
      Role: !GetAtt IncidentResponseRole.Arn
      Environment:
        Variables:
          SNS_TOPIC: !Ref SecurityAlertTopic
      Code:
        ZipFile: |
          import json
          import boto3
          import os

          def handler(event, context):
              sns = boto3.client('sns')

              # Parse security finding
              finding = event.get('detail', {}).get('findings', [{}])[0]
              severity = finding.get('Severity', {}).get('Label', 'UNKNOWN')
              title = finding.get('Title', 'Unknown Finding')

              # Send alert
              sns.publish(
                  TopicArn=os.environ['SNS_TOPIC'],
                  Subject=f'[YAWL Security Alert] {severity}: {title}',
                  Message=json.dumps(finding, indent=2)
              )

              # Take automated action based on severity
              if severity in ['CRITICAL', 'HIGH']:
                  # Implement automated remediation
                  pass

              return {'statusCode': 200, 'body': 'Processed'}
```

---

## 7. Application Security

### 7.1 SEC11-BP02: Automate Security Testing

#### CI/CD Security Gates

```yaml
# GitHub Actions security workflow
name: Security Scan
on: [push, pull_request]

jobs:
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: 'yawl/engine:${{ github.sha }}'
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'

      - name: Upload Trivy scan results
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: 'trivy-results.sarif'

      - name: Run SAST
        uses: github/codeql-action/analyze@v2
        with:
          languages: java

      - name: Run dependency check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'YAWL'
          path: '.'
          format: 'ALL'
```

### 7.2 SEC11-BP03: Perform Penetration Testing

| Test Type | Frequency | Scope |
|-----------|-----------|-------|
| Infrastructure | Annual | All AWS resources |
| Application | Quarterly | YAWL APIs, web console |
| API Security | Quarterly | Interface A/B/E/X |
| Social Engineering | Annual | Staff testing |

### 7.3 Input Validation

```java
// Java input validation example
public class InputValidator {

    private static final Pattern SAFE_STRING = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+$");
    private static final int MAX_STRING_LENGTH = 255;

    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        if (input.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Input exceeds maximum length");
        }
        if (!SAFE_STRING.matcher(input).matches()) {
            throw new IllegalArgumentException("Input contains invalid characters");
        }
        return input.trim();
    }

    public static void validateXmlInput(String xml) {
        // Prevent XXE attacks
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // ... parse XML
    }
}
```

---

## 8. Compliance Requirements

### 8.1 SOC 2 Type II

| Control | Implementation | Evidence |
|---------|---------------|----------|
| CC6.1 Logical Access | IAM policies, MFA | IAM policy documents |
| CC6.6 Security of Transmission | TLS 1.2+ | ALB configuration |
| CC6.7 Protection of Data | Encryption at rest | KMS key policies |
| CC7.1 Vulnerability Management | Amazon Inspector | Scan reports |
| CC7.2 Anomaly Detection | CloudWatch, GuardDuty | Alert configuration |

### 8.2 GDPR Compliance

| Requirement | Implementation |
|-------------|---------------|
| Data Minimization | Collect only necessary data |
| Right to Access | Customer data export API |
| Right to Erasure | Data deletion workflow |
| Data Portability | Standard export formats |
| Breach Notification | 72-hour notification process |

### 8.3 HIPAA (Optional Add-on)

| Requirement | Implementation |
|-------------|---------------|
| Access Controls | Role-based access, audit logs |
| Encryption | PHI encrypted at rest and in transit |
| Audit Controls | CloudTrail, CloudWatch Logs |
| Integrity Controls | Checksums, versioning |
| Transmission Security | TLS 1.2+, encrypted connections |

---

## Security Checklist

### Pre-Launch

- [ ] MFA enabled on all accounts
- [ ] Least privilege IAM policies implemented
- [ ] All data encrypted at rest (RDS, S3, EBS)
- [ ] TLS 1.2+ enforced on all connections
- [ ] CloudTrail enabled for all regions
- [ ] Security Hub enabled with relevant standards
- [ ] WAF rules configured on ALB
- [ ] Vulnerability scanning enabled
- [ ] Incident response plan documented
- [ ] Security training completed for team

### Ongoing

- [ ] Monthly security reviews
- [ ] Quarterly penetration testing
- [ ] Annual compliance audits
- [ ] Continuous vulnerability scanning
- [ ] Regular access reviews
- [ ] Security awareness training
- [ ] Patch management process
- [ ] Backup verification

---

*Document Version: 1.0*
*Last Updated: February 2025*
*Next Review: May 2025*
