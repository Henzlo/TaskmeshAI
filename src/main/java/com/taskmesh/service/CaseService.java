package com.taskmesh.service;

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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    private static final Set<CaseStatus> IN_PROGRESS = Set.of(
        CaseStatus.EXTRACTING,
        CaseStatus.ANALYZING,
        CaseStatus.REVIEWING
    );

    private final CaseRepository caseRepository;
    private final AnalysisPipelineService analysisPipelineService;
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

    public void startAnalysis(String caseId) {
        Case caseEntity = caseRepository.findById(caseId)
            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        if (IN_PROGRESS.contains(caseEntity.getStatus())) {
            throw new RuntimeException("Analysis already in progress");
        }

        log.info("ANALYSIS QUEUED | caseId={}", caseId);
        analysisPipelineService.runAnalysisPipeline(caseId);
    }

    public Map<String, String> getCaseStatus(String caseId) {
        Case caseEntity = caseRepository.findById(caseId)
            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        Map<String, String> body = new LinkedHashMap<>();
        body.put("caseId", caseEntity.getId());
        body.put("status", caseEntity.getStatus().name());
        body.put("currentStep", mapCurrentStep(caseEntity.getStatus()));
        return body;
    }

    private String mapCurrentStep(CaseStatus status) {
        return switch (status) {
            case CREATED -> "Waiting";
            case EXTRACTING -> "Facts Agent running";
            case ANALYZING -> "Law Agent running";
            case REVIEWING -> "Risk Agent running";
            case COMPLETED -> "Done";
            case FAILED -> "Failed";
        };
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
