#!/usr/bin/env bash
#==============================================================================
# YAWL Java 25 Migration Script
#==============================================================================
# Automates the migration of YAWL from Java 21 to Java 25
#
# Usage:
#   ./scripts/migrate-to-java25.sh [options]
#
# Options:
#   --dry-run        Show what would be changed without making changes
#   --backup         Create backup before making changes (default)
#   --no-backup      Skip backup creation
#   --verify-only    Only verify Java 25 availability
#   --update-all     Update all configuration files
#   --help           Show this help message
#
# Prerequisites:
#   - Java 25 installed (verify with: java --version)
#   - Git repository (for backup)
#   - Maven 3.9.6+ and Ant 1.10.14+
#
# Author: YAWL Architecture Team
# Date: 2026-02-15
# Version: 1.0.0
#==============================================================================

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="${PROJECT_ROOT}/backups/java25-migration-$(date +%Y%m%d-%H%M%S)"
DRY_RUN=false
CREATE_BACKUP=true
VERIFY_ONLY=false
UPDATE_ALL=false

# Files to update
POM_XML="${PROJECT_ROOT}/pom.xml"
BUILD_XML="${PROJECT_ROOT}/build/build.xml"
DOCKERFILE_MAIN="${PROJECT_ROOT}/Dockerfile"
DOCKERFILE_DEV="${PROJECT_ROOT}/Dockerfile.dev"
DOCKERFILE_BUILD="${PROJECT_ROOT}/Dockerfile.build"
GITHUB_WORKFLOW="${PROJECT_ROOT}/.github/workflows/unit-tests.yml"

#==============================================================================
# Helper Functions
#==============================================================================

print_header() {
    echo -e "${BLUE}==============================================================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}==============================================================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

#==============================================================================
# Verification Functions
#==============================================================================

verify_java25() {
    print_header "Verifying Java 25 Installation"

    if ! command -v java &> /dev/null; then
        print_error "Java not found in PATH"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)

    if [ "$JAVA_VERSION" -lt 25 ]; then
        print_error "Java 25 required, found Java $JAVA_VERSION"
        print_info "Install Java 25 with: sdk install java 25-tem"
        exit 1
    fi

    print_success "Java 25 detected: $(java -version 2>&1 | head -n 1)"

    # Verify javac
    if ! command -v javac &> /dev/null; then
        print_error "javac (Java compiler) not found"
        exit 1
    fi

    print_success "javac found: $(javac -version 2>&1)"
}

verify_maven() {
    print_header "Verifying Maven"

    if ! command -v mvn &> /dev/null; then
        print_error "Maven not found in PATH"
        exit 1
    fi

    MVN_VERSION=$(mvn --version | head -n 1 | awk '{print $3}')
    print_success "Maven $MVN_VERSION detected"

    # Check if version is >= 3.9.0
    if [[ "$(printf '%s\n' "3.9.0" "$MVN_VERSION" | sort -V | head -n1)" != "3.9.0" ]]; then
        print_warning "Maven 3.9.0+ recommended, found $MVN_VERSION"
    fi
}

verify_ant() {
    print_header "Verifying Ant"

    if ! command -v ant &> /dev/null; then
        print_warning "Ant not found (optional for Maven-based builds)"
        return
    fi

    ANT_VERSION=$(ant -version | head -n 1 | awk '{print $4}')
    print_success "Ant $ANT_VERSION detected"
}

verify_docker() {
    print_header "Verifying Docker"

    if ! command -v docker &> /dev/null; then
        print_warning "Docker not found (optional)"
        return
    fi

    DOCKER_VERSION=$(docker --version | awk '{print $3}' | tr -d ',')
    print_success "Docker $DOCKER_VERSION detected"
}

