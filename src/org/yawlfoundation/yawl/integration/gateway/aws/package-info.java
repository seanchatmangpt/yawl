/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * AWS API Gateway configuration generator for YAWL v6.0.0.
 *
 * <p>This package provides OpenAPI 3.0 configuration generation with AWS
 * extensions ({@code x-amazon-apigateway-*}) for deploying YAWL behind
 * AWS API Gateway. Supports Lambda authorizers, usage plans, throttling,
 * and VPC Link integration for private deployments.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.gateway.aws.AwsApiGatewayConfigGenerator} -
 *       Generates OpenAPI 3.0 definitions with AWS-specific extensions</li>
 * </ul>
 *
 * <h2>Generated OpenAPI Structure</h2>
 * <pre>{@code
 * openapi: 3.0.1
 * info:
 *   title: YAWL API
 *   version: 6.0.0
 * x-amazon-apigateway-api-key-source: AUTHORIZER
 * security:
 *   - lambda_authorizer: []
 * paths:
 *   /yawl/case:
 *     post:
 *       x-amazon-apigateway-integration:
 *         type: http_proxy
 *         uri: http://yawl-engine:8080/yawl/case
 *         connectionType: VPC_LINK
 *         connectionId: ${vpc_link_id}
 *       x-amazon-apigateway-throttling:
 *         rateLimit: 100
 *         burstLimit: 200
 * }</pre>
 *
 * <h2>AWS Extensions Used</h2>
 * <ul>
 *   <li><b>x-amazon-apigateway-integration</b> - Backend integration configuration</li>
 *   <li><b>x-amazon-apigateway-authorizer</b> - Lambda authorizer definition</li>
 *   <li><b>x-amazon-apigateway-throttling</b> - Per-method rate limiting</li>
 *   <li><b>x-amazon-apigateway-request-validator</b> - Request validation rules</li>
 *   <li><b>x-amazon-apigateway-gateway-responses</b> - Custom error responses</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Generate AWS API Gateway configuration
 * AwsApiGatewayConfigGenerator generator = new AwsApiGatewayConfigGenerator();
 * String openapiYaml = generator.generate(
 *     "http://yawl.internal:8080",
 *     AwsIntegrationConfig.vpcLink("vpc-link-12345"),
 *     ThrottlingConfig.tiered(10, 100, 600)
 * );
 *
 * // Deploy via AWS CLI
 * // aws apigateway import-rest-api --body file://openapi.yaml
 * }</pre>
 *
 * <h2>Deployment Options</h2>
 * <ul>
 *   <li><b>HTTP Proxy</b> - Direct proxy to YAWL engine (on-premises or EC2)</li>
 *   <li><b>VPC Link</b> - Private integration via NLB for AWS Fargate/ECS deployments</li>
 *   <li><b>Lambda</b> - Wrap YAWL calls in Lambda for request/response transformation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.integration.gateway.GatewayRouteDefinition
 */
package org.yawlfoundation.yawl.integration.gateway.aws;
