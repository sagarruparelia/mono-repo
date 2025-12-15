# =============================================================================
# ECR Module Outputs
# =============================================================================

output "repository_urls" {
  description = "Map of repository names to their URLs"
  value       = { for k, v in aws_ecr_repository.repos : k => v.repository_url }
}

output "repository_arns" {
  description = "Map of repository names to their ARNs"
  value       = { for k, v in aws_ecr_repository.repos : k => v.arn }
}

output "registry_id" {
  description = "The registry ID where repositories are created"
  value       = values(aws_ecr_repository.repos)[0].registry_id
}

output "registry_url" {
  description = "The registry URL (account_id.dkr.ecr.region.amazonaws.com)"
  value       = split("/", values(aws_ecr_repository.repos)[0].repository_url)[0]
}
