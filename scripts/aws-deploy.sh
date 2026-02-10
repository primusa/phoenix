#!/usr/bin/env bash
set -euo pipefail

KEY_NAME="${1:-}"
REGION="${2:-us-east-1}"
OVERRIDE_VPC="${3:-}"
OVERRIDE_PUBLIC_SUBNETS="${4:-}"
SERVICE_REPO="${5:-phoenix-service}"
UI_REPO="${6:-phoenix-ui}"
CREATE_ALB="${7:-false}"
INSTANCE_TYPE="${8:-t4g.small}"
STACK_NAME="phoenix-stack"

if [ -z "$KEY_NAME" ]; then
  echo "Usage: $0 <keypair> [region] [vpc] [subnets] [svc-repo] [ui-repo] [alb] [instance-type]"
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
IGW_ID="${IGW_ID#None}" # Convert "None" string to empty if needed

# 3. Determine Subnets
if [ -n "$OVERRIDE_PUBLIC_SUBNETS" ]; then
  PUBLIC_SUBNETS=$(echo "$OVERRIDE_PUBLIC_SUBNETS" | tr -d ' ')
else
  PUBLIC_SUBNET_ARRAY=$(aws ec2 describe-subnets --region "$REGION" --filters "Name=vpc-id,Values=$VPC_ID" "Name=map-public-ip-on-launch,Values=true" --query 'Subnets[*].SubnetId' --output text)
  PUBLIC_SUBNETS=$(echo $PUBLIC_SUBNET_ARRAY | sed 's/ /,/g')
fi

PRIVATE_SUBNET_ARRAY=$(aws ec2 describe-subnets --region "$REGION" --filters "Name=vpc-id,Values=$VPC_ID" "Name=map-public-ip-on-launch,Values=false" --query 'Subnets[*].SubnetId' --output text)
PRIVATE_SUBNETS=$(echo $PRIVATE_SUBNET_ARRAY | sed 's/ /,/g')

# 4. Check for existing Public Route (0.0.0.0/0 -> IGW)
# We check the first public subnet's route table
CREATE_ROUTE="true"
if [ -n "$PUBLIC_SUBNETS" ]; then
  FIRST_SUBNET=$(echo "$PUBLIC_SUBNETS" | cut -d',' -f1)
  RT_ID=$(aws ec2 describe-route-tables --region "$REGION" --filters "Name=association.subnet-id,Values=$FIRST_SUBNET" --query 'RouteTables[0].RouteTableId' --output text)
  
  if [ "$RT_ID" != "None" ] && [ -n "$RT_ID" ]; then
    HAS_IGW_ROUTE=$(aws ec2 describe-route-tables --region "$REGION" --route-table-ids "$RT_ID" --query 'RouteTables[0].Routes[?DestinationCidrBlock==`0.0.0.0/0` && GatewayId != `null`].GatewayId' --output text)
    if [ -n "$HAS_IGW_ROUTE" ] && [ "$HAS_IGW_ROUTE" != "None" ]; then
      echo "✅ Existing public route found in $RT_ID. Skipping route creation."
      CREATE_ROUTE="false"
    fi
  fi
fi

# 5. Deployment ID
DEPLOYMENT_ID=$(date +%s)

# Stack Status Check & Waiting
echo "Checking stack status..."
for i in {1..50}; do
  STATUS=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo "MISSING")
  
  if [[ "$STATUS" =~ CLEANUP_IN_PROGRESS|DELETE_IN_PROGRESS|WAIT_IN_PROGRESS ]]; then
    echo "Current Status: $STATUS. Waiting ($i/50)..."
    sleep 20
  elif [[ "$STATUS" =~ FAILED|ROLLBACK ]]; then
    echo "🧹 Cleanup old failure ($STATUS)..."
    aws cloudformation delete-stack --stack-name "$STACK_NAME" --region "$REGION"
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
  "InstanceType=$INSTANCE_TYPE"
  "DeploymentId=$DEPLOYMENT_ID"
  "CreatePublicRoute=$CREATE_ROUTE"
)

[[ -n "$IGW_ID" ]] && PARAMS+=("InternetGatewayId=$IGW_ID")
[[ -n "$PUBLIC_SUBNETS" ]] && PARAMS+=("PublicSubnetIds=$PUBLIC_SUBNETS")
[[ -n "$PRIVATE_SUBNETS" ]] && PARAMS+=("PrivateSubnetIds=$PRIVATE_SUBNETS")

echo "🚀 Deploying ($INSTANCE_TYPE)..."
aws cloudformation deploy \
  --template-file aws/cloudformation/stack.yaml \
  --stack-name "$STACK_NAME" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "${PARAMS[@]}" \
  --region "$REGION" || {
    echo "❌ Deployment failed. If it's stuck in CLEANUP_IN_PROGRESS, please wait 5 minutes and try again."
    exit 1
  }

echo "✅ Success!"

# DASHBOARD
INSTANCE_IP=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" --query 'Stacks[0].Outputs[?OutputKey==`InstancePublicIp`].OutputValue' --output text 2>/dev/null || echo "N/A")
INSTANCE_ID=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" --query 'Stacks[0].Outputs[?OutputKey==`InstanceId`].OutputValue' --output text 2>/dev/null || echo "N/A")
ALB_DNS=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerDns`].OutputValue' --output text 2>/dev/null || echo "N/A")
API_URL=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" --query 'Stacks[0].Outputs[?OutputKey==`ApiGatewayUrl`].OutputValue' --output text 2>/dev/null || echo "N/A")

echo "-------------------------------------------------------"
echo "🌎 ACCESS INFO"
echo "API Gateway (HTTPS): $API_URL"
if [ "$CREATE_ALB" = "true" ]; then
    echo "ALB DNS:             http://$ALB_DNS"
else
    echo "Direct Public IP:    http://$INSTANCE_IP:5173"
fi
echo " "
echo "📋 DEBUGGING"
echo "Connect:  aws ssm start-session --target $INSTANCE_ID --region $REGION"
echo "Logs:     sudo tail -f /var/log/user-data.log"
echo "-------------------------------------------------------"