/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ZAI Service Stub Interface
 * 
 * <p>This is a stub implementation that throws UnsupportedOperationException for methods
 * that are not yet implemented in the real ZaiService. This allows the application to
 * compile and run even when ZAI features are not fully implemented.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ZaiService {

    private String apiKey;
    private boolean configured;
    private String systemPrompt;
    private List<Object> conversationHistory;

    public ZaiService() {
        this.apiKey = System.getenv("ZAI_API_KEY");
        this.configured = apiKey != null && !apiKey.isEmpty();
        this.conversationHistory = new ArrayList<>();
    }

    public ZaiService(String apiKey) {
        this.apiKey = apiKey;
        this.configured = apiKey != null && !apiKey.isEmpty();
        this.conversationHistory = new ArrayList<>();
    }

    public boolean isConfigured() {
        return configured;
    }

    public String getApiKey() {
        return apiKey != null ? apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4) : "";
    }

    public String sendMessage(String message) {
        throw new UnsupportedOperationException("sendMessage is not implemented in this ZaiService stub");
    }

    public String sendMessage(String message, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("sendMessage with parameters is not implemented in this ZaiService stub");
    }

    @SuppressWarnings("unchecked")
    public String[] chatCompletion(Map<String, Object>[] messages) {
        throw new UnsupportedOperationException("chatCompletion is not implemented in this ZaiService stub");
    }

    public void clearCache() {
        throw new UnsupportedOperationException("clearCache is not implemented in this ZaiService stub");
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public List<Object> getConversationHistory() {
        return conversationHistory;
    }

    public boolean isInitialized() {
        return configured;
    }

    public boolean verifyConnection() {
        return configured;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    public String chat(String message) {
        conversationHistory.add(message);
        return "Mock response: " + message;
    }

    public String analyzeWorkflowContext(String workflowId, String currentTask, String workflowData) {
        return "Mock analysis for workflow " + workflowId;
    }

    public String makeWorkflowDecision(String decisionPoint, String inputData, List<String> options) {
        return "Mock decision for " + decisionPoint;
    }

    public String transformData(String inputData, String transformationRule) {
        return "Mock transformed data";
    }

    public String extractInformation(String text, String fieldsToExtract) {
        return "Mock extracted information";
    }

    public String generateDocumentation(String workflowSpec) {
        return "Mock documentation";
    }

    public String validateData(String data, String rules) {
        return "Mock validation result";
    }

    public String[] chatParallel(String message1, String message2) {
        return new String[]{"Mock response 1: " + message1, "Mock response 2: " + message2};
    }

    public String chatWithContext(String systemPromptOverride, String message) {
        return "Mock response with context: " + message;
    }

    public String getDefaultModel() {
        return "GLM-4.7-Flash";
    }

    public void shutdown() {
        // No-op for stub
    }
}
