package com.taskmesh.controller;

import com.taskmesh.dto.AnalysisReport;
import com.taskmesh.dto.CreateCaseRequest;
import com.taskmesh.dto.CaseResponse;
import com.taskmesh.dto.ReportResponse;
import com.taskmesh.service.CaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Slf4j
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        log.info("POST /api/cases | title={} | type={}", request.getTitle(), request.getDocumentType());
        CaseResponse response = caseService.createCase(request);
        log.info("CASE CREATED | id={}", response.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<AnalysisReport> analyzeCase(@PathVariable String id) {
        log.info("POST /api/cases/{}/analyze | Analysis started", id);
        AnalysisReport report = caseService.analyzeCase(id);
        log.info("ANALYSIS COMPLETE | caseId={} | riskLevel={}", id, report.getRiskAnalysis().getRiskLevel());
        return ResponseEntity.ok(report);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getCase(@PathVariable String id) {
        log.info("GET /api/cases/{} | Fetching case", id);
        CaseResponse response = caseService.getCase(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<ReportResponse> getReport(@PathVariable String id) {
        log.info("GET /api/cases/{}/report | Fetching report", id);
        ReportResponse report = caseService.getReport(id);
        log.info("REPORT FETCHED | caseId={} | riskScore={}", id, report.getRiskAnalysis().getOverallScore());
        return ResponseEntity.ok(report);
    }
}
