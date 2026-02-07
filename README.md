# Phoenix - Production Ready Guide

This repo contains a Spring Boot service and UI. The repository includes CI/CD and CloudFormation artifacts to build, publish, and deploy to AWS (ECR + EC2 t4g.small).

## Quick Start

### 1. Create GitHub Repository

Create an empty repository on GitHub, then push this code:

```bash
# Manual setup (recommended for clarity)
git remote add origin https://github.com/<owner>/<repo>.git
git branch -M main
git push -u origin main

# Or, use the helper script (requires repo to exist on GitHub first)
./scripts/create-github-repo.sh <owner>/<repo>
```

### 2. Configure GitHub Secrets

In your GitHub repository settings, add the following secrets (`Settings → Secrets and variables → Actions`):

- `AWS_ACCESS_KEY_ID` – AWS access key ID
- `AWS_SECRET_ACCESS_KEY` – AWS secret access key
- `AWS_REGION` – AWS region (e.g., `us-east-1`)
- `AWS_ACCOUNT_ID` – Your AWS account ID (12-digit number)
- `ECR_REPOSITORY` – ECR repo name (e.g., `phoenix-service`)
- `EC2_KEY_NAME` – Name of an EC2 keypair in your region

Example GitHub Secrets (values are examples — do NOT commit real secrets):

```text
AWS_ACCESS_KEY_ID=AKIAxxxxxxxxxxxxxx
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYzz
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
ECR_REPOSITORY=phoenix-service
EC2_KEY_NAME=phoenix-key
# Optional for CI deploy (if you prefer explicit inputs):
CFN_VPC_ID=vpc-0abc12345def67890
CFN_PUBLIC_SUBNETS=subnet-aaa111,subnet-bbb222
CFN_ROLE_ARN=arn:aws:iam::123456789012:role/CloudFormationExecutionRole
```

### 3. Deploy CloudFormation

Prepare the CloudFormation stack with your VPC and subnets:

```bash
# Get your VPC and public subnet IDs
aws ec2 describe-vpcs --region us-east-1 --query 'Vpcs[0].VpcId' --output text
aws ec2 describe-subnets --region us-east-1 --filters Name=vpc-id,Values=<vpc-id> Name=map-public-ip-on-launch,Values=true --query 'Subnets[*].SubnetId' --output text

# Deploy the stack
aws cloudformation deploy \
  --template-file aws/cloudformation/stack.yaml \
  --stack-name phoenix-stack \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    KeyName=<ec2-keypair-name> \
    VpcId=<vpc-id> \
    "PublicSubnets=<subnet-id-1>,<subnet-id-2>" \
  --region us-east-1
```

### 4. Manual Build & Push (Optional)

Test locally before relying on CI:

```bash
# Build the JAR
mvn -f phoenix-service/pom.xml clean package -DskipTests

# Build and push Docker image to ECR
./scripts/build-and-push.sh phoenix-service us-east-1

# Deploy CloudFormation
./scripts/aws-deploy.sh my-ec2-keypair us-east-1
```

## Automation

The GitHub Actions workflow (`.github/workflows/ci-cd.yml`) automatically:
1. Builds the Java service with Maven
2. Builds and pushes multi-arch Docker images to ECR
3. Deploys or updates the CloudFormation stack

Workflow triggers on push to `main` or `master` branch.

## Architecture

- **Spring Boot 4.0** with Java 17 (production-stable)
- **Docker**: Multi-arch images (linux/arm64, linux/amd64)
- **AWS**: Application Load Balancer + AutoScaling Group (1 t4g.small instance)
- **ECR**: Container registry for Docker images
- **CloudFormation**: Infrastructure as Code for reproducible deployments
