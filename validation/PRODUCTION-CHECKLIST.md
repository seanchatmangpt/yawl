# YAWL v6.0.0 Production Deployment Checklist

**Date:** 2026-02-16  
**Session:** claude/update-libraries-fix-tests-Vw4Si  
**Validator:** prod-val agent

---

## Current Status: ❌ 3/13 Complete

### Phase 1: Environment Setup (BLOCKED)

- [ ] **Java 25 Installation**
  ```bash
  sudo apt-get update
  sudo apt-get install openjdk-25-jdk
  sudo update-alternatives --config java
  java -version  # Should show Java 25
  ```

- [ ] **Maven Dependency Cache**
  ```bash
  # Enable network access temporarily
  mvn dependency:go-offline -Pprod,java25
  mvn dependency:resolve-plugins
  ```

- [ ] **Fix POM Configuration**
  ```bash
  # Remove duplicate Spring Boot entries
  sed -i '595,605d' pom.xml
  git diff pom.xml  # Verify changes
  ```

### Phase 2: Build & Test (BLOCKED)

- [ ] **Compile Source Code**
  ```bash
  mvn clean compile -Pprod
  # Expected: BUILD SUCCESS
  ```

- [ ] **Run Unit Tests**
  ```bash
  mvn clean test
  # Expected: 0 failures, 0 errors
  # Check: target/surefire-reports/
  ```

- [ ] **Build WAR Files**
  ```bash
  mvn clean package -Pprod
  # Expected output:
  # - yawl-utilities/target/yawl-utilities-5.2.jar
  # - yawl-engine/target/yawl-engine-5.2.jar
  # - yawl-webapps/yawl-engine-webapp/target/yawl-engine.war
  ```

### Phase 3: Security Validation (PARTIAL)

- [x] **HYPER_STANDARDS Compliance**
  - Zero TODO/FIXME markers ✓
  - Zero mock/stub implementations ✓
  - Code quality enforced ✓

- [ ] **OWASP Dependency Check**
  ```bash
  mvn org.owasp:dependency-check-maven:check -Pprod
  # Review: target/dependency-check-report.html
  # Accept: CVSS < 7.0 (with justification)
  # Reject: CVSS >= 7.0 (must fix or mitigate)
  ```

- [x] **Hardcoded Secrets Scan**
  - No API keys found ✓
  - Development password acceptable ✓
  - Production configs use env vars ✓

- [ ] **Fix Development Credentials**
  ```bash
  # Move jdbc.properties to .env.example
  mv src/jdbc.properties .env.example
  # Update references to use environment variables
  ```

### Phase 4: Configuration Validation (NOT STARTED)

- [ ] **Database Configuration**
  ```bash
  # Verify Hibernate properties
  cat */src/main/resources/hibernate.properties
  # Check for ${DB_URL}, ${DB_USERNAME}, ${DB_PASSWORD}
  ```

- [ ] **Environment Variables**
  ```bash
  # Required variables:
  export YAWL_ENGINE_URL="https://engine.yawl.example.com"
  export YAWL_USERNAME="admin"
  export YAWL_PASSWORD="$(vault read secret/yawl/admin-password)"
  export DATABASE_URL="jdbc:postgresql://db.example.com:5432/yawl_prod"
  export DATABASE_PASSWORD="$(vault read secret/yawl/db-password)"
  
  # Verify all are set
  env | grep YAWL
  ```

- [ ] **SSL/TLS Configuration**
  ```bash
  # Verify keystore exists
  ls -l config/keystore.jks
  # Check permissions (should be 600)
  # Verify certificate expiration
  keytool -list -v -keystore config/keystore.jks
  ```

### Phase 5: Containerization (NOT STARTED)

- [ ] **Docker Image Build**
  ```bash
  docker build -t yawl:5.2 -t yawl:latest .
  # Expected: Successfully built <image-id>
  ```

- [ ] **Docker Compose Test**
  ```bash
  docker-compose up -d
  docker-compose ps  # All services should be "Up"
  docker-compose logs yawl | grep ERROR  # Should be empty
  ```

- [ ] **Health Check Validation**
  ```bash
  curl http://localhost:8080/health
  # Expected: {"status":"UP"}
  
  curl http://localhost:8080/health/ready
  # Expected: 200 OK
  
  curl http://localhost:8080/health/live
  # Expected: 200 OK
  ```

### Phase 6: Kubernetes Validation (NOT STARTED)

