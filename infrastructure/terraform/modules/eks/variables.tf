# =============================================================================
# EKS Module Variables
# =============================================================================

variable "project" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for EKS nodes"
  type        = list(string)
}

variable "kubernetes_version" {
  description = "Kubernetes version"
  type        = string
  default     = "1.31"
}

variable "kms_key_arn" {
  description = "KMS key ARN for secrets encryption"
  type        = string
}

variable "enable_public_access" {
  description = "Enable public API endpoint access"
  type        = bool
  default     = false
}

variable "public_access_cidrs" {
  description = "CIDR blocks allowed to access public endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "enabled_cluster_log_types" {
  description = "List of EKS cluster log types to enable"
  type        = list(string)
  default     = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
}

variable "node_capacity_type" {
  description = "Node capacity type (ON_DEMAND or SPOT)"
  type        = string
  default     = "ON_DEMAND"
}

variable "app_node_instance_types" {
  description = "Instance types for application node group"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_disk_size" {
  description = "Disk size for nodes (GB)"
  type        = number
  default     = 50
}

variable "app_node_desired_size" {
  description = "Desired number of application nodes"
  type        = number
  default     = 2
}

variable "app_node_min_size" {
  description = "Minimum number of application nodes"
  type        = number
  default     = 1
}

variable "app_node_max_size" {
  description = "Maximum number of application nodes"
  type        = number
  default     = 10
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default     = {}
}
