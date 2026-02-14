"""
AWS CDK Stacks for YAWL Workflow Engine Infrastructure
This module defines production-ready infrastructure stacks including:
- VPC and Networking
- RDS PostgreSQL Database
- ECS Fargate Cluster
- Application Load Balancer
- CloudFront Distribution
- S3 Storage
- Monitoring and Logging
"""

from aws_cdk import (
    aws_ec2 as ec2,
    aws_ecs as ecs,
    aws_ecs_patterns as ecs_patterns,
    aws_rds as rds,
    aws_elasticloadbalancingv2 as elbv2,
    aws_s3 as s3,
    aws_cloudfront as cloudfront,
    aws_cloudfront_origins as origins,
    aws_cloudwatch as cloudwatch,
    aws_logs as logs,
    aws_iam as iam,
    aws_secretsmanager as secretsmanager,
    aws_kms as kms,
    aws_sns as sns,
    aws_lambda as lambda_,
    aws_events as events,
    aws_events_targets as targets,
    aws_autoscaling as autoscaling,
    aws_elasticache as elasticache,
    Duration,
    RemovalPolicy,
    Stack,
    Tags,
    CfnOutput,
)
from constructs import Construct
from typing import Optional


class YAWLNetworkStack(Stack):
    """Stack for VPC and networking infrastructure."""

    def __init__(
        self,
        scope: Construct,
        id: str,
        environment: str = "production",
        **kwargs
    ) -> None:
        super().__init__(scope, id, **kwargs)

        self.environment = environment

        # Create VPC with public and private subnets across 2 AZs
        self.vpc = ec2.Vpc(
            self,
            "YAWLVpc",
            cidr="10.0.0.0/16",
            max_azs=2,
            nat_gateways=1,
            subnet_configuration=[
                ec2.SubnetConfiguration(
                    name="Public",
                    subnet_type=ec2.SubnetType.PUBLIC,
                    cidr_mask=24,
                ),
                ec2.SubnetConfiguration(
                    name="Private",
                    subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS,
                    cidr_mask=24,
                ),
                ec2.SubnetConfiguration(
                    name="Isolated",
                    subnet_type=ec2.SubnetType.PRIVATE_ISOLATED,
                    cidr_mask=24,
                ),
            ],
            enable_dns_hostnames=True,
            enable_dns_support=True,
        )

        Tags.of(self.vpc).add("Name", f"yawl-vpc-{environment}")

        # Security Groups
        self.alb_sg = ec2.SecurityGroup(
            self,
            "ALBSecurityGroup",
            vpc=self.vpc,
            description="Security group for Application Load Balancer",
            allow_all_outbound=True,
        )

        # Allow HTTP and HTTPS from anywhere
        self.alb_sg.add_ingress_rule(
            peer=ec2.Peer.any_ipv4(),
            connection=ec2.Port.tcp(80),
            description="Allow HTTP from internet",
        )
        self.alb_sg.add_ingress_rule(
            peer=ec2.Peer.any_ipv4(),
            connection=ec2.Port.tcp(443),
            description="Allow HTTPS from internet",
        )

        self.ecs_sg = ec2.SecurityGroup(
            self,
            "ECSSecurityGroup",
            vpc=self.vpc,
            description="Security group for ECS tasks",
            allow_all_outbound=True,
        )

        # Allow ECS to receive traffic from ALB
        self.ecs_sg.add_ingress_rule(
            peer=self.alb_sg,
            connection=ec2.Port.tcp(8080),
            description="Allow traffic from ALB",
        )

        # Allow ECS to communicate with other ECS tasks
        self.ecs_sg.add_ingress_rule(
            peer=self.ecs_sg,
            connection=ec2.Port.all_tcp(),
            description="Allow communication between ECS tasks",
        )

        self.db_sg = ec2.SecurityGroup(
            self,
            "DatabaseSecurityGroup",
            vpc=self.vpc,
            description="Security group for RDS database",
            allow_all_outbound=False,
        )

        # Allow database access from ECS
        self.db_sg.add_ingress_rule(
            peer=self.ecs_sg,
            connection=ec2.Port.tcp(5432),
            description="Allow PostgreSQL from ECS",
        )

        self.redis_sg = ec2.SecurityGroup(
            self,
            "RedisSecurityGroup",
            vpc=self.vpc,
            description="Security group for Redis ElastiCache",
            allow_all_outbound=False,
        )

        # Allow Redis access from ECS
        self.redis_sg.add_ingress_rule(
            peer=self.ecs_sg,
            connection=ec2.Port.tcp(6379),
            description="Allow Redis from ECS",
        )

        # Outputs
        CfnOutput(
            self,
            "VpcId",
            value=self.vpc.vpc_id,
            description="VPC ID",
        )


