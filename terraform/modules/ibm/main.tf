#------------------------------------------------------------------------------
# YAWL IBM Cloud Module - IKS, Databases, COS
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# IBM Cloud Resource Group
#------------------------------------------------------------------------------

data "ibm_resource_group" "yawl" {
  name = var.resource_group
}

#------------------------------------------------------------------------------
# VPC Infrastructure
#------------------------------------------------------------------------------

data "ibm_is_vpc" "yawl" {
  name = var.vpc_name
}

data "ibm_is_subnet" "private" {
  count = length(var.private_subnet_names)
  name  = var.private_subnet_names[count.index]
}

data "ibm_is_subnet" "public" {
  count = length(var.public_subnet_names)
  name  = var.public_subnet_names[count.index]
}

#------------------------------------------------------------------------------
# IBM Cloud Kubernetes Service (IKS)
#------------------------------------------------------------------------------

resource "ibm_container_cluster" "yawl" {
  name              = "${local.name_prefix}-cluster"
  resource_group_id = data.ibm_resource_group.yawl.id
  region            = var.region
  kube_version      = var.kubernetes_version

  default_pool {
    name           = "default"
    size_per_zone  = var.node_count_min
    machine_type   = var.node_flavor
    disk_encryption = true

    labels = merge({
      environment = var.environment
    }, var.node_pool_labels)

    dynamic "zones" {
      for_each = var.zones
      content {
        name = zones.value
        private_vlan_id = data.ibm_is_subnet.private[0].id
        public_vlan_id  = data.ibm_is_subnet.public[0].id
      }
    }
  }

  vpc_id    = data.ibm_is_vpc.yawl.id
  subnet_id = data.ibm_is_subnet.private[0].id

  disable_public_service_endpoint = var.enable_private_endpoint

  pod_subnet         = "172.30.0.0/16"
  service_subnet     = "172.21.0.0/16"
  calico_autodiscovery_disabled = false

  wait_time_minutes = 90

  workerpool_taints = var.node_pool_taints

  force_delete_storage = var.environment == "dev"

  tags = var.common_tags

  lifecycle {
    ignore_changes = [
      default_pool[0].size_per_zone,
      worker_num
    ]
  }
}

resource "ibm_container_worker_pool" "workload" {
  count           = var.enable_workload_node_pool ? 1 : 0
  cluster         = ibm_container_cluster.yawl.id
  worker_pool_name = "workload"
  machine_type    = var.workload_node_flavor
  size_per_zone   = var.workload_node_count_min
  disk_encryption = true
  resource_group_id = data.ibm_resource_group.yawl.id

  labels = {
    workload = "true"
  }

  dynamic "zones" {
    for_each = var.zones
    content {
      name           = zones.value
      private_vlan_id = data.ibm_is_subnet.private[0].id
      public_vlan_id  = data.ibm_is_subnet.public[0].id
    }
  }
}

resource "ibm_container_worker_pool_zone_attachment" "workload" {
  count            = var.enable_workload_node_pool ? length(var.zones) : 0
  cluster          = ibm_container_cluster.yawl.id
  worker_pool      = ibm_container_worker_pool.workload[0].worker_pool_name
  zone             = var.zones[count.index]
  private_vlan_id  = data.ibm_is_subnet.private[count.index % length(data.ibm_is_subnet.private)].id
  public_vlan_id   = data.ibm_is_subnet.public[count.index % length(data.ibm_is_subnet.public)].id
  resource_group_id = data.ibm_resource_group.yawl.id
}

#------------------------------------------------------------------------------
# IBM Cloud Database (PostgreSQL)
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "database" {
  name              = var.database_name
  service           = "databases-for-postgresql"
  plan              = var.database_plan
  location          = var.region
  resource_group_id = data.ibm_resource_group.yawl.id

  parameters = {
    members_disk_allocation_mb = var.database_disk_gb * 1024
    members_memory_allocation_mb = var.database_memory_gb * 1024
  }

  tags = var.common_tags
}

