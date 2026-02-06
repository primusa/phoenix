#!/usr/bin/env bash
set -euo pipefail

REPO_NAME="$1"
VISIBILITY="${2:-public}"

if [ -z "${REPO_NAME}" ]; then
  echo "Usage: $0 <owner/repo> [public|private]"
  echo "Example: $0 my-org/phoenix public"
  exit 2
fi

# Ensure git remote is configured and push (assumes repo is already created)
if ! git remote get-url origin >/dev/null 2>&1; then
  echo "Setting remote origin to github.com/$REPO_NAME"
  git remote add origin "https://github.com/$REPO_NAME.git"
fi

echo "Pushing to origin..."
git push -u origin main 2>/dev/null || git push -u origin master 2>/dev/null || {
  echo "Push failed. Repository may not exist on GitHub yet."
  echo "Create it manually: https://github.com/new"
  echo "Then run: git push -u origin main"
  exit 1
}

echo "Done. Repository available at: https://github.com/$REPO_NAME"
