#------------------------------------------------------------------------------
# YAWL AWS Module - EKS, RDS, S3
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# RDS PostgreSQL
#------------------------------------------------------------------------------

resource "random_password" "db_password" {
  length  = 32
  special = false
}

resource "aws_kms_key" "rds" {
  count                   = var.database_encryption && var.kms_key_id == "" ? 1 : 0
  description             = "KMS key for RDS encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-rds-kms"
  })
}

resource "aws_db_subnet_group" "yawl" {
  name       = "${local.name_prefix}-db-subnet"
  subnet_ids = var.private_subnet_ids

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-db-subnet-group"
  })
}

resource "aws_rds_cluster" "yawl" {
  count                      = var.database_engine == "aurora-postgresql" ? 1 : 0
  engine                     = "aurora-postgresql"
  engine_version             = var.database_version
  database_name              = var.project_name
  master_username            = var.database_username
  master_password            = random_password.db_password.result
  db_subnet_group_name       = aws_db_subnet_group.yawl.name
  vpc_security_group_ids     = [aws_security_group.rds.id]
  storage_encrypted          = var.database_encryption
  kms_key_id                 = var.kms_key_id != "" ? var.kms_key_id : try(aws_kms_key.rds[0].key_id, null)
  deletion_protection        = var.database_deletion_protection
  skip_final_snapshot        = var.environment == "dev"
  final_snapshot_identifier  = "${local.name_prefix}-final-snapshot"
  backup_retention_period    = var.database_backup_days
  preferred_backup_window    = "03:00-05:00"
  preferred_maintenance_window = "sun:05:00-sun:07:00"

  enable_http_endpoint = true

  serverlessv2_scaling_configuration {
    min_capacity = 0.5
    max_capacity = 16
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-aurora-cluster"
  })
}

resource "aws_rds_cluster_instance" "yawl" {
  count                = var.database_engine == "aurora-postgresql" ? (var.database_multi_az ? 2 : 1) : 0
  identifier           = "${local.name_prefix}-aurora-${count.index + 1}"
  cluster_identifier   = aws_rds_cluster.yawl[0].id
  instance_class       = "db.serverless"
  engine               = aws_rds_cluster.yawl[0].engine
  engine_version       = aws_rds_cluster.yawl[0].engine_version
  performance_insights_enabled = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-aurora-instance-${count.index + 1}"
  })
}

resource "aws_db_instance" "yawl" {
  count                      = var.database_engine == "postgresql" ? 1 : 0
  identifier                 = "${local.name_prefix}-postgres"
  engine                     = "postgres"
  engine_version             = var.database_version
  instance_class             = var.database_instance_class
  allocated_storage          = var.database_storage_gb
  storage_type               = "gp3"
  storage_encrypted          = var.database_encryption
  kms_key_id                 = var.kms_key_id != "" ? var.kms_key_id : try(aws_kms_key.rds[0].key_id, null)
  db_name                    = var.project_name
  username                   = var.database_username
  password                   = random_password.db_password.result
  db_subnet_group_name       = aws_db_subnet_group.yawl.name
  vpc_security_group_ids     = [aws_security_group.rds.id]
  multi_az                   = var.database_multi_az
  deletion_protection        = var.database_deletion_protection
  skip_final_snapshot        = var.environment == "dev"
  final_snapshot_identifier  = "${local.name_prefix}-final-snapshot"
  backup_retention_period    = var.database_backup_days
  backup_window              = "03:00-05:00"
  maintenance_window         = "sun:05:00-sun:07:00"
  performance_insights_enabled = true
  monitoring_interval        = 60
  monitoring_role_arn        = aws_iam_role.rds_enhanced_monitoring.arn

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-postgres"
  })
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Security group for RDS database"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-rds-sg"
  })
}

resource "aws_iam_role" "rds_enhanced_monitoring" {
  name = "${local.name_prefix}-rds-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "rds_enhanced_monitoring" {
  role       = aws_iam_role.rds_enhanced_monitoring.name
  policy_arn = "arn:aws:iam:aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

#------------------------------------------------------------------------------
# EKS Cluster
#------------------------------------------------------------------------------

resource "aws_eks_cluster" "yawl" {
  name     = "${local.name_prefix}-cluster"
  version  = var.kubernetes_version
  role_arn = aws_iam_role.eks_cluster.arn

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_private_access = true
    endpoint_public_access  = !var.enable_private_endpoint
    security_group_ids      = [aws_security_group.eks_cluster.id]
  }

  encryption_config {
    provider {
      keyarn = var.kms_key_id != "" ? var.kms_key_id : aws_kms_key.eks.key_id
    }
    resources = ["secrets"]
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-eks-cluster"
  })
}

