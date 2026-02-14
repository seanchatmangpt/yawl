# YAWL Workflow Engine - Azure Security Requirements

## Overview

This document outlines the security requirements and controls for YAWL Workflow Engine on Azure Marketplace, aligned with the Azure Well-Architected Framework Security Pillar and Microsoft security best practices.

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

| Microsoft Responsibilities | YAWL Responsibilities |
|---------------------------|----------------------|
| Physical security | Application security |
| Hypervisor security | Data classification |
| Network infrastructure | Identity management |
| Azure service security | Encryption configuration |
| Compliance (infrastructure) | Compliance (application) |

### 1.2 Security Governance

#### Azure Tenant Structure

```
Azure Tenant
+-- Management Group (Root)
|   +-- Security Management Group
|   |   +-- Security Subscription (Azure Security Center, Sentinel)
|   |   +-- Log Analytics Subscription
|   +-- Production Management Group
|   |   +-- YAWL Production Subscription
|   +-- Development Management Group
|       +-- YAWL Development Subscription
```

#### Security Controls

| Control | Implementation | Status |
|---------|---------------|--------|
| Azure Policy | Built-in and custom policies | Required |
| Azure Security Center | Standard tier | Required |
| Microsoft Defender | For Cloud, Containers | Required |
| Azure Sentinel | SIEM integration | Recommended |
| Azure AD | Identity protection | Required |

### 1.3 Subscription Security

#### Subscription-Level Security

```bash
# Enable Security Center
az security pricing create --name VirtualMachines --tier Standard
az security pricing create --name AppServices --tier Standard
az security pricing create --name SqlServers --tier Standard
az security pricing create --name KeyVaults --tier Standard
az security pricing create --name Arm --tier Standard
az security pricing create --name Dns --tier Standard
az security pricing create --name Containers --tier Standard

# Enable Microsoft Defender for Containers
az security security-pricing create --name Containers --tier Standard
```

---

## 2. Identity and Access Management

### 2.1 Microsoft Entra ID Integration

#### SE:01 - Use Microsoft Entra ID for Authentication

| Requirement | Implementation |
|-------------|----------------|
| Single Sign-On | Azure AD SSO for YAWL console |
| Multi-Factor Authentication | Required for all admin accounts |
| Conditional Access | Enforce for management access |
| Passwordless | Recommended for user accounts |

```json
// Conditional Access Policy for YAWL Administrators
{
  "displayName": "YAWL Admin Access",
  "state": "enabled",
  "conditions": {
    "applications": {
      "includeApplications": ["yawl-app-id"]
    },
    "users": {
      "includeRoles": ["YAWL Administrator"]
    },
    "locations": {
      "includeLocations": ["All"]
    }
  },
  "grantControls": {
    "builtInControls": ["mfa", "compliantDevice"]
  }
}
```

### 2.2 Managed Identities

#### SE:02 - Use Managed Identities for Azure Resources

```bicep
// User-assigned managed identity for YAWL
resource yawlIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'yawl-app-identity'
  location: location
}

// Role assignments for the managed identity
resource storageBlobContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(subscription().id, 'Storage Blob Data Contributor')
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', 'ba92f5b4-2d11-453d-a403-e96b0029c9fe')
    principalId: yawlIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource keyVaultSecretsUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(subscription().id, 'Key Vault Secrets User')
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')
    principalId: yawlIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}
```

### 2.3 Azure RBAC

#### SE:03 - Apply Least Privilege Access

##### Custom Role for YAWL Application

```json
{
  "Name": "YAWL Application Role",
  "Description": "Minimal permissions for YAWL application",
  "Actions": [
    "Microsoft.Storage/storageAccounts/blobServices/containers/read",
    "Microsoft.Storage/storageAccounts/blobServices/containers/write",
    "Microsoft.KeyVault/vaults/secrets/read",
    "Microsoft.DBforPostgreSQL/flexibleServers/read"
  ],
  "NotActions": [
    "Microsoft.Authorization/*/write",
    "Microsoft.Resources/*/write"
  ],
  "AssignableScopes": [
    "/subscriptions/{subscription-id}/resourceGroups/yawl-production"
  ]
}
```

### 2.4 Azure AD Workload Identity

