# PortfolioSync Workflow

## Overview

The `PortfolioSync.yawl` workflow implements a complete SAFe portfolio synchronization process with WSJF (Weighted Shortest Job First) prioritization. It loads portfolio epics and features, calculates WSJF scores, prioritizes the backlog, allocates work to Agile Release Trains (ARTs), and exports the results as OCEL2 event logs.

## Workflow Steps

### 1. LoadPortfolio
- **Purpose**: Load portfolio epics and features from data store
- **Input**: `portfolioId` (string)
- **Output**: `portfolioItems`, `epics`, `features`, `portfolioName`, `timestamp`
- **Bridge Call**: `PortfolioBridge.loadPortfolio`

### 2. WSJFCalculation
- **Purpose**: Calculate WSJF scores for all portfolio items
- **Formula**: `WSJF = (BusinessValue + TimeCriticality + RiskReduction) / JobSize`
- **Input**: `portfolioItems`, `portfolioName`
- **Output**: `wsjfScores`, `totalItems`, `avgWSJF`
- **Bridge Call**: `WSJFBridge.calculateScores`

### 3. PrioritizeBacklog
- **Purpose**: Sort portfolio items by WSJF score descending
- **Input**: `wsjfScores`
- **Output**: `prioritizedBacklog`, `epics`, `features`
- **Parameters**: Sort descending by `wsjfScore`, limit to top 50 items

### 4. GetARTInformation
- **Purpose**: Retrieve ART names and available capacities
- **Input**: `portfolioName`
- **Output**: `artNames`, `artCapacities`
- **Bridge Call**: `ARTBridge.getARTInformation`

### 5. AllocateToARTs
- **Purpose**: Assign prioritized work to ARTs based on capacity
- **Input**: `prioritizedBacklog`, `artNames`, `artCapacities`
- **Output**: `artAllocations`, `allocationReport`
- **Bridge Call**: `AllocationEngine.allocateToARTs`
- **Strategy**: Capacity-based with dependency respect

### 6. ExportPortfolio
- **Purpose**: Export portfolio as OCEL2 event log
- **Input**: `artAllocations`, `portfolioName`, `timestamp`
- **Output**: `ocelPath`
- **Bridge Call**: `OCELBridge.exportAsOCEL2`

### 7. GeneratePortfolioReport
- **Purpose**: Generate comprehensive portfolio report
- **Input**: `artAllocations`, `allocationReport`, `ocelPath`, `totalItems`, `avgWSJF`
- **Output**: `portfolioReport`
- **Format**: HTML with visualizations and recommendations

## Data Structure Example

See `PortfolioSync-Data-Example.json` for the complete data structure format.

## Required Bridge Implementations

### PortfolioBridge
```java
public interface PortfolioBridge {
    PortfolioItems loadPortfolio(String portfolioId);
}
```

### WSJFBridge
```java
public interface WSJFBridge {
    WSJFResult calculateScores(List<PortfolioItem> items);
}
```

### ARTBridge
```java
public interface ARTBridge {
    ARTInformation getARTInformation(String portfolioName);
}
```

### AllocationEngine
```java
public interface AllocationEngine {
    ARTAllocationResult allocateToARTs(List<PortfolioItem> items,
                                      List<String> artNames,
                                      Map<String, Integer> capacities);
}
```

### OCELBridge
```java
public interface OCELBridge {
    String exportAsOCEL2(ARTAllocationResult allocations,
                         String portfolioName,
                         String timestamp);
}
```

## Execution Flow

```
Start → LoadPortfolio → WSJFCalculation → PrioritizeBacklog + GetARTInformation
                                               ↓
                                        AllocateToARTs → ExportPortfolio + GeneratePortfolioReport
                                                                   ↓
                                                                End
```

## Validation

The workflow has been validated for:
- XML syntax correctness
- YAWL schema compliance
- Proper data flow between tasks
- Correct gate and join configurations

## Usage

To execute this workflow:
1. Implement the required bridge interfaces
2. Prepare portfolio data in the specified format
3. Use YAWL engine to execute the workflow
4. Monitor the OCEL2 output for process mining analysis

## Output Files

- **OCEL2 Event Log**: For process mining and compliance analysis
- **Portfolio Report**: HTML report with allocations and recommendations
- **Allocation Summary**: JSON file with ART assignments and capacity utilization