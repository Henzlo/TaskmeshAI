package com.taskmesh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmesh.dto.ChatResponse;
import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.Case;
import com.taskmesh.model.CaseStatus;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.RiskAnalysis;
import com.taskmesh.repository.CaseRepository;
import com.taskmesh.shared.SharedMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractChatService {

    private final ChatClient chatClient;
    private final SharedMemoryStore sharedMemory;
    private final ObjectMapper objectMapper;
    private final CaseRepository caseRepository;

    public ChatResponse chat(String caseId, String question) {
        Case caseEntity = caseRepository.findById(caseId)
            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        if (caseEntity.getStatus() != CaseStatus.COMPLETED) {
            throw new IllegalArgumentException("Chat is only available for completed analyses");
        }

        ExtractedFacts facts = (ExtractedFacts) sharedMemory.get(caseId, "facts");
        ApplicableLaw laws = (ApplicableLaw) sharedMemory.get(caseId, "laws");
        RiskAnalysis risk = (RiskAnalysis) sharedMemory.get(caseId, "risk");

        if (facts == null || laws == null || risk == null) {
            throw new IllegalArgumentException(
                "Analysis data not available — it may have been cleared on server restart. Please re-run the analysis.");
        }

        String factsJson = toJson(facts);
        String lawsJson = toJson(laws);
        String riskJson = toJson(risk);

        String prompt = """
            You are a legal assistant for TaskMesh AI. Answer the user's question about a contract
            that has already been analyzed by three specialist agents. Base your answer ONLY on the
            analysis context below. If the answer is not in the context, say you don't have that
            information from the analysis — do not invent facts.

            Keep answers concise (2-5 sentences), practical, and reference specific findings
            (clauses, risk scores, laws) where relevant.

            EXTRACTED FACTS: %s
            APPLICABLE LAWS: %s
            RISK ANALYSIS: %s

            USER QUESTION: %s
            """.formatted(factsJson, lawsJson, riskJson, question);

        long start = System.currentTimeMillis();
        String answer = chatClient.prompt()
            .user(prompt)
            .call()
            .content();
        long responseTimeMs = System.currentTimeMillis() - start;

        log.info("CHAT | caseId={} | questionLen={} | answerLen={} | timeMs={}",
            caseId, question.length(), answer != null ? answer.length() : 0, responseTimeMs);

        return ChatResponse.builder()
            .caseId(caseId)
            .question(question)
            .answer(answer)
            .responseTimeMs(responseTimeMs)
            .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
