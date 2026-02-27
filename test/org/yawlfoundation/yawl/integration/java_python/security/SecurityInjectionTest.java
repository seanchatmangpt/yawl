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
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.integration.java_python.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.java_python.ValidationTestBase;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive security injection tests for Java-Python integration.
 * Focuses specifically on preventing various types of injection attacks
 * while maintaining proper sandbox isolation.
 *
 * <p>Tests OWASP Top 10 A03:2021 - Injection attacks targeting Java-Python
 * integration. Validates that path traversal, shell injection, code injection,
 * and sandbox isolation are properly enforced.</p>
 *
 * <p>Chicago TDD: Real security validation with actual Python execution.
 * No mocks, no stubs, no placeholder implementations.</p>
 *
 * @author YAWL Development Team
 * @since 6.0
 */
@Tag("security")
@Tag("integration")
public class SecurityInjectionTest extends ValidationTestBase {

    private static final String TEST_RESOURCE_DIR = "test_resources";
    private static final String SANDBOX_ROOT = "/tmp/yawl_sandbox";

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(graalpyAvailable, "GraalPy required for injection tests");

        // Create test resource directory if it doesn't exist
        Path testResourcePath = Paths.get(TEST_RESOURCE_DIR);
        if (!testResourcePath.toFile().exists()) {
            testResourcePath.toFile().mkdirs();
        }