- [ ] **Manifest Validation**
  ```bash
  kubectl apply --dry-run=client -f ci-cd/k8s/
  # Expected: No errors
  ```

- [ ] **Deploy to Staging**
  ```bash
  kubectl apply -f ci-cd/k8s/ --namespace=yawl-staging
  kubectl rollout status deployment/yawl-engine -n yawl-staging
  ```

- [ ] **Smoke Tests (Staging)**
  ```bash
  # Get ingress URL
  STAGING_URL=$(kubectl get ingress yawl -n yawl-staging -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
  
  # Test endpoints
  curl https://$STAGING_URL/health
  curl https://$STAGING_URL/yawl/ia
  curl https://$STAGING_URL/yawl/ib
  ```

### Phase 7: Performance Testing (NOT STARTED)

- [ ] **Startup Time**
  ```bash
  # Measure engine startup
  # Target: < 60 seconds
  time docker-compose up -d yawl
  docker-compose logs yawl | grep "Started YEngine"
  ```

- [ ] **Latency Benchmarks**
  ```bash
  # Case creation: < 500ms
  # Work item checkout: < 200ms
  ./scripts/run-performance-tests.sh
  # Review: reports/performance-benchmark-*.html
  ```

- [ ] **Load Testing**
  ```bash
  # Use JMeter or Gatling
  # Target: 100 concurrent users, 1000 req/sec
  cd load-tests
  ./run-load-test.sh --users=100 --duration=300
  ```

### Phase 8: Production Deployment (NOT STARTED)

- [ ] **Backup Verification**
  ```bash
  # Ensure backups are current
  ./scripts/backup-before-deploy.sh
  # Verify backup integrity
  ```

- [ ] **Deploy to Production**
  ```bash
  kubectl apply -f ci-cd/k8s/ --namespace=yawl-production
  kubectl rollout status deployment/yawl-engine -n yawl-production
  ```

- [ ] **Post-Deployment Verification**
  ```bash
  # Verify all pods running
  kubectl get pods -n yawl-production
  
  # Check logs for errors
  kubectl logs -n yawl-production -l app=yawl --tail=100
  
  # Test production endpoints
  curl https://yawl.example.com/health
  ```

- [ ] **Monitoring & Alerting**
  ```bash
  # Verify Prometheus scraping
  # Verify Grafana dashboards
  # Test alert rules
  ```

---

## Rollback Plan

If any of the following occur, execute rollback immediately:

### Trigger Conditions
- [ ] Any test failures
- [ ] CVSS >= 7.0 vulnerabilities
- [ ] Performance degradation > 20%
- [ ] Error rate > 1%
- [ ] Health checks failing

### Rollback Procedure
```bash
# 1. Revert to previous version
kubectl rollout undo deployment/yawl-engine -n yawl-production

# 2. Verify rollback
kubectl rollout status deployment/yawl-engine -n yawl-production

# 3. Smoke test
curl https://yawl.example.com/health

# 4. Notify team
# Send incident report with rollback details
```

---

## Sign-Off

### Development Team
- [ ] Code review complete
- [ ] All tests passing
- [ ] Documentation updated

### Security Team
- [ ] No critical vulnerabilities
- [ ] Secrets properly managed
- [ ] Penetration testing (if required)

### Operations Team
- [ ] Deployment runbook ready
- [ ] Monitoring configured
- [ ] Rollback tested

### Product Owner
- [ ] Business requirements met
- [ ] User acceptance complete
- [ ] Go-live approved

---

## Post-Deployment

### Monitoring (First 24 hours)
- [ ] Monitor error rates (target: < 0.1%)
- [ ] Monitor response times (target: p95 < 500ms)
- [ ] Monitor resource usage (CPU < 70%, Memory < 80%)
- [ ] Review logs for anomalies

### Week 1 Review
- [ ] Performance metrics stable
- [ ] No critical issues reported
- [ ] User feedback collected
- [ ] Lessons learned documented

---

## Notes

**Critical Blockers (Must Fix):**
1. Java 25 installation
2. Maven offline environment
3. POM duplicate entries

**Estimated Time to Complete:** 4-6 hours  
**Recommended Deployment Window:** Off-peak hours (2-6 AM UTC)

---

**Checklist Owner:** DevOps Team  
**Last Updated:** 2026-02-16  
**Session:** https://claude.ai/code/session_0122HyXHf6DvPaRKdh9UgqtJ
