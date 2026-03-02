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

package org.yawlfoundation.yawl.integration.safe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.safe.agent.PortfolioOptimizationAgent;
import org.yawlfoundation.yawl.integration.safe.agent.TransformationMeasurementAgent;

/**
 * Bootstrap initializer for SAFe portfolio and transformation agents.
 *
 * <p>Wires PortfolioOptimizationAgent with optional GroqLlmGateway for LLM-assisted
 * WSJF portfolio decisions, and initializes TransformationMeasurementAgent for flow metrics
 * and Comparative Agility assessments.
 *
 * <p>Called by SAFeAgentRegistry at startup to initialize specialized agents beyond
 * the core five SAFe team roles (Product Owner, Scrum Master, Developer, Architect, RTE).
 *
 * @since YAWL 6.0
 */
public final class SAFeAgentBootstrap {

    private static final Logger logger = LogManager.getLogger(SAFeAgentBootstrap.class);

    private SAFeAgentBootstrap() {
        // Static utility class
    }

    /**
     * Initializes portfolio and measurement agents with optional LLM support.
     *
     * <p>If yawl-ggen module is on the classpath and GroqLlmGateway can be instantiated,
     * wires it to PortfolioOptimizationAgent. Otherwise, agents use deterministic fallback behavior.
     *
     * @return PortfolioOptimizationAgent and TransformationMeasurementAgent as specialized agent pair
     */
    public static SpecializedAgentPair initializeSpecializedAgents() {
        logger.info("Initializing SAFe specialized agents");

        Object groqGateway = createGroqGateway();
        PortfolioOptimizationAgent portfolioAgent = new PortfolioOptimizationAgent(groqGateway);
        TransformationMeasurementAgent measurementAgent = new TransformationMeasurementAgent();

        logger.info("Specialized agents initialized: Portfolio={}, Measurement={}", portfolioAgent, measurementAgent);

        return new SpecializedAgentPair(portfolioAgent, measurementAgent);
    }

    /**
     * Attempts to create GroqLlmGateway from yawl-ggen module.
     *
     * <p>Uses reflection to avoid hard compile-time dependency on yawl-ggen.
     * If the class cannot be found or instantiated, returns null and logs warning.
     *
     * @return GroqLlmGateway instance, or null if not available on classpath
     */
    private static Object createGroqGateway() {
        try {
            Class<?> groqGatewayClass = Class.forName(
                "org.yawlfoundation.yawl.ggen.rl.GroqLlmGateway"
            );
            return groqGatewayClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            logger.warn("GroqLlmGateway not available on classpath (yawl-ggen module not included); " +
                    "Portfolio optimization will use deterministic fallback");
            return null;
        } catch (Exception e) {
            logger.warn("Failed to instantiate GroqLlmGateway: {}; using fallback", e.getMessage());
            return null;
        }
    }

    /**
     * Pair of specialized agents initialized at startup.
     *
     * @param portfolioAgent portfolio optimization agent with optional LLM support
     * @param measurementAgent transformation measurement and assessment agent
     */
    public record SpecializedAgentPair(
        PortfolioOptimizationAgent portfolioAgent,
        TransformationMeasurementAgent measurementAgent
    ) {}
}
