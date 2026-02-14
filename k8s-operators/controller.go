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
	"context"
	"fmt"
	"time"

	"github.com/go-logr/logr"
	appsv1 "k8s.io/api/apps/v1"
	autoscalingv2 "k8s.io/api/autoscaling/v2"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/api/meta"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/builder"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"sigs.k8s.io/controller-runtime/pkg/predicate"

	yawlv1beta1 "yawl.io/api/v1beta1"
)

const (
	yawlClusterFinalizerName = "yawlcluster.yawl.io/finalizer"
	yawlContainerName        = "yawl"
	yawlServiceAccountName   = "yawl"
	ownerKey                 = ".metadata.controller"
	apiGVStr                 = yawlv1beta1.GroupVersion.String()
)

// YawlClusterReconciler reconciles a YawlCluster object
type YawlClusterReconciler struct {
	client.Client
	Log      logr.Logger
	Scheme   *runtime.Scheme
	Recorder record.EventRecorder
}

//+kubebuilder:rbac:groups=yawl.io,resources=yawlclusters,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=yawl.io,resources=yawlclusters/status,verbs=get;update;patch
//+kubebuilder:rbac:groups=yawl.io,resources=yawlclusters/finalizers,verbs=update
//+kubebuilder:rbac:groups=apps,resources=deployments,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=core,resources=services,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=core,resources=configmaps,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=core,resources=secrets,verbs=get;list;watch
//+kubebuilder:rbac:groups=core,resources=persistentvolumeclaims,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=networking.k8s.io,resources=ingresses,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=networking.k8s.io,resources=networkpolicies,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=autoscaling,resources=horizontalpodautoscalers,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=core,resources=events,verbs=create;patch

