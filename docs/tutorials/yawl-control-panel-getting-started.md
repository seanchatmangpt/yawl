# Tutorial: YAWL Control Panel Getting Started

By the end of this tutorial you will have the YAWL Control Panel desktop application running on your local machine, understand how to use it for engine management, configure it to connect to a remote engine, and perform basic administrative operations.

---

## What is YAWL Control Panel?

The YAWL Control Panel is a Java Swing-based desktop application that provides a graphical user interface for YAWL engine administration. It allows you to:

- **Manage Specifications**: Upload, deploy, and remove workflow specifications
- **Monitor Cases**: View running cases, check case state, manage work items
- **View Logs**: Access engine logs and audit trails
- **Configure Service**: Adjust engine parameters and behavior
- **Remote Access**: Connect to YAWL engines on any network

---

## Prerequisites

```bash
java -version
```

Expected: Java 25+ (full JDK with Swing libraries).

```bash
mvn -version
```

Expected: Maven 3.9+.

A running YAWL Engine Webapp (local or remote):
- Local: `http://localhost:8080/yawl-engine`
- Remote: `https://yawl.example.com:8080/yawl-engine`

---

## Step 1: Build the Control Panel JAR

Build the yawl-control-panel module:

```bash
cd /path/to/yawl
mvn clean package -DskipTests -T 1.5C -pl yawl-control-panel
```

The executable JAR is created at:

```
yawl-control-panel/target/yawl-control-panel-6.0.0-GA.jar
```

---

## Step 2: Run the Control Panel

### Option 1: Direct JAR Execution

```bash
java -jar yawl-control-panel/target/yawl-control-panel-6.0.0-GA.jar
```

Expected output:

```
[INFO] YAWL Control Panel v6.0.0
[INFO] Initializing Swing UI...
[INFO] Loading configuration from ~/.yawl/controlpanel.properties
[INFO] UI ready - waiting for user input
```

The Control Panel window opens with an empty engine list.

### Option 2: Maven Exec Plugin

```bash
cd yawl-control-panel
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.controlpanel.YControlPanel"
```

### Option 3: Create Desktop Shortcut

Create a shell script for easy access:

```bash
cat > ~/bin/yawl-control-panel.sh << 'EOF'
#!/bin/bash
YAWL_HOME="/path/to/yawl"
java -jar "$YAWL_HOME/yawl-control-panel/target/yawl-control-panel-6.0.0-GA.jar" "$@"
EOF

chmod +x ~/bin/yawl-control-panel.sh
```

---

## Step 3: Connect to a Local Engine

### 3.1: Start a YAWL Engine

In another terminal, start the engine webapp:

```bash
cd yawl-webapps/yawl-engine-webapp
mvn jetty:run
```

Wait for the engine to initialize (typically 10-30 seconds).

### 3.2: Configure Engine Connection in Control Panel

In the Control Panel:

1. **Menu**: File → New Connection
2. **Engine URL**: `http://localhost:8080/yawl-engine`
3. **Username**: `admin`
4. **Password**: `YAWL`
5. **Engine Nickname** (optional): `Local Dev Engine`
6. Click **Connect**

Expected output in Control Panel:

```
Connected to: Local Dev Engine (http://localhost:8080/yawl-engine)
Engine Status: Running
YAWL Version: 6.0.0-GA
```

---

## Step 4: Upload and Deploy a Specification

### 4.1: Create a Test Specification

Create a simple YAWL specification file `test-process.yawl`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                  http://www.yawlfoundation.org/yawlschema/YAWL_Schema2.3.xsd">
    <specification uri="TestProcess" name="Test Process" version="1.0">
        <documentation/>
        <metaData/>
        <net id="TestNet">
            <inputCondition id="InputCondition">
                <flowInto node="Task1"/>
            </inputCondition>
            <task id="Task1" name="First Task">
                <flowInto node="OutputCondition"/>
            </task>
            <outputCondition id="OutputCondition"/>
        </net>
    </specification>
</specificationSet>
```

### 4.2: Upload Specification

In the Control Panel:

1. **Menu**: File → Upload Specification
2. **Select**: `test-process.yawl`
3. **Namespace**: `http://example.com/yawl`
4. Click **Upload**

Expected status message:

```
Specification uploaded successfully.
URI: TestProcess
Version: 1.0
Status: Active
```

### 4.3: Verify Deployment

In the Control Panel:

1. **Menu**: View → Active Specifications
2. Verify `TestProcess` appears in the list
3. Click to view specification details

---

## Step 5: Monitor and Manage Cases

### 5.1: Create a Case

In the Control Panel:

1. **Right-click** on `TestProcess` in the specifications list
2. **Create Case**
3. Provide case label: `Test-Case-001`
4. Click **Create**

Expected status:

```
Case created: Test-Case-001
Case ID: [auto-generated UUID]
Status: Running
```

### 5.2: View Case Details

1. **Menu**: View → Running Cases
2. **Select** `Test-Case-001`
3. View case information:
   - Case ID
   - Specification
   - Created time
   - Current state
   - Enabled tasks

### 5.3: Complete Work Items

1. **Select case** `Test-Case-001`
2. **Right-click** on enabled task `First Task`
3. **Complete Task**
4. Provide output data (if required): `<output/>`
5. Click **Submit**

Expected status:

```
Task completed successfully.
Case progressed to next step.
```

---

## Step 6: View Engine Logs

The Control Panel provides access to engine logs:

