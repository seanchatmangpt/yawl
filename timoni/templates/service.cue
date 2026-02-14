// YAWL Workflow Engine - Kubernetes Service Template

package main

import (
	corev1 "k8s.io/api/core/v1"
)

#Service: corev1.#Service & {
	apiVersion: "v1"
	kind: "Service"
	metadata: {
		name: "yawl"
		namespace: values.namespace.name
		labels: values.labels
		annotations: values.annotations & {
			"service.beta.kubernetes.io/aws-load-balancer-type": "nlb"
			"cloud.google.com/neg": "{\\"exposed_ports\\": {\\"80\\": \\"\\"}, \\"443\\": \\"\\"}"
		}
	}

	spec: {
		// Service type
		type: values.network.serviceType

		// Session affinity settings
		sessionAffinity: values.network.sessionAffinity
		if values.network.sessionAffinity == "ClientIP" {
			sessionAffinityConfig: {
				clientIP: {
					timeoutSeconds: values.network.sessionAffinityTimeout
				}
			}
		}

		// Selector for pods
		selector: {
			"app": "yawl"
			"app.kubernetes.io/name": "yawl"
		}

		// Ports
		ports: [
			{
				name: "http"
				port: values.network.ports.http.port
				targetPort: "http"
				protocol: values.network.ports.http.protocol
			},
			{
				name: "https"
				port: values.network.ports.https.port
				targetPort: "http"
				protocol: values.network.ports.https.protocol
			},
		]

		// Health check node port (for external load balancers)
		if values.network.serviceType == "LoadBalancer" {
			healthCheckNodePort: 30123
		}

		// IP families (IPv4 preferred, IPv6 supported)
		ipFamilies: ["IPv4"]
		ipFamilyPolicy: "SingleStack"

		// Publish not ready addresses (for session affinity)
		publishNotReadyAddresses: false

		// Traffic policy for external traffic
		externalTrafficPolicy: "Local"
	}
}

// Headless service for StatefulSets and DNS lookups (optional but useful)
#HeadlessService: corev1.#Service & {
	apiVersion: "v1"
	kind: "Service"
	metadata: {
		name: "yawl-headless"
		namespace: values.namespace.name
		labels: values.labels & {
			"service-type": "headless"
		}
		annotations: values.annotations
	}

	spec: {
		type: "ClusterIP"
		clusterIP: "None"

		selector: {
			"app": "yawl"
			"app.kubernetes.io/name": "yawl"
		}

		ports: [
			{
				name: "http"
				port: 8080
				targetPort: 8080
				protocol: "TCP"
			},
		]

		publishNotReadyAddresses: true
	}
}

// Output services
service: #Service
headlessService: #HeadlessService