// Reconcile implements the reconciliation loop
func (r *YawlClusterReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	log := r.Log.WithValues("yawlcluster", req.NamespacedName)

	// Fetch YawlCluster resource
	yawlCluster := &yawlv1beta1.YawlCluster{}
	if err := r.Get(ctx, req.NamespacedName, yawlCluster); err != nil {
		if apierrors.IsNotFound(err) {
			log.Info("YawlCluster resource not found, ignoring since object must be deleted")
			return ctrl.Result{}, nil
		}
		log.Error(err, "Failed to get YawlCluster")
		return ctrl.Result{}, err
	}

	log = log.WithValues("generation", yawlCluster.Generation)
	log.Info("Reconciling YawlCluster")

	// Handle deletion
	if yawlCluster.ObjectMeta.DeletionTimestamp != nil {
		return r.handleDeletion(ctx, log, yawlCluster)
	}

	// Add finalizer if not present
	if !controllerutil.ContainsFinalizer(yawlCluster, yawlClusterFinalizerName) {
		controllerutil.AddFinalizer(yawlCluster, yawlClusterFinalizerName)
		if err := r.Update(ctx, yawlCluster); err != nil {
			return ctrl.Result{}, err
		}
	}

	// Set initial status phase
	if yawlCluster.Status.Phase == "" {
		yawlCluster.Status.Phase = yawlv1beta1.PhasePending
		if err := r.Status().Update(ctx, yawlCluster); err != nil {
			log.Error(err, "Failed to set initial status phase")
			return ctrl.Result{}, err
		}
	}

	// Update status to Creating
	yawlCluster.Status.Phase = yawlv1beta1.PhaseCreating
	yawlCluster.Status.ObservedGeneration = yawlCluster.Generation
	if err := r.Status().Update(ctx, yawlCluster); err != nil {
		log.Error(err, "Failed to update status to Creating")
		return ctrl.Result{}, err
	}

	// Validate YawlCluster spec
	if err := r.validateClusterSpec(yawlCluster); err != nil {
		r.Recorder.Event(yawlCluster, corev1.EventTypeWarning, "InvalidSpec", err.Error())
		yawlCluster.Status.Phase = yawlv1beta1.PhaseFailed
		yawlCluster.Status.Errors = append(yawlCluster.Status.Errors, err.Error())
		if err := r.Status().Update(ctx, yawlCluster); err != nil {
			log.Error(err, "Failed to update status")
		}
		return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
	}

	// Reconcile Namespace
	if err := r.reconcileNamespace(ctx, log, yawlCluster); err != nil {
		return ctrl.Result{}, err
	}

	// Reconcile ServiceAccount and RBAC
	if err := r.reconcileServiceAccount(ctx, log, yawlCluster); err != nil {
		return ctrl.Result{}, err
	}

	// Reconcile ConfigMap
	if err := r.reconcileConfigMap(ctx, log, yawlCluster); err != nil {
		return ctrl.Result{}, err
	}

	// Reconcile PVC if persistence is enabled
	if yawlCluster.Spec.Persistence.Enabled {
		if err := r.reconcilePVC(ctx, log, yawlCluster); err != nil {
			return ctrl.Result{}, err
		}
	}

	// Reconcile Deployment
	deployment := &appsv1.Deployment{}
	if err := r.reconcileDeployment(ctx, log, yawlCluster, deployment); err != nil {
		return ctrl.Result{}, err
	}

	// Reconcile Service
	if err := r.reconcileService(ctx, log, yawlCluster); err != nil {
		return ctrl.Result{}, err
	}

	// Reconcile Ingress if enabled
	if yawlCluster.Spec.Networking.Ingress.Enabled {
		if err := r.reconcileIngress(ctx, log, yawlCluster); err != nil {
			return ctrl.Result{}, err
		}
	}

	// Reconcile NetworkPolicy if enabled
	if yawlCluster.Spec.Networking.NetworkPolicy.Enabled {
		if err := r.reconcileNetworkPolicy(ctx, log, yawlCluster); err != nil {
			return ctrl.Result{}, err
		}
	}

	// Reconcile HPA if autoscaling is enabled
	if yawlCluster.Spec.Scaling.Enabled {
		if err := r.reconcileHPA(ctx, log, yawlCluster); err != nil {
			return ctrl.Result{}, err
		}
	}

	// Update status
	yawlCluster.Status.Phase = yawlv1beta1.PhaseRunning
	yawlCluster.Status.Replicas = deployment.Status.Replicas
	yawlCluster.Status.ReadyReplicas = deployment.Status.ReadyReplicas
	yawlCluster.Status.UpdatedReplicas = deployment.Status.UpdatedReplicas
	yawlCluster.Status.ClusterVersion = yawlCluster.Spec.Version
	yawlCluster.Status.LastUpdateTime = &metav1.Time{Time: time.Now()}
	yawlCluster.Status.Endpoint = fmt.Sprintf("%s.%s.svc.cluster.local:%d",
		yawlCluster.Name, yawlCluster.Namespace, yawlCluster.Spec.Networking.Service.Port)

	// Set Ready condition
	meta.SetStatusCondition(&yawlCluster.Status.Conditions, metav1.Condition{
		Type:               "Ready",
		Status:             metav1.ConditionTrue,
		ObservedGeneration: yawlCluster.Generation,
		Reason:             "ClusterReady",
		Message:            "YawlCluster is ready",
	})

	if err := r.Status().Update(ctx, yawlCluster); err != nil {
		log.Error(err, "Failed to update final status")
		return ctrl.Result{}, err
	}

	r.Recorder.Event(yawlCluster, corev1.EventTypeNormal, "Synced", "YawlCluster synced successfully")

	// Requeue after 30 seconds for continuous reconciliation
	return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
}

// handleDeletion handles cleanup when YawlCluster is deleted
func (r *YawlClusterReconciler) handleDeletion(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) (ctrl.Result, error) {
	if controllerutil.ContainsFinalizer(yawlCluster, yawlClusterFinalizerName) {
		log.Info("Deleting YawlCluster resources")
		yawlCluster.Status.Phase = yawlv1beta1.PhaseTerminating
		if err := r.Status().Update(ctx, yawlCluster); err != nil {
			log.Error(err, "Failed to update status to Terminating")
		}

		// Perform cleanup operations
		if err := r.deleteClusterResources(ctx, log, yawlCluster); err != nil {
			log.Error(err, "Failed to delete cluster resources")
			return ctrl.Result{}, err
		}

		controllerutil.RemoveFinalizer(yawlCluster, yawlClusterFinalizerName)
		if err := r.Update(ctx, yawlCluster); err != nil {
			log.Error(err, "Failed to remove finalizer")
			return ctrl.Result{}, err
		}
		r.Recorder.Event(yawlCluster, corev1.EventTypeNormal, "Deleted", "YawlCluster deleted successfully")
	}
	return ctrl.Result{}, nil
}

