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

package v1beta1

import (
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

const (
	GroupName    = "yawl.io"
	GroupVersion = "v1beta1"
)

var (
	// SchemeGroupVersion is group version used to register these objects
	SchemeGroupVersion = schema.GroupVersion{Group: GroupName, Version: GroupVersion}

	// SchemeBuilder is used to add go types to the GroupVersionKind scheme
	SchemeBuilder = &scheme.Builder{GroupVersion: SchemeGroupVersion}

	// AddToScheme adds the types in this group-version to the given scheme.
	AddToScheme = SchemeBuilder.AddToScheme
)

// YawlClusterPhase represents the phase of a YawlCluster
type YawlClusterPhase string

const (
	PhasePending    YawlClusterPhase = "Pending"
	PhaseCreating   YawlClusterPhase = "Creating"
	PhaseRunning    YawlClusterPhase = "Running"
	PhaseUpdating   YawlClusterPhase = "Updating"
	PhaseDegraded   YawlClusterPhase = "Degraded"
	PhaseFailed     YawlClusterPhase = "Failed"
	PhaseTerminating YawlClusterPhase = "Terminating"
)

// YawlClusterSpec defines the desired state of YawlCluster
type YawlClusterSpec struct {
	// Size is the number of YAWL replicas
	// +kubebuilder:validation:Minimum=1
	// +kubebuilder:validation:Maximum=100
	// +kubebuilder:default=3
	Size int32 `json:"size,omitempty"`

	// Version is the YAWL version to deploy
	// +kubebuilder:default="5.2"
	Version string `json:"version,omitempty"`

	// Image defines the container image settings
	Image ImageSpec `json:"image,omitempty"`

	// Database defines database configuration
	Database DatabaseSpec `json:"database,omitempty"`

	// Persistence defines persistence configuration
	Persistence PersistenceSpec `json:"persistence,omitempty"`

	// Resources defines resource requests and limits
	Resources ResourcesSpec `json:"resources,omitempty"`

	// JVM defines JVM configuration
	JVM JVMSpec `json:"jvm,omitempty"`

	// Networking defines network configuration
	Networking NetworkingSpec `json:"networking,omitempty"`

	// Security defines security configuration
	Security SecuritySpec `json:"security,omitempty"`

	// HighAvailability defines HA configuration
	HighAvailability HighAvailabilitySpec `json:"highAvailability,omitempty"`

	// Monitoring defines monitoring configuration
	Monitoring MonitoringSpec `json:"monitoring,omitempty"`

	// Scaling defines autoscaling configuration
	Scaling ScalingSpec `json:"scaling,omitempty"`

	// Backup defines backup configuration
	Backup BackupSpec `json:"backup,omitempty"`
}

// ImageSpec defines container image settings
type ImageSpec struct {
	// Repository is the Docker image repository
	// +kubebuilder:default="yawlfoundation/yawl"
	Repository string `json:"repository,omitempty"`

	// Tag is the Docker image tag
	Tag string `json:"tag,omitempty"`

	// PullPolicy is the image pull policy
	// +kubebuilder:validation:Enum=Always;Never;IfNotPresent
	// +kubebuilder:default=IfNotPresent
	PullPolicy corev1.PullPolicy `json:"pullPolicy,omitempty"`

	// PullSecrets is a list of image pull secret names
	PullSecrets []string `json:"pullSecrets,omitempty"`
}

// DatabaseSpec defines database configuration
type DatabaseSpec struct {
	// Type is the database type
	// +kubebuilder:validation:Enum=postgresql;mysql;h2
	// +kubebuilder:default="postgresql"
	Type string `json:"type,omitempty"`

	// Host is the database host
	Host string `json:"host,omitempty"`

	// Port is the database port
	// +kubebuilder:validation:Minimum=1
	// +kubebuilder:validation:Maximum=65535
	Port int32 `json:"port,omitempty"`

	// Name is the database name
	// +kubebuilder:default="yawl"
	Name string `json:"name,omitempty"`

	// Username is the database username
	Username string `json:"username,omitempty"`

	// PasswordSecret is a reference to the secret containing the database password
	PasswordSecret SecretReference `json:"passwordSecret,omitempty"`

	// SSLEnabled indicates if SSL is enabled
	SSLEnabled bool `json:"sslEnabled,omitempty"`

	// ConnectionPoolSize is the database connection pool size
	// +kubebuilder:validation:Minimum=5
	// +kubebuilder:validation:Maximum=100
	// +kubebuilder:default=20
	ConnectionPoolSize int32 `json:"connectionPoolSize,omitempty"`
}

// SecretReference defines a reference to a Secret
type SecretReference struct {
	// Name is the name of the secret
	Name string `json:"name,omitempty"`

	// Key is the key in the secret
	Key string `json:"key,omitempty"`
}

// PersistenceSpec defines persistence configuration
type PersistenceSpec struct {
	// Enabled indicates if persistence is enabled
	// +kubebuilder:default=true
	Enabled bool `json:"enabled,omitempty"`

	// StorageClass is the storage class name
	StorageClass string `json:"storageClass,omitempty"`

	// Size is the PVC size
	// +kubebuilder:default="10Gi"
	Size string `json:"size,omitempty"`

	// LogsPersistence indicates if logs persistence is enabled
	LogsPersistence bool `json:"logsPersistence,omitempty"`

	// LogsSize is the logs PVC size
	// +kubebuilder:default="5Gi"
	LogsSize string `json:"logsSize,omitempty"`
}

// ResourcesSpec defines resource requests and limits
type ResourcesSpec struct {
	// Requests defines resource requests
	Requests ResourceQuantity `json:"requests,omitempty"`

	// Limits defines resource limits
	Limits ResourceQuantity `json:"limits,omitempty"`
}

// ResourceQuantity defines CPU and memory quantities
type ResourceQuantity struct {
	// CPU is the CPU request/limit
	// +kubebuilder:default="500m"
	CPU string `json:"cpu,omitempty"`

	// Memory is the memory request/limit
	// +kubebuilder:default="1Gi"
	Memory string `json:"memory,omitempty"`
}

// JVMSpec defines JVM configuration
type JVMSpec struct {
	// HeapSize is the JVM heap size
	// +kubebuilder:default="1024m"
	HeapSize string `json:"heapSize,omitempty"`

	// InitialHeapSize is the initial heap size
	// +kubebuilder:default="512m"
	InitialHeapSize string `json:"initialHeapSize,omitempty"`

	// GCType is the garbage collector type
	// +kubebuilder:validation:Enum=G1GC;ParallelGC;CMS
	// +kubebuilder:default="G1GC"
	GCType string `json:"gcType,omitempty"`

	// GCPauseTarget is the max GC pause time in milliseconds
	// +kubebuilder:default=200
	GCPauseTarget int32 `json:"gcPauseTarget,omitempty"`

	// AdditionalOptions are additional JVM options
	AdditionalOptions string `json:"additionalOptions,omitempty"`
}

// NetworkingSpec defines network configuration
type NetworkingSpec struct {
	// Service defines service configuration
	Service ServiceSpec `json:"service,omitempty"`

	// Ingress defines ingress configuration
	Ingress IngressSpec `json:"ingress,omitempty"`

	// NetworkPolicy defines network policy configuration
	NetworkPolicy NetworkPolicySpec `json:"networkPolicy,omitempty"`
}

// ServiceSpec defines service configuration
type ServiceSpec struct {
	// Type is the service type
	// +kubebuilder:validation:Enum=ClusterIP;LoadBalancer;NodePort
	// +kubebuilder:default="ClusterIP"
	Type string `json:"type,omitempty"`

	// Port is the service port
	// +kubebuilder:default=8080
	Port int32 `json:"port,omitempty"`

	// TargetPort is the target port
	// +kubebuilder:default=8080
	TargetPort int32 `json:"targetPort,omitempty"`

	// NodePort is the node port (for NodePort services)
	// +kubebuilder:validation:Minimum=30000
	// +kubebuilder:validation:Maximum=32767
	NodePort int32 `json:"nodePort,omitempty"`

	// Annotations are service annotations
	Annotations map[string]string `json:"annotations,omitempty"`
}

// IngressSpec defines ingress configuration
type IngressSpec struct {
	// Enabled indicates if ingress is enabled
	Enabled bool `json:"enabled,omitempty"`

	// ClassName is the ingress class name
	ClassName string `json:"className,omitempty"`

	// Hosts defines ingress hosts
	Hosts []IngressHost `json:"hosts,omitempty"`

	// TLS defines TLS configuration
	TLS []IngressTLS `json:"tls,omitempty"`
}

// IngressHost defines an ingress host
type IngressHost struct {
	// Host is the hostname
	Host string `json:"host,omitempty"`

	// Paths are the URL paths
	Paths []IngressPath `json:"paths,omitempty"`
}

// IngressPath defines an ingress path
type IngressPath struct {
	// Path is the URL path
	Path string `json:"path,omitempty"`

	// PathType is the path type
	PathType string `json:"pathType,omitempty"`
}

// IngressTLS defines ingress TLS configuration
type IngressTLS struct {
	// SecretName is the secret name containing the TLS certificate
	SecretName string `json:"secretName,omitempty"`

	// Hosts are the hosts covered by this TLS configuration
	Hosts []string `json:"hosts,omitempty"`
}

// NetworkPolicySpec defines network policy configuration
type NetworkPolicySpec struct {
	// Enabled indicates if network policy is enabled
	// +kubebuilder:default=true
	Enabled bool `json:"enabled,omitempty"`

	// Ingress defines ingress rules
	Ingress []interface{} `json:"ingress,omitempty"`

	// Egress defines egress rules
	Egress []interface{} `json:"egress,omitempty"`
}

// SecuritySpec defines security configuration
type SecuritySpec struct {
	// TLS defines TLS configuration
	TLS TLSSpec `json:"tls,omitempty"`

	// RBAC defines RBAC configuration
	RBAC RBACSpec `json:"rbac,omitempty"`

	// SecurityContext defines security context
	SecurityContext SecurityContextSpec `json:"securityContext,omitempty"`
}

// TLSSpec defines TLS configuration
type TLSSpec struct {
	// Enabled indicates if TLS is enabled
	Enabled bool `json:"enabled,omitempty"`

	// CertSecret is the certificate secret name
	CertSecret string `json:"certSecret,omitempty"`

	// KeySecret is the key secret name
	KeySecret string `json:"keySecret,omitempty"`
}

// RBACSpec defines RBAC configuration
type RBACSpec struct {
	// Enabled indicates if RBAC is enabled
	// +kubebuilder:default=true
	Enabled bool `json:"enabled,omitempty"`

	// RoleArn is the AWS IAM role ARN (for IRSA)
	RoleArn string `json:"roleArn,omitempty"`
}

// SecurityContextSpec defines security context
type SecurityContextSpec struct {
	// RunAsNonRoot indicates if the container must run as non-root
	// +kubebuilder:default=true
	RunAsNonRoot bool `json:"runAsNonRoot,omitempty"`

	// RunAsUser is the user ID
	// +kubebuilder:default=1000
	RunAsUser int64 `json:"runAsUser,omitempty"`

	// FSGroup is the file system group
	// +kubebuilder:default=1000
	FSGroup int64 `json:"fsGroup,omitempty"`

	// AllowPrivilegeEscalation indicates if privilege escalation is allowed
	// +kubebuilder:default=false
	AllowPrivilegeEscalation bool `json:"allowPrivilegeEscalation,omitempty"`
}

// HighAvailabilitySpec defines high availability configuration
type HighAvailabilitySpec struct {
	// Enabled indicates if HA is enabled
	// +kubebuilder:default=true
	Enabled bool `json:"enabled,omitempty"`

	// PodAntiAffinity is the pod anti-affinity type
	// +kubebuilder:validation:Enum=required;preferred
	// +kubebuilder:default="preferred"
	PodAntiAffinity string `json:"podAntiAffinity,omitempty"`

	// TopologySpreadConstraints defines topology spread constraints
	TopologySpreadConstraints []interface{} `json:"topologySpreadConstraints,omitempty"`

	// Affinity defines affinity rules
	Affinity *corev1.Affinity `json:"affinity,omitempty"`

	// Tolerations defines toleration rules
	Tolerations []corev1.Toleration `json:"tolerations,omitempty"`
}

// MonitoringSpec defines monitoring configuration
type MonitoringSpec struct {
	// Prometheus defines Prometheus monitoring
	Prometheus PrometheusSpec `json:"prometheus,omitempty"`

	// Logging defines logging configuration
	Logging LoggingSpec `json:"logging,omitempty"`

	// Jaeger defines Jaeger tracing
	JaegerSpec JaegerSpec `json:"jaeger,omitempty"`
}

// PrometheusSpec defines Prometheus monitoring
type PrometheusSpec struct {
	// Enabled indicates if Prometheus monitoring is enabled
	Enabled bool `json:"enabled,omitempty"`

	// Port is the Prometheus metrics port
	// +kubebuilder:default=9090
	Port int32 `json:"port,omitempty"`

	// Interval is the scrape interval
	// +kubebuilder:default="30s"
	Interval string `json:"interval,omitempty"`

	// Path is the metrics endpoint path
	// +kubebuilder:default="/metrics"
	Path string `json:"path,omitempty"`
}

// LoggingSpec defines logging configuration
type LoggingSpec struct {
	// Enabled indicates if logging is enabled
	// +kubebuilder:default=true
	Enabled bool `json:"enabled,omitempty"`

	// Level is the logging level
	// +kubebuilder:validation:Enum=DEBUG;INFO;WARN;ERROR
	// +kubebuilder:default="INFO"
	Level string `json:"level,omitempty"`

	// Format is the log format
	// +kubebuilder:validation:Enum=text;json
	// +kubebuilder:default="json"
	Format string `json:"format,omitempty"`
}

// JaegerSpec defines Jaeger tracing
type JaegerSpec struct {
	// Enabled indicates if Jaeger tracing is enabled
	Enabled bool `json:"enabled,omitempty"`

	// Endpoint is the Jaeger endpoint
	Endpoint string `json:"endpoint,omitempty"`
}

// ScalingSpec defines autoscaling configuration
type ScalingSpec struct {
	// Enabled indicates if autoscaling is enabled
	// +kubebuilder:default=true
	Enabled bool `json:"enabled,omitempty"`

	// MinReplicas is the minimum number of replicas
	// +kubebuilder:validation:Minimum=1
	// +kubebuilder:default=1
	MinReplicas int32 `json:"minReplicas,omitempty"`

	// MaxReplicas is the maximum number of replicas
	// +kubebuilder:validation:Minimum=1
	// +kubebuilder:default=10
	MaxReplicas int32 `json:"maxReplicas,omitempty"`

	// TargetCPUUtilization is the target CPU utilization percentage
	// +kubebuilder:validation:Minimum=1
	// +kubebuilder:validation:Maximum=100
	// +kubebuilder:default=70
	TargetCPUUtilization int32 `json:"targetCPUUtilization,omitempty"`

	// TargetMemoryUtilization is the target memory utilization percentage
	// +kubebuilder:validation:Minimum=1
	// +kubebuilder:validation:Maximum=100
	// +kubebuilder:default=80
	TargetMemoryUtilization int32 `json:"targetMemoryUtilization,omitempty"`
}

// BackupSpec defines backup configuration
type BackupSpec struct {
	// Enabled indicates if backups are enabled
	Enabled bool `json:"enabled,omitempty"`

	// Schedule is the cron schedule for backups
	Schedule string `json:"schedule,omitempty"`

	// Retention is the number of backups to retain
	// +kubebuilder:default=7
	Retention int32 `json:"retention,omitempty"`

	// Destination is the backup destination
	Destination string `json:"destination,omitempty"`
}

// YawlClusterStatus defines the observed state of YawlCluster
type YawlClusterStatus struct {
	// Phase is the current phase of the cluster
	Phase YawlClusterPhase `json:"phase,omitempty"`

	// Conditions represent the latest available observations of the YawlCluster state
	Conditions []metav1.Condition `json:"conditions,omitempty"`

	// Replicas is the current number of replicas
	Replicas int32 `json:"replicas,omitempty"`

	// ReadyReplicas is the number of ready replicas
	ReadyReplicas int32 `json:"readyReplicas,omitempty"`

	// UpdatedReplicas is the number of updated replicas
	UpdatedReplicas int32 `json:"updatedReplicas,omitempty"`

	// ObservedGeneration reflects the generation of the most recently observed YawlCluster
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`

	// LastUpdateTime is the last time the cluster was updated
	LastUpdateTime *metav1.Time `json:"lastUpdateTime,omitempty"`

	// ClusterVersion is the currently running cluster version
	ClusterVersion string `json:"clusterVersion,omitempty"`

	// Endpoint is the cluster access endpoint
	Endpoint string `json:"endpoint,omitempty"`

	// DatabaseStatus is the database connectivity status
	DatabaseStatus string `json:"databaseStatus,omitempty"`

	// Errors is a list of error messages
	Errors []string `json:"errors,omitempty"`
}

//+kubebuilder:object:root=true
//+kubebuilder:subresource:status
//+kubebuilder:resource:shortName=yc;path=yawlclusters

// YawlCluster is the Schema for the yawlclusters API
type YawlCluster struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec   YawlClusterSpec   `json:"spec,omitempty"`
	Status YawlClusterStatus `json:"status,omitempty"`
}

//+kubebuilder:object:root=true

// YawlClusterList contains a list of YawlCluster
type YawlClusterList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []YawlCluster `json:"items"`
}

func init() {
	SchemeBuilder.Register(&YawlCluster{}, &YawlClusterList{})
}
