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

package org.yawlfoundation.yawl.documentStore;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import org.yawlfoundation.yawl.engine.interfce.Interface_Client;
import org.yawlfoundation.yawl.util.PasswordEncryptor;

/**
 * An client-side interface to communicate with the YAWL Document Store
 * @author Michael Adams
 * @date 21/11/11
 * Migrated to java.net.http.HttpClient (2026-02-16)
 */
public class DocumentStoreClient extends Interface_Client {

    /**
     * Shared HTTP client using virtual threads for network I/O.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    // the uri of the YAWL doc store. A default would be:
    // "http://localhost:8080/documentStore/"
    protected String _storeURI;

    /**
     * Constructs a new, empty DocumentStoreClient object
     */
    public DocumentStoreClient() { }


    /**
     * Constructs a new DocumentStoreClient object
     * @param uri the uri of the YAWL document store
     */
    public DocumentStoreClient(String uri) {
        _storeURI = uri ;
    }


    /**
     * Sets the document store URI for this client
     * @param uri the uri of the YAWL document store
     */
    public void setURI(String uri)  { _storeURI = uri; }


    /**
     * Connects an external entity to the document store
     * @param userID the userid
     * @param password the corresponding password
     * @return a sessionHandle if successful, or a failure message if not
     * @throws IOException if the service can't be reached
     */
    public String connect(String userID, String password) throws IOException {
        byte[] bytes = toByteArray("connect", "", userID,
                PasswordEncryptor.encrypt(password, null));
        String result = executePost(bytes).toString(StandardCharsets.UTF_8);
        return (successful(result)) ? stripOuterElement(result) : result;
    }


    /**
     * Check that a session handle is active
     * @param handle the session handle to check
     * @return "true" if the handle is valid and active, "false" if otherwise
     * @throws IOException if the service can't be reached
     */
    public boolean checkConnection(String handle) throws IOException {
        String result = executePost(toByteArray("checkConnection", handle)).toString(StandardCharsets.UTF_8);
        return successful(result) && stripOuterElement(result).equalsIgnoreCase("true");
    }


    /**
     * Disconnects an external entity from the document store
     * @param handle the sessionHandle to disconnect
     * @throws IOException if the service can't be reached
     */
    public void disconnect(String handle) throws IOException {
        executePost(toByteArray("disconnect", handle));
    }


    /**
     * Stores a document in the Document Store
     * @param doc the YDocument to store. If the YDocument's id matches an existing
     *            document in the Store, this 'put' is treated as an update rather
     *            that a new document insertion
     * @param handle a valid session handle
     * @return the document id (as a String) if successful, otherwise an error message
     * @throws IOException if the service can't be reached
     */
    public String putDocument(YDocument doc, String handle) throws IOException {
        return executePost(toByteArray(doc, "put", handle)).toString(StandardCharsets.UTF_8);
    }


    /**
     * Gets a document from the Document Store
     * @param doc the YDocument to get. The YDocument's id must match an existing
     *            document
     * @param handle a valid session handle
     * @return the YDocument with the document (i.e. the binary file) inserted,
     *         if successful
     * @throws IOException if the service can't be reached
     */
    public YDocument getDocument(YDocument doc, String handle) throws IOException {
        doc.setDocument(executePost(toByteArray(doc, "get", handle)).toByteArray());
        return doc;
    }


    /**
     * Gets a document from the Document Store
     * @param docID the id of the document to get. The id must match an existing
     *              document
     * @param handle a valid session handle
     * @return the YDocument with the document (i.e. the binary file) inserted,
     *         if successful
     * @throws IOException if the service can't be reached
     */
    public YDocument getDocument(long docID, String handle) throws IOException {
        YDocument doc = new YDocument();
        doc.setId(docID);
        return getDocument(doc, handle);
    }


    /**
     * Removes a document from the Document Store
     * @param doc the YDocument to remove. The YDocument's id must match an existing
     *            document
     * @param handle a valid session handle
     * @return a success or error message
     * @throws IOException if the service can't be reached
     */
    public String removeDocument(YDocument doc, String handle) throws IOException {
        return executePost(toByteArray(doc, "remove", handle)).toString(StandardCharsets.UTF_8);
    }

