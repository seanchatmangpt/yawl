// =============================================================================
// YAWL v6.0 Docker Buildx Bake Configuration
// =============================================================================
// Multi-architecture builds for AMD64 and ARM64 platforms
// Usage:
//   docker buildx bake --load                # Build and load to local Docker (single platform)
//   docker buildx bake --push                # Build and push to registry (multi-platform)
//   docker buildx bake dev                   # Build development target only
//   docker buildx bake production            # Build production target only
//   docker buildx bake --set *.cache-from=type=registry,ref=ghcr.io/yawl/yawl:cache
//   docker buildx bake --print               # Print JSON configuration (dry-run)
// =============================================================================

// -----------------------------------------------------------------------------
// Variables
// -----------------------------------------------------------------------------
variable "REGISTRY" {
  default = "ghcr.io/yawlfoundation"
}

variable "IMAGE_NAME" {
  default = "yawl"
}

variable "VERSION" {
  default = "6.0.0-Alpha"
}

variable "BUILD_DATE" {
  default = timestamp()
}

variable "GIT_SHA" {
  default = "unknown"
}

variable "GIT_REF" {
  default = "main"
}

// -----------------------------------------------------------------------------
// Target: Development (Local builds, full JDK, debugging tools)
// -----------------------------------------------------------------------------
target "dev" {
  context    = "."
  dockerfile = "docker/development/Dockerfile.dev"
  platforms  = ["linux/amd64"]
  tags = [
    "${REGISTRY}/${IMAGE_NAME}:dev",
    "${REGISTRY}/${IMAGE_NAME}:dev-${VERSION}"
  ]
  cache-from = ["type=registry,ref=${REGISTRY}/${IMAGE_NAME}:dev-cache"]
  cache-to   = ["type=registry,ref=${REGISTRY}/${IMAGE_NAME}:dev-cache,mode=max"]
  labels = {
    "org.opencontainers.image.title"       = "YAWL Workflow Engine (Development)"
    "org.opencontainers.image.description" = "Development image with full JDK and debugging tools"
    "org.opencontainers.image.version"     = "${VERSION}"
    "org.opencontainers.image.created"     = "${BUILD_DATE}"
    "org.opencontainers.image.revision"    = "${GIT_SHA}"
    "org.opencontainers.image.source"      = "https://github.com/yawlfoundation/yawl"
    "org.opencontainers.image.vendor"      = "YAWL Foundation"
    "org.opencontainers.image.licenses"    = "LGPL-3.0"
  }
  target = "dev"
}

// -----------------------------------------------------------------------------
// Target: Builder Stage Only (for CI caching Maven dependencies)
// -----------------------------------------------------------------------------
target "builder" {
  context    = "."
  dockerfile = "docker/production/Dockerfile.engine"
  platforms  = ["linux/amd64", "linux/arm64"]
  target     = "builder"
  tags = [
    "${REGISTRY}/${IMAGE_NAME}:builder-${VERSION}"
  ]
  cache-from = ["type=registry,ref=${REGISTRY}/${IMAGE_NAME}:builder-cache"]
  cache-to   = ["type=registry,ref=${REGISTRY}/${IMAGE_NAME}:builder-cache,mode=max"]
  labels = {
    "org.opencontainers.image.title"       = "YAWL Workflow Engine (Builder)"
    "org.opencontainers.image.description" = "Builder stage for CI dependency caching"
    "org.opencontainers.image.version"     = "${VERSION}"
  }
  output = ["type=docker,compression=zstd,compression-level=9"]
}

