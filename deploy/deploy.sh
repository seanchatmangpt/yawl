#!/bin/bash
set -euo pipefail

# YAWL GCP Marketplace Deployment Script
# This script deploys YAWL to Google Cloud Platform

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:?Error: GCP_PROJECT_ID not set}"
REGION="${GCP_REGION:-us-central1}"
CLUSTER_NAME="yawl-cluster"
NAMESPACE="yawl"
IMAGE_NAME="yawl"

echo -e "${GREEN}=== YAWL Deployment Script ===${NC}"
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo "Cluster: $CLUSTER_NAME"
echo ""

# Step 1: Authenticate with GCP
echo -e "${YELLOW}Step 1: Authenticating with GCP...${NC}"
gcloud auth application-default login || true
gcloud config set project "$PROJECT_ID"
gcloud config set compute/region "$REGION"

# Step 2: Build Docker image
echo -e "${YELLOW}Step 2: Building Docker image...${NC}"
ARTIFACT_REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/yawl"
IMAGE_URI="${ARTIFACT_REGISTRY}/${IMAGE_NAME}:$(git rev-parse --short HEAD)"
IMAGE_LATEST="${ARTIFACT_REGISTRY}/${IMAGE_NAME}:latest"

docker build -t "$IMAGE_URI" -t "$IMAGE_LATEST" -f Dockerfile .

# Step 3: Configure Docker authentication
echo -e "${YELLOW}Step 3: Configuring Docker authentication...${NC}"
gcloud auth configure-docker "${REGION}-docker.pkg.dev"

# Step 4: Push Docker image
echo -e "${YELLOW}Step 4: Pushing Docker image...${NC}"
docker push "$IMAGE_URI"
docker push "$IMAGE_LATEST"
echo -e "${GREEN}✓ Image pushed to $IMAGE_URI${NC}"

# Step 5: Deploy infrastructure with Terraform
echo -e "${YELLOW}Step 5: Deploying infrastructure with Terraform...${NC}"
cd terraform
terraform init \
  -backend-config="bucket=${PROJECT_ID}-terraform-state" \
  -backend-config="prefix=prod"

terraform plan -var-file="terraform.tfvars" -out=tfplan
terraform apply tfplan
cd ..

# Step 6: Get cluster credentials
echo -e "${YELLOW}Step 6: Getting cluster credentials...${NC}"
gcloud container clusters get-credentials "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID"

# Step 7: Create namespace
echo -e "${YELLOW}Step 7: Creating Kubernetes namespace...${NC}"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Step 8: Create secrets
echo -e "${YELLOW}Step 8: Creating Kubernetes secrets...${NC}"
DB_PASSWORD=$(terraform output -raw db_password)
kubectl create secret generic yawl-db-secret \
  --from-literal=password="$DB_PASSWORD" \
  --from-literal=username=yawl \
  -n "$NAMESPACE" \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 9: Deploy Kubernetes manifests
echo -e "${YELLOW}Step 9: Deploying Kubernetes manifests...${NC}"
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/rbac.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/autoscaling.yaml
kubectl apply -f k8s/network-policy.yaml

# Step 10: Wait for deployment to be ready
echo -e "${YELLOW}Step 10: Waiting for deployment to be ready...${NC}"
kubectl rollout status deployment/yawl -n "$NAMESPACE" --timeout=5m

# Step 11: Verify deployment
echo -e "${YELLOW}Step 11: Verifying deployment...${NC}"
READY_REPLICAS=$(kubectl get deployment yawl -n "$NAMESPACE" -o jsonpath='{.status.readyReplicas}')
DESIRED_REPLICAS=$(kubectl get deployment yawl -n "$NAMESPACE" -o jsonpath='{.spec.replicas}')

if [ "$READY_REPLICAS" == "$DESIRED_REPLICAS" ]; then
  echo -e "${GREEN}✓ Deployment successful! ${READY_REPLICAS}/${DESIRED_REPLICAS} replicas running${NC}"
else
  echo -e "${RED}✗ Deployment not ready. ${READY_REPLICAS}/${DESIRED_REPLICAS} replicas running${NC}"
  exit 1
fi

# Step 12: Get service endpoint
echo -e "${YELLOW}Step 12: Getting service endpoint...${NC}"
EXTERNAL_IP=$(kubectl get service yawl -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
if [ -z "$EXTERNAL_IP" ]; then
  echo -e "${YELLOW}⚠ Load Balancer IP not yet assigned. Trying again in 30 seconds...${NC}"
  sleep 30
  EXTERNAL_IP=$(kubectl get service yawl -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
fi

echo -e "${GREEN}=== Deployment Complete ===${NC}"
echo ""
echo "Service accessible at: http://$EXTERNAL_IP"
echo "Resource Service: http://$EXTERNAL_IP/resourceService"
echo ""
echo "Next steps:"
echo "1. Configure DNS pointing to: $EXTERNAL_IP"
echo "2. Set up SSL certificates"
echo "3. Configure authentication"
echo "4. Access YAWL at: http://$EXTERNAL_IP/resourceService"
