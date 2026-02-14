# VCN Outputs
output "vcn_id" {
  description = "ID of the Virtual Cloud Network"
  value       = oci_core_vcn.main.id
}

output "vcn_cidr" {
  description = "CIDR block of the VCN"
  value       = oci_core_vcn.main.cidr_blocks[0]
}

output "public_subnet_id" {
  description = "ID of the public subnet"
  value       = oci_core_subnet.public_subnet.id
}

output "public_subnet_cidr" {
  description = "CIDR block of the public subnet"
  value       = oci_core_subnet.public_subnet.cidr_block
}

output "private_subnet_id" {
  description = "ID of the private subnet"
  value       = oci_core_subnet.private_subnet.id
}

output "private_subnet_cidr" {
  description = "CIDR block of the private subnet"
  value       = oci_core_subnet.private_subnet.cidr_block
}

output "database_subnet_id" {
  description = "ID of the database subnet"
  value       = oci_core_subnet.database_subnet.id
}

output "database_subnet_cidr" {
  description = "CIDR block of the database subnet"
  value       = oci_core_subnet.database_subnet.cidr_block
}

# Compute Instance Outputs
output "compute_instance_ids" {
  description = "IDs of all compute instances"
  value       = oci_core_instance.yawl_instances[*].id
}

output "compute_instance_display_names" {
  description = "Display names of all compute instances"
  value       = oci_core_instance.yawl_instances[*].display_name
}

output "compute_instance_private_ips" {
  description = "Private IPs of all compute instances"
  value       = data.oci_core_vnic.instance_vnic[*].private_ip_address
}

output "compute_instance_ssh_details" {
  description = "SSH connection details for compute instances"
  value = {
    for idx, instance in oci_core_instance.yawl_instances :
    instance.display_name => {
      private_ip = data.oci_core_vnic.instance_vnic[idx].private_ip_address
      user       = "ubuntu"
      command    = "ssh ubuntu@${data.oci_core_vnic.instance_vnic[idx].private_ip_address}"
    }
  }
}

output "compute_availability_domains" {
  description = "Availability domains of compute instances"
  value       = oci_core_instance.yawl_instances[*].availability_domain
}

# Database Outputs
output "mysql_db_system_id" {
  description = "ID of the MySQL Database System"
  value       = oci_mysql_mysql_db_system.yawl_db.id
}

output "mysql_db_display_name" {
  description = "Display name of the MySQL Database System"
  value       = oci_mysql_mysql_db_system.yawl_db.display_name
}

output "mysql_db_endpoint" {
  description = "MySQL Database endpoint hostname"
  value       = oci_mysql_mysql_db_system.yawl_db.endpoints[0].hostname
  sensitive   = false
}

output "mysql_db_ip_address" {
  description = "MySQL Database IP address"
  value       = oci_mysql_mysql_db_system.yawl_db.endpoints[0].ip_address
}

output "mysql_db_port" {
  description = "MySQL Database port"
  value       = oci_mysql_mysql_db_system.yawl_db.port
}

output "mysql_db_x_protocol_port" {
  description = "MySQL Database X Protocol port"
  value       = oci_mysql_mysql_db_system.yawl_db.port_x_protocol_port
}

output "mysql_db_connection_string" {
  description = "MySQL Database connection string"
  value       = "mysql://${var.mysql_admin_username}:<password>@${oci_mysql_mysql_db_system.yawl_db.endpoints[0].hostname}:${oci_mysql_mysql_db_system.yawl_db.port}/${var.mysql_db_name}"
  sensitive   = true
}

output "mysql_db_version" {
  description = "MySQL Database version"
  value       = oci_mysql_mysql_db_system.yawl_db.mysql_version
}

output "mysql_db_state" {
  description = "MySQL Database current state"
  value       = oci_mysql_mysql_db_system.yawl_db.state
}

output "mysql_app_user" {
  description = "Application MySQL user"
  value       = oci_mysql_user.yawl_app_user.username
}

output "mysql_app_user_password" {
  description = "Application MySQL user password"
  value       = random_password.db_password.result
  sensitive   = true
}

output "mysql_backup_id" {
  description = "ID of the initial database backup"
  value       = oci_mysql_backup.yawl_db_backup.id
}

output "mysql_schema_name" {
  description = "MySQL schema/database name"
  value       = oci_mysql_database.yawl_schema.schema_name
}

