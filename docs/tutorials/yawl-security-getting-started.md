# Getting Started with YAWL Security

Learn how to secure YAWL workflow specifications with cryptographic signing and certificate management.

## What You'll Learn

By the end of this tutorial, you'll understand:
- Public Key Infrastructure (PKI) concepts in YAWL
- How to generate X.509 certificates for signing
- How to digitally sign workflow specifications
- How to verify signed specifications
- How to manage certificate revocation lists

## Prerequisites

- Java 25 or higher
- Basic understanding of cryptography and X.509 certificates
- 20-30 minutes

## Step 1: Understanding YAWL Security Architecture

YAWL Security provides cryptographic assurance that workflow specifications are authentic and have not been tampered with. It uses:

- **X.509 Certificates**: Bind workflow identities to public keys
- **Digital Signatures**: Prove specification authenticity and integrity
- **Certificate Revocation Lists (CRL)**: Invalidate compromised certificates
- **OCSP (Online Certificate Status Protocol)**: Real-time revocation checking

```
┌─────────────────────────────────────────┐
│   YAWL Workflow Specification (XML)     │
└─────────────────────┬───────────────────┘
                      │
              ┌───────┴────────┐
              ↓                ↓
    ┌─────────────────┐  ┌──────────────┐
    │  Private Key    │  │   Sign       │
    │  (kept secret)  │  │  Algorithm   │
    └─────────────────┘  └──────────────┘
              │                │
              └────────┬───────┘
                       ↓
            ┌──────────────────────┐
            │  Digital Signature   │
            │  (attached to spec)  │
            └──────────────────────┘
```

## Step 2: Generate a Self-Signed Certificate

Create a certificate for signing workflow specifications:

```java
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

public class CertificateGenerator {

    static {
        // Add Bouncycastle as a security provider
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void generateCertificate(String outputPath) throws Exception {
        // 1. Generate RSA key pair (2048 bits for security)
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();

        // 2. Build certificate information
        X500Principal principal = new X500Principal(
            "CN=workflow-authority,O=MyOrg,C=US"
        );

        // 3. Create a self-signed certificate (valid for 365 days)
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider("BC")
            .build(keyPair.getPrivate());

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            principal,                          // issuer
            BigInteger.valueOf(System.currentTimeMillis()), // serial number
            new Date(),                         // notBefore
            new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000), // notAfter
            principal,                          // subject
            keyPair.getPublic()                // public key
        );

        X509Certificate certificate = new JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(builder.build(signer));

        // 4. Save certificate and key to file
        saveCertificateAndKey(outputPath, certificate, keyPair.getPrivate());

        System.out.println("Certificate generated: " + certificate.getSubjectDN());
        System.out.println("Valid from: " + certificate.getNotBefore());
        System.out.println("Valid to: " + certificate.getNotAfter());
    }

    private static void saveCertificateAndKey(String path,
                                             X509Certificate cert,
                                             PrivateKey privateKey) throws Exception {
        // Implementation to save PKCS#12 keystore
        // See JcaX509CertificateConverter and KeyStore documentation
    }
}
```

## Step 3: Sign a Workflow Specification

Sign a YAWL specification XML document:

```java
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class WorkflowSpecificationSigner {

    public static void signSpecification(String specPath,
                                        String keystorePath,
                                        String keystorePassword,
                                        String outputPath) throws Exception {
        // 1. Load the certificate and private key from keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }

        // 2. Get the private key and certificate
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(
            alias,
            keystorePassword.toCharArray()
        );
        X509Certificate certificate = (X509Certificate)
            keyStore.getCertificate(alias);

        // 3. Read the specification file
        String specContent = Files.readString(Paths.get(specPath));

        // 4. Create digital signature
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(specContent.getBytes(StandardCharsets.UTF_8));
        byte[] digitalSignature = signature.sign();

        // 5. Encode signature in Base64
        String encodedSignature = Base64.getEncoder()
            .encodeToString(digitalSignature);

        // 6. Create signed document (spec with attached signature)
        String signedDocument = createSignedDocument(
            specContent,
            encodedSignature,
            certificate
        );

        // 7. Save signed specification
        Files.write(Paths.get(outputPath), signedDocument.getBytes());
        System.out.println("Specification signed and saved: " + outputPath);
    }

    private static String createSignedDocument(String spec,
                                              String signature,
                                              X509Certificate cert) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<signedSpecification>\n" +
               "  <specification>\n" + spec + "\n  </specification>\n" +
               "  <signature algorithm=\"SHA256withRSA\">\n" +
               "    " + signature + "\n" +
               "  </signature>\n" +
               "  <certificate>\n" +
               "    " + Base64.getEncoder().encodeToString(
                      cert.getEncoded()) + "\n" +
               "  </certificate>\n" +
               "</signedSpecification>";
    }
}
```

## Step 4: Verify a Signed Specification

Verify that a specification hasn't been tampered with:

```java
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class WorkflowSpecificationVerifier {

    public static boolean verifySignedSpecification(String signedSpecPath)
            throws Exception {
        // 1. Parse the signed document
        Document doc = parseXML(signedSpecPath);

        // 2. Extract components
        String originalSpec = doc.getElementsByTagName("specification")
            .item(0).getTextContent();
        String encodedSignature = doc.getElementsByTagName("signature")
            .item(0).getTextContent().trim();
        String encodedCert = doc.getElementsByTagName("certificate")
            .item(0).getTextContent().trim();

        // 3. Reconstruct the certificate
        byte[] certBytes = Base64.getDecoder().decode(encodedCert);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate)
            cf.generateCertificate(
                new ByteArrayInputStream(certBytes)
            );

        System.out.println("Signature verified by: " + certificate.getSubjectDN());

        // 4. Verify the signature
        byte[] signatureBytes = Base64.getDecoder()
            .decode(encodedSignature);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(originalSpec.getBytes(StandardCharsets.UTF_8));

        boolean isValid = verifier.verify(signatureBytes);

        if (isValid) {
            System.out.println("✓ Signature is valid - specification is authentic");
            return true;
        } else {
            System.out.println("✗ Signature is invalid - specification may be tampered");
            return false;
        }
    }

    private static Document parseXML(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new File(path));
    }
}
```

