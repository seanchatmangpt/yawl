#------------------------------------------------------------------------------
# YAWL Oracle Cloud Module - OKE, Autonomous DB, Object Storage
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# Oracle Kubernetes Engine (OKE)
#------------------------------------------------------------------------------

resource "oci_containerengine_cluster" "yawl" {
  compartment_id     = var.compartment_id
  kubernetes_version = var.kubernetes_version
  name               = "${local.name_prefix}-cluster"
  vcn_id             = var.vcn_id

  endpoint_config {
    is_public_ip_enabled = !var.enable_private_endpoint
    nsg_ids              = []
    subnet_id            = var.public_subnet_ids[0]
  }

  options {
    add_ons {
      is_kubernetes_dashboard_enabled = false
      is_tiller_enabled               = false
    }

    kubernetes_network_config {
      pods_cidr     = "10.244.0.0/16"
      services_cidr = "10.96.0.0/16"
    }

    service_lb_subnet_ids = var.enable_private_endpoint ? [] : [var.public_subnet_ids[0]]

    persistent_volume_config {
      defined_tags = {}
    }

    service_lb_config {
      defined_tags = {}
    }
  }

  kms_key_id = var.kms_key_id != "" ? var.kms_key_id : null

  freeform_tags = merge(var.common_tags, {
    ManagedBy = "terraform"
  })
}

resource "oci_identity_compartment" "yawl" {
  count          = var.create_compartment ? 1 : 0
  compartment_id = var.tenancy_id
  description    = "Compartment for ${local.name_prefix} resources"
  name           = local.name_prefix

  freeform_tags = var.common_tags
}

resource "oci_containerengine_node_pool" "yawl" {
  compartment_id     = var.compartment_id
  cluster_id         = oci_containerengine_cluster.yawl.id
  kubernetes_version = var.kubernetes_version
  name               = "${local.name_prefix}-node-pool"

  node_config_details {
    placement_configs {
      availability_domain = var.availability_domains[0]
      capacity_reservation_id = null
      fault_domain = null
      subnet_id     = var.private_subnet_ids[0]
    }

    dynamic "placement_configs" {
      for_each = length(var.availability_domains) > 1 ? slice(var.availability_domains, 1, length(var.availability_domains)) : []
      content {
        availability_domain = placement_configs.value
        subnet_id           = var.private_subnet_ids[index(var.availability_domains, placement_configs.value) % length(var.private_subnet_ids)]
      }
    }

    is_pv_encryption_in_transit_enabled = true
    kms_key_id                          = var.kms_key_id != "" ? var.kms_key_id : null

    freeform_tags = merge(var.common_tags, {
      Name      = "${local.name_prefix}-node"
      NodeType  = "worker"
    })

    node_pool_pod_network_option_details {
      cni_type = "OCI_VCN_IP_NATIVE"
      max_pods_per_node = var.max_pods_per_node
      pod_network_options {
        subnet_id = var.private_subnet_ids[0]
      }
    }

    nsg_ids = []
  }

  node_evacuate_node_pool_setting {
    is_evacuate_on_node_pool_deletion = true
    is_force_delete_after_grace_duration = false
  }

  node_shape = var.node_shape

  dynamic "node_shape_config" {
    for_each = var.node_shape_flex ? [1] : []
    content {
      ocpus         = var.node_ocpus
      memory_in_gbs = var.node_memory_gb
    }
  }

  node_source_details {
    boot_volume_size_in_gbs = var.node_disk_size_gb
    image_id                = var.node_image_id
    source_type             = "IMAGE"

    boot_volume_vpus_per_gb = 20
  }

  initial_node_labels {
    key   = "environment"
    value = var.environment
  }

  dynamic "initial_node_labels" {
    for_each = var.node_pool_labels
    content {
      key   = initial_node_labels.key
      value = initial_node_labels.value
    }
  }

  ssh_public_key = var.ssh_public_key

  freeform_tags = var.common_tags
}

