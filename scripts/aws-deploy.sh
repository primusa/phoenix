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

if [ -z "$KEY_NAME" ]; then
  echo "Usage: $0 <keypair> [region] [vpc] [public-subnets] [service-repo] [ui-repo] [create-alb]"
  exit 2
fi

echo "-------------------------------------------------------"
echo "🔍 PRE-FLIGHT CHECKS"
echo "-------------------------------------------------------"

# 1. Determine VPC
if [ -n "$OVERRIDE_VPC" ]; then
  VPC_ID="$OVERRIDE_VPC"
else
  VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters "Name=tag:Name,Values=project-phoenix-vpc" --query 'Vpcs[0].VpcId' --output text)
fi

if [ "$VPC_ID" = "None" ] || [ -z "$VPC_ID" ]; then
  echo "❌ Error: Could not find VPC."
  exit 1
fi
echo "✅ VPC: $VPC_ID"

# 2. Find Internet Gateway
IGW_ID=$(aws ec2 describe-internet-gateways --region "$REGION" --filters "Name=attachment.vpc-id,Values=$VPC_ID" --query 'InternetGateways[0].InternetGatewayId' --output text)
if [ "$IGW_ID" = "None" ] || [ -z "$IGW_ID" ]; then
  echo "⚠️  No Internet Gateway found for VPC $VPC_ID. CloudFormation will attempt to create one."
  IGW_ID=""
else
  echo "✅ Internet Gateway: $IGW_ID"
fi

# 3. Determine Subnets
if [ -n "$OVERRIDE_PUBLIC_SUBNETS" ]; then
  PUBLIC_SUBNETS=$(echo "$OVERRIDE_PUBLIC_SUBNETS" | tr -d ' ')
else
  PUBLIC_SUBNET_ARRAY=$(aws ec2 describe-subnets --region "$REGION" --filters "Name=vpc-id,Values=$VPC_ID" "Name=map-public-ip-on-launch,Values=true" --query 'Subnets[*].SubnetId' --output text)
  PUBLIC_SUBNETS=$(echo $PUBLIC_SUBNET_ARRAY | sed 's/ /,/g')
fi

PRIVATE_SUBNET_ARRAY=$(aws ec2 describe-subnets --region "$REGION" --filters "Name=vpc-id,Values=$VPC_ID" "Name=map-public-ip-on-launch,Values=false" --query 'Subnets[*].SubnetId' --output text)
PRIVATE_SUBNETS=$(echo $PRIVATE_SUBNET_ARRAY | sed 's/ /,/g')

# 4. Deployment ID
DEPLOYMENT_ID=$(date +%s)

# Stack Status Check & Waiting
echo "Checking stack status..."
for i in {1..30}; do
  STATUS=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo "MISSING")
  echo "Current Status: $STATUS"
  
  if [[ "$STATUS" =~ CLEANUP_IN_PROGRESS|DELETE_IN_PROGRESS ]]; then
    echo "Stack is cleaning up/deleting. Waiting 10s ($i/30)..."
    sleep 10
  elif [[ "$STATUS" =~ FAILED|ROLLBACK ]]; then
    echo "🧹 Stack in a failed state ($STATUS). Deleting..."
    aws cloudformation delete-stack --stack-name "$STACK_NAME" --region "$REGION"
    echo "Waiting for deletion to complete..."
    aws cloudformation wait stack-delete-complete --stack-name "$STACK_NAME" --region "$REGION"
    break
  else
    break
  fi
done

# Deploy
PARAMS=(
  "KeyName=$KEY_NAME"
  "VpcId=$VPC_ID"
  "PhoenixServiceRepoName=$SERVICE_REPO"
  "PhoenixUiRepoName=$UI_REPO"
  "CreateALB=$CREATE_ALB"
  "DeploymentId=$DEPLOYMENT_ID"
)

[[ -n "$IGW_ID" ]] && PARAMS+=("InternetGatewayId=$IGW_ID")
[[ -n "$PUBLIC_SUBNETS" ]] && PARAMS+=("PublicSubnetIds=$PUBLIC_SUBNETS")
[[ -n "$PRIVATE_SUBNETS" ]] && PARAMS+=("PrivateSubnetIds=$PRIVATE_SUBNETS")

echo "🚀 Deploying (DeploymentId: $DEPLOYMENT_ID)..."
aws cloudformation deploy \
  --template-file aws/cloudformation/stack.yaml \
  --stack-name "$STACK_NAME" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "${PARAMS[@]}" \
  --region "$REGION"

echo "✅ Success!"