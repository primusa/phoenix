#!/usr/bin/env bash
set -euo pipefail

# Inputs
REPO_NAME="${1:-phoenix-service}"
REGION="${2:-us-east-1}"
CLEANUP="${3:-false}" # New optional flag: pass "true" to wipe images

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REPOS=("phoenix-service" "phoenix-ui")

# 1. Ensure Repos exist
for repo in "${REPOS[@]}"; do
  aws ecr describe-repositories --repository-names "$repo" --region "$REGION" >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name "$repo" --region "$REGION" >/dev/null
done

# 2. Optional Cleanup (Wipe all images if CLEANUP=true)
if [ "$CLEANUP" = "true" ]; then
  echo "⚠️ CLEANUP=true: Wiping existing images from repositories..."
  for repo in "${REPOS[@]}"; do
    # Get all image digests
    DIGESTS=$(aws ecr list-images --repository-name "$repo" --region "$REGION" --query 'imageIds[*]' --output json)

    # Only attempt delete if repo is not empty
    if [ "$DIGESTS" != "[]" ]; then
      echo "Deleting images in $repo..."
      aws ecr batch-delete-image --repository-name "$repo" --region "$REGION" --image-ids "$DIGESTS" >/dev/null
    fi
  done
else
  echo "✅ Skipping cleanup. Old images will be preserved."
fi

# 3. Build and Push
docker buildx create --use --driver docker-container || true

for repo in "${REPOS[@]}"; do
  URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$repo"
  echo "Building and pushing $repo..."
  docker buildx build --platform linux/arm64,linux/amd64 -t "$URI:latest" --push "./$repo"
  echo "Published $URI:latest"
done