// YAWL Workflow Engine - ConfigMap Templates

package main

import (
	corev1 "k8s.io/api/core/v1"
	"strconv"
)

// Main application configuration
#AppConfigMap: corev1.#ConfigMap & {
	apiVersion: "v1"
	kind: "ConfigMap"
	metadata: {
		name: "yawl-config"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	data: {
		"YAWL_ENV": values.environment
		"LOG_LEVEL": values.logging.level
		"TOMCAT_THREADS_MAX": strconv.FormatInt(int64(values.logging.tomcatThreadsMax), 10)
		"TOMCAT_THREADS_MIN": strconv.FormatInt(int64(values.logging.tomcatThreadsMin), 10)
		"JVM_OPTS": "-XX:+Use\(values.jvm.gcType) -XX:MaxGCPauseMillis=\(values.jvm.gcPauseTarget)"
		"YAWL_DB_DRIVER": values.database.driver
		"YAWL_DB_DIALECT": values.database.dialect
		"YAWL_DB_BATCH_SIZE": strconv.FormatInt(int64(values.database.batchSize), 10)
		"YAWL_DB_FETCH_SIZE": strconv.FormatInt(int64(values.database.fetchSize), 10)
		"YAWL_DB_CONNECTION_ISOLATION": strconv.FormatInt(int64(values.database.isolation), 10)
	}
}

// Log4j2 logging configuration
#LoggingConfigMap: corev1.#ConfigMap & {
	apiVersion: "v1"
	kind: "ConfigMap"
	metadata: {
		name: "yawl-logging-config"
		namespace: values.namespace.name
		labels: values.labels & {"component": "logging"}
		annotations: values.annotations
	}

	data: {
		"log4j2.xml": """
			<?xml version="1.0" encoding="UTF-8"?>
			<Configuration packages="org.apache.logging.log4j.core">
			  <Appenders>
			    <Console name="Console" target="SYSTEM_OUT">
			      <PatternLayout pattern="%d{\(values.logging.format)} [%t] %-5p %c{1} - %m%n"/>
			    </Console>
			    \(
			      if values.logging.fileRotation.enabled {
				"""
				    <RollingFile name="RollingFile" fileName="logs/yawl.log"
				                 filePattern="logs/yawl-%d{yyyy-MM-dd}-%i.log.gz">
				      <PatternLayout pattern="%d{\(values.logging.format)} [%t] %-5p %c{1} - %m%n"/>
				      <Policies>
				        <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
				        <SizeBasedTriggeringPolicy size="\(values.logging.fileRotation.maxSize)"/>
				      </Policies>
				      <DefaultRolloverStrategy max="\(values.logging.fileRotation.maxBackups)"/>
				    </RollingFile>
				"""
			      } else { "" }
			    )
			  </Appenders>
			  <Loggers>
			    <Root level="\(values.logging.level)">
			      <AppenderRef ref="Console"/>
			      \(if values.logging.fileRotation.enabled {
				      "<AppenderRef ref=\"RollingFile\"/>"
			      } else { "" })
			    </Root>
			    <Logger name="org.yawlfoundation" level="\(
			      if values.environment == "development" { "DEBUG" } else if values.environment == "staging" { "DEBUG" } else { "INFO" }
			    )"/>
			    <Logger name="org.hibernate" level="WARN"/>
			    <Logger name="org.apache.catalina" level="INFO"/>
			    <Logger name="org.springframework" level="INFO"/>
			  </Loggers>
			</Configuration>
			"""
	}
}

// Database datasource configuration
#DatasourceConfigMap: corev1.#ConfigMap & {
	apiVersion: "v1"
	kind: "ConfigMap"
	metadata: {
		name: "yawl-datasource-config"
		namespace: values.namespace.name
		labels: values.labels & {"component": "database"}
		annotations: values.annotations
	}

	data: {
		"datasource.properties": """
			# Hibernate Connection Settings
			hibernate.connection.driver_class=\(values.database.driver)
			hibernate.dialect=\(values.database.dialect)
			hibernate.show_sql=\(values.database.showSQL)
			hibernate.format_sql=\(values.database.formatSQL)
			hibernate.generate_statistics=\(values.database.generateStatistics)

			# Connection Pool Settings
			hibernate.jdbc.batch_size=\(values.database.batchSize)
			hibernate.jdbc.fetch_size=\(values.database.fetchSize)
			hibernate.connection.isolation=\(values.database.isolation)

			# C3P0 Connection Pool Configuration
			hibernate.c3p0.min_size=\(values.database.pool.minSize)
			hibernate.c3p0.max_size=\(values.database.pool.maxSize)
			hibernate.c3p0.acquire_increment=\(values.database.pool.acquireIncrement)
			hibernate.c3p0.idle_test_period=\(values.database.pool.idleTestPeriod)
			hibernate.c3p0.max_statements=\(values.database.pool.maxStatements)
			hibernate.c3p0.timeout=\(values.database.pool.timeout)
			"""
	}
}

