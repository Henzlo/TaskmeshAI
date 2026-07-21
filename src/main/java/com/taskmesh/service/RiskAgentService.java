package com.taskmesh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.RiskAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskAgentService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public RiskAnalysis assessRisk(ExtractedFacts facts, ApplicableLaw laws) {
        log.info("RISK AGENT | Input facts parties: {} | Laws acts: {}",
            facts.getParties() != null ? facts.getParties().size() : 0,
            laws.getApplicableActs() != null ? laws.getApplicableActs().size() : 0);

        String prompt = buildPrompt(facts, laws);

        String rawResponse = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        String jsonString = cleanJsonResponse(rawResponse);

        try {
            RiskAnalysis risk = objectMapper.readValue(jsonString, RiskAnalysis.class);

            log.info("RISK AGENT | Overall score: {} | Risk level: {} | Top risks: {}",
                risk.getOverallScore(),
                risk.getRiskLevel(),
                risk.getTopRisks() != null ? risk.getTopRisks().size() : 0);

            return risk;

        } catch (JsonProcessingException e) {
            log.error("RISK AGENT | JSON parse failed. Raw response:\n{}", rawResponse);
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

    private String buildPrompt(ExtractedFacts facts, ApplicableLaw laws) {
        return """
            You are an expert legal risk analyst. Based on the following case facts and applicable laws, provide a comprehensive risk assessment.
            
            Case Facts:
            - Parties: %s
            - Financial Terms: %s
            - Key Clauses: %s
            - Missing Standard Clauses: %s
            
            Applicable Laws:
            - Acts: %s
            - Regulatory Approvals Required: %s
            - Compliance Gaps: %s
            
            Instructions:
            Evaluate these 5 risk categories:
            1. Financial Risk (payment terms, penalties, indemnity gaps, currency exposure)
            2. Operational Risk (execution timeline, dependencies, resource requirements)
            3. Regulatory Risk (approval delays, compliance violations, jurisdiction issues)
            4. Reputational Risk (party history, public disputes, market perception)
            5. Termination Risk (exit clauses, breach consequences, post-termination obligations)
            
            For each category:
            - Severity: CRITICAL / HIGH / MEDIUM / LOW
            - Probability: 0-100%%
            - Impact description
            - Mitigation suggestion
            
            Also identify:
            - Overall risk score: 0-100
            - Overall risk level: LOW (0-20) / MEDIUM (21-40) / HIGH (41-70) / CRITICAL (71-100)
            - Top 3-5 specific risk flags with type, severity, description, and actionable suggestion
            - 3-5 overall mitigation strategies
            
            Output MUST be valid JSON only, no markdown, no explanation:
            {
              "overallScore": 0-100,
              "riskLevel": "LOW/MEDIUM/HIGH/CRITICAL",
              "categories": [
                {"name": "...", "severity": "...", "probability": 0-100, "impact": "...", "mitigation": "..."}
              ],
              "topRisks": [
                {"type": "...", "severity": "...", "description": "...", "suggestion": "..."}
              ],
              "mitigationStrategies": ["..."]
            }
            """.formatted(
                formatList(facts.getParties()),
                facts.getFinancialTerms(),
                formatList(facts.getKeyClauses()),
                formatList(facts.getMissingStandardClauses()),
                formatList(laws.getApplicableActs()),
                formatList(laws.getRegulatoryApprovals()),
                formatList(laws.getComplianceGaps())
            );
    }

    private String formatList(java.util.List<?> list) {
        if (list == null || list.isEmpty()) return "None identified";
        return list.stream()
            .map(Object::toString)
            .reduce((a, b) -> a + "; " + b)
            .orElse("None identified");
    }
}