    /**
     * Removes a document from the Document Store
     * @param docID the id of the document to remove. The id must match an existing
     *              document
     * @param handle a valid session handle
     * @return a success or error message
     * @throws IOException if the service can't be reached
     */
    public String removeDocument(long docID, String handle) throws IOException {
        YDocument doc = new YDocument();
        doc.setId(docID);
        return removeDocument(doc, handle);
    }


    /**
     * Updates a stored document with its case id. When a document is uploaded
     * at case start, it occurs before the case has been launched and thus there is
     * not yet a case id allocated. Once the case launch is successful, this method is
     * called to associate the case id with the already uploaded document.
     * @param docID the id of the already uploaded document
     * @param caseID the case id of the case launched with the document as a case param
     * @param handle a valid session handle
     * @return a success or error message
     * @throws IOException if the service can't be reached
     */
    public String addCaseID(long docID, String caseID, String handle) throws IOException {
        YDocument doc = new YDocument();
        doc.setId(docID);
        doc.setCaseId(caseID);
        return executePost(toByteArray(doc, "addcaseid", handle)).toString(StandardCharsets.UTF_8);
    }


    /**
     * Removes all documents from the Document Store matching a case id
     * @param doc a YDocument containing the case id of the documents to remove
     * @param handle a valid session handle
     * @return a success or error message
     * @throws IOException if the service can't be reached
     */
    public String clearCase(YDocument doc, String handle) throws IOException {
        return executePost(toByteArray(doc, "clearcase", handle)).toString(StandardCharsets.UTF_8);
    }


    /**
     * Removes all documents from the Document Store matching a case id
     * @param caseID the case id of the documents to remove
     * @param handle a valid session handle
     * @return a success or error message
     * @throws IOException if the service can't be reached
     */

    public String clearCase(String caseID, String handle) throws IOException {
        YDocument doc = new YDocument();
        doc.setCaseId(caseID);
        return clearCase(doc, handle);
    }


    /**
     * Removes all documents from the Document Store matching a case id
     * @param doc a YDocument containing the case id of the documents to remove
     * @param handle a valid session handle
     * @return a success or error message
     * @throws IOException if the service can't be reached
     */
    public String completeCase(YDocument doc, String handle) throws IOException {
        return executePost(toByteArray(doc, "completecase", handle)).toString(StandardCharsets.UTF_8);
    }


    /**
     * Removes all documents from the Document Store matching a case id
     * @param caseID the case id of the documents to remove
     * @param handle a valid session handle
     * @return a success or error message
     * @throws IOException if the service can't be reached
     */

    public String completeCase(String caseID, String handle) throws IOException {
        YDocument doc = new YDocument();
        doc.setCaseId(caseID);
        return clearCase(doc, handle);
    }


    /**********************************************************************************/

    private byte[] toByteArray(YDocument doc, String action, String handle) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(baos);
        d.writeUTF(action);
        d.writeUTF(handle);
        d.writeUTF(doc.getCaseId());
        d.writeLong(doc.getId());
        if (doc.getDocumentSize() > 0) d.write(doc.getDocument());
        return baos.toByteArray();
    }


    private byte[] toByteArray(String action, String handle, String... args) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(baos);
        d.writeUTF(action);
        d.writeUTF(handle);
        if (args != null) {
            for (String arg : args) {
                d.writeUTF(arg);
            }
        }
        return baos.toByteArray();
    }


    private ByteArrayOutputStream executePost(byte[] bytes) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(_storeURI))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "multipart/form-data")
                .header("Content-length", String.valueOf(bytes.length))
                .header("Connection", "close")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        try {
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            byte[] responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("No response from server (HTTP " + response.statusCode() + ")");
            }

            if (response.statusCode() >= 400) {
                throw new IOException("HTTP error " + response.statusCode() + ": " +
                        new String(responseBody, StandardCharsets.UTF_8));
            }

            ByteArrayOutputStream outStream = new ByteArrayOutputStream(responseBody.length);
            outStream.write(responseBody);
            return outStream;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }

}
