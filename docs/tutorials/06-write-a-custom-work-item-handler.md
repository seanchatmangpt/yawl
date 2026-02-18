# Tutorial 06: Write a Custom Work Item Handler

By the end of this tutorial you will have written a Java class that extends `InterfaceBWebsideController`, deployed it as a web application alongside the YAWL engine, and seen the engine call your handler automatically every time a task fires in a running case. The concrete example is a string-reversal handler: when the engine announces a work item, the handler checks it out, reads an input string, reverses it, and checks the item back in with the reversed value.

---

## Prerequisites

- Tutorial 01 completed: you can build YAWL from source and the engine is running.
- Tutorial 05 completed: you understand how the engine authenticates callers and what check-out/check-in means.
- Maven 3.9 or later with access to the YAWL artifacts in your local Maven repository (run `mvn install` from the YAWL source root to populate them).
- You have a servlet container (Tomcat 10 or later) where you will deploy the handler as a WAR.

---

## Background: how the engine calls custom services

The YAWL engine does not call custom code in-process. Instead it sends HTTP POST requests to a URL that you register. The sequence is:

1. Your service registers a URL with the engine (done once at startup via Interface A).
2. When a task fires that is assigned to your service, the engine sends `announceItemEnabled` to your URL.
3. Your servlet (`InterfaceB_EnvironmentBasedServer`) receives the POST and calls `handleEnabledWorkItemEvent` on your controller.
4. Your controller checks out the work item, processes it, and checks it back in.

The class you extend, `InterfaceBWebsideController`, lives in the YAWL engine module and provides all the HTTP client logic. You never write raw HTTP — you call its `checkOut`, `checkInWorkItem`, and `connect` helper methods.

The servlet that dispatches engine announcements to your controller is `InterfaceB_EnvironmentBasedServer`. You configure it in `web.xml` with the fully-qualified name of your controller class.

---

## Step 1: Create the Maven project

```bash
mvn archetype:generate \
    -DgroupId=com.example.yawl \
    -DartifactId=string-reversal-handler \
    -DarchetypeArtifactId=maven-archetype-webapp \
    -DinteractiveMode=false

cd string-reversal-handler
```

Add the YAWL engine dependency to `pom.xml`. The group and artifact IDs match the YAWL multi-module build:

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example.yawl</groupId>
  <artifactId>string-reversal-handler</artifactId>
  <version>1.0.0</version>
  <packaging>war</packaging>

  <properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <!-- YAWL engine interfaces and utilities -->
    <dependency>
      <groupId>org.yawlfoundation.yawl</groupId>
      <artifactId>yawl-engine</artifactId>
      <version>6.0.0-SNAPSHOT</version>
      <scope>provided</scope>
    </dependency>

    <!-- Jakarta Servlet API (provided by the container) -->
    <dependency>
      <groupId>jakarta.servlet</groupId>
      <artifactId>jakarta.servlet-api</artifactId>
      <version>6.0.0</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <finalName>string-reversal-handler</finalName>
  </build>
</project>
```

Expected: `mvn validate` reports `BUILD SUCCESS`.

---

## Step 2: Write the controller class

Create `src/main/java/com/example/yawl/StringReversalHandler.java`:

```java
package com.example.yawl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBWebsideController;

import java.io.IOException;

/**
 * A YAWL custom service that reverses a string input parameter.
 *
 * When the engine fires a task assigned to this service, it calls
 * handleEnabledWorkItemEvent with the enabled work item. This handler
 * checks the item out, reads the "inputText" parameter, reverses it,
 * and checks the item back in with "reversedText" set to the result.
 */
public class StringReversalHandler extends InterfaceBWebsideController {

    private static final Logger log = LogManager.getLogger(StringReversalHandler.class);

    /** Singleton. InterfaceB_EnvironmentBasedServer calls getInstance() if it exists. */
    private static final StringReversalHandler INSTANCE = new StringReversalHandler();

    private String _handle;

    private StringReversalHandler() {
        // private: use getInstance()
    }

    public static StringReversalHandler getInstance() {
        return INSTANCE;
    }