class YAWLDatabaseStack(Stack):
    """Stack for RDS PostgreSQL database with high availability."""

    def __init__(
        self,
        scope: Construct,
        id: str,
        vpc: ec2.Vpc,
        db_sg: ec2.SecurityGroup,
        environment: str = "production",
        **kwargs
    ) -> None:
        super().__init__(scope, id, **kwargs)

        self.environment = environment

        # Get isolated subnets for database
        isolated_subnets = [
            subnet for subnet in vpc.private_subnets
            if subnet.subnet_type == ec2.SubnetType.PRIVATE_ISOLATED
        ]

        # Create database secret
        self.db_secret = secretsmanager.Secret(
            self,
            "YAWLDatabaseSecret",
            generate_secret_string=secretsmanager.SecretStringValueEventDetails(
                secret_string_template='{"username": "yawlmaster"}',
                generate_string_key="password",
                exclude_punctuation=False,
                password_length=32,
            ),
            description="YAWL Database credentials",
            removal_policy=RemovalPolicy.RETAIN,
        )

        # KMS key for encryption
        self.kms_key = kms.Key(
            self,
            "YAWLDatabaseKey",
            enable_key_rotation=True,
            removal_policy=RemovalPolicy.RETAIN,
            description="KMS key for YAWL database encryption",
        )

        # Create RDS instance
        self.database = rds.DatabaseInstance(
            self,
            "YAWLDatabase",
            engine=rds.DatabaseInstanceEngine.postgres(
                version=rds.PostgresEngineVersion.VER_15_4
            ),
            instance_type=ec2.InstanceType.of(
                ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM
            ),
            allocated_storage=100,
            storage_type=rds.StorageType.GP3,
            storage_encrypted=True,
            kms_key=self.kms_key,
            credentials=rds.Credentials.from_secret(self.db_secret),
            database_name="yawl",
            vpc=vpc,
            vpc_subnets=ec2.SubnetSelection(
                subnet_type=ec2.SubnetType.PRIVATE_ISOLATED
            ),
            security_groups=[db_sg],
            backup_retention=Duration.days(30),
            preferred_backup_window="03:00-04:00",
            preferred_maintenance_window="mon:04:00-mon:05:00",
            multi_az=True if environment == "production" else False,
            auto_minor_version_upgrade=True,
            deletion_protection=True if environment == "production" else False,
            removal_policy=RemovalPolicy.SNAPSHOT,
            cloudwatch_logs_exports=["postgresql"],
            enable_iam_authentication=True,
            publicly_accessible=False,
        )

        Tags.of(self.database).add("Name", f"yawl-db-{environment}")

        # Enable enhanced monitoring
        monitoring_role = iam.Role(
            self,
            "RDSEnhancedMonitoringRole",
            assumed_by=iam.ServicePrincipal("monitoring.rds.amazonaws.com"),
        )
        monitoring_role.add_managed_policy(
            iam.ManagedPolicy.from_aws_managed_policy_name(
                "service-role/AmazonRDSEnhancedMonitoringRole"
            )
        )

        # Outputs
        CfnOutput(
            self,
            "DatabaseEndpoint",
            value=self.database.db_instance_endpoint_address,
            description="RDS Database endpoint",
        )

        CfnOutput(
            self,
            "DatabaseSecretArn",
            value=self.db_secret.secret_arn,
            description="Database secret ARN for credentials",
        )


