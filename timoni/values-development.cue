// YAWL Development Environment Values

package main

values: {
	environment: "development"

	namespace: {
		name: "yawl-dev"
		create: true
		labels: {
			"environment": "development"
			"managed-by": "timoni"
		}
	}

	image: {
		registry: "us-central1-docker.pkg.dev"
		repository: "my-gcp-project/yawl/yawl"
		tag: "latest"
		pullPolicy: "Always" // Always pull latest in dev
	}

	scaling: {
		replicas: 1
		strategy: {
			type: "RollingUpdate"
			rollingUpdate: {
				maxSurge: 1
				maxUnavailable: 0
			}
		}
		podDisruptionBudget: {
			enabled: false
		}
	}

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

	database: {
		host: "cloudsql-proxy.yawl-dev.svc.cluster.local"
		port: 5432
		name: "yawl_dev"
		user: "yawl_dev"
		secretName: "yawl-db-credentials"
		secretKey: "password"

		pool: {
			minSize: 2
			maxSize: 10
			acquireIncrement: 2
			maxStatements: 20
			idleTestPeriod: 60
			timeout: 300
		}

		batchSize: 10
		fetchSize: 20
		showSQL: true
		generateStatistics: true
	}

	probes: {
		liveness: {
			enabled: false // Disable for faster development iteration
		}
		readiness: {
			enabled: false
		}
	}

	logging: {
		level: "DEBUG"
		format: "ISO8601"
		tomcatThreadsMin: 5
		tomcatThreadsMax: 50
		fileRotation: {
			enabled: false // Disabled in dev
		}
	}

	affinity: {
		podAntiAffinity: {
			enabled: false
		}
		nodeAffinity: {
			enabled: false
		}
	}

	securityContext: {
		podSecurityContext: {
			runAsNonRoot: false // Less restrictive in dev
			runAsUser: 1000
			fsGroup: 1000
			supplementalGroups: []
			seccompProfile: {
				type: "Unconfined"
			}
		}
		containerSecurityContext: {
			allowPrivilegeEscalation: true // More permissive in dev
			readOnlyRootFilesystem: false
			runAsNonRoot: false
			runAsUser: 0
			capabilities: {
				drop: []
				add: ["NET_ADMIN", "SYS_ADMIN"]
			}
		}
	}

	cloudSQLProxy: {
		enabled: true
		image: {
			registry: "gcr.io"
			repository: "cloudsql-docker/cloud-sql-proxy"
			tag: "2.7.0"
			pullPolicy: "IfNotPresent"
		}
		resources: {
			requests: {
				cpu: "50m"
				memory: "64Mi"
			}
			limits: {
				cpu: "200m"
				memory: "256Mi"
			}
		}
		instances: [
			"my-gcp-project:us-central1:yawl-postgres-dev=tcp:5432",
		]
		privateIP: false // Use public IP in dev for easier access
		useHTTPHealthCheck: true
		securityContext: {
			runAsUser: 0
			readOnlyRootFilesystem: false
		}
	}

	network: {
		serviceType: "ClusterIP" // Use ClusterIP in dev
		sessionAffinity: "None"
		sessionAffinityTimeout: 10800
		ports: {
			http: {
				containerPort: 8080
				port: 8080
				protocol: "TCP"
			}
			https: {
				containerPort: 8080
				port: 8443
				protocol: "TCP"
			}
		}
	}

	jvm: {
		heapSize: "512m"
		initialHeap: "256m"
		maxHeap: "512m"
		gcType: "G1GC"
		gcPauseTarget: 500 // More relaxed in dev
		additionalOptions: [
			"-Xdebug",
			"-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005",
		]
	}

	labels: {
		"app.kubernetes.io/name": "yawl"
		"app.kubernetes.io/instance": "yawl-dev"
		"app.kubernetes.io/version": "latest"
		"app.kubernetes.io/managed-by": "timoni"
		"app.kubernetes.io/part-of": "yawl-workflow-engine"
		"environment": "development"
	}

	annotations: {
		"description": "YAWL Workflow Engine - Development"
		"documentation": "https://yawlfoundation.org"
		"alerting": "disabled"
		"monitoring": "disabled"
	}
}
