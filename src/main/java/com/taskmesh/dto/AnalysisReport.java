package com.taskmesh.dto;

import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.RiskAnalysis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReport {

    private String caseId;
    private ExtractedFacts facts;
    private ApplicableLaw laws;
    private RiskAnalysis riskAnalysis;
    private Map<String, Long> agentTimings;
    private Long totalProcessingTimeMs;
}
