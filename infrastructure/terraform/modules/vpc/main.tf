# =============================================================================
# VPC Module - Network Foundation
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
  azs          = slice(data.aws_availability_zones.available.names, 0, var.az_count)
  cluster_name = "${var.project}-${var.environment}-eks"
}

data "aws_availability_zones" "available" {
  state = "available"
}

# -----------------------------------------------------------------------------
# VPC
# -----------------------------------------------------------------------------
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-vpc"
  })
}

# -----------------------------------------------------------------------------
# Internet Gateway
# -----------------------------------------------------------------------------
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-igw"
  })
}

# -----------------------------------------------------------------------------
# Public Subnets (for ALB)
# -----------------------------------------------------------------------------
resource "aws_subnet" "public" {
  count                   = var.az_count
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name                                        = "${var.project}-${var.environment}-public-${local.azs[count.index]}"
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
  })
}

# -----------------------------------------------------------------------------
# Private Subnets (for EKS nodes)
# -----------------------------------------------------------------------------
resource "aws_subnet" "private" {
  count             = var.az_count
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + var.az_count)
  availability_zone = local.azs[count.index]

  tags = merge(var.tags, {
    Name                                        = "${var.project}-${var.environment}-private-${local.azs[count.index]}"
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
  })
}

# -----------------------------------------------------------------------------
# Database Subnets (isolated, for ElastiCache)
# -----------------------------------------------------------------------------
resource "aws_subnet" "database" {
  count             = var.az_count
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + (var.az_count * 2))
  availability_zone = local.azs[count.index]

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-database-${local.azs[count.index]}"
  })
}

# -----------------------------------------------------------------------------
# Elastic IPs for NAT Gateways
# -----------------------------------------------------------------------------
resource "aws_eip" "nat" {
  count  = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : var.az_count) : 0
  domain = "vpc"

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-nat-eip-${count.index + 1}"
  })

  depends_on = [aws_internet_gateway.main]
}

# -----------------------------------------------------------------------------
# NAT Gateways
# -----------------------------------------------------------------------------
resource "aws_nat_gateway" "main" {
  count         = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : var.az_count) : 0
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-nat-${count.index + 1}"
  })

  depends_on = [aws_internet_gateway.main]
}

# -----------------------------------------------------------------------------
# Route Tables
# -----------------------------------------------------------------------------

# Public route table
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-public-rt"
  })
}

# Private route tables
resource "aws_route_table" "private" {
  count  = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : var.az_count) : 0
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[var.single_nat_gateway ? 0 : count.index].id
  }

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-private-rt-${count.index + 1}"
  })
}

# -----------------------------------------------------------------------------
# Route Table Associations
# -----------------------------------------------------------------------------
resource "aws_route_table_association" "public" {
  count          = var.az_count
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count          = var.enable_nat_gateway ? var.az_count : 0
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[var.single_nat_gateway ? 0 : count.index].id
}

# -----------------------------------------------------------------------------
# VPC Flow Logs
# -----------------------------------------------------------------------------
resource "aws_flow_log" "main" {
  count                    = var.enable_flow_logs ? 1 : 0
  iam_role_arn             = aws_iam_role.flow_log[0].arn
  log_destination          = aws_cloudwatch_log_group.flow_log[0].arn
  traffic_type             = "ALL"
  vpc_id                   = aws_vpc.main.id
  max_aggregation_interval = 60

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-flow-log"
  })
}

resource "aws_cloudwatch_log_group" "flow_log" {
  count             = var.enable_flow_logs ? 1 : 0
  name              = "/aws/vpc/${var.project}-${var.environment}/flow-logs"
  retention_in_days = var.flow_log_retention_days

  tags = var.tags
}

resource "aws_iam_role" "flow_log" {
  count = var.enable_flow_logs ? 1 : 0
  name  = "${var.project}-${var.environment}-vpc-flow-log-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "vpc-flow-logs.amazonaws.com"
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "flow_log" {
  count = var.enable_flow_logs ? 1 : 0
  name  = "vpc-flow-log-policy"
  role  = aws_iam_role.flow_log[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ]
      Resource = "*"
    }]
  })
}