// -----------------------------------------------------------------------------
// Target: Staging (Pre-production testing)
// -----------------------------------------------------------------------------
target "staging" {
  context    = "."
  dockerfile = "docker/production/Dockerfile.engine"
  platforms  = ["linux/amd64", "linux/arm64"]
  tags = [
    "${REGISTRY}/${IMAGE_NAME}:staging",
    "${REGISTRY}/${IMAGE_NAME}:staging-${VERSION}",
    "${REGISTRY}/${IMAGE_NAME}:staging-${GIT_SHA}"
  ]
  cache-from = [
    "type=registry,ref=${REGISTRY}/${IMAGE_NAME}:builder-cache",
    "type=registry,ref=${REGISTRY}/${IMAGE_NAME}:staging-cache"
  ]
  cache-to = ["type=registry,ref=${REGISTRY}/${IMAGE_NAME}:staging-cache,mode=max"]
  labels = {
    "org.opencontainers.image.title"       = "YAWL Workflow Engine (Staging)"
    "org.opencontainers.image.description" = "Staging image for pre-production testing"
    "org.opencontainers.image.version"     = "${VERSION}"
    "org.opencontainers.image.created"     = "${BUILD_DATE}"
    "org.opencontainers.image.revision"    = "${GIT_SHA}"
    "org.opencontainers.image.source"      = "https://github.com/yawlfoundation/yawl"
    "org.opencontainers.image.vendor"      = "YAWL Foundation"
    "org.opencontainers.image.licenses"    = "LGPL-3.0"
    "environment"                          = "staging"
  }
  target = "runtime"
}

// -----------------------------------------------------------------------------
// Target: Production (Optimized, minimal runtime, multi-arch)
// -----------------------------------------------------------------------------
target "production" {
  context    = "."
  dockerfile = "docker/production/Dockerfile.engine"
  platforms  = ["linux/amd64", "linux/arm64"]
  tags = [
    "${REGISTRY}/${IMAGE_NAME}:latest",
    "${REGISTRY}/${IMAGE_NAME}:${VERSION}",
    "${REGISTRY}/${IMAGE_NAME}:${GIT_SHA}"
  ]
  cache-from = [
    "type=registry,ref=${REGISTRY}/${IMAGE_NAME}:builder-cache",
    "type=registry,ref=${REGISTRY}/${IMAGE_NAME}:prod-cache"
  ]
  cache-to = ["type=registry,ref=${REGISTRY}/${IMAGE_NAME}:prod-cache,mode=max"]
  labels = {
    "org.opencontainers.image.title"       = "YAWL Workflow Engine"
    "org.opencontainers.image.description" = "Production-ready YAWL workflow engine with Java 25 and virtual threads"
    "org.opencontainers.image.version"     = "${VERSION}"
    "org.opencontainers.image.created"     = "${BUILD_DATE}"
    "org.opencontainers.image.revision"    = "${GIT_SHA}"
    "org.opencontainers.image.source"      = "https://github.com/yawlfoundation/yawl"
    "org.opencontainers.image.vendor"      = "YAWL Foundation"
    "org.opencontainers.image.licenses"    = "LGPL-3.0"
    "environment"                          = "production"
  }
  target = "runtime"
  // Provenance and SBOM for supply chain security
  attest = [
    "type=provenance,mode=max",
    "type=sbom,generator=docker/scout-sbom-indexer:latest"
  ]
  // Optimized output for production
  output = ["type=registry,compression=zstd,compression-level=9"]
}

// -----------------------------------------------------------------------------
// Target: AMD64 Only (For systems without ARM64 support)
// -----------------------------------------------------------------------------
target "production-amd64" {
  inherits  = ["production"]
  platforms = ["linux/amd64"]
  tags = [
    "${REGISTRY}/${IMAGE_NAME}:latest-amd64",
    "${REGISTRY}/${IMAGE_NAME}:${VERSION}-amd64"
  ]
}

// -----------------------------------------------------------------------------
// Target: ARM64 Only (For ARM-based systems like Apple Silicon, AWS Graviton)
// -----------------------------------------------------------------------------
target "production-arm64" {
  inherits  = ["production"]
  platforms = ["linux/arm64"]
  tags = [
    "${REGISTRY}/${IMAGE_NAME}:latest-arm64",
    "${REGISTRY}/${IMAGE_NAME}:${VERSION}-arm64"
  ]
}

