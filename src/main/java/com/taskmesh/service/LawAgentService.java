package com.taskmesh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.ExtractedFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawAgentService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String PRECEDENTS = """
        Relevant Legal Precedents:
        1. Flipkart-Walmart Merger (2018): CCI approved with data localization and vendor non-discrimination conditions. Cross-border e-commerce precedent.
        2. Microsoft-LinkedIn Acquisition (2016): Cross-border tech acquisition, EU and US antitrust clearance required. Professional networking platform precedent.
        3. Tata-Corus Deal (2007): Large cross-border M&A ($12B), UK Takeover Panel and Indian SEBI compliance requirements. Steel industry precedent.
        """;

    public ApplicableLaw analyzeLaws(ExtractedFacts facts) {
        log.info("LAW AGENT | Input parties: {} | Timeline events: {} | Key clauses: {}",
            facts.getParties() != null ? facts.getParties().size() : 0,
            facts.getTimeline() != null ? facts.getTimeline().size() : 0,
            facts.getKeyClauses() != null ? facts.getKeyClauses().size() : 0);

        String prompt = buildPrompt(facts);

        String rawResponse = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        String jsonString = cleanJsonResponse(rawResponse);

        try {
            ApplicableLaw laws = objectMapper.readValue(jsonString, ApplicableLaw.class);

            log.info("LAW AGENT | Applicable acts: {} | Regulatory approvals: {} | Precedents cited: {}",
                laws.getApplicableActs() != null ? laws.getApplicableActs().size() : 0,
                laws.getRegulatoryApprovals() != null ? laws.getRegulatoryApprovals().size() : 0,
                laws.getCitedPrecedents() != null ? laws.getCitedPrecedents().size() : 0);

            return laws;

        } catch (JsonProcessingException e) {
            log.error("LAW AGENT | JSON parse failed. Raw response:\n{}", rawResponse);
            throw new RuntimeException("Failed to parse LLM response as JSON: " + e.getMessage());
        }
    }

    private String cleanJsonResponse(String rawResponse) {
        if (rawResponse == null) {
            throw new RuntimeException("LLM returned null response");
        }

        String cleaned = rawResponse.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    private String buildPrompt(ExtractedFacts facts) {
        return """
            You are an expert legal research assistant. Based on the following case facts and relevant precedents, identify applicable laws and regulatory requirements.
            
            Case Facts:
            - Parties: %s
            - Timeline: %s
            - Financial Terms: %s
            - Key Clauses: %s
            - Missing Standard Clauses: %s
            
            %s
            
            Instructions:
            1. Identify applicable acts and sections (Indian Contract Act 1872, Companies Act 2013, Competition Act 2002, SEBI regulations, FEMA, etc.)
            2. List required regulatory approvals with timelines and penalties for non-compliance
            3. Cite relevant precedents from the list above and explain their relevance
            4. Flag any compliance gaps based on missing clauses or unusual terms
            
            Output MUST be valid JSON only, no markdown, no explanation:
            {
              "applicableActs": [{"name": "...", "sections": ["..."], "relevance": "..."}],
              "regulatoryApprovals": [{"body": "...", "required": true/false, "timeline": "...", "penalty": "..."}],
              "citedPrecedents": [{"caseName": "...", "citation": "...", "relevanceScore": 1-10}],
              "complianceGaps": ["..."]
            }
            """.formatted(
                formatParties(facts.getParties()),
                formatTimeline(facts.getTimeline()),
                facts.getFinancialTerms(),
                formatKeyClauses(facts.getKeyClauses()),
                facts.getMissingStandardClauses(),
                PRECEDENTS
            );
    }

    private String formatParties(java.util.List<ExtractedFacts.Party> parties) {
        if (parties == null) return "None identified";
        return parties.stream()
            .map(p -> p.getName() + " (" + p.getRole() + ")")
            .reduce((a, b) -> a + ", " + b)
            .orElse("None identified");
    }

    private String formatTimeline(java.util.List<ExtractedFacts.TimelineEvent> timeline) {
        if (timeline == null) return "None identified";
        return timeline.stream()
            .map(t -> t.getDate() + ": " + t.getEvent())
            .reduce((a, b) -> a + "; " + b)
            .orElse("None identified");
    }

    private String formatKeyClauses(java.util.List<ExtractedFacts.KeyClause> clauses) {
        if (clauses == null) return "None identified";
        return clauses.stream()
            .map(c -> c.getType() + " - " + c.getSummary())
            .reduce((a, b) -> a + "; " + b)
            .orElse("None identified");
    }
}
