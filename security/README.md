# YAWL Security Directory

Comprehensive security policies and templates for HIPAA, SOC2, and PCI-DSS compliance.

**Last Updated**: 2026-02-14
**Compliance Frameworks**: HIPAA, SOC2 Type II, PCI-DSS 3.2.1

---

## Directory Structure

```
security/
├── README.md                           # This file
├── security-policy.yaml                # Core security policy (HIPAA, SOC2, PCI-DSS)
├── network-policies.yaml               # Kubernetes network policies
├── rbac-policies.yaml                  # Role-based access control policies
├── pod-security-standards.yaml         # Pod security and resource controls
├── compliance-checklist.md             # Comprehensive compliance checklist
└── iam-templates/                      # Cloud IAM templates
    ├── aws-iam-policy.json             # AWS IAM policy
    ├── aws-iam-role.yaml               # AWS IAM role definitions
    ├── gcp-iam-policy.yaml             # GCP IAM roles and bindings
    ├── azure-iam-policy.json           # Azure RBAC role definitions
    ├── azure-iam-roles.yaml            # Azure policies and assignments
    ├── gke-workload-identity.yaml      # GKE Workload Identity setup
    ├── aks-pod-identity.yaml           # AKS Pod Identity setup
    └── eks-irsa.yaml                   # EKS IRSA (IAM Roles for Service Accounts)
```

---

## File Descriptions

### 1. security-policy.yaml
**Purpose**: Master security policy defining HIPAA, SOC2, and PCI-DSS requirements

**Key Controls**:
- Authentication (HIPAA §164.312(a)(2)(i))
- Authorization (PCI-DSS 7)
- Encryption (HIPAA §164.312(a)(2)(iv))
- Audit logging (HIPAA §164.312(b))
- Data protection (HIPAA §164.308(a)(3))
- Network security (PCI-DSS 1)
- Incident response (HIPAA §164.308(a)(6))
- Vendor management (PCI-DSS 12.8)
- Vulnerability management (PCI-DSS 6.2)
- Access control (SOC2 CC6)

**Compliance Requirements**:
- MFA: Required for all users
- Password Policy: 14 chars, complex, 90-day rotation
- Encryption: AES-256-GCM at rest, TLS 1.3 in transit
- Log Retention: 7 years (HIPAA requirement)
- Key Rotation: 90 days

---

### 2. network-policies.yaml
**Purpose**: Kubernetes NetworkPolicy resources for zero-trust networking

**Policies Included**:
1. **default-deny-ingress**: Deny all inbound traffic by default (HIPAA §164.312(a)(1))
2. **default-deny-egress**: Restrict outbound traffic (PCI-DSS 1.3)
3. **allow-monitoring-ingress**: Enable monitoring access (HIPAA §164.312(b))
4. **allow-internal-communication**: Permit app-to-database traffic (PCI-DSS 1.2)
5. **database-isolation**: Isolate database access (HIPAA §164.312(a)(2)(i))
6. **egress-allow-dns-external**: Allow DNS queries
7. **ingress-api-gateway**: API gateway entry point (HIPAA §164.312(a)(2)(iv))
8. **deny-cross-namespace-egress**: Namespace isolation (PCI-DSS 1.2)

**Compliance Mapping**:
- HIPAA: §164.312(a)(1) - Firewall and access controls
- SOC2: CC7.4 - Network segregation controls
- PCI-DSS: 1 - Network segmentation and firewall rules

---

### 3. rbac-policies.yaml
**Purpose**: Kubernetes RBAC for role-based access control

**Service Accounts** (5 roles):
1. **yawl-admin**: Full cluster access
2. **yawl-developer**: Limited development permissions
3. **yawl-viewer**: Read-only access
4. **yawl-operator**: Infrastructure operations
5. **yawl-security-officer**: Audit and compliance access

**ClusterRoles**:
- `yawl-cluster-admin`: Cluster administration
- `yawl-developer-role`: Deployment and pod management
- `yawl-viewer-role`: Read-only resource access
- `yawl-operator-role`: Infrastructure management
- `yawl-security-officer-role`: Audit and compliance

**Compliance Mapping**:
- HIPAA: §164.308(a)(3) - Workforce security and authorization
- SOC2: CC6 - Logical access controls (least privilege)
- PCI-DSS 7 - Restricted access to data

---

