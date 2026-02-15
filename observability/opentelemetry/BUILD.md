# YAWL OpenTelemetry - Build and Deployment Guide

## Build Requirements

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- Internet connection (for downloading dependencies)
- Docker (for running observability stack)

### Maven Dependencies

The integration adds the following dependencies to `pom.xml`:

#### OpenTelemetry Core
- `io.opentelemetry:opentelemetry-api:1.36.0`
- `io.opentelemetry:opentelemetry-sdk:1.36.0`
- `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.36.0`

#### OpenTelemetry Exporters
- `io.opentelemetry:opentelemetry-exporter-otlp:1.36.0`
- `io.opentelemetry:opentelemetry-exporter-logging:1.36.0`
- `io.opentelemetry:opentelemetry-exporter-prometheus:1.36.0`

#### OpenTelemetry Instrumentation
- `io.opentelemetry.instrumentation:opentelemetry-instrumentation-api`
- `io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations`
- `io.opentelemetry.instrumentation:opentelemetry-jdbc`
- `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`

#### Micrometer Integration
- `io.micrometer:micrometer-tracing-bridge-otel`

## Build Process

### Step 1: Clean Build

```bash
cd /home/user/yawl
mvn clean
```

### Step 2: Compile

```bash
mvn compile
```

This will:
- Compile all YAWL source code
- Compile new observability classes:
  - `YAWLTelemetry.java`
  - `YAWLTracing.java`
  - `OpenTelemetryConfig.java`

### Step 3: Download OpenTelemetry Java Agent

The build automatically downloads the OpenTelemetry Java agent:

```bash
mvn process-resources
```

This executes the `download-maven-plugin` which downloads:
- `opentelemetry-javaagent.jar` → `target/agents/`

### Step 4: Run Tests

```bash
# Run all tests
mvn test

# Run only observability tests
mvn test -Dtest=YAWLTelemetryTest,YAWLTracingTest
```

### Step 5: Package

```bash
mvn package
```

This creates:
- `target/yawl-5.2.jar` - YAWL application JAR
- `target/agents/opentelemetry-javaagent.jar` - OpenTelemetry agent
- `target/otel-config/` - OpenTelemetry configuration files

### Step 6: Verify Build

```bash
# Check if agent was downloaded
ls -lh target/agents/opentelemetry-javaagent.jar

# Check if configuration files copied
ls -lh target/otel-config/

# Verify observability classes compiled
jar tf target/yawl-5.2.jar | grep observability
```

Expected output:
```
org/yawlfoundation/yawl/engine/observability/YAWLTelemetry.class
org/yawlfoundation/yawl/engine/observability/YAWLTracing.class
org/yawlfoundation/yawl/engine/observability/OpenTelemetryConfig.class
```

## Deployment Options

### Option 1: Standalone WAR Deployment

Build the WAR file for Tomcat deployment:

```bash
mvn clean package -P war

# Deploy to Tomcat
cp target/yawl.war $CATALINA_HOME/webapps/

# Run Tomcat with OpenTelemetry
export JAVA_OPTS="-javaagent:$PWD/target/agents/opentelemetry-javaagent.jar \
  -Dotel.javaagent.configuration-file=$PWD/observability/opentelemetry/agent-config.properties"

$CATALINA_HOME/bin/catalina.sh run
```

### Option 2: Spring Boot Executable JAR

```bash
mvn clean package -P executable-jar

# Run with OpenTelemetry
java -javaagent:target/agents/opentelemetry-javaagent.jar \
  -Dotel.javaagent.configuration-file=observability/opentelemetry/agent-config.properties \
  -jar target/yawl-5.2.jar
```

### Option 3: Docker Container

Build Docker image with OpenTelemetry:

```dockerfile
FROM tomcat:10-jdk21

# Set working directory
WORKDIR /opt/yawl

# Download OpenTelemetry agent
RUN curl -L -o /opt/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.2.0/opentelemetry-javaagent.jar

# Copy YAWL WAR
COPY target/yawl.war /usr/local/tomcat/webapps/

# Copy OpenTelemetry configuration
COPY observability/opentelemetry/agent-config.properties /opt/otel-config.properties

# Configure Java agent
ENV JAVA_OPTS="-javaagent:/opt/opentelemetry-javaagent.jar \
    -Dotel.javaagent.configuration-file=/opt/otel-config.properties"

EXPOSE 8080 9464

CMD ["catalina.sh", "run"]
```

Build and run:

```bash
# Build Docker image
docker build -t yawl-otel:5.2 .

# Run container
docker run -d \
  -p 8080:8080 \
  -p 9464:9464 \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 \
  --name yawl-engine \
  yawl-otel:5.2
```

### Option 4: Kubernetes Deployment

Create Kubernetes deployment with OpenTelemetry:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
  template:
    metadata:
      labels:
        app: yawl-engine
    spec:
      initContainers:
      - name: download-otel-agent
        image: curlimages/curl:latest
        command:
        - sh
        - -c
        - |
          curl -L -o /otel-agent/opentelemetry-javaagent.jar \
          https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.2.0/opentelemetry-javaagent.jar
        volumeMounts:
        - name: otel-agent
          mountPath: /otel-agent

      containers:
      - name: yawl-engine
        image: yawl-engine:5.2
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9464
          name: metrics
        env:
        - name: JAVA_OPTS
          value: "-javaagent:/otel-agent/opentelemetry-javaagent.jar"
        - name: OTEL_SERVICE_NAME
          value: "yawl-engine"
        - name: OTEL_EXPORTER_OTLP_ENDPOINT
          value: "http://otel-collector:4318"
        - name: OTEL_RESOURCE_ATTRIBUTES
          value: "deployment.environment=production,k8s.cluster.name=prod-cluster"
        volumeMounts:
        - name: otel-agent
          mountPath: /otel-agent
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /yawl/
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /yawl/
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10

      volumes:
      - name: otel-agent
        emptyDir: {}
