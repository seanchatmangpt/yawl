#------------------------------------------------------------------------------
# YAWL AWS Module Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.yawl.name
}

output "cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = aws_eks_cluster.yawl.endpoint
  sensitive   = true
}

output "cluster_ca_certificate" {
  description = "EKS cluster CA certificate (base64 encoded)"
  value       = aws_eks_cluster.yawl.certificate_authority[0].data
  sensitive   = true
}

output "kubectl_config_command" {
  description = "Command to configure kubectl for EKS"
  value       = "aws eks update-kubeconfig --name ${aws_eks_cluster.yawl.name} --region ${var.aws_region}"
}

output "rds_endpoint" {
  description = "RDS endpoint"
  value       = var.database_engine == "aurora-postgresql" ? aws_rds_cluster.yawl[0].endpoint : aws_db_instance.yawl[0].endpoint
}

output "rds_port" {
  description = "RDS port"
  value       = 5432
}

output "rds_db_name" {
  description = "RDS database name"
  value       = var.database_engine == "aurora-postgresql" ? aws_rds_cluster.yawl[0].database_name : aws_db_instance.yawl[0].db_name
}

output "rds_username" {
  description = "RDS master username"
  value       = var.database_username
}

output "rds_password" {
  description = "RDS master password"
  value       = random_password.db_password.result
  sensitive   = true
}

output "bucket_arns" {
  description = "S3 bucket ARNs"
  value       = { for k, v in aws_s3_bucket.yawl : k => v.arn }
}

output "bucket_names" {
  description = "S3 bucket names"
  value       = { for k, v in aws_s3_bucket.yawl : k => v.bucket }
}

output "bucket_urls" {
  description = "S3 bucket URLs"
  value       = { for k, v in aws_s3_bucket.yawl : k => "https://${v.bucket}.s3.${var.aws_region}.amazonaws.com" }
}

output "node_group_name" {
  description = "EKS node group name"
  value       = aws_eks_node_group.yawl.node_group_name
}

output "eks_role_arn" {
  description = "EKS cluster role ARN"
  value       = aws_iam_role.eks_cluster.arn
}

output "node_role_arn" {
  description = "EKS node role ARN"
  value       = aws_iam_role.eks_nodes.arn
}

output "yawl_endpoint" {
  description = "YAWL service endpoint"
  value       = "https://${aws_eks_cluster.yawl.endpoint}"
}

output "yawl_ui_endpoint" {
  description = "YAWL UI endpoint"
  value       = "https://${aws_eks_cluster.yawl.endpoint}/yawl"
}

output "lb_dns_name" {
  description = "Load balancer DNS name"
  value       = aws_eks_cluster.yawl.endpoint
}
