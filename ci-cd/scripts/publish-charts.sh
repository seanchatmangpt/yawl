#!/bin/bash
# Publish Helm Charts for YAWL Workflow Engine
# Packages and publishes charts to registries

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CHART_DIR="${CHART_DIR:-$PROJECT_ROOT/helm}"
CHART_NAME="${CHART_NAME:-yawl}"
VERSION="${VERSION:-}"
APP_VERSION="${APP_VERSION:-$VERSION}"
OUTPUT_DIR="${OUTPUT_DIR:-$PROJECT_ROOT/charts}"

# Registry configurations
OCI_REGISTRY="${OCI_REGISTRY:-}"
HELM_REPO_URL="${HELM_REPO_URL:-}"
HELM_REPO_NAME="${HELM_REPO_NAME:-yawl-charts}"

echo "=============================================="
echo "YAWL Helm Chart Publisher"
echo "=============================================="
echo ""
echo "Chart Directory: $CHART_DIR"
echo "Chart Name: $CHART_NAME"
echo "Version: ${VERSION:-auto}"
echo "App Version: ${APP_VERSION:-auto}"
echo "Output Directory: $OUTPUT_DIR"
echo ""

# Check for required tools
check_requirements() {
    echo "Checking requirements..."

    if ! command -v helm &> /dev/null; then
        echo -e "${RED}ERROR: Helm is not installed${NC}"
        echo "Install with: https://helm.sh/docs/intro/install/"
        exit 1
    fi

    # Enable OCI support
    export HELM_EXPERIMENTAL_OCI=1

    echo -e "${GREEN}Helm version: $(helm version --short)${NC}"
    echo ""
}

# Update chart version
update_version() {
    if [ -n "$VERSION" ]; then
        echo "Updating chart version to $VERSION..."

        # Update Chart.yaml
        if [ -f "$CHART_DIR/$CHART_NAME/Chart.yaml" ]; then
            sed -i.bak "s/^version:.*/version: $VERSION/" "$CHART_DIR/$CHART_NAME/Chart.yaml"
            if [ -n "$APP_VERSION" ]; then
                sed -i.bak "s/^appVersion:.*/appVersion: $APP_VERSION/" "$CHART_DIR/$CHART_NAME/Chart.yaml"
            fi
            rm -f "$CHART_DIR/$CHART_NAME/Chart.yaml.bak"
        fi
    else
        # Get version from Chart.yaml
        VERSION=$(grep '^version:' "$CHART_DIR/$CHART_NAME/Chart.yaml" | awk '{print $2}')
        echo "Using version from Chart.yaml: $VERSION"
    fi
}

# Update dependencies
update_dependencies() {
    echo "Updating chart dependencies..."

    if [ -f "$CHART_DIR/$CHART_NAME/Chart.lock" ]; then
        helm dependency update "$CHART_DIR/$CHART_NAME"
    fi

    echo -e "${GREEN}Dependencies updated${NC}"
    echo ""
}

# Lint chart
lint_chart() {
    echo "Linting chart..."

    if helm lint "$CHART_DIR/$CHART_NAME"; then
        echo -e "${GREEN}Chart linting passed${NC}"
    else
        echo -e "${RED}Chart linting failed${NC}"
        exit 1
    fi

    echo ""
}

# Validate chart
validate_chart() {
    echo "Validating chart..."

    # Check required files
    local required_files=(
        "$CHART_DIR/$CHART_NAME/Chart.yaml"
        "$CHART_DIR/$CHART_NAME/values.yaml"
        "$CHART_DIR/$CHART_NAME/templates/deployment.yaml"
        "$CHART_DIR/$CHART_NAME/templates/service.yaml"
    )

    for file in "${required_files[@]}"; do
        if [ ! -f "$file" ]; then
            echo -e "${RED}ERROR: Required file not found: $file${NC}"
            exit 1
        fi
    done

    # Validate template rendering
    helm template test-release "$CHART_DIR/$CHART_NAME" \
        --debug \
        > /dev/null 2>&1 || {
        echo -e "${RED}ERROR: Chart template validation failed${NC}"
        helm template test-release "$CHART_DIR/$CHART_NAME" --debug 2>&1 | head -50
        exit 1
    }

    echo -e "${GREEN}Chart validation passed${NC}"
    echo ""
}

