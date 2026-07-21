package com.taskmesh.service;

import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.Case;
import com.taskmesh.model.CaseStatus;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.RiskAnalysis;
import com.taskmesh.repository.CaseRepository;
import com.taskmesh.shared.SharedMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisPipelineService {

    private final CaseRepository caseRepository;
    private final FactsAgentService factsAgent;
    private final LawAgentService lawAgent;
    private final RiskAgentService riskAgent;
    private final SharedMemoryStore sharedMemory;

    @Async("analysisExecutor")
    public void runAnalysisPipeline(String caseId) {
        Case caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            log.error("ANALYSIS SKIPPED | caseId={} | Case not found", caseId);
            return;
        }

        try {
            String documentText = caseEntity.getDocumentText();
            Map<String, Long> agentTimings = new HashMap<>();

            // Step 1: Extract Facts
            log.info("ANALYSIS STARTED | caseId={} | Step 1: EXTRACTING", caseId);
            caseEntity.setStatus(CaseStatus.EXTRACTING);
            caseRepository.save(caseEntity);

            long factsStart = System.currentTimeMillis();
            ExtractedFacts facts = factsAgent.extractFacts(documentText);
            agentTimings.put("factsAgentMs", System.currentTimeMillis() - factsStart);
            sharedMemory.put(caseId, "facts", facts);
            log.info("FACTS STORED | caseId={}", caseId);

            // Step 2: Analyze Laws
            log.info("Step 2: ANALYZING | caseId={}", caseId);
            caseEntity.setStatus(CaseStatus.ANALYZING);
            caseRepository.save(caseEntity);

            long lawStart = System.currentTimeMillis();
            ApplicableLaw laws = lawAgent.analyzeLaws(facts);
            agentTimings.put("lawAgentMs", System.currentTimeMillis() - lawStart);
            sharedMemory.put(caseId, "laws", laws);
            log.info("LAWS STORED | caseId={}", caseId);

            // Step 3: Assess Risk
            log.info("Step 3: REVIEWING | caseId={}", caseId);
            caseEntity.setStatus(CaseStatus.REVIEWING);
            caseRepository.save(caseEntity);

            long riskStart = System.currentTimeMillis();
            RiskAnalysis risk = riskAgent.assessRisk(facts, laws);
            agentTimings.put("riskAgentMs", System.currentTimeMillis() - riskStart);
            sharedMemory.put(caseId, "risk", risk);
            log.info("RISK STORED | caseId={}", caseId);

            sharedMemory.put(caseId, "timings", agentTimings);

            // Complete
            caseEntity.setStatus(CaseStatus.COMPLETED);
            caseEntity.setCompletedAt(LocalDateTime.now());
            caseRepository.save(caseEntity);

            log.info("ANALYSIS COMPLETED | caseId={} | duration={}", caseId,
                java.time.Duration.between(caseEntity.getCreatedAt(), caseEntity.getCompletedAt()).getSeconds());
        } catch (Exception e) {
            log.error("ANALYSIS FAILED | caseId={} | error={}", caseId, e.getMessage(), e);
            caseRepository.findById(caseId).ifPresent(failedCase -> {
                failedCase.setStatus(CaseStatus.FAILED);
                caseRepository.save(failedCase);
            });
            // Do not rethrow — no caller is waiting on the async pipeline
        }
    }
}