verify_git() {
    print_header "Verifying Git Repository"

    if [ ! -d "${PROJECT_ROOT}/.git" ]; then
        print_warning "Not a Git repository (backup may fail)"
        return 1
    fi

    print_success "Git repository detected"

    # Check for uncommitted changes
    if ! git -C "$PROJECT_ROOT" diff --quiet || ! git -C "$PROJECT_ROOT" diff --cached --quiet; then
        print_warning "Uncommitted changes detected"
        git -C "$PROJECT_ROOT" status --short
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

#==============================================================================
# Backup Functions
#==============================================================================

create_backup() {
    if [ "$CREATE_BACKUP" = false ]; then
        print_info "Skipping backup (--no-backup specified)"
        return
    fi

    print_header "Creating Backup"

    mkdir -p "$BACKUP_DIR"

    # Backup configuration files
    echo "Backing up configuration files to: $BACKUP_DIR"

    [ -f "$POM_XML" ] && cp "$POM_XML" "$BACKUP_DIR/pom.xml.bak"
    [ -f "$BUILD_XML" ] && cp "$BUILD_XML" "$BACKUP_DIR/build.xml.bak"
    [ -f "$DOCKERFILE_MAIN" ] && cp "$DOCKERFILE_MAIN" "$BACKUP_DIR/Dockerfile.bak"
    [ -f "$DOCKERFILE_DEV" ] && cp "$DOCKERFILE_DEV" "$BACKUP_DIR/Dockerfile.dev.bak"
    [ -f "$DOCKERFILE_BUILD" ] && cp "$DOCKERFILE_BUILD" "$BACKUP_DIR/Dockerfile.build.bak"
    [ -f "$GITHUB_WORKFLOW" ] && cp "$GITHUB_WORKFLOW" "$BACKUP_DIR/unit-tests.yml.bak"

    # Backup all Dockerfiles in containerization/
    if [ -d "${PROJECT_ROOT}/containerization" ]; then
        cp -r "${PROJECT_ROOT}/containerization" "$BACKUP_DIR/"
    fi

    # Backup Oracle Cloud Dockerfiles
    if [ -d "${PROJECT_ROOT}/ci-cd/oracle-cloud" ]; then
        mkdir -p "$BACKUP_DIR/oracle-cloud"
        cp "${PROJECT_ROOT}/ci-cd/oracle-cloud"/Dockerfile.* "$BACKUP_DIR/oracle-cloud/" 2>/dev/null || true
    fi

    print_success "Backup created: $BACKUP_DIR"
}

#==============================================================================
# Update Functions
#==============================================================================

update_pom_xml() {
    print_header "Updating pom.xml"

    if [ ! -f "$POM_XML" ]; then
        print_warning "pom.xml not found, skipping"
        return
    fi

    if [ "$DRY_RUN" = true ]; then
        print_info "[DRY RUN] Would update pom.xml:"
        echo "  - maven.compiler.source: 21 → 25"
        echo "  - maven.compiler.target: 21 → 25"
        echo "  - maven.compiler.release: 21 → 25"
        echo "  - maven-compiler-plugin: <source> and <target> to 25"
        echo "  - Add --enable-preview to compilerArgs"
        return
    fi

    # Update properties
    sed -i.tmp 's/<maven.compiler.source>21<\/maven.compiler.source>/<maven.compiler.source>25<\/maven.compiler.source>/' "$POM_XML"
    sed -i.tmp 's/<maven.compiler.target>21<\/maven.compiler.target>/<maven.compiler.target>25<\/maven.compiler.target>/' "$POM_XML"

    # Add maven.compiler.release if not present
    if ! grep -q '<maven.compiler.release>' "$POM_XML"; then
        sed -i.tmp '/<maven.compiler.target>25<\/maven.compiler.target>/a\        <maven.compiler.release>25</maven.compiler.release>' "$POM_XML"
    else
        sed -i.tmp 's/<maven.compiler.release>21<\/maven.compiler.release>/<maven.compiler.release>25<\/maven.compiler.release>/' "$POM_XML"
    fi

    # Update maven-compiler-plugin configuration
    sed -i.tmp 's/<source>21<\/source>/<source>25<\/source>/' "$POM_XML"
    sed -i.tmp 's/<target>21<\/target>/<target>25<\/target>/' "$POM_XML"

    # Add --enable-preview if not present
    if ! grep -q -- '--enable-preview' "$POM_XML"; then
        # Add to compilerArgs section
        sed -i.tmp '/<arg>-Xlint:all<\/arg>/a\                    <arg>--enable-preview<\/arg>' "$POM_XML"
    fi

    # Update maven-compiler-plugin version to 3.13.0
    sed -i.tmp 's/<version>3\.11\.0<\/version>/<version>3.13.0<\/version>/' "$POM_XML"

    # Update surefire plugin to add --enable-preview
    if ! grep -q '<argLine>--enable-preview</argLine>' "$POM_XML"; then
        sed -i.tmp '/<artifactId>maven-surefire-plugin<\/artifactId>/,/<\/plugin>/ {
            /<configuration>/a\                    <argLine>--enable-preview<\/argLine>
        }' "$POM_XML"
    fi

    rm -f "${POM_XML}.tmp"

    print_success "Updated pom.xml"
}

