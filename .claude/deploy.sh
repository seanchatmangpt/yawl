#!/bin/bash
# YAWL Deploy Skill - Claude Code 2026 Best Practices
# Usage: /yawl-deploy [--docker] [--service=NAME]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Deploy Skill - Deploy YAWL services

Usage: /yawl-deploy [options]

Options:
  --docker           Deploy to Docker container
  --service=NAME     Deploy specific service
  --tomcat=PATH      Tomcat installation path
  --stop             Stop deployed services
  -h, --help         Show this help message

Services:
  engine            Core YAWL engine
  resourceService   Resource allocation
  workletService    Dynamic process adaptation
  monitorService    Process monitoring
  schedulingService Calendar-based scheduling
  costService       Cost tracking
  balancer          Load balancing

Examples:
  /yawl-deploy                        # Deploy all WARs to Tomcat
  /yawl-deploy --docker               # Deploy to Docker
  /yawl-deploy --service=engine       # Deploy only engine
EOF
}

# Parse arguments
DEPLOY_DOCKER=false
SERVICE=""
TOMCAT_PATH=""
STOP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --docker)
            DEPLOY_DOCKER=true
            shift
            ;;
        --service=*)
            SERVICE="${1#*=}"
            shift
            ;;
        --tomcat=*)
            TOMCAT_PATH="${1#*=}"
            shift
            ;;
        --stop)
            STOP=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

cd "${PROJECT_ROOT}"

# Check for WAR files
if [[ ! -d "${PROJECT_ROOT}/output" ]]; then
    echo -e "${YELLOW}[yawl-deploy] No output directory found, building first...${NC}"
    ant -f build/build.xml buildWebApps
fi

if [[ "${STOP}" == "true" ]]; then
    echo -e "${BLUE}[yawl-deploy] Stopping services...${NC}"

    if [[ "${DEPLOY_DOCKER}" == "true" ]]; then
        docker-compose down 2>/dev/null || echo "No Docker services running"
    else
        if [[ -n "${TOMCAT_PATH}" ]] && [[ -x "${TOMCAT_PATH}/bin/shutdown.sh" ]]; then
            "${TOMCAT_PATH}/bin/shutdown.sh"
        fi
    fi

    echo -e "${GREEN}[yawl-deploy] Services stopped${NC}"
    exit 0
fi

echo -e "${BLUE}[yawl-deploy] Deploying YAWL services...${NC}"

# Deploy based on mode
if [[ "${DEPLOY_DOCKER}" == "true" ]]; then
    echo -e "${BLUE}[yawl-deploy] Deploying to Docker...${NC}"

    if [[ -f "${PROJECT_ROOT}/docker-compose.yml" ]]; then
        docker-compose up -d
        echo -e "${GREEN}[yawl-deploy] Docker deployment started${NC}"
    else
        echo -e "${RED}[yawl-deploy] docker-compose.yml not found${NC}"
        exit 1
    fi
else
    # Find Tomcat
    if [[ -z "${TOMCAT_PATH}" ]]; then
        # Check common locations
        for path in /usr/local/tomcat /opt/tomcat ~/tomcat; do
            if [[ -d "${path}" ]]; then
                TOMCAT_PATH="${path}"
                break
            fi
        done
    fi

    if [[ -z "${TOMCAT_PATH}" ]]; then
        echo -e "${RED}[yawl-deploy] Tomcat not found. Specify with --tomcat=PATH${NC}"
        exit 1
    fi

    echo -e "${BLUE}[yawl-deploy] Using Tomcat at: ${TOMCAT_PATH}${NC}"

    # Deploy WAR files
    WEBAPPS="${TOMCAT_PATH}/webapps"

    if [[ -n "${SERVICE}" ]]; then
        WAR_FILE="${PROJECT_ROOT}/output/${SERVICE}.war"
        if [[ -f "${WAR_FILE}" ]]; then
            cp "${WAR_FILE}" "${WEBAPPS}/"
            echo -e "${GREEN}[yawl-deploy] Deployed ${SERVICE}.war${NC}"
        else
            echo -e "${RED}[yawl-deploy] WAR file not found: ${WAR_FILE}${NC}"
            exit 1
        fi
    else
        # Deploy all WARs
        for war in "${PROJECT_ROOT}"/output/*.war; do
            if [[ -f "${war}" ]]; then
                cp "${war}" "${WEBAPPS}/"
                echo -e "${GREEN}[yawl-deploy] Deployed $(basename "${war}")${NC}"
            fi
        done
    fi

    echo -e "${GREEN}[yawl-deploy] Deployment complete${NC}"
    echo -e "${YELLOW}[yawl-deploy] Restart Tomcat to apply changes${NC}"
fi