resource "oci_containerengine_node_pool" "workload" {
  count              = var.enable_workload_node_pool ? 1 : 0
  compartment_id     = var.compartment_id
  cluster_id         = oci_containerengine_cluster.yawl.id
  kubernetes_version = var.kubernetes_version
  name               = "${local.name_prefix}-workload-pool"

  node_config_details {
    placement_configs {
      availability_domain = var.availability_domains[0]
      subnet_id           = var.private_subnet_ids[0]
    }

    is_pv_encryption_in_transit_enabled = true

    freeform_tags = merge(var.common_tags, {
      Name      = "${local.name_prefix}-workload-node"
      NodeType  = "workload"
    })

    node_pool_pod_network_option_details {
      cni_type = "OCI_VCN_IP_NATIVE"
      max_pods_per_node = var.max_pods_per_node
      pod_network_options {
        subnet_id = var.private_subnet_ids[0]
      }
    }
  }

  node_shape = var.workload_node_shape

  dynamic "node_shape_config" {
    for_each = true ? [1] : []
    content {
      ocpus         = var.workload_node_ocpus
      memory_in_gbs = var.workload_node_memory_gb
    }
  }

  node_source_details {
    boot_volume_size_in_gbs = var.node_disk_size_gb
    image_id                = var.node_image_id
    source_type             = "IMAGE"
  }

  initial_node_labels {
    key   = "workload"
    value = "true"
  }

  ssh_public_key = var.ssh_public_key

  freeform_tags = var.common_tags

  lifecycle {
    ignore_changes = [node_config_details[0].size]
  }
}

#------------------------------------------------------------------------------
# Autonomous Database
#------------------------------------------------------------------------------

resource "oci_database_autonomous_database" "yawl" {
  admin_password           = random_password.db_password.result
  compartment_id           = var.compartment_id
  cpu_core_count           = var.database_cpu_core_count
  data_storage_size_in_tbs = var.database_storage_tbs
  db_name                  = replace("${var.project_name}${var.environment}", "-", "")
  display_name             = "${local.name_prefix}-adb"

  db_workload                      = "OLTP"
  is_auto_scaling_enabled          = var.database_auto_scaling
  is_preview_version_with_service_terms = "NONE"
  license_model                    = "BRING_YOUR_OWN_LICENSE"

  autonomous_database_backup_config {
    recovery_window_in_days = var.database_backup_days
  }

  nsg_ids = []

  private_endpoint {
    nsg_ids    = []
    subnet_id  = var.private_subnet_ids[0]
  }

  retention_period_days = var.database_backup_days

  freeform_tags = var.common_tags
}

resource "random_password" "db_password" {
  length  = 32
  special = true
  upper   = true
  lower   = true
  numeric = true
}

#------------------------------------------------------------------------------
# Object Storage
#------------------------------------------------------------------------------

resource "oci_objectstorage_bucket" "yawl" {
  for_each = toset(var.storage_bucket_names)

  compartment_id = var.compartment_id
  name           = "${local.name_prefix}-${each.value}"
  namespace      = data.oci_objectstorage_namespace.yawl.namespace

  access_type  = "NoPublicAccess"
  storage_tier = "Standard"

  versioning = var.storage_versioning ? "Enabled" : "Disabled"

  auto_tiering = "Disabled"

  freeform_tags = var.common_tags
}

data "oci_objectstorage_namespace" "yawl" {
  compartment_id = var.compartment_id
}

resource "oci_objectstorage_object_lifecycle_policy" "yawl" {
  for_each = oci_objectstorage_bucket.yawl

  bucket      = each.value.name
  namespace   = data.oci_objectstorage_namespace.yawl.namespace

  rules {
    action      = "ARCHIVE"
    is_enabled  = true
    name        = "archive-old-objects"
    time_amount = var.storage_lifecycle_days
    time_unit   = "DAYS"
    target      = "objects"
  }

  rules {
    action      = "DELETE"
    is_enabled  = true
    name        = "delete-archived-objects"
    time_amount = var.storage_archive_days
    time_unit   = "DAYS"
    target      = "objects"
  }
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "cluster_name" {
  value = oci_containerengine_cluster.yawl.name
}

output "cluster_id" {
  value = oci_containerengine_cluster.yawl.id
}

output "kubeconfig_command" {
  value = "oci ce cluster create-kubeconfig --cluster-id ${oci_containerengine_cluster.yawl.id} --file $HOME/.kube/config --region ${var.region}"
}

output "db_connection_strings" {
  value = {
    high     = oci_database_autonomous_database.yawl.connection_strings[0].profiles[0].value
    medium   = oci_database_autonomous_database.yawl.connection_strings[0].profiles[1].value
    low      = oci_database_autonomous_database.yawl.connection_strings[0].profiles[2].value
  }
  sensitive = true
}

output "storage_namespace" {
  value = data.oci_objectstorage_namespace.yawl.namespace
}

output "bucket_urls" {
  value = { for k, v in oci_objectstorage_bucket.yawl : k => "https://objectstorage.${var.region}.oraclecloud.com/n/${data.oci_objectstorage_namespace.yawl.namespace}/b/${v.name}" }
}
