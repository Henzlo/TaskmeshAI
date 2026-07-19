package com.taskmesh.controller;

import com.taskmesh.dto.AnalysisReport;
import com.taskmesh.dto.CreateCaseRequest;
import com.taskmesh.dto.CaseResponse;
import com.taskmesh.dto.ReportResponse;
import com.taskmesh.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Case Management")
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    @Operation(summary = "Create a new legal case from contract text")
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        log.info("POST /api/cases | title={} | type={}", request.getTitle(), request.getDocumentType());
        CaseResponse response = caseService.createCase(request);
        log.info("CASE CREATED | id={}", response.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List all cases ordered by newest first")
    public ResponseEntity<List<CaseResponse>> getAllCases() {
        log.info("GET /api/cases | Listing all cases");
        List<CaseResponse> cases = caseService.getAllCases();
        log.info("CASES LISTED | count={}", cases.size());
        return ResponseEntity.ok(cases);
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Run Facts → Law → Risk analysis pipeline for a case")
    public ResponseEntity<AnalysisReport> analyzeCase(@PathVariable String id) {
        log.info("POST /api/cases/{}/analyze | Analysis started", id);
        AnalysisReport report = caseService.analyzeCase(id);
        log.info("ANALYSIS COMPLETE | caseId={} | riskLevel={}", id, report.getRiskAnalysis().getRiskLevel());
        return ResponseEntity.ok(report);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get case details by ID")
    public ResponseEntity<CaseResponse> getCase(@PathVariable String id) {
        log.info("GET /api/cases/{} | Fetching case", id);
        CaseResponse response = caseService.getCase(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/report")
    @Operation(summary = "Fetch full analysis report for a case")
    public ResponseEntity<ReportResponse> getReport(@PathVariable String id) {
        log.info("GET /api/cases/{}/report | Fetching report", id);
        ReportResponse report = caseService.getReport(id);
        log.info("REPORT FETCHED | caseId={} | riskScore={}", id, report.getRiskAnalysis().getOverallScore());
        return ResponseEntity.ok(report);
    }
}