# Package chart
package_chart() {
    echo "Packaging chart..."

    mkdir -p "$OUTPUT_DIR"

    helm package "$CHART_DIR/$CHART_NAME" \
        --destination "$OUTPUT_DIR" \
        --app-version "$APP_VERSION" \
        --version "$VERSION"

    local chart_package="$OUTPUT_DIR/$CHART_NAME-$VERSION.tgz"

    if [ -f "$chart_package" ]; then
        echo -e "${GREEN}Chart packaged: $chart_package${NC}"

        # Generate checksum
        sha256sum "$chart_package" > "$chart_package.sha256"
        echo "SHA256: $(cat $chart_package.sha256)"
    else
        echo -e "${RED}ERROR: Failed to package chart${NC}"
        exit 1
    fi

    echo ""
}

# Generate documentation
generate_docs() {
    echo "Generating chart documentation..."

    if command -v helm-docs &> /dev/null; then
        helm-docs "$CHART_DIR/$CHART_NAME"
        echo -e "${GREEN}Documentation generated${NC}"
    else
        echo -e "${YELLOW}helm-docs not installed. Skipping documentation generation${NC}"
        echo "Install with: go install github.com/norwoodj/helm-docs/cmd/helm-docs@latest"
    fi

    echo ""
}

# Sign chart (optional)
sign_chart() {
    echo "Signing chart..."

    if [ -n "${HELM_SIGN_KEY:-}" ]; then
        helm package "$CHART_DIR/$CHART_NAME" \
            --destination "$OUTPUT_DIR" \
            --sign \
            --key "$HELM_SIGN_KEY" \
            --keyring "${HELM_KEYRING:-~/.gnupg/pubring.gpg}"

        echo -e "${GREEN}Chart signed${NC}"
    else
        echo -e "${YELLOW}Skipping chart signing (HELM_SIGN_KEY not set)${NC}"
    fi

    echo ""
}

# Publish to OCI registry
publish_oci() {
    if [ -z "$OCI_REGISTRY" ]; then
        echo -e "${YELLOW}OCI_REGISTRY not set. Skipping OCI publish${NC}"
        return
    fi

    echo "Publishing to OCI registry: $OCI_REGISTRY"

    local chart_package="$OUTPUT_DIR/$CHART_NAME-$VERSION.tgz"

    # Login to registry if credentials are set
    if [ -n "${OCI_USERNAME:-}" ] && [ -n "${OCI_PASSWORD:-}" ]; then
        echo "$OCI_PASSWORD" | helm registry login "$OCI_REGISTRY" \
            --username "$OCI_USERNAME" \
            --password-stdin
    fi

    # Push chart
    helm push "$chart_package" "oci://$OCI_REGISTRY"

    echo -e "${GREEN}Chart published to OCI registry${NC}"
    echo ""
}

# Publish to Helm repository
publish_helm_repo() {
    if [ -z "$HELM_REPO_URL" ]; then
        echo -e "${YELLOW}HELM_REPO_URL not set. Skipping Helm repo publish${NC}"
        return
    fi

    echo "Publishing to Helm repository: $HELM_REPO_URL"

    local chart_package="$OUTPUT_DIR/$CHART_NAME-$VERSION.tgz"

    # Add repository
    helm repo add "$HELM_REPO_NAME" "$HELM_REPO_URL" || true

    # Upload chart (requires curl)
    if [ -n "${HELM_REPO_USERNAME:-}" ] && [ -n "${HELM_REPO_PASSWORD:-}" ]; then
        curl -u "$HELM_REPO_USERNAME:$HELM_REPO_PASSWORD" \
            -T "$chart_package" \
            "$HELM_REPO_URL/"
    else
        curl -T "$chart_package" "$HELM_REPO_URL/"
    fi

    echo -e "${GREEN}Chart published to Helm repository${NC}"
    echo ""
}