        // Clean up sandbox directory before each test
        Path sandboxPath = Paths.get(SANDBOX_ROOT);
        if (sandboxPath.toFile().exists()) {
            sandboxPath.toFile().deleteOnExit();
        }
    }

    @Nested
    @DisplayName("Path Traversal Prevention Tests")
    @Tag("unit")
    class PathTraversalPreventionTests {

        @Test
        @DisplayName("Unix absolute paths")
        void testUnixAbsolutePaths() throws Exception {
        String[] maliciousPaths = {
            "../../../etc/passwd",
            "../../../../../etc/passwd",
            "../../../../../../../../etc/passwd",
            "/../../etc/passwd",
            "/etc/passwd",
            "/etc/shadow"
        };

        for (String path : maliciousPaths) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode("open('" + path + "', 'r').read()");
            }, "Absolute path access should be blocked: " + path);
        }
    }

        @Test
        @DisplayName("Windows paths")
        void testWindowsPaths() throws Exception {
        String[] maliciousWindowsPaths = {
            "..\\..\\..\\windows\\system32\\config\\sam",
            "..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            "..\\..\\..\\Program Files\\",
            "C:\\..\\..\\windows\\system32\\config\\sam",
            "..\\..\\..\\boot.ini"
        };

        for (String path : maliciousWindowsPaths) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(f"open(r'{path}', 'r').read()");
            }, "Windows path access should be blocked: " + path);
        }
    }

    @Test
    @DisplayName("Path traversal prevention: URL-encoded paths")
    @Order(3)
    void testPathTraversalPrevention_UrlEncodedPaths() throws Exception {
        String[] encodedPaths = {
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fshadow",
            "..%252f..%252f..%252fetc%2fpasswd",
            "%252e%252e%252f%252e%252e%252f%252e%252e%252fetc%2fpasswd"
        };

        for (String path : encodedPaths) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode("open('" + path + "', 'r').read()");
            }, "URL-encoded path access should be blocked: " + path);
        }
    }

    @Test
    @DisplayName("Path traversal prevention: Mixed traversal techniques")
    @Order(4)
    void testPathTraversalPrevention_MixedTraversal() throws Exception {
        String[] mixedPaths = {
            "../../../%2e%2e%2fetc/passwd",
            "..\\..\\../etc/passwd",
            "/etc/passwd/../../../etc/passwd",
            "....//....//....//etc/passwd",
            "/etc/././././../etc/passwd"
        };

        for (String path : mixedPaths) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode("open('" + path + "', 'r').read()");
            }, "Mixed traversal technique should be blocked: " + path);
        }
    }

    @Test
    @DisplayName("Path traversal prevention: Sandbox directory enforcement")
    @Order(5)
    void testPathTraversalPrevention_SandboxEnforcement() throws Exception {
        // Test that access outside sandbox root is blocked
        assertThrows(SecurityException.class, () -> {
            executePythonCode("open('/" + SANDBOX_ROOT + "/malicious.txt', 'w').write('test')");
        }, "Access outside sandbox directory should be blocked");

        // Test that within sandbox is allowed
        Object result = executePythonCode("open('" + SANDBOX_ROOT + "/test.txt', 'w').write('test'); open('" + SANDBOX_ROOT + "/test.txt', 'r').read()");
        assertEquals("test", result, "Valid sandbox access should work");
    }

    // ===================================================================================
    // SHELL INJECTION PREVENTION TESTS
    // ===================================================================================

    @Test
    @DisplayName("Shell injection prevention: Command separation")
    @Order(6)
    void testShellInjectionPrevention_CommandSeparation() throws Exception {
        String[] commandSeparations = {
            "; rm -rf /",
            "&& rm -rf /",
            "|| rm -rf /",
            "| rm -rf /",
            "&& cat /etc/passwd",
            "|| cat /etc/passwd",
            "; cat /etc/passwd",
            "| cat /etc/passwd"
        };

        for (String command : commandSeparations) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(f"import subprocess; subprocess.run(['echo', '{command}'], shell=True)");
            }, "Command separation should be blocked: " + command);
        }
    }

    @Test
    @DisplayName("Shell injection prevention: Process substitution")
    @Order(7)
    void testShellInjectionPrevention_ProcessSubstitution() throws Exception {
        String[] processSubstitutions = {
            "$(cat /etc/passwd)",
            "`cat /etc/passwd`",
            "$(echo 'test; rm -rf /')",
            "`echo 'test; rm -rf /'`"
        };

        for (String substitution : processSubstitutions) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(f"import subprocess; subprocess.run(['echo', '{substitution}'], shell=True)");
            }, "Process substitution should be blocked: " + substitution);
        }
    }

    @Test
 @DisplayName("Shell injection prevention: Redirection and pipes")
    @Order(8)
    void testShellInjectionPrevention_RedirectionPipes() throws Exception {
        String[] redirections = {
            "cat /etc/passwd > /tmp/malicious.txt",
            "cat /etc/passwd >> /tmp/malicious.txt",
            "cat /etc/passwd | grep root",
            "cat /etc/passwd 2>&1"
        };

        for (String redirection : redirections) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(f"import subprocess; subprocess.run(['echo', '{redirection}'], shell=True)");
            }, "Redirection should be blocked: " + redirection);
        }
    }

    @Test
    @DisplayName("Shell injection prevention: Background processes")
    @Order(9)
    void testShellInjectionPrevention_BackgroundProcesses() throws Exception {
        String[] backgroundProcesses = {
            "cat /etc/passwd &",
            "sleep 10 &",
            "rm -rf / &",
            "nohup cat /etc/passwd &"
        };

        for (String process : backgroundProcesses) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(f"import subprocess; subprocess.run(['echo', '{process}'], shell=True)");
            }, "Background processes should be blocked: " + process);
        }
    }

    // ===================================================================================
    // CODE INJECTION PREVENTION TESTS
    // ===================================================================================

    @Test
    @DisplayName("Code injection prevention: Built-in dangerous functions")
    @Order(10)
    void testCodeInjectionPrevention_BuiltInFunctions() throws Exception {
        String[] dangerousFunctions = {
            "__import__('os').system('cat /etc/passwd')",
            "__import__('subprocess').call(['rm', '-rf', '/'])",
            "__import__('sys').exit()",
            "__import__('os').environ['PATH']",
            "__import__('socket').socket()",
            "__import__('threading').Thread(target=lambda: None)"
        };

        for (String dangerousCode : dangerousFunctions) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(dangerousCode);
            }, "Dangerous function calls should be blocked: " + dangerousCode);
        }
    }

    @Test
    @DisplayName("Code injection prevention: Eval and exec")
    @Order(11)
    void testCodeInjectionPrevention_EvalExec() throws Exception {
        String[] evalExecCases = {
            "eval('__import__(\"os\").system(\"id\")')",
            "exec('import os; os.system(\"id\")')",
            "eval('print(\"dangerous\")')",
            "exec('import subprocess; subprocess.run([\"ls\", \"/etc\"])')",
            "compile('import os', '<string>', 'exec')"
        };

        for (String code : evalExecCases) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(code);
            }, "Eval/exec usage should be blocked: " + code);
        }
    }

    @Test
    @DisplayName("Code injection prevention: Lambda and generator expressions")
    @Order(12)
    void testCodeInjectionPrevention_LambdaGenerator() throws Exception {
        String[] lambdaGeneratorCases = {
            "lambda: __import__('os').system('id')",
            "(lambda x: x)(__import__('os'))",
            "(x for x in __import__('os').listdir('/'))",
            "[x for x in __import__('os').environ]"
        };

        for (String code : lambdaGeneratorCases) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(code);
            }, "Lambda/generator injection should be blocked: " + code);
        }
    }

    @Test
    @DisplayName("Code injection prevention: String manipulation exploits")
    @Order(13)
    void testCodeInjectionPrevention_StringManipulation() throws Exception {
        String[] stringManipulationCases = {
            "\"import os\".format()",
            "f\"import os\"",
            "str.__class__.__mro__[1].__subclasses__()",
            "().__class__.__bases__[0].__subclasses__()",
            "globals()['__builtins__']['open']('/etc/passwd')",
            "open.__class__.__module__ + '.' + open.__class__.__name__"
        };

        for (String code : stringManipulationCases) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(code);
            }, "String manipulation exploits should be blocked: " + code);
        }
    }

    // ===================================================================================
    // PYTHON SANDBOX ISOLATION TESTS
    // ===================================================================================

    @Test
    @DisplayName("Python sandbox isolation: System access prevention")
    @Order(14)
    void testPythonSandboxIsolation_SystemAccess() throws Exception {
        // Test Java system access
        assertThrows(SecurityException.class, () -> {
            executePythonCode("import java.lang.System; System.exit(0)");
        }, "Java System access should be blocked");

        // Test Python system access
        assertThrows(SecurityException.class, () -> {
            executePythonCode("__import__('sys').exit()");
        }, "Python sys access should be blocked");
    }

    @Test
    @DisplayName("Python sandbox isolation: File system access")
    @Order(15)
    void testPythonSandboxIsolation_FileSystemAccess() throws Exception {
        String[] restrictedFiles = {
            "/etc/passwd",
            "/etc/shadow",
            "/etc/hosts",
            "/etc/hostname",
            "/proc/self/environ",
            "/proc/self/cmdline"
        };

        for (String file : restrictedFiles) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(f"open('{file}', 'r').read()");
            }, "File access should be blocked: " + file);
        }
    }

    @Test
    @DisplayName("Python sandbox isolation: Network access")
    @Order(16)
    void testPythonSandboxIsolation_NetworkAccess() throws Exception {
        String[] networkAccess = {
            "import socket; socket.socket().connect(('evil.com', 80))",
            "import urllib.request; urllib.request.urlopen('http://evil.com')",
            "import requests; requests.get('http://evil.com')",
            "import http.client; http.client.HTTPConnection('evil.com')"
        };

        for (String code : networkAccess) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(code);
            }, "Network access should be blocked: " + code);
        }
    }

    @Test
    @DisplayName("Python sandbox isolation: Process execution")
    @Order(17)
    void testPythonSandboxIsolation_ProcessExecution() throws Exception {
        String[] processExecution = {
            "import subprocess; subprocess.run(['ls', '-la'])",
            "import os; os.system('ls')",
            "import os; os.popen('ls')",
            "import subprocess; subprocess.Popen(['ls'])"
        };

        for (String code : processExecution) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(code);
            }, "Process execution should be blocked: " + code);
        }
    }

    // ===================================================================================
    // RESOURCE ACCESS CONTROL TESTS
    // ===================================================================================

    @Test
    @DisplayName("Resource access control: Memory limits")
    @Order(18)
    void testResourceAccessControl_MemoryLimits() throws Exception {
        // Test large memory allocation
        assertThrows(SecurityException.class, () -> {
            executePythonCode("x = 'A' * 1000000000"); // 1GB allocation
        }, "Memory limit should be enforced");

        // Test memory limit for list
        assertThrows(SecurityException.class, () -> {
            executePythonCode("x = ['A' * 1000] * 1000000"); // Large list
        }, "Memory limit should be enforced for large collections");
    }

    @Test
    @DisplayName("Resource access control: Time limits")
    @Order(19)
    void testResourceAccessControl_TimeLimits() throws Exception {
        // Test infinite loop detection
        assertThrows(SecurityException.class, () -> {
            executePythonCode("while True: pass");
        }, "CPU time limit should be enforced for infinite loops");

        // Test long computation
        assertThrows(SecurityException.class, () -> {
            executePythonCode("sum(i**1000 for i in range(100000))");
        }, "CPU time limit should be enforced for long computations");
    }

    @Test
    @DisplayName("Resource access control: Thread/process limits")
    @Order(20)
    void testResourceAccessControl_ThreadLimits() throws Exception {
        // Test thread creation
        assertThrows(SecurityException.class, () -> {
            executePythonCode("import threading; threading.Thread(target=lambda: None).start()");
        }, "Thread creation should be blocked");

        // Test multiprocessing
        assertThrows(SecurityException.class, () -> {
            executePythonCode("import multiprocessing; multiprocessing.Process(target=lambda: None).start()");
        }, "Process creation should be blocked");
    }

    @Test
    @DisplayName("Resource access control: Module import restrictions")
    @Order(21)
    void testResourceAccessControl_ModuleImportRestrictions() throws Exception {
        String[] restrictedModules = {
            "os", "sys", "subprocess", "socket", "ctypes",
            "multiprocessing", "threading", "concurrent", "urllib",
            "urllib2", "requests", "http", "ftplib", "telnetlib",
            "pickle", "shelve", "sqlite3", "psycopg2", "mysql",
            "pymongo", "redis", "kazoo", "zk", "kafka", "pika",
            "selenium", "pyautogui", "keyboard", "mouse",
            "numpy", "pandas", "scipy", "matplotlib", "tensorflow", "torch"
        };

        for (String module : restrictedModules) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(f"import {module}");
            }, "Import of restricted module should be blocked: " + module);
        }
    }

    @Test
    @DisplayName("Resource access control: Allowed modules")
    @Order(22)
    void testResourceAccessControl_AllowedModules() throws Exception {
        // Test that safe modules can be imported
        Object result = executePythonCode("import math; math.sqrt(16)");
        assertEquals(4.0, result, "Safe math module should be allowed");

        // Test string operations
        result = executePythonCode("x = 'hello'; y = x.upper(); y");
        assertEquals("HELLO", result, "String operations should be allowed");

        // Test list operations
        result = executePythonCode("x = [1, 2, 3]; y = len(x); y");
        assertEquals(3, result, "List operations should be allowed");
    }

    @Test
    @DisplayName("Security: Code injection prevention through string formatting")
    @Order(23)
    void testCodeInjectionPrevention_StringFormatting() throws Exception {
        String[] formattingExploits = {
            "__import__('os').system('{0}id{0}'.format('.'))",
            "__import__('os').system(f'cat /etc/passwd')",
            "globals()['__builtins__']['open']('/etc/passwd')",
            "(lambda x: x)(__import__('os'))"
        };

        for (String exploit : formattingExploits) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(exploit);
            }, "String formatting exploit should be blocked: " + exploit);
        }
    }

    @Test
    @DisplayName("Security: Python introspection restrictions")
    @Order(24)
    void testSecurity_IntrospectionRestrictions() throws Exception {
        String[] introspectionExploits = {
            "object.__class__.__bases__[1].__subclasses__()",
            "[x for x in object.__subclasses__() if 'exec' in x.__name__]",
            "().__class__.__bases__[0].__subclasses__()",
            "().__class__.__mro__[1].__subclasses__()",
            "''.__class__.__mro__[1].__subclasses__()"
        };

        for (String exploit : introspectionExploits) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(exploit);
            }, "Introspection exploit should be blocked: " + exploit);
        }
    }

    @Test
    @DisplayName("Security: Bypass attempts through multiple import methods")
    @Order(25)
    void testSecurity_BypassAttempts_MultipleImportMethods() throws Exception {
        String[] bypassAttempts = {
            "import builtins; builtins.open('/etc/passwd')",
            "import __builtin__; __builtin__.open('/etc/passwd')",
            "import sys; __import__(sys.modules['builtins'].__name__).open('/etc/passwd')",
            "getattr(__import__('builtins'), 'open')('/etc/passwd')",
            "__import__('os').__dict__['system']('cat /etc/passwd')"
        };

        for (String attempt : bypassAttempts) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(attempt);
            }, "Import bypass attempt should be blocked: " + attempt);
        }
    }

    @Test
    @DisplayName("Security: Environment variable access prevention")
    @Order(26)
    void testSecurity_EnvironmentAccessPrevention() throws Exception {
        String[] envAccessAttempts = {
            "__import__('os').environ",
            "__import__('os').getenv('HOME')",
            "__import__('os').putenv('evil', 'value')",
            "__import__('os').listdir('.')",
            "__import__('os').path.exists('/etc/passwd')"
        };

        for (String attempt : envAccessAttempts) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(attempt);
            }, "Environment access attempt should be blocked: " + attempt);
        }
    }

    @Test
    @DisplayName("Security: Sandbox directory validation")
    @Order(27)
    void testSecurity_SandboxDirectoryValidation() throws Exception {
        // Test various sandbox bypass attempts
        String[] bypassAttempts = {
            "import os; os.path.abspath('')",
            "import os; os.getcwd()",
            "import os; os.path.expanduser('~')",
            "import os; os.path.realpath('.')",
            "import os; os.path.abspath('../')",
            "import os; os.path.join('..', 'etc')"
        };

        for (String attempt : bypassAttempts) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(attempt);
            }, "Sandbox bypass attempt should be blocked: " + attempt);
        }
    }

    @Test
    @DisplayName("Security: Network access prevention")
    @Order(28)
    void testSecurity_NetworkAccessPrevention() throws Exception {
        String[] networkAttempts = {
            "import socket; socket.socket().connect(('8.8.8.8', 80))",
            "import urllib.request; urllib.request.urlopen('http://example.com')",
            "import requests; requests.get('https://evil.com')",
            "import http.client; http.client.HTTPConnection('google.com')",
            "import ftplib; ftplib.FTP('ftp.example.com')",
            "import telnetlib; telnetlib.Telnet('example.com', 23)"
        };

        for (String attempt : networkAttempts) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(attempt);
            }, "Network access attempt should be blocked: " + attempt);
        }
    }

    @Test
    @DisplayName("Security: File system enumeration prevention")
    @Order(29)
    void testSecurity_FileSystemEnumerationPrevention() throws Exception {
        String[] fsEnumerationAttempts = {
            "import os; os.listdir('/')",
            "import os; os.walk('/')",
            "import glob; glob.glob('/etc/*')",
            "import pathlib; pathlib.Path('/').iterdir()",
            "import os; os.scandir('/')",
            "import os; os.system('ls -la /')"
        };

        for (String attempt : fsEnumerationAttempts) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(attempt);
            }, "File system enumeration attempt should be blocked: " + attempt);
        }
    }

    @Test
    @DisplayName("Security: Process creation prevention")
    @Order(30)
    void testSecurity_ProcessCreationPrevention() throws Exception {
        String[] processCreationAttempts = {
            "import subprocess; subprocess.Popen(['ls'])",
            "import subprocess; subprocess.check_output(['ls'])",
            "import subprocess; subprocess.call(['cat', '/etc/passwd'])",
            "import os; os.popen('ls')",
            "import os; os.spawnv(os.P_NOWAIT, 'ls', [])",
            "import signal; os.kill(1, signal.SIGTERM)"
        };

        for (String attempt : processCreationAttempts) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(attempt);
            }, "Process creation attempt should be blocked: " + attempt);
        }
    }

    // ===================================================================================
    // COMPREHENSIVE INPUT VALIDATION TESTS
    // ===================================================================================

    @Test
    @DisplayName("Comprehensive path traversal prevention")
    @Order(31)
    void testComprehensivePathTraversalPrevention() throws Exception {
        String[] pathTraversalPatterns = {
            // Unix-style path traversal
            "../../../etc/passwd",
            "../../../../../etc/passwd",
            "../../../../../../../../etc/passwd",
            "/../../etc/passwd",
            "/etc/passwd",
            "/etc/shadow",
            "/etc/hosts",
            "/etc/hostname",

            // Windows-style path traversal
            "..\\..\\..\\windows\\system32\\config\\sam",
            "..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            "..\\..\\..\\Program Files\\",
            "C:\\..\\..\\windows\\system32\\config\\sam",
            "..\\..\\..\\boot.ini",
            "..\\..\\..\\windows\\system32\\config\\security",

            // URL-encoded path traversal
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fshadow",
            "..%252f..%252f..%252fetc%2fpasswd",
            "%252e%252f%252e%252f%252e%252f%252e%252f%252e%252f%252e%252f%252e%252f%252e%252f%252e%252f%252e%2fetc%2fpasswd",

            // Mixed traversal techniques
            "../../../%2e%2e%2fetc/passwd",
            "..\\..\\../etc/passwd",
            "/etc/passwd/../../../etc/passwd",
            "....//....//....//etc/passwd",
            "/etc/././././../etc/passwd",
            "/../etc/passwd",
            "//..//..//etc/passwd",
            "/etc/../etc/passwd",

            // Short traversal attempts
            "../etc/passwd",
            "..\\etc\\passwd",
            "%2e%2e/etc/passwd",
            "..%2fetc/passwd"
        };

        for (String path : pathTraversalPatterns) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode("open('" + path + "', 'r').read()");
            }, "Path traversal attempt should be blocked: " + path);
        }
    }

    @Test
    @DisplayName("Comprehensive shell injection prevention")
    @Order(32)
    void testComprehensiveShellInjectionPrevention() throws Exception {
        String[] shellInjectionPatterns = {
            // Command separation
            "; rm -rf /",
            "&& rm -rf /",
            "|| rm -rf /",
            "| rm -rf /",
            "; cat /etc/passwd",
            "&& cat /etc/passwd",
            "|| cat /etc/passwd",
            "| cat /etc/passwd",

            // Process substitution
            "$(cat /etc/passwd)",
            "`cat /etc/passwd`",
            "$(echo 'test; rm -rf /')",
            "`echo 'test; rm -rf /'`",
            "$(whoami)",
            "`whoami`",

            // Redirection and pipes
            "cat /etc/passwd > /tmp/malicious.txt",
            "cat /etc/passwd >> /tmp/malicious.txt",
            "cat /etc/passwd | grep root",
            "cat /etc/passwd 2>&1",
            "ls -la | grep passwd",
            "cat file.txt < /etc/passwd",

            // Background processes
            "cat /etc/passwd &",
            "sleep 10 &",
            "rm -rf / &",
            "nohup cat /etc/passwd &",
            "ping -c 5 evil.com &",

            // Command chaining
            "echo hello; echo world",
            "echo hello && echo world",
            "echo hello || echo world",
            "echo hello | echo world",

            // Terminal escape sequences
            "echo $'\\r\\n/bin/sh'",
            "echo $'\\x2f\\x62\\x69\\x6e\\x2f\\x73\\x68'",

            // Special character combinations
            "$(ls -la)",
            "`ls -la`",
            "command $(cat file)",
            "command `cat file`"
        };

        for (String command : shellInjectionPatterns) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode("import subprocess; subprocess.run(['echo', '" + command + "'], shell=True)");
            }, "Shell injection attempt should be blocked: " + command);
        }
    }

    @Test
    @DisplayName("Comprehensive code injection prevention")
    @Order(33)
    void testComprehensiveCodeInjectionPrevention() throws Exception {
        String[] codeInjectionPatterns = {
            // Direct dangerous imports
            "__import__('os').system('cat /etc/passwd')",
            "__import__('subprocess').call(['rm', '-rf', '/'])",
            "__import__('sys').exit()",
            "__import__('os').environ['PATH']",
            "__import__('socket').socket()",
            "__import__('threading').Thread(target=lambda: None)",
            "__import__('multiprocessing').Process(target=lambda: None)",
            "__import__('ctypes').cdll.LoadLibrary('libc.so.6')",

            // Eval and exec variants
            "eval('__import__(\"os\").system(\"id\")')",
            "exec('import os; os.system(\"id\")')",
            "eval('print(\"dangerous\")')",
            "exec('import subprocess; subprocess.run([\"ls\", \"/etc\"])')",
            "compile('import os', '<string>', 'exec')",
            "eval('globals()[\"__import__\"](\"os\")')",

            // Lambda and generator expressions
            "lambda: __import__('os').system('id')",
            "(lambda x: x)(__import__('os'))",
            "(x for x in __import__('os').listdir('/'))",
            "[x for x in __import__('os').environ]",
            "map(lambda x: __import__('os').system(x), ['echo hello'])",

            // String manipulation exploits
            "str.__class__.__mro__[1].__subclasses__()",
            "().__class__.__bases__[0].__subclasses__()",
            "globals()['__builtins__']['open']('/etc/passwd')",
            "open.__class__.__module__ + '.' + open.__class__.__name__",
            "''.__class__.__mro__[1].__subclasses__()",
            "(1).__class__.__bases__[0].__subclasses__()",

            // Class exploitation
            "[c for c in (1).__class__.__mro__[1].__subclasses__() if 'exec' in c.__name__]",
            "type('type', (object,), {})().__subclasses__()",
            "__import__('types').new_class('Exploit', bases=object.__subclasses__())",

            // Import exploitation
            "import builtins; builtins.eval('import os')",
            "import __builtin__; __builtin__.exec('import os')",
            "getattr(__import__('builtins'), 'eval')('import os')",

            // Context exploitation
            "exec('print(\"dangerous\")', {'__import__': __import__('os'))",
            "eval('open(\"/etc/passwd\").read()', {'__import__': __import__('os'))",

            // Bytes and code objects
            "code.compile_command('import os', '<string>', 'exec')",
            "bytes('import os', 'utf-8').decode('utf-8')",
            "memoryview(b'import os').tobytes().decode('utf-8')"
        };

        for (String code : codeInjectionPatterns) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(code);
            }, "Code injection attempt should be blocked: " + code);
        }
    }

    @Test
    @DisplayName("Comprehensive Python sandbox isolation")
    @Order(34)
    void testComprehensivePythonSandboxIsolation() throws Exception {
        String[] systemAccessAttempts = {
            // Java system access
            "import java.lang.System; System.exit(0)",
            "import java.lang.Runtime; Runtime.getRuntime().exit(0)",
            "import java.io.File; File('/etc/passwd').exists()",
            "import java.nio.file.Files; Files.readAllBytes('/etc/passwd')",
            "java.lang.System.getProperty('user.home')",
            "java.lang.Class.forName('java.lang.System')",

            // Python system access
            "__import__('sys').exit()",
            "__import__('os').system('id')",
            "__import__('subprocess').run(['ls'])",
            "__import__('threading').active_count()",
            "__import__('multiprocessing').cpu_count()",

            // File system access
            "open('/etc/passwd', 'r').read()",
            "open('/etc/shadow', 'r').read()",
            "open('/proc/self/environ', 'r').read()",
            "import os; os.listdir('/')",
            "import os; os.walk('/tmp')",
            "import os; os.path.exists('/etc/passwd')",

            // Network access
            "import socket; socket.socket().connect(('8.8.8.8', 80))",
            "import urllib.request; urllib.request.urlopen('http://google.com')",
            "import http.client; http.client.HTTPConnection('google.com')",
            "import ftplib; ftplib.FTP('ftp.google.com')",
            "import requests; requests.get('https://google.com')",

            // Environment access
            "import os; os.environ['PATH']",
            "import os; os.getenv('HOME')",
            "import os; os.environ.get('SECRET')",
            "import os; os.putenv('EVIL', 'value')",

            // Process execution
            "import subprocess; subprocess.run(['ls', '-la'])",
            "import os; os.system('cat /etc/passwd')",
            "import os; os.popen('ls')",
            "import os; os.spawnv(os.P_NOWAIT, 'ls', [])",

            // Module access
            "import sys; sys.modules.keys()",
            "import importlib; importlib.import_module('os')",
            "import importlib.util; importlib.util.find_spec('os')",

            // Runtime information
            "import sys; sys.version",
            "import sys; sys.platform",
            "import os; os.uname()",
            "import platform; platform.platform()",

            // File handles
            "import os; os.open('/etc/passwd', os.O_RDONLY)",
            "import os; os.fork()",
            "import os; os.pipe()"
        };

        for (String attempt : systemAccessAttempts) {
            assertThrows(SecurityException.class, () -> {
                executePythonCode(attempt);
            }, "System access attempt should be blocked: " + attempt);
        }
    }

    @Test
    @DisplayName("Comprehensive resource access control")
    @Order(35)
    void testComprehensiveResourceAccessControl() throws Exception {
        String[] resourceViolationAttempts = {
            // Memory abuse
            "x = 'A' * 1000000000", // 1GB allocation
            "x = ['A' * 1000] * 1000000", // Large list
            "x = {i: 'A' * 1000 for i in range(100000)}", // Large dict
            "x = ['A' * 100000 for _ in range(1000)]", // Many large strings

            // CPU abuse
            "while True: pass",
            "sum(i**1000 for i in range(100000))",
            "for i in range(1000000): i * i * i * i * i",
            "def recursive(n): return recursive(n+1) if n < 1000 else 0; recursive(0)",

            // Thread/process abuse
            "import threading; [threading.Thread(target=lambda: None).start() for _ in range(100)]",
            "import multiprocessing; [multiprocessing.Process(target=lambda: None).start() for _ in range(10)]",
            "import threading; threading.active_count() > 10",
            "import os; os.fork()",

            // File handle abuse
            "files = [open('/tmp/test' + str(i), 'w') for i in range(100)]",
            "import os; os.open('/tmp/test', os.O_CREAT, 0o644)",

            // Network connections
            "import socket; [socket.socket() for _ in range(100)]",
            "import urllib.request; [urllib.request.urlopen('http://google.com') for _ in range(10)]",

            // Limited operations (should be allowed)
            "x = [1, 2, 3]; len(x)",          // Safe
            "x = 'hello'; x.upper()",          // Safe
            "x = {'a': 1}; x.get('a')",       // Safe
            "import math; math.sqrt(16)",      // Safe
            "x = range(10); list(x)[:5]"       // Safe
        };

        for (String attempt : resourceViolationAttempts) {
            // Check if this is a safe operation or should be blocked
            if (attempt.contains("// Safe")) {
                String safeCode = attempt.split("// Safe")[1].trim();
                Object result = executePythonCode(safeCode);
                assertNotNull(result, "Safe operation should work");
            } else {
                assertThrows(SecurityException.class, () -> {
                    executePythonCode(attempt);
                }, "Resource violation attempt should be blocked: " + attempt);
            }
        }
    }

    // ===================================================================================
    // HELPER METHODS
    // ===================================================================================

    /**
     * Creates a test file in the sandbox directory for testing purposes.
     */
    private void createTestFile(String filename, String content) throws Exception {
        Path testFile = Paths.get(SANDBOX_ROOT, filename);
        java.nio.file.Files.write(testFile, content.getBytes());
    }

    /**
     * Checks if a file exists in the sandbox directory.
     */
    private boolean fileExistsInSandbox(String filename) {
        Path testFile = Paths.get(SANDBOX_ROOT, filename);
        return testFile.toFile().exists();
    }

    /**
     * Clean up test files after tests.
     */
    private void cleanupTestFiles() {
        File sandboxDir = new File(SANDBOX_ROOT);
        if (sandboxDir.exists()) {
            File[] files = sandboxDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            sandboxDir.delete();
        }
    }
}