class YAWLCacheStack(Stack):
    """Stack for Redis ElastiCache cluster for caching and sessions."""

    def __init__(
        self,
        scope: Construct,
        id: str,
        vpc: ec2.Vpc,
        redis_sg: ec2.SecurityGroup,
        environment: str = "production",
        **kwargs
    ) -> None:
        super().__init__(scope, id, **kwargs)

        self.environment = environment

        # Create subnet group for ElastiCache
        subnet_ids = [
            subnet.subnet_id for subnet in vpc.private_subnets
        ]

        subnet_group = elasticache.CfnSubnetGroup(
            self,
            "CacheSubnetGroup",
            description="Subnet group for YAWL Redis cache",
            subnet_ids=subnet_ids,
        )

        # Create Redis cluster
        self.redis_cluster = elasticache.CfnReplicationGroup(
            self,
            "YAWLRedisCluster",
            engine="redis",
            engine_version="7.0",
            replication_group_description="YAWL Redis cache for sessions and caching",
            cache_node_type="cache.t3.micro" if environment == "development" else "cache.t3.small",
            num_cache_clusters=1 if environment == "development" else 2,
            automatic_failover_enabled=True if environment == "production" else False,
            multi_az_enabled=True if environment == "production" else False,
            cache_subnet_group_name=subnet_group.ref,
            security_group_ids=[redis_sg.security_group_id],
            at_rest_encryption_enabled=True,
            transit_encryption_enabled=True,
            auth_token_enabled=True,
            auto_minor_version_upgrade=True,
            log_delivery_configurations=[
                elasticache.CfnReplicationGroup.LogDeliveryConfigurationRequestProperty(
                    destination_type="cloudwatch-logs",
                    destination_name="yawl-redis-logs",
                    log_format="json",
                    enabled=True,
                )
            ],
            tags=[elasticache.CfnTag(key="Name", value=f"yawl-redis-{environment}")],
        )

        CfnOutput(
            self,
            "RedisEndpoint",
            value=self.redis_cluster.attr_primary_endpoint_address,
            description="Redis cluster endpoint",
        )


