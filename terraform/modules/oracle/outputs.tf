#------------------------------------------------------------------------------
# YAWL Oracle Cloud Module Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

output "cluster_name" {
  description = "OKE cluster name"
  value       = oci_containerengine_cluster.yawl.name
}

output "cluster_id" {
  description = "OKE cluster OCID"
  value       = oci_containerengine_cluster.yawl.id
}

output "cluster_endpoints" {
  description = "OKE cluster endpoints"
  value       = oci_containerengine_cluster.yawl.endpoints
}

output "kubeconfig_command" {
  description = "Command to generate OKE kubeconfig"
  value       = "oci ce cluster create-kubeconfig --cluster-id ${oci_containerengine_cluster.yawl.id} --file $HOME/.kube/config --region ${var.region}"
}

output "node_pool_id" {
  description = "OKE node pool OCID"
  value       = oci_containerengine_node_pool.yawl.id
}

output "node_pool_name" {
  description = "OKE node pool name"
  value       = oci_containerengine_node_pool.yawl.name
}

output "autonomous_db_id" {
  description = "Autonomous Database OCID"
  value       = oci_database_autonomous_database.yawl.id
}

output "autonomous_db_name" {
  description = "Autonomous Database name"
  value       = oci_database_autonomous_database.yawl.db_name
}

output "db_host" {
  description = "Autonomous Database host"
  value       = replace(oci_database_autonomous_database.yawl.connection_strings[0].profiles[0].value, "/^.*@([^:]+):.*$/", "$1")
}

output "db_connection_strings" {
  description = "Autonomous Database connection strings"
  value       = oci_database_autonomous_database.yawl.connection_strings
  sensitive   = true
}

output "db_password" {
  description = "Autonomous Database admin password"
  value       = random_password.db_password.result
  sensitive   = true
}

output "storage_namespace" {
  description = "Object Storage namespace"
  value       = data.oci_objectstorage_namespace.yawl.namespace
}

output "bucket_names" {
  description = "Object Storage bucket names"
  value       = { for k, v in oci_objectstorage_bucket.yawl : k => v.name }
}

output "bucket_urls" {
  description = "Object Storage bucket URLs"
  value       = { for k, v in oci_objectstorage_bucket.yawl : k => "https://objectstorage.${var.region}.oraclecloud.com/n/${data.oci_objectstorage_namespace.yawl.namespace}/b/${v.name}" }
}

output "yawl_endpoint" {
  description = "YAWL service endpoint"
  value       = "https://${oci_containerengine_cluster.yawl.endpoints[0].private_endpoint}"
}

output "yawl_ui_endpoint" {
  description = "YAWL UI endpoint"
  value       = "https://${oci_containerengine_cluster.yawl.endpoints[0].private_endpoint}/yawl"
}

output "lb_ip" {
  description = "Load balancer IP"
  value       = var.enable_private_endpoint ? oci_containerengine_cluster.yawl.endpoints[0].private_endpoint : oci_containerengine_cluster.yawl.endpoints[0].public_endpoint
}

output "compartment_id" {
  description = "Compartment OCID"
  value       = var.create_compartment ? oci_identity_compartment.yawl[0].id : var.compartment_id
}
