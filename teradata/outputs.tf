# Outputs for YAWL Teradata Deployment
# These outputs provide critical connection information for deployment and operations

output "deployment_summary" {
  value = {
    environment              = var.environment
    teradata_host           = google_compute_address.teradata.address
    teradata_port           = var.teradata_port
    gke_cluster_name        = google_container_cluster.yawl_teradata.name
    gke_region              = var.gcp_region
    yawl_metadata_database  = google_sql_database.yawl_metadata.name
    backup_storage_location = google_storage_bucket.yawl_teradata_backups.location
  }
  description = "Summary of YAWL Teradata deployment configuration"
}

output "teradata_connection_string" {
  value       = "jdbc:teradata://${google_compute_address.teradata.address}/DBS_PORT=${var.teradata_port},DATABASE=${var.yawl_database_name},USER=${var.yawl_db_user},PASSWORD=<password>,CHARSET=${var.teradata_charset}"
  sensitive   = true
  description = "JDBC connection string for Teradata (use Secret Manager for password)"
}

output "gke_connect_command" {
  value       = "gcloud container clusters get-credentials ${google_container_cluster.yawl_teradata.name} --region ${var.gcp_region} --project ${var.gcp_project_id}"
  description = "Command to get GKE cluster credentials"
}

output "kms_key_rings" {
  value = {
    keyring = google_kms_key_ring.yawl_teradata.id
    backup_key = google_kms_crypto_key.backup.id
    database_key = google_kms_crypto_key.database.id
  }
  description = "KMS key resources for encryption"
}

output "network_configuration" {
  value = {
    vpc_network    = google_compute_network.yawl_teradata.name
    subnet_name    = google_compute_subnetwork.yawl_teradata.name
    subnet_cidr    = google_compute_subnetwork.yawl_teradata.ip_cidr_range
    firewall_rules = [
      google_compute_firewall.teradata_ingress.name,
      google_compute_firewall.teradata_egress.name
    ]
  }
  description = "Network configuration details"
}

output "storage_configuration" {
  value = {
    backup_bucket    = google_storage_bucket.yawl_teradata_backups.name
    backup_location  = google_storage_bucket.yawl_teradata_backups.location
    versioning_enabled = google_storage_bucket.yawl_teradata_backups.versioning[0].enabled
    backup_retention_days = var.backup_retention_days
  }
  description = "Cloud Storage backup configuration"
}

output "monitoring_configuration" {
  value = var.enable_monitoring ? {
    cpu_alert_policy = google_monitoring_alert_policy.teradata_cpu[0].name
    disk_alert_policy = google_monitoring_alert_policy.teradata_disk[0].name
    alert_email = var.alert_email
  } : null
  description = "Cloud Monitoring alert configuration"
}

output "redis_connection_details" {
  value = {
    host              = google_redis_instance.yawl_cache.host
    port              = google_redis_instance.yawl_cache.port
    region            = google_redis_instance.yawl_cache.region
    memory_size_gb    = google_redis_instance.yawl_cache.memory_size_gb
    redis_version     = google_redis_instance.yawl_cache.redis_version
    tier              = google_redis_instance.yawl_cache.tier
  }
  description = "Redis instance connection details"
  sensitive   = false
}

output "service_accounts" {
  value = {
    teradata_sa = google_compute_instance.teradata.service_account[0].email
  }
  description = "Service accounts created for YAWL Teradata deployment"
}