class YAWLECSStack(Stack):
    """Stack for ECS Fargate cluster and YAWL application."""

    def __init__(
        self,
        scope: Construct,
        id: str,
        vpc: ec2.Vpc,
        alb_sg: ec2.SecurityGroup,
        ecs_sg: ec2.SecurityGroup,
        db_secret: secretsmanager.ISecret,
        environment: str = "production",
        container_image: str = "public.ecr.aws/docker/library/nginx:latest",
        **kwargs
    ) -> None:
        super().__init__(scope, id, **kwargs)

        self.environment = environment

        # Create ECS Cluster
        self.cluster = ecs.Cluster(
            self,
            "YAWLCluster",
            vpc=vpc,
            cluster_name=f"yawl-cluster-{environment}",
            container_insights=True,
        )

        Tags.of(self.cluster).add("Name", f"yawl-cluster-{environment}")

        # Create CloudWatch Log Group
        log_group = logs.LogGroup(
            self,
            "YAWLLogGroup",
            log_group_name=f"/ecs/yawl/{environment}",
            retention=logs.RetentionDays.TWO_WEEKS,
            encryption_key=kms.Key(self, "LogsKey", enable_key_rotation=True),
            removal_policy=RemovalPolicy.RETAIN,
        )

        # IAM Role for ECS Task Execution
        task_execution_role = iam.Role(
            self,
            "YAWLTaskExecutionRole",
            assumed_by=iam.ServicePrincipal("ecs-tasks.amazonaws.com"),
        )

        task_execution_role.add_managed_policy(
            iam.ManagedPolicy.from_aws_managed_policy_name(
                "service-role/AmazonECSTaskExecutionRolePolicy"
            )
        )

        # Allow access to database secret
        db_secret.grant_read(task_execution_role)

        # Log group permissions
        log_group.grant_write(task_execution_role)

        # IAM Role for ECS Task
        task_role = iam.Role(
            self,
            "YAWLTaskRole",
            assumed_by=iam.ServicePrincipal("ecs-tasks.amazonaws.com"),
        )

        # Create Application Load Balancer
        self.alb = elbv2.ApplicationLoadBalancer(
            self,
            "YAWLLoadBalancer",
            vpc=vpc,
            internet_facing=True,
            load_balancer_name=f"yawl-alb-{environment}",
            security_group=alb_sg,
        )

        Tags.of(self.alb).add("Name", f"yawl-alb-{environment}")

        # Create target group
        target_group = elbv2.ApplicationTargetGroup(
            self,
            "YAWLTargetGroup",
            vpc=vpc,
            port=8080,
            protocol=elbv2.ApplicationProtocol.HTTP,
            target_type=elbv2.TargetType.IP,
            health_check=elbv2.HealthCheck(
                path="/resourceService/",
                interval=Duration.seconds(30),
                timeout=Duration.seconds(10),
                healthy_threshold_count=2,
                unhealthy_threshold_count=3,
                protocol=elbv2.Protocol.HTTP,
            ),
            target_group_name=f"yawl-tg-{environment}",
            deregistration_delay=Duration.seconds(30),
        )

        # Add HTTP listener
        self.alb.add_listener(
            "HttpListener",
            port=80,
            protocol=elbv2.ApplicationProtocol.HTTP,
            default_action=elbv2.ListenerAction.forward([target_group]),
        )

        # Create ECS Task Definition
        task_definition = ecs.FargateTaskDefinition(
            self,
            "YAWLTaskDefinition",
            memory_limit_mib=2048,
            cpu=1024,
            execution_role=task_execution_role,
            task_role=task_role,
            family=f"yawl-task-{environment}",
        )

        # Add container
        container = task_definition.add_container(
            "yawl",
            image=ecs.ContainerImage.from_registry(container_image),
            environment={
                "YAWL_DB_PORT": "5432",
                "YAWL_DB_NAME": "yawl",
                "YAWL_HEAP_SIZE": "1024m",
                "LOG_LEVEL": "INFO",
                "REDIS_PORT": "6379",
                "ENVIRONMENT": environment,
            },
            secrets={
                "YAWL_DB_HOST": ecs.Secret.from_secrets_manager(
                    db_secret, "host"
                ),
                "YAWL_DB_USER": ecs.Secret.from_secrets_manager(
                    db_secret, "username"
                ),
                "YAWL_DB_PASSWORD": ecs.Secret.from_secrets_manager(
                    db_secret, "password"
                ),
            },
            logging=ecs.LogDriver.aws_logs(
                stream_prefix="yawl",
                log_group=log_group,
            ),
            port_mappings=[
                ecs.PortMapping(container_port=8080, protocol=ecs.Protocol.TCP)
            ],
        )

        # Create Fargate Service with Auto Scaling
        self.service = ecs.FargateService(
            self,
            "YAWLService",
            cluster=self.cluster,
            task_definition=task_definition,
            service_name=f"yawl-service-{environment}",
            desired_count=2 if environment == "production" else 1,
            security_groups=[ecs_sg],
            vpc_subnets=ec2.SubnetSelection(
                subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS
            ),
        )

        # Attach to load balancer
        self.service.attach_load_balancer(
            load_balancer=self.alb,
            container_name="yawl",
            container_port=8080,
        )

        # Auto-scaling
        scaling = self.service.auto_scale_task_count(
            min_capacity=2 if environment == "production" else 1,
            max_capacity=6 if environment == "production" else 2,
        )

        # Scale based on CPU utilization
        scaling.scale_on_cpu_utilization(
            "CpuScaling",
            target_utilization_percent=70,
            cooldown=Duration.minutes(5),
        )

        # Scale based on memory utilization
        scaling.scale_on_memory_utilization(
            "MemoryScaling",
            target_utilization_percent=80,
            cooldown=Duration.minutes(5),
        )

        Tags.of(self.service).add("Name", f"yawl-service-{environment}")

        # Outputs
        CfnOutput(
            self,
            "LoadBalancerDNS",
            value=self.alb.load_balancer_dns_name,
            description="Application Load Balancer DNS name",
        )

        CfnOutput(
            self,
            "ServiceName",
            value=self.service.service_name,
            description="ECS Service name",
        )


