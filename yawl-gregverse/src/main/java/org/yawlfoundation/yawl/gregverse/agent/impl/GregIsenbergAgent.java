package org.yawlfoundation.yawl.gregverse.agent.impl;

import org.yawlfoundation.yawl.gregverse.agent.GregVerseAgent;

import java.util.List;

/**
 * Greg Isenberg - AI skills strategy expert.
 *
 * <p>Specializes in AI skills strategy, product vision, and startup advisory.</p>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public class GregIsenbergAgent implements GregVerseAgent {

    @Override
    public String getAgentId() {
        return "greg-isenberg";
    }

    @Override
    public String getDisplayName() {
        return "Greg Isenberg";
    }

    @Override
    public String getBio() {
        return "AI skills strategist and product visionary helping startups navigate the AI revolution.";
    }

    @Override
    public List<String> getSpecializedSkills() {
        return List.of("ai-skills-strategy", "product-vision", "startup-advisory");
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are Greg Isenberg, a renowned AI skills strategist and product visionary.

            You help startups and established companies navigate the AI revolution by:
            - Developing AI-powered skills strategies
            - Creating compelling product visions for AI-driven applications
            - Providing startup advisory services for AI ventures
            - Helping companies identify AI opportunities in their domain

            Your communication style is direct, data-driven, and action-oriented.
            You focus on practical implementation and measurable results.

            Always provide specific, actionable advice with clear next steps.
            """;
    }

    @Override
    public String getCommunicationStyle() {
        return "Direct, data-driven, action-oriented with focus on practical implementation";
    }

    @Override
    public List<String> getExpertise() {
        return List.of("AI Strategy", "Product Vision", "Startup Advisory", "Skills Economy", "AI Product Development");
    }

    @Override
    public void initialize() {
        System.out.println("Greg Isenberg agent initialized");
    }

    @Override
    public String processQuery(String query) {
        if (query.toLowerCase().contains("skills strategy")) {
            return provideSkillsStrategyAdvice(query);
        } else if (query.toLowerCase().contains("product vision")) {
            return provideProductVisionAdvice(query);
        } else if (query.toLowerCase().contains("startup")) {
            return provideStartupAdvice(query);
        }
        return provideGeneralAdvice(query);
    }

    private String provideSkillsStrategyAdvice(String query) {
        return """
            ## AI Skills Strategy Advice

            Based on your query, here's my approach:

            1. **Assessment**: Map your current team's AI capabilities
            2. **Gap Analysis**: Identify missing skills for your AI initiatives
            3. **Development Plan**: Create actionable learning paths
            4. **Hiring Strategy**: Target key AI talent needed
            5. **Implementation Roadmap**: 90-day action plan

            Key Insight: Focus on building T-shaped skills - deep expertise in one area with broad AI knowledge.

            Next Steps: Schedule a skills audit session with your team.
            """;
    }

    private String provideProductVisionAdvice(String query) {
        return """
            ## Product Vision Framework

            For AI product development, I recommend:

            1. **Problem First**: Start with customer pain points, not technology
            2. **AI-First Design**: Design experiences that leverage AI's strengths
            3. **Phased Implementation**: Start with MVP, then expand capabilities
            4. **Data Strategy**: Plan for data collection and improvement from day one
            5. **Ethical Foundation**: Build responsible AI practices into your DNA

            Remember: Great AI products solve real problems, not showcase technology.
            """;
    }

    private String provideStartupAdvice(String query) {
        return """
            ## Startup Advisory: AI Venture Success

            Key considerations for your AI startup:

            1. **Market Validation**: Ensure real demand for your AI solution
            2. **Technical Feasibility**: Build with sustainable AI practices
            3. **Team Composition**: Balance AI expertise with domain knowledge
            4. **Funding Strategy**: Highlight AI differentiation to investors
            5. **Go-to-Market**: AI products need clear value propositions

            Pro Tip: Start with one problem and solve it exceptionally well before expanding.
            """;
    }

    private String provideGeneralAdvice(String query) {
        return """
            ## AI Strategy Advisory

            As an AI skills strategist, my focus is on helping you create tangible value from AI:

            • **Strategy Development**: Creating actionable AI roadmaps
            • **Product Innovation**: Building AI-powered products that matter
            • **Team Building**: Assembling AI-ready teams
            • **Implementation**: Turning AI vision into reality

            What specific aspect of AI strategy would you like to explore?
            """;
    }

    @Override
    public String provideAdvice(String topic, String context) {
        return String.format("""
            ## Advice on %s

            Context: %s

            My strategic approach:
            1. Analyze current state
            2. Identify AI opportunities
            3. Develop implementation plan
            4. Measure outcomes
            5. Iterate and improve

            Key Principle: AI should augment human capabilities, not replace them.
            """, topic, context);
    }

    @Override
    public String getResponseFormat() {
        return "Structured advice with clear sections: Overview, Key Points, Action Steps, and Next Steps";
    }
}