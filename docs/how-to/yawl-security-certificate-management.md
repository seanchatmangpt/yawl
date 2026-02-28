# How-To: Manage Certificates and Digital Signatures

Complete guide for certificate lifecycle management in YAWL security.

## Generate Production Certificates

**Goal**: Create certificates suitable for production use.

### Steps

1. **Generate a strong key pair**
```bash
# Generate RSA 3072-bit key (stronger than 2048)
openssl genrsa -out private-key.pem 3072

# Or generate ECDSA P-256 key (recommended for new deployments)
openssl ecparam -name prime256v1 -genkey -noout -out private-key.pem
```

2. **Create certificate request (CSR)**
```bash
# Generate CSR with organization details
openssl req -new \
  -key private-key.pem \
  -out workflow.csr \
  -subj "/C=US/ST=California/L=San Francisco/O=YourOrg/CN=workflow-prod.example.com"
```

3. **Sign the certificate (self-signed for internal use)**
```bash
# Create self-signed certificate valid for 3 years
openssl x509 -req \
  -in workflow.csr \
  -signkey private-key.pem \
  -out certificate.pem \
  -days 1095 \
  -extfile /dev/stdin <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=codeSigning
EOF
```

4. **Create PKCS#12 keystore for Java**
```bash
# Combine key and certificate into PKCS#12 format
openssl pkcs12 -export \
  -in certificate.pem \
  -inkey private-key.pem \
  -out keystore.p12 \
  -name "yawl-workflow-signing" \
  -password pass:SecurePassword123
```

## Load and Manage Keystores

**Goal**: Access certificates and keys from Java keystores.

### Steps

1. **Load PKCS#12 keystore**
```java
import java.security.*;
import java.security.cert.*;

public class KeystoreManager {
    public static KeyStore loadKeystore(String keystorePath,
                                       String password)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, password.toCharArray());
        }

        return keyStore;
    }

    public static void listKeyStoreEntries(KeyStore keyStore)
            throws Exception {
        Enumeration<String> aliases = keyStore.aliases();

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();

            if (keyStore.isCertificateEntry(alias)) {
                X509Certificate cert = (X509Certificate)
                    keyStore.getCertificate(alias);

                System.out.println("Alias: " + alias);
                System.out.println("  Subject: " + cert.getSubjectDN());
                System.out.println("  Issuer: " + cert.getIssuerDN());
                System.out.println("  Valid from: " + cert.getNotBefore());
                System.out.println("  Valid to: " + cert.getNotAfter());
                System.out.println("  Serial: " + cert.getSerialNumber());
            }
        }
    }
}
```

2. **Extract certificate and key**
```java
public static PrivateKey getPrivateKey(KeyStore keyStore,
                                      String alias,
                                      String password)
        throws Exception {
    return (PrivateKey) keyStore.getKey(
        alias,
        password.toCharArray()
    );
}

public static X509Certificate getCertificate(KeyStore keyStore,
                                            String alias)
        throws Exception {
    return (X509Certificate) keyStore.getCertificate(alias);
}
```

## Rotate Certificates

**Goal**: Replace expiring or compromised certificates.

### Steps

1. **Generate new certificate**
```bash
# Follow the steps in "Generate Production Certificates"
# Generate new key pair and certificate
openssl genrsa -out new-private-key.pem 3072
openssl req -new -key new-private-key.pem -out new.csr
openssl x509 -req -in new.csr ...
```

2. **Update keystore with new certificate**
```java
public void addNewCertificateToKeystore(KeyStore keyStore,
                                       String certPath,
                                       String alias)
        throws Exception {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");

    try (FileInputStream fis = new FileInputStream(certPath)) {
        X509Certificate cert = (X509Certificate)
            cf.generateCertificate(fis);

        keyStore.setCertificateEntry(alias, cert);
        System.out.println("Certificate added: " + alias);
    }
}
```

3. **Mark old certificate as revoked**
```java
// Remove old entry
keyStore.deleteEntry("old-certificate");

// Or keep with different alias for backward compatibility
KeyStore oldKeyStore = KeyStore.getInstance("PKCS12");
oldKeyStore.load(...);

X509Certificate oldCert = (X509Certificate)
    oldKeyStore.getCertificate("old-alias");

keyStore.setCertificateEntry("old-certificate-retired", oldCert);

System.out.println("Old certificate archived");
```

4. **Save updated keystore**
```java
public void saveKeystore(KeyStore keyStore,
                        String keystorePath,
                        String password)
        throws Exception {
    try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
        keyStore.store(fos, password.toCharArray());
        System.out.println("Keystore saved: " + keystorePath);
    }
}
```

## Validate Certificate Chains

**Goal**: Verify certificate signatures and expiration.

### Steps

