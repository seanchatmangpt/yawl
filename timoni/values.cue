// YAWL Workflow Engine - Timoni Values Configuration
// Comprehensive validation and production-ready defaults

package main

import (
	"encoding/json"
	"list"
	"strings"
)

// Version constraints for validation
#Version: {
	major: int | *0
	minor: int | *1
	patch: int | *0
}

// Image configuration with validation
#Image: {
	registry: string | *"us-central1-docker.pkg.dev"
	repository: string | *"yawl/yawl"
	tag: string | *"latest"
	pullPolicy: "Always" | "IfNotPresent" | "Never" | *"IfNotPresent"

	// Computed full image reference
	reference: "\(registry)/\(repository):\(tag)"
}

// Resource requests and limits
#Resources: {
	requests: {
		cpu: string | *"500m"
		memory: string | *"1Gi"
	}
	limits: {
		cpu: string | *"2"
		memory: string | *"2Gi"
	}
}

// Database configuration
#Database: {
	host: string | *"cloudsql-proxy.yawl.svc.cluster.local"
	port: int | *5432
	name: string | *"yawl"
	user: string | *"yawl"
	// Password should be provided via secrets
	secretName: string | *"yawl-db-secret"
	secretKey: string | *"password"

	// Connection pool settings
	pool: {
		minSize: int | *5
		maxSize: int | *20
		acquireIncrement: int | *5
		maxStatements: int | *50
		idleTestPeriod: int | *60
		timeout: int | *300
	}

	// Hibernate/Datasource settings
	driver: "org.postgresql.Driver" | *"org.postgresql.Driver"
	dialect: "org.hibernate.dialect.PostgreSQL10Dialect" | *"org.hibernate.dialect.PostgreSQL10Dialect"

	// Connection settings
	batchSize: int & >=1 & <=50 | *20
	fetchSize: int & >=1 & <=100 | *50
	isolation: int | *2 // SERIALIZABLE
	showSQL: bool | *false
	formatSQL: bool | *true
	generateStatistics: bool | *false
}

// Probe configuration
#Probe: {
	enabled: bool | *true
	initialDelaySeconds: int | *30
	periodSeconds: int | *10
	timeoutSeconds: int | *5
	failureThreshold: int | *3
	successThreshold: int | *1
	path: string | *"/resourceService/"
}

// Logging configuration
#Logging: {
	level: "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL" | *"INFO"
	format: string | *"ISO8601"

	// Tomcat thread configuration
	tomcatThreadsMin: int & >=1 & <=1000 | *10
	tomcatThreadsMax: int & >=10 & <=10000 | *250

	// File rotation settings
	fileRotation: {
		enabled: bool | *true
		maxSize: string | *"100MB"
		maxBackups: int | *30
		maxAgeMinutes: int | *10080 // 7 days
	}
}

// Scaling configuration
#Scaling: {
	replicas: int & >=1 | *3

	// Rolling update strategy
	strategy: {
		type: "RollingUpdate" | *"RollingUpdate"
		rollingUpdate: {
			maxSurge: int | 1 | *1
			maxUnavailable: int | *0
		}
	}

	// Pod disruption budget
	podDisruptionBudget: {
		enabled: bool | *true
		minAvailable: int | *1
	}
}

// Pod affinity and topology spread
#Affinity: {
	// Pod anti-affinity to spread across nodes
	podAntiAffinity: {
		enabled: bool | *true
		preferred: bool | *true
		weight: int | *100
		topologyKey: string | *"kubernetes.io/hostname"
	}

	// Node affinity for specific node pools
	nodeAffinity: {
		enabled: bool | *false
		preferredDuringScheduling: {
			weight: int | *50
			key: string | *"cloud.google.com/gke-nodepool"
			values: [...string]
		}
	}
}

// Security context configuration
#SecurityContext: {
	podSecurityContext: {
		runAsNonRoot: bool | *true
		runAsUser: int | *1000
		fsGroup: int | *1000
		supplementalGroups: [...int] | *[]
		seccompProfile: {
			type: string | *"RuntimeDefault"
		}
	}

	containerSecurityContext: {
		allowPrivilegeEscalation: bool | *false
		readOnlyRootFilesystem: bool | *false
		runAsNonRoot: bool | *true
		runAsUser: int | *1000
		capabilities: {
			drop: [...string] | *["ALL"]
			add: [...string] | *[]
		}
	}
}

