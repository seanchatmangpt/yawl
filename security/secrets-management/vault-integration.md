# HashiCorp Vault Integration for YAWL

## Overview

This document describes the integration of HashiCorp Vault with YAWL for secure secrets management across multi-cloud deployments.

## Architecture

```
+------------------+     +-------------------+     +------------------+
|   YAWL Engine    |---->|  Kubernetes Auth  |---->|   HashiCorp      |
|   (Pod)          |     |   Method          |     |   Vault          |
+------------------+     +-------------------+     +------------------+
                                                              |
                         +------------------------------------+
                         |
                         v
+------------------+     +-------------------+     +------------------+
|   AWS Secrets    |     |   Azure Key       |     |   GCP Secret     |
|   Manager        |     |   Vault           |     |   Manager        |
+------------------+     +-------------------+     +------------------+
```

## Vault Configuration

### Enable Kubernetes Authentication

```bash
# Enable Kubernetes auth method
vault auth enable kubernetes

# Configure Kubernetes auth
vault write auth/kubernetes/config \
    kubernetes_host="https://kubernetes.default.svc:443" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token
```

### Create Policy for YAWL Engine

```hcl
# vault/policies/yawl-engine.hcl

# Database credentials
path "yawl/data/database" {
  capabilities = ["read"]
}

# LDAP credentials
path "yawl/data/ldap" {
  capabilities = ["read"]
}

# Encryption keys
path "yawl/data/encryption" {
  capabilities = ["read"]
}

# API keys
path "yawl/data/api-keys" {
  capabilities = ["read"]
}

# PKI/cert-manager integration
path "pki/issue/yawl" {
  capabilities = ["update"]
}
```

### Create Policy for YAWL Resource Service

```hcl
# vault/policies/yawl-resource.hcl

# LDAP credentials
path "yawl/data/ldap" {
  capabilities = ["read"]
}

# Resource service secrets
path "yawl/data/resource-service" {
  capabilities = ["read"]
}
```

### Apply Policies

```bash
# Create policies
vault policy write yawl-engine vault/policies/yawl-engine.hcl
vault policy write yawl-resource vault/policies/yawl-resource.hcl

# Create roles for Kubernetes service accounts
vault write auth/kubernetes/role/yawl-engine \
    bound_service_account_names=yawl-engine \
    bound_service_account_namespaces=yawl \
    policies=yawl-engine \
    ttl=1h

vault write auth/kubernetes/role/yawl-resource \
    bound_service_account_namespaces=yawl \
    bound_service_account_names=yawl-resource-service \
    policies=yawl-resource \
    ttl=1h
```

## Secrets Engine Configuration

### KV Secrets Engine v2

```bash
# Enable KV engine
vault secrets enable -path=yawl kv-v2

# Store database credentials
vault kv put yawl/database \
    host="yawl-postgres.database.svc.cluster.local" \
    port="5432" \
    database="yawl" \
    username="yawl_user" \
    password="$(openssl rand -base64 32)"

# Store LDAP credentials
vault kv put yawl/ldap \
    url="ldaps://ldap.example.com:636" \
    bind_dn="cn=yawl,ou=services,dc=example,dc=com" \
    bind_password="$(openssl rand -base64 24)" \
    base_dn="ou=users,dc=example,dc=com" \
    user_filter="(uid={{.username}})" \
    group_filter="(member={{.user_dn}})"

# Store encryption key
vault kv put yawl/encryption \
    key="$(openssl rand -base64 32)" \
    algorithm="AES-256-GCM"
```

### Database Secrets Engine (Dynamic Credentials)

```bash
# Enable database secrets engine
vault secrets enable database

# Configure PostgreSQL connection
vault write database/config/yawl-postgresql \
    plugin_name=postgresql-database-plugin \
    allowed_roles="yawl-app" \
    connection_url="postgresql://{{username}}:{{password}}@yawl-postgres:5432/yawl?sslmode=require" \
    username="vault_admin" \
    password="VAULT_ADMIN_PASSWORD"

# Create role for dynamic credentials
vault write database/roles/yawl-app \
    db_name=yawl-postgresql \
    creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
    default_ttl="1h" \
    max_ttl="24h"
```

### PKI Secrets Engine (TLS Certificates)

```bash
# Enable PKI engine
vault secrets enable pki

# Configure CA
vault write pki/config/ca \
    pem_bundle=@"ca.pem"

# Configure role for YAWL
vault write pki/roles/yawl \
    allowed_domains="yawl.svc.cluster.local,yawl.internal.example.com" \
    allow_subdomains=true \
    max_ttl="720h"

# Issue certificate
vault write pki/issue/yawl \
    common_name="yawl-engine.yawl.svc.cluster.local" \
    ttl="24h"
```

