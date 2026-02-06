#!/usr/bin/env bash
set -euo pipefail

REPO_NAME="$1"
REGION="$2"

if [ -z "${REPO_NAME}" ] ; then
  echo "Usage: $0 <ecr-repo-name> <aws-region>"
  exit 2
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$REPO_NAME"

echo "Ensure ECR repository exists: $ECR_URI"
aws ecr describe-repositories --repository-names "$REPO_NAME" --region "$REGION" >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name "$REPO_NAME" --region "$REGION" >/dev/null

echo "Building and pushing multi-arch image to ECR ($ECR_URI:latest)"
docker buildx create --use --driver docker-container || true
docker buildx build --platform linux/arm64,linux/amd64 -t "$ECR_URI:latest" --push phoenix-service

echo "Published $ECR_URI:latest"
