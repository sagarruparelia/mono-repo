# BFF Infrastructure Documentation

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                  DEVELOPERS                                          │
│                                                                                      │
│    ┌──────────────┐         ┌──────────────┐         ┌──────────────┐               │
│    │   Feature    │         │     Code     │         │    Push      │               │
│    │  Development │────────▶│    Review    │────────▶│   to Git     │               │
│    └──────────────┘         └──────────────┘         └──────┬───────┘               │
└─────────────────────────────────────────────────────────────┼───────────────────────┘
                                                              │
                                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              GITHUB ACTIONS CI/CD                                    │
│                                                                                      │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    │
│    │   Checkout   │    │    Maven     │    │     Run      │    │    Docker    │    │
│    │     Code     │───▶│    Build     │───▶│    Tests     │───▶│    Build     │    │
│    └──────────────┘    └──────────────┘    └──────────────┘    └──────┬───────┘    │
│                                                                        │            │
│                         JDK 25 (Amazon Corretto)                       │            │
└────────────────────────────────────────────────────────────────────────┼────────────┘
                                                                         │
                                                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              JFROG ARTIFACTORY                                       │
│                                                                                      │
│                    ┌─────────────────────────────────────┐                          │
│                    │     bff-docker/bff:<tag>            │                          │
│                    │                                     │                          │
│                    │  Tags:                              │                          │
│                    │   • main (latest from main branch)  │                          │
│                    │   • develop                         │                          │
│                    │   • v1.0.0 (semver releases)        │                          │
│                    │   • sha-abc1234 (commit SHA)        │                          │
│                    └─────────────────────────────────────┘                          │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         │ Pull Image
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                 AMAZON EKS CLUSTER                                   │
│                                                                                      │
│  ┌───────────────────────────────────────────────────────────────────────────────┐  │
│  │                              bff Namespace                                     │  │
│  │                                                                                │  │
│  │   ┌─────────────────────────────────────────────────────────────────────┐     │  │
│  │   │                     Deployment (replicas: 3)                        │     │  │
│  │   │                                                                     │     │  │
│  │   │    ┌─────────────┐   ┌─────────────┐   ┌─────────────┐             │     │  │
│  │   │    │   BFF Pod   │   │   BFF Pod   │   │   BFF Pod   │             │     │  │
│  │   │    │             │   │             │   │             │             │     │  │
│  │   │    │ ┌─────────┐ │   │ ┌─────────┐ │   │ ┌─────────┐ │             │     │  │
│  │   │    │ │Corretto │ │   │ │Corretto │ │   │ │Corretto │ │             │     │  │
│  │   │    │ │  JDK 25 │ │   │ │  JDK 25 │ │   │ │  JDK 25 │ │             │     │  │
│  │   │    │ │         │ │   │ │         │ │   │ │         │ │             │     │  │
│  │   │    │ │ Spring  │ │   │ │ Spring  │ │   │ │ Spring  │ │             │     │  │
│  │   │    │ │  Boot   │ │   │ │  Boot   │ │   │ │  Boot   │ │             │     │  │
│  │   │    │ └─────────┘ │   │ └─────────┘ │   │ └─────────┘ │             │     │  │
│  │   │    │   :8080     │   │   :8080     │   │   :8080     │             │     │  │
│  │   │    └──────┬──────┘   └──────┬──────┘   └──────┬──────┘             │     │  │
│  │   │           │                 │                 │                    │     │  │
│  │   └───────────┼─────────────────┼─────────────────┼────────────────────┘     │  │
│  │               │                 │                 │                          │  │
│  │               └────────────┬────┴────┬────────────┘                          │  │
│  │                            │         │                                       │  │
│  │   ┌────────────────────────▼─────────▼────────────────────────────────────┐  │  │
│  │   │                    Service (ClusterIP)                                │  │  │
│  │   │                        bff:80 → :8080                                 │  │  │
│  │   └────────────────────────────────┬──────────────────────────────────────┘  │  │
│  │                                    │                                         │  │
│  │   ┌────────────────────────────────▼──────────────────────────────────────┐  │  │
│  │   │                    Ingress (ALB Controller)                           │  │  │
│  │   │                   bff.your-domain.com → bff:80                        │  │  │
│  │   └────────────────────────────────┬──────────────────────────────────────┘  │  │
│  │                                    │                                         │  │
│  │   ┌──────────────────┐  ┌──────────┴───────────┐  ┌────────────────────┐    │  │
│  │   │    ConfigMap     │  │   HPA (Autoscaler)   │  │   ServiceAccount   │    │  │
│  │   │   (env config)   │  │   min:2  max:10      │  │      (IRSA)        │    │  │
│  │   └──────────────────┘  └──────────────────────┘  └────────────────────┘    │  │
│  │                                                                              │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
└──────────────────────────────────────────────────────────────────────────────────────┘
                     │                    │                    │
                     ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              AWS MANAGED SERVICES                                    │
