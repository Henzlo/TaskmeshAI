package com.taskmesh.service;

import com.taskmesh.dto.AnalysisReport;
import com.taskmesh.dto.CreateCaseRequest;
import com.taskmesh.dto.CaseResponse;
import com.taskmesh.dto.ReportResponse;
import com.taskmesh.model.Case;
import com.taskmesh.model.CaseStatus;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.RiskAnalysis;
import com.taskmesh.repository.CaseRepository;
import com.taskmesh.shared.SharedMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    private final CaseRepository caseRepository;
    private final FactsAgentService factsAgent;
    private final LawAgentService lawAgent;
    private final RiskAgentService riskAgent;
    private final SharedMemoryStore sharedMemory;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request) {
        Case caseEntity = Case.builder()
            .title(request.getTitle())
            .documentType(request.getDocumentType())
            .documentText(request.getDocumentText())
            .build();

        caseRepository.save(caseEntity);

        log.info("CASE CREATED | id={} | title={}", caseEntity.getId(), caseEntity.getTitle());

        return CaseResponse.builder()
            .id(caseEntity.getId())
            .title(caseEntity.getTitle())
            .status(caseEntity.getStatus())
            .createdAt(caseEntity.getCreatedAt())
            .build();
    }

    public List<CaseResponse> getAllCases() {
        return caseRepository.findAll().stream()
            .sorted(Comparator.comparing(Case::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(caseEntity -> CaseResponse.builder()
                .id(caseEntity.getId())
                .title(caseEntity.getTitle())
                .status(caseEntity.getStatus())
                .createdAt(caseEntity.getCreatedAt())
                .build())
            .collect(Collectors.toList());
    }

    @Transactional
    public AnalysisReport analyzeCase(String caseId) {
        Case caseEntity = caseRepository.findById(caseId)
            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

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

            long totalProcessingTimeMs = agentTimings.values().stream().mapToLong(Long::longValue).sum();
            sharedMemory.put(caseId, "timings", agentTimings);

            // Complete
            caseEntity.setStatus(CaseStatus.COMPLETED);
            caseEntity.setCompletedAt(LocalDateTime.now());
            caseRepository.save(caseEntity);

            log.info("ANALYSIS COMPLETED | caseId={} | duration={}", caseId,
                java.time.Duration.between(caseEntity.getCreatedAt(), caseEntity.getCompletedAt()).getSeconds());

            return AnalysisReport.builder()
                .caseId(caseId)
                .facts(facts)
                .laws(laws)
                .riskAnalysis(risk)
                .agentTimings(agentTimings)
                .totalProcessingTimeMs(totalProcessingTimeMs)
                .build();
        } catch (Exception e) {
            log.error("ANALYSIS FAILED | caseId={} | error={}", caseId, e.getMessage(), e);
            markCaseFailed(caseId);
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }
    }

    private void markCaseFailed(String caseId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            caseRepository.findById(caseId).ifPresent(failedCase -> {
                failedCase.setStatus(CaseStatus.FAILED);
                caseRepository.save(failedCase);
            });
        });
    }

    public CaseResponse getCase(String caseId) {
        Case caseEntity = caseRepository.findById(caseId)
            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        return CaseResponse.builder()
            .id(caseEntity.getId())
            .title(caseEntity.getTitle())
            .status(caseEntity.getStatus())
            .createdAt(caseEntity.getCreatedAt())
            .build();
    }

    @SuppressWarnings("unchecked")
    public ReportResponse getReport(String caseId) {
        ExtractedFacts facts = (ExtractedFacts) sharedMemory.get(caseId, "facts");
        ApplicableLaw laws = (ApplicableLaw) sharedMemory.get(caseId, "laws");
        RiskAnalysis risk = (RiskAnalysis) sharedMemory.get(caseId, "risk");

        if (facts == null || laws == null || risk == null) {
            throw new RuntimeException("Analysis not complete for case: " + caseId);
        }

        Map<String, Long> agentTimings = (Map<String, Long>) sharedMemory.get(caseId, "timings");
        Long totalProcessingTimeMs = null;
        if (agentTimings != null) {
            totalProcessingTimeMs = agentTimings.values().stream().mapToLong(Long::longValue).sum();
        }

        return ReportResponse.builder()
            .caseId(caseId)
            .facts(facts)
            .laws(laws)
            .riskAnalysis(risk)
            .agentTimings(agentTimings)
            .totalProcessingTimeMs(totalProcessingTimeMs)
            .build();
    }
}
