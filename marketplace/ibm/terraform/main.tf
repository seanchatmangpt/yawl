#------------------------------------------------------------------------------
# YAWL Workflow Engine - IBM Cloud Marketplace Deployment
# Version: 1.0.0
#
# Deploys YAWL to IBM Cloud using:
# - IBM Kubernetes Service (IKS)
# - Databases for PostgreSQL
# - Cloud Object Storage (COS)
# - IBM Cloud Load Balancer
# - IBM Cloud Monitoring
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# Provider Configuration
#------------------------------------------------------------------------------

terraform {
  required_providers {
    ibm = {
      source  = "ibm-cloud/ibm"
      version = "~> 1.65.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.26.0"
    }
  }

  required_version = ">= 1.6.0"
}

provider "ibm" {
  region           = var.ibm_region
  ibmcloud_api_key = var.ibm_api_key
}

#------------------------------------------------------------------------------
# Local Variables
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = merge(var.common_tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Provider    = "ibm-cloud"
  })

  # IBM Cloud region mapping for services
  cos_region = var.ibm_region
  db_region  = var.ibm_region
}

#------------------------------------------------------------------------------
# Data Sources
#------------------------------------------------------------------------------

data "ibm_resource_group" "resource_group" {
  name = var.ibm_resource_group
}

data "ibm_account" "account" {
}

#------------------------------------------------------------------------------
# IBM Cloud VPC
#------------------------------------------------------------------------------

resource "ibm_is_vpc" "yawl_vpc" {
  name           = "${local.name_prefix}-vpc"
  resource_group = data.ibm_resource_group.resource_group.id
  tags           = tolist([for k, v in local.common_tags : "${k}:${v}"])

  address_prefix_management = "manual"
  default_network_acl_name  = "${local.name_prefix}-default-acl"
  default_security_group_name = "${local.name_prefix}-default-sg"
  default_routing_table_name = "${local.name_prefix}-rt"
}