```yaml
# Kubernetes service account with Azure AD annotation
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine-sa
  namespace: yawl
  annotations:
    azure.workload.identity/client-id: "<managed-identity-client-id>"
  labels:
    azure.workload.identity/use: "true"
---
# Pod using workload identity
apiVersion: v1
kind: Pod
metadata:
  name: yawl-engine
  namespace: yawl
  labels:
    azure.workload.identity/use: "true"
spec:
  serviceAccountName: yawl-engine-sa
  containers:
    - name: yawl-engine
      image: yawlfoundation.azurecr.io/yawl/engine:5.2.0
```

---

## 3. Detection and Monitoring

### 3.1 SE:04 - Configure Security Monitoring

#### Azure Security Center Configuration

```bash
# Enable auto-provisioning for monitoring agent
az security auto-provisioning-setting update \
  --name default \
  --auto-provision on

# Enable security solutions
az security security-solutions-reference-data create \
  --resource-group ${RESOURCE_GROUP} \
  --solution-name Containers

# Set security contact
az security contact create \
  --name default \
  --email security@yawlfoundation.org \
  --phone "+1-555-YAWL-SEC" \
  --alert-notifications on \
  --alerts-admins on
```

#### Log Analytics Workspace Configuration

```bicep
resource lawWorkspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: 'yawl-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 90
    features: {
      enableLogAccessUsingOnlyResourcePermissions: true
    }
  }
}

// Enable security solutions
resource securitySolution 'Microsoft.OperationsManagement/solutions@2015-11-01-preview' = {
  name: 'SecurityInsights(${lawWorkspace.name})'
  location: location
  properties: {
    workspaceResourceId: lawWorkspace.id
  }
}
```

### 3.2 Diagnostic Settings

```bicep
// AKS diagnostic settings
resource aksDiagnostics 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'yawl-aks-diagnostics'
  scope: aksCluster
  properties: {
    workspaceId: lawWorkspace.id
    logs: [
      {
        category: 'kube-apiserver'
        enabled: true
      }
      {
        category: 'kube-audit'
        enabled: true
      }
      {
        category: 'kube-audit-admin'
        enabled: true
      }
      {
        category: 'kube-controller-manager'
        enabled: true
      }
      {
        category: 'kube-scheduler'
        enabled: true
      }
      {
        category: 'cluster-autoscaler'
        enabled: true
      }
      {
        category: 'guard'
        enabled: true
      }
    ]
    metrics: [
      {
        category: 'AllMetrics'
        enabled: true
      }
    ]
  }
}
```

### 3.3 Alert Rules

```bicep
// Security alert rules
resource failedAuthAlert 'Microsoft.Insights/scheduledQueryRules@2022-08-01' = {
  name: 'yawl-failed-auth-alert'
  location: location
  properties: {
    displayName: 'YAWL Failed Authentication Attempts'
    description: 'Alert on multiple failed authentication attempts'
    severity: 2
    enabled: true
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      allOf: [
        {
          query: 'AzureDiagnostics | where Category == "kube-audit" | where log_s contains "Unauthorized" | summarize count() by bin(TimeGenerated, 5m)'
          timeAggregation: 'Count'
          threshold: 5
          operator: 'GreaterThan'
        }
      ]
    }
    actions: {
      actionGroups: [
        {
          actionGroupId: actionGroup.id
        }
      ]
    }
  }
}
```

---

## 4. Infrastructure Protection

### 4.1 SE:05 - Network Security

#### VNET Architecture

```
VNet (10.0.0.0/16)
+-- AzureFirewallSubnet (10.0.0.0/26)
|   +-- Azure Firewall
|
+-- GatewaySubnet (10.0.1.0/27)
|   +-- VPN/ExpressRoute Gateway (optional)
|
+-- PrivateEndpoints (10.0.2.0/24)
|   +-- PostgreSQL Private Endpoint
|   +-- Storage Private Endpoint
|   +-- Key Vault Private Endpoint
|   +-- ACR Private Endpoint
|
+-- AKSSystem (10.0.64.0/18)
|   +-- System Node Pool
|
+-- AKSUser (10.0.128.0/18)
|   +-- User Node Pool (YAWL workloads)
|
+-- ApplicationGateway (10.0.192.0/24)
    +-- Application Gateway (WAF)
```

#### Network Security Groups

