# Phoenix - Production Ready Guide

This repo contains a Spring Boot service and UI. The repository includes CI/CD and CloudFormation artifacts to build, publish, and deploy to AWS (ECR + EC2 t4g.small).

Quick steps (manual):

1. Create an ECR repository name (example: `phoenix-service`) and AWS credentials with permissions to create CloudFormation stacks, ECR, EC2 and IAM.
2. Create an EC2 keypair in the target region (to pass to CloudFormation).
3. Configure GitHub repository secrets:
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`
   - `AWS_REGION` (e.g., `us-east-1`)
   - `AWS_ACCOUNT_ID`
   - `ECR_REPOSITORY` (e.g., `phoenix-service`)
   - `EC2_KEY_NAME` (the keypair name)

Local build & push (example):

```bash
# build the jar
mvn -f phoenix-service/pom.xml clean package -DskipTests

# build and push docker image (requires aws cli authenticated)
./scripts/build-and-push.sh phoenix-service us-east-1

# deploy cloudformation
./scripts/aws-deploy.sh my-ec2-keypair us-east-1
```

Notes:
- Docker build uses multi-arch images (linux/arm64 and linux/amd64) for compatibility with t4g (arm64).
- The GitHub Actions workflow `/.github/workflows/ci-cd.yml` automates build, push and deploy when pushing to `main`/`master`.