### 4. pod-security-standards.yaml
**Purpose**: Pod security policies and enforcement

**Components**:
1. **Namespaces**: Security labels for different environments
2. **PodSecurityPolicy**:
   - `restricted`: Baseline policy (no privileged access)
   - `baseline`: Moderate security policy
3. **Resource Quotas**: Prevent resource exhaustion
4. **LimitRange**: Per-pod resource constraints
5. **Pod Disruption Budget**: High-availability controls
6. **Security Scanning Config**: Container image scanning
7. **Secret Management Policy**: Secret lifecycle and rotation

**Compliance Mapping**:
- HIPAA: §164.312(a)(1) - Security controls
- SOC2: CC6.1 - Logical access controls
- PCI-DSS: 2.2.4 - Secure configuration

---

### 5. compliance-checklist.md
**Purpose**: Comprehensive checklist for HIPAA, SOC2, and PCI-DSS compliance

**Sections**:
- HIPAA Compliance (§164.308 Admin Safeguards, §164.312 Technical Safeguards)
- SOC2 Compliance (CC6 Logical Access, CC7 System Monitoring)
- PCI-DSS Compliance (Requirements 1-10)
- Cross-Framework Controls
- Implementation Timeline (16-week phased approach)
- Audit and Testing Procedures
- Incident Response Procedures
- Documentation Requirements

**Status Tracking**:
- Implementation Status: Pending, Partial, Complete
- Evidence Files: References to YAML configuration files
- Review Frequency: Monthly, Quarterly, Annually

---

## Cloud IAM Templates

### AWS IAM Templates

#### aws-iam-policy.json
**Purpose**: AWS IAM policy for YAWL application

**Permissions**:
- EC2 instance management (read-only)
- KMS key access (encryption/decryption)
- S3 bucket access (production only)
- RDS database access
- Secrets Manager access
- CloudWatch Logs and Metrics
- CloudTrail audit logs

**Compliance**:
- Deny unencrypted transport (enforce HTTPS)
- Deny KMS key deletion (prevent accidental key loss)
- IP-based restrictions for secrets access

#### aws-iam-role.yaml
**Purpose**: AWS IAM role definitions for YAWL services

**Roles**:
1. **yawl-application-role**: Primary application role
2. **yawl-developer-role**: Limited developer permissions
3. **yawl-compliance-role**: Audit and compliance access

**Setup**:
- Trust policy for EC2 and EKS
- Pod Identity annotations for EKS
- Service account mapping

---

### GCP IAM Templates

#### gcp-iam-policy.yaml
**Purpose**: GCP IAM custom roles and bindings

**Custom Roles**:
1. **yawl-application-role**: Full application permissions
2. **yawl-developer-role**: Read-only and development access
3. **yawl-security-officer-role**: Audit and compliance
4. **yawl-viewer-role**: Read-only access

**Service Accounts**:
- `yawl-application-sa`: Main application service account
- `yawl-workload-identity-sa`: GKE Workload Identity

**Organization Policies**:
- Enforce CMEK for storage and Cloud SQL
- Restrict public IP addresses
- Uniform bucket-level access

---

#### gke-workload-identity.yaml
**Purpose**: GKE Workload Identity configuration

**Features**:
- Pod authentication to GCP services without service account keys
- Automatic token refresh (1-hour validity)
- Integration with Cloud KMS, Cloud Storage, Cloud SQL
- Setup instructions and environment variables

**Setup Steps**:
1. Create GCP service account
2. Grant IAM roles
3. Bind Kubernetes to GCP service account
4. Annotate Kubernetes service account
5. Deploy resources
6. Verify setup

---

### Azure IAM Templates

#### azure-iam-policy.json
**Purpose**: Azure custom RBAC role definition

**Permissions**:
- Compute (VMs, disks, networking)
- Storage (blobs, containers)
- SQL Database
- Key Vault (read and cryptographic operations)
- Monitoring and diagnostics
- Authorization and role management
- Container Service (AKS)

---

#### azure-iam-roles.yaml
**Purpose**: Azure IAM roles, policy assignments, and Managed Identity

**Components**:
1. **Developer Role**: Limited permissions for development
2. **Compliance Officer Role**: Audit and security access
3. **Policy Assignments**: Compliance policy enforcement
4. **Managed Identity**: Azure AD managed identity
5. **Federated Identity**: OIDC integration for AKS
6. **Conditional Access**: MFA, legacy auth blocking, device compliance
7. **Azure Defender**: Threat detection and compliance monitoring

