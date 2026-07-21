package com.taskmesh.dto;

import com.taskmesh.model.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseResponse {

    private String id;
    private String title;
    private CaseStatus status;
    private LocalDateTime createdAt;
}