// -----------------------------------------------------------------------------
// Target: Local Build (Load to local Docker daemon)
// -----------------------------------------------------------------------------
target "local" {
  context    = "."
  dockerfile = "docker/production/Dockerfile.engine"
  platforms  = ["linux/amd64"]  // Single platform for local loading
  tags = [
    "${IMAGE_NAME}:local",
    "${IMAGE_NAME}:${VERSION}-local"
  ]
  // No external cache for local builds - relies on Docker's built-in layer caching
  labels = {
    "org.opencontainers.image.title"       = "YAWL Workflow Engine (Local)"
    "org.opencontainers.image.description" = "Local build for development and testing"
    "org.opencontainers.image.version"     = "${VERSION}"
    "org.opencontainers.image.created"     = "${BUILD_DATE}"
  }
  target = "runtime"
  // Load into local Docker daemon
  output = ["type=docker"]
}

// -----------------------------------------------------------------------------
// Group: Default (Build all production variants)
// -----------------------------------------------------------------------------
group "default" {
  targets = ["production"]
}

// -----------------------------------------------------------------------------
// Group: All (Build all targets)
// -----------------------------------------------------------------------------
group "all" {
  targets = ["dev", "staging", "production"]
}

// -----------------------------------------------------------------------------
// Group: Multi-Arch (Build platform-specific variants)
// -----------------------------------------------------------------------------
group "multi-arch" {
  targets = ["production-amd64", "production-arm64"]
}

// -----------------------------------------------------------------------------
// Group: CI (Build targets optimized for CI/CD pipeline)
// -----------------------------------------------------------------------------
group "ci" {
  targets = ["builder", "production"]
}

// -----------------------------------------------------------------------------
// Target: MCP-A2A Application (Spring Boot with MCP and A2A integration)
// -----------------------------------------------------------------------------
target "mcp-a2a-app" {
  context    = "."
  dockerfile = "docker/production/Dockerfile.mcp-a2a-app"
  platforms  = ["linux/amd64", "linux/arm64"]
  tags = [
    "${REGISTRY}/yawl-mcp-a2a-app:latest",
    "${REGISTRY}/yawl-mcp-a2a-app:${VERSION}",
    "${REGISTRY}/yawl-mcp-a2a-app:${GIT_SHA}"
  ]
  cache-from = [
    "type=registry,ref=${REGISTRY}/yawl-mcp-a2a-app:cache"
  ]
  cache-to   = ["type=registry,ref=${REGISTRY}/yawl-mcp-a2a-app:cache,mode=max"]
  labels = {
    "org.opencontainers.image.title"       = "YAWL MCP-A2A Application"
    "org.opencontainers.image.description" = "Spring Boot application with MCP server and A2A agent integration"
    "org.opencontainers.image.version"     = "${VERSION}"
    "org.opencontainers.image.created"     = "${BUILD_DATE}"
    "org.opencontainers.image.revision"    = "${GIT_SHA}"
    "org.opencontainers.image.source"      = "https://github.com/yawlfoundation/yawl"
    "org.opencontainers.image.vendor"      = "YAWL Foundation"
    "org.opencontainers.image.licenses"    = "LGPL-3.0"
    "environment"                          = "production"
    "app"                                  = "mcp-a2a"
    "ports.exposed"                        = "8080,8081,8082"
    "ports.internal"                       = "8080/spring-boot,8081/mcp-server,8082/a2a-agent"
  }
  // Provenance and SBOM for marketplace submission
  attest = [
    "type=provenance,mode=max",
    "type=sbom,generator=docker/scout-sbom-indexer:latest"
  ]
  // Optimized output
  output = ["type=registry,compression=zstd,compression-level=9"]
}

// -----------------------------------------------------------------------------
// Group: Quick (Fast local builds without cache)
// -----------------------------------------------------------------------------
group "quick" {
  targets = ["local"]
}
