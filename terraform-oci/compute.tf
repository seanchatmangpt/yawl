# Data source for getting the latest Ubuntu image
data "oci_core_images" "ubuntu_image" {
  compartment_id = var.compartment_ocid
  state          = "AVAILABLE"

  filter {
    name   = "display_name"
    values = ["Canonical-Ubuntu-${var.compute_image_version}-*"]
    regex  = true
  }

  sort_by = "TIMECREATED"
  sort_order = "DESC"
}

# Compute Instance - Application Server
resource "oci_core_instance" "yawl_instances" {
  count               = var.compute_instance_count
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[tonumber(var.availability_domain) - 1].name
  compartment_id      = var.compartment_ocid
  display_name        = "${var.project_name}-app-${count.index + 1}-${var.environment}"

  shape = var.compute_shape

  shape_config {
    ocpus         = var.compute_ocpus
    memory_in_gbs = var.compute_memory_gb
  }

  create_vnic_details {
    subnet_id                 = oci_core_subnet.private_subnet.id
    display_name              = "${var.project_name}-vnic-${count.index + 1}-${var.environment}"
    private_ip                = "10.0.2.${10 + count.index}"
    skip_source_dest_check    = false
    nsg_ids                   = [oci_core_network_security_group.compute_nsg.id]
  }

  source_details {
    source_type             = "IMAGE"
    source_id               = data.oci_core_images.ubuntu_image.images[0].id
    boot_volume_size_in_gbs = 50
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(templatefile("${path.module}/user_data.sh", {
      environment              = var.environment
      project_name             = var.project_name
      mysql_host               = oci_mysql_mysql_db_system.yawl_db.endpoints[0].hostname
      mysql_port               = "3306"
      mysql_database           = var.mysql_db_name
      mysql_user               = var.mysql_admin_username
      mysql_password_secret    = "change_me_to_secret"  # Should use OCI Vault
      redis_host               = "localhost"  # Could be external Redis
      redis_port               = "6379"
    }))
  }

  preserve_boot_volume = false

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name"     = "${var.project_name}-app-${count.index + 1}-${var.environment}"
      "Component" = "Application"
    }
  )

  depends_on = [oci_mysql_mysql_db_system.yawl_db]
}

# Network Security Group for Compute Instances
resource "oci_core_network_security_group" "compute_nsg" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-compute-nsg-${var.environment}"

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-compute-nsg-${var.environment}"
    }
  )
}

# Ingress rule for Compute NSG - App port from Load Balancer
resource "oci_core_network_security_group_security_rule" "compute_nsg_app_port" {
  network_security_group_id = oci_core_network_security_group.compute_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"  # TCP
  source                    = var.public_subnet_cidr
  destination_port_range {
    min = var.lb_backend_port
    max = var.lb_backend_port
  }
  description = "Allow app port from LB"
}

# Ingress rule for Compute NSG - SSH from public subnet
resource "oci_core_network_security_group_security_rule" "compute_nsg_ssh" {
  network_security_group_id = oci_core_network_security_group.compute_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"  # TCP
  source                    = var.public_subnet_cidr
  destination_port_range {
    min = 22
    max = 22
  }
  description = "Allow SSH from public subnet"
}

# Ingress rule for Compute NSG - Internal communication
resource "oci_core_network_security_group_security_rule" "compute_nsg_internal" {
  network_security_group_id = oci_core_network_security_group.compute_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"  # TCP
  source                    = var.private_subnet_cidr
  destination_port_range {
    min = 0
    max = 65535
  }
  description = "Allow internal communication"
}

# Egress rule for Compute NSG - Allow all outbound
resource "oci_core_network_security_group_security_rule" "compute_nsg_egress" {
  network_security_group_id = oci_core_network_security_group.compute_nsg.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  description               = "Allow all outbound"
}

# Secondary VNIC for enhanced networking (optional, for HA setup)
resource "oci_core_vnic_attachment" "secondary_vnic" {
  count           = var.compute_instance_count > 1 ? 1 : 0
  instance_id     = oci_core_instance.yawl_instances[0].id
  display_name    = "${var.project_name}-secondary-vnic-${var.environment}"

  create_vnic_details {
    subnet_id              = oci_core_subnet.private_subnet.id
    display_name           = "${var.project_name}-secondary-vnic-${var.environment}"
    private_ip             = "10.0.2.20"
    skip_source_dest_check = false
    nsg_ids                = [oci_core_network_security_group.compute_nsg.id]
  }
}

# Output for instance IPs and connection info
data "oci_core_vnic" "instance_vnic" {
  count   = var.compute_instance_count
  vnic_id = oci_core_instance.yawl_instances[count.index].primary_vnic_id
}

# Block Storage Volume for application data (optional)
resource "oci_core_volume" "app_volume" {
  count               = var.compute_instance_count
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[tonumber(var.availability_domain) - 1].name
  compartment_id      = var.compartment_ocid
  display_name        = "${var.project_name}-app-volume-${count.index + 1}-${var.environment}"
  size_in_gbs         = 100

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-app-volume-${count.index + 1}-${var.environment}"
    }
  )
}

# Volume Attachment
resource "oci_core_volume_attachment" "app_volume_attachment" {
  count           = var.compute_instance_count
  attachment_type = "paravirtualized"
  instance_id     = oci_core_instance.yawl_instances[count.index].id
  volume_id       = oci_core_volume.app_volume[count.index].id
  display_name    = "${var.project_name}-app-vol-attach-${count.index + 1}-${var.environment}"
}

# Data source for availability domains
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_ocid
}
