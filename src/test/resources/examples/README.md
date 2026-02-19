# YAWL Z.ai Prompt Template Examples

This directory contains example YAWL XML specifications generated using the Z.ai prompt templates.

## Files

### Prompt Templates
- `../zai_prompts.json` - Main prompt template configuration with 8 workflow patterns

### Example XML Specifications
- `minimal_sequence_spec.xml` - Basic sequential workflow
- `parallel_split_sync_spec.xml` - Parallel split and synchronization pattern
- `exclusive_choice_spec.xml` - Exclusive choice with conditional routing
- `multiple_instance_spec.xml` - Multiple instance dynamic task generation
- `web_service_integration_spec.xml` - Web service integration with automated external interaction
- `resourced_workflow_spec.xml` - Human resource allocation with manual external interaction

## Usage

### Using Z.ai Prompt Templates

1. **Basic Sequence Pattern**
```json
{
  "template": "basic_sequence",
  "variables": {
    "requirements": "Create a simple workflow that processes an order sequentially through validation, payment, and shipping"
  }
}
```

2. **Multiple Instance Pattern**
```json
{
  "template": "multiple_instance",
  "variables": {
    "requirements": "Process multiple customer orders in parallel",
    "additional_requirements": "Each order should have 3-10 items, activate at 5 items"
  }
}
```

3. **Resourced Workflow**
```json
{
  "template": "resourced_workflow",
  "variables": {
    "requirements": "Manual approval workflow for purchase orders",
    "resource_config": "Supervisors can approve, Managers can delegate"
  }
}
```

### Key Features

All generated XML specifications:
- Validate against YAWL Schema 4.0
- Use proper namespace declarations
- Include required metadata (title, creator, version)
- Implement correct control types (and, or, xor)
- Maintain proper element references
- Follow YAWL workflow patterns

## Pattern Descriptions

1. **Basic Sequence**: Simple linear workflow with XOR joins and splits
2. **Parallel Split/Sync**: AND split for parallel execution, AND join for synchronization
3. **Exclusive Choice**: XOR branching with predicates for conditional routing
4. **Multiple Instance**: Dynamic task generation with configurable instance counts
5. **Web Service Integration**: Automated external service calls with WSDL configuration
6. **Resourced Workflow**: Human task allocation with role-based permissions
7. **Complex Nested**: Multi-level workflow with nested decompositions

## Schema Compliance

All examples follow YAWL Schema 4.0 requirements:
- Proper specificationSet structure with version="4.0"
- Correct namespace declarations
- Valid element relationships
- Required control types (join/split codes)
- Proper data type definitions
- Unique element IDs
- FlowsInto relationships with nextElementRef

## Testing

Validate generated XML using:
```bash
xmllint --schema schema/YAWL_Schema4.0.xsd example_file.xml
```