## Kubernetes Integration

### Injector Agent Installation

```yaml
# helm values for vault-helm chart
injector:
  enabled: true
  replicas: 2
  authPath: auth/kubernetes
  logLevel: info

server:
  enabled: true
  ha:
    enabled: true
    replicas: 3
```

### Pod Annotations for Secret Injection

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  template:
    metadata:
      annotations:
        # Enable Vault agent injection
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "yawl-engine"
        vault.hashicorp.com/agent-inject-secret-db: "yawl/data/database"
        vault.hashicorp.com/agent-inject-template-db: |
          {{- with secret "yawl/data/database" -}}
          export POSTGRES_HOST="{{ .Data.data.host }}"
          export POSTGRES_PORT="{{ .Data.data.port }}"
          export POSTGRES_USER="{{ .Data.data.username }}"
          export POSTGRES_PASSWORD="{{ .Data.data.password }}"
          {{- end }}
        vault.hashicorp.com/agent-inject-secret-ldap: "yawl/data/ldap"
        vault.hashicorp.com/agent-inject-template-ldap: |
          {{- with secret "yawl/data/ldap" -}}
          export LDAP_URL="{{ .Data.data.url }}"
          export LDAP_BIND_DN="{{ .Data.data.bind_dn }}"
          export LDAP_BIND_PASSWORD="{{ .Data.data.bind_password }}"
          {{- end }}
    spec:
      serviceAccountName: yawl-engine
      containers:
        - name: engine
          image: yawl/engine:5.2
          command: ["/bin/sh", "-c"]
          args:
            - "source /vault/secrets/db && source /vault/secrets/ldap && /entrypoint.sh"
```

## Transit Secrets Engine (Encryption as a Service)

```bash
# Enable Transit engine
vault secrets enable transit

# Create encryption key
vault write -f transit/keys/yawl

# Configure key rotation
vault write transit/keys/yawl/config \
    min_decryption_version=1 \
    min_encryption_version=2 \
    deletion_allowed=false

# Encrypt data (from application)
curl -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -d '{"plaintext": "SGVsbG8gV29ybGQ="}' \
    https://vault.example.com/v1/transit/encrypt/yawl

# Decrypt data
curl -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -d '{"ciphertext": "vault:v1:..."}' \
    https://vault.example.com/v1/transit/decrypt/yawl
```

## Audit Logging

```bash
# Enable file audit log
vault audit enable file file_path=/var/log/vault/audit.log

# Enable syslog audit
vault audit enable syslog tag="vault"
```

## High Availability Configuration

```hcl
# vault/ha-config.hcl
storage "consul" {
  address = "consul.service.consul:8500"
  path    = "vault"
}

ha_storage "consul" {
  address = "consul.service.consul:8500"
  path    = "vault-ha"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_cert_file = "/etc/vault/tls/cert.pem"
  tls_key_file  = "/etc/vault/tls/key.pem"
}

api_addr = "https://vault.service.consul:8200"
cluster_addr = "https://vault.service.consul:8201"
```

## Disaster Recovery

### Backup

```bash
# Backup Vault data (requires root token)
vault operator raft snapshot save vault-backup.snap

# Or with GCS
vault operator raft snapshot save -format=json - | gsutil cp - gs://vault-backups/$(date +%Y%m%d-%H%M%S).snap
```

### Restore

```bash
# Restore from snapshot
vault operator raft snapshot restore vault-backup.snap
```

## Security Hardening

### TLS Configuration

```hcl
listener "tcp" {
  address       = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"

  tls_cert_file   = "/etc/vault/tls/cert.pem"
  tls_key_file    = "/etc/vault/tls/key.pem"
  tls_client_ca_file = "/etc/vault/tls/ca.pem"

  tls_min_version = "tls12"
  tls_cipher_suites = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
}
```

### Disable Root Token

```bash
# Generate one-time password for unseal
vault operator generate-root -init

# Cancel root token after use
vault token revoke <root-token>
```

## Monitoring and Alerting

### Prometheus Metrics

Vault exposes metrics at `/v1/sys/metrics` (requires appropriate policy).

```yaml
# Prometheus scrape config
scrape_configs:
  - job_name: vault
    metrics_path: /v1/sys/metrics
    params:
      format: ['prometheus']
    static_configs:
      - targets:
          - vault-0.vault-internal:8200
          - vault-1.vault-internal:8200
          - vault-2.vault-internal:8200
```

### Key Alerts

```yaml
groups:
  - name: vault
    rules:
      - alert: VaultSealed
        expr: vault_core_unsealed == 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Vault node is sealed"

      - alert: VaultHighLatency
        expr: histogram_quantile(0.99, vault_barrier_request_duration_seconds_bucket) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Vault latency is high"
