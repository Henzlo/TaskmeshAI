package com.taskmesh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmesh.model.ExtractedFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FactsAgentService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ExtractedFacts extractFacts(String documentText) {
        String truncatedText = truncate(documentText, 8000);
        log.info("FACTS AGENT | Input length: {} chars | Truncated: {} chars",
            documentText.length(), truncatedText.length());

        String prompt = buildPrompt(truncatedText);

        // Raw string response from LLM
        String rawResponse = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        log.debug("FACTS AGENT | Raw response: {}", rawResponse);

        // Clean markdown code blocks if present
        String jsonString = cleanJsonResponse(rawResponse);

        try {
            ExtractedFacts facts = objectMapper.readValue(jsonString, ExtractedFacts.class);

            log.info("FACTS AGENT | Parties: {} | Timeline events: {} | Key clauses: {}",
                facts.getParties() != null ? facts.getParties().size() : 0,
                facts.getTimeline() != null ? facts.getTimeline().size() : 0,
                facts.getKeyClauses() != null ? facts.getKeyClauses().size() : 0);

            return facts;

        } catch (JsonProcessingException e) {
            log.error("FACTS AGENT | JSON parse failed. Raw response:\n{}", rawResponse);
            throw new RuntimeException("Failed to parse LLM response as JSON: " + e.getMessage());
        }
    }

    private String cleanJsonResponse(String rawResponse) {
        if (rawResponse == null) {
            throw new RuntimeException("LLM returned null response");
        }

        String cleaned = rawResponse.trim();

        // Remove markdown code blocks
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

    private String buildPrompt(String documentText) {
        return """
            You are an expert legal document analyzer. Extract structured facts from the following contract.
            
            Instructions:
            1. Identify ALL parties involved with their names and roles (e.g., Acquirer, Target, Lessor, Lessee, Buyer, Seller)
            2. Create a timeline of key events with dates (offer, acceptance, due diligence, closing, etc.)
            3. Extract financial terms: total value, currency, payment schedule, earnout, escrow
            4. Identify key clauses: termination, indemnity, non-compete, IP transfer, governing law, dispute resolution
            5. Note any missing standard clauses that should typically be present
            
            Output MUST be valid JSON only, no markdown, no explanation:
            {
              "parties": [{"name": "...", "role": "...", "type": "company/individual"}],
              "timeline": [{"date": "YYYY-MM-DD or description", "event": "...", "party": "..."}],
              "financialTerms": {"totalValue": "...", "currency": "USD/INR/EUR", "paymentSchedule": "..."},
              "keyClauses": [{"type": "...", "summary": "...", "pageRef": "..."}],
              "missingStandardClauses": ["..."]
            }
            
            Contract text:
            %s
            """.formatted(documentText);
    }

    private String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}