// Cloud SQL Proxy configuration
#CloudSQLProxy: {
	enabled: bool | *true
	image: #Image & {
		registry: "gcr.io"
		repository: "cloudsql-docker/cloud-sql-proxy"
		tag: "2.7.0"
	}
	resources: #Resources & {
		requests: {
			cpu: "100m"
			memory: "128Mi"
		}
		limits: {
			cpu: "500m"
			memory: "512Mi"
		}
	}
	instances: [...string]
	privateIP: bool | *true
	useHTTPHealthCheck: bool | *true

	securityContext: #SecurityContext.containerSecurityContext & {
		runAsUser: int | *2
		readOnlyRootFilesystem: bool | *true
	}
}

// Ingress/Network configuration
#Network: {
	serviceType: "ClusterIP" | "NodePort" | "LoadBalancer" | *"LoadBalancer"
	sessionAffinity: "None" | "ClientIP" | *"ClientIP"
	sessionAffinityTimeout: int | *10800 // 3 hours

	ports: {
		http: {
			containerPort: int | *8080
			port: int | *80
			protocol: "TCP" | "UDP" | *"TCP"
		}
		https: {
			containerPort: int | *8080
			port: int | *443
			protocol: "TCP" | "UDP" | *"TCP"
		}
	}
}

// JVM configuration
#JVM: {
	heapSize: string | *"1024m"
	initialHeap: string | *"512m"
	maxHeap: string | *"1024m"

	// Garbage collection settings
	gcType: "G1GC" | "ConcMarkSweepGC" | "ParallelGC" | *"G1GC"
	gcPauseTarget: int | *200 // milliseconds

	// Additional JVM options
	additionalOptions: [...string] | *[]
}

// Namespace configuration
#Namespace: {
	name: string | *"yawl"
	create: bool | *true
	labels: {
		[string]: string
	} | *{
		"app.kubernetes.io/name": "yawl"
		"app.kubernetes.io/component": "workflow-engine"
	}
}

// Root values schema
values: {
	// Namespace settings
	namespace: #Namespace

	// Container image
	image: #Image

	// Scaling
	scaling: #Scaling

	// Resources
	resources: #Resources

	// Database
	database: #Database

	// Probes
	probes: {
		liveness: #Probe & {
			initialDelaySeconds: int | *60
		}
		readiness: #Probe & {
			initialDelaySeconds: int | *30
		}
	}

	// Logging
	logging: #Logging

	// Pod affinity
	affinity: #Affinity

	// Security
	securityContext: #SecurityContext

	// Cloud SQL Proxy
	cloudSQLProxy: #CloudSQLProxy

	// Network
	network: #Network

	// JVM
	jvm: #JVM

	// Additional labels for all resources
	labels: {
		[string]: string
	} | *{
		"app.kubernetes.io/name": "yawl"
		"app.kubernetes.io/version": "0.1.0"
		"app.kubernetes.io/managed-by": "timoni"
		"app.kubernetes.io/part-of": "yawl-workflow-engine"
	}

	// Annotations for resources
	annotations: {
		[string]: string
	} | *{
		"description": "YAWL Workflow Engine"
		"documentation": "https://yawlfoundation.org"
	}

	// Environment settings (development, staging, production)
	environment: "development" | "staging" | "production" | *"production"
}

// Production preset - override defaults for production
if values.environment == "production" {
	values: {
		scaling: replicas: 3
		resources: {
			requests: {
				cpu: "1000m"
				memory: "2Gi"
			}
			limits: {
				cpu: "4"
				memory: "4Gi"
			}
		}
		logging: level: "WARN"
		probes: {
			liveness: failureThreshold: 5
			readiness: failureThreshold: 5
		}
		affinity: podAntiAffinity: enabled: true
		jvm: {
			initialHeap: "1024m"
			maxHeap: "2048m"
		}
		database: pool: maxSize: 50
	}
}

// Development preset
if values.environment == "development" {
	values: {
		scaling: replicas: 1
		resources: {
			requests: {
				cpu: "250m"
				memory: "512Mi"
			}
			limits: {
				cpu: "1"
				memory: "1Gi"
			}
		}
		logging: level: "DEBUG"
		image: pullPolicy: "Always"
		probes: enabled: false
	}
}

// Staging preset
if values.environment == "staging" {
	values: {
		scaling: replicas: 2
		resources: {
			requests: {
				cpu: "750m"
				memory: "1.5Gi"
			}
			limits: {
				cpu: "2"
				memory: "2Gi"
			}
		}
		logging: level: "INFO"
		affinity: podAntiAffinity: enabled: true
	}
}
