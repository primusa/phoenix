#!/usr/bin/env bash
set -euo pipefail

KEY_NAME="${1:-}"
REGION="${2:-us-east-1}"
OVERRIDE_VPC="${3:-}"
OVERRIDE_PUBLIC_SUBNETS="${4:-}"
STACK_NAME="phoenix-stack"
CFN_ROLE_ARN="${CFN_ROLE_ARN:-}"

if [ -z "$KEY_NAME" ]; then
  echo "Usage: $0 <ec2-keypair-name> [aws-region] [vpc-id] [public-subnets-comma-separated]"
  exit 2
fi

echo "Preparing deployment for stack $STACK_NAME in $REGION..."

# Determine VPC and PublicSubnets: prefer overrides (args or env), otherwise attempt lookup
if [ -n "$OVERRIDE_VPC" ]; then
  DEFAULT_VPC="$OVERRIDE_VPC"
  echo "Using VPC from argument: $DEFAULT_VPC"
elif [ -n "${CFN_VPC_ID:-}" ]; then
  DEFAULT_VPC="$CFN_VPC_ID"
  echo "Using VPC from env CFN_VPC_ID: $DEFAULT_VPC"
else
  echo "Looking up VPC named 'project-phoenix-vpc'..."
  DEFAULT_VPC=$(aws ec2 describe-vpcs \
    --region "$REGION" \
    --filters "Name=tag:Name,Values=project-phoenix-vpc" \
    --query 'Vpcs[0].VpcId' \
    --output text)
fi

if [ -z "$DEFAULT_VPC" ] || [ "$DEFAULT_VPC" = "None" ]; then
  echo "Error: VPC not found. Provide VPC by tagging a VPC 'project-phoenix-vpc' or pass the VPC ID as third argument or set CFN_VPC_ID env var."
  exit 1
fi

if [ -n "$OVERRIDE_PUBLIC_SUBNETS" ]; then
  PUBLIC_SUBNETS="$OVERRIDE_PUBLIC_SUBNETS"
  echo "Using PublicSubnets from argument: $PUBLIC_SUBNETS"
elif [ -n "${CFN_PUBLIC_SUBNETS:-}" ]; then
  PUBLIC_SUBNETS="$CFN_PUBLIC_SUBNETS"
  echo "Using PublicSubnets from env CFN_PUBLIC_SUBNETS: $PUBLIC_SUBNETS"
else
  echo "Looking up public subnets in VPC $DEFAULT_VPC..."
  SUBNET_ARRAY=$(aws ec2 describe-subnets \
    --region "$REGION" \
    --filters "Name=vpc-id,Values=$DEFAULT_VPC" \
    --query 'Subnets[*].SubnetId' \
    --output text)

  if [ -z "$SUBNET_ARRAY" ]; then
    echo "Error: No subnets found in VPC $DEFAULT_VPC"
    exit 1
  fi

  SUBNET_LIST=()
  for subnet in $SUBNET_ARRAY; do
    if [[ "$subnet" =~ ^subnet- ]]; then
      SUBNET_LIST+=("$subnet")
    fi
  done

  SUBNET_COUNT=${#SUBNET_LIST[@]}
  if [ "$SUBNET_COUNT" -lt 2 ]; then
    echo "Error: Found only $SUBNET_COUNT subnet(s), but at least 2 are recommended for ALB."
    echo "Available subnets: $(IFS=,; echo "${SUBNET_LIST[*]}")"
    exit 1
  fi

  PUBLIC_SUBNETS="${SUBNET_LIST[0]},${SUBNET_LIST[1]}"
  echo "Using subnets: $PUBLIC_SUBNETS (found $SUBNET_COUNT total)"
fi

# Get stack status if it exists
STACK_STATUS=$(aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --query 'Stacks[0].StackStatus' \
  --output text 2>/dev/null || echo "DOES_NOT_EXIST")

# Handle failed/rollback states
if [ "$STACK_STATUS" = "ROLLBACK_COMPLETE" ] || [ "$STACK_STATUS" = "ROLLBACK_FAILED" ] || [ "$STACK_STATUS" = "CREATE_FAILED" ] || [ "$STACK_STATUS" = "UPDATE_FAILED" ] || [ "$STACK_STATUS" = "UPDATE_ROLLBACK_FAILED" ]; then
  echo "Stack is in $STACK_STATUS state. Deleting stack before redeployment..."
  aws cloudformation delete-stack \
    --stack-name "$STACK_NAME" \
    --region "$REGION"
  echo "Waiting for stack deletion to complete..."
  aws cloudformation wait stack-delete-complete \
    --stack-name "$STACK_NAME" \
    --region "$REGION" 2>/dev/null || true
  echo "Stack deleted successfully."
fi

echo "Deploying CloudFormation stack $STACK_NAME to $REGION"
DEPLOY_CMD=(aws cloudformation deploy --template-file aws/cloudformation/stack.yaml --stack-name "$STACK_NAME" --capabilities CAPABILITY_NAMED_IAM --parameter-overrides KeyName="$KEY_NAME" VpcId="$DEFAULT_VPC" PublicSubnets="$PUBLIC_SUBNETS" Environment="development" CostCenter="engineering" --region "$REGION")

if [ -n "$CFN_ROLE_ARN" ]; then
  DEPLOY_CMD+=(--role-arn "$CFN_ROLE_ARN")
fi

"${DEPLOY_CMD[@]}"

echo "Deployment finished"
