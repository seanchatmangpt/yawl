# How to Manage Participant Access in YAWL v6

> YAWL v6 does not implement an organisational model. There are no `YOrganisationModel`,
> `ResourceManager`, roles, capabilities, positions, or org group classes in v6. The
> `yawl-resourcing` module is a stub with no Java implementation.

## What the Org Model Was in YAWL v4

In YAWL v4, the Resource Service maintained an organisational model consisting of:

- **Participants** — individual people who could be assigned work items
- **Roles** — job functions used for work item routing (e.g. "CreditAnalyst")
- **Capabilities** — skills or certifications used as routing filters
- **Positions** — organisational titles used to scope routing
- **Org groups** — containers (teams, departments) acting as tenancy boundaries

Task resourcing specs in v4 referenced these entities to determine which participants
received which work items.

None of this infrastructure exists in YAWL v6.

## How YAWL v6 Manages Participant Identity and Access

YAWL v6 delegates identity and access management entirely to your external OIDC provider.
The authoritative implementation is in:

- `src/org/yawlfoundation/yawl/integration/oauth2/YawlOAuth2Scopes.java` — scope constants
- `src/org/yawlfoundation/yawl/integration/oauth2/RbacAuthorizationEnforcer.java` — scope enforcement

### Scope-Based RBAC

Access to workflow operations is controlled by OAuth2 scopes in the caller's JWT. The
scope hierarchy is:

```
yawl:admin
  |-- yawl:designer   (specification management, participant administration)
  |-- yawl:operator   (case and work item operations)
  |-- yawl:monitor    (read-only access to cases, work items, specifications)
  |-- yawl:agent      (operator operations plus MCP and A2A endpoints)
```

These are not YAWL-internal roles. They are OAuth2 scopes you configure in your OIDC
provider (Keycloak, Auth0, Okta, Azure AD) and assign to users and service accounts.

### Operation-to-Scope Mapping

`RbacAuthorizationEnforcer` maps each YAWL workflow operation to the scopes that permit it:

| Operation | Required scope (any one suffices) |
|---|---|
| Launch case | `yawl:operator`, `yawl:admin` |
| Cancel case | `yawl:operator`, `yawl:admin` |
| Get case status | `yawl:monitor`, `yawl:operator`, `yawl:designer`, `yawl:agent`, `yawl:admin` |
| Check out work item | `yawl:operator`, `yawl:agent`, `yawl:admin` |
| Check in work item | `yawl:operator`, `yawl:agent`, `yawl:admin` |
| Load specification | `yawl:designer`, `yawl:admin` |
| Access MCP tools | `yawl:agent`, `yawl:admin` |
| Access A2A protocol | `yawl:agent`, `yawl:admin` |

## Setting Up Scope-Based Access

### 1. Configure scopes in your OIDC provider

In Keycloak, create client scopes matching the constants in `YawlOAuth2Scopes`:

```
yawl:admin
yawl:designer
yawl:operator
yawl:monitor
yawl:agent
```

Assign the scopes to users or service accounts based on their role in your organisation:

- Human workflow participants: `yawl:operator`
- Read-only monitoring dashboards: `yawl:monitor`
- Workflow designers: `yawl:designer`
- AI agent service accounts: `yawl:agent`
- System administrators: `yawl:admin`

### 2. Configure the YAWL engine as an OAuth2 resource server

Add the following to your Spring application configuration (typically `application.yml`):

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${YAWL_OAUTH2_ISSUER_URI}
          audiences:
            - yawl-api
```

The `YAWL_OAUTH2_ISSUER_URI` environment variable points to your OIDC provider's
discovery endpoint (e.g. `https://auth.example.com/realms/yawl`).

### 3. Wire `RbacAuthorizationEnforcer` into your security filter chain

```java
@Configuration
@EnableWebSecurity
public class YawlSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/specifications/**")
                    .hasAuthority("SCOPE_" + YawlOAuth2Scopes.DESIGNER)
                .requestMatchers(HttpMethod.POST, "/api/v1/cases")
                    .hasAnyAuthority(
                        "SCOPE_" + YawlOAuth2Scopes.OPERATOR,
                        "SCOPE_" + YawlOAuth2Scopes.ADMIN)
                .requestMatchers("/api/v1/workitems/**")
                    .hasAnyAuthority(
                        "SCOPE_" + YawlOAuth2Scopes.OPERATOR,
                        "SCOPE_" + YawlOAuth2Scopes.AGENT,
                        "SCOPE_" + YawlOAuth2Scopes.ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/v1/**")
                    .hasAnyAuthority(
                        "SCOPE_" + YawlOAuth2Scopes.MONITOR,
                        "SCOPE_" + YawlOAuth2Scopes.OPERATOR,
                        "SCOPE_" + YawlOAuth2Scopes.DESIGNER,
                        "SCOPE_" + YawlOAuth2Scopes.AGENT,
                        "SCOPE_" + YawlOAuth2Scopes.ADMIN)
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(yawlJwtConverter()))
            )
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }

    @Bean
    public JwtAuthenticationConverter yawlJwtConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
```

