#!/usr/bin/env python3
"""
YAWL AWS CDK Application
This module orchestrates the deployment of production-ready YAWL infrastructure on AWS.

Architecture Overview:
- VPC with public, private, and isolated subnets across 2 AZs
- RDS PostgreSQL database with Multi-AZ, encryption, and backups
- Redis ElastiCache cluster for sessions and caching
- ECS Fargate cluster with auto-scaling based on CPU/memory
- Application Load Balancer for traffic distribution
- CloudFront distribution for global content delivery
- S3 buckets for backups, static content, and artifacts
- CloudWatch monitoring with alarms and SNS notifications
- KMS encryption for data at rest
- Secrets Manager for credential management

Usage:
    cdk deploy --require-approval=never
    cdk deploy YAWLNetworkStack YAWLDatabaseStack YAWLCacheStack --require-approval=never

Environment Variables:
    ENVIRONMENT: deployment environment (development, staging, production) - default: production
    CONTAINER_IMAGE: Docker image URI for YAWL container - default: nginx:latest
    AWS_REGION: AWS region - default: us-east-1
"""

import os
from aws_cdk import App, Environment
from stacks import (
    YAWLNetworkStack,
    YAWLDatabaseStack,
    YAWLCacheStack,
    YAWLECSStack,
    YAWLStorageStack,
    YAWLDistributionStack,
    YAWLMonitoringStack,
)


def main():
    """Initialize and configure CDK application."""
    app = App()

    # Get configuration from environment
    environment_name = os.getenv("ENVIRONMENT", "production")
    container_image = os.getenv(
        "CONTAINER_IMAGE",
        "public.ecr.aws/docker/library/nginx:latest"
    )
    aws_region = os.getenv("AWS_REGION", "us-east-1")

    # Define AWS environment
    aws_env = Environment(
        account=os.getenv("AWS_ACCOUNT_ID"),
        region=aws_region,
    )

    stack_prefix = f"yawl-{environment_name}"

    # Create Network Stack
    print(f"Creating network infrastructure for {environment_name} environment...")
    network_stack = YAWLNetworkStack(
        app,
        f"{stack_prefix}-network",
        environment=environment_name,
        env=aws_env,
        description=f"YAWL Network Stack - {environment_name}",
    )

    # Create Database Stack
    print("Creating database infrastructure...")
    database_stack = YAWLDatabaseStack(
        app,
        f"{stack_prefix}-database",
        vpc=network_stack.vpc,
        db_sg=network_stack.db_sg,
        environment=environment_name,
        env=aws_env,
        description=f"YAWL Database Stack - {environment_name}",
    )
    database_stack.add_dependency(network_stack)

    # Create Cache Stack
    print("Creating cache infrastructure...")
    cache_stack = YAWLCacheStack(
        app,
        f"{stack_prefix}-cache",
        vpc=network_stack.vpc,
        redis_sg=network_stack.redis_sg,
        environment=environment_name,
        env=aws_env,
        description=f"YAWL Cache Stack - {environment_name}",
    )
    cache_stack.add_dependency(network_stack)

    # Create Storage Stack
    print("Creating storage infrastructure...")
    storage_stack = YAWLStorageStack(
        app,
        f"{stack_prefix}-storage",
        environment=environment_name,
        env=aws_env,
        description=f"YAWL Storage Stack - {environment_name}",
    )

    # Create ECS Stack
    print("Creating ECS infrastructure...")
    ecs_stack = YAWLECSStack(
        app,
        f"{stack_prefix}-ecs",
        vpc=network_stack.vpc,
        alb_sg=network_stack.alb_sg,
        ecs_sg=network_stack.ecs_sg,
        db_secret=database_stack.db_secret,
        environment=environment_name,
        container_image=container_image,
        env=aws_env,
        description=f"YAWL ECS Stack - {environment_name}",
    )
    ecs_stack.add_dependency(network_stack)
    ecs_stack.add_dependency(database_stack)

    # Create Distribution Stack
    print("Creating CloudFront distribution...")
    distribution_stack = YAWLDistributionStack(
        app,
        f"{stack_prefix}-distribution",
        static_bucket=storage_stack.static_bucket,
        alb_dns=ecs_stack.alb.load_balancer_dns_name,
        environment=environment_name,
        env=aws_env,
        description=f"YAWL CloudFront Stack - {environment_name}",
    )
    distribution_stack.add_dependency(storage_stack)
    distribution_stack.add_dependency(ecs_stack)

    # Create Monitoring Stack
    print("Creating monitoring infrastructure...")
    monitoring_stack = YAWLMonitoringStack(
        app,
        f"{stack_prefix}-monitoring",
        alb=ecs_stack.alb,
        ecs_service=ecs_stack.service,
        rds_instance=database_stack.database,
        environment=environment_name,
        env=aws_env,
        description=f"YAWL Monitoring Stack - {environment_name}",
    )
    monitoring_stack.add_dependency(ecs_stack)
    monitoring_stack.add_dependency(database_stack)

    # Synthesize
    app.synth()

    print("\nCDK Application successfully initialized!")
    print(f"Environment: {environment_name}")
    print(f"Region: {aws_region}")
    print(f"Container Image: {container_image}")
    print("\nTo deploy, run:")
    print(f"  cdk deploy --require-approval=never")
    print("\nTo deploy specific stacks, run:")
    print(f"  cdk deploy {stack_prefix}-network {stack_prefix}-database --require-approval=never")
    print("\nTo view deployed resources:")
    print(f"  aws cloudformation describe-stacks --region {aws_region}")


if __name__ == "__main__":
    main()