# Publish to GitHub Pages
publish_github_pages() {
    if [ "${PUBLISH_GITHUB_PAGES:-false}" != "true" ]; then
        return
    fi

    echo "Publishing to GitHub Pages..."

    local gh_pages_dir="$PROJECT_ROOT/.gh-pages"
    local chart_package="$OUTPUT_DIR/$CHART_NAME-$VERSION.tgz"

    # Clone or create gh-pages branch
    if [ -d "$gh_pages_dir" ]; then
        cd "$gh_pages_dir"
        git pull origin gh-pages || true
    else
        git clone --branch gh-pages "$(git remote get-url origin)" "$gh_pages_dir" 2>/dev/null || {
            mkdir -p "$gh_pages_dir"
            cd "$gh_pages_dir"
            git init
            git checkout -b gh-pages
        }
    fi

    # Copy chart
    cp "$chart_package" "$gh_pages_dir/"
    cp "$chart_package.sha256" "$gh_pages_dir/" 2>/dev/null || true

    # Generate index
    helm repo index "$gh_pages_dir" --url "https://${GITHUB_OWNER:-yawlfoundation}.github.io/${GITHUB_REPO:-yawl}"

    # Commit and push
    cd "$gh_pages_dir"
    git add .
    git commit -m "Add chart $CHART_NAME-$VERSION" || true
    git push origin gh-pages

    cd "$PROJECT_ROOT"

    echo -e "${GREEN}Chart published to GitHub Pages${NC}"
    echo ""
}

# Generate provenance
generate_provenance() {
    echo "Generating SBOM for chart..."

    if command -v syft &> /dev/null; then
        syft "$CHART_DIR/$CHART_NAME" \
            -o spdx-json="$OUTPUT_DIR/$CHART_NAME-$VERSION-sbom.spdx.json" \
            || true

        echo -e "${GREEN}SBOM generated${NC}"
    else
        echo -e "${YELLOW}Syft not installed. Skipping SBOM generation${NC}"
    fi

    echo ""
}

# Summary
print_summary() {
    echo "=============================================="
    echo "Publish Summary"
    echo "=============================================="
    echo ""
    echo "Chart: $CHART_NAME"
    echo "Version: $VERSION"
    echo "App Version: $APP_VERSION"
    echo ""
    echo "Artifacts:"
    ls -la "$OUTPUT_DIR/$CHART_NAME-$VERSION"* 2>/dev/null || true
    echo ""

    if [ -n "$OCI_REGISTRY" ]; then
        echo "OCI Registry: oci://$OCI_REGISTRY/$CHART_NAME:$VERSION"
    fi

    if [ -n "$HELM_REPO_URL" ]; then
        echo "Helm Repository: $HELM_REPO_URL"
    fi

    echo ""
    echo "To install the chart:"
    echo ""

    if [ -n "$OCI_REGISTRY" ]; then
        echo "  helm install $CHART_NAME oci://$OCI_REGISTRY/$CHART_NAME --version $VERSION"
    fi

    if [ -n "$HELM_REPO_URL" ]; then
        echo "  helm repo add $HELM_REPO_NAME $HELM_REPO_URL"
        echo "  helm install $CHART_NAME $HELM_REPO_NAME/$CHART_NAME --version $VERSION"
    fi

    echo ""
    echo -e "${GREEN}Chart publishing completed successfully!${NC}"
}

# Main execution
main() {
    check_requirements
    update_version
    update_dependencies
    lint_chart
    validate_chart
    generate_docs
    package_chart
    sign_chart
    generate_provenance
    publish_oci
    publish_helm_repo
    publish_github_pages
    print_summary
}

# Run main function
main "$@"
