package org.yawlfoundation.yawl.mcp.a2a.gregverse.analytics;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Generates daily, weekly, and monthly reports for GregVerse marketplace
 */
public class ReportGenerator {

    private final MarketplaceMetrics metrics;
    private final ExecutorService executor;
    private final Map<ReportType, ScheduledFuture<?>> scheduledReports;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public ReportGenerator(MarketplaceMetrics metrics) {
        this.metrics = metrics;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduledReports = new ConcurrentHashMap<>();
    }

    public void startScheduledReports() {
        if (isRunning.compareAndSet(false, true)) {
            // Schedule daily reports at 23:59 UTC
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> daily = scheduler.scheduleAtFixedRate(
                this::generateDailyReport,
                calculateNextTime(23, 59),
                24, TimeUnit.HOURS
            );

            // Schedule weekly reports on Sunday at 23:59 UTC
            ScheduledFuture<?> weekly = scheduler.scheduleAtFixedRate(
                this::generateWeeklyReport,
                calculateNextTime(0, 0, DayOfWeek.SUNDAY),
                7, TimeUnit.DAYS
            );

            // Schedule monthly reports on last day at 23:59 UTC
            ScheduledFuture<?> monthly = scheduler.scheduleAtFixedRate(
                this::generateMonthlyReport,
                calculateNextMonthEnd(),
                30, TimeUnit.DAYS  // Approximate, will adjust for actual month length
            );

            scheduledReports.put(ReportType.DAILY, daily);
            scheduledReports.put(ReportType.WEEKLY, weekly);
            scheduledReports.put(ReportType.MONTHLY, monthly);

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopScheduledReports));
        }
    }

    public void stopScheduledReports() {
        if (isRunning.compareAndSet(true, false)) {
            scheduledReports.values().forEach(future -> future.cancel(true));
            executor.shutdown();
        }
    }

    public CompletableFuture<DailyReport> generateDailyReportAsync() {
        return CompletableFuture.supplyAsync(this::generateDailyReport, executor);
    }

    public CompletableFuture<WeeklyReport> generateWeeklyReportAsync() {
        return CompletableFuture.supplyAsync(this::generateWeeklyReport, executor);
    }

    public CompletableFuture<MonthlyReport> generateMonthlyReportAsync() {
        return CompletableFuture.supplyAsync(this::generateMonthlyReport, executor);
    }

    public CompletableFuture<CustomReport> generateCustomReportAsync(
            LocalDate startDate, LocalDate endDate, ReportFilter... filters) {
        return CompletableFuture.supplyAsync(
            () -> generateCustomReport(startDate, endDate, filters),
            executor
        );
    }

    private DailyReport generateDailyReport() {
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();

        return new DailyReport(
            today,
            metrics.getActiveProviderCount(),
            metrics.getActiveConsumerCount(),
            metrics.getTotalTransactionCount(),
            metrics.getAverageTransactionValue(),
            metrics.getAverageResponseTime(),
            metrics.getAveragePricesByCategory(),
            metrics.getRatingDistributionPercentages(),
            metrics.getTransactionCountsByCategory(),
            metrics.getGeographicDistribution(),
            now
        );
    }

    private WeeklyReport generateWeeklyReport() {
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        // In a real implementation, we would store historical data
        // For now, using current metrics as approximation
        return new WeeklyReport(
            weekStart,
            weekEnd,
            metrics.getActiveProviderCount(),
            metrics.getActiveConsumerCount(),
            metrics.getTotalTransactionCount(),
            metrics.getAverageTransactionValue(),
            metrics.getAverageResponseTime(),
            metrics.getAveragePricesByCategory(),
            metrics.getRatingDistributionPercentages(),
            metrics.getTransactionCountsByCategory(),
            metrics.getGeographicDistribution(),
            now
        );
    }

    private MonthlyReport generateMonthlyReport() {
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        YearMonth month = YearMonth.from(today);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        return new MonthlyReport(
            month,
            monthStart,
            monthEnd,
            metrics.getActiveProviderCount(),
            metrics.getActiveConsumerCount(),
            metrics.getTotalTransactionCount(),
            metrics.getAverageTransactionValue(),
            metrics.getAverageResponseTime(),
            metrics.getAveragePricesByCategory(),
            metrics.getRatingDistributionPercentages(),
            metrics.getTransactionCountsByCategory(),
            metrics.getGeographicDistribution(),
            now
        );
    }

    private CustomReport generateCustomReport(LocalDate startDate, LocalDate endDate, ReportFilter... filters) {
        Instant now = Instant.now();

        // Start with copies of the metrics data
        Map<String, Double> categoryMetrics = new HashMap<>(metrics.getAveragePricesByCategory());
        Map<Integer, Double> ratingDistribution = new HashMap<>(metrics.getRatingDistributionPercentages());
        Map<String, Long> transactionCounts = new HashMap<>(metrics.getTransactionCountsByCategory());
        Map<String, Long> geographicDistribution = new HashMap<>(metrics.getGeographicDistribution());

        // Apply filters if provided
        if (filters != null) {
            for (ReportFilter filter : filters) {
                switch (filter.getType()) {
                    case CATEGORY:
                        categoryMetrics.entrySet().retainAll(
                            categoryMetrics.entrySet().stream()
                                .filter(e -> filter.getValues().contains(e.getKey()))
                                .collect(Collectors.toSet())
                        );
                        break;
                    case RATING:
                        ratingDistribution.entrySet().retainAll(
                            ratingDistribution.entrySet().stream()
                                .filter(e -> filter.getValues().contains(String.valueOf(e.getKey())))
                                .collect(Collectors.toSet())
                        );
                        break;
                    case LOCATION:
                        geographicDistribution.entrySet().retainAll(
                            geographicDistribution.entrySet().stream()
                                .filter(e -> filter.getValues().contains(e.getKey()))
                                .collect(Collectors.toSet())
                        );
                        break;
                }
            }
        }

        return new CustomReport(
            startDate,
            endDate,
            metrics.getActiveProviderCount(),
            metrics.getActiveConsumerCount(),
            metrics.getTotalTransactionCount(),
            metrics.getAverageTransactionValue(),
            metrics.getAverageResponseTime(),
            categoryMetrics,
            ratingDistribution,
            transactionCounts,
            geographicDistribution,
            filters,
            now
        );
    }

    private long calculateNextTime(int hour, int minute) {
        return calculateNextTime(hour, minute, null);
    }

    private long calculateNextTime(int hour, int minute, DayOfWeek dayOfWeek) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime next = now.withHour(hour).withMinute(minute).withSecond(0);

        if (dayOfWeek != null) {
            next = next.with(DayOfWeek.values()[(next.getDayOfWeek().getValue() % 7 + 1) % 7]);
        }

        if (next.isBefore(now)) {
            next = next.plusDays(1);
        }

        return Duration.between(now, next).toMillis();
    }

    private long calculateNextMonthEnd() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate monthEnd = now.withDayOfMonth(now.lengthOfMonth());

        if (monthEnd.isAfter(now)) {
            return Duration.between(now.atStartOfDay(), monthEnd.atTime(23, 59)).toMillis();
        } else {
            // Next month end
            monthEnd = now.plusMonths(1).withDayOfMonth(1).minusDays(1);
            return Duration.between(now.atStartOfDay(), monthEnd.atTime(23, 59)).toMillis();
        }
    }

    // Report types
    public enum ReportType {
        DAILY, WEEKLY, MONTHLY, CUSTOM
    }

    // Filter types for custom reports
    public enum FilterType {
        CATEGORY, RATING, LOCATION
    }

    public static class ReportFilter {
        private final FilterType type;
        private final List<String> values;

        public ReportFilter(FilterType type, List<String> values) {
            this.type = type;
            this.values = values;
        }

        public FilterType getType() { return type; }
        public List<String> getValues() { return values; }
    }

    // Report DTOs
    public static class DailyReport {
        private final LocalDate date;
        private final int activeProviders;
        private final int activeConsumers;
        private final long totalTransactions;
        private final double averageTransactionValue;
        private final double averageResponseTime;
        private final Map<String, Double> averagePricesByCategory;
        private final Map<Integer, Double> ratingDistribution;
        private final Map<String, Long> transactionCountsByCategory;
        private final Map<String, Long> geographicDistribution;
        private final Instant generatedAt;

        public DailyReport(LocalDate date, int activeProviders, int activeConsumers,
                          long totalTransactions, double averageTransactionValue,
                          double averageResponseTime, Map<String, Double> averagePricesByCategory,
                          Map<Integer, Double> ratingDistribution,
                          Map<String, Long> transactionCountsByCategory,
                          Map<String, Long> geographicDistribution, Instant generatedAt) {
            this.date = date;
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
            this.totalTransactions = totalTransactions;
            this.averageTransactionValue = averageTransactionValue;
            this.averageResponseTime = averageResponseTime;
            this.averagePricesByCategory = averagePricesByCategory;
            this.ratingDistribution = ratingDistribution;
            this.transactionCountsByCategory = transactionCountsByCategory;
            this.geographicDistribution = geographicDistribution;
            this.generatedAt = generatedAt;
        }

        // Getters
        public LocalDate getDate() { return date; }
        public int getActiveProviders() { return activeProviders; }
        public int getActiveConsumers() { return activeConsumers; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getAverageTransactionValue() { return averageTransactionValue; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public Map<String, Double> getAveragePricesByCategory() { return averagePricesByCategory; }
        public Map<Integer, Double> getRatingDistribution() { return ratingDistribution; }
        public Map<String, Long> getTransactionCountsByCategory() { return transactionCountsByCategory; }
        public Map<String, Long> getGeographicDistribution() { return geographicDistribution; }
        public Instant getGeneratedAt() { return generatedAt; }
    }

    public static class WeeklyReport {
        private final LocalDate weekStart;
        private final LocalDate weekEnd;
        private final int activeProviders;
        private final int activeConsumers;
        private final long totalTransactions;
        private final double averageTransactionValue;
        private final double averageResponseTime;
        private final Map<String, Double> averagePricesByCategory;
        private final Map<Integer, Double> ratingDistribution;
        private final Map<String, Long> transactionCountsByCategory;
        private final Map<String, Long> geographicDistribution;
        private final Instant generatedAt;

        public WeeklyReport(LocalDate weekStart, LocalDate weekEnd, int activeProviders,
                          int activeConsumers, long totalTransactions, double averageTransactionValue,
                          double averageResponseTime, Map<String, Double> averagePricesByCategory,
                          Map<Integer, Double> ratingDistribution,
                          Map<String, Long> transactionCountsByCategory,
                          Map<String, Long> geographicDistribution, Instant generatedAt) {
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
            this.totalTransactions = totalTransactions;
            this.averageTransactionValue = averageTransactionValue;
            this.averageResponseTime = averageResponseTime;
            this.averagePricesByCategory = averagePricesByCategory;
            this.ratingDistribution = ratingDistribution;
            this.transactionCountsByCategory = transactionCountsByCategory;
            this.geographicDistribution = geographicDistribution;
            this.generatedAt = generatedAt;
        }

        // Getters
        public LocalDate getWeekStart() { return weekStart; }
        public LocalDate getWeekEnd() { return weekEnd; }
        public int getActiveProviders() { return activeProviders; }
        public int getActiveConsumers() { return activeConsumers; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getAverageTransactionValue() { return averageTransactionValue; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public Map<String, Double> getAveragePricesByCategory() { return averagePricesByCategory; }
        public Map<Integer, Double> getRatingDistribution() { return ratingDistribution; }
        public Map<String, Long> getTransactionCountsByCategory() { return transactionCountsByCategory; }
        public Map<String, Long> getGeographicDistribution() { return geographicDistribution; }
        public Instant getGeneratedAt() { return generatedAt; }
    }

    public static class MonthlyReport {
        private final YearMonth month;
        private final LocalDate monthStart;
        private final LocalDate monthEnd;
        private final int activeProviders;
        private final int activeConsumers;
        private final long totalTransactions;
        private final double averageTransactionValue;
        private final double averageResponseTime;
        private final Map<String, Double> averagePricesByCategory;
        private final Map<Integer, Double> ratingDistribution;
        private final Map<String, Long> transactionCountsByCategory;
        private final Map<String, Long> geographicDistribution;
        private final Instant generatedAt;

        public MonthlyReport(YearMonth month, LocalDate monthStart, LocalDate monthEnd,
                            int activeProviders, int activeConsumers, long totalTransactions,
                            double averageTransactionValue, double averageResponseTime,
                            Map<String, Double> averagePricesByCategory,
                            Map<Integer, Double> ratingDistribution,
                            Map<String, Long> transactionCountsByCategory,
                            Map<String, Long> geographicDistribution, Instant generatedAt) {
            this.month = month;
            this.monthStart = monthStart;
            this.monthEnd = monthEnd;
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
            this.totalTransactions = totalTransactions;
            this.averageTransactionValue = averageTransactionValue;
            this.averageResponseTime = averageResponseTime;
            this.averagePricesByCategory = averagePricesByCategory;
            this.ratingDistribution = ratingDistribution;
            this.transactionCountsByCategory = transactionCountsByCategory;
            this.geographicDistribution = geographicDistribution;
            this.generatedAt = generatedAt;
        }

        // Getters
        public YearMonth getMonth() { return month; }
        public LocalDate getMonthStart() { return monthStart; }
        public LocalDate getMonthEnd() { return monthEnd; }
        public int getActiveProviders() { return activeProviders; }
        public int getActiveConsumers() { return activeConsumers; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getAverageTransactionValue() { return averageTransactionValue; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public Map<String, Double> getAveragePricesByCategory() { return averagePricesByCategory; }
        public Map<Integer, Double> getRatingDistribution() { return ratingDistribution; }
        public Map<String, Long> getTransactionCountsByCategory() { return transactionCountsByCategory; }
        public Map<String, Long> getGeographicDistribution() { return geographicDistribution; }
        public Instant getGeneratedAt() { return generatedAt; }
    }

    public static class CustomReport {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final int activeProviders;
        private final int activeConsumers;
        private final long totalTransactions;
        private final double averageTransactionValue;
        private final double averageResponseTime;
        private final Map<String, Double> averagePricesByCategory;
        private final Map<Integer, Double> ratingDistribution;
        private final Map<String, Long> transactionCountsByCategory;
        private final Map<String, Long> geographicDistribution;
        private final ReportFilter[] filters;
        private final Instant generatedAt;

        public CustomReport(LocalDate startDate, LocalDate endDate, int activeProviders,
                           int activeConsumers, long totalTransactions, double averageTransactionValue,
                           double averageResponseTime, Map<String, Double> averagePricesByCategory,
                           Map<Integer, Double> ratingDistribution,
                           Map<String, Long> transactionCountsByCategory,
                           Map<String, Long> geographicDistribution, ReportFilter[] filters,
                           Instant generatedAt) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
            this.totalTransactions = totalTransactions;
            this.averageTransactionValue = averageTransactionValue;
            this.averageResponseTime = averageResponseTime;
            this.averagePricesByCategory = averagePricesByCategory;
            this.ratingDistribution = ratingDistribution;
            this.transactionCountsByCategory = transactionCountsByCategory;
            this.geographicDistribution = geographicDistribution;
            this.filters = filters;
            this.generatedAt = generatedAt;
        }

        // Getters
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public int getActiveProviders() { return activeProviders; }
        public int getActiveConsumers() { return activeConsumers; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getAverageTransactionValue() { return averageTransactionValue; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public Map<String, Double> getAveragePricesByCategory() { return averagePricesByCategory; }
        public Map<Integer, Double> getRatingDistribution() { return ratingDistribution; }
        public Map<String, Long> getTransactionCountsByCategory() { return transactionCountsByCategory; }
        public Map<String, Long> getGeographicDistribution() { return geographicDistribution; }
        public ReportFilter[] getFilters() { return filters; }
        public Instant getGeneratedAt() { return generatedAt; }
    }
}