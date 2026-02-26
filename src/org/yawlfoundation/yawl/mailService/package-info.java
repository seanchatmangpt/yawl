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
 * Email service for YAWL workflow notifications.
 *
 * <p>This package provides a configurable email service that can be invoked
 * by workflow tasks to send email notifications. It integrates with the
 * Simple Java Mail library for SMTP delivery.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.mailService.MailService} - Main service for sending emails from tasks</li>
 *   <li>{@link org.yawlfoundation.yawl.mailService.MailServiceClient} - Client interface for mail operations</li>
 *   <li>{@link org.yawlfoundation.yawl.mailService.MailServiceGateway} - Gateway for mail service configuration</li>
 * </ul>
 *
 * <p>Configuration is managed via mail.properties or environment variables.</p>
 */
package org.yawlfoundation.yawl.mailService;