---

#### aks-pod-identity.yaml
**Purpose**: AKS Pod Identity (AAD Pod Identity) configuration

**Features**:
- Pod authentication using Azure Managed Identity
- Automatic token refresh
- Integration with Key Vault and Storage
- Setup instructions for deployment

**Components**:
- AzureIdentity resource
- AzureIdentityBinding for pod association
- Deployment and StatefulSet examples
- NetworkPolicy for pod identity traffic

---

### EKS IAM Templates

#### eks-irsa.yaml
**Purpose**: EKS IAM Roles for Service Accounts (IRSA)

**Features**:
- Pod authentication to AWS services via OIDC
- Automatic credential refresh (15 minutes)
- Temporary security credentials
- Integration with KMS, S3, RDS
- Setup instructions

**Service Accounts**:
- `yawl-app-irsa`: Application service account
- `yawl-db-irsa`: Database access service account
- `yawl-monitoring-irsa`: Monitoring service account

**Setup Steps**:
1. Enable OIDC provider on EKS cluster
2. Create IAM role with OIDC trust
3. Attach IAM policies
4. Annotate Kubernetes service account
5. Deploy resources
6. Verify setup

---

## Implementation Guide

### Phase 1: Foundation (Weeks 1-4)
- [x] Create RBAC policies
- [x] Create network policies
- [x] Create pod security standards
- [ ] Deploy to test environment
- [ ] Conduct initial security audit

### Phase 2: Authentication & Encryption (Weeks 5-8)
- [ ] Implement MFA
- [ ] Deploy TLS 1.3 infrastructure
- [ ] Implement AES-256-GCM encryption
- [ ] Deploy key management system
- [ ] Test encryption key rotation

### Phase 3: Monitoring & Logging (Weeks 9-12)
- [ ] Deploy SIEM solution
- [ ] Implement centralized logging
- [ ] Deploy real-time alerting
- [ ] Establish log retention policies
- [ ] Test log integrity verification

### Phase 4: Validation & Certification (Weeks 13-16)
- [ ] Conduct third-party audit
- [ ] Remediate findings
- [ ] Obtain compliance certification
- [ ] Document compliance artifacts
- [ ] Establish continuous monitoring

---

## Compliance Mapping

### HIPAA Controls
| Section | Control | File | Status |
|---------|---------|------|--------|
| §164.308(a)(1) | Security Management | security-policy.yaml | Pending |
| §164.308(a)(3) | Workforce Security | rbac-policies.yaml | Complete |
| §164.312(a)(1) | Access Controls | rbac-policies.yaml, network-policies.yaml | Partial |
| §164.312(a)(2)(i) | User Auth | security-policy.yaml | Pending |
| §164.312(a)(2)(iv) | Encryption | security-policy.yaml | Pending |
| §164.312(b) | Audit Controls | security-policy.yaml | Partial |

### SOC2 Controls
| Control | Description | File | Status |
|---------|-------------|------|--------|
| CC6.1 | Logical Access Restrictions | rbac-policies.yaml | Complete |
| CC6.2 | User Access Provisioning | rbac-policies.yaml | Pending |
| CC6.3 | User Access Revocation | rbac-policies.yaml | Pending |
| CC7.1 | System Monitoring | security-policy.yaml | Partial |
| CC7.2 | Anomaly Detection | security-policy.yaml | Pending |
| CC7.4 | Network Monitoring | network-policies.yaml | Partial |

### PCI-DSS Controls
| Requirement | Description | File | Status |
|-------------|-------------|------|--------|
| 1 | Network Segmentation | network-policies.yaml | Partial |
| 2 | Default Security Config | pod-security-standards.yaml | Partial |
| 3 | Protect Stored Data | security-policy.yaml | Pending |
| 6 | Security Updates | pod-security-standards.yaml | Pending |
| 7 | Restrict Access | rbac-policies.yaml | Complete |
| 8 | User Identification | security-policy.yaml | Partial |
| 10 | Logging and Monitoring | security-policy.yaml | Partial |

---

## Deployment Instructions

### Prerequisites
1. Kubernetes cluster (EKS, GKE, or AKS)
2. kubectl configured
3. Cloud CLI tools (aws-cli, gcloud, az-cli)

