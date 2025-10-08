# Terraform Outputs for AWS CardDemo Modernized Application
# Exposes critical infrastructure endpoints and configuration values for Kubernetes deployment

# =========================================================================
# EKS Cluster Outputs
# =========================================================================

output "cluster_endpoint" {
  description = "Kubernetes API server endpoint for kubectl configuration"
  value       = aws_eks_cluster.carddemo.endpoint
}

output "cluster_certificate_authority_data" {
  description = "Certificate authority data for secure cluster API access"
  value       = aws_eks_cluster.carddemo.certificate_authority[0].data
}

output "cluster_name" {
  description = "EKS cluster name for kubectl context configuration and resource tagging"
  value       = aws_eks_cluster.carddemo.name
}

output "cluster_security_group_id" {
  description = "Security group ID for EKS cluster control plane"
  value       = aws_eks_cluster.carddemo.vpc_config[0].cluster_security_group_id
}

output "cluster_arn" {
  description = "EKS cluster Amazon Resource Name for IAM policy resource references"
  value       = aws_eks_cluster.carddemo.arn
}

output "cluster_version" {
  description = "Kubernetes version running on the EKS cluster"
  value       = aws_eks_cluster.carddemo.version
}

# =========================================================================
# EKS Node Group Outputs
# =========================================================================

output "node_group_id" {
  description = "Managed node group identifier for autoscaling configuration and monitoring"
  value       = aws_eks_node_group.carddemo.id
}

output "node_group_status" {
  description = "Current operational status of worker nodes (ACTIVE, UPDATING, DEGRADED)"
  value       = aws_eks_node_group.carddemo.status
}

output "node_security_group_id" {
  description = "Security group ID for EKS worker nodes and application LoadBalancer configuration"
  value       = aws_security_group.eks_nodes.id
}

output "node_group_arn" {
  description = "Node group Amazon Resource Name for IAM policy references"
  value       = aws_eks_node_group.carddemo.arn
}

output "node_role_arn" {
  description = "IAM role ARN for EKS worker nodes"
  value       = aws_iam_role.eks_node_group.arn
}

# =========================================================================
# RDS PostgreSQL Database Outputs
# =========================================================================

output "rds_endpoint" {
  description = "RDS instance endpoint hostname for JDBC connection strings (format: hostname:port)"
  value       = aws_db_instance.carddemo.endpoint
}

output "rds_address" {
  description = "RDS instance hostname without port (for k8s ConfigMap DB_HOST)"
  value       = aws_db_instance.carddemo.address
}

output "rds_port" {
  description = "PostgreSQL listener port for database connection configuration"
  value       = aws_db_instance.carddemo.port
}

output "rds_database_name" {
  description = "Initial database name created in RDS instance (for JDBC URL and k8s ConfigMap DB_NAME)"
  value       = aws_db_instance.carddemo.db_name
}

output "rds_username" {
  description = "Master database username for admin access (for k8s Secret DB_USER)"
  value       = aws_db_instance.carddemo.username
  sensitive   = true
}

output "rds_arn" {
  description = "RDS instance Amazon Resource Name for IAM database authentication and AWS Secrets Manager"
  value       = aws_db_instance.carddemo.arn
}

output "rds_resource_id" {
  description = "DBI resource identifier for CloudWatch enhanced monitoring and Performance Insights"
  value       = aws_db_instance.carddemo.resource_id
}

output "rds_availability_zone" {
  description = "Availability zone where the RDS instance is deployed"
  value       = aws_db_instance.carddemo.availability_zone
}

output "rds_engine_version_actual" {
  description = "Actual PostgreSQL engine version running on RDS instance"
  value       = aws_db_instance.carddemo.engine_version_actual
}

# =========================================================================
# VPC Networking Outputs
# =========================================================================

output "vpc_id" {
  description = "VPC identifier for additional resource provisioning and security group creation"
  value       = aws_vpc.carddemo.id
}

output "vpc_cidr_block" {
  description = "VPC CIDR block for network planning and security rule configuration"
  value       = aws_vpc.carddemo.cidr_block
}

