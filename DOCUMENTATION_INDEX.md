# YAWL Documentation Index

Welcome to the comprehensive YAWL documentation suite. This index will help you navigate all available documentation resources.

## Quick Links

- **New to YAWL?** Start with [README.md](README.md) for an overview
- **Setting up YAWL?** See [INSTALLATION.md](INSTALLATION.md)
- **Understanding the system?** Read [ARCHITECTURE.md](ARCHITECTURE.md)
- **Running YAWL in production?** Check [OPERATIONS.md](OPERATIONS.md)
- **Something not working?** Consult [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- **Building with YAWL?** Review [API.md](API.md)
- **Want to contribute?** Read [CONTRIBUTING.md](CONTRIBUTING.md)

## Documentation Overview

### 1. [ARCHITECTURE.md](ARCHITECTURE.md) - System Design & Components
**For**: Architects, senior developers, system designers
**Length**: ~1,900 words
**Contains**:
- System architecture overview
- Core component descriptions
- Architecture diagrams (SOA, Engine internals, Data flow)
- Service interactions
- Deployment architecture patterns
- Scalability design
- Security architecture layers

**Key Sections**:
- Overview & architectural principles
- System components (10+ microservices)
- Complete architecture diagrams
- Database schema overview
- Service communication patterns
- Performance bottleneck analysis
- Security implementation layers

---

### 2. [INSTALLATION.md](INSTALLATION.md) - Setup & Deployment
**For**: DevOps engineers, system administrators, developers
**Length**: ~2,800 words
**Contains**:
- System and software prerequisites
- Quick start (30 seconds)
- Docker installation methods
- Docker Compose multi-service setup
- Kubernetes deployment (Helm & manual)
- Cloud platform deployments (GCP, AWS, Azure)
- Post-installation configuration
- Verification and testing

**Quick Start Options**:
- **Docker**: `docker-compose up -d` (fastest)
- **Kubernetes**: Helm chart installation
- **Cloud**: GCP Marketplace, AWS, Azure deployment
- **Manual**: Step-by-step installation

**Key Sections**:
- Prerequisites checklist
- 30-second quick start
- Docker build and pre-built image options
- Docker Compose with monitoring stack
- Kubernetes setup (GKE, EKS, AKS)
- Cloud platform specific guides
- Troubleshooting common installation issues

---

### 3. [OPERATIONS.md](OPERATIONS.md) - Running YAWL in Production
**For**: Operations engineers, DevOps teams, system administrators
**Length**: ~3,600 words
**Contains**:
- Daily operations checklist
- Monitoring and health checks
- Backup and recovery procedures
- Performance tuning strategies
- Scaling operations
- User and role management
- Workflow deployment
- Case management
- Maintenance schedules
- Incident response

**Daily Tasks**:
- Morning health check
- Hourly monitoring
- Log review
- Performance baseline
- Backup verification

**Key Sections**:
- Daily/hourly operations procedures
- Prometheus metrics and Grafana setup
- Automated and manual backups
- Database and application tuning
- Horizontal/vertical scaling
- User lifecycle management
- Workflow deployment procedures
- Incident response playbooks

---

### 4. [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Problem Diagnosis & Resolution
**For**: Support teams, operators, developers
**Length**: ~4,500 words
**Contains**:
- Systematic troubleshooting methodology
- Installation issues (10+ scenarios)
- Service connectivity problems
- Database issues (5+ categories)
- Performance problems
- Workflow execution issues
- Authentication/authorization issues
- Container and Kubernetes issues
- Data corruption and loss recovery
- External integration failures

**Common Issues Covered**:
- Service won't start → 7 solutions
- Database connection fails → 6 solutions
- High CPU usage → 6 solutions
- Memory leaks → 6 solutions
- Cases stuck in workflow → 4 solutions
- Port already in use → 5 solutions
- Login failures → 5 solutions
- Pod restart loops → 3 solutions

**Key Sections**:
- General troubleshooting methodology
- Installation issue resolution
- Connectivity diagnostics
- Database problem solving
- Performance troubleshooting
- Workflow execution debugging
- Authentication/authorization fixes
- Container/Kubernetes issues
- Data recovery procedures
- Diagnostic tools and commands

---

### 5. [API.md](API.md) - REST API Reference
**For**: Developers, integrators, API consumers
**Length**: ~2,300 words
**Contains**:
- API overview and authentication methods
- Complete endpoint reference (40+ endpoints)
- Resource schemas (Workflow, Case, WorkItem, User)
- Request/response formats (XML, JSON)
- Error handling guide
- Usage examples
- Rate limiting
- Webhooks
- SDK references

**Endpoint Categories**:
- **Workflow Management**: Deploy, enable, disable
- **Case Management**: Create, list, monitor
- **Work Items**: Execute, complete, skip
- **Users**: CRUD, password, roles
- **Roles**: Create, assign
- **Monitoring**: Statistics, history, performance

**Example Workflows**:
- Complete order processing flow
- Batch case creation
- Dynamic workflow deployment
- Webhook event handling

**Key Sections**:
- Base URL and API versions
- Three authentication methods
- 40+ endpoint specifications
- Resource schema definitions
- Request/response examples
- HTTP status codes
- Error scenarios
- Complete workflow examples
- Batch operations
- Rate limiting and retry logic
- Webhook setup and events
- SDKs (Python, Java, cURL)

---

### 6. [CONTRIBUTING.md](CONTRIBUTING.md) - Developer Guidelines
**For**: Contributors, developers, maintainers
**Length**: ~2,300 words
**Contains**:
- Getting started for contributors
- Development environment setup
- Code organization
- Development workflow
- Coding standards
- Testing procedures
- Documentation guidelines
- Build and release process
- Pull request process
- Community guidelines

**Development Setup**:
- Clone repository
- Create feature branch
- Set up IDE (Eclipse, IntelliJ)
- Initialize database
- Build and test

**Contributing Process**:
1. Fork repository
2. Create feature branch
3. Make changes
4. Add tests
5. Update documentation
6. Submit pull request
7. Address review comments
8. Get merged

**Key Sections**:
- Prerequisites and setup
- Development environment configuration
- Code organization and modules
- Feature development workflow
- Coding standards and style guide
- Unit and integration testing
- Documentation standards
- Build and release procedures
- Pull request process
- Community guidelines
- Development tips and debugging

---

## How to Use This Documentation

### If You Are...

**A New YAWL User**
1. Read: [README.md](README.md) - Project overview
2. Read: [ARCHITECTURE.md](ARCHITECTURE.md) - Understand the system
3. Follow: [INSTALLATION.md](INSTALLATION.md) - Install YAWL

**Setting Up YAWL**
1. Check: [INSTALLATION.md](INSTALLATION.md) prerequisites
2. Choose: Docker, Kubernetes, or Cloud deployment
3. Follow: Step-by-step installation guide
4. Verify: Post-installation checklist
5. Troubleshoot: [TROUBLESHOOTING.md](TROUBLESHOOTING.md) if needed

**Running YAWL in Production**
1. Review: [OPERATIONS.md](OPERATIONS.md) daily checklist
2. Set up: Monitoring and backups
3. Configure: Performance tuning
4. Plan: Scaling and maintenance
5. Reference: Incident response procedures

**Troubleshooting an Issue**
1. Identify: Problem category
2. Consult: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
3. Follow: Diagnosis steps
4. Try: Solutions in order
5. If unresolved: Check logs and system state

**Building Applications with YAWL**
1. Read: [API.md](API.md) overview
2. Choose: Authentication method
3. Study: Endpoint reference
4. Review: Code examples
5. Try: Example workflows
6. Implement: Your integration

**Contributing to YAWL**
1. Read: [CONTRIBUTING.md](CONTRIBUTING.md)
2. Set up: Development environment
3. Create: Feature branch
4. Make: Your changes
5. Test: Thoroughly
6. Submit: Pull request

---

## Document Statistics

| Document | Size | Words | Key Content |
|----------|------|-------|-------------|
| ARCHITECTURE.md | 35 KB | 1,900 | Architecture diagrams, components, security |
| INSTALLATION.md | 23 KB | 2,800 | Setup guides, cloud platforms, troubleshooting |
| OPERATIONS.md | 31 KB | 3,600 | Monitoring, backups, scaling, maintenance |
| TROUBLESHOOTING.md | 34 KB | 4,500 | 150+ issue scenarios with solutions |
| API.md | 21 KB | 2,300 | 40+ endpoints, authentication, examples |
| CONTRIBUTING.md | 19 KB | 2,300 | Development setup, coding standards, PR process |
| **Total** | **163 KB** | **17,489** | **Comprehensive documentation** |

---

## Features of This Documentation

- ✅ **Complete Coverage**: All aspects of YAWL covered
- ✅ **Practical Examples**: 100+ code examples and commands
- ✅ **Diagrams**: 25+ ASCII architecture diagrams
- ✅ **Step-by-Step**: Easy to follow procedures
- ✅ **Troubleshooting**: 150+ common issues with solutions
- ✅ **Scripts**: 30+ automation scripts
- ✅ **Production Ready**: Enterprise-grade guidance
- ✅ **Multiple Formats**: Docker, Kubernetes, Cloud platforms
- ✅ **Security Focused**: Best practices and hardening guides
- ✅ **Developer Friendly**: Clear examples and SDKs

---

## Common Workflows

### Get YAWL Running (5 minutes)
```bash
# See INSTALLATION.md for detailed instructions
docker-compose up -d
# Access: http://localhost:8080/resourceService/
```

### Create and Execute a Workflow
```bash
# See API.md for endpoint details
1. Deploy workflow (API POST /engine/workflows)
2. Create case (API POST /engine/cases)
3. Complete work items (API POST /engine/workitems/{id}/complete)
4. Monitor progress (API GET /engine/cases/{id})
```

### Monitor System Health
```bash
# See OPERATIONS.md for details
1. Check services: docker-compose ps
2. View metrics: http://localhost:9090 (Prometheus)
3. Dashboard: http://localhost:3000 (Grafana)
4. Review logs: docker logs yawl-engine
```

### Troubleshoot an Issue
```bash
# See TROUBLESHOOTING.md for diagnosis
1. Identify issue type
2. Run diagnostic commands
3. Follow solution steps
4. Verify resolution
```

---

## Additional Resources

- **YAWL Foundation**: https://www.yawlfoundation.org
- **GitHub Repository**: https://github.com/yawlfoundation/yawl
- **Community Forum**: https://forum.yawlfoundation.org
- **Issue Tracker**: https://github.com/yawlfoundation/yawl/issues
- **Email Support**: support@yawlfoundation.org

---

## Documentation Version

- **Version**: 1.0
- **Created**: 2026-02-14
- **Maintained By**: YAWL Foundation
- **Status**: Production Ready

---

## Table of Contents by Role

### For System Administrators
1. [INSTALLATION.md](INSTALLATION.md) - Getting started
2. [OPERATIONS.md](OPERATIONS.md) - Day-to-day operations
3. [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Problem solving

### For Developers
1. [ARCHITECTURE.md](ARCHITECTURE.md) - Understanding the system
2. [API.md](API.md) - Integration guide
3. [CONTRIBUTING.md](CONTRIBUTING.md) - Contributing code

### For DevOps/SRE
1. [INSTALLATION.md](INSTALLATION.md) - Deployment options
2. [OPERATIONS.md](OPERATIONS.md) - Monitoring and scaling
3. [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Issue resolution

### For Enterprise Users
1. [README.md](README.md) - Product overview
2. [ARCHITECTURE.md](ARCHITECTURE.md) - Technical understanding
3. [INSTALLATION.md](INSTALLATION.md) - Deployment planning

---

## Quick Reference Commands

See the specific documentation files for more details:

**Start YAWL**: `docker-compose up -d`
**Stop YAWL**: `docker-compose down`
**View logs**: `docker-compose logs -f yawl`
**Check health**: `curl http://localhost:8080/resourceService/`
**Run tests**: `cd build && ant test`
**Build image**: `docker build -f Dockerfile -t yawl:5.2.0 .`

---

**Last Updated**: 2026-02-14
**Total Documentation**: 17,489 words across 6 comprehensive guides
