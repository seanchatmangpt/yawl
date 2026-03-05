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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;

/**
 * Provides simple one-way encryption for passwords
 *
 * @author Michael Adams
 * Date: 9/06/2008
 */

public class PasswordEncryptor {

    private PasswordEncryptor() { }

    /**
     * Encrypts a password using SHA-1.
     *
     * @deprecated SHA-1 is cryptographically broken for password storage. Migrate to
     *             {@code Argon2PasswordEncryptor} which uses Argon2id. This method is
     *             retained only for backward-compatibility with existing persisted hashes
     *             during a migration window. New code must not call this method.
     * @param text the plaintext password
     * @return the SHA-1 Base64-encoded hash
     * @throws NoSuchAlgorithmException if SHA is unavailable (should never happen on JVM)
     */
    @Deprecated
    public static synchronized String encrypt(String text)
                        throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA");
        md.update(text.getBytes(StandardCharsets.UTF_8));
        byte[] raw = md.digest();
        return new Base64(-1).encodeToString(raw);            // -1 means no line breaks
    }

    /**
     * Encrypts a password using SHA-1 with a non-null fallback on error.
     *
     * @deprecated Silent fallback is a security anti-pattern. This method swallows
     *             {@link NoSuchAlgorithmException} and returns a caller-supplied default,
     *             which can mask misconfiguration. Additionally SHA-1 is cryptographically
     *             broken for password storage. Migrate to {@code Argon2PasswordEncryptor}.
     *             This method is retained only for backward-compatibility during migration.
     * @param text the plaintext password
     * @param defText the value to return if encryption fails (must not be a real password)
     * @return the SHA-1 Base64-encoded hash, or {@code defText} on failure
     */
    @Deprecated
    public static synchronized String encrypt(String text, String defText) {
        if (defText == null) defText = text;
        try {
            return encrypt(text);
        }
        catch (Exception e) {
            throw new UnsupportedOperationException(
                "SHA-1 password encryption failed. SHA-1 is deprecated for password storage. " +
                "Migrate to Argon2PasswordEncryptor. Original error: " + e.getMessage(), e);
        }
    }

}
