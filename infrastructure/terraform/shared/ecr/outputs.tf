# =============================================================================
# Shared ECR Outputs
# =============================================================================

output "repository_urls" {
  description = "Map of repository names to their URLs"
  value       = module.ecr.repository_urls
}

output "repository_arns" {
  description = "Map of repository names to their ARNs"
  value       = module.ecr.repository_arns
}

output "registry_url" {
  description = "The ECR registry URL"
  value       = module.ecr.registry_url
}

output "github_actions_role_arn" {
  description = "ARN of IAM role for GitHub Actions CI"
  value       = aws_iam_role.github_actions_ci.arn
}