1. **Check certificate validity**
```java
public static boolean isCertificateValid(X509Certificate cert) {
    try {
        // Check validity period
        cert.checkValidity();
        System.out.println("✓ Certificate is within validity period");

        // Check if self-signed or verify with issuer
        try {
            cert.verify(cert.getPublicKey());
            System.out.println("✓ Certificate signature is valid");
            return true;

        } catch (SignatureException e) {
            System.out.println("✗ Invalid signature");
            return false;
        }

    } catch (CertificateExpiredException e) {
        System.out.println("✗ Certificate has expired: " + e.getMessage());
        return false;

    } catch (CertificateNotYetValidException e) {
        System.out.println("✗ Certificate is not yet valid");
        return false;
    }
}
```

2. **Verify certificate chain**
```java
public static boolean verifyCertificateChain(X509Certificate[] chain)
        throws Exception {
    for (int i = 0; i < chain.length - 1; i++) {
        X509Certificate cert = chain[i];
        X509Certificate issuer = chain[i + 1];

        try {
            cert.verify(issuer.getPublicKey());
            System.out.println("✓ " + cert.getSubjectDN() +
                             " signed by " + issuer.getSubjectDN());

        } catch (SignatureException e) {
            System.out.println("✗ Chain verification failed at position " + i);
            return false;
        }
    }

    // Verify root is self-signed
    X509Certificate root = chain[chain.length - 1];
    try {
        root.verify(root.getPublicKey());
        System.out.println("✓ Root certificate is self-signed");
        return true;

    } catch (SignatureException e) {
        System.out.println("✗ Root certificate is not self-signed");
        return false;
    }
}
```

## Check Certificate Revocation

**Goal**: Detect and handle revoked certificates.

### Steps

1. **Check against CRL (Certificate Revocation List)**
```java
public static boolean isCertificateRevoked(X509Certificate cert,
                                          String crlPath)
        throws Exception {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");

    try (FileInputStream fis = new FileInputStream(crlPath)) {
        CRL crl = cf.generateCRL(fis);

        if (crl instanceof X509CRL) {
            X509CRL x509crl = (X509CRL) crl;
            X509CRLEntry entry = x509crl.getRevokedCertificate(
                cert.getSerialNumber()
            );

            if (entry != null) {
                System.out.println("✗ Certificate is revoked");
                System.out.println("  Revoked on: " + entry.getRevocationDate());
                return true;
            }
        }
    }

    System.out.println("✓ Certificate is not revoked");
    return false;
}
```

2. **Check with OCSP (Online Certificate Status Protocol)**
```java
public static boolean checkOCSP(X509Certificate cert,
                               X509Certificate issuer,
                               String ocspUrl)
        throws Exception {
    // Note: Full OCSP implementation is complex
    // Use BouncyCastle OCSPReqGenerator and OCSPRespGenerator

    System.out.println("Checking certificate status via OCSP...");
    System.out.println("URL: " + ocspUrl);

    // Implementation requires OCSPReq and OCSPResp handling
    // See BouncyCastle documentation for full implementation
    return true;  // Placeholder
}
```

## Export Certificates for Distribution

**Goal**: Share public certificates with others for signature verification.

### Steps

1. **Export public certificate only**
```java
public static void exportPublicCertificate(KeyStore keyStore,
                                          String alias,
                                          String outputPath)
        throws Exception {
    X509Certificate cert = (X509Certificate)
        keyStore.getCertificate(alias);

    // Export in PEM format
    String pemHeader = "-----BEGIN CERTIFICATE-----";
    String pemFooter = "-----END CERTIFICATE-----";

    String encoded = Base64.getMimeEncoder()
        .encodeToString(cert.getEncoded());

    String pem = pemHeader + "\n" + encoded + "\n" + pemFooter;

    Files.write(Paths.get(outputPath), pem.getBytes());
    System.out.println("Certificate exported: " + outputPath);
}
```

2. **Create certificate bundle**
```bash
# Combine multiple certificates into single PEM file
cat certificate1.pem certificate2.pem certificate3.pem > bundle.pem

# Or export from keystore
keytool -export -alias myalias -file cert.pem -keystore keystore.p12 \
  -storepass password -rfc
```

## Troubleshooting Certificate Issues

**Certificate file not found:**
```java
Path certPath = Paths.get(certificatePath);
if (!Files.exists(certPath)) {
    throw new FileNotFoundException("Certificate not found: " + certPath);
}
```

**Wrong password for keystore:**
```java
try {
    keyStore.load(fis, password.toCharArray());
} catch (IOException e) {
    if (e.getMessage().contains("password")) {
        System.err.println("Incorrect keystore password");
    }
}
```

**Certificate expired:**
```java
// Check expiration
LocalDateTime now = LocalDateTime.now();
LocalDateTime expiry = cert.getNotAfter().toInstant()
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime();

if (now.isAfter(expiry)) {
    System.out.println("Certificate expires in: " +
                      ChronoUnit.DAYS.between(now, expiry) + " days");
}
```

---

For more information, see:
- [Security Reference](../reference/security-policy.md)
- [Cryptographic Best Practices](../reference/security-overview.md)
- [Certificate Generation Tutorial](../tutorials/yawl-security-getting-started.md)
