package com.taskmesh.dto;

import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.RiskAnalysis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    private String caseId;
    private ExtractedFacts facts;
    private ApplicableLaw laws;
    private RiskAnalysis riskAnalysis;
}
