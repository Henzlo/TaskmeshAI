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
public class ApplicableLaw {

    private List<ApplicableAct> applicableActs;
    private List<RegulatoryApproval> regulatoryApprovals;
    private List<CitedPrecedent> citedPrecedents;
    private List<String> complianceGaps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicableAct {
        private String name;
        private List<String> sections;
        private String relevance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatoryApproval {
        private String body;
        private boolean required;
        private String timeline;
        private String penalty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitedPrecedent {
        private String caseName;
        private String citation;
        private int relevanceScore;
    }
}