```bicep
// NSG for AKS subnet
resource aksNsg 'Microsoft.Network/networkSecurityGroups@2023-04-01' = {
  name: 'yawl-aks-nsg'
  location: location
  properties: {
    securityRules: [
      {
        name: 'AllowHTTPSInbound'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '443'
          sourceAddressPrefix: '10.0.192.0/24'  // Application Gateway
          destinationAddressPrefix: '*'
          access: 'Allow'
          priority: 100
          direction: 'Inbound'
        }
      }
      {
        name: 'AllowAzureLoadBalancer'
        properties: {
          protocol: '*'
          sourcePortRange: '*'
          destinationPortRange: '*'
          sourceAddressPrefix: 'AzureLoadBalancer'
          destinationAddressPrefix: '*'
          access: 'Allow'
          priority: 110
          direction: 'Inbound'
        }
      }
      {
        name: 'DenyAllInbound'
        properties: {
          protocol: '*'
          sourcePortRange: '*'
          destinationPortRange: '*'
          sourceAddressPrefix: '*'
          destinationAddressPrefix: '*'
          access: 'Deny'
          priority: 4096
          direction: 'Inbound'
        }
      }
    ]
  }
}
```

### 4.2 Private Endpoints

```bicep
// Private endpoint for PostgreSQL
resource postgresPrivateEndpoint 'Microsoft.Network/privateEndpoints@2023-04-01' = {
  name: 'yawl-postgres-pe'
  location: location
  properties: {
    subnet: {
      id: privateEndpointSubnetId
    }
    privateLinkServiceConnections: [
      {
        name: 'yawl-postgres-pls'
        properties: {
          privateLinkServiceId: postgresServer.id
          groupIds: ['postgresqlServer']
          privateLinkServiceConnectionState: {
            status: 'Approved'
            description: 'Auto-approved'
          }
        }
      }
    ]
  }
}

// Private DNS zone for PostgreSQL
resource postgresDnsZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.postgres.database.azure.com'
  location: 'global'
  properties: {
    privateDnsZoneConfig: [
      {
        name: 'yawl-postgres-dns-config'
        properties: {
          privateDnsZoneId: postgresDnsZone.id
          recordSets: [
            {
              name: postgresServer.name
              properties: {
                aRecords: [
                  {
                    ipv4Address: postgresPrivateEndpoint.properties.networkInterfaces[0].properties.ipConfigurations[0].properties.privateIPAddress
                  }
                ]
              }
            }
          ]
        }
      }
    ]
  }
}
```

### 4.3 Azure Firewall

```bicep
// Azure Firewall for egress control
resource azureFirewall 'Microsoft.Network/azureFirewalls@2023-04-01' = {
  name: 'yawl-firewall'
  location: location
  properties: {
    sku: {
      name: 'AZFW_VNet'
      tier: 'Standard'
    }
    threatIntelMode: 'Alert'
    ipConfigurations: [
      {
        name: 'azureFirewallIpConfiguration'
        properties: {
          subnet: {
            id: firewallSubnetId
          }
          publicIPAddress: {
            id: firewallPip.id
          }
        }
      }
    ]
    applicationRuleCollections: [
      {
        name: 'yawl-allow-rules'
        properties: {
          priority: 100
          action: {
            type: 'Allow'
          }
          rules: [
            {
              name: 'allow-azure-apis'
              protocols: [
                {
                  protocolType: 'Https'
                  port: 443
                }
              ]
              targetFqdns: [
                '*.azure.com'
                '*.azure.net'
                '*.microsoft.com'
              ]
              sourceAddresses: ['10.0.64.0/18']
            }
          ]
        }
      }
    ]
    networkRuleCollections: [
      {
        name: 'yawl-network-rules'
        properties: {
          priority: 100
          action: {
            type: 'Allow'
          }
          rules: [
            {
              name: 'allow-ntp'
              protocols: ['UDP']
              sourceAddresses: ['10.0.64.0/18']
              destinationAddresses: ['*']
              destinationPorts: ['123']
            }
          ]
        }
      }
    ]
  }
}
```

---

## 5. Data Protection

### 5.1 SE:06 - Data Classification

| Classification | Examples | Controls |
|---------------|----------|----------|
| **Confidential** | Customer PII, credentials | Encrypt at rest and in transit, strict access |
| **Internal** | Workflow specifications, logs | Encrypt at rest, role-based access |
| **Public** | Documentation, examples | No encryption required |

### 5.2 SE:07 - Encryption at Rest

#### Key Vault Configuration