resource "ibm_database" "yawl" {
  name              = "${local.name_prefix}-db"
  resource_group_id = data.ibm_resource_group.yawl.id
  location          = var.region
  service           = "databases-for-postgresql"
  plan              = var.database_plan
  service_endpoints = "private"

  members_memory_allocation_mb = var.database_memory_gb * 1024
  members_disk_allocation_mb   = var.database_disk_gb * 1024
  members_cpu_allocation_count = var.database_cpu_count

  group {
    group_id = "member"
    members {
      allocation_count = var.database_members
    }
  }

  adminpassword                = random_password.db_password.result
  auto_scaling {
    cpu {
      max_cpu_count  = 6
      max_node_count = 3
    }
    disk {
      max_capacity_enabled = true
    }
    memory {
      max_capacity_enabled = true
    }
  }

  tags = var.common_tags

  lifecycle {
    prevent_destroy = var.environment == "prod"
  }
}

resource "random_password" "db_password" {
  length  = 32
  special = false
}

#------------------------------------------------------------------------------
# Cloud Object Storage (COS)
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "cos" {
  name              = "${local.name_prefix}-cos"
  service           = "cloud-object-storage"
  plan              = var.cos_plan
  location          = "global"
  resource_group_id = data.ibm_resource_group.yawl.id

  tags = var.common_tags
}

resource "ibm_cos_bucket" "yawl" {
  for_each          = toset(var.cos_bucket_names)
  bucket_name       = "${local.name_prefix}-${each.value}"
  resource_instance_id = ibm_resource_instance.cos.id
  region_location   = var.region
  storage_class     = var.storage_class

  endpoint_type = "private"

  # Object versioning
  # Note: IBM COS versioning is configured via API or console

  # Retention policy
  dynamic "retention_rule" {
    for_each = var.enable_retention ? [1] : []
    content {
      default = var.retention_days
      maximum = var.retention_days
      permanent = false
    }
  }

  # Archive rule (lifecycle)
  dynamic "archive_rule" {
    for_each = var.enable_archive ? [1] : []
    content {
      enable = true
      days   = var.storage_lifecycle_days
      type   = "Glacier"
    }
  }

  # Expire rule (lifecycle)
  dynamic "expire_rule" {
    for_each = var.enable_expiry ? [1] : []
    content {
      enable = true
      days   = var.storage_archive_days
      expired_object_delete_marker = true
    }
  }
}

#------------------------------------------------------------------------------
# Secrets Manager for storing credentials
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "secrets_manager" {
  count             = var.enable_secrets_manager ? 1 : 0
  name              = "${local.name_prefix}-secrets"
  service           = "secrets-manager"
  plan              = "standard"
  location          = var.region
  resource_group_id = data.ibm_resource_group.yawl.id

  tags = var.common_tags
}

resource "ibm_sm_arbitrary_secret" "db_password" {
  count              = var.enable_secrets_manager ? 1 : 0
  instance_id        = ibm_resource_instance.secrets_manager[0].guid
  region             = var.region
  name               = "${local.name_prefix}-db-password"
  description        = "Database password for ${local.name_prefix}"
  secret_group_id    = ibm_sm_secret_group.yawl[0].secret_group_id
  payload            = random_password.db_password.result
}

resource "ibm_sm_secret_group" "yawl" {
  count        = var.enable_secrets_manager ? 1 : 0
  instance_id  = ibm_resource_instance.secrets_manager[0].guid
  region       = var.region
  name         = local.name_prefix
  description  = "Secrets for ${local.name_prefix}"
}

#------------------------------------------------------------------------------
# Service Binding for Kubernetes to access services
#------------------------------------------------------------------------------

resource "ibm_container_bind_service" "database" {
  cluster_name_id   = ibm_container_cluster.yawl.id
  service_instance_id = ibm_database.yawl.guid
  namespace_id      = "default"
  resource_group_id = data.ibm_resource_group.yawl.id

  role = "Writer"
}

resource "ibm_container_bind_service" "cos" {
  cluster_name_id   = ibm_container_cluster.yawl.id
  service_instance_id = ibm_resource_instance.cos.guid
  namespace_id      = "default"
  resource_group_id = data.ibm_resource_group.yawl.id

  role = "Writer"
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "cluster_name" {
  value = ibm_container_cluster.yawl.name
}

output "cluster_id" {
  value = ibm_container_cluster.yawl.id
}

output "kubeconfig_command" {
  value = "ibmcloud ks cluster config --cluster ${ibm_container_cluster.yawl.id} --region ${var.region}"
}

output "database_connection_string" {
  value     = ibm_database.yawl.connectionstrings[0].composed[0]
  sensitive = true
}

output "cos_bucket_urls" {
  value = { for k, v in ibm_cos_bucket.yawl : k => "https://${v.bucket_name}.s3.${var.region}.cloud-object-storage.appdomain.cloud" }
}