# Load Balancer Outputs
output "load_balancer_id" {
  description = "ID of the Load Balancer"
  value       = oci_load_balancer_load_balancer.main.id
}

output "load_balancer_display_name" {
  description = "Display name of the Load Balancer"
  value       = oci_load_balancer_load_balancer.main.display_name
}

output "load_balancer_ip_address" {
  description = "Public IP address of the Load Balancer"
  value       = oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address
}

output "load_balancer_ip_addresses" {
  description = "All IP addresses associated with the Load Balancer"
  value       = oci_load_balancer_load_balancer.main.ip_address_details[*].ip_address
}

output "load_balancer_public_subnet_id" {
  description = "Public subnet ID where Load Balancer is deployed"
  value       = oci_load_balancer_load_balancer.main.subnet_ids[0]
}

output "load_balancer_shape" {
  description = "Shape of the Load Balancer"
  value       = oci_load_balancer_load_balancer.main.shape
}

output "load_balancer_http_endpoint" {
  description = "HTTP endpoint for Load Balancer"
  value       = "http://${oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address}/"
}

output "load_balancer_https_endpoint" {
  description = "HTTPS endpoint for Load Balancer"
  value       = "https://${oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address}/"
}

output "load_balancer_dns_name" {
  description = "DNS name of the Load Balancer"
  value       = oci_load_balancer_load_balancer.main.dns_registry_name
}

output "backend_set_http_name" {
  description = "Name of the HTTP backend set"
  value       = oci_load_balancer_backend_set.backend_set_http.name
}

output "backend_set_https_name" {
  description = "Name of the HTTPS backend set"
  value       = oci_load_balancer_backend_set.backend_set_https.name
}

output "certificate_name" {
  description = "Name of the SSL certificate"
  value       = oci_load_balancer_certificate.main.certificate_name
}

# Network Security Group Outputs
output "compute_nsg_id" {
  description = "ID of the compute NSG"
  value       = oci_core_network_security_group.compute_nsg.id
}

output "database_nsg_id" {
  description = "ID of the database NSG"
  value       = oci_core_network_security_group.database_nsg.id
}

output "lb_nsg_id" {
  description = "ID of the load balancer NSG"
  value       = oci_core_network_security_group.lb_nsg.id
}

# Summary Outputs
output "deployment_summary" {
  description = "Summary of the YAWL deployment on OCI"
  value = {
    environment             = var.environment
    region                  = var.oci_region
    vcn_cidr                = oci_core_vcn.main.cidr_blocks[0]
    compute_instances       = var.compute_instance_count
    compute_shape           = var.compute_shape
    database_system         = oci_mysql_mysql_db_system.yawl_db.display_name
    database_mysql_version  = oci_mysql_mysql_db_system.yawl_db.mysql_version
    load_balancer_ip        = oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address
    load_balancer_shape     = oci_load_balancer_load_balancer.main.shape
  }
}

# Access Instructions Output
output "access_instructions" {
  description = "Instructions for accessing YAWL deployment"
  value = <<-EOT

    =================================================
    YAWL Deployment on Oracle Cloud - Access Guide
    =================================================

    Load Balancer:
      - Public IP: ${oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address}
      - HTTP URL: http://${oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address}/
      - HTTPS URL: https://${oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address}/

    Database:
      - Hostname: ${oci_mysql_mysql_db_system.yawl_db.endpoints[0].hostname}
      - Port: ${oci_mysql_mysql_db_system.yawl_db.port}
      - Database: ${var.mysql_db_name}
      - Admin User: ${var.mysql_admin_username}
      - App User: ${oci_mysql_user.yawl_app_user.username}

    Compute Instances:
      %{ for idx, instance in oci_core_instance.yawl_instances ~}
      - Instance ${idx + 1}: ${instance.display_name}
        Private IP: ${data.oci_core_vnic.instance_vnic[idx].private_ip_address}
        SSH: ssh ubuntu@${data.oci_core_vnic.instance_vnic[idx].private_ip_address}
      %{ endfor ~}

    Network Configuration:
      - VCN ID: ${oci_core_vcn.main.id}
      - VCN CIDR: ${oci_core_vcn.main.cidr_blocks[0]}
      - Public Subnet: ${oci_core_subnet.public_subnet.cidr_block}
      - Private Subnet: ${oci_core_subnet.private_subnet.cidr_block}
      - Database Subnet: ${oci_core_subnet.database_subnet.cidr_block}

    =================================================
    EOT
}