class YAWLStorageStack(Stack):
    """Stack for S3 buckets for backups, artifacts, and static content."""

    def __init__(
        self,
        scope: Construct,
        id: str,
        environment: str = "production",
        **kwargs
    ) -> None:
        super().__init__(scope, id, **kwargs)

        self.environment = environment

        # KMS key for S3 encryption
        s3_kms_key = kms.Key(
            self,
            "S3EncryptionKey",
            enable_key_rotation=True,
            removal_policy=RemovalPolicy.RETAIN,
            description="KMS key for S3 bucket encryption",
        )

        # Backup bucket
        self.backup_bucket = s3.Bucket(
            self,
            "YAWLBackupBucket",
            bucket_name=f"yawl-backups-{self.account}-{environment}",
            block_public_access=s3.BlockPublicAccess.BLOCK_ALL,
            encryption=s3.BucketEncryption.KMS,
            encryption_key=s3_kms_key,
            versioned=True,
            removal_policy=RemovalPolicy.RETAIN,
            lifecycle_rules=[
                s3.LifecycleRule(
                    transitions=[
                        s3.Transition(
                            storage_class=s3.StorageClass.GLACIER,
                            transition_after=Duration.days(30),
                        ),
                        s3.Transition(
                            storage_class=s3.StorageClass.DEEP_ARCHIVE,
                            transition_after=Duration.days(90),
                        ),
                    ],
                    noncurrent_version_transitions=[
                        s3.NoncurrentVersionTransition(
                            storage_class=s3.StorageClass.GLACIER,
                            transition_after=Duration.days(30),
                        ),
                    ],
                    noncurrent_version_expiration=Duration.days(365),
                ),
            ],
            server_access_logs_prefix="logs/",
        )

        Tags.of(self.backup_bucket).add("Name", f"yawl-backups-{environment}")

        # Static content bucket
        self.static_bucket = s3.Bucket(
            self,
            "YAWLStaticBucket",
            bucket_name=f"yawl-static-{self.account}-{environment}",
            block_public_access=s3.BlockPublicAccess(
                block_public_acls=False,
                block_public_policy=False,
                ignore_public_acls=False,
                restrict_public_buckets=False,
            ),
            encryption=s3.BucketEncryption.KMS,
            encryption_key=s3_kms_key,
            removal_policy=RemovalPolicy.RETAIN,
            cors=[
                s3.CorsRule(
                    allowed_methods=[
                        s3.HttpMethods.GET,
                        s3.HttpMethods.HEAD,
                    ],
                    allowed_origins=["*"],
                    max_age=Duration.days(3),
                )
            ],
        )

        # Add bucket policy for CloudFront access
        self.static_bucket.add_to_resource_policy(
            iam.PolicyStatement(
                effect=iam.Effect.ALLOW,
                principals=[
                    iam.ServicePrincipal(
                        "cloudfront.amazonaws.com"
                    )
                ],
                actions=["s3:GetObject"],
                resources=[self.static_bucket.arn_for_objects("*")],
            )
        )

        Tags.of(self.static_bucket).add("Name", f"yawl-static-{environment}")

        # Artifacts bucket for build artifacts
        self.artifacts_bucket = s3.Bucket(
            self,
            "YAWLArtifactsBucket",
            bucket_name=f"yawl-artifacts-{self.account}-{environment}",
            block_public_access=s3.BlockPublicAccess.BLOCK_ALL,
            encryption=s3.BucketEncryption.KMS,
            encryption_key=s3_kms_key,
            versioned=True,
            removal_policy=RemovalPolicy.RETAIN,
            lifecycle_rules=[
                s3.LifecycleRule(
                    noncurrent_version_expiration=Duration.days(30),
                    expiration=Duration.days(90),
                ),
            ],
        )

        Tags.of(self.artifacts_bucket).add("Name", f"yawl-artifacts-{environment}")

        # Outputs
        CfnOutput(
            self,
            "BackupBucketName",
            value=self.backup_bucket.bucket_name,
            description="Backup bucket name",
        )

        CfnOutput(
            self,
            "StaticBucketName",
            value=self.static_bucket.bucket_name,
            description="Static content bucket name",
        )

        CfnOutput(
            self,
            "ArtifactsBucketName",
            value=self.artifacts_bucket.bucket_name,
            description="Artifacts bucket name",
        )


