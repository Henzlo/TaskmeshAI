package com.taskmesh.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAnalysis {

    private int overallScore;
    private String riskLevel;
    private List<RiskCategory> categories;
    private List<RiskFlag> topRisks;
    private List<String> mitigationStrategies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskCategory {
        private String name;
        private String severity;
        private int probability;
        private String impact;
        private String mitigation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFlag {
        private String type;
        private String severity;
        private String description;
        private String suggestion;
    }
}