│                                                                                      │
│    ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐              │
│    │                  │   │                  │   │                  │              │
│    │   MongoDB Atlas  │   │ Amazon ElastiCache│   │    Amazon S3    │              │
│    │   (DocumentDB)   │   │    (Valkey)      │   │   (Documents)   │              │
│    │                  │   │                  │   │                  │              │
│    │  • User data     │   │  • Sessions      │   │  • File uploads │              │
│    │  • Documents     │   │  • Cache         │   │  • Presigned    │              │
│    │  • Categories    │   │  • Rate limiting │   │    URLs         │              │
│    │                  │   │                  │   │                  │              │
│    └──────────────────┘   └──────────────────┘   └──────────────────┘              │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Local Development

### Prerequisites

- Java 25 (Amazon Corretto recommended)
- Maven 3.9+
- Docker & Docker Compose

### Running Locally with Docker Compose

```bash
# Build the application
./mvnw clean package -DskipTests

# Start all services (BFF + MongoDB + Valkey)
docker-compose up --build

# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Services

| Service | Port | Description |
|---------|------|-------------|
| BFF     | 8080 | Spring Boot application |
| MongoDB | 27017 | Document database |
| Valkey  | 6379 | Session & cache store (Redis-compatible) |

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

---

## Docker Image

### Base Image

- **Runtime**: `amazoncorretto:25-alpine`
- **User**: Non-root (`appuser:appgroup`, UID/GID 1001)
- **Port**: 8080

### JVM Configuration

```
-XX:+UseContainerSupport        # Respect container memory limits
-XX:MaxRAMPercentage=75.0       # Use 75% of container memory for heap
-XX:InitialRAMPercentage=50.0   # Start with 50% of max heap
-Djava.security.egd=file:/dev/./urandom  # Faster startup
```

### Building Locally

```bash
# Build JAR first
./mvnw clean package -DskipTests

# Build Docker image
docker build -t bff:local .

# Run container
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/bff \
  bff:local
```

---

## CI/CD Pipeline

### GitHub Actions Workflow

**Trigger Events:**
- Push to `main` or `develop` branches
- Tags matching `v*` (e.g., `v1.0.0`)
- Pull requests to `main`

**Pipeline Stages:**

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Checkout   │────▶│  Maven Build │────▶│  Run Tests   │────▶│ Docker Build │
│              │     │  (JDK 25)    │     │              │     │   & Push     │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
```

### Image Tags

| Event | Tag Format | Example |
|-------|------------|---------|
| Branch push | `{branch}` | `main`, `develop` |
| Semver tag | `{version}` | `1.0.0` |
| Commit | `sha-{short-sha}` | `sha-abc1234` |
| PR | `pr-{number}` | `pr-42` |

### Required GitHub Secrets

| Secret | Description | Example |
|--------|-------------|---------|
| `JFROG_ARTIFACTORY_URL` | JFrog registry URL | `company.jfrog.io` |
| `JFROG_USERNAME` | JFrog username | `ci-user` |
| `JFROG_PASSWORD` | JFrog API key | `AKC...` |

---

## Kubernetes Deployment

### Namespace

All resources are deployed to the `bff` namespace.

```bash
kubectl create namespace bff
```

### Resource Overview

| Resource | Name | Description |
|----------|------|-------------|
| Namespace | `bff` | Isolated namespace for BFF |
| ConfigMap | `bff-config` | Non-secret environment variables |
| Secret | `bff-secrets` | Sensitive credentials |
| ServiceAccount | `bff-sa` | IRSA for AWS access |
| Deployment | `bff` | Application pods (3 replicas) |
| Service | `bff` | ClusterIP service |
| Ingress | `bff` | ALB ingress controller |
| HPA | `bff` | Horizontal pod autoscaler |

### Deployment Specifications

```yaml
Replicas: 3 (min: 2, max: 10 via HPA)

Resources:
  Requests:
    CPU: 250m
    Memory: 512Mi
  Limits:
    CPU: 1000m
    Memory: 1Gi

Probes:
  Liveness:  /actuator/health/liveness  (60s initial, 10s period)
  Readiness: /actuator/health/readiness (30s initial, 5s period)
  Startup:   /actuator/health           (10s initial, 30 failures)
```

### Autoscaling (HPA)

```yaml
Min Replicas: 2
Max Replicas: 10

Scale Up Triggers:
  - CPU > 70% utilization
  - Memory > 80% utilization

Scale Down:
  - Stabilization window: 5 minutes
  - Max 10% reduction per minute
```

