package com.taskmesh.service;

import com.taskmesh.dto.CreateCaseRequest;
import com.taskmesh.dto.CaseResponse;
import com.taskmesh.dto.ReportResponse;
import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.Case;
import com.taskmesh.model.CaseStatus;
import com.taskmesh.model.DocumentType;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.RiskAnalysis;
import com.taskmesh.repository.CaseRepository;
import com.taskmesh.shared.SharedMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private AnalysisPipelineService analysisPipelineService;

    @Mock
    private SharedMemoryStore sharedMemory;

    @InjectMocks
    private CaseService caseService;

    private static final String CASE_ID = "case-123";

    @BeforeEach
    void setUpSaveBehavior() {
        lenient().when(caseRepository.save(any(Case.class))).thenAnswer(invocation -> {
            Case saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(CASE_ID);
            }
            if (saved.getStatus() == null) {
                saved.setStatus(CaseStatus.CREATED);
            }
            if (saved.getCreatedAt() == null) {
                saved.setCreatedAt(LocalDateTime.now());
            }
            return saved;
        });
    }

    @Test
    void createCase_buildsEntitySavesAndReturnsResponse() {
        CreateCaseRequest request = CreateCaseRequest.builder()
            .title("TechCorp M&A")
            .documentType(DocumentType.MERGER_AGREEMENT)
            .documentText("Contract body")
            .build();

        CaseResponse response = caseService.createCase(request);

        ArgumentCaptor<Case> captor = ArgumentCaptor.forClass(Case.class);
        verify(caseRepository).save(captor.capture());

        Case saved = captor.getValue();
        assertEquals("TechCorp M&A", saved.getTitle());
        assertEquals(DocumentType.MERGER_AGREEMENT, saved.getDocumentType());
        assertEquals("Contract body", saved.getDocumentText());

        assertEquals(CASE_ID, response.getId());
        assertEquals("TechCorp M&A", response.getTitle());
        assertEquals(CaseStatus.CREATED, response.getStatus());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void startAnalysis_happyPath_invokesPipelineOnce() {
        Case existing = Case.builder()
            .id(CASE_ID)
            .title("Test")
            .status(CaseStatus.CREATED)
            .build();
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(existing));

        caseService.startAnalysis(CASE_ID);

        verify(analysisPipelineService, times(1)).runAnalysisPipeline(CASE_ID);
    }

    @Test
    void startAnalysis_caseNotFound_throwsRuntimeException() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> caseService.startAnalysis(CASE_ID));

        assertTrue(ex.getMessage().contains(CASE_ID));
        verify(analysisPipelineService, never()).runAnalysisPipeline(any());
    }

    @Test
    void startAnalysis_alreadyExtracting_throwsAndDoesNotInvokePipeline() {
        Case existing = Case.builder()
            .id(CASE_ID)
            .status(CaseStatus.EXTRACTING)
            .build();
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> caseService.startAnalysis(CASE_ID));

        assertEquals("Analysis already in progress", ex.getMessage());
        verify(analysisPipelineService, never()).runAnalysisPipeline(any());
    }

    @Test
    void getReport_allKeysPresent_returnsAssembledReport() {
        ExtractedFacts facts = ExtractedFacts.builder().build();
        ApplicableLaw laws = ApplicableLaw.builder().build();
        RiskAnalysis risk = RiskAnalysis.builder().overallScore(72).riskLevel("HIGH").build();
        Map<String, Long> timings = Map.of(
            "factsAgentMs", 1000L,
            "lawAgentMs", 2000L,
            "riskAgentMs", 1500L
        );

        when(sharedMemory.get(CASE_ID, "facts")).thenReturn(facts);
        when(sharedMemory.get(CASE_ID, "laws")).thenReturn(laws);
        when(sharedMemory.get(CASE_ID, "risk")).thenReturn(risk);
        when(sharedMemory.get(CASE_ID, "timings")).thenReturn(timings);

        ReportResponse report = caseService.getReport(CASE_ID);

        assertEquals(CASE_ID, report.getCaseId());
        assertSame(facts, report.getFacts());
        assertSame(laws, report.getLaws());
        assertSame(risk, report.getRiskAnalysis());
        assertEquals(timings, report.getAgentTimings());
        assertEquals(4500L, report.getTotalProcessingTimeMs());
    }

    @Test
    void getReport_lawsMissing_throwsIncompleteAnalysis() {
        when(sharedMemory.get(CASE_ID, "facts")).thenReturn(ExtractedFacts.builder().build());
        when(sharedMemory.get(CASE_ID, "laws")).thenReturn(null);
        when(sharedMemory.get(CASE_ID, "risk")).thenReturn(RiskAnalysis.builder().build());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> caseService.getReport(CASE_ID));

        assertTrue(ex.getMessage().contains("Analysis not complete for case: " + CASE_ID));
    }
}
