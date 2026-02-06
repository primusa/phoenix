#!/usr/bin/env bash
set -euo pipefail

KEY_NAME="${1:-}"
REGION="${2:-us-east-2}"
STACK_NAME="phoenix-stack"

if [ -z "$KEY_NAME" ]; then
  echo "Usage: $0 <ec2-keypair-name> [aws-region]"
  exit 2
fi

echo "Deploying CloudFormation stack $STACK_NAME to $REGION"
aws cloudformation deploy \
  --template-file aws/cloudformation/stack.yaml \
  --stack-name "$STACK_NAME" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides KeyName="$KEY_NAME" \
  --region "$REGION"

echo "Deployment finished"
