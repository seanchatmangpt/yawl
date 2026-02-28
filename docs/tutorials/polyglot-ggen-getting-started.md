# Tutorial: Code Generation with ggen — Getting Started

By the end of this tutorial you will have built a working YAWL workflow specification using **ggen** (the YAWL code generation engine), learned how deterministic code generation via RDF + SPARQL works, and deployed a synthesized workflow to the engine.

---

## Prerequisites

- YAWL built locally: `bash scripts/dx.sh -pl yawl-ggen` succeeds
- Java 25+ installed
- Maven 3.8+
- Basic familiarity with YAWL workflow specifications

---

## What is ggen?

**ggen** is YAWL's deterministic code generation engine. It uses:

- **RDF (Resource Description Framework)** to represent workflow knowledge
- **SPARQL queries** to extract and synthesize patterns
- **Tera templates** to emit Java, XML, or YAWL specifications
- **Process mining integration** (via `@aarkue/process_mining_wasm`) to synthesize workflows from PNML files

Unlike template engines that generate code from strings, ggen generates from **semantic facts**, ensuring:
- **Deterministic output** — same input always produces identical output
- **Traceable decisions** — SPARQL queries show exactly why each artifact was generated
- **Validation by construction** — generated code respects YAWL schema constraints

---

## Step 0: Add the Maven Dependency

Add `yawl-ggen` to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-ggen</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

---

## Step 1: Create an RDF Knowledge Base

ggen starts with an RDF model describing your workflow domain. Create a file `workflow.ttl`:

```turtle
@prefix : <http://example.com/workflow#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

# Define a workflow
:LoanWorkflow a :Workflow ;
    rdfs:label "Loan Processing" ;
    :startCondition :Receive ;
    :endCondition :Complete .

# Define tasks
:Receive a :Task ;
    rdfs:label "Receive Application" ;
    :splitType :xor .

:ReviewTask a :Task ;
    rdfs:label "Review Loan Application" ;
    :isMultiInstance false .

:ApproveTask a :Task ;
    rdfs:label "Approve Loan" ;
    :isMultiInstance false .

:RejectTask a :Task ;
    rdfs:label "Reject Loan" ;
    :isMultiInstance false .

:NotifyTask a :Task ;
    rdfs:label "Notify Applicant" ;
    :isMultiInstance false .

:Complete a :Condition ;
    rdfs:label "Application Complete" .

# Define flows
:Receive :flowsTo :ReviewTask .
:ReviewTask :flowsTo :Decision .

:Decision a :Condition ;
    rdfs:label "Decision Condition" .

:Decision :flowsTo :ApproveTask ;
    :condition "eligible = true" .

:Decision :flowsTo :RejectTask ;
    :condition "eligible = false" .

:ApproveTask :flowsTo :NotifyTask .
:RejectTask :flowsTo :NotifyTask .
:NotifyTask :flowsTo :Complete .
```

Load this into Java:

```java
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;

public class GgenDemo {
    public static void main(String[] args) throws Exception {
        // Load the RDF model
        Model model = RDFDataMgr.loadModel("workflow.ttl");

        System.out.println("Loaded RDF model with " + model.size() + " triples");
        model.listStatements().forEachRemaining(stmt -> {
            System.out.println(stmt);
        });
    }
}
```

Compile and run:

```bash
javac -cp target/yawl-ggen-6.0.0-GA.jar GgenDemo.java
java -cp target/yawl-ggen-6.0.0-GA.jar:. GgenDemo
```

Expected output:

```
Loaded RDF model with 28 triples
[http://example.com/workflow#Receive, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, http://example.com/workflow#Task]
... (27 more triples)
```

---

## Step 2: Query the RDF Model with SPARQL

Now extract all tasks and their flows using SPARQL:

```java
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;

public class GgenSparqlDemo {
    public static void main(String[] args) throws Exception {
        Model model = RDFDataMgr.loadModel("workflow.ttl");

        // SPARQL query: find all tasks
        String query = """
            PREFIX : <http://example.com/workflow#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?taskId ?label
            WHERE {
                ?taskId a :Task ;
                        rdfs:label ?label .
            }
            """;

        Query q = QueryFactory.create(query);
        QueryExecution qe = QueryExecutionFactory.create(q, model);
        ResultSet rs = qe.execSelect();

        System.out.println("Tasks found:");
        while (rs.hasNext()) {
            QuerySolution soln = rs.next();
            System.out.println("  - " + soln.get("label"));
        }
        qe.close();
    }
}
```

Run this and see all tasks extracted from the RDF model:

```
Tasks found:
  - Receive Application
  - Review Loan Application
  - Approve Loan
  - Reject Loan
  - Notify Applicant
```

---

## Step 3: Generate YAWL XML Using Tera Templates

Create a Tera template `workflow.tera`:

```tera
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema http://www.yawlfoundation.org/yawlschema/YAWL_Schema2.3.xsd">
  <specification uri="http://example.com/{{ workflow.name }}">
    <name>{{ workflow.label }}</name>
    <documentation></documentation>
    <metaData>
      <version>1.0</version>
      <author>ggen</author>
    </metaData>

    <net id="{{ workflow.id }}">
      <name>{{ workflow.label }}</name>

      <!-- Start condition -->
      <inputCondition id="{{ start_condition }}">
        <name>{{ start_label }}</name>
      </inputCondition>

      <!-- Tasks -->
      {% for task in tasks %}
      <task id="{{ task.id }}">
        <name>{{ task.label }}</name>
        <flowsInto>
          <nextElementRef id="{{ task.next_element }}" />
        </flowsInto>
      </task>
      {% endfor %}

      <!-- End condition -->
      <outputCondition id="{{ end_condition }}">
        <name>{{ end_label }}</name>
      </outputCondition>
    </net>
  </specification>
</specificationSet>
```