1. **Menu**: Tools → Engine Log Viewer
2. **Filter** by:
   - Log Level: `DEBUG`, `INFO`, `WARN`, `ERROR`
   - Component: `Engine`, `Task`, `Case`
   - Time Range: Last hour, last day, custom
3. **Search** for keywords: `error`, `exception`, `timeout`

Example useful filters:

```
Component: Engine
Log Level: ERROR
Time: Last hour
Search: "exception"
```

---

## Step 7: Configuration and Settings

### 7.1: Edit Connection Settings

1. **Menu**: File → Manage Connections
2. **Select connection** from list
3. **Edit**:
   - URL
   - Username
   - Password (stored encrypted locally)
   - Connection timeout
   - Reconnection interval
4. **Save**

### 7.2: Control Panel Preferences

1. **Menu**: Edit → Preferences
2. **Configure**:
   - **UI Theme**: Light, Dark, System
   - **Auto-refresh**: Enable/disable, interval (seconds)
   - **Case polling**: Interval for checking case updates
   - **Log buffer**: Number of log entries to display
   - **Encoding**: Character encoding for XML files
3. **Apply**

### 7.3: Save Configuration

Configuration is automatically saved to:

```
~/.yawl/controlpanel.properties
```

Manual backup:

```bash
cp ~/.yawl/controlpanel.properties ~/.yawl/controlpanel.properties.backup
```

---

## Step 8: Connect to Remote Engine

For connecting to a production engine:

### 8.1: Configure Secure Connection

1. **Menu**: File → New Connection
2. **Engine URL**: `https://yawl-prod.example.com:8080/yawl-engine`
3. **SSL/TLS**:
   - Enable: **yes**
   - Certificate File: `/path/to/ca-certificate.pem`
   - Verify Hostname: **yes**
4. **Credentials**:
   - Username: `admin`
   - Password: `[secure password]`
5. **Connection Timeout**: 30000 ms
6. **Retry Policy**:
   - Max Retries: 3
   - Backoff Interval: 5 seconds
7. Click **Connect**

### 8.2: Test Connection

The Control Panel attempts to authenticate immediately. Expected status:

```
Connected to: Production Engine
Status: Responding
YAWL Version: 6.0.0-GA
Server Time: [UTC timestamp]
```

---

## Architecture Overview

```
┌─────────────────────────────────────────┐
│  YAWL Control Panel (Swing)             │
├─────────────────────────────────────────┤
│ ┌───────────────────────────────────────┐│
│ │ UI Components (javax.swing)           ││
│ │ - JFrame (main window)                ││
│ │ - JTree (specification navigator)     ││
│ │ - JTable (case/task display)          ││
│ │ - JDialog (dialogs/wizards)           ││
│ └───────────────────────────────────────┘│
│ ┌───────────────────────────────────────┐│
│ │ Engine Communication (REST Client)    ││
│ │ - Specification upload/deploy         ││
│ │ - Case creation/monitoring            ││
│ │ - Task completion                     ││
│ │ - Log retrieval                       ││
│ └───────────────────────────────────────┘│
│ ┌───────────────────────────────────────┐│
│ │ Local Storage                         ││
│ │ - Connection profiles                 ││
│ │ - User preferences                    ││
│ │ - Credential storage (encrypted)      ││
│ └───────────────────────────────────────┘│
└─────────────────────────────────────────┘
         ↓ (HTTP/HTTPS)
┌─────────────────────────────────────────┐
│  YAWL Engine Webapp (remote or local)   │
└─────────────────────────────────────────┘
```

---

## Keyboard Shortcuts

Common shortcuts in Control Panel:

| Shortcut | Action |
|----------|--------|
| `Ctrl+N` | New connection |
| `Ctrl+O` | Open specification file |
| `Ctrl+S` | Save log to file |
| `Ctrl+Q` | Quit |
| `Ctrl+R` | Refresh current view |
| `F5` | Full refresh |
| `Ctrl+F` | Search/filter in current view |

---

## Troubleshooting

### Cannot Connect to Engine

**Problem**: "Connection refused" error

**Solution**: Verify engine is running:
```bash
curl -I http://localhost:8080/yawl-engine
# Expected: HTTP/1.1 200 OK
```

### Incorrect Credentials

**Problem**: "Authentication failed" error

**Solution**: Verify credentials:
- Default username: `admin`
- Default password: `YAWL`
- Check if user exists on resource service

### Specification Upload Fails

**Problem**: "Specification already exists" error

**Solution**: Either:
1. Delete existing specification first
2. Upload with different version number
3. Change specification URI in XML

### Swing UI Rendering Issues

**Problem**: Distorted or tiny UI on high-DPI displays

**Solution**: Set DPI awareness:
```bash
java -Dsun.java2d.dpiaware=false -jar yawl-control-panel-6.0.0-GA.jar
```

---

## Next Steps

1. **Manage Specifications**: [how-to/deployment/specification-management.md](../how-to/deployment/specification-management.md)
2. **Configure Resource Service**: [how-to/configure-resource-service.md](../how-to/configure-resource-service.md)
3. **Monitor Performance**: [how-to/operations/monitoring.md](../how-to/operations/monitoring.md)
4. **Automation**: [how-to/deployment/control-panel-automation.md](../how-to/deployment/control-panel-automation.md)

---

## Further Reading

- **[Control Panel Reference](../reference/control-panel-reference.md)**: Complete feature reference
- **[REST API for Automation](../reference/api-reference.md)**: Programmatic access
- **[Troubleshooting Guide](../how-to/troubleshooting.md)**: Common issues and solutions
