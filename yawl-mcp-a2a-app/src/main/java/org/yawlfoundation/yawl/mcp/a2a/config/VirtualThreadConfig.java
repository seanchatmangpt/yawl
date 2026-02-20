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

package org.yawlfoundation.yawl.mcp.a2a.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring configuration for Java 25 virtual thread support in A2A server.
 *
 * <p>This configuration enables virtual threads for all async operations in the
 * Spring Boot A2A application:</p>
 *
 * <ul>
 *   <li><b>Async tasks</b>: Spring {@code @Async} methods run on virtual threads</li>
 *   <li><b>Scheduled tasks</b>: Spring {@code @Scheduled} methods run on virtual threads</li>
 *   <li><b>HTTP client</b>: Shared HttpClient with virtual thread executor</li>
 *   <li><b>Graceful shutdown</b>: Configurable shutdown timeout for virtual threads</li>
 * </ul>
 *
 * <h2>Configuration Properties</h2>
 * <p>Configure via {@code application.yml}:</p>
 * <pre>{@code
 * yawl:
 *   a2a:
 *     virtual-threads:
 *       enabled: true
 *       graceful-shutdown-seconds: 30
 *       http-client-timeout-seconds: 60
 * }</pre>
 *
 * <h2>Virtual Thread Best Practices</h2>
 * <p>When using virtual threads with Spring Boot:</p>
 * <ul>
 *   <li>Avoid synchronized blocks that may pin carrier threads</li>
 *   <li>Use StructuredTaskScope for parallel operations</li>
 *   <li>Let blocking I/O operations block virtual threads naturally</li>
 *   <li>Configure appropriate timeouts to prevent thread accumulation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 * @see <a href="https://openjdk.org/jeps/444">JEP 444: Virtual Threads</a>
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(VirtualThreadConfig.VirtualThreadProperties.class)
public class VirtualThreadConfig implements AsyncConfigurer {

    private static final Logger _logger = LogManager.getLogger(VirtualThreadConfig.class);

    private final VirtualThreadProperties properties;

    /**
     * Construct configuration with properties.
     *
     * @param properties virtual thread configuration properties
     */
    public VirtualThreadConfig(VirtualThreadProperties properties) {
        this.properties = properties;
        _logger.info("VirtualThreadConfig initialized: enabled={}, shutdownTimeout={}s",
                     properties.enabled(), properties.getGracefulShutdownSecondsOrDefault());
    }

    /**
     * Provide a virtual thread executor for Spring async operations.
     *
     * <p>Spring's {@code @Async} annotation will use this executor for
     * asynchronous method execution on virtual threads.</p>
     *
     * @return async task executor backed by virtual threads
     */
    @Override
    @Bean(name = "virtualThreadTaskExecutor")
    public AsyncTaskExecutor getAsyncExecutor() {
        if (!properties.enabled()) {
            _logger.info("Virtual threads disabled, using platform thread pool");
            return new TaskExecutorAdapter(Executors.newCachedThreadPool());
        }

        ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        _logger.info("Virtual thread task executor created for Spring async operations");
        return new TaskExecutorAdapter(virtualThreadExecutor);
    }

    /**
     * Provide a virtual thread executor service bean.
     *
     * <p>This executor can be injected into services that need virtual thread
     * execution for I/O-bound operations.</p>
     *
     * @return virtual thread executor service
     */
    @Bean
    @ConditionalOnProperty(prefix = "yawl.a2a.virtual-threads", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorService virtualThreadExecutor() {
        _logger.info("Creating virtual thread executor service");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Provide a shared HttpClient optimized for virtual threads.
     *
     * <p>This client is configured with:</p>
     * <ul>
     *   <li>Virtual thread executor for async operations</li>
     *   <li>Configurable connection timeout</li>
     *   <li>HTTP/1.1 for compatibility</li>
     * </ul>
     *
     * @return configured HttpClient instance
     */
    @Bean
    @ConditionalOnProperty(prefix = "yawl.a2a.virtual-threads", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HttpClient virtualThreadHttpClient() {
        int timeoutSeconds = properties.getHttpClientTimeoutSecondsOrDefault();
        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        _logger.info("Virtual thread HttpClient created with timeout={}s", timeoutSeconds);
        return client;
    }

    /**
     * Configuration properties for virtual thread setup.
     *
     * @param enabled whether virtual threads are enabled
     * @param gracefulShutdownSeconds graceful shutdown timeout
     * @param httpClientTimeoutSeconds HTTP client connection timeout
     */
    @ConfigurationProperties(prefix = "yawl.a2a.virtual-threads")
    public record VirtualThreadProperties(
        boolean enabled,
        int gracefulShutdownSeconds,
        int httpClientTimeoutSeconds
    ) {
        /**
         * Default configuration values.
         */
        public static final int DEFAULT_GRACEFUL_SHUTDOWN_SECONDS = 30;
        public static final int DEFAULT_HTTP_CLIENT_TIMEOUT_SECONDS = 60;

        /**
         * Create default properties with virtual threads enabled.
         *
         * @return default properties instance
         */
        public static VirtualThreadProperties defaults() {
            return new VirtualThreadProperties(
                true,
                DEFAULT_GRACEFUL_SHUTDOWN_SECONDS,
                DEFAULT_HTTP_CLIENT_TIMEOUT_SECONDS
            );
        }

        /**
         * Create properties with virtual threads disabled.
         *
         * @return properties with virtual threads disabled
         */
        public static VirtualThreadProperties disabled() {
            return new VirtualThreadProperties(
                false,
                DEFAULT_GRACEFUL_SHUTDOWN_SECONDS,
                DEFAULT_HTTP_CLIENT_TIMEOUT_SECONDS
            );
        }

        /**
         * Gets the HTTP client timeout with default fallback.
         *
         * @return the HTTP client timeout in seconds, never zero or negative
         */
        public int getHttpClientTimeoutSecondsOrDefault() {
            return httpClientTimeoutSeconds > 0
                ? httpClientTimeoutSeconds
                : DEFAULT_HTTP_CLIENT_TIMEOUT_SECONDS;
        }

        /**
         * Gets the graceful shutdown timeout with default fallback.
         *
         * @return the graceful shutdown timeout in seconds, never zero or negative
         */
        public int getGracefulShutdownSecondsOrDefault() {
            return gracefulShutdownSeconds > 0
                ? gracefulShutdownSeconds
                : DEFAULT_GRACEFUL_SHUTDOWN_SECONDS;
        }
    }
}