update_build_xml() {
    print_header "Updating build.xml"

    if [ ! -f "$BUILD_XML" ]; then
        print_warning "build.xml not found, skipping"
        return
    fi

    if [ "$DRY_RUN" = true ]; then
        print_info "[DRY RUN] Would update build.xml:"
        echo "  - Uncomment and set ant.build.javac.source=25"
        echo "  - Uncomment and set ant.build.javac.target=25"
        echo "  - Add ant.build.javac.release=25"
        return
    fi

    # Uncomment and update Java version properties
    sed -i.tmp 's/<!--    <property name="ant.build.javac.source" value="1.8"\/>-->/<property name="ant.build.javac.source" value="25"\/>/' "$BUILD_XML"
    sed -i.tmp 's/<!--    <property name="ant.build.javac.target" value="1.8"\/>-->/<property name="ant.build.javac.target" value="25"\/>/' "$BUILD_XML"

    # Add release property if not present
    if ! grep -q 'ant.build.javac.release' "$BUILD_XML"; then
        sed -i.tmp '/<property name="ant.build.javac.target" value="25"\/>/a\    <property name="ant.build.javac.release" value="25"\/>' "$BUILD_XML"
    fi

    rm -f "${BUILD_XML}.tmp"

    print_success "Updated build.xml"
}

update_dockerfiles() {
    print_header "Updating Dockerfiles"

    if [ "$DRY_RUN" = true ]; then
        print_info "[DRY RUN] Would update Dockerfiles:"
        echo "  - Replace eclipse-temurin:17 → eclipse-temurin:25"
        echo "  - Replace eclipse-temurin:21 → eclipse-temurin:25"
        echo "  - Add --enable-preview to JAVA_OPTS"
        return
    fi

    # Update main Dockerfile
    if [ -f "$DOCKERFILE_MAIN" ]; then
        sed -i.tmp 's/eclipse-temurin:21/eclipse-temurin:25/g' "$DOCKERFILE_MAIN"
        rm -f "${DOCKERFILE_MAIN}.tmp"
        print_success "Updated Dockerfile"
    fi

    # Update dev Dockerfile
    if [ -f "$DOCKERFILE_DEV" ]; then
        sed -i.tmp 's/eclipse-temurin:21/eclipse-temurin:25/g' "$DOCKERFILE_DEV"
        rm -f "${DOCKERFILE_DEV}.tmp"
        print_success "Updated Dockerfile.dev"
    fi

    # Update build Dockerfile
    if [ -f "$DOCKERFILE_BUILD" ]; then
        sed -i.tmp 's/eclipse-temurin:17/eclipse-temurin:25/g' "$DOCKERFILE_BUILD"
        rm -f "${DOCKERFILE_BUILD}.tmp"
        print_success "Updated Dockerfile.build"
    fi

    # Update containerization Dockerfiles
    if [ -d "${PROJECT_ROOT}/containerization" ]; then
        find "${PROJECT_ROOT}/containerization" -name "Dockerfile*" -type f | while read -r dockerfile; do
            sed -i.tmp 's/eclipse-temurin:17/eclipse-temurin:25/g' "$dockerfile"
            sed -i.tmp 's/eclipse-temurin:21/eclipse-temurin:25/g' "$dockerfile"
            rm -f "${dockerfile}.tmp"
            print_success "Updated $(basename "$dockerfile")"
        done
    fi

    # Update Oracle Cloud Dockerfiles
    if [ -d "${PROJECT_ROOT}/ci-cd/oracle-cloud" ]; then
        find "${PROJECT_ROOT}/ci-cd/oracle-cloud" -name "Dockerfile*" -type f | while read -r dockerfile; do
            sed -i.tmp 's/eclipse-temurin:17/eclipse-temurin:25/g' "$dockerfile"
            sed -i.tmp 's/eclipse-temurin:21/eclipse-temurin:25/g' "$dockerfile"
            rm -f "${dockerfile}.tmp"
            print_success "Updated Oracle Cloud $(basename "$dockerfile")"
        done
    fi
}

