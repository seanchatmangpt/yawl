variable "gcp_project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "gcp_region" {
  description = "GCP Region"
  type        = string
  default     = "us-central1"
}

variable "gke_node_count" {
  description = "GKE initial node count"
  type        = number
  default     = 3
}

variable "gke_min_nodes" {
  description = "GKE minimum nodes for autoscaling"
  type        = number
  default     = 3
}

variable "gke_max_nodes" {
  description = "GKE maximum nodes for autoscaling"
  type        = number
  default     = 10
}

variable "gke_machine_type" {
  description = "GKE node machine type"
  type        = string
  default     = "n2-standard-4"
}

variable "cloudsql_tier" {
  description = "Cloud SQL instance tier"
  type        = string
  default     = "db-custom-2-7680"
}

variable "cloudsql_disk_size" {
  description = "Cloud SQL disk size in GB"
  type        = number
  default     = 100
}

variable "redis_memory_gb" {
  description = "Redis memory size in GB"
  type        = number
  default     = 5
}

variable "alert_email" {
  description = "Email for alerts"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "production"
}
