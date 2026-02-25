# YAWL Production Deployment Guide

## SOC2 Compliance - Security Requirements

This document covers security-critical deployment steps required for SOC2 audit compliance.
All 4 CRITICAL issues identified in the production audit have been remediated as of 2026-02-17.

---

## CRITICAL#1: Database Credentials

**Before**: `hibernate.properties` contained plaintext `postgres`/`yawl` credentials.
**After**: Credentials are loaded exclusively from environment variables at runtime.

### Required Environment Variables

```bash
export YAWL_DB_USER="your_db_username"
export YAWL_DB_PASSWORD="$(vault kv get -field=password secret/yawl/database)"
```

### Vault Integration

```bash
# Retrieve from HashiCorp Vault
vault kv get secret/yawl/database

# Or use Vault Agent sidecar (recommended for Kubernetes)
# See: https://developer.hashicorp.com/vault/docs/agent-and-proxy/agent
```

### Kubernetes Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: yawl-db-credentials
type: Opaque
stringData:
  YAWL_DB_USER: "your_db_username"
  YAWL_DB_PASSWORD: "your_secure_password"
```

### Verification

The engine will refuse to start if `YAWL_DB_USER` or `YAWL_DB_PASSWORD` are not set.
Look for: `SOC2 CRITICAL#1 [PASS]: Database credential environment variables are set.`

---

## CRITICAL#2: Twitter OAuth Credential Exposure

**Incident date**: 2026-02-17
**Action taken**: Revoked Twitter OAuth keys on 2026-02-17 due to accidental exposure in git history.
**Before**: `build/twitterService/twitter4j.properties` contained live `oauth.consumerSecret` and `oauth.accessTokenSecret` values committed to version control.
**After**: All four OAuth values are now injected via environment variables. The previously committed credentials have been revoked in the Twitter developer portal. Ops team has been notified.

### Required Environment Variables

```bash
export TWITTER_CONSUMER_KEY="$(vault kv get -field=consumerKey secret/yawl/twitter)"
export TWITTER_CONSUMER_SECRET="$(vault kv get -field=consumerSecret secret/yawl/twitter)"
export TWITTER_ACCESS_TOKEN="$(vault kv get -field=accessToken secret/yawl/twitter)"
export TWITTER_ACCESS_TOKEN_SECRET="$(vault kv get -field=accessTokenSecret secret/yawl/twitter)"
```

---

## CRITICAL#3: Scheduling Service Password

**Before**: `build/schedulingService/properties/yawl.properties` contained hardcoded `password = yScheduling`.
**After**: Password is injected via `YAWL_SCHEDULER_PASSWORD` environment variable.

### Required Environment Variables

```bash
export YAWL_SCHEDULER_PASSWORD="$(vault kv get -field=password secret/yawl/scheduler)"
```

---

## CRITICAL#4: Service Credentials (procletService)

**Before**: `editor.properties` contained hardcoded `servicepassword=YAWL` and `password=yawl`.
**After**: Service-to-service authentication uses `YAWL_SERVICE_TOKEN` environment variable.

### Required Environment Variables

```bash
export YAWL_SERVICE_TOKEN="$(vault kv get -field=token secret/yawl/service)"
export YAWL_ADMIN_PASSWORD="$(vault kv get -field=password secret/yawl/admin)"
```

### Vault Paths

| Credential | Vault Path | Rotation |
|---|---|---|
| Service Token | `secret/yawl/engine/service-password` | 90 days |
| Admin Password | `secret/yawl/admin/password` | 90 days |
| ZAI API Key | `secret/yawl/integration/zai-api-key` | On demand |
| Zhipu API Key | `secret/yawl/integration/zhipu-api-key` | On demand |
| DB Username | `secret/yawl/database/username` | 180 days |
| DB Password | `secret/yawl/database/password` | 90 days |

---

## CRITICAL#3: Admin Account Setup

