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
    echo "Checking $repo for images..."
    # Get all image digests in a format batch-delete-image understands
    DIGESTS=$(aws ecr list-images --repository-name "$repo" --region "$REGION" --query 'imageIds[*]' --output json)

    if [ "$DIGESTS" != "[]" ] && [ "$DIGESTS" != "" ]; then
      echo "Deleting images in $repo..."
      # Use the json input directly to batch-delete-image
      aws ecr batch-delete-image --repository-name "$repo" --region "$REGION" --image-ids "$DIGESTS" >/dev/null || echo "Some images could not be deleted"
    else
      echo "No images found in $repo."
    fi
  done
else
  echo "✅ Skipping cleanup. Old images will be preserved."
fi

# 3. Build and Push
# (The GitHub Action 'docker/setup-buildx-action' handles builder creation)
echo "Starting Docker builds..."

for repo in "${REPOS[@]}"; do
  URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$repo"
  echo "Building and pushing $repo for linux/amd64 and linux/arm64..."
  
  # --load won't work with multi-platform, so we use --push directly
  docker buildx build \
    --platform linux/arm64,linux/amd64 \
    -t "$URI:latest" \
    --push \
    "./$repo"
    
  echo "✅ Published $URI:latest"
done