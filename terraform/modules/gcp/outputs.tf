#------------------------------------------------------------------------------
# YAWL GCP Module Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

output "cluster_name" {
  description = "GKE cluster name"
  value       = google_container_cluster.yawl.name
}

output "cluster_endpoint" {
  description = "GKE cluster endpoint"
  value       = google_container_cluster.yawl.endpoint
  sensitive   = true
}

output "cluster_ca_certificate" {
  description = "GKE cluster CA certificate (base64 encoded)"
  value       = google_container_cluster.yawl.master_auth[0].cluster_ca_certificate
  sensitive   = true
}

output "gcloud_command" {
  description = "Command to configure kubectl for GKE"
  value       = "gcloud container clusters get-credentials ${google_container_cluster.yawl.name} --region ${var.region} --project ${var.gcp_project_id}"
}

output "cloudsql_connection_name" {
  description = "Cloud SQL connection name for connecting from GKE"
  value       = google_sql_database_instance.yawl_db.connection_name
}

output "db_host" {
  description = "Cloud SQL private IP address"
  value       = google_sql_database_instance.yawl_db.private_ip_address
}

output "db_name" {
  description = "Database name"
  value       = google_sql_database.yawl.name
}

output "db_username" {
  description = "Database username"
  value       = google_sql_user.yawl.name
}

output "db_password" {
  description = "Database password"
  value       = random_password.db_password.result
  sensitive   = true
}

output "bucket_urls" {
  description = "GCS bucket URLs"
  value       = { for k, v in google_storage_bucket.yawl : k => v.url }
}

output "bucket_names" {
  description = "GCS bucket names"
  value       = { for k, v in google_storage_bucket.yawl : k => v.name }
}

output "service_account_email" {
  description = "GKE node service account email"
  value       = google_service_account.gke_nodes.email
}

output "node_pool_name" {
  description = "GKE node pool name"
  value       = google_container_node_pool.yawl.name
}

output "yawl_endpoint" {
  description = "YAWL service endpoint"
  value       = "https://${google_container_cluster.yawl.endpoint}"
}

output "yawl_ui_endpoint" {
  description = "YAWL UI endpoint"
  value       = "https://${google_container_cluster.yawl.endpoint}/yawl"
}

output "lb_ip" {
  description = "Load balancer IP address"
  value       = google_container_cluster.yawl.endpoint
}
