# =============================================================================
# ECR Module Variables
# =============================================================================

variable "project" {
  description = "Project name used as prefix for repository names"
  type        = string
}

variable "image_tag_mutability" {
  description = "Tag mutability setting for the repository (MUTABLE or IMMUTABLE)"
  type        = string
  default     = "IMMUTABLE"

  validation {
    condition     = contains(["MUTABLE", "IMMUTABLE"], var.image_tag_mutability)
    error_message = "image_tag_mutability must be either MUTABLE or IMMUTABLE."
  }
}

variable "scan_on_push" {
  description = "Enable vulnerability scanning on image push"
  type        = bool
  default     = true
}

variable "kms_key_arn" {
  description = "ARN of KMS key for encryption. If null, AES256 encryption is used."
  type        = string
  default     = null
}

variable "production_image_count" {
  description = "Number of production images to retain"
  type        = number
  default     = 30
}

variable "staging_image_count" {
  description = "Number of staging images to retain"
  type        = number
  default     = 15
}

variable "dev_image_count" {
  description = "Number of dev/PR images to retain"
  type        = number
  default     = 10
}

variable "sha_image_count" {
  description = "Number of SHA-tagged images to retain"
  type        = number
  default     = 20
}

variable "untagged_image_days" {
  description = "Days to retain untagged images before expiration"
  type        = number
  default     = 7
}

variable "cross_account_arns" {
  description = "List of AWS account ARNs allowed to pull images (for cross-account access)"
  type        = list(string)
  default     = null
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default     = {}
}
