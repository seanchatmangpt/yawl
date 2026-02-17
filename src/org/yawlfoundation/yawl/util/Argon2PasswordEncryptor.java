/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.util;

/**
 * Password encryptor using Argon2id, the current industry standard for password
 * hashing (OWASP Password Storage Cheat Sheet, 2024). Replaces the deprecated
 * SHA-1 implementation in {@link PasswordEncryptor}.
 *
 * <p>Argon2id parameters follow OWASP minimum recommendations:
 * <ul>
 *   <li>Memory: 19 MiB (19456 KiB)</li>
 *   <li>Iterations: 2</li>
 *   <li>Parallelism: 1</li>
 *   <li>Output length: 32 bytes</li>
 *   <li>Salt length: 16 bytes (generated via {@code SecureRandom})</li>
 * </ul>
 *
 * <p>The encoded output format is the standard Argon2 PHC string:
 * {@code $argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>}
 *
 * <p>This class requires the {@code de.mkammerer:argon2-jvm} library at runtime.
 * If the library is absent, {@link UnsupportedOperationException} is thrown with a
 * clear message directing the operator to the vault integration runbook.
 *
 * @author YAWL Foundation - Security Hardening
 * @since YAWL 5.3
 */
public final class Argon2PasswordEncryptor {

    /** Argon2id memory cost in KiB (19 MiB as recommended by OWASP). */
    private static final int MEMORY_COST_KIB = 19456;

    /** Argon2id time cost (iterations). */
    private static final int TIME_COST = 2;

    /** Argon2id parallelism factor. */
    private static final int PARALLELISM = 1;

    /** Hash output length in bytes. */
    private static final int HASH_LENGTH = 32;

    /** Salt length in bytes. */
    private static final int SALT_LENGTH = 16;

    private Argon2PasswordEncryptor() { }

    /**
     * Hashes a plaintext password using Argon2id.
     *
     * <p>Each call generates a fresh random salt and returns a self-contained PHC
     * string that includes algorithm, parameters, salt, and hash. Store this string
     * directly; do not strip or transform it.
     *
     * @param plaintext the password to hash; must not be null or empty
     * @return the Argon2id PHC string, safe for storage in the credential store
     * @throws IllegalArgumentException if {@code plaintext} is null or empty
     * @throws UnsupportedOperationException if the {@code argon2-jvm} library is not
     *         on the classpath - deploy the library or use the vault integration described
     *         in SECURITY.md
     */
    public static String hash(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException(
                "Password plaintext must not be null or empty.");
        }
        try {
            Class<?> factoryClass = Class.forName("de.mkammerer.argon2.Argon2Factory");
            Object argon2 = factoryClass
                .getMethod("createAdvanced", factoryClass.getField("Argon2Types").getType())
                .invoke(null, resolveArgon2id(factoryClass));
            return (String) argon2.getClass()
                .getMethod("hash", int.class, int.class, int.class,
                           char[].class, int.class, int.class)
                .invoke(argon2, TIME_COST, MEMORY_COST_KIB, PARALLELISM,
                        plaintext.toCharArray(), SALT_LENGTH, HASH_LENGTH);
        }
        catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                "Argon2 password hashing requires 'de.mkammerer:argon2-jvm' on the classpath. " +
                "Add the dependency to pom.xml or deploy via the vault integration described in " +
                "SECURITY.md. SHA-1 (PasswordEncryptor) must not be used for new credentials.", e);
        }
        catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException(
                "Failed to invoke argon2-jvm API. Verify the library version is compatible. " +
                "See SECURITY.md for supported versions.", e);
        }
    }

    /**
     * Verifies a plaintext password against a previously computed Argon2id PHC string.
     *
     * @param phcString the stored Argon2id PHC string produced by {@link #hash(String)}
     * @param plaintext the candidate plaintext password to verify
     * @return {@code true} if the plaintext matches the stored hash; {@code false} otherwise
     * @throws IllegalArgumentException if either argument is null or empty
     * @throws UnsupportedOperationException if the {@code argon2-jvm} library is absent
     */
    public static boolean verify(String phcString, String plaintext) {
        if (phcString == null || phcString.isEmpty()) {
            throw new IllegalArgumentException("PHC string must not be null or empty.");
        }
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("Password plaintext must not be null or empty.");
        }
        try {
            Class<?> factoryClass = Class.forName("de.mkammerer.argon2.Argon2Factory");
            Object argon2 = factoryClass
                .getMethod("createAdvanced", factoryClass.getField("Argon2Types").getType())
                .invoke(null, resolveArgon2id(factoryClass));
            return (Boolean) argon2.getClass()
                .getMethod("verify", String.class, char[].class)
                .invoke(argon2, phcString, plaintext.toCharArray());
        }
        catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                "Argon2 verification requires 'de.mkammerer:argon2-jvm' on the classpath. " +
                "See SECURITY.md.", e);
        }
        catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException(
                "Failed to invoke argon2-jvm verify API. See SECURITY.md.", e);
        }
    }

    private static Object resolveArgon2id(Class<?> factoryClass)
            throws ReflectiveOperationException {
        Class<?> typesClass = Class.forName("de.mkammerer.argon2.Argon2Factory$Argon2Types");
        return Enum.valueOf((Class<Enum>) typesClass, "ARGON2id");
    }

}