resource "ibm_is_public_gateway" "public_gateway" {
  count = length(var.availability_zones)

  name           = "${local.name_prefix}-pgw-${count.index}"
  vpc            = ibm_is_vpc.yawl_vpc.id
  zone           = "${var.ibm_region}-${var.availability_zones[count.index]}"
  resource_group = data.ibm_resource_group.resource_group.id
  tags           = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

resource "ibm_is_subnet" "public_subnet" {
  count = length(var.subnet_cidrs.public)

  name                     = "${local.name_prefix}-public-${count.index}"
  vpc                      = ibm_is_vpc.yawl_vpc.id
  zone                     = "${var.ibm_region}-${var.availability_zones[count.index % length(var.availability_zones)]}"
  total_ipv4_address_count = 256
  resource_group           = data.ibm_resource_group.resource_group.id
  tags                     = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

resource "ibm_is_subnet" "private_subnet" {
  count = length(var.subnet_cidrs.private)

  name                     = "${local.name_prefix}-private-${count.index}"
  vpc                      = ibm_is_vpc.yawl_vpc.id
  zone                     = "${var.ibm_region}-${var.availability_zones[count.index % length(var.availability_zones)]}"
  total_ipv4_address_count = 256
  resource_group           = data.ibm_resource_group.resource_group.id
  tags                     = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

resource "ibm_is_subnet_public_gateway_attachment" "pgw_attachment" {
  count = length(ibm_is_subnet.public_subnet)

  subnet        = ibm_is_subnet.public_subnet[count.index].id
  public_gateway = ibm_is_public_gateway.public_gateway[count.index % length(ibm_is_public_gateway.public_gateway)].id
}

#------------------------------------------------------------------------------
# IBM Kubernetes Service (IKS) Cluster
#------------------------------------------------------------------------------

resource "ibm_container_vpc_cluster" "yawl_cluster" {
  name              = "${local.name_prefix}-cluster"
  vpc_id            = ibm_is_vpc.yawl_vpc.id
  resource_group_id = data.ibm_resource_group.resource_group.id
  region            = var.ibm_region
  flavors           = [var.iks_flavor]

  kube_version = var.kubernetes_version

  worker_pool {
    name           = "default"
    vpc_id         = ibm_is_vpc.yawl_vpc.id
    flavor         = var.iks_flavor
    worker_count   = var.node_count_min
    resource_group_id = data.ibm_resource_group.resource_group.id

    dynamic "zones" {
      for_each = ibm_is_subnet.private_subnet
      content {
        name      = zones.value.zone
        subnet_id = zones.value.id
      }
    }

    labels = var.node_pool_labels
  }

  disable_public_service_endpoint = var.enable_private_endpoint

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

resource "ibm_container_vpc_worker_pool" "worker_pool" {
  count = var.enable_node_auto_scaling ? 1 : 0

  cluster           = ibm_container_vpc_cluster.yawl_cluster.id
  worker_pool_name  = "autoscaling"
  vpc_id            = ibm_is_vpc.yawl_vpc.id
  flavor            = var.iks_flavor
  worker_count      = var.node_count_min
  resource_group_id = data.ibm_resource_group.resource_group.id

  dynamic "zones" {
    for_each = ibm_is_subnet.private_subnet
    content {
      name      = zones.value.zone
      subnet_id = zones.value.id
    }
  }

  autoscale {
    enabled    = true
    min        = var.node_count_min
    max        = var.node_count_max
  }

  labels = var.node_pool_labels
}

#------------------------------------------------------------------------------
# Databases for PostgreSQL
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "postgresql" {
  name              = "${local.name_prefix}-postgresql"
  service           = "databases-for-postgresql"
  plan              = var.database_plan
  location          = local.db_region
  resource_group_id = data.ibm_resource_group.resource_group.id

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])

  parameters = {
    members_memory_allocation_mb = var.database_memory_mb
    members_disk_allocation_mb   = var.database_disk_mb
    backup_id                    = null
  }

  timeouts {
    create = "120m"
    update = "120m"
    delete = "60m"
  }
}

resource "ibm_database" "postgresql_db" {
  name              = "${local.name_prefix}-db"
  resource_group_id = data.ibm_resource_group.resource_group.id
  service           = "databases-for-postgresql"
  plan              = var.database_plan
  location          = local.db_region

  adminpassword = var.database_password
  members_cpu_allocation_count = var.database_cpu_cores
  members_memory_allocation_mb = var.database_memory_mb
  members_disk_allocation_mb   = var.database_disk_mb
  members_deployment_flavor    = var.database_flavor

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])

  whitelist {
    address     = var.database_whitelist_cidr
    description = "YAWL cluster access"
  }

  autoscaling {
    cpu {
      rate_increase_percent       = 10
      rate_limit_count_per_member = 10
      rate_period_seconds         = 900
      rate_units                  = "count"
    }
    disk {
      capacity_enabled             = true
      free_space_less_than_percent = 15
      io_above_percent             = 85
      io_enabled                   = true
      io_over_period               = "15m"
      rate_increase_percent        = 10
      rate_limit_mb_per_member     = 3670016
      rate_period_seconds          = 900
      rate_units                   = "mb"
    }
    memory {
      io_above_percent         = 85
      io_enabled               = true
      io_over_period           = "15m"
      rate_increase_percent    = 10
      rate_limit_mb_per_member = 114688
      rate_period_seconds      = 900
      rate_units               = "mb"
    }
  }

  lifecycle {
    ignore_changes = [
      whitelist
    ]
  }
}

resource "ibm_database_connection" "yawl_db_connection" {
  database_id     = ibm_database.postgresql_db.id
  user_type       = "database"
  user_id         = var.database_username
  endpoint_type   = "private"
  certificate_root = "intermediate"
}