## Step 5: Certificate Validation and Revocation Checking

Validate certificates and check revocation status:

```java
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import java.io.FileReader;
import java.security.cert.*;

public class CertificateValidator {

    public static boolean validateCertificate(X509Certificate cert)
            throws CertificateException {
        // 1. Check certificate validity period
        try {
            cert.checkValidity();
            System.out.println("✓ Certificate is within validity period");
        } catch (CertificateExpiredException e) {
            System.out.println("✗ Certificate has expired");
            return false;
        } catch (CertificateNotYetValidException e) {
            System.out.println("✗ Certificate is not yet valid");
            return false;
        }

        // 2. Check certificate signature (for self-signed: verify self)
        try {
            cert.verify(cert.getPublicKey());
            System.out.println("✓ Certificate signature is valid");
        } catch (SignatureException e) {
            System.out.println("✗ Certificate signature is invalid");
            return false;
        }

        // 3. Check key usage
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null && keyUsage.length > 0) {
            boolean canSign = keyUsage[0];  // digitalSignature
            if (!canSign) {
                System.out.println("✗ Certificate cannot be used for signing");
                return false;
            }
        }

        return true;
    }

    public static void checkCertificateRevocation(X509Certificate cert,
                                                  String crlPath)
            throws Exception {
        // Parse CRL file
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        try (FileInputStream fis = new FileInputStream(crlPath)) {
            CRL crl = cf.generateCRL(fis);

            if (crl instanceof X509CRL) {
                X509CRL x509crl = (X509CRL) crl;

                // Check if certificate is revoked
                X509CRLEntry revokedCert = x509crl.getRevokedCertificate(
                    cert.getSerialNumber()
                );

                if (revokedCert != null) {
                    System.out.println("✗ Certificate has been revoked");
                    System.out.println("  Revocation date: " +
                                     revokedCert.getRevocationDate());
                } else {
                    System.out.println("✓ Certificate is not revoked");
                }
            }
        }
    }
}
```

## Key Takeaways

1. **X.509 Certificates** bind workflow identities to cryptographic keys
2. **Digital Signatures** prove specification authenticity and integrity
3. **Key Pairs** (public/private) enable asymmetric cryptography
4. **Certificate Validation** checks expiration and signature correctness
5. **Revocation Lists** track compromised certificates

## Common Patterns

### Complete Signing Workflow

```java
public void signAndVerifyWorkflow() throws Exception {
    String specPath = "approval-workflow.yawl";
    String keystorePath = "workflow-keys.p12";
    String keystorePassword = "secure-password";

    // Step 1: Sign the specification
    WorkflowSpecificationSigner.signSpecification(
        specPath,
        keystorePath,
        keystorePassword,
        "approval-workflow-signed.yawl"
    );

    // Step 2: Verify the signature
    boolean isValid = WorkflowSpecificationVerifier
        .verifySignedSpecification("approval-workflow-signed.yawl");

    if (isValid) {
        System.out.println("Ready to deploy specification");
        // deployWorkflow("approval-workflow-signed.yawl");
    } else {
        System.out.println("Specification verification failed");
    }
}
```

### Certificate Chain Validation

```java
public boolean validateCertificateChain(X509Certificate[] chain)
        throws Exception {
    for (int i = 0; i < chain.length; i++) {
        X509Certificate cert = chain[i];

        System.out.println("Validating certificate " + (i + 1) + "...");

        if (!CertificateValidator.validateCertificate(cert)) {
            return false;
        }

        // For non-root certs, verify signature with issuer's public key
        if (i < chain.length - 1) {
            X509Certificate issuer = chain[i + 1];
            try {
                cert.verify(issuer.getPublicKey());
                System.out.println("✓ Certificate signed by issuer");
            } catch (SignatureException e) {
                System.out.println("✗ Invalid issuer signature");
                return false;
            }
        }
    }

    return true;
}
```

## Troubleshooting

**"Invalid key size":**
- Ensure RSA key is at least 2048 bits
- Use `KeyPairGenerator.getInstance("RSA"); keyGen.initialize(2048);`

**"Certificate not found in keystore":**
- Verify keystore password is correct
- Check that certificate was stored under expected alias
- Use `keytool -list -v -keystore file.p12` to inspect

**"Signature verification failed":**
- Ensure same key pair used for signing and verification
- Check that specification wasn't modified after signing
- Verify certificate matches signer's certificate

**"Certificate expired":**
- Generate new certificate with longer validity period
- Update all signed specifications with new certificate
- Consider certificate rotation strategy

## Next Steps

- Learn [Authentication Configuration](../how-to/configure-spiffe.md) for zero-trust identity
- Explore [Cryptographic Best Practices](../reference/security-policy.md)
- Set up [Certificate Deployment](../how-to/deployment/production.md)
- Implement [SPIFFE/SVID Integration](../how-to/configure-spiffe.md)

---

**Ready to secure your workflows?** Continue with [Security Testing](../how-to/security/testing.md).
