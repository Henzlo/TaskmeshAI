package com.taskmesh.dto;

import com.taskmesh.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCaseRequest {

    @NotBlank
    private String title;

    @NotNull
    private DocumentType documentType;

    @NotBlank
    @Size(max = 10000)
    private String documentText;
}
