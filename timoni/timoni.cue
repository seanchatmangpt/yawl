// YAWL Timoni Bundle - Main entry point for Timoni

package main

import (
	"timoni.sh/v1alpha1"
)

bundle: {
	apiVersion: "v1alpha1"
	kind: "Bundle"
	metadata: {
		name: "yawl"
		version: "0.1.0"
		description: "YAWL Workflow Engine - Timoni Module"
	}

	spec: {
		sources: [
			{
				kind: "OCIRepository"
				name: "yawl"
				url: "oci://us-central1-docker.pkg.dev/my-gcp-project/timoni/yawl"
				tag: "0.1.0"
				pullSecret: "timoni-registry"
			},
		]

		targets: [
			{
				namespace: "yawl"
				name: "yawl-app"
				source: "yawl"
				values: {
					environment: "production"
					namespace: {
						name: "yawl"
						create: true
					}
				}
			},
		]

		intervals: {
			reconciliation: "1m"
			retryAttempts: 5
		}
	}
}
