// YAWL Staging Environment Values

package main

values: {
	environment: "staging"

	namespace: {
		name: "yawl-staging"
		create: true
		labels: {
			"environment": "staging"
			"managed-by": "timoni"
		}
	}

	image: {
		registry: "us-central1-docker.pkg.dev"
		repository: "my-gcp-project/yawl/yawl"
		tag: "1.0.0-rc1" // Use release candidate tag
		pullPolicy: "IfNotPresent"
	}

	scaling: {
		replicas: 3
		strategy: {
			type: "RollingUpdate"
			rollingUpdate: {
				maxSurge: 1
				maxUnavailable: 0
			}
		}
		podDisruptionBudget: {
			enabled: true
			minAvailable: 1
		}
	}

	resources: {
		requests: {
			cpu: "1000m"
			memory: "2Gi"
		}
		limits: {
			cpu: "2"
			memory: "4Gi"
		}
	}

	database: {
		host: "cloudsql-proxy.yawl-staging.svc.cluster.local"
		port: 5432
		name: "yawl_staging"
		user: "yawl_app"
		secretName: "yawl-db-credentials"
		secretKey: "password"

		pool: {
			minSize: 5
			maxSize: 50
			acquireIncrement: 5
			maxStatements: 50
			idleTestPeriod: 60
			timeout: 300
		}

		batchSize: 30
		fetchSize: 75
	}

	probes: {
		liveness: {
			enabled: true
			initialDelaySeconds: 90
			periodSeconds: 30
			timeoutSeconds: 10
			failureThreshold: 3
		}
		readiness: {
			enabled: true
			initialDelaySeconds: 45
			periodSeconds: 15
			timeoutSeconds: 5
			failureThreshold: 3
		}
	}

	logging: {
		level: "INFO"
		format: "ISO8601"
		tomcatThreadsMin: 15
		tomcatThreadsMax: 300
		fileRotation: {
			enabled: true
			maxSize: "200MB"
			maxBackups: 30
			maxAgeMinutes: 10080 // 7 days
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
			enabled: false
			preferredDuringScheduling: {
				weight: 50
				key: "cloud.google.com/gke-nodepool"
				values: ["general-pool"]
			}
		}
	}

	securityContext: {
		podSecurityContext: {
			runAsNonRoot: true
			runAsUser: 1000
			fsGroup: 1000
			supplementalGroups: [1001]
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
				cpu: "100m"
				memory: "128Mi"
			}
			limits: {
				cpu: "500m"
				memory: "512Mi"
			}
		}
		instances: [
			"my-gcp-project:us-central1:yawl-postgres-staging=tcp:5432",
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
		heapSize: "2048m"
		initialHeap: "1024m"
		maxHeap: "2048m"
		gcType: "G1GC"
		gcPauseTarget: 200
		additionalOptions: [
			"-XX:+UnlockExperimentalVMOptions",
			"-XX:G1NewCollectionPercentThreshold=35",
			"-XX:G1MaxNewGenPercent=35",
		]
	}

	labels: {
		"app.kubernetes.io/name": "yawl"
		"app.kubernetes.io/instance": "yawl-staging"
		"app.kubernetes.io/version": "1.0.0-rc1"
		"app.kubernetes.io/managed-by": "timoni"
		"app.kubernetes.io/part-of": "yawl-workflow-engine"
		"environment": "staging"
	}

	annotations: {
		"description": "YAWL Workflow Engine - Staging"
		"documentation": "https://yawlfoundation.org"
		"alerting": "enabled"
		"monitoring": "prometheus"
	}
}
