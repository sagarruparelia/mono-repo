# =============================================================================
# ElastiCache Module Variables
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

variable "subnet_ids" {
  description = "List of subnet IDs for ElastiCache"
  type        = list(string)
}

variable "eks_node_security_group_id" {
  description = "EKS node security group ID for ingress rules"
  type        = string
}

variable "valkey_version" {
  description = "Valkey engine version (MUST be 8.2+ for CVE-2025-49844 fix)"
  type        = string
  default     = "8.2"

  validation {
    condition     = tonumber(split(".", var.valkey_version)[1]) >= 2
    error_message = "Valkey version must be 8.2 or higher due to CVE-2025-49844."
  }
}

variable "node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t3.micro"
}

variable "num_cache_clusters" {
  description = "Number of cache clusters (nodes) in the replication group"
  type        = number
  default     = 3
}

variable "auth_token" {
  description = "Auth token for Redis AUTH"
  type        = string
  sensitive   = true
}

variable "maintenance_window" {
  description = "Maintenance window"
  type        = string
  default     = "sun:05:00-sun:06:00"
}

variable "snapshot_window" {
  description = "Snapshot window"
  type        = string
  default     = "04:00-05:00"
}

variable "log_retention_days" {
  description = "CloudWatch log retention days"
  type        = number
  default     = 30
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default     = {}
}
