```mermaid
flowchart TB

    User[Users / Browser]

    APIGW[API Gateway<br/>HTTP API]

    ALB[Application Load Balancer]

    ASG[Auto Scaling Group<br/>(1 instance)]
    EC2[EC2 Instance<br/>Phoenix App<br/>Frontend :5173<br/>Backend :8080<br/>Grafana :3000<br/>Jaeger :16686]

    RDS[(RDS PostgreSQL 16)]

    IGW[Internet Gateway]
    VPC[VPC]
    PUB1[Public Subnet AZ1]
    PUB2[Public Subnet AZ2]
    PRIV1[Private Subnet AZ1]
    PRIV2[Private Subnet AZ2]

    User --> APIGW
    APIGW -->|HTTP Proxy| ALB

    ALB -->|/| EC2
    ALB -->|/api/*| EC2
    ALB -->|/grafana*| EC2
    ALB -->|/jaeger*| EC2

    ASG --> EC2
    EC2 --> RDS

    VPC --> IGW
    PUB1 --> ALB
    PUB2 --> ALB
    PRIV1 --> ASG
    PRIV2 --> ASG
```
