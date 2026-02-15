# Cloud Secret Managers Integration for YAWL

## Overview

This document covers integration patterns with cloud-native secret management services across AWS, Azure, GCP, Oracle Cloud, and IBM Cloud.

## AWS Secrets Manager

### Architecture

```
+----------------+     +------------------+     +-------------------+
|  YAWL Pod      |---->|  IAM Role        |---->|  AWS Secrets      |
|  (IRSA)        |     |  (Pod Identity)  |     |  Manager          |
+----------------+     +------------------+     +-------------------+
```

### IAM Policy for Secrets Access

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SecretsAccess",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": [
        "arn:aws:secretsmanager:us-east-1:ACCOUNT_ID:secret:yawl/*"
      ]
    }
  ]
}
```

### Kubernetes Integration (IRSA)

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine
  namespace: yawl
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT_ID:role/YawlEngineRole
---
# Pod reads secrets via AWS SDK
# Environment variables set at runtime
```

### Secret Rotation

```bash
# Configure rotation with Lambda
aws secretsmanager rotate-secret \
    --secret-id yawl/database \
    --rotation-lambda-arn arn:aws:lambda:us-east-1:ACCOUNT_ID:function:SecretsRotation \
    --rotation-rules AutomaticallyAfterDays=30
```

## Azure Key Vault

### Architecture

```
+----------------+     +------------------+     +-------------------+
|  YAWL Pod      |---->|  Managed         |---->|  Azure Key        |
|  (Workload ID) |     |  Identity        |     |  Vault            |
+----------------+     +------------------+     +-------------------+
```

### Azure RBAC Configuration

```bash
# Create managed identity
az identity create \
  --name yawl-engine-identity \
  --resource-group yawl-production

# Assign Key Vault Secrets User role
az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee <principal-id> \
  --scope /subscriptions/SUB_ID/resourceGroups/yawl-production/providers/Microsoft.KeyVault/vaults/yawl-kv

# Enable soft delete and purge protection
az keyvault update \
  --name yawl-kv \
  --enable-soft-delete true \
  --enable-purge-protection true
```

### Kubernetes Integration (Workload Identity)

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine
  namespace: yawl
  labels:
    azure.workload.identity/use: "true"
  annotations:
    azure.workload.identity/client-id: "<managed-identity-client-id>"
    azure.workload.identity/tenant-id: "<tenant-id>"
```

### Access Secrets from Application

```python
# Using Azure Identity and Key Vault SDK
from azure.identity import DefaultAzureCredential
from azure.keyvault.secrets import SecretClient

credential = DefaultAzureCredential()
client = SecretClient(vault_url="https://yawl-kv.vault.azure.net/", credential=credential)

db_password = client.get_secret("database-password").value
```

## GCP Secret Manager

### Architecture

```
+----------------+     +------------------+     +-------------------+
|  YAWL Pod      |---->|  Service Account |---->|  Secret           |
|  (Workload ID) |     |  (K8s SA)        |     |  Manager          |
+----------------+     +------------------+     +-------------------+
```

### IAM Configuration

```bash
# Create service account
gcloud iam service-accounts create yawl-engine \
  --display-name="YAWL Engine Service Account"

# Grant secret accessor role
gcloud secrets add-iam-policy-binding yawl-database \
  --member="serviceAccount:yawl-engine@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

# Configure workload identity
gcloud iam service-accounts add-iam-policy-binding \
  yawl-engine@PROJECT_ID.iam.gserviceaccount.com \
  --member="serviceAccount:PROJECT_ID.svc.id.goog[yawl/yawl-engine]" \
  --role="roles/iam.workloadIdentityUser"
```

### Kubernetes Integration

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine
  namespace: yawl
  annotations:
    iam.gke.io/gcp-service-account: yawl-engine@PROJECT_ID.iam.gserviceaccount.com
```

### Access Secrets from Application

```python
from google.cloud import secretmanager

client = secretmanager.SecretManagerServiceClient()
name = f"projects/PROJECT_ID/secrets/yawl-database/versions/latest"
response = client.access_secret_version(request={"name": name})
db_password = response.payload.data.decode("UTF-8")
```

## Oracle Cloud Vault

### Architecture

```
+----------------+     +------------------+     +-------------------+
|  YAWL Pod      |---->|  Dynamic Group   |---->|  OCI Vault        |
|  (Instance     |     |  + Policy        |     |  (Key Management) |
|   Principal)   |     |                  |     |                   |
+----------------+     +------------------+     +-------------------+
```

