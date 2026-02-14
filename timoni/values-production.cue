// YAWL Production Environment Values

package main

values: {
	environment: "production"

	namespace: {
		name: "yawl-prod"
		create: true
		labels: {
			"environment": "production"
			"managed-by": "timoni"
		}
	}

	image: {
		registry: "us-central1-docker.pkg.dev"
		repository: "my-gcp-project/yawl/yawl"
		tag: "1.0.0" // Use specific production tag
		pullPolicy: "IfNotPresent"
	}

	scaling: {
		replicas: 5
		strategy: {
			type: "RollingUpdate"
			rollingUpdate: {
				maxSurge: 2
				maxUnavailable: 0
			}
		}
		podDisruptionBudget: {
			enabled: true
			minAvailable: 2
		}
	}

	resources: {
		requests: {
			cpu: "2000m"
			memory: "4Gi"
		}
		limits: {
			cpu: "4"
			memory: "8Gi"
		}
	}

	database: {
		host: "cloudsql-proxy.yawl-prod.svc.cluster.local"
		port: 5432
		name: "yawl_prod"
		user: "yawl_app"
		secretName: "yawl-db-credentials"
		secretKey: "password"

		pool: {
			minSize: 10
			maxSize: 100
			acquireIncrement: 10
			maxStatements: 100
			idleTestPeriod: 60
			timeout: 300
		}

		batchSize: 50
		fetchSize: 100
	}

	probes: {
		liveness: {
			enabled: true
			initialDelaySeconds: 120
			periodSeconds: 30
			timeoutSeconds: 10
			failureThreshold: 5
		}
		readiness: {
			enabled: true
			initialDelaySeconds: 60
			periodSeconds: 10
			timeoutSeconds: 5
			failureThreshold: 5
		}
	}

	logging: {
		level: "WARN"
		format: "ISO8601"
		tomcatThreadsMin: 20
		tomcatThreadsMax: 500
		fileRotation: {
			enabled: true
			maxSize: "500MB"
			maxBackups: 60
			maxAgeMinutes: 43200 // 30 days
		}
	}

	affinity: {
		podAntiAffinity: {
			enabled: true
			preferred: true
			weight: 100
			topologyKey: "kubernetes.io/hostname"
		}
		nodeAffinity: {
			enabled: true
			preferredDuringScheduling: {
				weight: 100
				key: "cloud.google.com/gke-nodepool"
				values: ["yawl-pool", "compute-pool"]
			}
		}
	}

	securityContext: {
		podSecurityContext: {
			runAsNonRoot: true
			runAsUser: 1000
			fsGroup: 1000
			supplementalGroups: [1001, 1002]
			seccompProfile: {
				type: "RuntimeDefault"
			}
		}
		containerSecurityContext: {
			allowPrivilegeEscalation: false
			readOnlyRootFilesystem: false
			runAsNonRoot: true
			runAsUser: 1000
			capabilities: {
				drop: ["ALL"]
				add: ["NET_BIND_SERVICE"]
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
				cpu: "250m"
				memory: "256Mi"
			}
			limits: {
				cpu: "1"
				memory: "1Gi"
			}
		}
		instances: [
			"my-gcp-project:us-central1:yawl-postgres-prod=tcp:5432",
		]
		privateIP: true
		useHTTPHealthCheck: true
		securityContext: {
			runAsUser: 2
			readOnlyRootFilesystem: true
		}
	}

	network: {
		serviceType: "LoadBalancer"
		sessionAffinity: "ClientIP"
		sessionAffinityTimeout: 10800
		ports: {
			http: {
				containerPort: 8080
				port: 80
				protocol: "TCP"
			}
			https: {
				containerPort: 8080
				port: 443
				protocol: "TCP"
			}
		}
	}

	jvm: {
		heapSize: "4096m"
		initialHeap: "2048m"
		maxHeap: "4096m"
		gcType: "G1GC"
		gcPauseTarget: 200
		additionalOptions: [
			"-XX:+UnlockExperimentalVMOptions",
			"-XX:G1NewCollectionPercentThreshold=30",
			"-XX:G1MaxNewGenPercent=40",
			"-XX:+ParallelRefProcEnabled",
			"-XX:+AlwaysPreTouch",
		]
	}

	labels: {
		"app.kubernetes.io/name": "yawl"
		"app.kubernetes.io/instance": "yawl-prod"
		"app.kubernetes.io/version": "1.0.0"
		"app.kubernetes.io/managed-by": "timoni"
		"app.kubernetes.io/part-of": "yawl-workflow-engine"
		"environment": "production"
	}

	annotations: {
		"description": "YAWL Workflow Engine - Production"
		"documentation": "https://yawlfoundation.org"
		"alerting": "enabled"
		"monitoring": "prometheus"
	}
}
