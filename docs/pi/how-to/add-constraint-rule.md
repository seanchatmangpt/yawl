# How to Add a Constraint Rule

`ProcessConstraintModel` uses Apache Jena RDF to filter recommended actions.
This guide shows how to add task-ordering or resource-assignment constraints.

---

## What constraints do

When `PrescriptiveEngine.recommend()` generates action candidates, it passes each
through `ProcessConstraintModel.isValid(action)`. Actions that fail validation are
removed from the result list.

Two constraint types are supported:
- **Task reachability** — is `toTaskName` reachable from the current state?
- **Resource assignability** — is `toResourceId` able to handle the work item?

---

## Reading the current constraint model

```java
import org.yawlfoundation.yawl.pi.prescriptive.ProcessConstraintModel;
import org.apache.jena.rdf.model.Model;

ProcessConstraintModel model = new ProcessConstraintModel();
// model starts empty — all RerouteActions pass by default
```

---

## Adding a task-reachability constraint

Declare that `task-B` is a legal successor of `task-A` using the
`yawl:canSucceedBy` predicate:

```java
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;

String YAWL_NS = "http://www.yawlfoundation.org/yawl#";

Model rdf = ModelFactory.createDefaultModel();
Resource taskA = rdf.createResource(YAWL_NS + "task-A");
Resource taskB = rdf.createResource(YAWL_NS + "task-B");
Property canSucceedBy = rdf.createProperty(YAWL_NS, "canSucceedBy");

rdf.add(taskA, canSucceedBy, taskB);

ProcessConstraintModel constraintModel = new ProcessConstraintModel(rdf);
```

Now, a `RerouteAction(fromTaskName="task-A", toTaskName="task-B")` passes validation.
A reroute to `toTaskName="task-C"` (no `canSucceedBy` triple declared) is filtered out.

---

## Adding a resource-assignability constraint

Declare that resource `res-alice` can handle work item type `credit-check`:

```java
String YAWL_NS = "http://www.yawlfoundation.org/yawl#";

Model rdf = ModelFactory.createDefaultModel();
Resource workItem = rdf.createResource(YAWL_NS + "credit-check");
Resource resource = rdf.createResource(YAWL_NS + "res-alice");
Property assignableTo = rdf.createProperty(YAWL_NS, "assignableTo");

rdf.add(workItem, assignableTo, resource);

ProcessConstraintModel constraintModel = new ProcessConstraintModel(rdf);
```

`ReallocateResourceAction(workItemId="credit-check", toResourceId="res-alice")`
now passes. Reallocation to `"res-bob"` (no triple) is filtered.

---

## Combining constraints

Add as many triples as needed in a single `Model`:

```java
Model rdf = ModelFactory.createDefaultModel();
String YAWL_NS = "http://www.yawlfoundation.org/yawl#";
Property canSucceedBy = rdf.createProperty(YAWL_NS, "canSucceedBy");
Property assignableTo = rdf.createProperty(YAWL_NS, "assignableTo");

// Task ordering
rdf.add(rdf.createResource(YAWL_NS + "assess-claim"),
        canSucceedBy,
        rdf.createResource(YAWL_NS + "fast-track-approval"));
rdf.add(rdf.createResource(YAWL_NS + "assess-claim"),
        canSucceedBy,
        rdf.createResource(YAWL_NS + "manual-review"));

// Resource assignability
rdf.add(rdf.createResource(YAWL_NS + "fast-track-approval"),
        assignableTo,
        rdf.createResource(YAWL_NS + "res-senior-adjuster"));

ProcessConstraintModel model = new ProcessConstraintModel(rdf);
```

---

## Loading from an RDF/XML file

For large constraint sets, load from a Turtle or RDF/XML file:

```java
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;

Model rdf = ModelFactory.createDefaultModel();
RDFDataMgr.read(rdf, "/opt/yawl/constraints/loan-process.ttl");

ProcessConstraintModel model = new ProcessConstraintModel(rdf);
```

Sample Turtle file (`loan-process.ttl`):

```turtle
@prefix yawl: <http://www.yawlfoundation.org/yawl#> .

yawl:assess-claim   yawl:canSucceedBy  yawl:fast-track-approval .
yawl:assess-claim   yawl:canSucceedBy  yawl:manual-review .
yawl:fast-track-approval  yawl:assignableTo  yawl:res-senior-adjuster .
```

---

## Open-world vs closed-world

`ProcessConstraintModel` uses **open-world semantics**:
- If no constraint triple exists for an action, the action **passes** validation.
- Add triples only when you want to **restrict** choices.

This means an empty model permits all actions. Add constraints progressively
as you discover that certain reroutes or reallocations are invalid in production.
