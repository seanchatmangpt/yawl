# YAWL Control Panel

## Overview

The YAWL Control Panel is a desktop administration application for managing YAWL workflow engines. It provides a graphical user interface for deploying workflow specifications, monitoring case execution, managing work items, and configuring engine resources.

### Purpose

- **Workflow Deployment**: Upload and deploy YAWL specifications to the engine
- **Case Monitoring**: Visualize and monitor workflow execution in real-time
- **Work Item Management**: Handle work items (allocate, complete, deallocate)
- **Resource Configuration**: Configure engine settings and resource mappings
- **Performance Monitoring**: Track engine performance metrics and logs

## Key Classes and Interfaces

### Main Application
- **`YControlPanel`** - Main entry point and controller class
  - Handles command-line argument parsing
  - Manages engine connection parameters
  - Initializes Swing UI components
  - Provides administration interface for engine operations

### Configuration
- **Connection Parameters**:
  - `engineUrl`: URL of the YAWL engine (default: `http://localhost:8080/yawl`)
  - `username`: Admin username for authentication
  - `password`: Admin password for authentication

### User Interface
- **Swing Framework**: Uses Java Swing for cross-platform desktop interface
- **Look and Feel**: System-specific look and feel integration
- **Responsive Design**: Event-driven architecture with proper threading

## Dependencies

### Core Dependencies
- **yawl-engine**: Core workflow engine functionality
- **Apache Commons**: Lang3, IO for utilities
- **Logging**: Log4j API and SLF4J for application logging

### Optional Dependencies
- **Jakarta Servlet API**: Required for web-based features

## Usage Examples

### Basic Usage
```java
// Create control panel with default settings
YControlPanel controlPanel = new YControlPanel();
controlPanel.start();
```

### Custom Engine Connection
```java
// Connect to remote engine with authentication
YControlPanel controlPanel = new YControlPanel(
    "https://workflow.company.com:8443/yawl",
    "admin",
    "securePassword"
);
controlPanel.start();
```

### Command Line Interface
```bash
# Run with default engine connection
java -jar yawl-control-panel.jar

# Connect to specific engine
java -jar yawl-control-panel.jar --engine-url https://workflow.company.com/yawl

# Use authentication
java -jar yawl-control-panel.jar --engine-url https://workflow.company.com/yawl --user admin --password secret

# Show help
java -jar yawl-control-panel.jar --help
```

## Architecture

### Control Flow
1. **Initialization**: Parse command-line arguments and initialize connection parameters
2. **UI Setup**: Configure Swing look and feel and create main window
3. **Engine Connection**: Establish connection to YAWL engine
4. **Main Loop**: Event-driven GUI interaction
5. **Shutdown**: Clean up resources and close connections

### Threading Model
- **Event Dispatch Thread**: All UI operations run on EDT
- **Background Tasks**: Long-running operations use worker threads
- **Logging**: Asynchronous logging with Log4j

### Future Enhancements
- Full Swing UI implementation (currently throws UnsupportedOperationException)
- Real-time case monitoring dashboards
- Work item allocation and completion interfaces
- Specification deployment wizards
- Performance monitoring tools
- Resource management interfaces

## Integration Notes

The Control Panel integrates with the YAWL engine through:
- **REST API**: HTTP-based communication for all operations
- **Authentication**: Basic authentication for secure access
- **XML Processing**: XML-based specification deployment
- **Logging**: Centralized logging engine integration

## Known Limitations

- GUI implementation is not yet complete (throws UnsupportedOperationException)
- Currently serves as a foundation for future desktop administration tools
- Limited to command-line interface for testing connection parameters

## Development Status

This module is in early development phase with the following status:
- Core framework: Complete
- GUI implementation: Not started
- Engine integration: Command-line interface complete
- Testing: Basic unit tests for connection handling