```

Deploy:

```bash
kubectl apply -f k8s/yawl-deployment-otel.yaml
```

## Configuration Management

### Environment-Specific Configurations

#### Development
```properties
# observability/opentelemetry/agent-config-dev.properties
otel.traces.exporter=logging
otel.metrics.exporter=logging,prometheus
otel.traces.sampler=always_on
otel.javaagent.debug=true
```

#### Staging
```properties
# observability/opentelemetry/agent-config-staging.properties
otel.traces.exporter=otlp
otel.metrics.exporter=otlp,prometheus
otel.traces.sampler=parentbased_traceidratio
otel.traces.sampler.arg=0.2
otel.exporter.otlp.endpoint=http://otel-collector-staging:4318
```

#### Production
```properties
# observability/opentelemetry/agent-config.properties
otel.traces.exporter=otlp
otel.metrics.exporter=otlp,prometheus
otel.traces.sampler=parentbased_traceidratio
otel.traces.sampler.arg=0.1
otel.exporter.otlp.endpoint=http://otel-collector:4318
```

### Selecting Configuration

Via command line:
```bash
java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.javaagent.configuration-file=observability/opentelemetry/agent-config-dev.properties \
  -jar yawl.jar
```

Via environment variable:
```bash
export OTEL_JAVAAGENT_CONFIGURATION_FILE=/path/to/agent-config.properties
java -javaagent:opentelemetry-javaagent.jar -jar yawl.jar
```

## Build Profiles

### Default Profile
```bash
mvn clean package
```
- Includes all dependencies
- Downloads OpenTelemetry agent
- Runs all tests

### Skip Tests
```bash
mvn clean package -DskipTests
```

### Skip OpenTelemetry Agent Download
```bash
mvn clean package -Dotel.download.skip=true
```

### WAR Profile
```bash
mvn clean package -P war
```

### Executable JAR Profile
```bash
mvn clean package -P executable-jar
```

## Troubleshooting Build Issues

### Issue: OpenTelemetry Agent Download Fails

**Solution:**
```bash
# Manually download the agent
curl -L -o target/agents/opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.2.0/opentelemetry-javaagent.jar

# Continue with build
mvn package -Dotel.download.skip=true
```

### Issue: Dependency Resolution Fails

**Solution:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository/io/opentelemetry

# Retry with forced update
mvn clean package -U
```

### Issue: Compilation Errors in Observability Classes

**Solution:**
```bash
# Verify Java version
java -version  # Must be 21+

# Clean and rebuild
mvn clean compile
```

### Issue: Tests Fail

**Solution:**
```bash
# Run tests with debug output
mvn test -X -Dtest=YAWLTelemetryTest

# Skip failing tests temporarily
mvn package -DskipTests
```

## CI/CD Integration

### GitHub Actions

```yaml
name: Build YAWL with OpenTelemetry

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven

    - name: Build with Maven
      run: mvn clean package

    - name: Run observability tests
      run: mvn test -Dtest=*observability*Test

    - name: Upload artifacts
      uses: actions/upload-artifact@v3
      with:
        name: yawl-otel
        path: |
          target/yawl-*.jar
          target/agents/opentelemetry-javaagent.jar
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any

    tools {
        maven 'Maven 3.8'
        jdk 'JDK 21'
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar,target/agents/*.jar'
            }
        }
    }
}
```

## Validation

After building, validate the integration:

```bash
# 1. Check classes are compiled
jar tf target/yawl-5.2.jar | grep -i observability

# 2. Verify agent downloaded
test -f target/agents/opentelemetry-javaagent.jar && echo "Agent OK"

# 3. Run a test execution
java -javaagent:target/agents/opentelemetry-javaagent.jar \
  -Dotel.traces.exporter=logging \
  -Dotel.metrics.exporter=logging \
  -jar target/yawl-5.2.jar &

# 4. Check for OpenTelemetry initialization in logs
sleep 10
curl http://localhost:9464/metrics | grep otel
```

## Production Checklist

Before deploying to production:

- [ ] Maven build succeeds without errors
- [ ] All tests pass
- [ ] OpenTelemetry agent downloaded successfully
- [ ] Configuration files are present in target/otel-config/
- [ ] Environment-specific configuration selected
- [ ] Observability stack (collector, Jaeger, Prometheus) is deployed
- [ ] Network connectivity to OTLP endpoint verified
- [ ] Sampling ratio configured appropriately
- [ ] Resource limits set for collector
- [ ] Dashboards imported to Grafana
- [ ] Alerting rules deployed to Prometheus
- [ ] Health checks configured
- [ ] Monitoring of monitoring stack in place

## Performance Impact

Expected overhead with OpenTelemetry:

- **CPU**: 2-5% additional CPU usage
- **Memory**: 50-100MB additional heap usage
- **Latency**: <1ms per instrumented operation
- **Network**: Varies based on sampling ratio and batch settings

Optimize for production:
- Set sampling to 5-10% (`otel.traces.sampler.arg=0.1`)
- Use batching (default: 5 second delay)
- Filter unnecessary spans in collector
- Use tail-based sampling in collector for intelligent trace retention

## Support and Documentation

- **Architecture**: `observability/opentelemetry/README.md`
- **Queries**: `observability/opentelemetry/QUERIES.md`
- **Quick Start**: `observability/opentelemetry/QUICKSTART.md`
- **OpenTelemetry Docs**: https://opentelemetry.io/docs/java/