resource "aws_kms_key" "eks" {
  description             = "KMS key for EKS secrets encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-eks-kms"
  })
}

resource "aws_iam_role" "eks_cluster" {
  name = "${local.name_prefix}-eks-cluster"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "eks.amazonaws.com"
        }
      }
    ]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "eks_cluster" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

resource "aws_iam_role_policy_attachment" "eks_cluster_vpc" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.eks_cluster.name
}

resource "aws_security_group" "eks_cluster" {
  name        = "${local.name_prefix}-eks-cluster-sg"
  description = "Security group for EKS cluster control plane"
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-eks-cluster-sg"
  })
}

#------------------------------------------------------------------------------
# EKS Node Group
#------------------------------------------------------------------------------

resource "aws_iam_role" "eks_nodes" {
  name = "${local.name_prefix}-eks-nodes"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "eks_nodes" {
  for_each = toset([
    "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy",
    "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
    "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  ])

  policy_arn = each.value
  role       = aws_iam_role.eks_nodes.name
}

resource "aws_eks_node_group" "yawl" {
  cluster_name    = aws_eks_cluster.yawl.name
  node_group_name = "${local.name_prefix}-nodes"
  node_role_arn   = aws_iam_role.eks_nodes.arn
  subnet_ids      = var.private_subnet_ids

  scaling_config {
    desired_size = var.node_count_min
    min_size     = var.node_count_min
    max_size     = var.node_count_max
  }

  update_config {
    max_unavailable_percentage = 33
  }

  instance_types = [var.node_instance_type]
  disk_size      = var.node_disk_size_gb
  ami_type       = var.node_ami_type

  labels = merge({
    environment = var.environment
  }, var.node_pool_labels)

  dynamic "taint" {
    for_each = var.node_pool_taints
    content {
      key    = taint.value.key
      value  = taint.value.value
      effect = taint.value.effect
    }
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-eks-node-group"
  })

  depends_on = [
    aws_iam_role_policy_attachment.eks_nodes,
    aws_eks_cluster.yawl
  ]

  lifecycle {
    ignore_changes = [scaling_config[0].desired_size]
  }
}

resource "aws_security_group" "eks_nodes" {
  name        = "${local.name_prefix}-eks-nodes-sg"
  description = "Security group for EKS worker nodes"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-eks-nodes-sg"
  })
}

#------------------------------------------------------------------------------
# S3 Buckets
#------------------------------------------------------------------------------

resource "aws_s3_bucket" "yawl" {
  for_each = toset(var.storage_bucket_names)

  bucket = "${local.name_prefix}-${each.value}"

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-${each.value}"
  })
}

resource "aws_s3_bucket_versioning" "yawl" {
  for_each = aws_s3_bucket.yawl

  bucket = each.value.id

  versioning_configuration {
    status = var.storage_versioning ? "Enabled" : "Suspended"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "yawl" {
  for_each = aws_s3_bucket.yawl

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.database_encryption ? "aws:kms" : "AES256"
      kms_master_key_id = var.kms_key_id != "" ? var.kms_key_id : null
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "yawl" {
  for_each = aws_s3_bucket.yawl

  bucket = each.value.id

  rule {
    id     = "transition-to-ia"
    status = "Enabled"

    transition {
      days          = var.storage_lifecycle_days
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = var.storage_archive_days
      storage_class = "GLACIER"
    }

    noncurrent_version_transition {
      noncurrent_days = 30
      storage_class   = "STANDARD_IA"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "yawl" {
  for_each = aws_s3_bucket.yawl

  bucket = each.value.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "cluster_name" {
  value = aws_eks_cluster.yawl.name
}

output "cluster_endpoint" {
  value     = aws_eks_cluster.yawl.endpoint
  sensitive = true
}

output "cluster_ca_certificate" {
  value     = aws_eks_cluster.yawl.certificate_authority[0].data
  sensitive = true
}

output "kubectl_config_command" {
  value = "aws eks update-kubeconfig --name ${aws_eks_cluster.yawl.name} --region ${var.aws_region}"
}

output "rds_endpoint" {
  value = var.database_engine == "aurora-postgresql" ? aws_rds_cluster.yawl[0].endpoint : aws_db_instance.yawl[0].endpoint
}

output "rds_port" {
  value = 5432
}

output "bucket_arns" {
  value = { for k, v in aws_s3_bucket.yawl : k => v.arn }
}

output "bucket_urls" {
  value = { for k, v in aws_s3_bucket.yawl : k => v.bucket }
}