// validateClusterSpec validates the YawlCluster specification
func (r *YawlClusterReconciler) validateClusterSpec(yawlCluster *yawlv1beta1.YawlCluster) error {
	if yawlCluster.Spec.Size < 1 || yawlCluster.Spec.Size > 100 {
		return fmt.Errorf("invalid size: must be between 1 and 100")
	}

	if yawlCluster.Spec.Database.Type == "" {
		return fmt.Errorf("database type is required")
	}

	if yawlCluster.Spec.Scaling.Enabled {
		if yawlCluster.Spec.Scaling.MinReplicas > yawlCluster.Spec.Scaling.MaxReplicas {
			return fmt.Errorf("minReplicas cannot be greater than maxReplicas")
		}
	}

	return nil
}

// reconcileNamespace ensures the namespace exists
func (r *YawlClusterReconciler) reconcileNamespace(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	ns := &corev1.Namespace{}
	nsName := types.NamespacedName{Name: yawlCluster.Namespace}

	err := r.Get(ctx, nsName, ns)
	if err == nil {
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	ns = &corev1.Namespace{
		ObjectMeta: metav1.ObjectMeta{
			Name: yawlCluster.Namespace,
			Labels: map[string]string{
				"app":     "yawl",
				"managed": "operator",
			},
		},
	}

	if err := r.Create(ctx, ns); err != nil && !apierrors.IsAlreadyExists(err) {
		log.Error(err, "Failed to create namespace")
		return err
	}

	log.Info("Namespace created/verified", "namespace", yawlCluster.Namespace)
	return nil
}

// reconcileServiceAccount creates the ServiceAccount for YAWL
func (r *YawlClusterReconciler) reconcileServiceAccount(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	sa := &corev1.ServiceAccount{}
	saName := types.NamespacedName{Name: yawlServiceAccountName, Namespace: yawlCluster.Namespace}

	err := r.Get(ctx, saName, sa)
	if err == nil {
		log.Info("ServiceAccount already exists")
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	sa = &corev1.ServiceAccount{
		ObjectMeta: metav1.ObjectMeta{
			Name:      yawlServiceAccountName,
			Namespace: yawlCluster.Namespace,
			Labels:    r.constructLabels(yawlCluster),
		},
	}

	if err := controllerutil.SetControllerReference(yawlCluster, sa, r.Scheme); err != nil {
		return err
	}

	if err := r.Create(ctx, sa); err != nil {
		log.Error(err, "Failed to create ServiceAccount")
		return err
	}

	log.Info("ServiceAccount created", "serviceAccount", yawlServiceAccountName)
	return nil
}

// reconcileConfigMap creates/updates ConfigMap with YAWL configuration
func (r *YawlClusterReconciler) reconcileConfigMap(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	cm := &corev1.ConfigMap{}
	cmName := types.NamespacedName{Name: fmt.Sprintf("%s-config", yawlCluster.Name), Namespace: yawlCluster.Namespace}

	existingCM := &corev1.ConfigMap{}
	err := r.Get(ctx, cmName, existingCM)
	if err == nil {
		log.Info("ConfigMap already exists")
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	cm = &corev1.ConfigMap{
		ObjectMeta: metav1.ObjectMeta{
			Name:      cmName.Name,
			Namespace: cmName.Namespace,
			Labels:    r.constructLabels(yawlCluster),
		},
		Data: map[string]string{
			"database.type":     yawlCluster.Spec.Database.Type,
			"database.host":     yawlCluster.Spec.Database.Host,
			"database.port":     fmt.Sprintf("%d", yawlCluster.Spec.Database.Port),
			"database.name":     yawlCluster.Spec.Database.Name,
			"database.username": yawlCluster.Spec.Database.Username,
			"jvm.heap.size":     yawlCluster.Spec.JVM.HeapSize,
			"jvm.gc.type":       yawlCluster.Spec.JVM.GCType,
		},
	}

	if err := controllerutil.SetControllerReference(yawlCluster, cm, r.Scheme); err != nil {
		return err
	}

	if err := r.Create(ctx, cm); err != nil {
		log.Error(err, "Failed to create ConfigMap")
		return err
	}

	log.Info("ConfigMap created", "configmap", cmName.Name)
	return nil
}

// reconcilePVC creates PersistentVolumeClaim for data and logs
func (r *YawlClusterReconciler) reconcilePVC(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	// Data PVC
	dataPVC := &corev1.PersistentVolumeClaim{}
	dataPVCName := types.NamespacedName{Name: fmt.Sprintf("%s-data", yawlCluster.Name), Namespace: yawlCluster.Namespace}

	err := r.Get(ctx, dataPVCName, dataPVC)
	if err == nil {
		log.Info("Data PVC already exists")
	} else if apierrors.IsNotFound(err) {
		dataPVC = r.constructPVC(yawlCluster, dataPVCName.Name, yawlCluster.Spec.Persistence.Size)
		if err := controllerutil.SetControllerReference(yawlCluster, dataPVC, r.Scheme); err != nil {
			return err
		}
		if err := r.Create(ctx, dataPVC); err != nil {
			log.Error(err, "Failed to create data PVC")
			return err
		}
		log.Info("Data PVC created", "pvc", dataPVCName.Name)
	} else {
		return err
	}

	// Logs PVC
	if yawlCluster.Spec.Persistence.LogsPersistence {
		logsPVC := &corev1.PersistentVolumeClaim{}
		logsPVCName := types.NamespacedName{Name: fmt.Sprintf("%s-logs", yawlCluster.Name), Namespace: yawlCluster.Namespace}

		err := r.Get(ctx, logsPVCName, logsPVC)
		if err == nil {
			log.Info("Logs PVC already exists")
		} else if apierrors.IsNotFound(err) {
			logsPVC = r.constructPVC(yawlCluster, logsPVCName.Name, yawlCluster.Spec.Persistence.LogsSize)
			if err := controllerutil.SetControllerReference(yawlCluster, logsPVC, r.Scheme); err != nil {
				return err
			}
			if err := r.Create(ctx, logsPVC); err != nil {
				log.Error(err, "Failed to create logs PVC")
				return err
			}
			log.Info("Logs PVC created", "pvc", logsPVCName.Name)
		} else {
			return err
		}
	}

	return nil
}

// reconcileDeployment creates/updates the Deployment for YAWL
func (r *YawlClusterReconciler) reconcileDeployment(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster, deployment *appsv1.Deployment) error {
	deploymentName := types.NamespacedName{Name: yawlCluster.Name, Namespace: yawlCluster.Namespace}

	err := r.Get(ctx, deploymentName, deployment)
	if err == nil {
		log.Info("Deployment already exists, updating if needed")
		updated := r.updateDeploymentIfNeeded(yawlCluster, deployment)
		if updated {
			if err := r.Update(ctx, deployment); err != nil {
				log.Error(err, "Failed to update Deployment")
				return err
			}
			log.Info("Deployment updated")
		}
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	deployment = r.constructDeployment(yawlCluster)
	if err := controllerutil.SetControllerReference(yawlCluster, deployment, r.Scheme); err != nil {
		return err
	}

	if err := r.Create(ctx, deployment); err != nil {
		log.Error(err, "Failed to create Deployment")
		return err
	}

	log.Info("Deployment created", "deployment", deploymentName.Name)
	return nil
}

// reconcileService creates/updates the Service for YAWL
func (r *YawlClusterReconciler) reconcileService(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	service := &corev1.Service{}
	serviceName := types.NamespacedName{Name: yawlCluster.Name, Namespace: yawlCluster.Namespace}

	err := r.Get(ctx, serviceName, service)
	if err == nil {
		log.Info("Service already exists")
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	service = r.constructService(yawlCluster)
	if err := controllerutil.SetControllerReference(yawlCluster, service, r.Scheme); err != nil {
		return err
	}

	if err := r.Create(ctx, service); err != nil {
		log.Error(err, "Failed to create Service")
		return err
	}

	log.Info("Service created", "service", serviceName.Name)
	return nil
}

// reconcileIngress creates/updates the Ingress for YAWL
func (r *YawlClusterReconciler) reconcileIngress(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	ingress := &networkingv1.Ingress{}
	ingressName := types.NamespacedName{Name: fmt.Sprintf("%s-ingress", yawlCluster.Name), Namespace: yawlCluster.Namespace}

	err := r.Get(ctx, ingressName, ingress)
	if err == nil {
		log.Info("Ingress already exists")
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	ingress = r.constructIngress(yawlCluster)
	if err := controllerutil.SetControllerReference(yawlCluster, ingress, r.Scheme); err != nil {
		return err
	}

	if err := r.Create(ctx, ingress); err != nil {
		log.Error(err, "Failed to create Ingress")
		return err
	}

	log.Info("Ingress created", "ingress", ingressName.Name)
	return nil
}

// reconcileNetworkPolicy creates/updates the NetworkPolicy for YAWL
func (r *YawlClusterReconciler) reconcileNetworkPolicy(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	networkPolicy := &networkingv1.NetworkPolicy{}
	policyName := types.NamespacedName{Name: fmt.Sprintf("%s-policy", yawlCluster.Name), Namespace: yawlCluster.Namespace}

	err := r.Get(ctx, policyName, networkPolicy)
	if err == nil {
		log.Info("NetworkPolicy already exists")
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	networkPolicy = r.constructNetworkPolicy(yawlCluster)
	if err := controllerutil.SetControllerReference(yawlCluster, networkPolicy, r.Scheme); err != nil {
		return err
	}

	if err := r.Create(ctx, networkPolicy); err != nil {
		log.Error(err, "Failed to create NetworkPolicy")
		return err
	}

	log.Info("NetworkPolicy created", "networkPolicy", policyName.Name)
	return nil
}

// reconcileHPA creates/updates the HorizontalPodAutoscaler for YAWL
func (r *YawlClusterReconciler) reconcileHPA(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	hpa := &autoscalingv2.HorizontalPodAutoscaler{}
	hpaName := types.NamespacedName{Name: fmt.Sprintf("%s-hpa", yawlCluster.Name), Namespace: yawlCluster.Namespace}

	err := r.Get(ctx, hpaName, hpa)
	if err == nil {
		log.Info("HPA already exists")
		return nil
	}

	if !apierrors.IsNotFound(err) {
		return err
	}

	hpa = r.constructHPA(yawlCluster)
	if err := controllerutil.SetControllerReference(yawlCluster, hpa, r.Scheme); err != nil {
		return err
	}

	if err := r.Create(ctx, hpa); err != nil {
		log.Error(err, "Failed to create HPA")
		return err
	}

	log.Info("HPA created", "hpa", hpaName.Name)
	return nil
}

// deleteClusterResources performs cleanup operations
func (r *YawlClusterReconciler) deleteClusterResources(ctx context.Context, log logr.Logger, yawlCluster *yawlv1beta1.YawlCluster) error {
	log.Info("Performing cleanup operations")
	// Add any custom cleanup logic here
	return nil
}

// Helper functions for constructing Kubernetes objects

func (r *YawlClusterReconciler) constructLabels(yawlCluster *yawlv1beta1.YawlCluster) map[string]string {
	return map[string]string{
		"app":                          "yawl",
		"yawl.io/cluster-name":         yawlCluster.Name,
		"yawl.io/managed-by":           "yawl-operator",
		"yawl.io/version":              yawlCluster.Spec.Version,
	}
}

func (r *YawlClusterReconciler) constructPVC(yawlCluster *yawlv1beta1.YawlCluster, name, size string) *corev1.PersistentVolumeClaim {
	quantity := r.parseStorageSize(size)
	return &corev1.PersistentVolumeClaim{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: yawlCluster.Namespace,
			Labels:    r.constructLabels(yawlCluster),
		},
		Spec: corev1.PersistentVolumeClaimSpec{
			AccessModes: []corev1.PersistentVolumeAccessMode{corev1.ReadWriteOnce},
			Resources: corev1.VolumeResourceRequirements{
				Requests: corev1.ResourceList{
					corev1.ResourceStorage: quantity,
				},
			},
		},
	}
}

func (r *YawlClusterReconciler) constructDeployment(yawlCluster *yawlv1beta1.YawlCluster) *appsv1.Deployment {
	replicas := int32(yawlCluster.Spec.Size)
	labels := r.constructLabels(yawlCluster)
	containerPort := int32(yawlCluster.Spec.Networking.Service.TargetPort)

	deployment := &appsv1.Deployment{
		ObjectMeta: metav1.ObjectMeta{
			Name:      yawlCluster.Name,
			Namespace: yawlCluster.Namespace,
			Labels:    labels,
		},
		Spec: appsv1.DeploymentSpec{
			Replicas: &replicas,
			Strategy: appsv1.DeploymentStrategy{
				Type: appsv1.RollingUpdateDeploymentStrategyType,
				RollingUpdate: &appsv1.RollingUpdateDeployment{
					MaxSurge:       &intstr.IntOrString{Type: intstr.Int, IntVal: 1},
					MaxUnavailable: &intstr.IntOrString{Type: intstr.Int, IntVal: 0},
				},
			},
			Selector: &metav1.LabelSelector{
				MatchLabels: labels,
			},
			Template: corev1.PodTemplateSpec{
				ObjectMeta: metav1.ObjectMeta{
					Labels: labels,
				},
				Spec: r.constructPodSpec(yawlCluster, containerPort),
			},
		},
	}

	return deployment
}

func (r *YawlClusterReconciler) constructPodSpec(yawlCluster *yawlv1beta1.YawlCluster, containerPort int32) corev1.PodSpec {
	// Implementation of PodSpec construction
	// This should include containers, volumes, affinity, security context, etc.
	return corev1.PodSpec{}
}

func (r *YawlClusterReconciler) constructService(yawlCluster *yawlv1beta1.YawlCluster) *corev1.Service {
	labels := r.constructLabels(yawlCluster)
	serviceType := corev1.ServiceType(yawlCluster.Spec.Networking.Service.Type)

	return &corev1.Service{
		ObjectMeta: metav1.ObjectMeta{
			Name:      yawlCluster.Name,
			Namespace: yawlCluster.Namespace,
			Labels:    labels,
		},
		Spec: corev1.ServiceSpec{
			Type:     serviceType,
			Selector: labels,
			Ports: []corev1.ServicePort{
				{
					Name:       "http",
					Port:       int32(yawlCluster.Spec.Networking.Service.Port),
					TargetPort: intstr.FromInt(yawlCluster.Spec.Networking.Service.TargetPort),
					Protocol:   corev1.ProtocolTCP,
				},
			},
		},
	}
}

func (r *YawlClusterReconciler) constructIngress(yawlCluster *yawlv1beta1.YawlCluster) *networkingv1.Ingress {
	// Implementation of Ingress construction
	return &networkingv1.Ingress{}
}

func (r *YawlClusterReconciler) constructNetworkPolicy(yawlCluster *yawlv1beta1.YawlCluster) *networkingv1.NetworkPolicy {
	// Implementation of NetworkPolicy construction
	return &networkingv1.NetworkPolicy{}
}

func (r *YawlClusterReconciler) constructHPA(yawlCluster *yawlv1beta1.YawlCluster) *autoscalingv2.HorizontalPodAutoscaler {
	// Implementation of HPA construction
	return &autoscalingv2.HorizontalPodAutoscaler{}
}

func (r *YawlClusterReconciler) updateDeploymentIfNeeded(yawlCluster *yawlv1beta1.YawlCluster, deployment *appsv1.Deployment) bool {
	// Check if update is needed and apply changes
	return false
}

func (r *YawlClusterReconciler) parseStorageSize(size string) resource.Quantity {
	// Parse storage size string and return Quantity
	q, _ := resource.ParseQuantity(size)
	return q
}

// SetupWithManager sets up the controller with the Manager
func (r *YawlClusterReconciler) SetupWithManager(mgr ctrl.Manager) error {
	// Create indexing for owned resources
	if err := mgr.GetFieldIndexer().IndexField(context.Background(), &appsv1.Deployment{}, ownerKey, func(rawObj client.Object) []string {
		deployment := rawObj.(*appsv1.Deployment)
		owner := metav1.GetControllerOf(deployment)
		if owner == nil {
			return nil
		}
		if owner.APIVersion != apiGVStr || owner.Kind != "YawlCluster" {
			return nil
		}
		return []string{owner.Name}
	}); err != nil {
		return err
	}

	return ctrl.NewControllerManagedBy(mgr).
		For(&yawlv1beta1.YawlCluster{}).
		Owns(&appsv1.Deployment{}).
		Owns(&corev1.Service{}).
		Owns(&corev1.ConfigMap{}).
		Owns(&corev1.PersistentVolumeClaim{}).
		Owns(&networkingv1.Ingress{}).
		Owns(&networkingv1.NetworkPolicy{}).
		Owns(&autoscalingv2.HorizontalPodAutoscaler{}).
		WithOptions(controller.Options{MaxConcurrentReconciles: 1}).
		WithEventFilter(predicate.GenerationChangedPredicate{}).
		Complete(r)
}