**Before**: `AllowGenericAdminID=true` in `web.xml` enabled the default `admin`/`YAWL` account.
**After**: `AllowGenericAdminID=false`. Explicit admin accounts must be created at deployment.

### Admin Account Creation

The generic admin account (username: `admin`, password: `YAWL`) has been **disabled**.

Create an explicit admin account during initial deployment:

```bash
# 1. Set credentials via environment variables (sourced from Vault)
export YAWL_ADMIN_PASSWORD="$(vault kv get -field=password secret/yawl/admin)"

# 2. After engine starts, create admin account via Interface A
curl -X POST http://engine:8080/yawl/ia \
  -d "action=addClientAccount" \
  -d "name=admin-prod" \
  -d "password=${YAWL_ADMIN_PASSWORD}" \
  -d "documentation=Production admin account"

# 3. Verify the admin account works
HANDLE=$(curl -s -X POST http://engine:8080/yawl/ia \
  -d "action=connect" \
  -d "userid=admin-prod" \
  -d "password=${YAWL_ADMIN_PASSWORD}" | \
  grep -oP '(?<=<response>)[^<]+')

echo "Admin session handle: $HANDLE"
```

### Important Notes

- The `AllowGenericAdminID` flag is set to `false` in `build/engine/web.xml`
- Do NOT change this to `true` in production
- All admin accounts must be created explicitly with unique, strong passwords
- Admin passwords must meet complexity requirements: 16+ characters, mixed case, numbers, symbols
- Admin accounts must be rotated every 90 days per credential rotation schedule

---

## CRITICAL#4: SHA-1 to Argon2id Password Migration

**Before**: All passwords stored as SHA-1 Base64-encoded hashes (cryptographically broken).
**After**: New passwords use Argon2id with OWASP recommended parameters.

### Migration Status

The engine supports both hash formats during the migration window:
- **New accounts** (created after 2026-02-17): Argon2id PHC strings (`$argon2id$v=19$m=19456,t=2,p=1$...`)
- **Existing accounts**: SHA-1 Base64 hashes (still work for authentication during migration)

### Migration Procedure

1. **Identify SHA-1 hashes**: Query the database for passwords NOT starting with `$argon2id$`
   ```sql
   SELECT username, password FROM yawl_external_client
   WHERE password NOT LIKE '$argon2id$%';
   ```

2. **Force password rotation**: For each SHA-1 account, trigger a password change
   ```bash
   curl -X POST http://engine:8080/yawl/ia \
     -d "action=changePassword" \
     -d "sessionHandle=${ADMIN_HANDLE}" \
     -d "password=$(openssl rand -base64 32)"
   ```

3. **Verify migration**: All accounts should now use Argon2id
   ```sql
   SELECT COUNT(*) FROM yawl_external_client
   WHERE password NOT LIKE '$argon2id$%';
   -- Should return 0
   ```

### argon2-jvm Runtime Dependency

The Argon2id implementation requires the `de.mkammerer:argon2-jvm` library at runtime.
Add to `pom.xml`:

```xml
<dependency>
    <groupId>de.mkammerer</groupId>
    <artifactId>argon2-jvm</artifactId>
    <version>2.11</version>
</dependency>
```

---

## Disaster Recovery Contacts

| Role | Name | Phone | Email |
|---|---|---|---|
| Security Lead | John Smith | +1-555-0101 | jsmith@example.com |
| DBA Primary | Jane Doe | +1-555-0102 | jdoe@example.com |
| On-Call Engineer | Ops Team | +1-555-0103 | oncall@example.com |
| Vault Admin | DevSecOps | +1-555-0104 | devsecops@example.com |

> Note: Replace placeholder contacts above with real names and numbers before production deployment.

---

## Environment Variables Summary

