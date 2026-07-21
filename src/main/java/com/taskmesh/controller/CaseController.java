package com.taskmesh.controller;

import com.taskmesh.dto.ChatRequest;
import com.taskmesh.dto.ChatResponse;
import com.taskmesh.dto.CreateCaseRequest;
import com.taskmesh.dto.CaseResponse;
import com.taskmesh.dto.ReportResponse;
import com.taskmesh.model.DocumentType;
import com.taskmesh.service.CaseService;
import com.taskmesh.service.ContractChatService;
import com.taskmesh.service.PdfExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Case Management")
public class CaseController {

    private final CaseService caseService;
    private final PdfExtractionService pdfExtractionService;
    private final ContractChatService contractChatService;

    @PostMapping
    @Operation(summary = "Create a new legal case from contract text")
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        log.info("POST /api/cases | title={} | type={}", request.getTitle(), request.getDocumentType());
        CaseResponse response = caseService.createCase(request);
        log.info("CASE CREATED | id={}", response.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a case by uploading a PDF contract")
    public ResponseEntity<CaseResponse> uploadCase(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("documentType") String documentType) {
        log.info("POST /api/cases/upload | title={} | type={} | filename={}",
            title, documentType, file.getOriginalFilename());

        String extractedText = pdfExtractionService.extractText(file);

        DocumentType docType = parseDocumentType(documentType);
        CreateCaseRequest request = CreateCaseRequest.builder()
            .title(title)
            .documentType(docType)
            .documentText(extractedText)
            .build();

        CaseResponse response = caseService.createCase(request);
        log.info("CASE CREATED FROM PDF | id={}", response.getId());
        return ResponseEntity.ok(response);
    }

    private DocumentType parseDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return DocumentType.OTHER;
        }
        try {
            return DocumentType.valueOf(documentType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DocumentType.OTHER;
        }
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
    @Operation(summary = "Start async Facts → Law → Risk analysis pipeline (poll GET /api/cases/{id}/status)")
    public ResponseEntity<Map<String, String>> analyzeCase(@PathVariable String id) {
        log.info("POST /api/cases/{}/analyze | Analysis queued", id);
        caseService.startAnalysis(id);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("caseId", id);
        body.put("status", "STARTED");
        body.put("message", "Analysis pipeline started. Poll GET /api/cases/{id} for status.");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Poll case analysis status and current pipeline step")
    public ResponseEntity<Map<String, String>> getCaseStatus(@PathVariable String id) {
        log.info("GET /api/cases/{}/status | Polling status", id);
        return ResponseEntity.ok(caseService.getCaseStatus(id));
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

    @PostMapping("/{id}/chat")
    @Operation(summary = "Ask a question about a completed contract analysis")
    public ResponseEntity<ChatResponse> chatWithContract(
            @PathVariable String id,
            @Valid @RequestBody ChatRequest request) {
        log.info("POST /api/cases/{}/chat | questionLen={}", id, request.getQuestion().length());
        ChatResponse response = contractChatService.chat(id, request.getQuestion());
        log.info("CHAT COMPLETE | caseId={} | responseTimeMs={}", id, response.getResponseTimeMs());
        return ResponseEntity.ok(response);
    }
}