### 4. Encode participant identity in JWT claims

Because there is no org model in YAWL v6, participant-level identity (who the specific
person is, not just what they are permitted to do) is carried in standard JWT claims:

| JWT claim | Meaning | Example value |
|---|---|---|
| `sub` | Unique participant identifier | `"alice@example.com"` |
| `name` | Display name | `"Alice Smith"` |
| `email` | Contact address | `"alice@example.com"` |
| `roles` (custom) | Application-level roles | `["loan-officer", "credit-analyst"]` |
| `department` (custom) | Organisational unit | `"retail-lending"` |

Your application reads these claims from the token and uses them to:

- Display the participant's name in your worklist UI.
- Filter which work items are shown to this participant.
- Record which participant checked out and completed each work item (for audit).

YAWL itself does not inspect these claims for routing — that is your application's
responsibility.

## Participant-Level Routing Without an Org Model

Since YAWL v6 has no built-in worklist, task-to-participant matching is done by your
application. The recommended pattern:

1. Your application fetches all enabled work items from the engine via Interface B.
2. It reads each work item's `taskID` and `specURI`.
3. It looks up the task in its own configuration to determine which application role
   should handle it.
4. It shows only matching work items to each participant, based on the participant's
   JWT claims.
5. The participant clicks "take" in your UI; your application calls
   `checkOutWorkItem` on their behalf.
6. After the participant completes the form, your application calls
   `checkInWorkItem` with the output data.

This is architecturally equivalent to what the YAWL v4 Resource Service did, but
implemented as part of your application rather than as a separate YAWL service.

## SPIFFE/SPIRE for Service Account Identity

For service-to-service calls (e.g. an A2A agent calling the engine), YAWL v6 supports
SPIFFE/SPIRE for workload identity. Rather than long-lived service account credentials,
each workload receives a short-lived X.509 SVID. This is fully implemented in the
`yawl-integration` module.

Configure SPIFFE by setting `SPIFFE_ENDPOINT_SOCKET` to the path of your SPIRE agent
socket. The engine verifies the SVID's SPIFFE ID against an allowlist you configure.

## Verify

Confirm that scope enforcement is working:

```bash
# Get a token for a user with yawl:monitor scope
MONITOR_TOKEN=$(curl -s -X POST "$OIDC_TOKEN_URL" \
  -d "grant_type=client_credentials" \
  -d "client_id=monitor-service" \
  -d "client_secret=$SECRET" \
  -d "scope=yawl:monitor" | jq -r '.access_token')

# Read-only operations should succeed
curl -s -H "Authorization: Bearer $MONITOR_TOKEN" \
  "http://localhost:8080/yawl/ib?action=getCompleteListOfLiveWorkItems&sessionHandle=$SESSION"

# Mutating operations should be rejected (HTTP 403)
curl -s -X POST -H "Authorization: Bearer $MONITOR_TOKEN" \
  "http://localhost:8080/yawl/ib" \
  -d "action=checkOutWorkItem&workItemID=1:Task1&sessionHandle=$SESSION"
```

The second call must return HTTP 403 if RBAC is correctly enforced.

## Troubleshooting

**JWT does not contain expected scopes**
Use `jwt.io` to decode the token and inspect the `scope` claim. Ensure your OIDC client
configuration requests the YAWL scopes when obtaining tokens. In Keycloak, verify that
the client scope mappings include the `yawl:*` scopes you created.

**SCOPE_ prefix mismatch**
Spring Security prefixes OAuth2 scopes with `SCOPE_` by default. `YawlOAuth2Scopes.OPERATOR`
is `"yawl:operator"`, but the authority in the `SecurityContext` is `"SCOPE_yawl:operator"`.
Use `"SCOPE_" + YawlOAuth2Scopes.OPERATOR` (or `hasAuthority("SCOPE_yawl:operator")`)
in your security configuration. Do not use `hasRole()` — that adds a `ROLE_` prefix which
does not match OAuth2 scopes.

**"Invalid session" from Interface B despite valid JWT**
The YAWL engine session handle (used in Interface B calls) is separate from the JWT.
Authenticate to the engine with `action=connect` to obtain a session handle, then include
both the JWT header and the session handle parameter in subsequent calls if your deployment
requires both layers of authentication.
