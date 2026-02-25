/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.pi.predictive;

import org.yawlfoundation.yawl.pi.PIException;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxTensor;

import java.nio.FloatBuffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe registry for ONNX predictive models.
 *
 * <p>Loads, caches, and provides inference on ONNX Runtime models.
 * On construction, automatically scans the model directory for .onnx files.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class PredictiveModelRegistry implements AutoCloseable {

    private final OrtEnvironment ortEnvironment;
    private final ConcurrentHashMap<String, OrtSession> sessions;
    private final ConcurrentHashMap<String, OnnxModelHandle> handles;
    private final ReentrantLock registryLock = new ReentrantLock();

    /**
     * Construct and auto-load models from directory.
     *
     * @param modelDirectory Directory to scan for .onnx files
     * @throws PIException If ORT environment initialization fails
     */
    public PredictiveModelRegistry(Path modelDirectory) throws PIException {
        this.ortEnvironment = OrtEnvironment.getEnvironment();
        this.sessions = new ConcurrentHashMap<>();
        this.handles = new ConcurrentHashMap<>();

        autoloadModels(modelDirectory);
    }

    private void autoloadModels(Path modelDirectory) throws PIException {
        if (!Files.exists(modelDirectory)) {
            return;
        }

        try {
            List<Path> onnxFiles = new ArrayList<>();
            Files.list(modelDirectory)
                .filter(p -> p.toString().endsWith(".onnx"))
                .forEach(onnxFiles::add);

            for (Path modelPath : onnxFiles) {
                String taskName = modelPath.getFileName().toString()
                    .replaceAll("\\.onnx$", "");
                register(taskName, modelPath);
            }
        } catch (IOException e) {
            throw new PIException("Failed to scan model directory", "predictive", e);
        }
    }

    /**
     * Register a model by task name and path.
     *
     * @param taskName Identifier for this model's task
     * @param modelPath Path to .onnx model file
     * @throws PIException If model loading or parsing fails
     */
    public void register(String taskName, Path modelPath) throws PIException {
        registryLock.lock();
        try {
            if (sessions.containsKey(taskName)) {
                throw new PIException("Model already registered: " + taskName, "predictive");
            }

            try {
                OrtSession session = ortEnvironment.createSession(modelPath.toString());
                String hash = computeFileHash(modelPath);
                long size = Files.size(modelPath);

                sessions.put(taskName, session);
                handles.put(taskName, new OnnxModelHandle(
                    taskName,
                    modelPath,
                    hash,
                    size,
                    Instant.now()
                ));
            } catch (OrtException | IOException e) {
                throw new PIException(
                    "Failed to load model: " + modelPath,
                    "predictive",
                    e);
            }
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * Check if a model is available for inference.
     *
     * @param taskName Task identifier
     * @return true if model is loaded and ready
     */
    public boolean isAvailable(String taskName) {
        return sessions.containsKey(taskName);
    }

    /**
     * Run inference on a model.
     *
     * @param taskName Task identifier
     * @param features Input feature vector
     * @return Output tensor values
     * @throws PIException If model not found or inference fails
     */
    public float[] infer(String taskName, float[] features) throws PIException {
        OrtSession session = sessions.get(taskName);
        if (session == null) {
            throw new PIException("Model not found: " + taskName, "predictive");
        }

        try {
            long[] shape = {1, features.length};
            FloatBuffer buf = FloatBuffer.wrap(features);
            OnnxTensor input = OnnxTensor.createTensor(ortEnvironment, buf, shape);

            var results = session.run(java.util.Map.of("input", input));
            Object output = results.get(0).getValue();

            if (output instanceof float[][]) {
                return ((float[][]) output)[0];
            } else if (output instanceof float[]) {
                return (float[]) output;
            } else {
                throw new PIException(
                    "Unexpected output type: " + output.getClass().getName(),
                    "predictive");
            }
        } catch (OrtException e) {
            throw new PIException("Inference failed on model: " + taskName, "predictive", e);
        }
    }

    /**
     * Get metadata about a loaded model.
     *
     * @param taskName Task identifier
     * @return Model metadata if available
     */
    public Optional<OnnxModelHandle> getHandle(String taskName) {
        return Optional.ofNullable(handles.get(taskName));
    }

    /**
     * Close all model sessions and release resources.
     *
     * @throws Exception If session cleanup fails
     */
    @Override
    public void close() throws Exception {
        registryLock.lock();
        try {
            for (OrtSession session : sessions.values()) {
                session.close();
            }
            sessions.clear();
            handles.clear();
        } finally {
            registryLock.unlock();
        }
    }

    private String computeFileHash(Path path) throws PIException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new PIException("Failed to compute file hash", "predictive", e);
        }
    }
}
