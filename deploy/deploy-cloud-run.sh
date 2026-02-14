#!/bin/bash
set -euo pipefail

# YAWL Cloud Run Deployment Script
# Alternative lightweight deployment option using Cloud Run

PROJECT_ID="${GCP_PROJECT_ID:?Error: GCP_PROJECT_ID not set}"
REGION="${GCP_REGION:-us-central1}"
SERVICE_NAME="yawl-workflow"
IMAGE_REGION="${REGION}"

echo "=== YAWL Cloud Run Deployment ==="
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo ""

# Set project
gcloud config set project "$PROJECT_ID"
gcloud config set compute/region "$REGION"

# Build image
echo "Building Docker image..."
ARTIFACT_REGISTRY="${IMAGE_REGION}-docker.pkg.dev/${PROJECT_ID}/yawl"
IMAGE_URI="${ARTIFACT_REGISTRY}/yawl:$(git rev-parse --short HEAD)"

docker build -t "$IMAGE_URI" -f Dockerfile .
gcloud auth configure-docker "${IMAGE_REGION}-docker.pkg.dev"
docker push "$IMAGE_URI"

# Create Cloud SQL instance
echo "Setting up Cloud SQL..."
gcloud sql instances create yawl-postgres \
  --database-version=POSTGRES_14 \
  --tier=db-custom-2-7680 \
  --region=$REGION \
  --network=default \
  --no-backup || true

# Create database and user
gcloud sql databases create yawl --instance=yawl-postgres || true
gcloud sql users create yawl --instance=yawl-postgres --password=yawl-secure-password || true

# Create service account for Cloud Run
echo "Creating service account..."
gcloud iam service-accounts create yawl-cloudruns \
  --display-name="YAWL Cloud Run Service Account" || true

# Grant Cloud SQL Client role
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:yawl-cloudruns@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client" || true

# Deploy to Cloud Run
echo "Deploying to Cloud Run..."
gcloud run deploy "$SERVICE_NAME" \
  --image="$IMAGE_URI" \
  --region="$REGION" \
  --platform=managed \
  --memory=2Gi \
  --cpu=2 \
  --timeout=3600 \
  --max-instances=100 \
  --service-account="yawl-cloudruns@${PROJECT_ID}.iam.gserviceaccount.com" \
  --set-cloudsql-instances="${PROJECT_ID}:${REGION}:yawl-postgres" \
  --set-env-vars="YAWL_DB_HOST=/cloudsql/${PROJECT_ID}:${REGION}:yawl-postgres,YAWL_DB_NAME=yawl,YAWL_DB_USER=yawl" \
  --set-secrets="YAWL_DB_PASSWORD=yawl-db-password:latest" \
  --allow-unauthenticated

echo ""
echo "=== Deployment Complete ==="
SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')
echo "Service URL: $SERVICE_URL"
echo "YAWL available at: ${SERVICE_URL}/resourceService"
