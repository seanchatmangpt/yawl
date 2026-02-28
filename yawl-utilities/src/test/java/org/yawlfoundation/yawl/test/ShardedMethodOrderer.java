package org.yawlfoundation.yawl.test;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * JUnit 5 MethodOrderer that enables test sharding for CI parallelization.
 *
 * Usage: Set system properties test.shard.index and test.shard.total
 * Example: -Dtest.shard.index=0 -Dtest.shard.total=4
 */
public class ShardedMethodOrderer implements MethodOrderer {

    @Override
    public void orderMethods(MethodOrdererContext context) {
        int shardIndex = Integer.getInteger("test.shard.index", 0);
        int totalShards = Integer.getInteger("test.shard.total", 1);

        if (totalShards <= 1) {
            return; // No sharding, run all tests
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        // Hash method class+name to deterministically assign to shards
        context.getMethodDescriptors().forEach(descriptor -> {
            String identifier = descriptor.getTestClass().getName() + "#" +
                descriptor.getMethod().getName();
            byte[] hash = md.digest(identifier.getBytes(StandardCharsets.UTF_8));
            BigInteger hashInt = new BigInteger(1, hash);
            int assignedShard = hashInt.mod(BigInteger.valueOf(totalShards)).intValue();

            if (assignedShard != shardIndex) {
                descriptor.disable("Assigned to shard " + assignedShard +
                    " (current shard: " + shardIndex + ")");
            }
        });
    }
}