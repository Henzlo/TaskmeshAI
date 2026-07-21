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
public class ExtractedFacts {

    private List<Party> parties;
    private List<TimelineEvent> timeline;
    private FinancialTerms financialTerms;
    private List<KeyClause> keyClauses;
    private List<String> missingStandardClauses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Party {
        private String name;
        private String role;
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private String date;
        private String event;
        private String party;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialTerms {
        private String totalValue;
        private String currency;
        private String paymentSchedule;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyClause {
        private String type;
        private String summary;
        private String pageRef;
    }
}
