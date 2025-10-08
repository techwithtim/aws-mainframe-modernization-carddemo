# ==============================================================================
# AWS CardDemo Modernized - Terraform Variables
# ==============================================================================
# Terraform input variable definitions for AWS CardDemo infrastructure
# Migrated from: COBOL/CICS mainframe to Java 21 on EKS + RDS PostgreSQL
# ==============================================================================

# ==============================================================================
# AWS Region and Availability Zone Configuration
# ==============================================================================

variable "aws_region" {
  description = "AWS region where resources will be deployed"
  type        = string
  default     = "us-east-1"

  validation {
    condition     = can(regex("^[a-z]{2}-[a-z]+-[0-9]{1}$", var.aws_region))
    error_message = "AWS region must be a valid region identifier (e.g., us-east-1, eu-west-1)."
  }
}

variable "availability_zones" {
  description = "List of availability zones for multi-AZ deployment"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]

  validation {
    condition     = length(var.availability_zones) >= 2
    error_message = "At least 2 availability zones are required for high availability."
  }
}

variable "environment" {
  description = "Environment name for resource naming and sizing (dev, test, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "test", "prod"], var.environment)
    error_message = "Environment must be one of: dev, test, prod."
  }
}

# ==============================================================================
# EKS Cluster Configuration Variables
# ==============================================================================

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "carddemo-eks"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9-]{0,99}$", var.cluster_name))
    error_message = "Cluster name must start with a letter, contain only alphanumeric characters and hyphens, and be 1-100 characters long."
  }
}

variable "kubernetes_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"

  validation {
    condition     = can(regex("^1\\.(2[6-9]|[3-9][0-9])$", var.kubernetes_version))
    error_message = "Kubernetes version must be 1.26 or higher."
  }
}

variable "node_group_desired_capacity" {
  description = "Desired number of worker nodes in the EKS node group"
  type        = number
  default     = 3

  validation {
    condition     = var.node_group_desired_capacity >= 2 && var.node_group_desired_capacity <= 100
    error_message = "Desired capacity must be between 2 and 100 nodes."
  }
}

variable "node_group_min_size" {
  description = "Minimum number of worker nodes for autoscaling"
  type        = number
  default     = 2

  validation {
    condition     = var.node_group_min_size >= 2
    error_message = "Minimum node group size must be at least 2 for high availability."
  }
}

variable "node_group_max_size" {
  description = "Maximum number of worker nodes for autoscaling"
  type        = number
  default     = 10

  validation {
    condition     = var.node_group_max_size >= var.node_group_min_size
    error_message = "Maximum size must be greater than or equal to minimum size."
  }
}

variable "node_instance_types" {
  description = "List of EC2 instance types for EKS worker nodes"
  type        = list(string)
  default     = ["t3.medium"]

  validation {
    condition     = length(var.node_instance_types) > 0
    error_message = "At least one instance type must be specified."
  }
}

variable "enable_cluster_autoscaler" {
  description = "Enable Kubernetes Cluster Autoscaler addon"
  type        = bool
  default     = true
}

variable "cluster_endpoint_public_access" {
  description = "Enable public access to EKS cluster API server endpoint"
  type        = bool
  default     = true
}

variable "cluster_endpoint_private_access" {
  description = "Enable private access to EKS cluster API server endpoint"
  type        = bool
  default     = true
}

# ==============================================================================
# RDS PostgreSQL Database Variables
# ==============================================================================

variable "db_engine_version" {
  description = "PostgreSQL engine version for RDS"
  type        = string
  default     = "15.4"

  validation {
    condition     = can(regex("^1[5-9]\\.[0-9]+$", var.db_engine_version))
    error_message = "PostgreSQL version must be 15.0 or higher."
  }
}

variable "db_instance_class" {
  description = "RDS instance class for database sizing"
  type        = string
  default     = "db.t3.medium"

  validation {
    condition     = can(regex("^db\\.(t3|t4g|r5|r6g|m5|m6g)\\.(micro|small|medium|large|xlarge|2xlarge|4xlarge|8xlarge|12xlarge|16xlarge)$", var.db_instance_class))
    error_message = "Database instance class must be a valid RDS instance type."
  }
}

variable "db_allocated_storage" {
  description = "Initial storage allocation for RDS instance (in GB)"
  type        = number
  default     = 100

  validation {
    condition     = var.db_allocated_storage >= 20 && var.db_allocated_storage <= 65536
    error_message = "Allocated storage must be between 20 and 65536 GB."
  }
}