    /**
     * Called by the engine when a task assigned to this service fires.
     * The work item arrives in Enabled status.
     */
    @Override
    public void handleEnabledWorkItemEvent(WorkItemRecord enabledItem) {
        log.info("Received enabled work item: {}", enabledItem.getID());
        try {
            // Establish a session with the engine if not already connected.
            if (!connected()) {
                _handle = connect(engineLogonName, engineLogonPassword);
            }

            // Check the item out: transitions it from Enabled to Executing.
            WorkItemRecord executingItem = checkOut(enabledItem.getID(), _handle);

            // Read the input parameter "inputText" from the work item's data.
            Element data = executingItem.getDataList();
            String inputText = "";
            if (data != null) {
                Element inputEl = data.getChild("inputText");
                if (inputEl != null) {
                    inputText = inputEl.getText();
                }
            }
            log.info("Processing work item {}. Input: '{}'", executingItem.getID(), inputText);

            // Perform the actual work: reverse the string.
            String reversed = new StringBuilder(inputText).reverse().toString();
            log.info("Reversed text: '{}'", reversed);

            // Build the output data element.
            // The root element name must match the task's decomposition name.
            Element outputData = prepareReplyRootElement(enabledItem, _handle);
            Element reversedEl = new Element("reversedText");
            reversedEl.setText(reversed);
            outputData.addContent(reversedEl);

            // Keep the original input parameter in the output (required by the engine
            // so it can merge input and output correctly for net-level data flow).
            Element inputEchoEl = new Element("inputText");
            inputEchoEl.setText(inputText);
            outputData.addContent(inputEchoEl);

            // Check the item back in. This completes the task in the engine.
            String result = checkInWorkItem(
                    executingItem.getID(),
                    executingItem.getDataList(),   // original input data
                    outputData,                    // merged output data
                    _handle);

            if (!successful(result)) {
                log.error("Check-in failed for work item {}: {}", executingItem.getID(), result);
            } else {
                log.info("Work item {} completed successfully.", executingItem.getID());
            }

        } catch (IOException e) {
            log.error("IO error processing work item {}: {}", enabledItem.getID(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error processing work item {}: {}", enabledItem.getID(), e.getMessage(), e);
        }
    }

    /**
     * Called when the engine cancels a work item that this service had checked out.
     * The engine expects this method to clean up any local state held for the item.
     */
    @Override
    public void handleCancelledWorkItemEvent(WorkItemRecord cancelledItem) {
        log.info("Work item {} was cancelled by the engine.", cancelledItem.getID());
        // Remove from local cache so it does not appear as a phantom executing item.
        _ibCache.removeRemotelyCachedWorkItem(cancelledItem.getID());
    }

    /**
     * Declares the parameters this service expects to find on any task decomposition
     * it is assigned to. The YAWL Editor reads this list and pre-populates the task's
     * parameter panel when the service is selected.
     */
    @Override
    public YParameter[] describeRequiredParams() {
        YParameter inputParam = new YParameter(null, YParameter._INPUT_PARAM_TYPE);
        inputParam.setDataTypeAndName("string", "inputText", XSD_NAMESPACE);
        inputParam.setDocumentation("The text to reverse.");
        inputParam.setOptional(false);

        YParameter outputParam = new YParameter(null, YParameter._OUTPUT_PARAM_TYPE);
        outputParam.setDataTypeAndName("string", "reversedText", XSD_NAMESPACE);
        outputParam.setDocumentation("The reversed text returned to the net.");
        outputParam.setOptional(false);

        return new YParameter[] { inputParam, outputParam };
    }
}
```

Expected: `mvn compile` reports `BUILD SUCCESS`.

---

## Step 3: Understand what `handleEnabledWorkItemEvent` receives

The engine sends the work item to your handler as an XML snippet in the HTTP POST body. The `InterfaceB_EnvironmentBasedServer` servlet deserialises this into a `WorkItemRecord` before calling your method. The important fields are:

| `WorkItemRecord` method | What it returns |
|------------------------|----------------|
| `getID()` | The work item identifier, e.g. `"1:ReverseTask"` |
| `getCaseID()` | The running case's ID, e.g. `"1"` |
| `getTaskID()` | The task's definition ID in the specification |
| `getTaskName()` | The task's human-readable name |
| `getStatus()` | At this point always `"Enabled"` |
| `getDataList()` | A JDOM `Element` containing the task's current input values |

After `checkOut` the returned `WorkItemRecord` has `status = "Executing"` and `getDataList()` returns the task's input data populated from the running case's net variables.

---

## Step 4: Configure the deployment descriptor

Replace `src/main/webapp/WEB-INF/web.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

  <display-name>String Reversal Handler</display-name>

  <!--
    InterfaceB_EnvironmentBasedServer reads "InterfaceBWebSideController" to find
    your controller class. It calls Class.forName() on this value and then calls
    getInstance() if that method exists, or the no-arg constructor otherwise.
  -->
  <context-param>
    <param-name>InterfaceBWebSideController</param-name>
    <param-value>com.example.yawl.StringReversalHandler</param-value>
  </context-param>

  <!--
    The engine's Interface B URL. The server servlet uses this to call back to the
    engine for check-out, check-in, and connection operations.
  -->
  <context-param>
    <param-name>InterfaceB_BackEnd</param-name>
    <param-value>http://localhost:8080/yawl/ib</param-value>
  </context-param>

  <!--
    Credentials this service uses when it connects to the engine.
    Override YAWL_ENGINE_PASSWORD environment variable to avoid storing passwords
    in web.xml (see SECURITY.md). The values below are the YAWL defaults.
  -->
  <context-param>
    <param-name>EngineLogonUserName</param-name>
    <param-value>admin</param-value>
  </context-param>
  <context-param>
    <param-name>EngineLogonPassword</param-name>
    <param-value>YAWL</param-value>
  </context-param>

  <!--
    The servlet that receives "announceItemEnabled" and similar POSTs from the engine.
    It deserialises the work item and dispatches it to your controller.
  -->
  <servlet>
    <servlet-name>InterfaceB_EnvironmentBasedServer</servlet-name>
    <servlet-class>
      org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedServer
    </servlet-class>
    <load-on-startup>1</load-on-startup>
  </servlet>

  <servlet-mapping>
    <servlet-name>InterfaceB_EnvironmentBasedServer</servlet-name>
    <url-pattern>/ib</url-pattern>
  </servlet-mapping>

</web-app>
```

The engine will POST events to `http://<your-host>:<port>/string-reversal-handler/ib`.

---

## Step 5: Build and deploy the WAR

```bash
mvn clean package
```

This produces `target/string-reversal-handler.war`. Copy it to Tomcat's webapps directory:

```bash
cp target/string-reversal-handler.war /path/to/tomcat/webapps/
```

Wait for Tomcat to hot-deploy the WAR. You will see log output like:

```
INFO  InterfaceB_EnvironmentBasedServer - Controller 'com.example.yawl.StringReversalHandler'
      initialised and connected to engine at http://localhost:8080/yawl/ib
```

Expected: no `ERROR` or `WARN` lines. If you see `ClassNotFoundException` the YAWL engine JAR is not on the WAR's classpath — change the dependency scope from `provided` to `compile` if the YAWL classes are not already provided by the container.

---

## Step 6: Register the service with the engine

The engine only sends work item announcements to services it knows about. Register the service URI using the Interface A `addYAWLService` action. You can do this with `curl`:

```bash
# 1. Get a session handle
SESSION=$(curl -s -X POST http://localhost:8080/yawl/ia \
    -d "action=connect&userid=admin&password=a321c4f618a0948e96da4748e9a6eef8" \
    | sed 's/.*<response>//;s/<\/response>.*//')

echo "Session: $SESSION"

# 2. Register the service
curl -s -X POST http://localhost:8080/yawl/ia \
    -d "action=addYAWLService" \
    -d "sessionHandle=$SESSION" \
    -d "serviceURI=http://localhost:8080/string-reversal-handler/ib" \
    -d "serviceID=StringReversalHandler" \
    -d "serviceName=String Reversal Handler" \
    -d "serviceDescription=Reverses a string input parameter" \
    -d "assignable=true"
```

Expected response from the registration call:

```xml
<success/>
```

The MD5 hash for `YAWL` shown above (`a321c4f618a0948e96da4748e9a6eef8`) is the same hash produced by `PasswordEncryptor.encrypt("YAWL")` in the YAWL source. If your engine uses a different password, replace it with `echo -n 'YourPassword' | md5sum | cut -c1-32`.

---

## Step 7: Create a specification that uses the handler

Create a file `string-reversal.yawl` with the following content. This is the minimal valid YAWL specification XML for a single automatic task:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                      http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd"
                  version="4.0">

  <specification uri="StringReversal">
    <name>String Reversal</name>
    <documentation>Reverses a string via a custom handler.</documentation>
    <metaData>
      <creator>Tutorial</creator>
      <version>0.1</version>
      <validFrom>2026-01-01</validFrom>
      <validUntil>2099-12-31</validUntil>
    </metaData>

    <schema xmlns="http://www.w3.org/2001/XMLSchema">
      <element name="inputText"   type="string"/>
      <element name="reversedText" type="string"/>
    </schema>

    <decomposition id="StringReversal" isRootNet="true" xsi:type="NetFactsType">
      <inputParam>
        <index>0</index>
        <name>inputText</name>
        <type>string</type>
        <namespace>http://www.w3.org/2001/XMLSchema</namespace>
      </inputParam>
      <outputParam>
        <index>0</index>
        <name>reversedText</name>
        <type>string</type>
        <namespace>http://www.w3.org/2001/XMLSchema</namespace>
      </outputParam>

      <processControlElements>
        <inputCondition id="InputCondition">
          <flowsInto>
            <nextElementRef id="ReverseTask"/>
          </flowsInto>
        </inputCondition>

        <task id="ReverseTask">
          <name>Reverse String</name>
          <flowsInto>
            <nextElementRef id="OutputCondition"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
          <startingMappings>
            <mapping>
              <expression query="/StringReversal/inputText/text()"/>
              <mapsTo>inputText</mapsTo>
            </mapping>
          </startingMappings>
          <completedMappings>
            <mapping>
              <expression query="/ReverseTask/reversedText/text()"/>
              <mapsTo>reversedText</mapsTo>
            </mapping>
          </completedMappings>
          <resourcing>
            <offer initiator="system"/>
            <allocate initiator="system"/>
            <start initiator="system"/>
            <yawlService id="StringReversalHandler" uri="http://localhost:8080/string-reversal-handler/ib"/>
          </resourcing>
          <decomposesTo id="ReverseTask"/>
        </task>

        <outputCondition id="OutputCondition"/>
      </processControlElements>
    </decomposition>

    <decomposition id="ReverseTask" xsi:type="WebServiceGatewayFactsType">
      <inputParam>
        <index>0</index>
        <name>inputText</name>
        <type>string</type>
        <namespace>http://www.w3.org/2001/XMLSchema</namespace>
      </inputParam>
      <outputParam>
        <index>0</index>
        <name>reversedText</name>
        <type>string</type>
        <namespace>http://www.w3.org/2001/XMLSchema</namespace>
      </outputParam>
      <yawlService id="StringReversalHandler" uri="http://localhost:8080/string-reversal-handler/ib"/>
    </decomposition>

  </specification>
</specificationSet>
```

---

## Step 8: Load the specification and run a case

Use the commands from Tutorial 05 (Step 5 of that tutorial covers loading a spec; Step 6 covers launching a case). With `curl`:

```bash
# Load the specification
curl -s -X POST http://localhost:8080/yawl/ia \
    -d "action=upload" \
    -d "sessionHandle=$SESSION" \
    --data-urlencode "specXML@string-reversal.yawl"
```

Expected:

```xml
<success/>
```

```bash
# Launch a case with initial data
curl -s -X POST http://localhost:8080/yawl/ib \
    -d "action=launchCase" \
    -d "sessionHandle=$SESSION" \
    -d "specidentifier=StringReversal" \
    -d "specversion=0.1" \
    -d "specuri=StringReversal" \
    -d "caseParams=<data><inputText>Hello YAWL</inputText></data>"
```

Expected:

```xml
<response>1</response>
```

Within a fraction of a second, the engine fires `ReverseTask` and POSTs `announceItemEnabled` to your handler at `http://localhost:8080/string-reversal-handler/ib`. You will see in the handler's log:

```
INFO  StringReversalHandler - Received enabled work item: 1:ReverseTask
INFO  StringReversalHandler - Processing work item 1:ReverseTask. Input: 'Hello YAWL'
INFO  StringReversalHandler - Reversed text: 'LWAV olleH'
INFO  StringReversalHandler - Work item 1:ReverseTask completed successfully.
```

---

## Step 9: Verify the case completed with the correct output

After the handler checks the item in, the engine advances through `OutputCondition` and completes the case. Retrieve the case's final data to confirm the `reversedText` variable holds the correct value:

```bash
curl -s "http://localhost:8080/yawl/ib?action=getCaseData&caseID=1&sessionHandle=$SESSION"
```

Expected:

```xml
<response>
  <StringReversal>
    <inputText>Hello YAWL</inputText>
    <reversedText>LWAV olleH</reversedText>
  </StringReversal>
</response>
```

If the case data response shows `reversedText` is empty or unchanged, the handler's check-in call returned an error. Check the handler's log for the error message from the engine.

---

## What you learned

- How `InterfaceBWebsideController` is the single abstract class you extend to create a YAWL custom service.
- How `InterfaceB_EnvironmentBasedServer` — the included servlet — receives engine announcements and dispatches them to your controller's event handlers.
- How `handleEnabledWorkItemEvent` is the primary extension point: the engine calls it every time a task assigned to your service fires.
- How `checkOut` and `checkInWorkItem` drive the task lifecycle: check out to claim the work, check in to complete it with output data.
- How `describeRequiredParams` advertises the expected parameters to the YAWL Editor so it can pre-populate the task decomposition form.
- How to wire the deployment together: `web.xml` maps your controller class name and the engine's back-end URL.
- How to register your service with the engine via the Interface A `addYAWLService` call so the engine knows where to send announcements.

## What next

To write a service that handles multiple tasks concurrently, look at the virtual thread batch operations in `InterfaceB_EnvironmentBasedClient.checkOutWorkItemsBatch`. For services that need to initiate cases themselves rather than waiting for the engine, use `AbstractEngineClient` as the base class — it wraps both Interface A and Interface B and provides connection management via `ReentrantLock` for thread safety.
