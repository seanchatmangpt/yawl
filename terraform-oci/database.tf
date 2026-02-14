# MySQL Database System
resource "oci_mysql_mysql_db_system" "yawl_db" {
  compartment_id       = var.compartment_ocid
  availability_domain  = data.oci_identity_availability_domains.ads.availability_domains[tonumber(var.availability_domain) - 1].name
  display_name         = var.database_display_name

  shape_name           = var.mysql_shape
  mysql_version        = var.mysql_db_version

  # Subnet configuration
  subnet_id            = oci_core_subnet.database_subnet.id
  port                 = 3306
  port_x_protocol_port = 33060

  # Admin user configuration
  admin_username = var.mysql_admin_username
  admin_password = var.mysql_admin_password

  # Backup configuration
  backup_policy {
    is_enabled            = true
    window_start_time     = "01:00-00:00"  # 1 AM UTC
    retention_in_days     = var.mysql_backup_retention_days
    pitr_policy {
      is_enabled = var.environment == "production" ? true : false
    }
  }

  # High Availability configuration
  high_availability_config {
    is_ha_enabled = var.mysql_enable_high_availability
  }

  # Network security group
  network_security_group_ids = [oci_core_network_security_group.database_nsg.id]

  # Database configuration
  configuration_id = data.oci_mysql_configurations.mysql_configuration.configurations[0].id

  # Maintenance window
  maintenance_window_start_time = "SUNDAY 03:00"

  # Initial database
  initial_display_name = var.mysql_db_name

  # Enable encryption
  data_storage_size_in_gb = 50

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name"      = var.database_display_name
      "Component" = "Database"
    }
  )

  depends_on = [oci_core_network_security_group.database_nsg]
}

# Network Security Group for Database
resource "oci_core_network_security_group" "database_nsg" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-db-nsg-${var.environment}"

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-db-nsg-${var.environment}"
    }
  )
}

# Ingress rule for Database NSG - MySQL from private subnet
resource "oci_core_network_security_group_security_rule" "db_nsg_mysql" {
  network_security_group_id = oci_core_network_security_group.database_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"  # TCP
  source                    = var.private_subnet_cidr
  destination_port_range {
    min = 3306
    max = 3306
  }
  description = "Allow MySQL from app subnet"
}

# Ingress rule for Database NSG - MySQL X Protocol from private subnet
resource "oci_core_network_security_group_security_rule" "db_nsg_mysql_x" {
  network_security_group_id = oci_core_network_security_group.database_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"  # TCP
  source                    = var.private_subnet_cidr
  destination_port_range {
    min = 33060
    max = 33060
  }
  description = "Allow MySQL X Protocol from app subnet"
}

# Egress rule for Database NSG
resource "oci_core_network_security_group_security_rule" "db_nsg_egress" {
  network_security_group_id = oci_core_network_security_group.database_nsg.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  description               = "Allow all outbound"
}

# Data source for MySQL configurations
data "oci_mysql_configurations" "mysql_configuration" {
  compartment_id = var.compartment_ocid
  shape_name     = var.mysql_shape
  type           = ["DEFAULT"]

  filter {
    name   = "display_name"
    values = ["MySQL.*"]
    regex  = true
  }
}

# MySQL Database Schema and User setup
resource "oci_mysql_user" "yawl_app_user" {
  db_system_id = oci_mysql_mysql_db_system.yawl_db.id
  username     = "yawl_app"
  password     = random_password.db_password.result
  host         = "%"  # Allow connections from any host in VCN
}

# Generate random password for app user
resource "random_password" "db_password" {
  length  = 16
  special = true
}

# Grant privileges to app user
resource "oci_mysql_user_grants" "yawl_app_grants" {
  db_system_id = oci_mysql_mysql_db_system.yawl_db.id
  username     = oci_mysql_user.yawl_app_user.username
  host         = oci_mysql_user.yawl_app_user.host

  privilege {
    grant = "SELECT"
    schema = var.mysql_db_name
  }

  privilege {
    grant = "INSERT"
    schema = var.mysql_db_name
  }

  privilege {
    grant = "UPDATE"
    schema = var.mysql_db_name
  }

  privilege {
    grant = "DELETE"
    schema = var.mysql_db_name
  }

  privilege {
    grant = "CREATE"
    schema = var.mysql_db_name
  }

  privilege {
    grant = "ALTER"
    schema = var.mysql_db_name
  }

  privilege {
    grant = "INDEX"
    schema = var.mysql_db_name
  }
}

# MySQL Backup (stored in OCI Object Storage)
resource "oci_mysql_backup" "yawl_db_backup" {
  db_system_id = oci_mysql_mysql_db_system.yawl_db.id
  backup_type  = "FULL"
  display_name = "${var.project_name}-db-backup-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
  description  = "YAWL Database backup - ${var.environment}"

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name"      = "${var.project_name}-db-backup-${var.environment}"
      "Component" = "Backup"
    }
  )

  depends_on = [oci_mysql_mysql_db_system.yawl_db]
}

# Database Parameter Group (Custom configuration)
resource "oci_mysql_database" "yawl_schema" {
  db_system_id = oci_mysql_mysql_db_system.yawl_db.id
  schema_name  = var.mysql_db_name
  character_set = "utf8mb4"
  collation    = "utf8mb4_unicode_ci"
}

# Optional: Replication Channel for read replicas (requires Advanced MySQL shape)
# Uncomment if using a shape that supports HA Cluster with read replicas
/*
resource "oci_mysql_mysql_db_system" "read_replica" {
  count                = var.environment == "production" ? 1 : 0
  compartment_id       = var.compartment_ocid
  availability_domain  = data.oci_identity_availability_domains.ads.availability_domains[(tonumber(var.availability_domain) % length(data.oci_identity_availability_domains.ads.availability_domains))].name
  display_name         = "${var.database_display_name}-replica"

  shape_name           = var.mysql_shape
  mysql_version        = var.mysql_db_version
  subnet_id            = oci_core_subnet.database_subnet.id
  port                 = 3306
  port_x_protocol_port = 33060

  admin_username = var.mysql_admin_username
  admin_password = var.mysql_admin_password

  network_security_group_ids = [oci_core_network_security_group.database_nsg.id]

  backup_policy {
    is_enabled        = true
    window_start_time = "02:00-00:00"
    retention_in_days = var.mysql_backup_retention_days
  }

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name"      = "${var.database_display_name}-replica"
      "Component" = "Database-Replica"
    }
  )
}
*/
