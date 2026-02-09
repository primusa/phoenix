#!/usr/bin/env bash
set -euo pipefail

KEY_NAME="${1:-}"
REGION="${2:-us-east-1}"
OVERRIDE_VPC="${3:-}"
OVERRIDE_PUBLIC_SUBNETS="${4:-}"
SERVICE_REPO="${5:-phoenix-service}"
UI_REPO="${6:-phoenix-ui}"
CREATE_ALB="${7:-false}"
STACK_NAME="phoenix-stack"
CFN_ROLE_ARN="${CFN_ROLE_ARN:-}"

if [ -z "$KEY_NAME" ]; then
  echo "Usage: $0 <ec2-keypair-name> [aws-region] [vpc-id] [public-subnets] [service-repo] [ui-repo] [create-alb]"
  exit 2
fi

echo "-------------------------------------------------------"
echo "🔍 PRE-FLIGHT CHECKS"
echo "-------------------------------------------------------"

# 1. Verify KeyPair
echo -n "Checking KeyPair '$KEY_NAME' in $REGION... "
if ! aws ec2 describe-key-pairs --key-name "$KEY_NAME" --region "$REGION" >/dev/null 2>&1; then
    echo "❌ NOT FOUND"
    exit 1
fi
echo "✅ OK"

# Determine VPC
if [ -n "$OVERRIDE_VPC" ]; then
  DEFAULT_VPC="$OVERRIDE_VPC"
elif [ -n "${CFN_VPC_ID:-}" ]; then
  DEFAULT_VPC="$CFN_VPC_ID"
else
  DEFAULT_VPC=$(aws ec2 describe-vpcs --region "$REGION" --filters "Name=tag:Name,Values=project-phoenix-vpc" --query 'Vpcs[0].VpcId' --output text)
fi

if [ "$DEFAULT_VPC" = "None" ] || [ -z "$DEFAULT_VPC" ]; then echo "❌ VPC not found"; exit 1; fi

# Determine Subnets
if [ -n "$OVERRIDE_PUBLIC_SUBNETS" ]; then
  PUBLIC_SUBNETS=$(echo "$OVERRIDE_PUBLIC_SUBNETS" | tr -d ' ')
else
  SUBNET_ARRAY=$(aws ec2 describe-subnets --region "$REGION" --filters "Name=vpc-id,Values=$DEFAULT_VPC" --query 'Subnets[*].SubnetId' --output text)
  PUBLIC_SUBNETS=$(echo $SUBNET_ARRAY | sed 's/ /,/g')
fi

# 2. Final Validation: Do these subnets actually belong to that VPC?
IFS=',' read -ra ADDR <<< "$PUBLIC_SUBNETS"
for i in "${ADDR[@]}"; do
    V_ID=$(aws ec2 describe-subnets --subnet-ids "$i" --region "$REGION" --query 'Subnets[0].VpcId' --output text 2>/dev/null || echo "MISSING")
    if [ "$V_ID" != "$DEFAULT_VPC" ]; then
        echo "❌ Error: Subnet $i is not in VPC $DEFAULT_VPC (found in $V_ID)"
        exit 1
    fi
done

# Cleanup failed state
STACK_STATUS=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo "DOES_NOT_EXIST")
if [[ "$STACK_STATUS" == *"FAILED"* || "$STACK_STATUS" == *"ROLLBACK"* || "$STACK_STATUS" == *"REVIEW_IN_PROGRESS"* ]]; then
  echo "Cleaning up stack in $STACK_STATUS state..."
  aws cloudformation delete-stack --stack-name "$STACK_NAME" --region "$REGION"
  aws cloudformation wait stack-delete-complete --stack-name "$STACK_NAME" --region "$REGION"
elif [[ "$STACK_STATUS" == *"DELETE_IN_PROGRESS"* ]]; then
  echo "Stack is already being deleted. Please wait..."
  aws cloudformation wait stack-delete-complete --stack-name "$STACK_NAME" --region "$REGION"
fi

# Build Deployment Command
DEPLOY_CMD=(aws cloudformation deploy
    --template-file aws/cloudformation/stack.yaml
    --stack-name "$STACK_NAME"
    --capabilities CAPABILITY_NAMED_IAM
    --parameter-overrides
        KeyName="$KEY_NAME"
        VpcId="$DEFAULT_VPC"
        PublicSubnets="$PUBLIC_SUBNETS"
        Environment="development"
        CostCenter="engineering"
        PhoenixServiceRepoName="$SERVICE_REPO"
        PhoenixUiRepoName="$UI_REPO"
        CreateALB="$CREATE_ALB"
    --region "$REGION")

if [ -n "$CFN_ROLE_ARN" ]; then DEPLOY_CMD+=(--role-arn "$CFN_ROLE_ARN"); fi

if ! "${DEPLOY_CMD[@]}"; then
    echo "❌ DEPLOYMENT FAILED"
    # Try to find the latest changeset for this stack to get a better reason
    LATEST_CHANGESET=$(aws cloudformation list-change-sets --stack-name "$STACK_NAME" --region "$REGION" --query 'Summaries[0].ChangeSetId' --output text 2>/dev/null || echo "")
    if [ -n "$LATEST_CHANGESET" ] && [ "$LATEST_CHANGESET" != "None" ]; then
        REASON=$(aws cloudformation describe-change-set --change-set-name "$LATEST_CHANGESET" --region "$REGION" --query 'StatusReason' --output text 2>/dev/null || echo "Details unavailable.")
    else
        REASON="Could not retrieve changeset details."
    fi
    echo "Reason: $REASON"
    exit 1
fi

echo "✅ Success!"