variable "db_max_allocated_storage" {
  description = "Maximum storage for RDS autoscaling (in GB)"
  type        = number
  default     = 500

  validation {
    condition     = var.db_max_allocated_storage >= var.db_allocated_storage
    error_message = "Maximum allocated storage must be greater than or equal to initial allocated storage."
  }
}

variable "db_name" {
  description = "Name of the default database to create"
  type        = string
  default     = "carddemo"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.db_name))
    error_message = "Database name must start with a letter, contain only alphanumeric characters and underscores, and be 1-63 characters long."
  }
}

variable "db_username" {
  description = "Master username for RDS database"
  type        = string
  default     = "carddemo_admin"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.db_username))
    error_message = "Database username must start with a letter, contain only alphanumeric characters and underscores, and be 1-63 characters long."
  }
}

variable "db_password" {
  description = "Master password for RDS database (must be provided, min 16 characters)"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 16
    error_message = "Database password must be at least 16 characters long."
  }
}

variable "db_port" {
  description = "PostgreSQL port number"
  type        = number
  default     = 5432

  validation {
    condition     = var.db_port >= 1024 && var.db_port <= 65535
    error_message = "Database port must be between 1024 and 65535."
  }
}

variable "db_multi_az" {
  description = "Enable multi-AZ deployment for RDS high availability"
  type        = bool
  default     = true
}

variable "db_backup_retention_period" {
  description = "Number of days to retain automated backups (0-35)"
  type        = number
  default     = 7

  validation {
    condition     = var.db_backup_retention_period >= 0 && var.db_backup_retention_period <= 35
    error_message = "Backup retention period must be between 0 and 35 days."
  }
}

variable "db_backup_window" {
  description = "Preferred backup window for automated backups (UTC)"
  type        = string
  default     = "03:00-04:00"

  validation {
    condition     = can(regex("^([0-1][0-9]|2[0-3]):[0-5][0-9]-([0-1][0-9]|2[0-3]):[0-5][0-9]$", var.db_backup_window))
    error_message = "Backup window must be in HH:MM-HH:MM format."
  }
}

variable "db_maintenance_window" {
  description = "Preferred maintenance window for RDS (UTC)"
  type        = string
  default     = "sun:04:00-sun:05:00"

  validation {
    condition     = can(regex("^(mon|tue|wed|thu|fri|sat|sun):([0-1][0-9]|2[0-3]):[0-5][0-9]-(mon|tue|wed|thu|fri|sat|sun):([0-1][0-9]|2[0-3]):[0-5][0-9]$", var.db_maintenance_window))
    error_message = "Maintenance window must be in ddd:HH:MM-ddd:HH:MM format."
  }
}

variable "db_deletion_protection" {
  description = "Enable deletion protection for RDS instance"
  type        = bool
  default     = true
}

variable "db_storage_encrypted" {
  description = "Enable encryption at rest for RDS storage"
  type        = bool
  default     = true
}

variable "db_performance_insights_enabled" {
  description = "Enable Performance Insights for RDS monitoring"
  type        = bool
  default     = true
}

variable "db_performance_insights_retention_period" {
  description = "Number of days to retain Performance Insights data"
  type        = number
  default     = 7

  validation {
    condition     = contains([7, 731], var.db_performance_insights_retention_period)
    error_message = "Performance Insights retention must be 7 days (free tier) or 731 days (long-term retention)."
  }
}

# ==============================================================================
# VPC Networking Variables
# ==============================================================================

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0))
    error_message = "VPC CIDR must be a valid IPv4 CIDR block."
  }
}

variable "public_subnet_cidrs" {
  description = "List of CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) >= 2
    error_message = "At least 2 public subnets are required for high availability."
  }
}

variable "private_subnet_cidrs" {
  description = "List of CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.20.0/24"]

  validation {
    condition     = length(var.private_subnet_cidrs) >= 2
    error_message = "At least 2 private subnets are required for high availability."
  }
}

variable "enable_nat_gateway" {
  description = "Enable NAT Gateway for private subnet internet access"
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Use a single NAT Gateway for all private subnets (cost optimization for dev/test)"
  type        = bool
  default     = false
}

variable "enable_dns_hostnames" {
  description = "Enable DNS hostnames in the VPC"
  type        = bool
  default     = true
}

variable "enable_dns_support" {
  description = "Enable DNS support in the VPC"
  type        = bool
  default     = true
}

variable "enable_vpc_flow_logs" {
  description = "Enable VPC Flow Logs for network traffic monitoring"
  type        = bool
  default     = true
}

