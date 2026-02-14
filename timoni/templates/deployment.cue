// YAWL Workflow Engine - Kubernetes Deployment Template

package main

import (
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
)

#Deployment: appsv1.#Deployment & {
	apiVersion: "apps/v1"
	kind: "Deployment"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	spec: {
		replicas: values.scaling.replicas

		// Rolling update strategy
		strategy: {
			type: values.scaling.strategy.type
			if values.scaling.strategy.type == "RollingUpdate" {
				rollingUpdate: {
					maxSurge: values.scaling.strategy.rollingUpdate.maxSurge
					maxUnavailable: values.scaling.strategy.rollingUpdate.maxUnavailable
				}
			}
		}

		selector: {
			matchLabels: {
				"app": "yawl"
				"app.kubernetes.io/name": "yawl"
			}
		}

		template: {
			metadata: {
				labels: {
					"app": "yawl"
					"app.kubernetes.io/name": "yawl"
					"version": "v1"
				} & values.labels
				annotations: values.annotations & {
					"prometheus.io/scrape": "true"
					"prometheus.io/port": "8080"
					"prometheus.io/path": "/metrics"
				}
			}

			spec: {
				// Service account
				serviceAccountName: "yawl"
				automountServiceAccountToken: true

				// Security context for pod
				securityContext: {
					runAsNonRoot: values.securityContext.podSecurityContext.runAsNonRoot
					runAsUser: values.securityContext.podSecurityContext.runAsUser
					fsGroup: values.securityContext.podSecurityContext.fsGroup
					seccompProfile: {
						type: values.securityContext.podSecurityContext.seccompProfile.type
					}
				}

				// Pod affinity configuration
				affinity: {
					if values.affinity.podAntiAffinity.enabled {
						podAntiAffinity: {
							if values.affinity.podAntiAffinity.preferred {
								preferredDuringSchedulingIgnoredDuringExecution: [
									{
										weight: values.affinity.podAntiAffinity.weight
										podAffinityTerm: {
											labelSelector: {
												matchExpressions: [
													{
														key: "app"
														operator: "In"
														values: ["yawl"]
													},
												]
											}
											topologyKey: values.affinity.podAntiAffinity.topologyKey
										}
									},
								]
							}
							if !values.affinity.podAntiAffinity.preferred {
								requiredDuringSchedulingIgnoredDuringExecution: [
									{
										labelSelector: {
											matchExpressions: [
												{
													key: "app"
													operator: "In"
													values: ["yawl"]
												},
											]
										}
										topologyKey: values.affinity.podAntiAffinity.topologyKey
									},
								]
							}
						}
					}
					if values.affinity.nodeAffinity.enabled {
						nodeAffinity: {
							preferredDuringSchedulingIgnoredDuringExecution: [
								{
									weight: values.affinity.nodeAffinity.preferredDuringScheduling.weight
									preference: {
										matchExpressions: [
											{
												key: values.affinity.nodeAffinity.preferredDuringScheduling.key
												operator: "In"
												values: values.affinity.nodeAffinity.preferredDuringScheduling.values
											},
										]
									}
								},
							]
						}
					}
				}

				// DNS policy for better DNS resolution
				dnsPolicy: "ClusterFirst"

				// Termination grace period for graceful shutdown
				terminationGracePeriodSeconds: 30

				containers: [
					// YAWL container
					{
						name: "yawl"
						image: values.image.reference
						imagePullPolicy: values.image.pullPolicy

						// Ports
						ports: [
							{
								name: "http"
								containerPort: values.network.ports.http.containerPort
								protocol: "TCP"
							},
						]

						// Environment variables
						env: [
							{
								name: "YAWL_ENV"
								value: values.environment
							},
							{
								name: "YAWL_DB_HOST"
								value: values.database.host
							},
							{
								name: "YAWL_DB_PORT"
								value: "\(values.database.port)"
							},
							{
								name: "YAWL_DB_NAME"
								value: values.database.name
							},
							{
								name: "YAWL_DB_USER"
								value: values.database.user
							},
							{
								name: "YAWL_DB_PASSWORD"
								valueFrom: {
									secretKeyRef: {
										name: values.database.secretName
										key: values.database.secretKey
									}
								}
							},
							{
								name: "LOG_LEVEL"
								value: values.logging.level
							},
							{
								name: "TOMCAT_THREADS_MIN"
								value: "\(values.logging.tomcatThreadsMin)"
							},
							{
								name: "TOMCAT_THREADS_MAX"
								value: "\(values.logging.tomcatThreadsMax)"
							},
							{
								name: "YAWL_HEAP_SIZE"
								value: values.jvm.maxHeap
							},
							{
								name: "JAVA_OPTS"
								value: "-Xms\(values.jvm.initialHeap) -Xmx\(values.jvm.maxHeap) -XX:+Use\(values.jvm.gcType) -XX:MaxGCPauseMillis=\(values.jvm.gcPauseTarget)"
							},
						]

						// Resources
						resources: {
							requests: {
								cpu: resource.#Quantity & values.resources.requests.cpu
								memory: resource.#Quantity & values.resources.requests.memory
							}
							limits: {
								cpu: resource.#Quantity & values.resources.limits.cpu
								memory: resource.#Quantity & values.resources.limits.memory
							}
						}

						// Liveness probe
						if values.probes.liveness.enabled {
							livenessProbe: {
								httpGet: {
									path: values.probes.liveness.path
									port: "http"
									scheme: "HTTP"
								}
								initialDelaySeconds: values.probes.liveness.initialDelaySeconds
								periodSeconds: values.probes.liveness.periodSeconds
								timeoutSeconds: values.probes.liveness.timeoutSeconds
								failureThreshold: values.probes.liveness.failureThreshold
								successThreshold: values.probes.liveness.successThreshold
							}
						}

						// Readiness probe
						if values.probes.readiness.enabled {
							readinessProbe: {
								httpGet: {
									path: values.probes.readiness.path
									port: "http"
									scheme: "HTTP"
								}
								initialDelaySeconds: values.probes.readiness.initialDelaySeconds
								periodSeconds: values.probes.readiness.periodSeconds
								timeoutSeconds: values.probes.readiness.timeoutSeconds
								failureThreshold: values.probes.readiness.failureThreshold
								successThreshold: values.probes.readiness.successThreshold
							}
						}

						// Startup probe - ensures pod has time to start
						startupProbe: {
							httpGet: {
								path: values.probes.readiness.path
								port: "http"
								scheme: "HTTP"
							}
							initialDelaySeconds: 0
							periodSeconds: 10
							timeoutSeconds: 5
							failureThreshold: 30
							successThreshold: 1
						}

						// Lifecycle hooks
						lifecycle: {
							preStop: {
								exec: {
									command: ["/bin/sh", "-c", "sleep 15"]
								}
							}
						}

						// Volume mounts
						volumeMounts: [
							{
								name: "logs"
								mountPath: "/usr/local/tomcat/logs"
							},
							{
								name: "temp"
								mountPath: "/tmp"
							},
						]

						// Security context
						securityContext: {
							allowPrivilegeEscalation: values.securityContext.containerSecurityContext.allowPrivilegeEscalation
							readOnlyRootFilesystem: values.securityContext.containerSecurityContext.readOnlyRootFilesystem
							runAsNonRoot: values.securityContext.containerSecurityContext.runAsNonRoot
							runAsUser: values.securityContext.containerSecurityContext.runAsUser
							capabilities: {
								drop: values.securityContext.containerSecurityContext.capabilities.drop
							}
						}

						// Resource limits for container
						stdin: false
						tty: false
					},

					// Cloud SQL Proxy sidecar
					if values.cloudSQLProxy.enabled {
						{
							name: "cloud-sql-proxy"
							image: values.cloudSQLProxy.image.reference
							imagePullPolicy: values.cloudSQLProxy.image.pullPolicy

							command: ["/cloud-sql-proxy"]
							args: [
								for instance in values.cloudSQLProxy.instances {
									instance
								},
								if values.cloudSQLProxy.useHTTPHealthCheck {
									"--use-http-health-check"
								},
								if values.cloudSQLProxy.privateIP {
									"--private-ip"
								},
							]

							// Resources
							resources: {
								requests: {
									cpu: resource.#Quantity & values.cloudSQLProxy.resources.requests.cpu
									memory: resource.#Quantity & values.cloudSQLProxy.resources.requests.memory
								}
								limits: {
									cpu: resource.#Quantity & values.cloudSQLProxy.resources.limits.cpu
									memory: resource.#Quantity & values.cloudSQLProxy.resources.limits.memory
								}
							}

							// Security context
							securityContext: {
								runAsNonRoot: true
								runAsUser: values.cloudSQLProxy.securityContext.runAsUser
								allowPrivilegeEscalation: false
								readOnlyRootFilesystem: values.cloudSQLProxy.securityContext.readOnlyRootFilesystem
								capabilities: {
									drop: ["ALL"]
								}
							}
						}
					},
				]

				// Volumes
				volumes: [
					{
						name: "logs"
						emptyDir: {
							sizeLimit: resource.#Quantity & "500Mi"
						}
					},
					{
						name: "temp"
						emptyDir: {
							sizeLimit: resource.#Quantity & "1Gi"
						}
					},
				]
			}
		}
	}
}

// Output the deployment
deployment: #Deployment