// Tomcat configuration for YAWL
#TomcatConfigMap: corev1.#ConfigMap & {
	apiVersion: "v1"
	kind: "ConfigMap"
	metadata: {
		name: "yawl-tomcat-config"
		namespace: values.namespace.name
		labels: values.labels & {"component": "tomcat"}
		annotations: values.annotations
	}

	data: {
		"catalina.properties": """
			# JVM Configuration
			java.util.logging.manager=org.apache.logging.log4j.jul.Log4jBridgeHandler

			# Tomcat Performance Tuning
			org.apache.tomcat.util.net.NioSelectorShared=true
			org.apache.catalina.startup.EXIT_ON_INIT_FAILURE=true

			# Server Properties
			org.apache.catalina.security.SECURITY_MANAGER_PROP=true
			"""

		"server.xml.fragment": """
			<!-- YAWL Custom Connector Configuration -->
			<Connector port="8080"
			           protocol="org.apache.coyote.http11.Http11NioProtocol"
			           redirectPort="8443"
			           maxThreads="\(values.logging.tomcatThreadsMax)"
			           minSpareThreads="\(values.logging.tomcatThreadsMin)"
			           enableLookups="false"
			           acceptorThreadCount="2"
			           processorCache="\(values.logging.tomcatThreadsMax)"
			           TCPNoDelay="true"
			           maxKeepAliveRequests="100"
			           keepAliveTimeout="30000"
			           connectionTimeout="60000"
			           disableUploadTimeout="true"
			           useBodyEncodingForURI="false"
			           compression="on"
			           compressionMinSize="2048"
			           compressibleMimeType="text/html,text/xml,text/plain,text/javascript,application/json"/>

			<!-- Valve Configuration -->
			<Valve className="org.apache.catalina.valves.AccessLogValve"
			       directory="logs"
			       prefix="localhost_access_log"
			       suffix=".txt"
			       pattern="%h %l %u %t &quot;%r&quot; %s %b" />
			"""
	}
}

// Environment-specific configuration overrides
#EnvironmentConfigMap: corev1.#ConfigMap & {
	apiVersion: "v1"
	kind: "ConfigMap"
	metadata: {
		name: "yawl-environment-\(values.environment)"
		namespace: values.namespace.name
		labels: values.labels & {"environment": values.environment}
		annotations: values.annotations & {"environment": values.environment}
	}

	data: {
		if values.environment == "production" {
			"PROFILE": "production"
			"ENABLE_PROFILING": "false"
			"CACHE_SIZE": "10000"
			"BATCH_PROCESSING_SIZE": "100"
		}
		if values.environment == "staging" {
			"PROFILE": "staging"
			"ENABLE_PROFILING": "true"
			"CACHE_SIZE": "5000"
			"BATCH_PROCESSING_SIZE": "50"
		}
		if values.environment == "development" {
			"PROFILE": "development"
			"ENABLE_PROFILING": "true"
			"CACHE_SIZE": "1000"
			"BATCH_PROCESSING_SIZE": "10"
		}
	}
}

// Application properties for YAWL core
#AppPropertiesConfigMap: corev1.#ConfigMap & {
	apiVersion: "v1"
	kind: "ConfigMap"
	metadata: {
		name: "yawl-app-properties"
		namespace: values.namespace.name
		labels: values.labels & {"component": "application"}
		annotations: values.annotations
	}

	data: {
		"application.properties": """
			# YAWL Application Configuration
			app.name=YAWL Workflow Engine
			app.version=0.1.0
			app.environment=\(values.environment)

			# Application Behavior
			app.enable.cache=true
			app.cache.ttl=3600
			app.enable.metrics=true
			app.metrics.interval=60

			# Security
			app.security.enforce.https=\(values.environment == "production")
			app.security.disable.autocreate.accounts=\(values.environment == "production")

			# Performance
			app.max.connections=\(values.database.pool.maxSize)
			app.connection.timeout=\(values.database.pool.timeout)
			app.enable.query.cache=\(values.environment != "development")
			"""
	}
}

// Output all ConfigMaps
appConfigMap: #AppConfigMap
loggingConfigMap: #LoggingConfigMap
datasourceConfigMap: #DatasourceConfigMap
tomcatConfigMap: #TomcatConfigMap
environmentConfigMap: #EnvironmentConfigMap
appPropertiesConfigMap: #AppPropertiesConfigMap