# ==============================================================================
# Security and Access Control Variables
# ==============================================================================

variable "allowed_cidr_blocks" {
  description = "List of CIDR blocks allowed to access the cluster and database"
  type        = list(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = length(var.allowed_cidr_blocks) > 0
    error_message = "At least one CIDR block must be specified."
  }
}

variable "ssl_enforce_mode" {
  description = "SSL enforcement mode for RDS connections (require, verify-ca, verify-full)"
  type        = string
  default     = "require"

  validation {
    condition     = contains(["require", "verify-ca", "verify-full"], var.ssl_enforce_mode)
    error_message = "SSL enforce mode must be one of: require, verify-ca, verify-full."
  }
}

variable "enable_secrets_manager" {
  description = "Store database credentials in AWS Secrets Manager instead of Kubernetes secrets"
  type        = bool
  default     = true
}

# ==============================================================================
# Application Configuration Variables
# ==============================================================================

variable "app_name" {
  description = "Application name for resource tagging and naming"
  type        = string
  default     = "carddemo"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,62}$", var.app_name))
    error_message = "Application name must start with a lowercase letter, contain only lowercase alphanumeric characters and hyphens, and be 1-63 characters long."
  }
}

variable "app_version" {
  description = "Application version for deployment tracking"
  type        = string
  default     = "1.0.0"

  validation {
    condition     = can(regex("^[0-9]+\\.[0-9]+\\.[0-9]+(-[a-zA-Z0-9]+)?$", var.app_version))
    error_message = "Application version must follow semantic versioning (e.g., 1.0.0 or 1.0.0-beta)."
  }
}

variable "max_db_connections_per_pod" {
  description = "Maximum database connections per application pod (for HikariCP pool sizing)"
  type        = number
  default     = 10

  validation {
    condition     = var.max_db_connections_per_pod >= 5 && var.max_db_connections_per_pod <= 50
    error_message = "Maximum database connections per pod must be between 5 and 50."
  }
}

variable "enable_spring_boot_actuator" {
  description = "Enable Spring Boot Actuator endpoints for health checks and metrics"
  type        = bool
  default     = true
}

variable "enable_prometheus_metrics" {
  description = "Enable Prometheus metrics export for monitoring"
  type        = bool
  default     = true
}

# ==============================================================================
# Resource Tagging Variables
# ==============================================================================

variable "tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    Project    = "AWS CardDemo Modernized"
    ManagedBy  = "Terraform"
    Repository = "aws-card-demo-modernized"
  }
}

variable "additional_tags" {
  description = "Additional custom tags to apply to resources"
  type        = map(string)
  default     = {}
}

# ==============================================================================
# Cost Optimization Variables
# ==============================================================================

variable "enable_deletion_protection" {
  description = "Enable deletion protection for critical resources (recommended for production)"
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Skip final DB snapshot on deletion (use true for dev/test, false for prod)"
  type        = bool
  default     = false
}

variable "monitoring_interval" {
  description = "Enhanced monitoring interval for RDS in seconds (0, 1, 5, 10, 15, 30, 60)"
  type        = number
  default     = 60

  validation {
    condition     = contains([0, 1, 5, 10, 15, 30, 60], var.monitoring_interval)
    error_message = "Monitoring interval must be one of: 0, 1, 5, 10, 15, 30, or 60 seconds."
  }
}

variable "enable_auto_minor_version_upgrade" {
  description = "Enable automatic minor version upgrades during maintenance window"
  type        = bool
  default     = true
}

variable "enable_cloudwatch_logs_exports" {
  description = "List of log types to export to CloudWatch Logs (postgresql)"
  type        = list(string)
  default     = ["postgresql", "upgrade"]

  validation {
    condition     = alltrue([for log in var.enable_cloudwatch_logs_exports : contains(["postgresql", "upgrade"], log)])
    error_message = "CloudWatch log exports must be from: postgresql, upgrade."
  }
}

# ==============================================================================
# Backup and Disaster Recovery Variables
# ==============================================================================

variable "enable_cross_region_backup" {
  description = "Enable cross-region automated backups for disaster recovery"
  type        = bool
  default     = false
}

variable "backup_region" {
  description = "AWS region for cross-region backup replication"
  type        = string
  default     = ""
}

variable "enable_snapshot_copy" {
  description = "Enable automatic snapshot copying to another region"
  type        = bool
  default     = false
}

# ==============================================================================
# End of Variables Definition
# ==============================================================================