update_github_workflow() {
    print_header "Updating GitHub Workflow"

    if [ ! -f "$GITHUB_WORKFLOW" ]; then
        print_warning "GitHub workflow not found, skipping"
        return
    fi

    if [ "$DRY_RUN" = true ]; then
        print_info "[DRY RUN] Would update GitHub workflow:"
        echo "  - Add Java 25 to test matrix"
        echo "  - Add MAVEN_OPTS with --enable-preview"
        return
    fi

    # Add Java 25 to matrix if not present
    if ! grep -q "java-version.*25" "$GITHUB_WORKFLOW"; then
        sed -i.tmp '/java-version:/ {
            N
            s/\[21\]/[21, 25]/
        }' "$GITHUB_WORKFLOW"
    fi

    # Add MAVEN_OPTS if not present
    if ! grep -q "MAVEN_OPTS.*enable-preview" "$GITHUB_WORKFLOW"; then
        sed -i.tmp '/Run unit tests with Maven/,/mvn/ {
            /mvn/i\        env:\n          MAVEN_OPTS: "--enable-preview"
        }' "$GITHUB_WORKFLOW"
    fi

    rm -f "${GITHUB_WORKFLOW}.tmp"

    print_success "Updated GitHub workflow"
}

#==============================================================================
# Test Functions
#==============================================================================

test_compilation() {
    print_header "Testing Compilation with Java 25"

    if [ "$DRY_RUN" = true ]; then
        print_info "[DRY RUN] Would test compilation with: mvn clean compile"
        return
    fi

    cd "$PROJECT_ROOT"

    print_info "Running: mvn clean compile -DskipTests"
    if mvn clean compile -DskipTests; then
        print_success "Compilation successful with Java 25"
    else
        print_error "Compilation failed"
        print_warning "Review errors and fix before proceeding"
        exit 1
    fi
}

test_unit_tests() {
    print_header "Running Unit Tests"

    if [ "$DRY_RUN" = true ]; then
        print_info "[DRY RUN] Would run tests with: mvn test"
        return
    fi

    cd "$PROJECT_ROOT"

    print_info "Running: mvn test"
    if mvn test; then
        print_success "All tests passed with Java 25"
    else
        print_warning "Some tests failed - review test results"
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

#==============================================================================
# Main Execution
#==============================================================================

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --backup)
                CREATE_BACKUP=true
                shift
                ;;
            --no-backup)
                CREATE_BACKUP=false
                shift
                ;;
            --verify-only)
                VERIFY_ONLY=true
                shift
                ;;
            --update-all)
                UPDATE_ALL=true
                shift
                ;;
            --help)
                grep '^#' "$0" | head -n 30 | cut -c 2-
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done
}

main() {
    parse_arguments "$@"

    print_header "YAWL Java 25 Migration Script"
    echo "Project: $PROJECT_ROOT"
    echo "Dry Run: $DRY_RUN"
    echo "Create Backup: $CREATE_BACKUP"
    echo ""

    # Verification phase
    verify_java25
    verify_maven
    verify_ant
    verify_docker
    verify_git || true

    if [ "$VERIFY_ONLY" = true ]; then
        print_success "Verification complete - all prerequisites met"
        exit 0
    fi

    # Backup phase
    create_backup

    # Update phase
    update_pom_xml
    update_build_xml
    update_dockerfiles
    update_github_workflow

    if [ "$DRY_RUN" = true ]; then
        print_header "Dry Run Complete"
        print_info "No changes were made. Run without --dry-run to apply changes."
        exit 0
    fi

    # Test phase
    print_header "Testing Changes"
    read -p "Run compilation test? (Y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]] || [[ -z $REPLY ]]; then
        test_compilation
    fi

    read -p "Run unit tests? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        test_unit_tests
    fi

    # Summary
    print_header "Migration Complete"
    print_success "Successfully migrated to Java 25"
    echo ""
    echo "Next steps:"
    echo "  1. Review changes: git diff"
    echo "  2. Commit changes: git commit -am 'Upgrade to Java 25'"
    echo "  3. Run full test suite: mvn clean verify"
    echo "  4. Build Docker images: docker build -f Dockerfile.java25 -t yawl:java25 ."
    echo "  5. Deploy to staging and test"
    echo ""
    echo "Backup location: $BACKUP_DIR"
    echo ""
    echo "For more information, see:"
    echo "  docs/deployment/java25-upgrade-guide.md"
    echo "  docs/deployment/java25-implementation-checklist.md"
}

# Run main function
main "$@"