```bicep
resource keyVault 'Microsoft.KeyVault/vaults@2023-02-01' = {
  name: 'yawl-kv'
  location: location
  properties: {
    tenantId: tenant().tenantId
    sku: {
      name: 'standard'
      family: 'A'
    }
    enableSoftDelete: true
    enablePurgeProtection: true
    softDeleteRetentionInDays: 90
    enableRbacAuthorization: true
    networkAcls: {
      defaultAction: 'Deny'
      bypass: 'AzureServices'
      virtualNetworkRules: [
        {
          id: aksSubnetId
        }
      ]
    }
  }
}

// Customer-managed key for encryption
resource encryptionKey 'Microsoft.KeyVault/vaults/keys@2023-02-01' = {
  parent: keyVault
  name: 'yawl-encryption-key'
  properties: {
    keySize: 4096
    kty: 'RSA'
    keyOps: ['encrypt', 'decrypt', 'wrapKey', 'unwrapKey']
    rotationPolicy: {
      lifetimeActions: [
        {
          trigger: {
            timeBeforeExpiry: 'P30D'
          }
          action: {
            type: 'rotate'
          }
        }
      ]
    }
  }
}
```

#### PostgreSQL Encryption

```bicep
resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2023-03-01-preview' = {
  name: 'yawl-postgres'
  location: location
  properties: {
    version: '15'
    storage: {
      storageSizeGB: 100
      autoGrow: 'Enabled'
      tier: 'GeneralPurpose'
    }
    backup: {
      backupRetentionDays: 30
      geoRedundantBackup: 'Enabled'
    }
    highAvailability: {
      mode: 'ZoneRedundant'
    }
    dataEncryption: {
      type: 'AzureKeyVault'
      keyVaultUri: keyVault.properties.vaultUri
      keyName: encryptionKey.name
    }
  }
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${encryptionIdentity.id}': {}
    }
  }
}
```

### 5.3 SE:08 - Encryption in Transit

#### Application Gateway TLS Configuration

```bicep
resource appGateway 'Microsoft.Network/applicationGateways@2023-04-01' = {
  name: 'yawl-appgw'
  location: location
  properties: {
    sku: {
      name: 'WAF_v2'
      tier: 'WAF_v2'
      capacity: 2
    }
    sslPolicy: {
      policyType: 'CustomV2'
      minProtocolVersion: 'TLSv1_2'
      cipherSuites: [
        'TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384'
        'TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256'
        'TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384'
        'TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256'
      ]
    }
    httpListeners: [
      {
        name: 'https-listener'
        properties: {
          protocol: 'Https'
          port: 443
          sslCertificate: {
            id: sslCertificate.id
          }
        }
      }
    ]
  }
}
```

#### PostgreSQL SSL Enforcement

```bash
# Enforce SSL for PostgreSQL
az postgres flexible-server parameter set \
  --resource-group ${RESOURCE_GROUP} \
  --server-name yawl-postgres \
  --name ssl_enforcement \
  --value Enabled

# Set minimum TLS version
az postgres flexible-server parameter set \
  --resource-group ${RESOURCE_GROUP} \
  --server-name yawl-postgres \
  --name ssl_min_protocol_version \
  --value TLSV1.2
```

---

## 6. Incident Response

### 6.1 SE:09 - Incident Response Plan

#### Incident Severity Levels

| Incident Type | Severity | Response Time | Escalation |
|--------------|----------|---------------|------------|
| Data breach | Critical | 15 min | CISO, Legal, Microsoft |
| Unauthorized access | High | 1 hour | Security Team Lead |
| Malware detection | High | 1 hour | Security Team |
| Vulnerability disclosure | Medium | 4 hours | Development Team |
| Policy violation | Low | 24 hours | Compliance Team |

### 6.2 SE:10 - Forensic Capabilities

```bash
# Enable diagnostic settings for forensics
az monitor diagnostic-settings create \
  --resource ${AKS_CLUSTER_ID} \
  --name forensic-logging \
  --logs '[{"category": "kube-audit", "enabled": true}, {"category": "kube-audit-admin", "enabled": true}]' \
  --workspace ${WORKSPACE_ID}

# Create disk snapshot for forensics
az snapshot create \
  --resource-group ${RESOURCE_GROUP} \
  --name forensic-snapshot-$(date +%Y%m%d) \
  --source ${DISK_ID}

# Isolate compromised resources
az network nsg rule create \
  --resource-group ${RESOURCE_GROUP} \
  --nsg-name yawl-aks-nsg \
  --name deny-all-isolation \
  --priority 100 \
  --direction Inbound \
  --access Deny \
  --protocol '*' \
  --source-address-prefixes "${COMPROMISED_IP}"
```

