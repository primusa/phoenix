#!/usr/bin/env bash
set -euo pipefail

REPO_NAME="${1:-phoenix-service}"
REGION="${2:-us-east-1}"

if [ -z "${REPO_NAME}" ] ; then
  echo "Usage: $0 [backend-repo-name] [aws-region]"
  exit 2
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_BACKEND_URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/phoenix-service"
ECR_UI_URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/phoenix-ui"

echo "Ensuring ECR repositories exist..."
aws ecr describe-repositories --repository-names "phoenix-service" --region "$REGION" >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name "phoenix-service" --region "$REGION" >/dev/null
aws ecr describe-repositories --repository-names "phoenix-ui" --region "$REGION" >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name "phoenix-ui" --region "$REGION" >/dev/null

echo "=============================================="
echo "Building and pushing phoenix-service (backend)"
echo "=============================================="
docker buildx create --use --driver docker-container || true
docker buildx build --platform linux/arm64,linux/amd64 -t "$ECR_BACKEND_URI:latest" --push phoenix-service
echo "Published $ECR_BACKEND_URI:latest"

echo ""
echo "=============================================="
echo "Building and pushing phoenix-ui (frontend)"
echo "=============================================="
docker buildx build --platform linux/arm64,linux/amd64 -t "$ECR_UI_URI:latest" --push phoenix-ui
echo "Published $ECR_UI_URI:latest"

echo ""
echo "=============================================="
echo "Build and push complete!"
echo "Backend:  $ECR_BACKEND_URI:latest"
echo "Frontend: $ECR_UI_URI:latest"
echo "=============================================="