### Deploy Security Policies

```bash
# Deploy Kubernetes security policies
kubectl apply -f security-policy.yaml
kubectl apply -f network-policies.yaml
kubectl apply -f rbac-policies.yaml
kubectl apply -f pod-security-standards.yaml

# Verify deployment
kubectl get networkpolicies -A
kubectl get roles,rolebindings -A
kubectl get psp
```

### Deploy Cloud IAM Templates

#### AWS (EKS)
```bash
# Update with your AWS account ID and EKS cluster details
sed -i 's/ACCOUNT-ID/123456789012/g' iam-templates/aws-iam-*.yaml
sed -i 's/ACCOUNT-ID/123456789012/g' iam-templates/eks-irsa.yaml

# Apply IRSA configuration
kubectl apply -f iam-templates/eks-irsa.yaml

# Configure IAM role (via AWS CLI)
aws iam create-role --role-name yawl-app-irsa-role \
  --assume-role-policy-document file://trust-policy.json
```

#### Google Cloud (GKE)
```bash
# Update with your GCP project ID
sed -i 's/PROJECT-ID/my-project-id/g' iam-templates/gke-workload-identity.yaml
sed -i 's/PROJECT-ID/my-project-id/g' iam-templates/gcp-iam-policy.yaml

# Apply Workload Identity configuration
kubectl apply -f iam-templates/gke-workload-identity.yaml
```

#### Azure (AKS)
```bash
# Update with your Azure details
sed -i 's/SUBSCRIPTION-ID/00000000-0000-0000-0000-000000000000/g' iam-templates/aks-pod-identity.yaml
sed -i 's/TENANT-ID/00000000-0000-0000-0000-000000000000/g' iam-templates/aks-pod-identity.yaml

# Apply Pod Identity configuration
kubectl apply -f iam-templates/aks-pod-identity.yaml
```

---

## Validation and Testing

### Security Policy Validation
```bash
# Check network policies
kubectl get networkpolicies -A
kubectl describe networkpolicy <policy-name> -n <namespace>

# Check RBAC bindings
kubectl get rolebindings,clusterrolebindings -A
kubectl auth can-i get pods --as=system:serviceaccount:default:yawl-developer

# Check pod security policies
kubectl get psp
kubectl describe psp restricted
```

### Cloud IAM Validation

```bash
# AWS - List role trust relationships
aws iam get-role --role-name yawl-app-irsa-role

# GCP - List service accounts and bindings
gcloud iam service-accounts list
gcloud iam service-accounts get-iam-policy yawl-app@PROJECT-ID.iam.gserviceaccount.com

# Azure - List role assignments
az role assignment list --resource-group yawl-rg
```

---

## Compliance Audit

### Annual Audit Process
1. **Risk Assessment**: Update threat model and security posture
2. **Security Assessment**: Vulnerability scanning, penetration testing
3. **Compliance Audit**: HIPAA, SOC2, PCI-DSS assessment
4. **Evidence Collection**: Gather audit logs and documentation
5. **Remediation**: Address findings and gaps
6. **Certification**: Obtain compliance certifications

### Documentation
- Maintain audit logs for 7 years (HIPAA requirement)
- Document all security incidents and responses
- Keep compliance audit reports on file
- Update policies based on assessment findings

---

## Support and References

### Compliance Frameworks
- [HIPAA Security Rule](https://www.hhs.gov/hipaa/for-professionals/security/index.html)
- [SOC2 Trust Service Criteria](https://www.aicpa.org/interestareas/informationmanagementtechnology/pages/soc-2-trust-service-criteria.aspx)
- [PCI-DSS Standard](https://www.pcisecuritystandards.org/)

### Cloud Documentation
- [AWS IAM Best Practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html)
- [GCP IAM Best Practices](https://cloud.google.com/iam/docs/best-practices)
- [Azure RBAC Best Practices](https://docs.microsoft.com/en-us/azure/role-based-access-control/best-practices)

### Kubernetes Security
- [Kubernetes Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/)
- [Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)
- [Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/)

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-14 | Security Team | Initial creation with all compliance frameworks |

---

## Document Classification

**Classification**: Internal Use Only
**Retention Period**: 7 years (HIPAA requirement)
**Last Updated**: 2026-02-14
**Next Review**: 2026-05-14

---

For questions or updates, contact the Security Team.
