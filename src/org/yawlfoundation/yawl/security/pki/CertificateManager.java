/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations who
 * are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.security.pki;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Manages X.509 certificates, keystores, and certificate validation.
 * Supports JKS and PKCS12 keystore formats.
 */
public class CertificateManager {
    private final KeyStore keyStore;
    private final String keystorePath;
    private final String keystorePassword;
    private final String keystoreType;

    /**
     * Creates a CertificateManager with the specified keystore.
     *
     * @param keystorePath     Path to the keystore file (JKS or PKCS12)
     * @param keystorePassword Password for the keystore
     * @param keystoreType     Type of keystore ("JKS" or "PKCS12")
     * @throws PkiException if certificate cannot be loaded or is invalid
     */
    public CertificateManager(String keystorePath, String keystorePassword,
                             String keystoreType) throws PkiException {
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.keystoreType = keystoreType;

        try {
            this.keyStore = KeyStore.getInstance(keystoreType);
            loadKeystore();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | java.security.cert.CertificateException e) {
            throw new PkiException("Failed to initialize keystore: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the keystore from disk.
     */
    private void loadKeystore() throws IOException, CertificateException,
            NoSuchAlgorithmException, KeyStoreException {
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }
    }

    /**
     * Retrieves a certificate by alias.
     *
     * @param alias The certificate alias
     * @return The X.509 certificate, or null if not found
     * @throws CertificateException if the certificate cannot be retrieved
     */
    public X509Certificate getCertificate(String alias) throws PkiException {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (!(cert instanceof X509Certificate)) {
                throw new PkiException("Certificate is not X.509");
            }
            return (X509Certificate) cert;
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to retrieve certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all certificate aliases in the keystore.
     *
     * @return List of certificate aliases
     * @throws CertificateException if aliases cannot be retrieved
     */
    public List<String> listCertificateAliases() throws PkiException {
        List<String> aliases = new ArrayList<>();
        try {
            Enumeration<String> enumeration = keyStore.aliases();
            while (enumeration.hasMoreElements()) {
                String alias = enumeration.nextElement();
                if (keyStore.isCertificateEntry(alias)) {
                    aliases.add(alias);
                }
            }
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to list aliases: " + e.getMessage(), e);
        }
        return aliases;
    }

    /**
     * Validates a certificate's expiration and basic chain.
     *
     * @param certificate The certificate to validate
     * @throws CertificateException if validation fails
     */
    public void validateCertificate(X509Certificate certificate) throws PkiException {
        try {
            certificate.checkValidity();
        } catch (java.security.cert.CertificateException e) {
            throw new PkiException("Certificate validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the certificate chain for a given alias.
     *
     * @param alias The certificate alias
     * @return Array of certificates in the chain
     * @throws CertificateException if the chain cannot be retrieved
     */
    public X509Certificate[] getCertificateChain(String alias) throws PkiException {
        try {
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null) {
                throw new PkiException("No certificate chain found for alias: " + alias);
            }

            X509Certificate[] x509Chain = new X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (!(chain[i] instanceof X509Certificate)) {
                    throw new PkiException("Chain contains non-X.509 certificate");
                }
                x509Chain[i] = (X509Certificate) chain[i];
            }
            return x509Chain;
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to get certificate chain: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the private key for a given alias.
     *
     * @param alias The certificate alias
     * @return The private key
     * @throws CertificateException if the key cannot be retrieved
     */
    public java.security.PrivateKey getPrivateKey(String alias) throws PkiException {
        try {
            return (java.security.PrivateKey) keyStore.getKey(
                    alias,
                    keystorePassword.toCharArray()
            );
        } catch (KeyStoreException | NoSuchAlgorithmException | java.security.UnrecoverableKeyException e) {
            throw new PkiException("Failed to retrieve private key: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a certificate exists in the keystore.
     *
     * @param alias The certificate alias
     * @return true if the certificate exists, false otherwise
     * @throws CertificateException if the keystore cannot be accessed
     */
    public boolean certificateExists(String alias) throws PkiException {
        try {
            return keyStore.containsAlias(alias);
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to check certificate existence: " + e.getMessage(), e);
        }
    }
}