output "public_subnet_ids" {
  description = "Public subnet IDs for load balancers, NAT gateways, and Ingress controllers"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs for EKS worker nodes and RDS database instances"
  value       = aws_subnet.private[*].id
}

output "nat_gateway_ids" {
  description = "NAT gateway identifiers for private subnet routing and network troubleshooting"
  value       = aws_nat_gateway.carddemo[*].id
}

output "internet_gateway_id" {
  description = "Internet gateway ID for public subnet routing"
  value       = aws_internet_gateway.carddemo.id
}

# =========================================================================
# Security Group Outputs
# =========================================================================

output "rds_security_group_id" {
  description = "RDS database security group ID for security rule auditing and compliance validation"
  value       = aws_security_group.rds.id
}

output "alb_security_group_id" {
  description = "Application Load Balancer security group ID (if created)"
  value       = try(aws_security_group.alb[0].id, "")
}

# =========================================================================
# CloudWatch Logging Outputs
# =========================================================================

output "eks_log_group_name" {
  description = "CloudWatch Logs group for Kubernetes API logs and CloudWatch Insights queries"
  value       = aws_cloudwatch_log_group.eks.name
}

output "eks_log_group_arn" {
  description = "CloudWatch Logs group ARN for IAM policy configuration"
  value       = aws_cloudwatch_log_group.eks.arn
}

output "rds_log_group_name" {
  description = "CloudWatch Logs group for PostgreSQL database logs and query performance analysis"
  value       = "/aws/rds/instance/${aws_db_instance.carddemo.identifier}/postgresql"
}

# =========================================================================
# IAM Role Outputs
# =========================================================================

output "cluster_role_arn" {
  description = "IAM role ARN for EKS cluster control plane"
  value       = aws_iam_role.eks_cluster.arn
}

output "cluster_autoscaler_role_arn" {
  description = "IAM role ARN for Cluster Autoscaler service account (if using IRSA)"
  value       = try(aws_iam_role.cluster_autoscaler[0].arn, "")
}

# =========================================================================
# Configuration Helper Outputs
# =========================================================================

output "kubectl_config_command" {
  description = "AWS CLI command to configure kubectl with cluster credentials"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.carddemo.name}"
}

output "connection_string" {
  description = "PostgreSQL JDBC connection string template for application.yml spring.datasource.url"
  value       = "jdbc:postgresql://${aws_db_instance.carddemo.address}:${aws_db_instance.carddemo.port}/${aws_db_instance.carddemo.db_name}"
}

output "connection_string_with_ssl" {
  description = "PostgreSQL JDBC connection string with SSL enforcement for production"
  value       = "jdbc:postgresql://${aws_db_instance.carddemo.address}:${aws_db_instance.carddemo.port}/${aws_db_instance.carddemo.db_name}?sslmode=require"
}

output "kubeconfig_context_name" {
  description = "kubectl context name for multi-cluster management and CI/CD environment targeting"
  value       = "arn:aws:eks:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster/${aws_eks_cluster.carddemo.name}"
}

# =========================================================================
# Resource Tagging Outputs
# =========================================================================

output "common_tags" {
  description = "Common tags applied to all resources for cost allocation and resource management"
  value       = var.tags
}

# =========================================================================
# Kubernetes ConfigMap Values (for k8s/configmap.yml)
# =========================================================================

output "k8s_configmap_values" {
  description = "Key-value pairs for Kubernetes ConfigMap generation"
  value = {
    DB_HOST              = aws_db_instance.carddemo.address
    DB_PORT              = tostring(aws_db_instance.carddemo.port)
    DB_NAME              = aws_db_instance.carddemo.db_name
    AWS_REGION           = var.aws_region
    CLUSTER_NAME         = aws_eks_cluster.carddemo.name
    ENVIRONMENT          = var.environment
    APP_NAME             = var.app_name
    LOG_GROUP_NAME       = aws_cloudwatch_log_group.eks.name
  }
}

# =========================================================================
# Kubernetes Secret Values (for k8s/secret.yml) - Sensitive
# =========================================================================