class YAWLDistributionStack(Stack):
    """Stack for CloudFront distribution for global content delivery."""

    def __init__(
        self,
        scope: Construct,
        id: str,
        static_bucket: s3.Bucket,
        alb_dns: str,
        environment: str = "production",
        **kwargs
    ) -> None:
        super().__init__(scope, id, **kwargs)

        self.environment = environment

        # CloudFront Origin Access Control for S3
        oac = cloudfront.OriginAccessControl(
            self,
            "S3OAC",
            origin_access_control_name=f"yawl-oac-{environment}",
        )

        # S3 Origin
        s3_origin = origins.S3Origin(
            bucket=static_bucket,
            origin_access_control=oac,
        )

        # ALB Origin
        alb_origin = origins.HttpOrigin(
            domain_name=alb_dns,
            protocol=cloudfront.OriginProtocolPolicy.HTTP_ONLY,
        )

        # Create CloudFront distribution
        self.distribution = cloudfront.Distribution(
            self,
            "YAWLDistribution",
            default_behavior=cloudfront.BehaviorOptions(
                origin=alb_origin,
                viewer_protocol_policy=cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                cache_policy=cloudfront.CachePolicy.CACHING_OPTIMIZED,
                origin_request_policy=cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
                allowed_methods=cloudfront.AllowedMethods.ALLOW_ALL,
            ),
            additional_behaviors={
                "/static/*": cloudfront.BehaviorOptions(
                    origin=s3_origin,
                    viewer_protocol_policy=cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                    cache_policy=cloudfront.CachePolicy.CACHING_OPTIMIZED,
                ),
                "/assets/*": cloudfront.BehaviorOptions(
                    origin=s3_origin,
                    viewer_protocol_policy=cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                    cache_policy=cloudfront.CachePolicy.CACHING_OPTIMIZED,
                ),
            },
            comment=f"YAWL Distribution - {environment}",
            enable_logging=True,
            log_bucket=s3.Bucket(
                self,
                "DistributionLogsBucket",
                block_public_access=s3.BlockPublicAccess.BLOCK_ALL,
                encryption=s3.BucketEncryption.S3_MANAGED,
                removal_policy=RemovalPolicy.RETAIN,
                lifecycle_rules=[
                    s3.LifecycleRule(
                        expiration=Duration.days(30),
                    ),
                ],
            ),
            log_file_prefix="cloudfront-logs/",
        )

        Tags.of(self.distribution).add("Name", f"yawl-distribution-{environment}")

        # Outputs
        CfnOutput(
            self,
            "CloudFrontDomain",
            value=self.distribution.domain_name,
            description="CloudFront distribution domain name",
        )

        CfnOutput(
            self,
            "CloudFrontDistributionId",
            value=self.distribution.distribution_id,
            description="CloudFront distribution ID",
        )