Render this template with extracted data:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class GgenTemplateDemo {
    public static void main(String[] args) throws Exception {
        Model model = RDFDataMgr.loadModel("workflow.ttl");

        // Build template context from SPARQL results
        Map<String, Object> context = new HashMap<>();
        Map<String, String> workflow = new HashMap<>();
        workflow.put("id", "LoanWorkflow");
        workflow.put("name", "loan-workflow");
        workflow.put("label", "Loan Processing");
        context.put("workflow", workflow);

        context.put("start_condition", "Receive");
        context.put("start_label", "Receive Application");
        context.put("end_condition", "Complete");
        context.put("end_label", "Application Complete");

        List<Map<String, String>> tasks = new ArrayList<>();
        String[] taskIds = {"Receive", "ReviewTask", "ApproveTask", "RejectTask", "NotifyTask"};
        String[] taskLabels = {"Receive Application", "Review Loan", "Approve Loan", "Reject Loan", "Notify Applicant"};
        for (int i = 0; i < taskIds.length; i++) {
            Map<String, String> task = new HashMap<>();
            task.put("id", taskIds[i]);
            task.put("label", taskLabels[i]);
            task.put("next_element", i < taskIds.length - 1 ? taskIds[i + 1] : "Complete");
            tasks.add(task);
        }
        context.put("tasks", tasks);

        // In practice, use tera-rs via GraalVM to render templates
        System.out.println("Template context: " + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(context));
    }
}
```

---

## Step 4: Synthesize a Workflow from PNML (Process Mining)

ggen can also synthesize workflows from Petri Net Markup Language (PNML) files. This is useful when you have a process recorded as event logs:

```java
import org.yawlfoundation.yawl.ggen.synthesis.PnmlSynthesizer;

public class GgenPnmlDemo {
    public static void main(String[] args) throws Exception {
        PnmlSynthesizer synthesizer = new PnmlSynthesizer();

        // Load PNML from a process mining tool output
        String pnml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml xmlns="http://www.pnml.org/version-2009-05-13/grammar/pnml">
              <net id="loan-net" type="http://www.pnml.org/version-2009-05-13/grammar/pnmlcoremodel">
                <name><text>Loan Process</text></name>

                <place id="p1"><name><text>Application Received</text></name></place>
                <place id="p2"><name><text>Reviewed</text></name></place>
                <place id="p3"><name><text>Approved</text></name></place>
                <place id="p4"><name><text>Rejected</text></name></place>

                <transition id="t1"><name><text>Receive</text></name></transition>
                <transition id="t2"><name><text>Review</text></name></transition>
                <transition id="t3"><name><text>Approve</text></name></transition>
                <transition id="t4"><name><text>Reject</text></name></transition>

                <arc id="arc1" source="p1" target="t1"/>
                <arc id="arc2" source="t1" target="p2"/>
                <arc id="arc3" source="p2" target="t2"/>
                <arc id="arc4" source="t2" target="p3"/>
                <arc id="arc5" source="t2" target="p4"/>
              </net>
            </pnml>
            """;

        // Synthesize to YAWL specification
        String yawlXml = synthesizer.synthesize(pnml);
        System.out.println("Generated YAWL specification:\n" + yawlXml);
    }
}
```

---

## Step 5: Validate Generated Artifacts

Before deploying, validate the generated specification:

```java
import org.yawlfoundation.yawl.ggen.validation.YawlSpecificationValidator;

public class GgenValidationDemo {
    public static void main(String[] args) throws Exception {
        String yawlXml = "... generated spec from Step 4 ...";

        YawlSpecificationValidator validator = new YawlSpecificationValidator();
        var result = validator.validate(yawlXml);

        if (result.isValid()) {
            System.out.println("Specification is valid!");
        } else {
            System.out.println("Validation errors:");
            result.getErrors().forEach(System.out::println);
        }
    }
}
```

---

## Step 6: Deploy and Execute

Deploy the generated YAWL specification to your running YAWL engine:

```bash
# Start YAWL engine (if not already running)
bash scripts/start-engine.sh

# Deploy the generated specification
curl -X POST \
  -H "Content-Type: application/xml" \
  -d @generated-workflow.xml \
  http://localhost:8080/yawl/yi/yawapiService?method=uploadSpecification

# Create a new case
curl -X POST \
  http://localhost:8080/yawl/yi/yawapiService?method=createNewCase&specIdentifier=LoanWorkflow
```

---

## Next Steps

1. **Understand ggen architecture** → Read [Explanation: Code Generation Architecture](../explanation/ggen-architecture.md)
2. **Build more complex templates** → See [How-To: Write Tera Templates](../how-to/ggen-tera-templates.md)
3. **Integrate process mining** → Follow [How-To: Synthesize from Event Logs](../how-to/ggen-synthesis-from-logs.md)
4. **API reference** → Consult [Reference: ggen API](../reference/ggen-api.md)

Happy code generation!
