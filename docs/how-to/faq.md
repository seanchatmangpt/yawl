# YAWL FAQ

**Version:** 6.0.0
**Last Updated:** 2026-02-14

---

## General

### What is YAWL?

YAWL (Yet Another Workflow Language) is a BPM/Workflow management system based on Petri Nets. It provides a formal foundation for workflow specification and supports 36+ workflow patterns.

### What can I use YAWL for?

- Business process automation
- Approval workflows
- Data pipeline orchestration
- Human task management
- System integration workflows

### Is YAWL open source?

Yes, YAWL is released under the GNU Lesser General Public License (LGPL).

---

## Deployment

### What cloud platforms are supported?

- Amazon Web Services (AWS)
- Microsoft Azure
- Google Cloud Platform (GCP)
- Oracle Cloud Infrastructure (OCI)
- IBM Cloud
- Any Kubernetes-compatible platform

### What are the system requirements?

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Kubernetes | 1.26+ | 1.29+ |
| PostgreSQL | 13+ | 15+ |
| Redis | 6.x | 7.x |
| CPU | 4 cores | 8+ cores |
| Memory | 16 GB | 32+ GB |

### Can I deploy YAWL on-premises?

Yes, YAWL can be deployed on any Kubernetes cluster, including on-premises installations like OpenShift, Rancher, or vanilla Kubernetes.

---

## Functionality

### What workflow patterns does YAWL support?

YAWL supports 36+ workflow patterns including:
- Sequence, Parallel Split, Synchronization
- Exclusive Choice, Multi-Choice
- Structured Loop, Interleaved Routing
- Cancellation patterns
- Multi-instance patterns
- State-based patterns

### Can I use YAWL without coding?

Yes, YAWL includes a visual process editor for designing workflows without writing code. However, complex data transformations may require XQuery expressions.

### Does YAWL support human tasks?

Yes, YAWL has comprehensive support for human tasks including:
- Role-based assignment
- Work queue management
- Task prioritization
- Delegation and escalation

### Can I integrate with external systems?

Yes, YAWL supports:
- REST API calls
- SOAP web services
- Database queries
- Custom code (Java)
- Message queues

---

## Performance

### How many cases can YAWL handle?

With proper scaling, YAWL can handle:
- 10,000+ cases per hour
- 500+ concurrent users
- 50,000+ work items per hour

### How do I scale YAWL?

1. Increase pod replicas (HPA)
2. Add database read replicas
3. Scale Redis cluster
4. Add worker nodes

### What affects performance?

- Database query complexity
- Task duration
- External service latency
- Data transformation complexity
- Concurrent user load

---

## Security

### How is authentication handled?

YAWL supports:
- Basic authentication
- OAuth 2.0/OIDC
- SAML 2.0
- API keys

### Is data encrypted?

Yes, all data is encrypted:
- At rest: AES-256
- In transit: TLS 1.2+
- Backups: Encrypted

### Does YAWL support RBAC?

Yes, YAWL has role-based access control with:
- Predefined roles (Admin, Manager, Participant)
- Custom role creation
- Task-level permissions
- Resource-level security

---

## Operations

### How do I backup YAWL?

1. Database backups (automated via cloud provider)
2. Configuration exports
3. Specification archives

### How do I monitor YAWL?

- Built-in monitoring dashboard
- CloudWatch/Azure Monitor integration
- Prometheus metrics
- Custom Grafana dashboards

### What is the upgrade process?

1. Backup database
2. Update Helm chart
3. Run database migrations
4. Verify functionality
5. Rollback if issues

---

## Licensing

### What license does YAWL use?

YAWL is released under the GNU Lesser General Public License (LGPL).

### Can I use YAWL commercially?

Yes, LGPL allows commercial use. Consult the license for specific requirements.

### Is there commercial support available?

Yes, commercial support is available from:
- YAWL Foundation partners
- Authorized service providers

---

## Troubleshooting

### Why is my case stuck?

Common causes:
- Deadlock in workflow design
- External service timeout
- Resource not available
- Conditional expression error

### Why can't I see my work items?

Check:
- Role assignment
- Work queue filters
- Work item status
- User permissions

### How do I reset a failed case?

```bash
curl -X POST "https://yawl.yourdomain.com/ib/api/cases/{caseId}/restart" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Support

### Where can I get help?

- Documentation: https://yawlfoundation.github.io
- Community: yawl@list.unsw.edu.au
- GitHub Issues: https://github.com/yawlfoundation/yawl/issues

### How do I report a bug?

Open an issue on GitHub:
https://github.com/yawlfoundation/yawl/issues

Include:
- YAWL version
- Steps to reproduce
- Expected behavior
- Actual behavior
- Logs (if applicable)

### Is there training available?

- Online tutorials on the YAWL website
- YouTube video tutorials
- Community workshops (periodic)
