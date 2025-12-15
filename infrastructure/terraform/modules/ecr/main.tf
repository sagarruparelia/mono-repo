# =============================================================================
# ECR Module - Container Registries for Monorepo Applications
# =============================================================================

terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }
}

# -----------------------------------------------------------------------------
# Local Variables
# -----------------------------------------------------------------------------
locals {
  # Define all repositories for the monorepo
  repositories = {
    bff         = "${var.project}/bff"
    web-cl      = "${var.project}/web-cl"
    web-hs      = "${var.project}/web-hs"
    mfe-summary = "${var.project}/mfe-summary"
    mfe-profile = "${var.project}/mfe-profile"
  }
}

# -----------------------------------------------------------------------------
# ECR Repositories
# -----------------------------------------------------------------------------
resource "aws_ecr_repository" "repos" {
  for_each = local.repositories

  name                 = each.value
  image_tag_mutability = var.image_tag_mutability

  encryption_configuration {
    encryption_type = var.kms_key_arn != null ? "KMS" : "AES256"
    kms_key         = var.kms_key_arn
  }

  image_scanning_configuration {
    scan_on_push = var.scan_on_push
  }

  tags = merge(var.tags, {
    Name      = each.value
    Component = each.key
  })
}

# -----------------------------------------------------------------------------
# Lifecycle Policies - Cleanup Old Images
# -----------------------------------------------------------------------------
resource "aws_ecr_lifecycle_policy" "repos" {
  for_each   = aws_ecr_repository.repos
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last ${var.production_image_count} production images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["prod-", "v", "release-"]
          countType     = "imageCountMoreThan"
          countNumber   = var.production_image_count
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep last ${var.staging_image_count} staging images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["staging-", "stg-"]
          countType     = "imageCountMoreThan"
          countNumber   = var.staging_image_count
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 3
        description  = "Keep last ${var.dev_image_count} dev images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["dev-", "pr-"]
          countType     = "imageCountMoreThan"
          countNumber   = var.dev_image_count
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 4
        description  = "Keep last ${var.sha_image_count} SHA-tagged images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["sha-"]
          countType     = "imageCountMoreThan"
          countNumber   = var.sha_image_count
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 10
        description  = "Remove untagged images after ${var.untagged_image_days} days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.untagged_image_days
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# -----------------------------------------------------------------------------
# Repository Policy for Cross-Account Access (if needed)
# -----------------------------------------------------------------------------
resource "aws_ecr_repository_policy" "cross_account" {
  for_each   = var.cross_account_arns != null ? aws_ecr_repository.repos : {}
  repository = each.value.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCrossAccountPull"
        Effect = "Allow"
        Principal = {
          AWS = var.cross_account_arns
        }
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability"
        ]
      }
    ]
  })
}
