#!/usr/bin/env bash
set -euo pipefail

# Lightweight local deploy script (preserves original lookup behavior)
# Usage: ./scripts/aws-deploy-local.sh <ec2-keypair-name> [aws-region]

KEY_NAME="${1:-}"
REGION="${2:-us-east-1}"
STACK_NAME="phoenix-stack"

if [ -z "$KEY_NAME" ]; then
  echo "Usage: $0 <ec2-keypair-name> [aws-region]"
  exit 2
fi

echo "Checking stack status for $STACK_NAME in $REGION..."

# Get VPC by name
echo "Looking up VPC named 'project-phoenix-vpc'..."
DEFAULT_VPC=$(aws ec2 describe-vpcs \
  --region "$REGION" \
  --filters "Name=tag:Name,Values=project-phoenix-vpc" \
  --query 'Vpcs[0].VpcId' \
  --output text)

if [ -z "$DEFAULT_VPC" ] || [ "$DEFAULT_VPC" = "None" ]; then
  echo "Error: VPC named 'project-phoenix-vpc' not found in region $REGION"
  exit 1
fi
echo "Using VPC: $DEFAULT_VPC"

# Get all subnets in the default VPC (sorted for consistency)
echo "Looking up subnets in VPC..."
SUBNET_ARRAY=$(aws ec2 describe-subnets \
  --region "$REGION" \
  --filters "Name=vpc-id,Values=$DEFAULT_VPC" \
  --query 'Subnets[*].SubnetId' \
  --output text)

if [ -z "$SUBNET_ARRAY" ]; then
  echo "Error: No subnets found in VPC $DEFAULT_VPC"
  exit 1
fi

# Convert to array (handle both tab and space-separated output)
SUBNET_LIST=()
for subnet in $SUBNET_ARRAY; do
  if [[ "$subnet" =~ ^subnet- ]]; then
    SUBNET_LIST+=("$subnet")
  fi
done

SUBNET_COUNT=${#SUBNET_LIST[@]}

if [ "$SUBNET_COUNT" -lt 2 ]; then
  echo "Error: Found only $SUBNET_COUNT subnet(s), but at least 2 are required for ALB high availability"
  echo "Available subnets: $(IFS=,; echo "${SUBNET_LIST[*]}")"
  echo ""
  echo "Solution: Create another subnet in a different AZ:"
  echo "aws ec2 create-subnet --vpc-id $DEFAULT_VPC --cidr-block <new-cidr> --availability-zone <different-az> --region $REGION"
  exit 1
fi

# Use first 2 subnets (comma-separated for CloudFormation List parameter)
PUBLIC_SUBNETS="${SUBNET_LIST[0]},${SUBNET_LIST[1]}"
echo "Using subnets: $PUBLIC_SUBNETS (found $SUBNET_COUNT total)"

echo "Deploying CloudFormation stack $STACK_NAME to $REGION"
aws cloudformation deploy \
  --template-file aws/cloudformation/stack.yaml \
  --stack-name "$STACK_NAME" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides KeyName="$KEY_NAME" VpcId="$DEFAULT_VPC" PublicSubnets="$PUBLIC_SUBNETS" Environment="development" CostCenter="engineering" \
  --region "$REGION"

echo "Deployment finished"
