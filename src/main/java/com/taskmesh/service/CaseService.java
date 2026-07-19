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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    private final CaseRepository caseRepository;
    private final FactsAgentService factsAgent;
    private final LawAgentService lawAgent;
    private final RiskAgentService riskAgent;
    private final SharedMemoryStore sharedMemory;

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

    @Transactional
    public AnalysisReport analyzeCase(String caseId) {
        Case caseEntity = caseRepository.findById(caseId)
            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        String documentText = caseEntity.getDocumentText();

        // Step 1: Extract Facts
        log.info("ANALYSIS STARTED | caseId={} | Step 1: EXTRACTING", caseId);
        caseEntity.setStatus(CaseStatus.EXTRACTING);
        caseRepository.save(caseEntity);

        ExtractedFacts facts = factsAgent.extractFacts(documentText);
        sharedMemory.put(caseId, "facts", facts);
        log.info("FACTS STORED | caseId={}", caseId);

        // Step 2: Analyze Laws
        log.info("Step 2: ANALYZING | caseId={}", caseId);
        caseEntity.setStatus(CaseStatus.ANALYZING);
        caseRepository.save(caseEntity);

        ApplicableLaw laws = lawAgent.analyzeLaws(facts);
        sharedMemory.put(caseId, "laws", laws);
        log.info("LAWS STORED | caseId={}", caseId);

        // Step 3: Assess Risk
        log.info("Step 3: REVIEWING | caseId={}", caseId);
        caseEntity.setStatus(CaseStatus.REVIEWING);
        caseRepository.save(caseEntity);

        RiskAnalysis risk = riskAgent.assessRisk(facts, laws);
        sharedMemory.put(caseId, "risk", risk);
        log.info("RISK STORED | caseId={}", caseId);

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
            .build();
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

    public ReportResponse getReport(String caseId) {
        ExtractedFacts facts = (ExtractedFacts) sharedMemory.get(caseId, "facts");
        ApplicableLaw laws = (ApplicableLaw) sharedMemory.get(caseId, "laws");
        RiskAnalysis risk = (RiskAnalysis) sharedMemory.get(caseId, "risk");

        if (facts == null || laws == null || risk == null) {
            throw new RuntimeException("Analysis not complete for case: " + caseId);
        }

        return ReportResponse.builder()
            .caseId(caseId)
            .facts(facts)
            .laws(laws)
            .riskAnalysis(risk)
            .build();
    }
}