output "k8s_secret_values" {
  description = "Sensitive key-value pairs for Kubernetes Secret generation (create as sealed secret)"
  value = {
    DB_USER     = aws_db_instance.carddemo.username
    DB_PASSWORD = var.db_password
  }
  sensitive = true
}

# =========================================================================
# Infrastructure Summary
# =========================================================================

output "infrastructure_summary" {
  description = "Complete infrastructure summary for documentation and deployment guides"
  value = {
    eks_cluster = {
      name                = aws_eks_cluster.carddemo.name
      endpoint            = aws_eks_cluster.carddemo.endpoint
      version             = aws_eks_cluster.carddemo.version
      security_group_id   = aws_eks_cluster.carddemo.vpc_config[0].cluster_security_group_id
    }
    eks_nodes = {
      node_group_id       = aws_eks_node_group.carddemo.id
      status              = aws_eks_node_group.carddemo.status
      security_group_id   = aws_security_group.eks_nodes.id
      desired_capacity    = aws_eks_node_group.carddemo.scaling_config[0].desired_size
      min_size            = aws_eks_node_group.carddemo.scaling_config[0].min_size
      max_size            = aws_eks_node_group.carddemo.scaling_config[0].max_size
      instance_types      = aws_eks_node_group.carddemo.instance_types
    }
    rds_database = {
      endpoint            = aws_db_instance.carddemo.endpoint
      address             = aws_db_instance.carddemo.address
      port                = aws_db_instance.carddemo.port
      database_name       = aws_db_instance.carddemo.db_name
      engine_version      = aws_db_instance.carddemo.engine_version_actual
      multi_az            = aws_db_instance.carddemo.multi_az
      storage_encrypted   = aws_db_instance.carddemo.storage_encrypted
      backup_retention    = aws_db_instance.carddemo.backup_retention_period
    }
    networking = {
      vpc_id              = aws_vpc.carddemo.id
      vpc_cidr            = aws_vpc.carddemo.cidr_block
      public_subnets      = aws_subnet.public[*].id
      private_subnets     = aws_subnet.private[*].id
      nat_gateways        = aws_nat_gateway.carddemo[*].id
    }
  }
}

# =========================================================================
# AWS Account Information
# =========================================================================

data "aws_caller_identity" "current" {}

output "aws_account_id" {
  description = "AWS account ID where infrastructure is deployed"
  value       = data.aws_caller_identity.current.account_id
}

output "aws_region" {
  description = "AWS region where infrastructure is deployed"
  value       = var.aws_region
}

# =========================================================================
# Deployment Instructions
# =========================================================================

output "deployment_instructions" {
  description = "Quick start deployment instructions for developers"
  value = <<-EOT
    ========================================================================
    AWS CardDemo Modernized - Infrastructure Deployed Successfully
    ========================================================================
    
    1. Configure kubectl:
       ${format("aws eks update-kubeconfig --region %s --name %s", var.aws_region, aws_eks_cluster.carddemo.name)}
    
    2. Verify cluster access:
       kubectl get nodes
    
    3. Create Kubernetes namespace:
       kubectl create namespace carddemo
    
    4. Create database secret (replace <password> with actual password):
       kubectl create secret generic db-credentials \
         --namespace=carddemo \
         --from-literal=username=${aws_db_instance.carddemo.username} \
         --from-literal=password=<password>
    
    5. Update k8s/configmap.yml with database endpoint:
       DB_HOST: ${aws_db_instance.carddemo.address}
       DB_PORT: "${aws_db_instance.carddemo.port}"
       DB_NAME: ${aws_db_instance.carddemo.db_name}
    
    6. Deploy application:
       kubectl apply -f k8s/
    
    7. Verify deployment:
       kubectl get pods -n carddemo
       kubectl get services -n carddemo
    
    ========================================================================
    Database Connection String:
    jdbc:postgresql://${aws_db_instance.carddemo.address}:${aws_db_instance.carddemo.port}/${aws_db_instance.carddemo.db_name}?sslmode=require
    ========================================================================
  EOT
}