### Deploying to EKS

```bash
# Create JFrog registry secret
kubectl create secret docker-registry jfrog-registry-secret \
  --namespace=bff \
  --docker-server=YOUR_ARTIFACTORY_URL \
  --docker-username=YOUR_USERNAME \
  --docker-password=YOUR_PASSWORD

# Apply all manifests
kubectl apply -f k8s/

# Verify deployment
kubectl get all -n bff

# Check pod logs
kubectl logs -n bff -l app=bff --tail=100

# Port forward for local testing
kubectl port-forward -n bff svc/bff 8080:80
```

---

## Configuration

### Environment Variables

#### Required (No Defaults)

| Variable | Description |
|----------|-------------|
| `HSID_CLIENT_ID` | OIDC client ID for HSID |
| `HSID_CLIENT_SECRET` | OIDC client secret |
| `HCP_CLIENT_ID` | HCP API client ID |
| `HCP_CLIENT_SECRET` | HCP API client secret |
| `SPRING_DATA_MONGODB_URI` | MongoDB connection string |
| `SPRING_DATA_REDIS_HOST` | Valkey/Redis host |
| `DOCUMENT_S3_BUCKET` | S3 bucket for documents |

#### Optional (With Defaults)

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | (none) | Active Spring profile |
| `AWS_REGION` | `us-east-1` | AWS region |
| `BFF_SESSION_STORE` | `in-memory` | Session store type |
| `BFF_CACHE_STORE` | `in-memory` | Cache store type |
| `SPRING_DATA_REDIS_PORT` | `6379` | Valkey/Redis port |
| `LOG_LEVEL` | `INFO` | Log level |

### Spring Profiles

| Profile | Use Case |
|---------|----------|
| `local` | Local development with docker-compose |
| `prod` | Production EKS deployment |

---

## Security

### Container Security

- **Non-root user**: Runs as `appuser` (UID 1001)
- **Read-only filesystem**: Consider enabling `readOnlyRootFilesystem: true`
- **Dropped capabilities**: All Linux capabilities dropped
- **No privilege escalation**: `allowPrivilegeEscalation: false`

### AWS Access (IRSA)

The ServiceAccount uses IAM Roles for Service Accounts (IRSA) to access AWS services without storing credentials in the cluster.

```yaml
# k8s/serviceaccount.yaml
annotations:
  eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT_ID:role/bff-eks-role
```

Required IAM permissions:
- `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` on document bucket
- `kms:Decrypt`, `kms:GenerateDataKey` for KMS encryption

### Secrets Management

Recommended approaches:
1. **AWS Secrets Manager** + External Secrets Operator
2. **Sealed Secrets** for GitOps
3. **kubectl create secret** for manual management

---

## Monitoring & Observability

### Health Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Overall health status |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/info` | Application info |
| `/actuator/metrics` | Prometheus metrics |

### Recommended Stack

- **Metrics**: Prometheus + Grafana
- **Logging**: CloudWatch Logs or ELK Stack
- **Tracing**: AWS X-Ray or Jaeger

---

## Troubleshooting

### Common Issues

**Pods not starting:**
```bash
kubectl describe pod -n bff -l app=bff
kubectl logs -n bff -l app=bff --previous
```

**Image pull errors:**
```bash
# Verify registry secret
kubectl get secret jfrog-registry-secret -n bff -o yaml

# Check image exists
docker pull YOUR_ARTIFACTORY_URL/bff-docker/bff:TAG
```

**Health check failures:**
```bash
# Port forward and test manually
kubectl port-forward -n bff svc/bff 8080:80
curl http://localhost:8080/actuator/health
```

**Memory issues:**
```bash
# Check resource usage
kubectl top pods -n bff

# Increase limits in deployment.yaml
resources:
  limits:
    memory: "2Gi"
```

---

## File Structure

```
bff/
├── Dockerfile                          # Container build definition
├── .dockerignore                       # Docker build exclusions
├── docker-compose.yml                  # Local development stack
├── .github/
│   └── workflows/
│       └── build-and-push.yml          # CI/CD pipeline
├── k8s/
│   ├── namespace.yaml                  # Kubernetes namespace
│   ├── configmap.yaml                  # Environment configuration
│   ├── secret.yaml                     # Secrets template
│   ├── serviceaccount.yaml             # IRSA configuration
│   ├── deployment.yaml                 # Application deployment
│   ├── service.yaml                    # Internal service
│   ├── ingress.yaml                    # External access (ALB)
│   └── hpa.yaml                        # Autoscaling rules
└── docs/
    └── INFRASTRUCTURE.md               # This document
```
