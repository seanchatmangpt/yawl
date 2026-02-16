package org.yawlfoundation.yawl.integration.spiffe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * SPIFFE Federation Configuration for Multi-Cloud Identity
 *
 * Manages SPIFFE federation bundles that enable cross-trust-domain authentication.
 * This allows YAWL workloads to authenticate with services in different clouds:
 *
 *   - GCP SPIRE: spiffe://gcp.yawl.cloud
 *   - AWS SPIRE: spiffe://aws.yawl.cloud
 *   - Azure SPIRE: spiffe://azure.yawl.cloud
 *   - On-prem: spiffe://onprem.yawl.cloud
 *
 * Federation enables a workload in GCP to securely communicate with a workload
 * in AWS using SPIFFE identities, without shared credentials.
 *
 * Configuration sources:
 *   1. SPIFFE_FEDERATION_CONFIG environment variable (path to YAML)
 *   2. /etc/spiffe/federation.yaml (default)
 *   3. Programmatic configuration
 *
 * Example federation.yaml:
 * <pre>
 * federations:
 *   - trust_domain: gcp.yawl.cloud
 *     bundle_endpoint: https://spire.gcp.example.com/bundle
 *   - trust_domain: aws.yawl.cloud
 *     bundle_endpoint: https://spire.aws.example.com/bundle
 *   - trust_domain: azure.yawl.cloud
 *     bundle_endpoint: https://spire.azure.example.com/bundle
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeFederationConfig {

    private static final String DEFAULT_CONFIG_PATH = "/etc/spiffe/federation.yaml";
    private static final String CONFIG_ENV_VAR = "SPIFFE_FEDERATION_CONFIG";

    private final Map<String, TrustDomain> federatedDomains;
    private final String localTrustDomain;

    /**
     * Federated trust domain configuration
     */
    public static class TrustDomain {
        private final String name;
        private final String bundleEndpoint;
        private final Map<String, String> metadata;

        public TrustDomain(String name, String bundleEndpoint, Map<String, String> metadata) {
            this.name = name;
            this.bundleEndpoint = bundleEndpoint;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }

        public String getName() {
            return name;
        }

        public String getBundleEndpoint() {
            return bundleEndpoint;
        }

        public Map<String, String> getMetadata() {
            return Collections.unmodifiableMap(metadata);
        }

        public String getCloud() {
            return metadata.getOrDefault("cloud", "unknown");
        }

        public String getRegion() {
            return metadata.getOrDefault("region", "unknown");
        }
    }

    /**
     * Create federation config from default locations
     */
    public SpiffeFederationConfig() throws IOException {
        this(loadDefaultConfig());
    }

    /**
     * Create federation config from explicit path
     */
    public SpiffeFederationConfig(Path configPath) throws IOException {
        this(loadConfig(configPath));
    }

    /**
     * Create federation config from parsed data
     */
    private SpiffeFederationConfig(FederationData data) {
        this.federatedDomains = new HashMap<>();
        this.localTrustDomain = data.localTrustDomain;

        for (TrustDomain td : data.federations) {
            federatedDomains.put(td.getName(), td);
        }
    }

    /**
     * Check if a trust domain is federated
     */
    public boolean isFederated(String trustDomain) {
        return federatedDomains.containsKey(trustDomain);
    }

    /**
     * Get federated trust domain configuration
     */
    public Optional<TrustDomain> getTrustDomain(String name) {
        return Optional.ofNullable(federatedDomains.get(name));
    }

    /**
     * Get all federated trust domains
     */
    public Collection<TrustDomain> getAllTrustDomains() {
        return Collections.unmodifiableCollection(federatedDomains.values());
    }

    /**
     * Get local trust domain
     */
    public String getLocalTrustDomain() {
        return localTrustDomain;
    }

    /**
     * Check if an identity is from a federated domain
     */
    public boolean isFederatedIdentity(SpiffeWorkloadIdentity identity) {
        String trustDomain = identity.getTrustDomain();
        return isFederated(trustDomain);
    }

    /**
     * Get cloud provider for a trust domain
     */
    public Optional<String> getCloudProvider(String trustDomain) {
        return getTrustDomain(trustDomain)
            .map(TrustDomain::getCloud);
    }

    /**
     * Get all GCP trust domains
     */
    public List<TrustDomain> getGcpDomains() {
        return filterByCloud("gcp");
    }

    /**
     * Get all AWS trust domains
     */
    public List<TrustDomain> getAwsDomains() {
        return filterByCloud("aws");
    }

    /**
     * Get all Azure trust domains
     */
    public List<TrustDomain> getAzureDomains() {
        return filterByCloud("azure");
    }

    /**
     * Filter trust domains by cloud provider
     */
    private List<TrustDomain> filterByCloud(String cloud) {
        List<TrustDomain> result = new ArrayList<>();
        for (TrustDomain td : federatedDomains.values()) {
            if (cloud.equalsIgnoreCase(td.getCloud())) {
                result.add(td);
            }
        }
        return result;
    }

    /**
     * Load configuration from default location
     */
    private static FederationData loadDefaultConfig() throws IOException {
        String configPathStr = System.getenv(CONFIG_ENV_VAR);
        Path configPath = configPathStr != null
            ? Paths.get(configPathStr)
            : Paths.get(DEFAULT_CONFIG_PATH);

        if (!Files.exists(configPath)) {
            System.err.println("SPIFFE: No federation config found at " + configPath + ", using defaults");
            return createDefaultConfig();
        }

        return loadConfig(configPath);
    }

    /**
     * Load configuration from file
     */
    private static FederationData loadConfig(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("Federation config not found: " + configPath);
        }

        try (InputStream is = Files.newInputStream(configPath)) {
            return parseYamlConfig(is);
        }
    }

    /**
     * Parse YAML configuration (simplified parser without external dependencies)
     */
    private static FederationData parseYamlConfig(InputStream is) throws IOException {
        String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        FederationData data = new FederationData();
        data.federations = new ArrayList<>();

        String[] lines = content.split("\n");
        TrustDomain currentDomain = null;
        Map<String, String> currentMetadata = new HashMap<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("local_trust_domain:")) {
                data.localTrustDomain = extractYamlValue(line);
            } else if (line.startsWith("- trust_domain:")) {
                if (currentDomain != null) {
                    data.federations.add(new TrustDomain(
                        currentDomain.getName(),
                        currentDomain.getBundleEndpoint(),
                        currentMetadata
                    ));
                    currentMetadata = new HashMap<>();
                }
                String name = extractYamlValue(line);
                currentDomain = new TrustDomain(name, null, null);
            } else if (line.startsWith("bundle_endpoint:") && currentDomain != null) {
                String endpoint = extractYamlValue(line);
                currentDomain = new TrustDomain(
                    currentDomain.getName(),
                    endpoint,
                    currentMetadata
                );
            } else if (line.startsWith("cloud:")) {
                currentMetadata.put("cloud", extractYamlValue(line));
            } else if (line.startsWith("region:")) {
                currentMetadata.put("region", extractYamlValue(line));
            }
        }

        if (currentDomain != null) {
            data.federations.add(new TrustDomain(
                currentDomain.getName(),
                currentDomain.getBundleEndpoint(),
                currentMetadata
            ));
        }

        if (data.localTrustDomain == null) {
            data.localTrustDomain = "yawl.local";
        }

        return data;
    }

    /**
     * Extract value from YAML line
     */
    private static String extractYamlValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid YAML line, missing colon: " + line);
        }
        String value = line.substring(colonIndex + 1).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("YAML value is empty for line: " + line);
        }
        return value.replaceAll("^[\"']|[\"']$", "");
    }

    /**
     * Create default federation configuration
     */
    private static FederationData createDefaultConfig() {
        FederationData data = new FederationData();
        data.localTrustDomain = "yawl.local";
        data.federations = new ArrayList<>();

        Map<String, String> gcpMeta = new HashMap<>();
        gcpMeta.put("cloud", "gcp");
        gcpMeta.put("region", "us-central1");
        data.federations.add(new TrustDomain(
            "gcp.yawl.cloud",
            "https://spire-gcp.yawl.cloud/bundle",
            gcpMeta
        ));

        Map<String, String> awsMeta = new HashMap<>();
        awsMeta.put("cloud", "aws");
        awsMeta.put("region", "us-east-1");
        data.federations.add(new TrustDomain(
            "aws.yawl.cloud",
            "https://spire-aws.yawl.cloud/bundle",
            awsMeta
        ));

        Map<String, String> azureMeta = new HashMap<>();
        azureMeta.put("cloud", "azure");
        azureMeta.put("region", "eastus");
        data.federations.add(new TrustDomain(
            "azure.yawl.cloud",
            "https://spire-azure.yawl.cloud/bundle",
            azureMeta
        ));

        return data;
    }

    /**
     * Internal data structure for parsing
     */
    private static class FederationData {
        String localTrustDomain;
        List<TrustDomain> federations;
    }

    /**
     * Builder for programmatic configuration
     */
    public static class Builder {
        private final List<TrustDomain> federations = new ArrayList<>();
        private String localTrustDomain = "yawl.local";

        public Builder localTrustDomain(String domain) {
            this.localTrustDomain = domain;
            return this;
        }

        public Builder addFederation(String name, String bundleEndpoint) {
            federations.add(new TrustDomain(name, bundleEndpoint, new HashMap<>()));
            return this;
        }

        public Builder addFederation(String name, String bundleEndpoint, String cloud, String region) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("cloud", cloud);
            metadata.put("region", region);
            federations.add(new TrustDomain(name, bundleEndpoint, metadata));
            return this;
        }

        public SpiffeFederationConfig build() {
            FederationData data = new FederationData();
            data.localTrustDomain = localTrustDomain;
            data.federations = federations;
            return new SpiffeFederationConfig(data);
        }
    }
}
