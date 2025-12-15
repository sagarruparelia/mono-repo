# =============================================================================
# ElastiCache Module - Valkey (Redis-compatible) for Session Storage
# CRITICAL: Must use version 8.2.2+ due to CVE-2025-49844
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
  cluster_id = "${var.project}-${var.environment}-valkey"
}

# -----------------------------------------------------------------------------
# Subnet Group
# -----------------------------------------------------------------------------
resource "aws_elasticache_subnet_group" "main" {
  name        = "${local.cluster_id}-subnet-group"
  description = "Subnet group for ${local.cluster_id}"
  subnet_ids  = var.subnet_ids

  tags = var.tags
}

# -----------------------------------------------------------------------------
# Security Group
# -----------------------------------------------------------------------------
resource "aws_security_group" "elasticache" {
  name_prefix = "${local.cluster_id}-"
  description = "Security group for ElastiCache Valkey"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, {
    Name = "${local.cluster_id}-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "elasticache_ingress" {
  type                     = "ingress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = var.eks_node_security_group_id
  security_group_id        = aws_security_group.elasticache.id
  description              = "Allow Redis from EKS nodes"
}

resource "aws_security_group_rule" "elasticache_egress" {
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.elasticache.id
  description       = "Allow all egress"
}

# -----------------------------------------------------------------------------
# Parameter Group for Valkey
# -----------------------------------------------------------------------------
resource "aws_elasticache_parameter_group" "main" {
  family      = "valkey8"
  name        = "${local.cluster_id}-params"
  description = "Parameter group for ${local.cluster_id}"

  # Session-optimized parameters
  parameter {
    name  = "maxmemory-policy"
    value = "volatile-lru"
  }

  parameter {
    name  = "timeout"
    value = "300"
  }

  tags = var.tags
}

# -----------------------------------------------------------------------------
# CloudWatch Log Group
# -----------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "valkey" {
  name              = "/aws/elasticache/${local.cluster_id}"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

# -----------------------------------------------------------------------------
# Replication Group
# CRITICAL: engine_version must be 8.2+ for CVE-2025-49844 fix
# -----------------------------------------------------------------------------
resource "aws_elasticache_replication_group" "main" {
  replication_group_id = local.cluster_id
  description          = "Valkey cluster for BFF session storage"

  engine               = "valkey"
  engine_version       = var.valkey_version
  node_type            = var.node_type
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.main.name

  # Cluster configuration
  num_cache_clusters = var.environment == "prod" ? var.num_cache_clusters : 1

  # High availability for production
  automatic_failover_enabled = var.environment == "prod"
  multi_az_enabled           = var.environment == "prod"

  # Security
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = var.auth_token
  auth_token_update_strategy = "ROTATE"

  # Network
  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.elasticache.id]

  # Maintenance
  maintenance_window       = var.maintenance_window
  snapshot_retention_limit = var.environment == "prod" ? 7 : 1
  snapshot_window          = var.snapshot_window
  auto_minor_version_upgrade = true

  # Logging
  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.valkey.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }

  tags = merge(var.tags, {
    Name = local.cluster_id
  })

  lifecycle {
    ignore_changes = [auth_token]
  }
}
