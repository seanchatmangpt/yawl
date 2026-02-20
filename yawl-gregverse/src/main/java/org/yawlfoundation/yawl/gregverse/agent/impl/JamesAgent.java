package org.yawlfoundation.yawl.gregverse.agent.impl;

import org.yawlfoundation.yawl.gregverse.agent.GregVerseAgent;

import java.util.List;

/**
 * James - SEO analysis and conversion copy expert.
 *
 * <p>Specializes in SEO analysis, positioning, newsletter optimization, and conversion copy.</p>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public class JamesAgent implements GregVerseAgent {

    @Override
    public String getAgentId() {
        return "james";
    }

    @Override
    public String getDisplayName() {
        return "James";
    }

    @Override
    public String getBio() {
        return "SEO and conversion specialist helping businesses optimize their online presence and marketing copy.";
    }

    @Override
    public List<String> getSpecializedSkills() {
        return List.of("seo-analysis", "positioning", "newsletter", "conversion-copy");
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are James, an SEO and conversion copy specialist.

            You help businesses improve their online presence through:
            - Comprehensive SEO analysis and optimization
            - Strategic positioning for maximum impact
            - Newsletter optimization for higher engagement
            - Conversion-focused copywriting

            Your style is analytical, data-driven, and focused on measurable results.
            You provide actionable insights with clear implementation steps.
            """;
    }

    @Override
    public String getCommunicationStyle() {
        return "Analytical, data-driven with practical implementation insights";
    }

    @Override
    public List<String> getExpertise() {
        return List.of("SEO Optimization", "Content Strategy", "Conversion Rate Optimization", "Email Marketing", "Copywriting");
    }

    @Override
    public void initialize() {
        System.out.println("James agent initialized");
    }

    @Override
    public String processQuery(String query) {
        if (query.toLowerCase().contains("seo")) {
            return provideSEOAdvice(query);
        } else if (query.toLowerCase().contains("newsletter") || query.toLowerCase().contains("email")) {
            return provideNewsletterAdvice(query);
        } else if (query.toLowerCase().contains("conversion") || query.toLowerCase().contains("copy")) {
            return provideCopywritingAdvice(query);
        }
        return provideGeneralAdvice(query);
    }

    private String provideSEOAdvice(String query) {
        return """
            ## SEO Analysis & Strategy

            For your SEO needs, here's my comprehensive approach:

            **Technical SEO:**
            - Page speed optimization (target <3s load time)
            - Mobile-first indexing preparation
            - Schema markup implementation
            - Core Web Vitals improvement

            **Content SEO:**
            - Keyword research and clustering
            - Content gap analysis
            - Topic authority building
            - E-E-A-T optimization

            **Link Building:**
            - Quality backlink strategies
            - Content promotion tactics
            - Relationship-based outreach

            Quick Win: Focus on page titles and meta descriptions - they impact click-through rates significantly.
            """;
    }

    private String provideNewsletterAdvice(String query) {
        return """
            ## Newsletter Optimization Strategy

            To maximize newsletter performance:

            **Content Strategy:**
            - Personalized subject lines (open rate +17% on average)
            - Value-driven content structure
            - Clear CTAs (click-through rate improvement)
            - Segmentation for relevance

            **Technical Setup:**
            - Responsive design templates
            - Preheader text optimization
            - Send time optimization
            - A/B testing framework

            **Engagement Boosters:**
            - Interactive content
            - Social sharing integration
            - Forward-to-friend feature
            - Preference center

            Key Metric: Focus on list growth and engagement rate over just open rates.
            """;
    }

    private String provideCopywritingAdvice(String query) {
        return """
            ## Conversion Copywriting Framework

            High-converting copy follows these principles:

            **AIDA Structure:**
            Attention → Interest → Desire → Action

            **Persuasive Elements:**
            - Specific numbers and details
            - Social proof and testimonials
            - Urgency and scarcity
            - Clear benefits over features
            - Strong value proposition

            **Headline Formulas:**
            - How to [achieve result] in [timeframe]
            - The [number] ways to [achieve result]
            - Why [problem] and how to solve it

            **CTA Best Practices:**
            - Action-oriented verbs
            - Specific next steps
            - Urgency when appropriate
            - Benefit-focused language

            Test everything - copy optimization is data-driven improvement.
            """;
    }

    private String provideGeneralAdvice(String query) {
        return """
            ## Marketing Optimization Advisory

            As a marketing specialist, I help businesses optimize their digital presence:

            • **SEO Strategy**: Improve search visibility and organic traffic
            • **Content Optimization**: Create high-converting copy
            • **Email Marketing**: Build engaged audiences
            • **Analytics**: Measure and improve performance

            What specific marketing challenge are you facing?
            """;
    }

    @Override
    public String provideAdvice(String topic, String context) {
        return String.format("""
            ## %s Marketing Strategy

            Context: %s

            My optimization approach:
            1. Audit current performance
            2. Identify improvement opportunities
            3. Implement tactical changes
            4. Measure and iterate

            Key Metric: Focus on conversion rates, not just vanity metrics.
            """, topic, context);
    }

    @Override
    public String getResponseFormat() {
        return "Data-driven advice with specific tactics, metrics, and implementation steps";
    }
}