### OCI IAM Configuration

```bash
# Create dynamic group for OKE pods
# Rule: resource.type = 'ocipod' AND resource.compartment.id = '<compartment-ocid>'

# Create policy
# Allow dynamic-group yawl-engine-pods to read secret-family in compartment yawl-production
# Allow dynamic-group yawl-engine-pods to use keys in compartment yawl-production where target.key.id = '<key-ocid>'
```

### Access Secrets from Application

```python
import oci

signer = oci.auth.signers.InstancePrincipalsSecurityTokenSigner()
client = oci.secrets.SecretsClient(config={}, signer=signer)

secret_id = "ocid1.secret.oc1..<secret-ocid>"
response = client.get_secret_bundle(secret_id)
secret_content = response.data.secret_bundle_content.content
```

## IBM Cloud Secrets Manager

### Architecture

```
+----------------+     +------------------+     +-------------------+
|  YAWL Pod      |---->|  Trusted Profile |---->|  Secrets          |
|  (Service ID)  |     |  (Workload ID)   |     |  Manager          |
+----------------+     +------------------+     +-------------------+
```

### IAM Configuration

```bash
# Create service ID
ibmcloud iam service-id-create yawl-engine --description "YAWL Engine"

# Create API key (store in Secrets Manager)
ibmcloud iam service-api-key-create yawl-engine-key yawl-engine

# Grant access to Secrets Manager
ibmcloud iam service-policy-create yawl-engine \
  --roles SecretReader \
  --service-name secrets-manager \
  --service-instance <secrets-manager-instance-id>

# Create trusted profile for workload identity
ibmcloud iam trusted-profile-create yawl-engine-profile \
  --cr-type iks \
  --cluster <cluster-id> \
  --namespace yawl \
  --service-account yawl-engine
```

### Access Secrets from Application

```python
from ibm_cloud_sdk_core.authenticators import IAMAuthenticator
from ibm_secrets_manager_sdk.secrets_manager_v2 import SecretsManagerV2

authenticator = IAMAuthenticator(api_key)
client = SecretsManagerV2(authenticator=authenticator)
client.set_service_url("https://<region>.secrets-manager.appdomain.cloud")

secret = client.get_secret(id="<secret-id>")
password = secret.result["data"]["payload"]
```

## Comparison Matrix

| Feature | AWS Secrets Manager | Azure Key Vault | GCP Secret Manager | OCI Vault | IBM Secrets Manager |
|---------|---------------------|-----------------|--------------------|-----------|--------------------|
| Automatic Rotation | Yes (Lambda) | Yes (built-in) | Pub/Sub triggered | Manual | Yes (Notifications) |
| Versioning | Yes | Yes | Yes | Yes | Yes |
| Audit Logging | CloudTrail | Monitor | Cloud Audit | Audit | Activity Tracker |
| Encryption | KMS | Key Vault | Cloud KMS | Key Management | Key Protect |
| Max Secret Size | 64KB | 25KB | 64KB | Varies | 64KB |
| IAM Integration | IAM | RBAC | IAM | IAM | IAM |
| K8s Integration | IRSA | Workload ID | Workload ID | Instance Principal | Trusted Profile |

## Best Practices

### 1. Secret Naming Convention

```
yawl/{environment}/{component}/{secret-name}

Examples:
- yawl/production/database/credentials
- yawl/production/ldap/bind-credentials
- yawl/staging/api-keys/webhook
```

### 2. Secret Rotation Strategy

- Database credentials: Rotate every 30-90 days
- API keys: Rotate every 90 days
- Encryption keys: Rotate annually
- Service account tokens: Rotate every 24 hours

### 3. Access Control

- Use dedicated service accounts per component
- Implement least-privilege access
- Enable audit logging for all secret access
- Use short-lived tokens when possible

### 4. Secret Injection Methods

1. **Environment Variables** (via External Secrets Operator)
2. **Volume Mounts** (via CSI Secret Store driver)
3. **SDK Integration** (direct API calls)
4. **Sidecar Injection** (HashiCorp Vault Agent)

### 5. Compliance Considerations

- Enable secret versioning for audit trails
- Implement secret expiration policies
- Use HSM-backed keys for sensitive secrets
- Enable soft delete/purge protection