class YAWLMonitoringStack(Stack):
    """Stack for CloudWatch monitoring, alarms, and SNS notifications."""

    def __init__(
        self,
        scope: Construct,
        id: str,
        alb: elbv2.ApplicationLoadBalancer,
        ecs_service: ecs.FargateService,
        rds_instance: rds.DatabaseInstance,
        environment: str = "production",
        **kwargs
    ) -> None:
        super().__init__(scope, id, **kwargs)

        self.environment = environment

        # Create SNS topic for alarms
        self.alarm_topic = sns.Topic(
            self,
            "YAWLAlarmTopic",
            display_name="YAWL Infrastructure Alarms",
            topic_name=f"yawl-alarms-{environment}",
        )

        # ALB Metrics
        # Target Response Time
        alb_response_time = cloudwatch.Metric(
            namespace="AWS/ApplicationELB",
            metric_name="TargetResponseTime",
            statistic="Average",
            period=Duration.minutes(5),
            dimensions_map={
                "LoadBalancer": alb.load_balancer_full_name,
            },
        )

        cloudwatch.Alarm(
            self,
            "HighResponseTimeAlarm",
            metric=alb_response_time,
            threshold=1,
            evaluation_periods=2,
            alarm_description="Alert when ALB response time is high",
            alarm_name=f"yawl-alb-high-response-time-{environment}",
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # Target Count
        target_count = cloudwatch.Metric(
            namespace="AWS/ApplicationELB",
            metric_name="TargetCount",
            statistic="Average",
            period=Duration.minutes(1),
            dimensions_map={
                "LoadBalancer": alb.load_balancer_full_name,
            },
        )

        cloudwatch.Alarm(
            self,
            "NoHealthyTargetsAlarm",
            metric=target_count,
            threshold=0,
            evaluation_periods=2,
            alarm_description="Alert when no healthy targets",
            alarm_name=f"yawl-alb-no-targets-{environment}",
            comparison_operator=cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # Request Count
        request_count = cloudwatch.Metric(
            namespace="AWS/ApplicationELB",
            metric_name="RequestCount",
            statistic="Sum",
            period=Duration.minutes(5),
            dimensions_map={
                "LoadBalancer": alb.load_balancer_full_name,
            },
        )

        cloudwatch.Alarm(
            self,
            "HighRequestCountAlarm",
            metric=request_count,
            threshold=10000,
            evaluation_periods=2,
            alarm_description="Alert on high request volume",
            alarm_name=f"yawl-alb-high-requests-{environment}",
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # ECS Metrics
        # CPU Utilization
        ecs_cpu = cloudwatch.Metric(
            namespace="AWS/ECS",
            metric_name="CPUUtilization",
            statistic="Average",
            period=Duration.minutes(5),
            dimensions_map={
                "ServiceName": ecs_service.service_name,
                "ClusterName": ecs_service.cluster.cluster_name,
            },
        )

        cloudwatch.Alarm(
            self,
            "HighCPUAlarm",
            metric=ecs_cpu,
            threshold=80,
            evaluation_periods=3,
            alarm_description="Alert when ECS CPU is high",
            alarm_name=f"yawl-ecs-high-cpu-{environment}",
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # Memory Utilization
        ecs_memory = cloudwatch.Metric(
            namespace="AWS/ECS",
            metric_name="MemoryUtilization",
            statistic="Average",
            period=Duration.minutes(5),
            dimensions_map={
                "ServiceName": ecs_service.service_name,
                "ClusterName": ecs_service.cluster.cluster_name,
            },
        )

        cloudwatch.Alarm(
            self,
            "HighMemoryAlarm",
            metric=ecs_memory,
            threshold=85,
            evaluation_periods=3,
            alarm_description="Alert when ECS memory is high",
            alarm_name=f"yawl-ecs-high-memory-{environment}",
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # RDS Metrics
        # Database CPU
        rds_cpu = cloudwatch.Metric(
            namespace="AWS/RDS",
            metric_name="CPUUtilization",
            statistic="Average",
            period=Duration.minutes(5),
            dimensions_map={
                "DBInstanceIdentifier": rds_instance.instance_identifier,
            },
        )

        cloudwatch.Alarm(
            self,
            "HighDatabaseCPUAlarm",
            metric=rds_cpu,
            threshold=80,
            evaluation_periods=3,
            alarm_description="Alert when database CPU is high",
            alarm_name=f"yawl-db-high-cpu-{environment}",
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # Database Connections
        db_connections = cloudwatch.Metric(
            namespace="AWS/RDS",
            metric_name="DatabaseConnections",
            statistic="Average",
            period=Duration.minutes(5),
            dimensions_map={
                "DBInstanceIdentifier": rds_instance.instance_identifier,
            },
        )

        cloudwatch.Alarm(
            self,
            "HighDatabaseConnectionsAlarm",
            metric=db_connections,
            threshold=50,
            evaluation_periods=2,
            alarm_description="Alert when database connections are high",
            alarm_name=f"yawl-db-high-connections-{environment}",
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # Database Free Storage
        db_free_storage = cloudwatch.Metric(
            namespace="AWS/RDS",
            metric_name="FreeableMemory",
            statistic="Average",
            period=Duration.minutes(5),
            dimensions_map={
                "DBInstanceIdentifier": rds_instance.instance_identifier,
            },
        )

        cloudwatch.Alarm(
            self,
            "LowDatabaseMemoryAlarm",
            metric=db_free_storage,
            threshold=268435456,  # 256 MB in bytes
            evaluation_periods=2,
            alarm_description="Alert when database free memory is low",
            alarm_name=f"yawl-db-low-memory-{environment}",
            comparison_operator=cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
        ).add_alarm_action(cloudwatch.SnsAction(self.alarm_topic))

        # Create CloudWatch Dashboard
        dashboard = cloudwatch.Dashboard(
            self,
            "YAWLDashboard",
            dashboard_name=f"yawl-infrastructure-{environment}",
        )

        dashboard.add_widgets(
            cloudwatch.GraphWidget(
                title="ALB Response Time",
                left=[alb_response_time],
            ),
            cloudwatch.GraphWidget(
                title="ECS CPU and Memory",
                left=[ecs_cpu, ecs_memory],
            ),
            cloudwatch.GraphWidget(
                title="RDS CPU and Connections",
                left=[rds_cpu, db_connections],
            ),
            cloudwatch.GraphWidget(
                title="ALB Request Count",
                left=[request_count],
            ),
        )

        # Outputs
        CfnOutput(
            self,
            "AlarmTopicArn",
            value=self.alarm_topic.topic_arn,
            description="SNS topic ARN for alarms",
        )

        CfnOutput(
            self,
            "DashboardUrl",
            value=f"https://console.aws.amazon.com/cloudwatch/home?region={self.region}#dashboards:name={dashboard.dashboard_name}",
            description="CloudWatch Dashboard URL",
        )
