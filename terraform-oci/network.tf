# Virtual Cloud Network (VCN)
resource "oci_core_vcn" "main" {
  compartment_id = var.compartment_ocid
  cidr_blocks    = [var.vcn_cidr]
  display_name   = "${var.project_name}-vcn-${var.environment}"
  dns_label      = "${replace(var.project_name, "-", "")}${replace(var.environment, "-", "")}"

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-vcn-${var.environment}"
    }
  )
}

# Internet Gateway
resource "oci_core_internet_gateway" "main" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-igw-${var.environment}"
  enabled        = true

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-igw-${var.environment}"
    }
  )
}

# Route Table for Public Subnet
resource "oci_core_route_table" "public_route_table" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-public-rt-${var.environment}"

  route_rules {
    cidr_block        = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.main.id
  }

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-public-rt-${var.environment}"
    }
  )
}

# Public Subnet
resource "oci_core_subnet" "public_subnet" {
  compartment_id      = var.compartment_ocid
  vcn_id              = oci_core_vcn.main.id
  cidr_block          = var.public_subnet_cidr
  display_name        = "${var.project_name}-public-subnet-${var.environment}"
  dns_label           = "public${replace(var.environment, "-", "")}"
  route_table_id      = oci_core_route_table.public_route_table.id
  security_list_ids   = [oci_core_security_list.public_security_list.id]
  map_public_ip_on_launch = true

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-public-subnet-${var.environment}"
    }
  )
}

# Route Table for Private Subnet
resource "oci_core_route_table" "private_route_table" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-private-rt-${var.environment}"

  # No route rules - private subnet with no internet access for security
  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-private-rt-${var.environment}"
    }
  )
}

# Private Subnet for Application Servers
resource "oci_core_subnet" "private_subnet" {
  compartment_id      = var.compartment_ocid
  vcn_id              = oci_core_vcn.main.id
  cidr_block          = var.private_subnet_cidr
  display_name        = "${var.project_name}-private-subnet-${var.environment}"
  dns_label           = "private${replace(var.environment, "-", "")}"
  route_table_id      = oci_core_route_table.private_route_table.id
  security_list_ids   = [oci_core_security_list.private_security_list.id]
  map_public_ip_on_launch = false

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-private-subnet-${var.environment}"
    }
  )
}

# Database Subnet
resource "oci_core_subnet" "database_subnet" {
  compartment_id      = var.compartment_ocid
  vcn_id              = oci_core_vcn.main.id
  cidr_block          = var.database_subnet_cidr
  display_name        = "${var.project_name}-db-subnet-${var.environment}"
  dns_label           = "db${replace(var.environment, "-", "")}"
  route_table_id      = oci_core_route_table.private_route_table.id
  security_list_ids   = [oci_core_security_list.database_security_list.id]
  map_public_ip_on_launch = false

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-db-subnet-${var.environment}"
    }
  )
}

# Public Security List
resource "oci_core_security_list" "public_security_list" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-public-sl-${var.environment}"

  # Ingress rules for public subnet
  ingress_security_rules {
    protocol    = "6"  # TCP
    source      = "0.0.0.0/0"
    destination_port_range {
      min = 80
      max = 80
    }
    description = "Allow HTTP"
  }

  ingress_security_rules {
    protocol    = "6"  # TCP
    source      = "0.0.0.0/0"
    destination_port_range {
      min = 443
      max = 443
    }
    description = "Allow HTTPS"
  }

  ingress_security_rules {
    protocol    = "6"  # TCP
    source      = "0.0.0.0/0"
    destination_port_range {
      min = 22
      max = 22
    }
    description = "Allow SSH"
  }

  # Egress rules
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "Allow all outbound traffic"
  }

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-public-sl-${var.environment}"
    }
  )
}

# Private Security List
resource "oci_core_security_list" "private_security_list" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-private-sl-${var.environment}"

  # Ingress rules for private subnet
  ingress_security_rules {
    protocol    = "6"  # TCP
    source      = var.public_subnet_cidr
    destination_port_range {
      min = 8080
      max = 8080
    }
    description = "Allow YAWL app port from public subnet"
  }

  ingress_security_rules {
    protocol    = "6"  # TCP
    source      = var.public_subnet_cidr
    destination_port_range {
      min = 22
      max = 22
    }
    description = "Allow SSH from public subnet"
  }

  ingress_security_rules {
    protocol    = "6"  # TCP
    source      = var.private_subnet_cidr
    destination_port_range {
      min = 0
      max = 65535
    }
    description = "Allow all TCP from private subnet"
  }

  # Egress rules
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "Allow all outbound traffic"
  }

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-private-sl-${var.environment}"
    }
  )
}

# Database Security List
resource "oci_core_security_list" "database_security_list" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-db-sl-${var.environment}"

  # Ingress rules for database subnet
  ingress_security_rules {
    protocol    = "6"  # TCP
    source      = var.private_subnet_cidr
    destination_port_range {
      min = 3306
      max = 3306
    }
    description = "Allow MySQL from private subnet"
  }

  # Egress rules
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "Allow all outbound traffic"
  }

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-db-sl-${var.environment}"
    }
  )
}

# Network Security Group for Load Balancer
resource "oci_core_network_security_group" "lb_nsg" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "${var.project_name}-lb-nsg-${var.environment}"

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.project_name}-lb-nsg-${var.environment}"
    }
  )
}

# Ingress rule for LB NSG - HTTP
resource "oci_core_network_security_group_security_rule" "lb_nsg_http" {
  network_security_group_id = oci_core_network_security_group.lb_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"  # TCP
  source                    = "0.0.0.0/0"
  destination_port_range {
    min = 80
    max = 80
  }
  description = "Allow HTTP to LB"
}

# Ingress rule for LB NSG - HTTPS
resource "oci_core_network_security_group_security_rule" "lb_nsg_https" {
  network_security_group_id = oci_core_network_security_group.lb_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"  # TCP
  source                    = "0.0.0.0/0"
  destination_port_range {
    min = 443
    max = 443
  }
  description = "Allow HTTPS to LB"
}

# Egress rule for LB NSG
resource "oci_core_network_security_group_security_rule" "lb_nsg_egress" {
  network_security_group_id = oci_core_network_security_group.lb_nsg.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  description               = "Allow all outbound from LB"
}
