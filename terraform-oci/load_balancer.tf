# Load Balancer
resource "oci_load_balancer_load_balancer" "main" {
  compartment_id = var.compartment_ocid
  display_name   = var.load_balancer_display_name
  shape          = var.load_balancer_shape

  # For flexible shape
  shape_details {
    minimum_bandwidth_in_mbps = var.load_balancer_min_bandwidth
    maximum_bandwidth_in_mbps = var.load_balancer_max_bandwidth
  }

  subnet_ids = [oci_core_subnet.public_subnet.id]

  network_security_group_ids = [oci_core_network_security_group.lb_nsg.id]

  is_private = false

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name"      = var.load_balancer_display_name
      "Component" = "LoadBalancer"
    }
  )
}

# Backend Set for HTTP
resource "oci_load_balancer_backend_set" "backend_set_http" {
  name             = "${var.project_name}-backend-http"
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  policy           = "ROUND_ROBIN"

  health_checker {
    port            = var.lb_backend_port
    protocol        = "HTTP"
    url_path        = "/health"
    interval_ms     = 10000
    timeout_in_millis = 3000
    healthy_threshold = 3
    unhealthy_threshold = 3
  }

  session_persistence_configuration {
    cookie_name      = "YAWL_LB_COOKIE"
    disable_fallback = true
  }
}

# Backend Set for HTTPS
resource "oci_load_balancer_backend_set" "backend_set_https" {
  name             = "${var.project_name}-backend-https"
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  policy           = "ROUND_ROBIN"

  health_checker {
    port            = var.lb_backend_port
    protocol        = "HTTP"
    url_path        = "/health"
    interval_ms     = 10000
    timeout_in_millis = 3000
    healthy_threshold = 3
    unhealthy_threshold = 3
  }

  session_persistence_configuration {
    cookie_name      = "YAWL_LB_COOKIE"
    disable_fallback = true
  }

  ssl_configuration {
    certificate_name        = oci_load_balancer_certificate.main.certificate_name
    protocols               = ["TLSv1.2", "TLSv1.3"]
    cipher_suite_name       = "oci-default-ssl-cipher-suite-v1"
    server_order_preference = "SERVER"
  }
}

# Backend Configuration for Compute Instances
resource "oci_load_balancer_backend" "backends" {
  count            = var.compute_instance_count
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  backendset_name  = oci_load_balancer_backend_set.backend_set_http.name
  ip_address       = data.oci_core_vnic.instance_vnic[count.index].private_ip_address
  port             = var.lb_backend_port
  weight           = 1
  drain            = false
  offline          = false
}

# HTTP Listener - Redirect to HTTPS
resource "oci_load_balancer_listener" "http_listener" {
  name                     = "${var.project_name}-http-listener"
  load_balancer_id         = oci_load_balancer_load_balancer.main.id
  default_backend_set_name = oci_load_balancer_backend_set.backend_set_http.name
  port                     = 80
  protocol                 = "HTTP"

  routing_policy_name = oci_load_balancer_rule_set.redirect_to_https.name

  depends_on = [oci_load_balancer_backend.backends]
}

# HTTPS Listener
resource "oci_load_balancer_listener" "https_listener" {
  name                     = "${var.project_name}-https-listener"
  load_balancer_id         = oci_load_balancer_load_balancer.main.id
  default_backend_set_name = oci_load_balancer_backend_set.backend_set_https.name
  port                     = 443
  protocol                 = "HTTPS"

  depends_on = [oci_load_balancer_backend.backends]
}

# SSL Certificate (Self-signed for demo, replace with real certificate)
resource "tls_private_key" "main" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "main" {
  private_key_pem = tls_private_key.main.private_key_pem

  subject {
    common_name  = "yawl.example.com"
    organization = "YAWL Project"
  }

  validity_period_hours = 8760  # 1 year

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]
}

# Load Balancer Certificate
resource "oci_load_balancer_certificate" "main" {
  certificate_name   = "${var.project_name}-cert"
  load_balancer_id   = oci_load_balancer_load_balancer.main.id
  ca_certificate     = tls_self_signed_cert.main.cert_pem
  private_key        = tls_private_key.main.private_key_pem
  public_certificate = tls_self_signed_cert.main.cert_pem
  passphrase         = ""

  depends_on = [oci_load_balancer_load_balancer.main]
}

# Rule Set for HTTP to HTTPS redirect
resource "oci_load_balancer_rule_set" "redirect_to_https" {
  name             = "${var.project_name}-redirect-https"
  load_balancer_id = oci_load_balancer_load_balancer.main.id

  items {
    action = "REDIRECT"
    conditions {
      attribute_name  = "PATH"
      attribute_value = ".*"
      operator        = "MATCH_REGEX"
    }
    redirect_uri {
      protocol = "https"
      port     = 443
      host     = "{host}"
      path     = "{path}"
      query    = "{query}"
    }
    response_code = 301
  }
}

# Hostname for Load Balancer
resource "oci_load_balancer_hostname" "main" {
  name             = "${var.project_name}-hostname"
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  hostname         = "yawl.example.com"
}

# Path Route Set for routing specific paths
resource "oci_load_balancer_path_route_set" "main" {
  load_balancer_id             = oci_load_balancer_load_balancer.main.id
  name                         = "${var.project_name}-path-routes"

  path_routes {
    path                        = "/api/*"
    backend_set_name            = oci_load_balancer_backend_set.backend_set_https.name
    path_match_type {
      match_type = "PREFIX_MATCH"
    }
  }

  path_routes {
    path                        = "/static/*"
    backend_set_name            = oci_load_balancer_backend_set.backend_set_https.name
    path_match_type {
      match_type = "PREFIX_MATCH"
    }
  }

  path_routes {
    path                        = "/*"
    backend_set_name            = oci_load_balancer_backend_set.backend_set_https.name
    path_match_type {
      match_type = "PREFIX_MATCH"
    }
  }
}

# Autoscaling Group (optional - for future use)
# This would require scaling your compute instances based on demand

# Connection Idling Timeout
resource "oci_load_balancer_load_balancer" "main_with_timeout" {
  count          = 0  # Set to 1 if you want to enable this
  compartment_id = var.compartment_ocid
  display_name   = "${var.load_balancer_display_name}-timeout"
  shape          = var.load_balancer_shape

  shape_details {
    minimum_bandwidth_in_mbps = var.load_balancer_min_bandwidth
    maximum_bandwidth_in_mbps = var.load_balancer_max_bandwidth
  }

  subnet_ids = [oci_core_subnet.public_subnet.id]
  is_private = false

  # Connection timeout configuration
  connection_configuration {
    idle_timeout_in_seconds = 60
  }

  tags = merge(
    var.common_tags,
    var.tags,
    {
      "Name" = "${var.load_balancer_display_name}-timeout"
    }
  )
}

# Load Balancer Logging
resource "oci_load_balancer_load_balancer_log" "main" {
  load_balancer_id = oci_load_balancer_load_balancer.main.id

  access_logs {
    is_enabled = var.environment == "production" ? true : false
  }
}
