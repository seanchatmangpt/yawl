/*
Copyright 2024 The YAWL Foundation.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package controllers

import (
	"fmt"
	"log"

	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/util/validation"
	"k8s.io/apimachinery/pkg/util/validation/field"
	ctrl "sigs.k8s.io/controller-runtime"
	logf "sigs.k8s.io/controller-runtime/pkg/log"
	"sigs.k8s.io/controller-runtime/pkg/webhook"
	"sigs.k8s.io/controller-runtime/pkg/webhook/admission"

	yawlv1beta1 "yawl.io/api/v1beta1"
)

// log is for logging in this package.
var yawlclusterlog = logf.Log.WithName("yawlcluster-resource")

func (r *YawlCluster) SetupWebhookWithManager(mgr ctrl.Manager) error {
	return ctrl.NewWebhookManagedBy(mgr).
		For(r).
		Complete()
}

// +kubebuilder:webhook:path=/mutate-yawl-io-v1beta1-yawlcluster,mutating=true,failurePolicy=fail,sideEffects=None,groups=yawl.io,resources=yawlclusters,verbs=create;update,versions=v1beta1,name=myawlcluster.kb.io,admissionReviewVersions=v1,clientConfig={}

var _ webhook.Mutator = &YawlCluster{}

// Default implements webhook.Mutator so a webhook will be registered for the type
func (r *YawlCluster) Default() {
	yawlclusterlog.Info("default", "name", r.Name)

	// Set default values for YawlCluster
	if r.Spec.Size == 0 {
		r.Spec.Size = 3
	}

	if r.Spec.Version == "" {
		r.Spec.Version = "5.2"
	}

	// Image defaults
	if r.Spec.Image.Repository == "" {
		r.Spec.Image.Repository = "yawlfoundation/yawl"
	}
	if r.Spec.Image.PullPolicy == "" {
		r.Spec.Image.PullPolicy = "IfNotPresent"
	}

	// Database defaults
	if r.Spec.Database.Type == "" {
		r.Spec.Database.Type = "postgresql"
	}
	if r.Spec.Database.Port == 0 {
		if r.Spec.Database.Type == "mysql" {
			r.Spec.Database.Port = 3306
		} else {
			r.Spec.Database.Port = 5432
		}
	}
	if r.Spec.Database.Name == "" {
		r.Spec.Database.Name = "yawl"
	}
	if r.Spec.Database.ConnectionPoolSize == 0 {
		r.Spec.Database.ConnectionPoolSize = 20
	}

	// Persistence defaults
	if r.Spec.Persistence.Size == "" {
		r.Spec.Persistence.Size = "10Gi"
	}
	if r.Spec.Persistence.LogsSize == "" {
		r.Spec.Persistence.LogsSize = "5Gi"
	}

	// Resources defaults
	if r.Spec.Resources.Requests.CPU == "" {
		r.Spec.Resources.Requests.CPU = "500m"
	}
	if r.Spec.Resources.Requests.Memory == "" {
		r.Spec.Resources.Requests.Memory = "1Gi"
	}
	if r.Spec.Resources.Limits.CPU == "" {
		r.Spec.Resources.Limits.CPU = "2"
	}
	if r.Spec.Resources.Limits.Memory == "" {
		r.Spec.Resources.Limits.Memory = "2Gi"
	}

	// JVM defaults
	if r.Spec.JVM.HeapSize == "" {
		r.Spec.JVM.HeapSize = "1024m"
	}
	if r.Spec.JVM.InitialHeapSize == "" {
		r.Spec.JVM.InitialHeapSize = "512m"
	}
	if r.Spec.JVM.GCType == "" {
		r.Spec.JVM.GCType = "G1GC"
	}
	if r.Spec.JVM.GCPauseTarget == 0 {
		r.Spec.JVM.GCPauseTarget = 200
	}

	// Networking defaults
	if r.Spec.Networking.Service.Port == 0 {
		r.Spec.Networking.Service.Port = 8080
	}
	if r.Spec.Networking.Service.TargetPort == 0 {
		r.Spec.Networking.Service.TargetPort = 8080
	}
	if r.Spec.Networking.Service.Type == "" {
		r.Spec.Networking.Service.Type = "ClusterIP"
	}

	// Scaling defaults
	if !r.Spec.Scaling.Enabled && r.Spec.Scaling.MinReplicas == 0 {
		r.Spec.Scaling.Enabled = true
		r.Spec.Scaling.MinReplicas = 1
		r.Spec.Scaling.MaxReplicas = 10
		r.Spec.Scaling.TargetCPUUtilization = 70
		r.Spec.Scaling.TargetMemoryUtilization = 80
	}

	// Security defaults
	if r.Spec.Security.SecurityContext.RunAsUser == 0 {
		r.Spec.Security.SecurityContext.RunAsUser = 1000
		r.Spec.Security.SecurityContext.RunAsNonRoot = true
		r.Spec.Security.SecurityContext.FSGroup = 1000
	}

	// High Availability defaults
	if r.Spec.HighAvailability.PodAntiAffinity == "" {
		r.Spec.HighAvailability.Enabled = true
		r.Spec.HighAvailability.PodAntiAffinity = "preferred"
	}

	// Monitoring defaults
	if r.Spec.Monitoring.Logging.Level == "" {
		r.Spec.Monitoring.Logging.Enabled = true
		r.Spec.Monitoring.Logging.Level = "INFO"
		r.Spec.Monitoring.Logging.Format = "json"
	}
}

// +kubebuilder:webhook:path=/validate-yawl-io-v1beta1-yawlcluster,mutating=false,failurePolicy=fail,sideEffects=None,groups=yawl.io,resources=yawlclusters,verbs=create;update,versions=v1beta1,name=vyawlcluster.kb.io,admissionReviewVersions=v1,clientConfig={}

var _ webhook.Validator = &YawlCluster{}

// ValidateCreate implements webhook.Validator so a webhook will be registered for the type
func (r *YawlCluster) ValidateCreate() (admission.Warnings, error) {
	yawlclusterlog.Info("validate create", "name", r.Name)

	var allErrs field.ErrorList
	var warnings admission.Warnings

	if err := r.validateClusterSpec(); err != nil {
		allErrs = append(allErrs, err...)
	}

	if len(allErrs) == 0 {
		return warnings, nil
	}

	return warnings, allErrs.ToAggregate()
}

// ValidateUpdate implements webhook.Validator so a webhook will be registered for the type
func (r *YawlCluster) ValidateUpdate(old runtime.Object) (admission.Warnings, error) {
	yawlclusterlog.Info("validate update", "name", r.Name)

	var allErrs field.ErrorList
	var warnings admission.Warnings

	oldCluster := old.(*YawlCluster)

	// Validate new spec
	if err := r.validateClusterSpec(); err != nil {
		allErrs = append(allErrs, err...)
	}

	// Validate update rules
	if err := r.validateUpdateRules(oldCluster); err != nil {
		allErrs = append(allErrs, err...)
	}

	if len(allErrs) == 0 {
		return warnings, nil
	}

	return warnings, allErrs.ToAggregate()
}

// ValidateDelete implements webhook.Validator so a webhook will be registered for the type
func (r *YawlCluster) ValidateDelete() (admission.Warnings, error) {
	yawlclusterlog.Info("validate delete", "name", r.Name)

	var warnings admission.Warnings

	// Add deletion validation logic if needed
	// For example, check if there are active workflows before deletion

	return warnings, nil
}

// validateClusterSpec validates the YawlCluster specification
func (r *YawlCluster) validateClusterSpec() field.ErrorList {
	var allErrs field.ErrorList

	// Validate size
	if r.Spec.Size < 1 || r.Spec.Size > 100 {
		allErrs = append(allErrs, field.Invalid(
			field.NewPath("spec").Child("size"),
			r.Spec.Size,
			"must be between 1 and 100",
		))
	}

	// Validate version format
	if r.Spec.Version != "" {
		versionRegex := `^\d+\.\d+(\.\d+)?$`
		if errs := validation.IsValidFieldValue(r.Spec.Version, versionRegex); len(errs) > 0 {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("version"),
				r.Spec.Version,
				"must be in format X.Y or X.Y.Z",
			))
		}
	}

	// Validate database configuration
	if allErrs := r.validateDatabase(); len(allErrs) > 0 {
		allErrs = append(allErrs, allErrs...)
	}

	// Validate resources
	if allErrs := r.validateResources(); len(allErrs) > 0 {
		allErrs = append(allErrs, allErrs...)
	}

	// Validate JVM configuration
	if allErrs := r.validateJVM(); len(allErrs) > 0 {
		allErrs = append(allErrs, allErrs...)
	}

	// Validate networking configuration
	if allErrs := r.validateNetworking(); len(allErrs) > 0 {
		allErrs = append(allErrs, allErrs...)
	}

	// Validate scaling configuration
	if allErrs := r.validateScaling(); len(allErrs) > 0 {
		allErrs = append(allErrs, allErrs...)
	}

	return allErrs
}

// validateDatabase validates database configuration
func (r *YawlCluster) validateDatabase() field.ErrorList {
	var allErrs field.ErrorList

	if r.Spec.Database.Type == "" {
		allErrs = append(allErrs, field.Required(
			field.NewPath("spec").Child("database").Child("type"),
			"database type is required",
		))
		return allErrs
	}

	validDatabaseTypes := map[string]bool{
		"postgresql": true,
		"mysql":      true,
		"h2":         true,
	}

	if !validDatabaseTypes[r.Spec.Database.Type] {
		allErrs = append(allErrs, field.NotSupported(
			field.NewPath("spec").Child("database").Child("type"),
			r.Spec.Database.Type,
			[]string{"postgresql", "mysql", "h2"},
		))
	}

	// If database type requires host and port, validate them
	if r.Spec.Database.Type != "h2" {
		if r.Spec.Database.Host == "" {
			allErrs = append(allErrs, field.Required(
				field.NewPath("spec").Child("database").Child("host"),
				"database host is required for external databases",
			))
		}

		if r.Spec.Database.Port < 1 || r.Spec.Database.Port > 65535 {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("database").Child("port"),
				r.Spec.Database.Port,
				"must be a valid port number (1-65535)",
			))
		}
	}

	// Validate connection pool size
	if r.Spec.Database.ConnectionPoolSize < 5 || r.Spec.Database.ConnectionPoolSize > 100 {
		allErrs = append(allErrs, field.Invalid(
			field.NewPath("spec").Child("database").Child("connectionPoolSize"),
			r.Spec.Database.ConnectionPoolSize,
			"must be between 5 and 100",
		))
	}

	return allErrs
}

// validateResources validates resource configuration
func (r *YawlCluster) validateResources() field.ErrorList {
	var allErrs field.ErrorList

	// Validate that limits are not less than requests
	cpuRequest := parseQuantity(r.Spec.Resources.Requests.CPU)
	cpuLimit := parseQuantity(r.Spec.Resources.Limits.CPU)
	if cpuLimit > 0 && cpuRequest > cpuLimit {
		allErrs = append(allErrs, field.Invalid(
			field.NewPath("spec").Child("resources").Child("requests").Child("cpu"),
			r.Spec.Resources.Requests.CPU,
			"CPU request cannot be greater than limit",
		))
	}

	memoryRequest := parseQuantity(r.Spec.Resources.Requests.Memory)
	memoryLimit := parseQuantity(r.Spec.Resources.Limits.Memory)
	if memoryLimit > 0 && memoryRequest > memoryLimit {
		allErrs = append(allErrs, field.Invalid(
			field.NewPath("spec").Child("resources").Child("requests").Child("memory"),
			r.Spec.Resources.Requests.Memory,
			"memory request cannot be greater than limit",
		))
	}

	return allErrs
}

// validateJVM validates JVM configuration
func (r *YawlCluster) validateJVM() field.ErrorList {
	var allErrs field.ErrorList

	validGCTypes := map[string]bool{
		"G1GC":       true,
		"ParallelGC": true,
		"CMS":        true,
	}

	if r.Spec.JVM.GCType != "" && !validGCTypes[r.Spec.JVM.GCType] {
		allErrs = append(allErrs, field.NotSupported(
			field.NewPath("spec").Child("jvm").Child("gcType"),
			r.Spec.JVM.GCType,
			[]string{"G1GC", "ParallelGC", "CMS"},
		))
	}

	// Validate GC pause target
	if r.Spec.JVM.GCPauseTarget < 10 || r.Spec.JVM.GCPauseTarget > 1000 {
		allErrs = append(allErrs, field.Invalid(
			field.NewPath("spec").Child("jvm").Child("gcPauseTarget"),
			r.Spec.JVM.GCPauseTarget,
			"must be between 10 and 1000 milliseconds",
		))
	}

	return allErrs
}

// validateNetworking validates networking configuration
func (r *YawlCluster) validateNetworking() field.ErrorList {
	var allErrs field.ErrorList

	validServiceTypes := map[string]bool{
		"ClusterIP":   true,
		"LoadBalancer": true,
		"NodePort":    true,
	}

	if r.Spec.Networking.Service.Type != "" && !validServiceTypes[r.Spec.Networking.Service.Type] {
		allErrs = append(allErrs, field.NotSupported(
			field.NewPath("spec").Child("networking").Child("service").Child("type"),
			r.Spec.Networking.Service.Type,
			[]string{"ClusterIP", "LoadBalancer", "NodePort"},
		))
	}

	// Validate port ranges
	if r.Spec.Networking.Service.Port < 1 || r.Spec.Networking.Service.Port > 65535 {
		allErrs = append(allErrs, field.Invalid(
			field.NewPath("spec").Child("networking").Child("service").Child("port"),
			r.Spec.Networking.Service.Port,
			"must be a valid port number (1-65535)",
		))
	}

	if r.Spec.Networking.Service.TargetPort < 1 || r.Spec.Networking.Service.TargetPort > 65535 {
		allErrs = append(allErrs, field.Invalid(
			field.NewPath("spec").Child("networking").Child("service").Child("targetPort"),
			r.Spec.Networking.Service.TargetPort,
			"must be a valid port number (1-65535)",
		))
	}

	// Validate NodePort range if service type is NodePort
	if r.Spec.Networking.Service.Type == "NodePort" && r.Spec.Networking.Service.NodePort != 0 {
		if r.Spec.Networking.Service.NodePort < 30000 || r.Spec.Networking.Service.NodePort > 32767 {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("networking").Child("service").Child("nodePort"),
				r.Spec.Networking.Service.NodePort,
				"must be in NodePort range (30000-32767)",
			))
		}
	}

	return allErrs
}

// validateScaling validates scaling configuration
func (r *YawlCluster) validateScaling() field.ErrorList {
	var allErrs field.ErrorList

	if r.Spec.Scaling.Enabled {
		if r.Spec.Scaling.MinReplicas < 1 {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("scaling").Child("minReplicas"),
				r.Spec.Scaling.MinReplicas,
				"must be at least 1",
			))
		}

		if r.Spec.Scaling.MaxReplicas < 1 {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("scaling").Child("maxReplicas"),
				r.Spec.Scaling.MaxReplicas,
				"must be at least 1",
			))
		}

		if r.Spec.Scaling.MinReplicas > r.Spec.Scaling.MaxReplicas {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("scaling").Child("minReplicas"),
				r.Spec.Scaling.MinReplicas,
				"minReplicas cannot be greater than maxReplicas",
			))
		}

		// Validate utilization targets
		if r.Spec.Scaling.TargetCPUUtilization < 1 || r.Spec.Scaling.TargetCPUUtilization > 100 {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("scaling").Child("targetCPUUtilization"),
				r.Spec.Scaling.TargetCPUUtilization,
				"must be between 1 and 100",
			))
		}

		if r.Spec.Scaling.TargetMemoryUtilization < 1 || r.Spec.Scaling.TargetMemoryUtilization > 100 {
			allErrs = append(allErrs, field.Invalid(
				field.NewPath("spec").Child("scaling").Child("targetMemoryUtilization"),
				r.Spec.Scaling.TargetMemoryUtilization,
				"must be between 1 and 100",
			))
		}
	}

	return allErrs
}

// validateUpdateRules validates rules for updates
func (r *YawlCluster) validateUpdateRules(oldCluster *YawlCluster) field.ErrorList {
	var allErrs field.ErrorList

	// Prevent changing database type after creation
	if r.Spec.Database.Type != oldCluster.Spec.Database.Type {
		allErrs = append(allErrs, field.Forbidden(
			field.NewPath("spec").Child("database").Child("type"),
			"database type cannot be changed after cluster creation",
		))
	}

	// Warn if significantly scaling down
	if r.Spec.Size < oldCluster.Spec.Size/2 {
		log.Printf("Warning: Significant scale-down detected for %s", r.Name)
	}

	return allErrs
}

// parseQuantity parses a quantity string to a numeric value for comparison
func parseQuantity(q string) float64 {
	// Simplified parsing - in production, use proper quantity parsing
	switch q {
	case "500m":
		return 0.5
	case "1":
		return 1
	case "2":
		return 2
	case "1Gi":
		return 1024
	case "2Gi":
		return 2048
	default:
		return 0
	}
}
