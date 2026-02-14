locals {
  # Common naming convention
  common_name = "${var.project_name}-${var.environment}"

  # Merge all tags
  all_tags = merge(
    var.common_tags,
    var.tags,
    {
      "Terraform"  = "true"
      "Environment" = var.environment
      "CreatedAt"   = timestamp()
    }
  )

  # Network locals
  vcn_display_name  = "${local.common_name}-vcn"
  igw_display_name  = "${local.common_name}-igw"
  nat_display_name  = "${local.common_name}-nat"

  # Compute locals
  compute_display_name_prefix = "${local.common_name}-app"
  compute_nsg_name           = "${local.common_name}-compute-nsg"

  # Database locals
  mysql_display_name = "${var.database_display_name}-${var.environment}"
  mysql_nsg_name     = "${local.common_name}-mysql-nsg"

  # Load Balancer locals
  lb_display_name    = "${var.load_balancer_display_name}-${var.environment}"
  lb_nsg_name        = "${local.common_name}-lb-nsg"
  lb_cert_name       = "${local.common_name}-cert"
  lb_hostname        = "${var.project_name}.example.com"

  # Monitoring and logging
  enable_monitoring  = var.environment == "production" ? true : false
  log_retention_days = var.environment == "production" ? 90 : 30

  # Resource naming prefixes by component
  compute_names = {
    for i in range(var.compute_instance_count) :
    i => {
      display_name = "${local.compute_display_name_prefix}-${i + 1}"
      private_ip   = "10.0.2.${10 + i}"
    }
  }

  # Security considerations
  enable_public_ip_on_compute = false  # Compute instances use private IPs
  enable_nat_gateway          = false  # Optional: Enable for internet access from private subnet

  # Database connection details (for outputs and scripts)
  database_config = {
    hostname = oci_mysql_mysql_db_system.yawl_db.endpoints[0].hostname
    port     = oci_mysql_mysql_db_system.yawl_db.port
    database = var.mysql_db_name
    username = var.mysql_admin_username
  }

  # Load balancer configuration
  lb_config = {
    public_ip        = oci_load_balancer_load_balancer.main.ip_address_details[0].ip_address
    http_port        = 80
    https_port       = 443
    backend_port     = var.lb_backend_port
    health_check_url = "/health"
  }
}