#------------------------------------------------------------------------------
# Cloud Object Storage (COS)
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "cos" {
  name              = "${local.name_prefix}-cos"
  service           = "cloud-object-storage"
  plan              = var.cos_plan
  location          = "global"
  resource_group_id = data.ibm_resource_group.resource_group.id

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

resource "ibm_cos_bucket" "data_bucket" {
  count = length(var.storage_bucket_names)

  bucket_name          = "${local.name_prefix}-${var.storage_bucket_names[count.index]}"
  resource_instance_id = ibm_resource_instance.cos.id
  region_location      = local.cos_region
  storage_class        = var.cos_storage_class

  expire_rule {
    days    = var.storage_lifecycle_days
    enable  = true
    prefix  = ""
    rule_id = "expire-rule"
  }

  archive_rule {
    days    = var.storage_archive_days
    enable  = true
    rule_id = "archive-rule"
  }

  retention_rule {
    default = 0
    enable  = false
    maximum = 365
    minimum = 0
    permanent = false
  }
}

#------------------------------------------------------------------------------
# IBM Key Protect (for encryption)
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "key_protect" {
  count = var.enable_encryption ? 1 : 0

  name              = "${local.name_prefix}-kp"
  service           = "kms"
  plan              = "tiered-pricing"
  location          = var.ibm_region
  resource_group_id = data.ibm_resource_group.resource_group.id

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

resource "ibm_kms_key" "encryption_key" {
  count = var.enable_encryption ? 1 : 0

  instance_id  = ibm_resource_instance.key_protect[0].id
  key_name     = "${local.name_prefix}-encryption-key"
  standard_key = false
  force_delete = true
}

#------------------------------------------------------------------------------
# IBM Cloud Secrets Manager
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "secrets_manager" {
  count = var.enable_secrets_manager ? 1 : 0

  name              = "${local.name_prefix}-sm"
  service           = "secrets-manager"
  plan              = "standard"
  location          = var.ibm_region
  resource_group_id = data.ibm_resource_group.resource_group.id

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

resource "ibm_sm_secret_group" "yawl_secrets" {
  count = var.enable_secrets_manager ? 1 : 0

  instance_id = ibm_resource_instance.secrets_manager[0].id
  name        = "${local.name_prefix}-secrets"
  description = "YAWL workflow engine secrets"
}

resource "ibm_sm_arbitrary_secret" "db_credentials" {
  count = var.enable_secrets_manager ? 1 : 0

  instance_id  = ibm_resource_instance.secrets_manager[0].id
  secret_group = ibm_sm_secret_group.yawl_secrets[0].secret_group_id
  name         = "database-credentials"
  description  = "YAWL database credentials"
  payload      = jsonencode({
    username = var.database_username
    password = var.database_password
    host     = ibm_database_connection.yawl_db_connection.mongodb[0].host
    port     = ibm_database_connection.yawl_db_connection.mongodb[0].port
  })
}

#------------------------------------------------------------------------------
# IBM Cloud Monitoring (Sysdig)
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "monitoring" {
  count = var.enable_monitoring ? 1 : 0

  name              = "${local.name_prefix}-monitoring"
  service           = "sysdig-monitor"
  plan              = var.monitoring_plan
  location          = var.ibm_region
  resource_group_id = data.ibm_resource_group.resource_group.id

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

#------------------------------------------------------------------------------
# IBM Cloud Log Analysis
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "log_analysis" {
  count = var.enable_logging ? 1 : 0

  name              = "${local.name_prefix}-logs"
  service           = "log-analysis"
  plan              = var.logging_plan
  location          = var.ibm_region
  resource_group_id = data.ibm_resource_group.resource_group.id

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

#------------------------------------------------------------------------------
# IBM Cloud Internet Services (CDN/WAF)
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "cis" {
  count = var.enable_cdn_waf ? 1 : 0

  name              = "${local.name_prefix}-cis"
  service           = "internet-svcs"
  plan              = var.cis_plan
  location          = "global"
  resource_group_id = data.ibm_resource_group.resource_group.id

  tags = tolist([for k, v in local.common_tags : "${k}:${v}"])
}

#------------------------------------------------------------------------------
# Kubernetes Provider Configuration
#------------------------------------------------------------------------------

data "ibm_container_cluster_config" "yawl_cluster_config" {
  cluster_name_id = ibm_container_vpc_cluster.yawl_cluster.id
  resource_group_id = data.ibm_resource_group.resource_group.id
  config_dir      = pathexpand("~/.kube")
}

provider "kubernetes" {
  host                   = data.ibm_container_cluster_config.yawl_cluster_config.host
  token                  = data.ibm_container_cluster_config.yawl_cluster_config.token
  cluster_ca_certificate = data.ibm_container_cluster_config.yawl_cluster_config.ca_certificate
}

provider "helm" {
  kubernetes {
    host                   = data.ibm_container_cluster_config.yawl_cluster_config.host
    token                  = data.ibm_container_cluster_config.yawl_cluster_config.token
    cluster_ca_certificate = data.ibm_container_cluster_config.yawl_cluster_config.ca_certificate
  }
}

#------------------------------------------------------------------------------
# Kubernetes Namespace
#------------------------------------------------------------------------------

resource "kubernetes_namespace" "yawl" {
  metadata {
    name = var.kubernetes_namespace
    labels = merge(local.common_tags, {
      "app.kubernetes.io/name" = "yawl"
    })
  }
}

#------------------------------------------------------------------------------
# Kubernetes Secrets
#------------------------------------------------------------------------------

resource "kubernetes_secret" "db_credentials" {
  metadata {
    name      = "yawl-db-credentials"
    namespace = kubernetes_namespace.yawl.metadata[0].name
  }

  data = {
    username = var.database_username
    password = var.database_password
    host     = ibm_database_connection.yawl_db_connection.postgresql[0].host
    port     = tostring(ibm_database_connection.yawl_db_connection.postgresql[0].port)
    database = "yawl"
  }

  type = "Opaque"
}

resource "kubernetes_secret" "cos_credentials" {
  metadata {
    name      = "yawl-cos-credentials"
    namespace = kubernetes_namespace.yawl.metadata[0].name
  }

  data = {
    access_key_id     = ibm_resource_instance.cos.id
    secret_access_key = ""  # Will be populated by IBM Cloud service binding
    endpoint          = "s3.${local.cos_region}.cloud-object-storage.appdomain.cloud"
    bucket_data       = ibm_cos_bucket.data_bucket[0].bucket_name
    bucket_logs       = ibm_cos_bucket.data_bucket[1].bucket_name
    bucket_backups    = ibm_cos_bucket.data_bucket[2].bucket_name
  }

  type = "Opaque"
}

#------------------------------------------------------------------------------
# Kubernetes ConfigMap
#------------------------------------------------------------------------------

resource "kubernetes_config_map" "yawl_config" {
  metadata {
    name      = "yawl-config"
    namespace = kubernetes_namespace.yawl.metadata[0].name
  }

  data = {
    DATABASE_HOST         = ibm_database_connection.yawl_db_connection.postgresql[0].host
    DATABASE_PORT         = tostring(ibm_database_connection.yawl_db_connection.postgresql[0].port)
    DATABASE_NAME         = "yawl"
    COS_ENDPOINT          = "s3.${local.cos_region}.cloud-object-storage.appdomain.cloud"
    COS_BUCKET_DATA       = ibm_cos_bucket.data_bucket[0].bucket_name
    LOG_LEVEL             = var.log_level
    JAVA_OPTS             = "-Xms${var.yawl_heap_size} -Xmx${var.yawl_max_heap_size}"
    YAWL_ENVIRONMENT      = var.environment
    IBM_REGION            = var.ibm_region
  }
}

#------------------------------------------------------------------------------
# Helm Release - YAWL Workflow Engine
#------------------------------------------------------------------------------

resource "helm_release" "yawl" {
  name       = "yawl"
  namespace  = kubernetes_namespace.yawl.metadata[0].name
  repository = var.helm_repository
  chart      = var.helm_chart_name
  version    = var.helm_chart_version

  values = [
    yamlencode({
      replicaCount = var.yawl_replica_count

      image = {
        repository = var.yawl_image_repository
        tag        = var.yawl_image_tag
        pullPolicy = "IfNotPresent"
      }

      resources = {
        requests = {
          cpu    = var.yawl_cpu_request
          memory = var.yawl_memory_request
        }
        limits = {
          cpu    = var.yawl_cpu_limit
          memory = var.yawl_memory_limit
        }
      }

      autoscaling = {
        enabled                          = var.yawl_autoscaling_enabled
        minReplicas                      = var.yawl_min_replicas
        maxReplicas                      = var.yawl_max_replicas
        targetCPUUtilizationPercentage   = var.yawl_cpu_target
        targetMemoryUtilizationPercentage = var.yawl_memory_target
      }

      service = {
        type = "ClusterIP"
        port = 8080
      }

      ingress = {
        enabled          = var.yawl_ingress_enabled
        className        = "public-iks-k8s-nginx"
        annotations      = {
          "nginx.ingress.kubernetes.io/ssl-redirect" = "true"
          "nginx.ingress.kubernetes.io/proxy-body-size" = "50m"
        }
        hosts = var.yawl_ingress_hosts
        tls   = var.yawl_ingress_tls
      }

      serviceAccount = {
        create = true
        annotations = {
          "eks.amazonaws.com/role-arn" = ""  # Not needed for IKS
        }
      }

      config = {
        existingConfigMap = kubernetes_config_map.yawl_config.metadata[0].name
      }

      secrets = {
        dbCredentials = kubernetes_secret.db_credentials.metadata[0].name
        cosCredentials = kubernetes_secret.cos_credentials.metadata[0].name
      }

      monitoring = {
        enabled = var.enable_monitoring
      }

      logAnalysis = {
        enabled = var.enable_logging
      }
    })
  ]

  depends_on = [
    ibm_container_vpc_cluster.yawl_cluster,
    ibm_database.postgresql_db,
    kubernetes_namespace.yawl
  ]
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "cluster_id" {
  description = "ID of the IKS cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.id
}

output "cluster_name" {
  description = "Name of the IKS cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.name
}

output "cluster_endpoint" {
  description = "Public endpoint of the IKS cluster"
  value       = "https://${ibm_container_vpc_cluster.yawl_cluster.public_service_endpoint_url}"
}

output "database_connection_string" {
  description = "PostgreSQL connection string"
  value       = "postgresql://${var.database_username}:****@${ibm_database_connection.yawl_db_connection.postgresql[0].host}:${ibm_database_connection.yawl_db_connection.postgresql[0].port}/yawl"
  sensitive   = true
}

output "database_host" {
  description = "PostgreSQL host"
  value       = ibm_database_connection.yawl_db_connection.postgresql[0].host
}

output "cos_bucket_data" {
  description = "Cloud Object Storage data bucket name"
  value       = ibm_cos_bucket.data_bucket[0].bucket_name
}

output "cos_bucket_logs" {
  description = "Cloud Object Storage logs bucket name"
  value       = ibm_cos_bucket.data_bucket[1].bucket_name
}

output "cos_bucket_backups" {
  description = "Cloud Object Storage backups bucket name"
  value       = ibm_cos_bucket.data_bucket[2].bucket_name
}

output "monitoring_instance_id" {
  description = "IBM Cloud Monitoring instance ID"
  value       = var.enable_monitoring ? ibm_resource_instance.monitoring[0].id : null
}

output "log_analysis_instance_id" {
  description = "IBM Cloud Log Analysis instance ID"
  value       = var.enable_logging ? ibm_resource_instance.log_analysis[0].id : null
}

output "yawl_url" {
  description = "YAWL application URL"
  value       = var.yawl_ingress_enabled && length(var.yawl_ingress_hosts) > 0 ? "https://${var.yawl_ingress_hosts[0].host}/yawl" : "http://${ibm_container_vpc_cluster.yawl_cluster.public_service_endpoint_url}:8080/yawl"
}

output "resource_group_id" {
  description = "IBM Cloud resource group ID"
  value       = data.ibm_resource_group.resource_group.id
}

output "vpc_id" {
  description = "VPC ID"
  value       = ibm_is_vpc.yawl_vpc.id
}

output "kubeconfig_command" {
  description = "Command to download kubeconfig"
  value       = "ibmcloud ks cluster config --cluster ${ibm_container_vpc_cluster.yawl_cluster.id}"
}
