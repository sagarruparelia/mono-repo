# =============================================================================
# Shared ECR Configuration
# ECR repositories are shared across all environments
# =============================================================================

terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }

  backend "s3" {
    # Configure via backend.hcl or CLI
    # bucket         = "mono-repo-terraform-state"
    # key            = "shared/ecr/terraform.tfstate"
    # region         = "us-east-1"
    # encrypt        = true
    # dynamodb_table = "mono-repo-terraform-locks"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
      Component = "ecr"
    }
  }
}

# -----------------------------------------------------------------------------
# ECR Module
# -----------------------------------------------------------------------------
module "ecr" {
  source = "../../modules/ecr"

  project              = var.project
  image_tag_mutability = "IMMUTABLE"
  scan_on_push         = true

  # Image retention settings
  production_image_count = 30
  staging_image_count    = 15
  dev_image_count        = 10
  sha_image_count        = 20
  untagged_image_days    = 7

  tags = var.tags
}

# -----------------------------------------------------------------------------
# IAM Policy for GitHub Actions CI/CD
# -----------------------------------------------------------------------------
data "aws_caller_identity" "current" {}

# GitHub OIDC Provider
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]

  tags = var.tags
}

# IAM Role for GitHub Actions CI
resource "aws_iam_role" "github_actions_ci" {
  name = "${var.project}-github-actions-ci"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github.arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = "repo:${var.github_org}/${var.github_repo}:*"
          }
        }
      }
    ]
  })

  tags = var.tags
}

# ECR Push/Pull Policy for CI
resource "aws_iam_role_policy" "github_actions_ecr" {
  name = "ecr-push-pull"
  role = aws_iam_role.github_actions_ci.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:DescribeImages",
          "ecr:DescribeRepositories",
          "ecr:ListImages"
        ]
        Resource = values(module.ecr.repository_arns)
      }
    ]
  })
}