### 6.3 Automated Response with Azure Sentinel

```kql
// Azure Sentinel analytic rule for detecting suspicious activity
AzureDiagnostics
| where Category == "kube-audit"
| where log_s contains "exec" or log_s contains "attach"
| where requestURI !contains "healthz"
| summarize ExecCount = count() by bin(TimeGenerated, 5m), user_s
| where ExecCount > 10
| project TimeGenerated, user_s, ExecCount
```

---

## 7. Application Security

### 7.1 SE:11 - Secure Development

#### Container Image Security

```dockerfile
# Use minimal base image
FROM eclipse-temurin:17-jre-alpine

# Run as non-root user
RUN addgroup -S yawl && adduser -S yawl -G yawl
USER yawl

# Copy application
COPY --chown=yawl:yawl target/yawl.war /opt/yawl/app.war

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1
```

#### Image Scanning

```bash
# Scan container images with Azure Defender
az security security-solutions-reference-data show \
  --resource-group ${RESOURCE_GROUP} \
  --solution-name Containers

# Check vulnerability assessment
az security task list \
  --resource-group ${RESOURCE_GROUP} \
  --query "[?contains(name, 'Container')]"
```

### 7.2 SE:12 - Input Validation

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
    }
}
```

### 7.3 Web Application Firewall (WAF)

```bicep
resource appGateway 'Microsoft.Network/applicationGateways@2023-04-01' = {
  name: 'yawl-appgw'
  properties: {
    webApplicationFirewallConfiguration: {
      enabled: true
      firewallMode: 'Prevention'
      ruleSetType: 'OWASP'
      ruleSetVersion: '3.2'
      disabledRuleGroups: []
      requestBodyCheck: true
      maxRequestBodySizeInKb: 128
      fileUploadLimitInMb: 100
      exclusions: []
    }
  }
}
```

---

## 8. Compliance Requirements

### 8.1 SOC 2 Type II

| Control | Implementation | Evidence |
|---------|---------------|----------|
| CC6.1 Logical Access | Azure RBAC, Managed Identities | Role assignments |
| CC6.6 Security of Transmission | TLS 1.2+, Application Gateway | SSL configuration |
| CC6.7 Protection of Data | Encryption at rest | Key Vault, CMK |
| CC7.1 Vulnerability Management | Microsoft Defender | Scan reports |
| CC7.2 Anomaly Detection | Azure Sentinel | Alert rules |

### 8.2 GDPR Compliance

| Requirement | Implementation |
|-------------|---------------|
| Data Minimization | Collect only necessary data |
| Right to Access | Customer data export API |
| Right to Erasure | Data deletion workflow |
| Data Portability | Standard export formats |
| Breach Notification | 72-hour notification process |

### 8.3 Azure Security Benchmarks

| Control | Status | Implementation |
|---------|--------|---------------|
| IM-1: Identity Management | Compliant | Azure AD integration |
| IM-3: Managed Identities | Compliant | User-assigned managed identities |
| DP-3: Encryption | Compliant | CMK for all data stores |
| NS-1: Network Security | Compliant | NSGs, Private Endpoints |
| LT-1: Logging | Compliant | Log Analytics, diagnostic settings |

---

## Security Checklist

### Pre-Launch

- [ ] Azure AD integration configured
- [ ] Managed identities for all resources
- [ ] Private endpoints for PaaS services
- [ ] All data encrypted at rest (CMK)
- [ ] TLS 1.2+ enforced on all connections
- [ ] Azure Security Center enabled
- [ ] Microsoft Defender for Containers enabled
- [ ] WAF rules configured on Application Gateway
- [ ] Azure Firewall for egress control
- [ ] Incident response plan documented

### Ongoing

- [ ] Monthly security reviews
- [ ] Quarterly penetration testing
- [ ] Annual compliance audits
- [ ] Continuous vulnerability scanning
- [ ] Regular access reviews
- [ ] Security awareness training
- [ ] Patch management
- [ ] Backup verification

---

*Document Version: 1.0*
*Last Updated: February 2025*
*Next Review: May 2025*