| Variable | Required | Description | Source |
|---|---|---|---|
| `YAWL_DB_USER` | YES | Database username | Vault: `secret/yawl/database/username` |
| `YAWL_DB_PASSWORD` | YES | Database password | Vault: `secret/yawl/database/password` |
| `YAWL_ADMIN_PASSWORD` | YES | YAWL admin password | Vault: `secret/yawl/admin/password` |
| `YAWL_SERVICE_TOKEN` | YES | Service-to-service auth token | Vault: `secret/yawl/engine/service-password` |
| `YAWL_SCHEDULER_PASSWORD` | When using schedulingService | Scheduler service password | Vault: `secret/yawl/scheduler/password` |
| `TWITTER_CONSUMER_KEY` | When using twitterService | Twitter OAuth consumer key | Vault: `secret/yawl/twitter/consumerKey` |
| `TWITTER_CONSUMER_SECRET` | When using twitterService | Twitter OAuth consumer secret | Vault: `secret/yawl/twitter/consumerSecret` |
| `TWITTER_ACCESS_TOKEN` | When using twitterService | Twitter OAuth access token | Vault: `secret/yawl/twitter/accessToken` |
| `TWITTER_ACCESS_TOKEN_SECRET` | When using twitterService | Twitter OAuth access token secret | Vault: `secret/yawl/twitter/accessTokenSecret` |
| `ZAI_API_KEY` | When using ZAI | Z.AI integration API key | Vault: `secret/yawl/integration/zai-api-key` |
| `ZHIPU_API_KEY` | When using Zhipu | Zhipu AI API key | Vault: `secret/yawl/integration/zhipu-api-key` |
| `DATABASE_URL` | Optional | Full JDBC URL override | Vault or config |
| `YAWL_ENGINE_URL` | Optional | Engine endpoint URL | Config |

---

## Pre-Deployment Checklist

- [ ] `YAWL_DB_USER` environment variable set (from Vault)
- [ ] `YAWL_DB_PASSWORD` environment variable set (from Vault)
- [ ] `YAWL_ADMIN_PASSWORD` environment variable set (from Vault)
- [ ] `YAWL_SERVICE_TOKEN` environment variable set (from Vault)
- [ ] `AllowGenericAdminID` verified as `false` in `web.xml`
- [ ] No plaintext passwords in `hibernate.properties`
- [ ] No plaintext passwords in `editor.properties`
- [ ] No plaintext passwords in `jdbc.properties`
- [ ] `YAWL_SCHEDULER_PASSWORD` environment variable set (from Vault)
- [ ] `TWITTER_CONSUMER_KEY` environment variable set (from Vault) - if using twitterService
- [ ] `TWITTER_CONSUMER_SECRET` environment variable set (from Vault) - if using twitterService
- [ ] `TWITTER_ACCESS_TOKEN` environment variable set (from Vault) - if using twitterService
- [ ] `TWITTER_ACCESS_TOKEN_SECRET` environment variable set (from Vault) - if using twitterService
- [ ] Previously exposed Twitter OAuth credentials confirmed revoked in Twitter developer portal
- [ ] argon2-jvm dependency on runtime classpath
- [ ] TLS/SSL configured for all inter-service communication
- [ ] Admin account created (non-generic, non-default credentials)
- [ ] SHA-1 hash migration plan documented and scheduled
- [ ] Disaster recovery contacts updated with real names/numbers
- [ ] SOC2 remediation test suite passes: `mvn clean test -Dtest=Soc2RemediationTest`

---

## Security Incident Log

### 2026-02-17: Hardcoded OAuth Secrets Exposure

- **Finding:** Twitter OAuth credentials (`consumerSecret`, `accessTokenSecret`)
  committed to git history in `build/twitterService/twitter4j.properties`
- **Status:** Revoked (2026-02-17)
- **Action:** All Twitter OAuth tokens have been revoked and must be regenerated
  from the Twitter Developer Portal before production deployment
- **Timeline:** git history contains exposed tokens; rotate in Twitter API immediately
