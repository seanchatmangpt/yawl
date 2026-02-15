#------------------------------------------------------------------------------
# YAWL IBM Cloud Module Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

output "cluster_name" {
  description = "IKS cluster name"
  value       = ibm_container_cluster.yawl.name
}

output "cluster_id" {
  description = "IKS cluster ID"
  value       = ibm_container_cluster.yawl.id
}

output "cluster_crn" {
  description = "IKS cluster CRN"
  value       = ibm_container_cluster.yawl.crn
}

output "kubeconfig_command" {
  description = "Command to configure kubectl for IKS"
  value       = "ibmcloud ks cluster config --cluster ${ibm_container_cluster.yawl.id} --region ${var.region}"
}

output "cluster_public_service_endpoint" {
  description = "IKS cluster public endpoint"
  value       = ibm_container_cluster.yawl.public_service_endpoint_url
}

output "cluster_private_service_endpoint" {
  description = "IKS cluster private endpoint"
  value       = ibm_container_cluster.yawl.private_service_endpoint_url
}

output "database_id" {
  description = "Database instance ID"
  value       = ibm_database.yawl.guid
}

output "database_name" {
  description = "Database instance name"
  value       = ibm_database.yawl.name
}

output "database_hostname" {
  description = "Database hostname"
  value       = ibm_database.yawl.hostname
}

output "database_port" {
  description = "Database port"
  value       = split(":", ibm_database.yawl.connectionstrings[0].composed[0])[length(split(":", ibm_database.yawl.connectionstrings[0].composed[0])) - 1]
}

output "database_connection_string" {
  description = "Database connection string"
  value       = ibm_database.yawl.connectionstrings[0].composed[0]
  sensitive   = true
}

output "db_host" {
  description = "Database host"
  value       = ibm_database.yawl.hostname
}

output "db_password" {
  description = "Database password"
  value       = random_password.db_password.result
  sensitive   = true
}

output "cos_instance_id" {
  description = "COS instance ID"
  value       = ibm_resource_instance.cos.guid
}

output "cos_instance_name" {
  description = "COS instance name"
  value       = ibm_resource_instance.cos.name
}

output "cos_bucket_names" {
  description = "COS bucket names"
  value       = { for k, v in ibm_cos_bucket.yawl : k => v.bucket_name }
}

output "cos_bucket_ids" {
  description = "COS bucket IDs"
  value       = { for k, v in ibm_cos_bucket.yawl : k => v.id }
}

output "cos_bucket_urls" {
  description = "COS bucket URLs"
  value       = { for k, v in ibm_cos_bucket.yawl : k => "https://${v.bucket_name}.s3.${var.region}.cloud-object-storage.appdomain.cloud" }
}

output "secrets_manager_id" {
  description = "Secrets Manager instance ID"
  value       = var.enable_secrets_manager ? ibm_resource_instance.secrets_manager[0].guid : null
}

output "resource_group_id" {
  description = "Resource group ID"
  value       = data.ibm_resource_group.yawl.id
}

output "worker_pool_id" {
  description = "Default worker pool ID"
  value       = ibm_container_cluster.yawl.default_pool[0].id
}

output "workload_worker_pool_id" {
  description = "Workload worker pool ID"
  value       = var.enable_workload_node_pool ? ibm_container_worker_pool.workload[0].worker_pool_id : null
}

output "yawl_endpoint" {
  description = "YAWL service endpoint"
  value       = var.enable_private_endpoint ? ibm_container_cluster.yawl.private_service_endpoint_url : ibm_container_cluster.yawl.public_service_endpoint_url
}

output "yawl_ui_endpoint" {
  description = "YAWL UI endpoint"
  value       = "${var.enable_private_endpoint ? ibm_container_cluster.yawl.private_service_endpoint_url : ibm_container_cluster.yawl.public_service_endpoint_url}/yawl"
}

output "lb_hostname" {
  description = "Load balancer hostname"
  value       = ibm_container_cluster.yawl.public_service_endpoint_url
}
