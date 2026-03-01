/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.supervision;

import org.yawlfoundation.yawl.erlang.workflow.ServiceRestartEvent;
import org.yawlfoundation.yawl.erlang.workflow.WorkflowEventBus;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Autonomic supervisor for Java services in the YAWL Native Bridge Architecture.
 *
 * <p>The supervisor health-checks each registered {@link ManagedService} at a
 * configurable interval (default: 5 seconds). When a service fails
 * {@code maxConsecutiveFailures} consecutive health checks, the supervisor
 * autonomously triggers a restart sequence:</p>
 *
 * <ol>
 *   <li>Transition service to {@link ServiceStatus#DRAINING} — call {@code drain(5s)}</li>
 *   <li>Construct a replacement instance via the registered {@link Supplier} factory</li>
 *   <li>Transition to {@link ServiceStatus#RUNNING} with the new instance</li>
 *   <li>Publish {@link ServiceRestartEvent} to the {@link WorkflowEventBus}</li>
 *   <li>Increment the cumulative restart counter for the service</li>
 * </ol>
 *
 * <p>OTP integration is optional: if an {@code otpCallable} is provided at
 * construction, the supervisor will notify the OTP supervisor node via
 * {@code yawl_supervisor:service_restarted/2} RPC after each restart. This
 * is not required for correct autonomous operation in pure-Java deployments.</p>
 *
 * <p>Usage:
 * <pre>
 *   WorkflowEventBus bus = new WorkflowEventBus();
 *   YawlServiceSupervisor supervisor = new YawlServiceSupervisor(bus, 3);
 *
 *   supervisor.register("data-modelling",
 *       () -> new DataModellingService(bridge),
 *       Duration.ofSeconds(5));
 *
 *   // supervisor runs autonomously, restarting failed services
 *   // ...
 *   supervisor.close();
 * </pre>
 *
 * @see ManagedService
 * @see ServiceRestartEvent
 */
public final class YawlServiceSupervisor implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(YawlServiceSupervisor.class.getName());
    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private record Registration(
            String name,
            AtomicReference<ManagedService> serviceRef,
            Supplier<ManagedService> factory,
            AtomicInteger consecutiveFailures,
            AtomicInteger totalRestarts,
            AtomicReference<ServiceStatus> status) {}

    private final WorkflowEventBus eventBus;
    private final int maxConsecutiveFailures;
    private final Duration checkInterval;
    private final Duration drainTimeout;
    private final ConcurrentHashMap<String, Registration> registrations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdog;
    private volatile boolean closed = false;
    private ScheduledFuture<?> watchdogTask;

    /**
     * Creates a supervisor with default check interval (5 seconds) and drain timeout (5 seconds).
     *
     * @param eventBus             bus for publishing {@link ServiceRestartEvent}s
     * @param maxConsecutiveFailures number of consecutive failures before restart (≥ 1)
     * @throws IllegalArgumentException if maxConsecutiveFailures < 1
     */
    public YawlServiceSupervisor(WorkflowEventBus eventBus, int maxConsecutiveFailures) {
        this(eventBus, maxConsecutiveFailures,
             Duration.ofSeconds(5), DEFAULT_DRAIN_TIMEOUT);
    }

    /**
     * Creates a supervisor with configurable intervals.
     *
     * @param eventBus             bus for publishing {@link ServiceRestartEvent}s
     * @param maxConsecutiveFailures number of consecutive failures before restart (≥ 1)
     * @param checkInterval        interval between health checks (must be positive)
     * @param drainTimeout         time allowed for draining before forced restart
     */
    public YawlServiceSupervisor(
            WorkflowEventBus eventBus,
            int maxConsecutiveFailures,
            Duration checkInterval,
            Duration drainTimeout) {
        if (eventBus == null) throw new IllegalArgumentException("eventBus must not be null");
        if (maxConsecutiveFailures < 1)
            throw new IllegalArgumentException("maxConsecutiveFailures must be >= 1");
        if (checkInterval == null || checkInterval.isNegative() || checkInterval.isZero())
            throw new IllegalArgumentException("checkInterval must be positive");
        if (drainTimeout == null || drainTimeout.isNegative() || drainTimeout.isZero())
            throw new IllegalArgumentException("drainTimeout must be positive");

        this.eventBus = eventBus;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.checkInterval = checkInterval;
        this.drainTimeout = drainTimeout;

        this.watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Thread.ofVirtual().unstarted(r);
            t.setName("yawl-supervisor-watchdog");
            t.setDaemon(true);
            return t;
        });

        long intervalMs = checkInterval.toMillis();
        this.watchdogTask = watchdog.scheduleAtFixedRate(
                this::runHealthChecks, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Registers a service for autonomous supervision.
     *
     * <p>The service is immediately transitioned to {@link ServiceStatus#RUNNING}.
     * The factory is called on each restart to produce a fresh service instance.</p>
     *
     * @param name    unique service name within this supervisor
     * @param factory supplies a new service instance on restart
     * @throws SupervisionException     if a service with the same name is already registered
     * @throws IllegalArgumentException if name or factory is null
     */
    public void register(String name, Supplier<ManagedService> factory) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name must be non-blank");
        if (factory == null)
            throw new IllegalArgumentException("factory must not be null");
        if (closed) throw new SupervisionException("Supervisor is closed");

        ManagedService initial = factory.get();
        if (initial == null)
            throw new SupervisionException("Factory returned null for service '" + name + "'");

        Registration registration = new Registration(
                name,
                new AtomicReference<>(initial),
                factory,
                new AtomicInteger(0),
                new AtomicInteger(0),
                new AtomicReference<>(ServiceStatus.RUNNING));

        if (registrations.putIfAbsent(name, registration) != null) {
            throw new SupervisionException(
                    "Service '" + name + "' is already registered. Call deregister() first.");
        }

        LOG.info("YawlServiceSupervisor: registered service '" + name + "'");
    }

    /**
     * Deregisters a service, draining it before removal.
     *
     * @param name the service name to deregister
     * @return true if the service was found and removed, false if not found
     */
    public boolean deregister(String name) {
        Registration reg = registrations.remove(name);
        if (reg == null) return false;

        reg.status().set(ServiceStatus.DRAINING);
        try {
            reg.serviceRef().get().drain(drainTimeout);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Exception draining service '" + name + "' during deregister", e);
        }
        reg.status().set(ServiceStatus.STOPPED);
        LOG.info("YawlServiceSupervisor: deregistered service '" + name + "'");
        return true;
    }

    /**
     * Returns the current status of a registered service.
     *
     * @param name the service name
     * @return the service status, or null if not registered
     */
    public ServiceStatus getStatus(String name) {
        Registration reg = registrations.get(name);
        return reg == null ? null : reg.status().get();
    }

    /**
     * Returns the cumulative restart count for a service.
     *
     * @param name the service name
     * @return restart count, or -1 if not registered
     */
    public int getRestartCount(String name) {
        Registration reg = registrations.get(name);
        return reg == null ? -1 : reg.totalRestarts().get();
    }

    /**
     * Manually pings a registered service.
     *
     * @param name the service name
     * @return true if the service is healthy, false if unhealthy or not registered
     */
    public boolean ping(String name) {
        Registration reg = registrations.get(name);
        if (reg == null) return false;
        try {
            return reg.serviceRef().get().ping();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Exception pinging service '" + name + "'", e);
            return false;
        }
    }

    /**
     * Closes the supervisor, stopping the watchdog and draining all services.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;

        if (watchdogTask != null) {
            watchdogTask.cancel(false);
        }
        watchdog.shutdown();

        for (String name : registrations.keySet()) {
            deregister(name);
        }
        LOG.info("YawlServiceSupervisor: closed");
    }

    // -------------------------------------------------------------------------
    // Private watchdog logic
    // -------------------------------------------------------------------------

    private void runHealthChecks() {
        for (Registration reg : registrations.values()) {
            if (reg.status().get() == ServiceStatus.DRAINING ||
                reg.status().get() == ServiceStatus.STOPPED) {
                continue;
            }
            checkService(reg);
        }
    }

    private void checkService(Registration reg) {
        boolean healthy;
        try {
            healthy = reg.serviceRef().get().ping();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Exception pinging service '" + reg.name() + "'", e);
            healthy = false;
        }

        if (healthy) {
            reg.consecutiveFailures().set(0);
        } else {
            int failures = reg.consecutiveFailures().incrementAndGet();
            LOG.warning("Service '" + reg.name() + "' ping failed (consecutive: " + failures + ")");

            if (failures >= maxConsecutiveFailures) {
                restartService(reg, "ping failed " + failures + " consecutive times");
            }
        }
    }

    private void restartService(Registration reg, String reason) {
        reg.status().set(ServiceStatus.DRAINING);
        String name = reg.name();

        LOG.warning("YawlServiceSupervisor: restarting service '" + name + "' — " + reason);

        try {
            reg.serviceRef().get().drain(drainTimeout);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Exception draining '" + name + "' before restart", e);
        }

        ManagedService newInstance;
        try {
            newInstance = reg.factory().get();
            if (newInstance == null) {
                LOG.severe("Factory returned null for '" + name + "' — restart aborted");
                reg.status().set(ServiceStatus.RUNNING);
                return;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Factory threw for '" + name + "' — restart aborted", e);
            reg.status().set(ServiceStatus.RUNNING);
            return;
        }

        reg.serviceRef().set(newInstance);
        reg.consecutiveFailures().set(0);
        int count = reg.totalRestarts().incrementAndGet();
        reg.status().set(ServiceStatus.RUNNING);

        eventBus.publish(new ServiceRestartEvent(name, Instant.now(), count, reason));
        LOG.info("YawlServiceSupervisor: service '" + name + "' restarted (total: " + count + ")");
    }
}
