# CardDemo Modernized - Deployment Guide

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen.svg)
![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
  - [Docker Containerization](#docker-containerization)
  - [Docker Compose Environment](#docker-compose-environment)
- [Kubernetes Deployment](#kubernetes-deployment)
  - [Deployment Manifest](#deployment-manifest)
  - [Service Configuration](#service-configuration)
  - [ConfigMap Management](#configmap-management)
  - [Secrets Management](#secrets-management)
  - [Ingress Configuration](#ingress-configuration)
  - [Horizontal Pod Autoscaler](#horizontal-pod-autoscaler)
- [Infrastructure Provisioning](#infrastructure-provisioning)
  - [Terraform Setup](#terraform-setup)
  - [AWS EKS Cluster](#aws-eks-cluster)
  - [RDS PostgreSQL Database](#rds-postgresql-database)
  - [VPC and Networking](#vpc-and-networking)
- [CI/CD Pipeline](#cicd-pipeline)
  - [Continuous Integration](#continuous-integration)
  - [Continuous Deployment](#continuous-deployment)
- [Configuration Management](#configuration-management)
  - [Spring Profiles](#spring-profiles)
  - [Environment Variables](#environment-variables)
  - [Database Migrations](#database-migrations)
- [Operational Procedures](#operational-procedures)
  - [Deployment Workflow](#deployment-workflow)
  - [Scaling Operations](#scaling-operations)
  - [Health Checks and Monitoring](#health-checks-and-monitoring)
  - [Logging](#logging)
  - [Backup and Recovery](#backup-and-recovery)
- [Troubleshooting](#troubleshooting)
  - [Common Issues](#common-issues)
  - [Debugging Techniques](#debugging-techniques)
  - [Rollback Procedures](#rollback-procedures)
- [Security Considerations](#security-considerations)
- [Performance Tuning](#performance-tuning)
- [Support](#support)

## Overview

This deployment guide provides comprehensive instructions for deploying the modernized CardDemo application, which has been migrated from a COBOL/CICS/VSAM mainframe application to a cloud-native Java 21 application built with Spring Boot 3.3.0.

**Architecture Overview:**
- **Application**: Java 21 with Spring Boot 3.3.0, Spring Data JPA, Spring Batch
- **Database**: PostgreSQL 15+ with Flyway migrations
- **Container Runtime**: Docker with multi-stage builds
- **Orchestration**: Kubernetes 1.28+ (AWS EKS)
- **Infrastructure**: Terraform-managed AWS resources (EKS, RDS, VPC)
- **CI/CD**: GitHub Actions for automated build, test, and deployment

**Deployment Targets:**
- **Local Development**: Docker Compose with PostgreSQL
- **Test Environment**: Kubernetes cluster with in-cluster PostgreSQL
- **Production**: AWS EKS with RDS PostgreSQL Multi-AZ

## Prerequisites

Before deploying the CardDemo modernized application, ensure you have the following tools and access:

### Required Tools

| Tool | Minimum Version | Purpose | Installation |
|------|----------------|---------|--------------|
| **Docker** | 20.10+ | Container runtime | [Docker Installation](https://docs.docker.com/get-docker/) |
| **Docker Compose** | 2.0+ | Local multi-container orchestration | [Compose Installation](https://docs.docker.com/compose/install/) |
| **kubectl** | 1.28+ | Kubernetes CLI | [kubectl Installation](https://kubernetes.io/docs/tasks/tools/) |
| **AWS CLI** | 2.0+ | AWS resource management | [AWS CLI Installation](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) |
| **Terraform** | 1.5+ | Infrastructure as Code | [Terraform Installation](https://developer.hashicorp.com/terraform/install) |
| **Maven** | 3.9+ | Build automation | [Maven Installation](https://maven.apache.org/install.html) |
| **Java JDK** | 21 (LTS) | Local development | [OpenJDK 21](https://openjdk.org/projects/jdk/21/) or [Eclipse Temurin 21](https://adoptium.net/) |
| **Git** | 2.30+ | Source control | [Git Installation](https://git-scm.com/downloads) |

### Optional Tools

| Tool | Purpose |
|------|---------|
| **kubectx/kubens** | Simplified Kubernetes context and namespace switching |
| **k9s** | Terminal UI for Kubernetes cluster management |
| **Lens** | Kubernetes IDE for cluster visualization |
| **Postman** | API testing |

### AWS Access Requirements

- **IAM User** with permissions for:
  - EKS cluster management (create, update, delete)
  - RDS database management
  - VPC and networking resources
  - EC2 instances (for EKS worker nodes)
  - CloudWatch logs and metrics
  - Secrets Manager (for credential storage)
- **AWS CLI** configured with access keys:
  ```bash
  aws configure
  # Enter AWS Access Key ID
  # Enter AWS Secret Access Key
  # Default region: us-east-1 (or your preferred region)
  # Default output format: json
  ```

### Kubernetes Cluster Access

- **EKS Cluster** (for production deployment)
- **kubectl** configured to access your cluster:
  ```bash
  aws eks update-kubeconfig --region us-east-1 --name carddemo-cluster
  ```

### GitHub Repository Access

- **GitHub Account** with repository access
- **Personal Access Token** for GitHub Actions (for CI/CD):
  - Permissions: `repo`, `workflow`, `write:packages`

### Network Requirements

- **Outbound Internet Access** for:
  - Docker image pulls from Docker Hub
  - Maven dependency downloads from Maven Central
  - AWS API calls
- **Inbound Access** (for production):
  - Port 8080 (Application HTTP)
  - Port 443 (HTTPS via ALB/Ingress)

## Local Development Setup

### Docker Containerization

The CardDemo application uses a multi-stage Docker build to optimize image size and security.

#### Dockerfile Structure

Create a `Dockerfile` in the repository root:

```dockerfile
# =============================================================================
# Stage 1: Build Stage
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy Maven configuration and source code
COPY pom.xml .
COPY src ./src

# Build the application (skip tests for faster builds)
# Tests are run separately in CI/CD pipeline
RUN mvn clean package -DskipTests

# =============================================================================
# Stage 2: Runtime Stage
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the compiled JAR from build stage
COPY --from=build /app/target/carddemo-*.jar app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080

# Health check configuration
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# Application entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

#### Building the Docker Image

```bash
# Navigate to repository root
cd aws-card-demo-modernized

# Build the Docker image
docker build -t carddemo:latest .

# Build with specific version tag
docker build -t carddemo:1.0.0 -t carddemo:latest .

# Verify the image
docker images | grep carddemo

# Expected output:
# carddemo    latest    abc123def456    2 minutes ago    ~200MB
```

#### Docker Image Optimization

The multi-stage build produces an optimized image:
- **Build stage**: Uses full JDK with Maven (~800MB)
- **Runtime stage**: Uses JRE on Alpine Linux (~200MB)
- **Security**: Runs as non-root user
- **Health checks**: Built-in health monitoring

#### Running Locally with Docker

```bash
# Run the application container
docker run -d \
  --name carddemo \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=carddemo \
  -e DB_USER=postgres \
  -e DB_PASS=password \
  carddemo:latest

# View logs
docker logs -f carddemo

# Stop the container
docker stop carddemo

# Remove the container
docker rm carddemo
```

### Docker Compose Environment

For local development with the full stack (application + database), use Docker Compose.

#### docker-compose.yml

Create a `docker-compose.yml` file in the repository root:

```yaml
version: '3.8'

services:
  # PostgreSQL Database Service
  postgres:
    image: postgres:15-alpine
    container_name: carddemo-postgres
    environment:
      POSTGRES_DB: carddemo
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
      POSTGRES_INITDB_ARGS: "--encoding=UTF8"
    ports:
      - "5432:5432"
    volumes:
      # Persist database data
      - postgres-data:/var/lib/postgresql/data
      # Optional: Initialize with custom SQL scripts
      - ./src/main/resources/db/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - carddemo-network
    restart: unless-stopped

  # CardDemo Application Service
  carddemo:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: carddemo-app
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      # Spring Profile
      SPRING_PROFILES_ACTIVE: dev
      
      # Database Configuration
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: carddemo
      DB_USER: postgres
      DB_PASS: password
      
      # JVM Options
      JAVA_OPTS: >-
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -Xms512m
        -Xmx1g
      
      # Application Configuration
      SERVER_PORT: 8080
      
      # Logging
      LOGGING_LEVEL_ROOT: INFO
      LOGGING_LEVEL_COM_AWS_CARDDEMO: DEBUG
    ports:
      - "8080:8080"
    volumes:
      # Optional: Mount application logs
      - ./logs:/app/logs
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - carddemo-network
    restart: unless-stopped

volumes:
  postgres-data:
    driver: local

networks:
  carddemo-network:
    driver: bridge
```

#### Starting the Local Environment

```bash
# Start all services in detached mode
docker-compose up -d

# View logs for all services
docker-compose logs -f

# View logs for specific service
docker-compose logs -f carddemo

# Check service status
docker-compose ps

# Expected output:
# NAME                COMMAND              SERVICE     STATUS          PORTS
# carddemo-app        "sh -c 'java..."     carddemo    Up (healthy)    0.0.0.0:8080->8080/tcp
# carddemo-postgres   "docker-entryp..."   postgres    Up (healthy)    0.0.0.0:5432->5432/tcp
```

#### Accessing the Application

Once the services are running:

```bash
# Health check endpoint
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}

# API documentation (Swagger UI)
open http://localhost:8080/swagger-ui.html

# Example API request - Get account
curl http://localhost:8080/api/v1/accounts/1

# Example API request - Authentication
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"USER0001","password":"PASSWORD"}'
```

#### Stopping and Cleaning Up

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes database data)
docker-compose down -v

# Stop, remove volumes, and remove images
docker-compose down -v --rmi all

# Rebuild and restart after code changes
docker-compose up -d --build
```

#### Development Workflow

1. **Make Code Changes**: Edit source files in `src/`
2. **Rebuild**: `docker-compose up -d --build carddemo`
3. **Test**: Access endpoints at `http://localhost:8080`
4. **View Logs**: `docker-compose logs -f carddemo`
5. **Iterate**: Repeat as needed

## Kubernetes Deployment

This section covers deploying the CardDemo application to a Kubernetes cluster (AWS EKS for production).

### Deployment Manifest

The Kubernetes Deployment defines how the application runs in the cluster.

Create `k8s/namespace.yml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: carddemo
  labels:
    name: carddemo
    environment: production
```

Create `k8s/deployment.yml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: carddemo
  namespace: carddemo
  labels:
    app: carddemo
    version: v1
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: carddemo
  template:
    metadata:
      labels:
        app: carddemo
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      # Security context for pod
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      
      # Service account for pod (for AWS IAM roles)
      serviceAccountName: carddemo-sa
      
      containers:
      - name: carddemo
        image: <AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/carddemo:latest
        imagePullPolicy: Always
        
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        
        # Environment variables from ConfigMap
        envFrom:
        - configMapRef:
            name: carddemo-config
        
        # Environment variables from Secret
        env:
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: carddemo-db-credentials
              key: username
        - name: DB_PASS
          valueFrom:
            secretKeyRef:
              name: carddemo-db-credentials
              key: password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: carddemo-jwt-secret
              key: secret
        
        # Resource requests and limits
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        
        # Liveness probe - restart container if unhealthy
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
          successThreshold: 1
        
        # Readiness probe - remove from load balancer if not ready
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
          successThreshold: 1
        
        # Startup probe - allow slow startup
        startupProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 0
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 30
          successThreshold: 1
        
        # Lifecycle hooks
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 15"]
        
        # Security context for container
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: false
          runAsNonRoot: true
          runAsUser: 1000
          capabilities:
            drop:
            - ALL
      
      # Termination grace period for graceful shutdown
      terminationGracePeriodSeconds: 30
      
      # Node affinity (optional)
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - carddemo
              topologyKey: kubernetes.io/hostname
```

### Service Configuration

The Service exposes the application within the cluster and externally.

Create `k8s/service.yml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: carddemo-service
  namespace: carddemo
  labels:
    app: carddemo
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
spec:
  type: LoadBalancer
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 10800
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  selector:
    app: carddemo
```

Alternative: ClusterIP with Ingress (for ALB Ingress Controller):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: carddemo-service
  namespace: carddemo
  labels:
    app: carddemo
spec:
  type: ClusterIP
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  selector:
    app: carddemo
```

### ConfigMap Management

ConfigMaps store non-sensitive configuration data.

Create `k8s/configmap.yml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: carddemo-config
  namespace: carddemo
data:
  # Spring Profile
  SPRING_PROFILES_ACTIVE: "prod"
  
  # Server Configuration
  SERVER_PORT: "8080"
  SERVER_SERVLET_CONTEXT_PATH: "/"
  
  # Database Configuration
  DB_HOST: "carddemo-db.c9xyz.us-east-1.rds.amazonaws.com"
  DB_PORT: "5432"
  DB_NAME: "carddemo"
  
  # HikariCP Connection Pool Settings
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: "20"
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: "5"
  SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT: "30000"
  SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT: "600000"
  SPRING_DATASOURCE_HIKARI_MAX_LIFETIME: "1800000"
  
  # JPA/Hibernate Settings
  SPRING_JPA_HIBERNATE_DDL_AUTO: "validate"
  SPRING_JPA_SHOW_SQL: "false"
  SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL: "false"
  SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT: "org.hibernate.dialect.PostgreSQLDialect"
  
  # Flyway Migration
  SPRING_FLYWAY_ENABLED: "true"
  SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
  
  # Logging Configuration
  LOGGING_LEVEL_ROOT: "INFO"
  LOGGING_LEVEL_COM_AWS_CARDDEMO: "INFO"
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK: "WARN"
  LOGGING_LEVEL_ORG_HIBERNATE: "WARN"
  
  # Actuator Endpoints
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics,prometheus"
  MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: "when-authorized"
  
  # JWT Configuration (non-sensitive parts)
  JWT_EXPIRATION_MS: "3600000"
  
  # Batch Job Settings
  SPRING_BATCH_JOB_ENABLED: "false"
  
  # Cache Configuration
  SPRING_CACHE_TYPE: "caffeine"
  SPRING_CACHE_CAFFEINE_SPEC: "maximumSize=500,expireAfterAccess=600s"
```

Apply the ConfigMap:

```bash
kubectl apply -f k8s/configmap.yml
```

Update ConfigMap without downtime:

```bash
# Edit the ConfigMap
kubectl edit configmap carddemo-config -n carddemo

# Restart pods to pick up new configuration
kubectl rollout restart deployment/carddemo -n carddemo
```

### Secrets Management

Secrets store sensitive data like database credentials and JWT secrets.

#### Using Kubernetes Secrets (Basic)

Create `k8s/secret.yml` (for reference only - do NOT commit to Git):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: carddemo-db-credentials
  namespace: carddemo
type: Opaque
stringData:
  username: "carddemo_user"
  password: "CHANGE_ME_IN_PRODUCTION"
---
apiVersion: v1
kind: Secret
metadata:
  name: carddemo-jwt-secret
  namespace: carddemo
type: Opaque
stringData:
  secret: "CHANGE_ME_IN_PRODUCTION_USE_256_BIT_SECRET"
```

Create secrets from command line (recommended):

```bash
# Create database credentials secret
kubectl create secret generic carddemo-db-credentials \
  --from-literal=username='carddemo_user' \
  --from-literal=password='YOUR_SECURE_PASSWORD' \
  -n carddemo

# Create JWT secret
kubectl create secret generic carddemo-jwt-secret \
  --from-literal=secret='YOUR_256_BIT_JWT_SECRET' \
  -n carddemo

# Verify secrets
kubectl get secrets -n carddemo
```

#### Using Sealed Secrets (Recommended for GitOps)

Install Sealed Secrets Controller:

```bash
# Install sealed-secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml

# Install kubeseal CLI
wget https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/kubeseal-0.24.0-linux-amd64.tar.gz
tar -xvzf kubeseal-0.24.0-linux-amd64.tar.gz
sudo mv kubeseal /usr/local/bin/
```

Create and seal secrets:

```bash
# Create a regular secret (locally, not applied)
kubectl create secret generic carddemo-db-credentials \
  --from-literal=username='carddemo_user' \
  --from-literal=password='YOUR_SECURE_PASSWORD' \
  --dry-run=client -o yaml > temp-secret.yml

# Seal the secret
kubeseal -f temp-secret.yml -w k8s/sealed-secret.yml \
  --controller-namespace=kube-system \
  --controller-name=sealed-secrets-controller

# Remove temporary file
rm temp-secret.yml

# Apply sealed secret (safe to commit to Git)
kubectl apply -f k8s/sealed-secret.yml
```

#### Using AWS Secrets Manager (Production Recommended)

Install External Secrets Operator:

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets-system --create-namespace
```

Create `k8s/external-secret.yml`:

```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets-manager
  namespace: carddemo
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: carddemo-sa
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: carddemo-db-credentials
  namespace: carddemo
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: carddemo-db-credentials
    creationPolicy: Owner
  data:
  - secretKey: username
    remoteRef:
      key: carddemo/db/credentials
      property: username
  - secretKey: password
    remoteRef:
      key: carddemo/db/credentials
      property: password
```

### Ingress Configuration

Ingress provides HTTP/HTTPS routing to the service.

Create `k8s/ingress.yml` (for AWS ALB Ingress Controller):

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: carddemo-ingress
  namespace: carddemo
  annotations:
    # ALB Ingress Controller annotations
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'
    alb.ingress.kubernetes.io/ssl-redirect: '443'
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789012:certificate/abc123...
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health
    alb.ingress.kubernetes.io/healthcheck-interval-seconds: '30'
    alb.ingress.kubernetes.io/healthcheck-timeout-seconds: '5'
    alb.ingress.kubernetes.io/healthy-threshold-count: '2'
    alb.ingress.kubernetes.io/unhealthy-threshold-count: '3'
    alb.ingress.kubernetes.io/success-codes: '200'
spec:
  rules:
  - host: carddemo.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: carddemo-service
            port:
              number: 8080
```

Apply the Ingress:

```bash
kubectl apply -f k8s/ingress.yml

# Get Ingress details
kubectl get ingress -n carddemo

# Get ALB DNS name
kubectl get ingress carddemo-ingress -n carddemo -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

### Horizontal Pod Autoscaler

HPA automatically scales pods based on CPU/memory utilization.

Create `k8s/hpa.yml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: carddemo-hpa
  namespace: carddemo
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: carddemo
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
      - type: Pods
        value: 2
        periodSeconds: 60
      selectPolicy: Min
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 4
        periodSeconds: 30
      selectPolicy: Max
```

Apply and monitor HPA:

```bash
# Apply HPA
kubectl apply -f k8s/hpa.yml

# Check HPA status
kubectl get hpa -n carddemo

# Watch HPA in real-time
kubectl get hpa -n carddemo --watch

# Describe HPA for details
kubectl describe hpa carddemo-hpa -n carddemo
```

### Deploying to Kubernetes

Complete deployment workflow:

```bash
# Step 1: Create namespace
kubectl apply -f k8s/namespace.yml

# Step 2: Create ConfigMap
kubectl apply -f k8s/configmap.yml

# Step 3: Create Secrets
kubectl apply -f k8s/sealed-secret.yml
# OR
kubectl create secret generic carddemo-db-credentials --from-literal=username='user' --from-literal=password='pass' -n carddemo
kubectl create secret generic carddemo-jwt-secret --from-literal=secret='your-256-bit-secret' -n carddemo

# Step 4: Create Service Account (if using IAM roles)
kubectl apply -f k8s/serviceaccount.yml

# Step 5: Deploy application
kubectl apply -f k8s/deployment.yml

# Step 6: Create Service
kubectl apply -f k8s/service.yml

# Step 7: Create Ingress (optional)
kubectl apply -f k8s/ingress.yml

# Step 8: Create HPA
kubectl apply -f k8s/hpa.yml

# Verify deployment
kubectl get all -n carddemo

# Check pod logs
kubectl logs -f deployment/carddemo -n carddemo

# Check pod status
kubectl get pods -n carddemo -w
```

## Infrastructure Provisioning

This section covers provisioning AWS infrastructure using Terraform.

### Terraform Setup

#### Directory Structure

```
terraform/
├── main.tf              # Main infrastructure definition
├── variables.tf         # Input variables
├── outputs.tf           # Output values
├── providers.tf         # Provider configuration
├── vpc.tf               # VPC and networking
├── eks.tf               # EKS cluster
├── rds.tf               # RDS PostgreSQL
├── security-groups.tf   # Security groups
├── iam.tf               # IAM roles and policies
├── terraform.tfvars.example  # Example variable values
└── README.md            # Terraform documentation
```

#### Provider Configuration

Create `terraform/providers.tf`:

```hcl
terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
  }
  
  # Backend configuration for state storage
  backend "s3" {
    bucket         = "carddemo-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "carddemo-terraform-locks"
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "CardDemo"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
  
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
  }
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
    
    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
    }
  }
}
```

#### Variables Definition

Create `terraform/variables.tf`:

```hcl
variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, test, prod)"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "carddemo"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones for multi-AZ deployment"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "eks_cluster_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"
}

variable "eks_node_instance_types" {
  description = "EC2 instance types for EKS worker nodes"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "eks_node_desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 3
}

variable "eks_node_min_size" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 2
}

variable "eks_node_max_size" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 6
}

variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.r6g.xlarge"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 100
}

variable "rds_max_allocated_storage" {
  description = "RDS maximum allocated storage for autoscaling"
  type        = number
  default     = 500
}

variable "rds_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "15.4"
}

variable "rds_database_name" {
  description = "Database name"
  type        = string
  default     = "carddemo"
}

variable "rds_master_username" {
  description = "RDS master username"
  type        = string
  default     = "postgres"
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ for RDS"
  type        = bool
  default     = true
}

variable "rds_backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default     = 7
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}
```

Create `terraform/terraform.tfvars.example`:

```hcl
aws_region                  = "us-east-1"
environment                 = "prod"
project_name                = "carddemo"
vpc_cidr                    = "10.0.0.0/16"
availability_zones          = ["us-east-1a", "us-east-1b", "us-east-1c"]
eks_cluster_version         = "1.28"
eks_node_instance_types     = ["t3.medium", "t3.large"]
eks_node_desired_size       = 3
eks_node_min_size           = 2
eks_node_max_size           = 10
rds_instance_class          = "db.r6g.xlarge"
rds_allocated_storage       = 100
rds_max_allocated_storage   = 500
rds_engine_version          = "15.4"
rds_database_name           = "carddemo"
rds_master_username         = "carddemo_admin"
rds_multi_az                = true
rds_backup_retention_period = 30

tags = {
  Project     = "CardDemo"
  Owner       = "Platform Team"
  CostCenter  = "Engineering"
}
```

### AWS EKS Cluster

Create `terraform/eks.tf`:

```hcl
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"

  cluster_name    = "${var.project_name}-${var.environment}-cluster"
  cluster_version = var.eks_cluster_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Cluster endpoint configuration
  cluster_endpoint_private_access = true
  cluster_endpoint_public_access  = true

  # Cluster addons
  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
    }
    aws-ebs-csi-driver = {
      most_recent = true
    }
  }

  # EKS Managed Node Groups
  eks_managed_node_groups = {
    application = {
      name = "${var.project_name}-${var.environment}-ng"

      instance_types = var.eks_node_instance_types
      capacity_type  = "ON_DEMAND"

      min_size     = var.eks_node_min_size
      max_size     = var.eks_node_max_size
      desired_size = var.eks_node_desired_size

      # Disk configuration
      disk_size = 50
      disk_type = "gp3"

      # Labels
      labels = {
        Environment = var.environment
        NodeGroup   = "application"
      }

      # Taints - none for general purpose nodes
      taints = []

      # IAM role for nodes
      iam_role_additional_policies = {
        AmazonSSMManagedInstanceCore = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
      }

      # Update configuration
      update_config = {
        max_unavailable_percentage = 25
      }

      # Tags
      tags = merge(
        var.tags,
        {
          Name = "${var.project_name}-${var.environment}-node"
        }
      )
    }
  }

  # Cluster security group rules
  cluster_security_group_additional_rules = {
    egress_nodes_ephemeral_ports_tcp = {
      description                = "To node 1025-65535"
      protocol                   = "tcp"
      from_port                  = 1025
      to_port                    = 65535
      type                       = "egress"
      source_node_security_group = true
    }
  }

  # Node security group rules
  node_security_group_additional_rules = {
    ingress_self_all = {
      description = "Node to node all ports/protocols"
      protocol    = "-1"
      from_port   = 0
      to_port     = 0
      type        = "ingress"
      self        = true
    }
    egress_all = {
      description      = "Node all egress"
      protocol         = "-1"
      from_port        = 0
      to_port          = 0
      type             = "egress"
      cidr_blocks      = ["0.0.0.0/0"]
      ipv6_cidr_blocks = ["::/0"]
    }
  }

  # aws-auth configmap
  manage_aws_auth_configmap = true

  aws_auth_roles = [
    {
      rolearn  = "arn:aws:iam::123456789012:role/DevOps"
      username = "devops"
      groups   = ["system:masters"]
    },
  ]

  tags = var.tags
}

# IRSA for EBS CSI Driver
module "ebs_csi_irsa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name = "${var.project_name}-${var.environment}-ebs-csi-driver"

  attach_ebs_csi_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }

  tags = var.tags
}

# IRSA for ALB Ingress Controller
module "alb_controller_irsa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name = "${var.project_name}-${var.environment}-alb-controller"

  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }

  tags = var.tags
}

# IRSA for CardDemo application
resource "aws_iam_role" "carddemo_app" {
  name = "${var.project_name}-${var.environment}-app-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = module.eks.oidc_provider_arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${replace(module.eks.oidc_provider, "https://", "")}:sub" = "system:serviceaccount:carddemo:carddemo-sa"
          "${replace(module.eks.oidc_provider, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

# IAM policy for CardDemo app (Secrets Manager, CloudWatch)
resource "aws_iam_policy" "carddemo_app" {
  name = "${var.project_name}-${var.environment}-app-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [
          "arn:aws:secretsmanager:${var.aws_region}:*:secret:carddemo/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = [
          "arn:aws:logs:${var.aws_region}:*:log-group:/aws/carddemo/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
      }
    ]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "carddemo_app" {
  role       = aws_iam_role.carddemo_app.name
  policy_arn = aws_iam_policy.carddemo_app.arn
}
```

### RDS PostgreSQL Database

Create `terraform/rds.tf`:

```hcl
resource "random_password" "rds_master_password" {
  length  = 32
  special = true
}

resource "aws_secretsmanager_secret" "rds_credentials" {
  name = "${var.project_name}/${var.environment}/db/credentials"
  
  tags = var.tags
}

resource "aws_secretsmanager_secret_version" "rds_credentials" {
  secret_id = aws_secretsmanager_secret.rds_credentials.id
  secret_string = jsonencode({
    username = var.rds_master_username
    password = random_password.rds_master_password.result
    host     = aws_db_instance.carddemo.address
    port     = aws_db_instance.carddemo.port
    dbname   = var.rds_database_name
    engine   = "postgres"
  })
}

resource "aws_db_subnet_group" "carddemo" {
  name       = "${var.project_name}-${var.environment}-db-subnet-group"
  subnet_ids = module.vpc.database_subnets

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-db-subnet-group"
    }
  )
}

resource "aws_db_parameter_group" "carddemo" {
  name   = "${var.project_name}-${var.environment}-postgres15"
  family = "postgres15"

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
  }

  parameter {
    name  = "log_statement"
    value = "all"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  tags = var.tags
}

resource "aws_db_instance" "carddemo" {
  identifier = "${var.project_name}-${var.environment}-db"

  # Engine configuration
  engine               = "postgres"
  engine_version       = var.rds_engine_version
  instance_class       = var.rds_instance_class
  
  # Storage configuration
  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  
  # Database configuration
  db_name  = var.rds_database_name
  username = var.rds_master_username
  password = random_password.rds_master_password.result
  port     = 5432
  
  # Network configuration
  db_subnet_group_name   = aws_db_subnet_group.carddemo.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  
  # High availability
  multi_az = var.rds_multi_az
  
  # Backup configuration
  backup_retention_period = var.rds_backup_retention_period
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"
  
  # Snapshot configuration
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-${var.environment}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
  copy_tags_to_snapshot     = true
  
  # Monitoring
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  monitoring_interval             = 60
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn
  performance_insights_enabled    = true
  performance_insights_retention_period = 7
  
  # Parameter group
  parameter_group_name = aws_db_parameter_group.carddemo.name
  
  # Deletion protection
  deletion_protection = true
  
  # Auto minor version upgrade
  auto_minor_version_upgrade = true
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-db"
    }
  )
}

resource "aws_iam_role" "rds_monitoring" {
  name = "${var.project_name}-${var.environment}-rds-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "monitoring.rds.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
```

### VPC and Networking

Create `terraform/vpc.tf`:

```hcl
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${var.project_name}-${var.environment}-vpc"
  cidr = var.vpc_cidr

  azs              = var.availability_zones
  private_subnets  = [for k, v in var.availability_zones : cidrsubnet(var.vpc_cidr, 4, k)]
  public_subnets   = [for k, v in var.availability_zones : cidrsubnet(var.vpc_cidr, 8, k + 48)]
  database_subnets = [for k, v in var.availability_zones : cidrsubnet(var.vpc_cidr, 8, k + 60)]

  enable_nat_gateway   = true
  single_nat_gateway   = false
  enable_dns_hostnames = true
  enable_dns_support   = true

  # Kubernetes tags for subnet discovery
  public_subnet_tags = {
    "kubernetes.io/role/elb"                                        = 1
    "kubernetes.io/cluster/${var.project_name}-${var.environment}-cluster" = "shared"
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"                               = 1
    "kubernetes.io/cluster/${var.project_name}-${var.environment}-cluster" = "shared"
  }

  tags = var.tags
}

# Security group for RDS
resource "aws_security_group" "rds" {
  name_prefix = "${var.project_name}-${var.environment}-rds-"
  vpc_id      = module.vpc.vpc_id
  description = "Security group for RDS PostgreSQL"

  ingress {
    description     = "PostgreSQL from EKS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.eks.cluster_security_group_id, module.eks.node_security_group_id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-rds-sg"
    }
  )

  lifecycle {
    create_before_destroy = true
  }
}
```

Create `terraform/outputs.tf`:

```hcl
output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = module.eks.cluster_endpoint
}

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "eks_cluster_security_group_id" {
  description = "EKS cluster security group ID"
  value       = module.eks.cluster_security_group_id
}

output "configure_kubectl" {
  description = "Command to configure kubectl"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.carddemo.endpoint
}

output "rds_database_name" {
  description = "RDS database name"
  value       = aws_db_instance.carddemo.db_name
}

output "rds_master_username" {
  description = "RDS master username"
  value       = aws_db_instance.carddemo.username
  sensitive   = true
}

output "rds_secret_arn" {
  description = "ARN of Secrets Manager secret containing RDS credentials"
  value       = aws_secretsmanager_secret.rds_credentials.arn
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = module.vpc.private_subnets
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = module.vpc.public_subnets
}

output "carddemo_app_role_arn" {
  description = "IAM role ARN for CardDemo application"
  value       = aws_iam_role.carddemo_app.arn
}
```

### Terraform Execution

#### Initialize Terraform

```bash
cd terraform

# Copy example variables
cp terraform.tfvars.example terraform.tfvars

# Edit terraform.tfvars with your values
vim terraform.tfvars

# Initialize Terraform (download providers, setup backend)
terraform init

# Validate configuration
terraform validate

# Format configuration
terraform fmt -recursive
```

#### Plan and Apply

```bash
# Create execution plan
terraform plan -out=tfplan

# Review the plan carefully
# The plan shows resources to be created, modified, or destroyed

# Apply the plan
terraform apply tfplan

# Expected output:
# ...
# Plan: 50 to add, 0 to change, 0 to destroy.
# ...
# Apply complete! Resources: 50 added, 0 changed, 0 destroyed.
# 
# Outputs:
# eks_cluster_endpoint = "https://ABC123...eks.amazonaws.com"
# eks_cluster_name = "carddemo-prod-cluster"
# rds_endpoint = "carddemo-prod-db.c9xyz.us-east-1.rds.amazonaws.com:5432"
# ...
```

#### Configure kubectl

```bash
# Configure kubectl to access the new EKS cluster
aws eks update-kubeconfig --region us-east-1 --name carddemo-prod-cluster

# Verify connectivity
kubectl get nodes

# Expected output:
# NAME                         STATUS   ROLES    AGE   VERSION
# ip-10-0-1-100.ec2.internal   Ready    <none>   5m    v1.28.2-eks-...
# ip-10-0-2-200.ec2.internal   Ready    <none>   5m    v1.28.2-eks-...
# ip-10-0-3-300.ec2.internal   Ready    <none>   5m    v1.28.2-eks-...
```

#### Destroy Infrastructure (when needed)

```bash
# Plan destruction
terraform plan -destroy -out=destroy-plan

# Review the destroy plan

# Execute destruction
terraform apply destroy-plan

# Or in one command (requires confirmation)
terraform destroy
```

## CI/CD Pipeline

This section covers automated build, test, and deployment using GitHub Actions.

### Continuous Integration

Create `.github/workflows/ci.yml`:

```yaml
name: CI - Build and Test

on:
  push:
    branches:
      - main
      - develop
      - 'feature/**'
  pull_request:
    branches:
      - main
      - develop

env:
  JAVA_VERSION: '21'
  MAVEN_OPTS: '-Xmx1024m'

jobs:
  build:
    name: Build and Test
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      with:
        fetch-depth: 0
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: ${{ env.JAVA_VERSION }}
        distribution: 'temurin'
        cache: 'maven'
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Build with Maven
      run: mvn clean compile -B
    
    - name: Run Unit Tests
      run: mvn test -B
    
    - name: Run Integration Tests
      run: mvn verify -B -Pintegration-tests
    
    - name: Generate Test Coverage Report
      run: mvn jacoco:report
    
    - name: Check Coverage Threshold
      run: mvn jacoco:check -Djacoco.haltOnFailure=true
    
    - name: Upload Coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        files: ./target/site/jacoco/jacoco.xml
        flags: unittests
        name: codecov-carddemo
    
    - name: Package Application
      run: mvn package -DskipTests -B
    
    - name: Upload JAR Artifact
      uses: actions/upload-artifact@v3
      with:
        name: carddemo-jar
        path: target/carddemo-*.jar
        retention-days: 5
    
    - name: Build Docker Image
      run: |
        docker build -t carddemo:${{ github.sha }} .
        docker tag carddemo:${{ github.sha }} carddemo:latest
    
    - name: Scan Docker Image for Vulnerabilities
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: carddemo:${{ github.sha }}
        format: 'sarif'
        output: 'trivy-results.sarif'
    
    - name: Upload Trivy Results to GitHub Security
      uses: github/codeql-action/upload-sarif@v2
      if: always()
      with:
        sarif_file: 'trivy-results.sarif'
    
    - name: Configure AWS Credentials
      if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
      uses: aws-actions/configure-aws-credentials@v4
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: us-east-1
    
    - name: Login to Amazon ECR
      if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v2
    
    - name: Push Image to Amazon ECR
      if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: carddemo
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker tag carddemo:${{ github.sha }} $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
        docker tag carddemo:${{ github.sha }} $ECR_REGISTRY/$ECR_REPOSITORY:latest
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest
        
        echo "IMAGE_URI=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT
    
    - name: Generate SBOM
      uses: anchore/sbom-action@v0
      with:
        image: carddemo:${{ github.sha }}
        format: spdx-json
        output-file: sbom.spdx.json
    
    - name: Upload SBOM
      uses: actions/upload-artifact@v3
      with:
        name: sbom
        path: sbom.spdx.json
        retention-days: 30

  code-quality:
    name: Code Quality Analysis
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      with:
        fetch-depth: 0
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: ${{ env.JAVA_VERSION }}
        distribution: 'temurin'
        cache: 'maven'
    
    - name: Run SonarQube Scan
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        mvn sonar:sonar \
          -Dsonar.projectKey=carddemo \
          -Dsonar.host.url=${{ secrets.SONAR_HOST_URL }} \
          -Dsonar.login=${{ secrets.SONAR_TOKEN }}
```

### Continuous Deployment

Create `.github/workflows/cd.yml`:

```yaml
name: CD - Deploy to Kubernetes

on:
  workflow_run:
    workflows: ["CI - Build and Test"]
    types:
      - completed
    branches:
      - main
      - develop

env:
  AWS_REGION: us-east-1
  EKS_CLUSTER_NAME: carddemo-prod-cluster
  K8S_NAMESPACE: carddemo

jobs:
  deploy:
    name: Deploy to EKS
    runs-on: ubuntu-latest
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Configure AWS Credentials
      uses: aws-actions/configure-aws-credentials@v4
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}
    
    - name: Update kubeconfig
      run: |
        aws eks update-kubeconfig \
          --region ${{ env.AWS_REGION }} \
          --name ${{ env.EKS_CLUSTER_NAME }}
    
    - name: Verify Cluster Access
      run: kubectl get nodes
    
    - name: Create Namespace (if not exists)
      run: |
        kubectl create namespace ${{ env.K8S_NAMESPACE }} --dry-run=client -o yaml | kubectl apply -f -
    
    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v2
    
    - name: Get Image URI
      id: image
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: carddemo
        IMAGE_TAG: ${{ github.sha }}
      run: |
        echo "IMAGE_URI=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT
    
    - name: Update Deployment Image
      run: |
        sed -i "s|<AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/carddemo:latest|${{ steps.image.outputs.IMAGE_URI }}|g" k8s/deployment.yml
    
    - name: Apply Kubernetes Manifests
      run: |
        kubectl apply -f k8s/namespace.yml
        kubectl apply -f k8s/configmap.yml -n ${{ env.K8S_NAMESPACE }}
        kubectl apply -f k8s/secret.yml -n ${{ env.K8S_NAMESPACE }} || echo "Secrets already exist"
        kubectl apply -f k8s/serviceaccount.yml -n ${{ env.K8S_NAMESPACE }} || echo "ServiceAccount already exists"
        kubectl apply -f k8s/deployment.yml -n ${{ env.K8S_NAMESPACE }}
        kubectl apply -f k8s/service.yml -n ${{ env.K8S_NAMESPACE }}
        kubectl apply -f k8s/ingress.yml -n ${{ env.K8S_NAMESPACE }}
        kubectl apply -f k8s/hpa.yml -n ${{ env.K8S_NAMESPACE }}
    
    - name: Wait for Deployment Rollout
      run: |
        kubectl rollout status deployment/carddemo -n ${{ env.K8S_NAMESPACE }} --timeout=5m
    
    - name: Verify Deployment
      run: |
        kubectl get pods -n ${{ env.K8S_NAMESPACE }}
        kubectl get svc -n ${{ env.K8S_NAMESPACE }}
        kubectl get ingress -n ${{ env.K8S_NAMESPACE }}
    
    - name: Run Smoke Tests
      run: |
        LOAD_BALANCER_DNS=$(kubectl get svc carddemo-service -n ${{ env.K8S_NAMESPACE }} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
        echo "Load Balancer DNS: $LOAD_BALANCER_DNS"
        
        # Wait for load balancer to be ready
        sleep 60
        
        # Health check
        curl -f http://$LOAD_BALANCER_DNS:8080/actuator/health || exit 1
        echo "Health check passed"
        
        # API smoke test
        curl -f http://$LOAD_BALANCER_DNS:8080/api/v1/menu || exit 1
        echo "API smoke test passed"
    
    - name: Notify Deployment Success
      if: success()
      uses: 8398a7/action-slack@v3
      with:
        status: ${{ job.status }}
        text: 'CardDemo deployment to ${{ env.EKS_CLUSTER_NAME }} succeeded! :rocket:'
        webhook_url: ${{ secrets.SLACK_WEBHOOK_URL }}
    
    - name: Notify Deployment Failure
      if: failure()
      uses: 8398a7/action-slack@v3
      with:
        status: ${{ job.status }}
        text: 'CardDemo deployment to ${{ env.EKS_CLUSTER_NAME }} failed! :x:'
        webhook_url: ${{ secrets.SLACK_WEBHOOK_URL }}

  rollback:
    name: Rollback on Failure
    runs-on: ubuntu-latest
    needs: deploy
    if: failure()
    
    steps:
    - name: Configure AWS Credentials
      uses: aws-actions/configure-aws-credentials@v4
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}
    
    - name: Update kubeconfig
      run: |
        aws eks update-kubeconfig \
          --region ${{ env.AWS_REGION }} \
          --name ${{ env.EKS_CLUSTER_NAME }}
    
    - name: Rollback Deployment
      run: |
        kubectl rollout undo deployment/carddemo -n ${{ env.K8S_NAMESPACE }}
        kubectl rollout status deployment/carddemo -n ${{ env.K8S_NAMESPACE }} --timeout=5m
    
    - name: Notify Rollback
      uses: 8398a7/action-slack@v3
      with:
        status: 'warning'
        text: 'CardDemo deployment failed and was rolled back'
        webhook_url: ${{ secrets.SLACK_WEBHOOK_URL }}
```

### GitHub Secrets Configuration

Configure the following secrets in your GitHub repository (Settings → Secrets and variables → Actions):

| Secret Name | Description | Example Value |
|-------------|-------------|---------------|
| AWS_ACCESS_KEY_ID | AWS IAM access key | AKIAIOSFODNN7EXAMPLE |
| AWS_SECRET_ACCESS_KEY | AWS IAM secret key | wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY |
| AWS_ACCOUNT_ID | AWS account ID | 123456789012 |
| SONAR_TOKEN | SonarQube authentication token | sqp_abc123... |
| SONAR_HOST_URL | SonarQube server URL | https://sonarcloud.io |
| SLACK_WEBHOOK_URL | Slack webhook for notifications | https://hooks.slack.com/services/... |

## Configuration Management

### Spring Profiles

The application supports multiple Spring profiles for different environments:

#### Development Profile (`application-dev.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/carddemo
    username: postgres
    password: password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true

logging:
  level:
    root: INFO
    com.aws.carddemo: DEBUG
    org.springframework: DEBUG
    org.hibernate: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: "*"
```

#### Test Profile (`application-test.yml`)

```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:15-alpine:///carddemo
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false

logging:
  level:
    root: WARN
    com.aws.carddemo: DEBUG
```

#### Production Profile (`application-prod.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASS}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    baseline-on-migrate: false

logging:
  level:
    root: INFO
    com.aws.carddemo: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  file:
    name: /app/logs/carddemo.log

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

server:
  shutdown: graceful
  tomcat:
    threads:
      max: 200
      min-spare: 10
```

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| SPRING_PROFILES_ACTIVE | Active Spring profile | Yes | dev |
| DB_HOST | Database hostname | Yes | localhost |
| DB_PORT | Database port | Yes | 5432 |
| DB_NAME | Database name | Yes | carddemo |
| DB_USER | Database username | Yes | postgres |
| DB_PASS | Database password | Yes | - |
| JWT_SECRET | JWT signing secret (256-bit) | Yes | - |
| JWT_EXPIRATION_MS | JWT expiration time in milliseconds | No | 3600000 (1 hour) |
| SERVER_PORT | Application port | No | 8080 |
| LOGGING_LEVEL_ROOT | Root logging level | No | INFO |
| JAVA_OPTS | JVM options | No | (see Dockerfile) |

### Database Migrations

The application uses Flyway for database schema versioning and migrations.

#### Migration Scripts Location

```
src/main/resources/db/migration/
├── V1__create_tables.sql
├── V2__create_indexes.sql
├── V3__seed_reference_data.sql
└── V4__load_test_data.sql
```

#### Migration Execution

Migrations run automatically on application startup when `spring.flyway.enabled=true`.

Manual execution:

```bash
# Run migrations
mvn flyway:migrate

# Check migration status
mvn flyway:info

# Validate migrations
mvn flyway:validate

# Repair migration history (if needed)
mvn flyway:repair
```

#### Baseline Existing Database

For databases with existing schema:

```bash
mvn flyway:baseline -Dflyway.baselineVersion=1 -Dflyway.baselineDescription="Initial baseline"
```

## Operational Procedures

### Deployment Workflow

Complete production deployment checklist:

#### Pre-Deployment

1. **Code Review**
   - Ensure all PRs are reviewed and approved
   - Verify CI pipeline passed all tests
   - Check code coverage meets threshold (≥80%)

2. **Database Backup**
   ```bash
   # Create RDS snapshot
   aws rds create-db-snapshot \
     --db-instance-identifier carddemo-prod-db \
     --db-snapshot-identifier carddemo-$(date +%Y%m%d-%H%M%S)
   ```

3. **Notification**
   - Notify stakeholders of deployment window
   - Update status page

#### Deployment

1. **Deploy to Staging**
   ```bash
   # Deploy to staging environment first
   kubectl config use-context staging
   kubectl apply -f k8s/ -n carddemo-staging
   ```

2. **Run Smoke Tests**
   ```bash
   # Health check
   curl https://staging.carddemo.example.com/actuator/health
   
   # API tests
   newman run tests/postman/carddemo-api-tests.json \
     --environment tests/postman/staging-env.json
   ```

3. **Deploy to Production**
   ```bash
   # Switch to production context
   kubectl config use-context production
   
   # Apply manifests
   kubectl apply -f k8s/namespace.yml
   kubectl apply -f k8s/configmap.yml -n carddemo
   kubectl apply -f k8s/deployment.yml -n carddemo
   kubectl apply -f k8s/service.yml -n carddemo
   kubectl apply -f k8s/hpa.yml -n carddemo
   
   # Monitor rollout
   kubectl rollout status deployment/carddemo -n carddemo --watch
   ```

4. **Verify Deployment**
   ```bash
   # Check pod status
   kubectl get pods -n carddemo
   
   # Check logs
   kubectl logs -f deployment/carddemo -n carddemo
   
   # Health check
   curl https://carddemo.example.com/actuator/health
   ```

#### Post-Deployment

1. **Monitor Metrics**
   - Check CloudWatch dashboards
   - Monitor error rates in logs
   - Verify response times <200ms

2. **Smoke Tests**
   ```bash
   # Run production smoke tests
   newman run tests/postman/carddemo-api-tests.json \
     --environment tests/postman/prod-env.json
   ```

3. **Documentation**
   - Update deployment log
   - Document any issues encountered
   - Update runbook if necessary

### Scaling Operations

#### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment carddemo --replicas=5 -n carddemo

# Verify scaling
kubectl get pods -n carddemo

# Check HPA status
kubectl get hpa -n carddemo
```

#### Automatic Scaling via HPA

HPA automatically scales based on CPU/memory utilization (configured in `k8s/hpa.yml`):
- **Target CPU**: 70%
- **Target Memory**: 80%
- **Min Replicas**: 3
- **Max Replicas**: 10

Monitor HPA:

```bash
# Watch HPA in real-time
kubectl get hpa -n carddemo --watch

# Describe HPA for details
kubectl describe hpa carddemo-hpa -n carddemo
```

#### Cluster Autoscaling

EKS Cluster Autoscaler automatically scales worker nodes based on pod resource requests.

Check Cluster Autoscaler status:

```bash
# Get Cluster Autoscaler logs
kubectl logs -f deployment/cluster-autoscaler -n kube-system

# Get node status
kubectl get nodes
```

### Health Checks and Monitoring

#### Health Check Endpoints

| Endpoint | Purpose | Expected Response |
|----------|---------|-------------------|
| `/actuator/health` | Overall health | `{"status":"UP"}` |
| `/actuator/health/liveness` | Liveness probe | `{"status":"UP"}` |
| `/actuator/health/readiness` | Readiness probe | `{"status":"UP"}` |
| `/actuator/info` | Application info | Version, build info |
| `/actuator/metrics` | Metrics endpoint | Metric names |
| `/actuator/prometheus` | Prometheus metrics | Prometheus format |

#### Kubernetes Probes

**Liveness Probe**: Restarts container if unhealthy
- Initial Delay: 60s
- Period: 10s
- Timeout: 5s
- Failure Threshold: 3

**Readiness Probe**: Removes from service if not ready
- Initial Delay: 30s
- Period: 5s
- Timeout: 3s
- Failure Threshold: 3

**Startup Probe**: Allows slow startup (up to 5 minutes)
- Period: 10s
- Failure Threshold: 30

#### CloudWatch Monitoring

Key metrics to monitor:

| Metric | Threshold | Action |
|--------|-----------|--------|
| CPU Utilization | >80% | Scale up |
| Memory Utilization | >85% | Scale up |
| HTTP 5xx Errors | >1% | Investigate |
| Response Time P95 | >200ms | Investigate |
| Database Connections | >18/20 | Investigate |
| Disk Usage | >80% | Clean up logs |

Create CloudWatch Alarms:

```bash
# High CPU alarm
aws cloudwatch put-metric-alarm \
  --alarm-name carddemo-high-cpu \
  --alarm-description "Alert when CPU exceeds 80%" \
  --metric-name CPUUtilization \
  --namespace AWS/EKS \
  --statistic Average \
  --period 300 \
  --evaluation-periods 2 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --alarm-actions arn:aws:sns:us-east-1:123456789012:alerts
```

#### Prometheus and Grafana

If using Prometheus:

```bash
# Install Prometheus
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace

# Access Grafana
kubectl port-forward svc/prometheus-grafana 3000:80 -n monitoring
# Default credentials: admin / prom-operator

# Import CardDemo dashboard
# Dashboard ID: (create custom dashboard for CardDemo metrics)
```

### Logging

#### Log Aggregation

Logs are sent to CloudWatch Logs in JSON format.

View logs:

```bash
# Via kubectl
kubectl logs -f deployment/carddemo -n carddemo

# Via AWS CLI
aws logs tail /aws/eks/carddemo-prod-cluster/application --follow

# Filter by error level
aws logs filter-log-events \
  --log-group-name /aws/eks/carddemo-prod-cluster/application \
  --filter-pattern '{ $.level = "ERROR" }' \
  --start-time $(date -d '1 hour ago' +%s)000
```

#### Log Format

Application logs are in JSON format for structured logging:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.aws.carddemo.service.TransactionService",
  "message": "Transaction posted successfully",
  "transactionId": "TXN-12345",
  "accountId": "ACC-67890",
  "amount": 100.50,
  "traceId": "abc123-def456",
  "spanId": "xyz789"
}
```

#### Log Retention

Configure log retention policies:

```bash
# Set retention to 30 days
aws logs put-retention-policy \
  --log-group-name /aws/eks/carddemo-prod-cluster/application \
  --retention-in-days 30
```

### Backup and Recovery

#### Database Backups

**Automated Backups** (configured via Terraform):
- Daily automated snapshots
- Retention: 30 days
- Backup window: 03:00-04:00 UTC

**Manual Snapshots**:

```bash
# Create snapshot
aws rds create-db-snapshot \
  --db-instance-identifier carddemo-prod-db \
  --db-snapshot-identifier carddemo-manual-$(date +%Y%m%d)

# List snapshots
aws rds describe-db-snapshots \
  --db-instance-identifier carddemo-prod-db

# Restore from snapshot
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier carddemo-restored \
  --db-snapshot-identifier carddemo-manual-20240115
```

**Point-in-Time Recovery**:

```bash
# Restore to specific time
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier carddemo-prod-db \
  --target-db-instance-identifier carddemo-pitr-recovery \
  --restore-time 2024-01-15T10:00:00Z
```

#### Application State

Application is stateless - no local state to backup. Configuration stored in:
- **ConfigMaps**: Backed up in Git
- **Secrets**: Backed up in AWS Secrets Manager
- **Deployment manifests**: Versioned in Git

## Troubleshooting

### Common Issues

#### Issue: Pods in CrashLoopBackOff

**Symptoms**:
```bash
kubectl get pods -n carddemo
# NAME                        READY   STATUS             RESTARTS   AGE
# carddemo-7d8f5c9b4c-abc12   0/1     CrashLoopBackOff   5          3m
```

**Diagnosis**:
```bash
# Check pod logs
kubectl logs carddemo-7d8f5c9b4c-abc12 -n carddemo

# Check pod events
kubectl describe pod carddemo-7d8f5c9b4c-abc12 -n carddemo
```

**Common Causes & Solutions**:

1. **Database Connection Failure**
   - Verify database credentials in Secret
   - Check DB_HOST points to correct RDS endpoint
   - Verify security group allows traffic from EKS
   - Test database connectivity:
   ```bash
   kubectl run -it --rm debug --image=postgres:15 --restart=Never -- \
     psql -h carddemo-db.c9xyz.us-east-1.rds.amazonaws.com -U carddemo_user -d carddemo
   ```

2. **Missing Environment Variables**
   - Verify ConfigMap exists: `kubectl get configmap -n carddemo`
   - Verify Secret exists: `kubectl get secret -n carddemo`
   - Check deployment references correct ConfigMap/Secret

3. **Application Startup Failure**
   - Check Java errors in logs
   - Verify Flyway migrations succeeded
   - Check for port conflicts

#### Issue: Pod OOMKilled (Out of Memory)

**Symptoms**:
```bash
kubectl get pods -n carddemo
# NAME                        READY   STATUS      RESTARTS   AGE
# carddemo-7d8f5c9b4c-abc12   0/1     OOMKilled   1          5m
```

**Diagnosis**:
```bash
# Check resource limits
kubectl describe pod carddemo-7d8f5c9b4c-abc12 -n carddemo | grep -A 5 "Limits"
```

**Solution**:
```bash
# Increase memory limits in k8s/deployment.yml
# Change from:
#   limits:
#     memory: "1Gi"
# To:
#   limits:
#     memory: "2Gi"

# Apply changes
kubectl apply -f k8s/deployment.yml -n carddemo
```

#### Issue: ImagePullBackOff

**Symptoms**:
```bash
kubectl get pods -n carddemo
# NAME                        READY   STATUS             RESTARTS   AGE
# carddemo-7d8f5c9b4c-abc12   0/1     ImagePullBackOff   0          2m
```

**Diagnosis**:
```bash
# Check image pull errors
kubectl describe pod carddemo-7d8f5c9b4c-abc12 -n carddemo | grep -A 10 "Events"
```

**Common Causes & Solutions**:

1. **ECR Authentication Failure**
   ```bash
   # Verify ECR login
   aws ecr get-login-password --region us-east-1 | \
     docker login --username AWS --password-stdin <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com
   
   # Update imagePullSecrets in deployment if needed
   ```

2. **Image Does Not Exist**
   ```bash
   # List images in ECR
   aws ecr describe-images --repository-name carddemo --region us-east-1
   
   # Verify image tag in deployment matches ECR
   ```

3. **IAM Permissions**
   - Verify worker node IAM role has `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage`

#### Issue: Service Not Accessible

**Symptoms**:
- Cannot reach application via LoadBalancer DNS
- Health check returns 503

**Diagnosis**:
```bash
# Check service
kubectl get svc -n carddemo

# Check endpoints
kubectl get endpoints -n carddemo

# Check ingress
kubectl get ingress -n carddemo
```

**Solutions**:

1. **No Endpoints (Pods Not Ready)**
   ```bash
   # Check readiness probe
   kubectl describe pod <pod-name> -n carddemo | grep -A 10 "Readiness"
   
   # Check application logs
   kubectl logs <pod-name> -n carddemo
   ```

2. **LoadBalancer Not Provisioned**
   ```bash
   # Check service annotations
   kubectl describe svc carddemo-service -n carddemo
   
   # Verify ALB/NLB creation
   aws elbv2 describe-load-balancers --region us-east-1
   ```

3. **Security Group Issues**
   ```bash
   # Check security groups on load balancer
   # Ensure inbound rules allow port 8080 from 0.0.0.0/0
   ```

#### Issue: High CPU/Memory Usage

**Diagnosis**:
```bash
# Check resource usage
kubectl top pods -n carddemo

# Check HPA status
kubectl get hpa -n carddemo
```

**Solutions**:

1. **Scale Up**
   ```bash
   kubectl scale deployment carddemo --replicas=10 -n carddemo
   ```

2. **Investigate Performance**
   - Check slow queries in RDS Performance Insights
   - Review application logs for errors
   - Profile application with JVM tools

3. **Optimize Resources**
   - Tune JVM parameters in JAVA_OPTS
   - Optimize database queries
   - Add caching for frequently accessed data

### Debugging Techniques

#### Exec into Running Pod

```bash
# Get shell in pod
kubectl exec -it <pod-name> -n carddemo -- /bin/sh

# Run commands inside container
ps aux
netstat -tuln
env | grep DB
```

#### Port Forwarding for Local Access

```bash
# Forward pod port to localhost
kubectl port-forward <pod-name> 8080:8080 -n carddemo

# Access application locally
curl http://localhost:8080/actuator/health
```

#### View Pod Events

```bash
# Get recent events for pod
kubectl get events --field-selector involvedObject.name=<pod-name> -n carddemo --sort-by='.lastTimestamp'
```

#### Debug with Ephemeral Container

```bash
# Add debug container to running pod (Kubernetes 1.23+)
kubectl debug -it <pod-name> -n carddemo --image=busybox --target=carddemo
```

#### Check Network Connectivity

```bash
# Test DNS resolution
kubectl run -it --rm debug --image=busybox --restart=Never -- nslookup carddemo-service.carddemo.svc.cluster.local

# Test service connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://carddemo-service.carddemo.svc.cluster.local:8080/actuator/health
```

### Rollback Procedures

#### Rollback Kubernetes Deployment

```bash
# View rollout history
kubectl rollout history deployment/carddemo -n carddemo

# Rollback to previous revision
kubectl rollout undo deployment/carddemo -n carddemo

# Rollback to specific revision
kubectl rollout undo deployment/carddemo --to-revision=5 -n carddemo

# Monitor rollback
kubectl rollout status deployment/carddemo -n carddemo --watch
```

#### Rollback Database Changes

```bash
# Flyway repair (if needed)
kubectl exec -it <pod-name> -n carddemo -- \
  java -jar app.jar flyway:repair

# Manual rollback (if Flyway doesn't support)
# Connect to database and run rollback SQL scripts
```

#### Rollback Infrastructure Changes

```bash
cd terraform

# View previous state
terraform show

# Revert to previous Terraform state
terraform apply -var-file=terraform.tfvars.backup

# Or restore from state backup
aws s3 cp s3://carddemo-terraform-state/backup/terraform.tfstate.backup \
  s3://carddemo-terraform-state/prod/terraform.tfstate
```

## Security Considerations

### Container Security

- **Non-root user**: Application runs as user 1000, not root
- **Read-only root filesystem**: Configured in securityContext (where possible)
- **Drop capabilities**: All capabilities dropped except required ones
- **Image scanning**: Trivy scans in CI pipeline
- **SBOM**: Software Bill of Materials generated for each image

### Network Security

- **Network Policies**: Restrict pod-to-pod communication
- **Ingress TLS**: HTTPS only, TLS 1.3
- **Security Groups**: Restrict database access to EKS only
- **Private Subnets**: Application pods in private subnets

### Secrets Management

- **No hardcoded secrets**: All secrets in Kubernetes Secrets or AWS Secrets Manager
- **Sealed Secrets**: Encrypted secrets in Git
- **IAM Roles**: IRSA for pod-level IAM permissions
- **Rotation**: Secrets rotated every 90 days

### Compliance

- **PCI-DSS**: Card numbers masked in logs, encrypted at rest
- **SOC 2**: Audit logging enabled
- **GDPR**: Data encryption, backup retention policies

## Performance Tuning

### Application Tuning

#### JVM Options

```yaml
env:
- name: JAVA_OPTS
  value: >-
    -XX:+UseG1GC
    -XX:MaxRAMPercentage=75.0
    -XX:+UseStringDeduplication
    -XX:+ParallelRefProcEnabled
    -XX:MaxGCPauseMillis=200
    -XX:+UnlockExperimentalVMOptions
    -XX:+UseJVMCICompiler
    -Djava.security.egd=file:/dev/./urandom
```

#### Connection Pooling

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

### Database Tuning

#### RDS Parameter Group

Optimize PostgreSQL parameters:
- `shared_buffers`: 25% of RAM
- `effective_cache_size`: 75% of RAM
- `work_mem`: 64MB
- `maintenance_work_mem`: 512MB
- `max_connections`: 200

#### Indexes

Ensure indexes exist on frequently queried columns:
- Account ID, Card Number
- Transaction date ranges
- User username

### Kubernetes Resource Optimization

#### Resource Requests and Limits

Set appropriate values based on load testing:

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

#### HPA Tuning

Adjust HPA thresholds based on observed metrics:
- Target CPU: 60-70%
- Target Memory: 70-80%
- Scale-up quickly, scale-down slowly

## Support

### Getting Help

- **Documentation**: https://github.com/aws-samples/aws-card-demo-modernized/docs
- **Issues**: https://github.com/aws-samples/aws-card-demo-modernized/issues
- **Slack**: #carddemo-support (internal)
- **Email**: devops-team@example.com

### Reporting Issues

When reporting issues, include:
1. Description of the problem
2. Steps to reproduce
3. Expected vs. actual behavior
4. Environment (dev/test/prod)
5. Logs and error messages
6. Screenshots (if applicable)

### Incident Response

For production incidents:
1. Check status page
2. Review CloudWatch alarms
3. Check recent deployments
4. Review application logs
5. Engage on-call engineer if needed

### Maintenance Windows

Regular maintenance:
- **Database**: Sunday 03:00-04:00 UTC (automated backups)
- **Kubernetes**: First Sunday of month 02:00-06:00 UTC (cluster upgrades)
- **Application**: Rolling updates (zero downtime)

---

**Document Version**: 1.0.0  
**Last Updated**: 2024-01-15  
**Maintained By**: DevOps Team  
**License**: Apache 2.0

For questions or updates to this guide, please contact the Platform Engineering team or submit a pull request to the repository.
