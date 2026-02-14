// YAWL Workflow Engine - Timoni Module
// Main entry point that exports all Kubernetes resources

package main

import (
	corev1 "k8s.io/api/core/v1"
	policyv1 "k8s.io/api/policy/v1"
	rbacv1 "k8s.io/api/rbac/v1"
)

// Service Account configuration
#ServiceAccount: corev1.#ServiceAccount & {
	apiVersion: "v1"
	kind: "ServiceAccount"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}
}

// Role configuration for YAWL
#Role: rbacv1.#Role & {
	apiVersion: "rbac.authorization.k8s.io/v1"
	kind: "Role"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	rules: [
		{
			apiGroups: [""]
			resources: ["pods", "pods/log"]
			verbs: ["get", "list", "watch"]
		},
		{
			apiGroups: [""]
			resources: ["configmaps"]
			verbs: ["get", "list"]
		},
		{
			apiGroups: [""]
			resources: ["secrets"]
			verbs: ["get", "list"]
		},
		{
			apiGroups: ["batch"]
			resources: ["jobs", "cronjobs"]
			verbs: ["get", "list", "watch", "create", "update", "patch"]
		},
	]
}

// RoleBinding configuration
#RoleBinding: rbacv1.#RoleBinding & {
	apiVersion: "rbac.authorization.k8s.io/v1"
	kind: "RoleBinding"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	roleRef: {
		apiGroup: "rbac.authorization.k8s.io"
		kind: "Role"
		name: "yawl"
	}

	subjects: [
		{
			kind: "ServiceAccount"
			name: "yawl"
			namespace: values.namespace.name
		},
	]
}

// Namespace configuration
#Namespace: corev1.#Namespace & {
	apiVersion: "v1"
	kind: "Namespace"
	metadata: {
		name: values.namespace.name
		labels: values.namespace.labels
	}
}

// Pod Disruption Budget
#PodDisruptionBudget: policyv1.#PodDisruptionBudget & {
	apiVersion: "policy/v1"
	kind: "PodDisruptionBudget"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	spec: {
		minAvailable: values.scaling.podDisruptionBudget.minAvailable
		selector: {
			matchLabels: {
				"app": "yawl"
				"app.kubernetes.io/name": "yawl"
			}
		}
		unhealthyPodEvictionPolicy: "AlwaysAllow"
	}
}

// Network Policy for YAWL
#NetworkPolicy: {
	apiVersion: "networking.k8s.io/v1"
	kind: "NetworkPolicy"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	spec: {
		podSelector: {
			matchLabels: {
				"app": "yawl"
			}
		}
		policyTypes: ["Ingress", "Egress"]

		// Ingress rules
		ingress: [
			{
				from: [
					{
						podSelector: {
							matchLabels: {}
						}
					},
					{
						namespaceSelector: {
							matchLabels: {
								"name": values.namespace.name
							}
						}
					},
				]
				ports: [
					{
						protocol: "TCP"
						port: 8080
					},
				]
			},
		]

		// Egress rules - allow to DNS, databases, and external services
		egress: [
			{
				to: [
					{
						podSelector: {
							matchLabels: {}
						}
					},
					{
						namespaceSelector: {
							matchLabels: {
								"name": "kube-system"
							}
						}
					},
				]
				ports: [
					{
						protocol: "UDP"
						port: 53
					},
					{
						protocol: "TCP"
						port: 53
					},
				]
			},
			{
				to: [
					{
						podSelector: {}
					},
				]
				ports: [
					{
						protocol: "TCP"
						port: 5432
					},
				]
			},
			{
				to: [
					{
						namespaceSelector: {}
					},
				]
				ports: [
					{
						protocol: "TCP"
						port: 443
					},
					{
						protocol: "TCP"
						port: 80
					},
				]
			},
		]
	}
}

// HorizontalPodAutoscaler - for production environments
#HorizontalPodAutoscaler: {
	apiVersion: "autoscaling/v2"
	kind: "HorizontalPodAutoscaler"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	spec: {
		scaleTargetRef: {
			apiVersion: "apps/v1"
			kind: "Deployment"
			name: "yawl"
		}
		minReplicas: values.scaling.replicas
		maxReplicas: values.scaling.replicas * 3

		metrics: [
			{
				type: "Resource"
				resource: {
					name: "cpu"
					target: {
						type: "Utilization"
						averageUtilization: 70
					}
				}
			},
			{
				type: "Resource"
				resource: {
					name: "memory"
					target: {
						type: "Utilization"
						averageUtilization: 80
					}
				}
			},
		]

		behavior: {
			scaleDown: {
				stabilizationWindowSeconds: 300
				policies: [
					{
						type: "Percent"
						value: 50
						periodSeconds: 60
					},
				]
			}
			scaleUp: {
				stabilizationWindowSeconds: 0
				policies: [
					{
						type: "Percent"
						value: 100
						periodSeconds: 15
					},
					{
						type: "Pods"
						value: 2
						periodSeconds: 15
					},
				]
				selectPolicy: "Max"
			}
		}
	}
}

// ResourceQuota for namespace
#ResourceQuota: corev1.#ResourceQuota & {
	apiVersion: "v1"
	kind: "ResourceQuota"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations
	}

	spec: {
		hard: {
			"pods": "100"
			"requests.cpu": "100"
			"requests.memory": "200Gi"
			"limits.cpu": "200"
			"limits.memory": "400Gi"
			"persistentvolumeclaims": "10"
		}
	}
}

// Export all resources
objects: [
	if values.namespace.create {#Namespace},
	#ServiceAccount,
	#Role,
	#RoleBinding,
	deployment,
	service,
	headlessService,
	appConfigMap,
	loggingConfigMap,
	datasourceConfigMap,
	tomcatConfigMap,
	environmentConfigMap,
	appPropertiesConfigMap,
	if values.scaling.podDisruptionBudget.enabled {#PodDisruptionBudget},
	#NetworkPolicy,
	if values.environment == "production" {#HorizontalPodAutoscaler},
	#ResourceQuota,
]
