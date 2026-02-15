# Network Configuration
# VPC, Subnets, Security Groups for Teradata Vantage

# =============================================================================
# VPC Configuration
# =============================================================================

resource "aws_vpc" "teradata" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

resource "aws_vpc_ipv4_cidr_block_association" "secondary" {
  count      = var.enable_secondary_cidr ? 1 : 0
  vpc_id     = aws_vpc.teradata.id
  cidr_block = var.secondary_vpc_cidr
}

# =============================================================================
# Subnets
# =============================================================================

# Primary subnet in first AZ
resource "aws_subnet" "primary" {
  vpc_id                  = aws_vpc.teradata.id
  cidr_block              = var.primary_subnet_cidr
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.name_prefix}-primary-subnet"
    Tier = "Private"
  }
}

# Secondary subnet in second AZ
resource "aws_subnet" "secondary" {
  vpc_id                  = aws_vpc.teradata.id
  cidr_block              = var.secondary_subnet_cidr
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.name_prefix}-secondary-subnet"
    Tier = "Private"
  }
}

# Public subnet for bastion (if enabled)
resource "aws_subnet" "public" {
  count                   = var.enable_bastion ? 1 : 0
  vpc_id                  = aws_vpc.teradata.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-subnet"
    Tier = "Public"
  }
}

# =============================================================================
# Internet Gateway (for public subnet)
# =============================================================================

resource "aws_internet_gateway" "main" {
  count  = var.enable_bastion ? 1 : 0
  vpc_id = aws_vpc.teradata.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

# =============================================================================
# NAT Gateway (for private subnet egress)
# =============================================================================

resource "aws_eip" "nat" {
  count  = var.enable_nat_gateway ? 1 : 0
  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-nat-eip"
  }
}

resource "aws_nat_gateway" "main" {
  count         = var.enable_nat_gateway ? 1 : 0
  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name = "${local.name_prefix}-nat-gateway"
  }

  depends_on = [aws_internet_gateway.main]
}

# =============================================================================
# Route Tables
# =============================================================================

# Private route table
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.teradata.id

  # Route to NAT gateway if enabled
  dynamic "route" {
    for_each = var.enable_nat_gateway ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = aws_nat_gateway.main[0].id
    }
  }

  tags = {
    Name = "${local.name_prefix}-private-rt"
  }
}

# Public route table
resource "aws_route_table" "public" {
  count  = var.enable_bastion ? 1 : 0
  vpc_id = aws_vpc.teradata.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main[0].id
  }

  tags = {
    Name = "${local.name_prefix}-public-rt"
  }
}

# Route table associations
resource "aws_route_table_association" "primary" {
  subnet_id      = aws_subnet.primary.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "secondary" {
  subnet_id      = aws_subnet.secondary.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "public" {
  count          = var.enable_bastion ? 1 : 0
  subnet_id      = aws_subnet.public[0].id
  route_table_id = aws_route_table.public[0].id
}

# =============================================================================
# Security Groups
# =============================================================================

# Teradata Vantage security group
resource "aws_security_group" "teradata" {
  name        = "${local.name_prefix}-teradata-sg"
  description = "Security group for Teradata Vantage"
  vpc_id      = aws_vpc.teradata.id

  # JDBC access from YAWL engine
  ingress {
    description     = "Teradata JDBC"
    from_port       = 1025
    to_port         = 1025
    protocol        = "tcp"
    security_groups = var.yawl_engine_security_group_ids
  }

  # HTTPS for console/API
  ingress {
    description = "HTTPS Console"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.allowed_console_cidr_blocks
  }

  # Teradata Studio
  ingress {
    description     = "Teradata Studio"
    from_port       = 9047
    to_port         = 9047
    protocol        = "tcp"
    security_groups = var.admin_security_group_ids
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-teradata-sg"
  }
}

# YAWL Engine security group
resource "aws_security_group" "yawl_engine" {
  name        = "${local.name_prefix}-yawl-engine-sg"
  description = "Security group for YAWL Engine"
  vpc_id      = aws_vpc.teradata.id

  # Application traffic
  ingress {
    description = "YAWL Engine HTTP"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = var.allowed_application_cidr_blocks
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-yawl-engine-sg"
  }
}

# Bastion host security group
resource "aws_security_group" "bastion" {
  count       = var.enable_bastion ? 1 : 0
  name        = "${local.name_prefix}-bastion-sg"
  description = "Security group for Bastion host"
  vpc_id      = aws_vpc.teradata.id

  # SSH access from allowed IPs
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.allowed_ssh_cidr_blocks
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-bastion-sg"
  }
}

# =============================================================================
# Network ACLs (Additional Layer)
# =============================================================================

resource "aws_network_acl" "private" {
  vpc_id     = aws_vpc.teradata.id
  subnet_ids = [aws_subnet.primary.id, aws_subnet.secondary.id]

  # Allow all inbound from VPC
  ingress {
    protocol   = -1
    rule_no    = 100
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 0
  }

  # Allow all outbound
  egress {
    protocol   = -1
    rule_no    = 100
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }

  tags = {
    Name = "${local.name_prefix}-private-nacl"
  }
}

# =============================================================================
# VPC Flow Logs
# =============================================================================

resource "aws_flow_log" "main" {
  iam_role_arn    = aws_iam_role.flow_log.arn
  log_destination = aws_cloudwatch_log_group.flow_log.arn
  traffic_type    = "ALL"
  vpc_id          = aws_vpc.teradata.id

  tags = {
    Name = "${local.name_prefix}-flow-log"
  }
}

resource "aws_cloudwatch_log_group" "flow_log" {
  name              = "/aws/vpc-flow-logs/${local.name_prefix}"
  retention_in_days = 30

  tags = {
    Name = "${local.name_prefix}-flow-log-group"
  }
}

resource "aws_iam_role" "flow_log" {
  name = "${local.name_prefix}-flow-log-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "vpc-flow-logs.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "flow_log" {
  name = "${local.name_prefix}-flow-log-policy"
  role = aws_iam_role.flow_log.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogGroups",
          "logs:DescribeLogStreams"
        ]
        Effect   = "Allow"
        Resource = "*"
      }
    ]
  })
}
