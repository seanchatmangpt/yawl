# GregVerse Analytics Reporter

Provides real-time analytics and reporting for the GregVerse marketplace economy.

## Architecture

```
┌─────────────────────────────────────────────────┐
│              GregVerseAnalyticsReporter           │
│                 (Main Orchestrator)              │
└─────────────────────┬───────────────────────────┘
                      │
    ┌─────────────────┼─────────────────────────┐
    │                 │                         │
┌───▼───┐        ┌────▼────┐             ┌─────▼─────┐
│Marketplace  │  │Analytics│             │Dashboard  │
│Metrics       │  │Collector│             │DataProvider│
└───┬───┘        └────┬────┘             └────┬─────┘
    │                 │                         │
    │                 │                         │
┌───▼───┐        ┌────▼────┐             ┌─────▼─────┐
│Events  │        │Reports  │             │Trends &   │
│Stream  │        │Generator│             │Forecasts  │
└────────┘        └─────────┘             └───────────┘
```

## Key Features

### 1. Real-time Metrics Collection
- Transaction tracking and aggregation
- Provider/consumer activity monitoring
- Category-based metrics
- Geographic distribution analysis
- Rating distribution tracking

### 2. Stream Processing
- Structured concurrency with virtual threads
- Batch processing for performance
- Event queuing and backpressure handling
- Custom event processors

### 3. Report Generation
- **Daily Reports**: Transaction summaries, performance metrics
- **Weekly Reports**: Provider performance, market trends
- **Monthly Reports**: Economic health analysis
- **Custom Reports**: Filtered and aggregated views

### 4. Dashboard Data
- Real-time statistics updates
- Trend analysis with linear regression
- Health metrics scoring
- Performance forecasting

## Usage Examples

### Basic Setup

```java
// Create and start analytics reporter
GregVerseAnalyticsReporter analytics = new GregVerseAnalyticsReporter();
analytics.start();

// Record transactions
analytics.recordTransaction(
    "provider-001", "consumer-001",
    150.0, "consulting",
    500, 5, "US"
);

// Get dashboard data
DashboardDataProvider.DashboardData data = analytics.getDashboardData();
System.out.println("Total transactions: " + data.getTotalTransactions());
```

### Advanced Configuration

```java
// Custom configuration with builder
GregVerseAnalyticsReporter customAnalytics = GregVerseAnalyticsReporter.builder()
    .autoStart(false)
    .batchSize(200)
    .updateInterval(25)
    .trendWindowSize(100)
    .eventProcessor(event -> {
        // Custom processing logic
        if (event.getValue() > 1000) {
            // Alert for high-value transactions
            System.out.println("High-value transaction: " + event.getValue());
        }
    })
    .build();

customAnalytics.start();
```

### Working with Reports

```java
// Get daily report
ReportGenerator.DailyReport daily = analytics.getDailyReport().get();

// Get weekly report
ReportGenerator.WeeklyReport weekly = analytics.getWeeklyReport().get();

// Generate custom report
ReportGenerator.ReportFilter categoryFilter = new ReportGenerator.ReportFilter(
    ReportGenerator.FilterType.CATEGORY,
    List.of("consulting", "development")
);

ReportGenerator.CustomReport custom = analytics.getCustomReport(
    LocalDate.now().minusDays(30),
    LocalDate.now(),
    categoryFilter
).get();
```

### Dashboard and Trends

```java
// Get real-time dashboard data
DashboardDataProvider.DashboardData dashboard = analytics.getDashboardData();

// Get metric trends
DashboardDataProvider.MetricTrend transactionTrend = analytics.getMetricTrend("transactionCount");

// Generate forecasts
DashboardDataProvider.ForecastMetrics forecast = analytics.getForecast(24);

// Health metrics
DashboardDataProvider.HealthMetrics health = analytics.getHealthMetrics();
```

### Monitoring and Alerts

```java
// Configure price alerts
analytics.configurePriceAlerts(50.0, 200.0, "consulting");

// Enable real-time processing
analytics.enableRealTimeProcessing();

// Get performance metrics
GregVerseAnalyticsReporter.PerformanceMetrics perf = analytics.getPerformanceMetrics().get();
```

## Metrics Tracked

### Core Metrics
- **Active Providers/Consumers**: Real-time participant counts
- **Transaction Volume**: Total and by category
- **Transaction Value**: Averages and distributions
- **Response Times**: Average processing times
- **Ratings**: Provider rating distributions

### Derived Metrics
- **Provider Health Score**: Activity and transaction-based scoring
- **Market Activity Score**: Overall marketplace participation
- **Trend Analysis**: Linear regression on key metrics
- **Forecasts**: Future performance predictions

### Geographic Metrics
- Location-based transaction distribution
- Regional price analysis
- Geographic participant activity

## Performance Characteristics

### Concurrency
- Virtual thread-based execution
- Non-blocking I/O operations
- Parallel event processing
- Asynchronous report generation

### Memory Efficiency
- Bounded event queues
- Trend window management
- Efficient metric aggregation
- Optional metric filtering

### Scalability
- Horizontal scaling support
- Connection pooling for external systems
- Configurable batch sizes
- Graceful degradation under load

## Integration Points

### Event Sources
- Transaction systems
- User activity logs
- Service monitoring systems
- External data feeds

### Destinations
- Real-time dashboards
- Alerting systems
- Data warehouses
- Reporting APIs

### Configuration
```java
// Custom event processors
analytics.setEventProcessor(event -> {
    // Process events based on type
    switch (event.getType()) {
        case TRANSACTION:
            // Handle transaction events
            break;
        case PROVIDER_ACTIVITY:
            // Handle provider activity
            break;
    }
});

// Custom aggregations
GregVerseAnalyticsReporter.MetricAggregation aggregation =
    analytics.aggregateMetrics(a -> a.getTotalTransactions() > 100).get();
```

## Testing

The package includes comprehensive tests covering:
- Event processing and metric aggregation
- Report generation accuracy
- Trend calculations
- Concurrent operation handling
- Error scenarios
- Performance characteristics

Run tests with:
```bash
mvn test -Dtest=GregVerseAnalyticsReporterTest
```

## Dependencies

- Java 21+ (virtual threads)
- CompletableFuture for async operations
- ConcurrentHashMap for thread-safe collections
- Java Time API for temporal calculations

## Best Practices

1. **Start/Stop Management**: Always call start() and stop() in proper lifecycle
2. **Error Handling**: Use try-catch around async operations
3. **Resource Management**: Monitor queue sizes and memory usage
4. **Configuration**: Use builder pattern for complex configurations
5. **Monitoring**: Track performance metrics and trends
6. **Alerting**: Configure appropriate thresholds for critical metrics

## Future Enhancements

- Integration with time-series databases
- Machine learning-based anomaly detection
- Advanced forecasting algorithms
- Multi-dimensional analysis capabilities
- Real-time alerting system
- Export to various data formats