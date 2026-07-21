package com.taskmesh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmesh.dto.ChatResponse;
import com.taskmesh.model.ApplicableLaw;
import com.taskmesh.model.Case;
import com.taskmesh.model.CaseStatus;
import com.taskmesh.model.ExtractedFacts;
import com.taskmesh.model.RiskAnalysis;
import com.taskmesh.repository.CaseRepository;
import com.taskmesh.shared.SharedMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractChatServiceTest {

    private static final String CASE_ID = "case-456";
    private static final String QUESTION = "What are the top regulatory risks?";
    private static final String STUB_ANSWER = "The top regulatory risk is CCI approval delay.";

    @Mock
    private SharedMemoryStore sharedMemory;

    @Mock
    private CaseRepository caseRepository;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ContractChatService contractChatService;

    @BeforeEach
    void setUp() {
        contractChatService = new ContractChatService(
            chatClient, sharedMemory, objectMapper, caseRepository);
    }

    @Test
    void chat_happyPath_returnsStubbedAnswerWithTiming() {
        Case completedCase = Case.builder()
            .id(CASE_ID)
            .status(CaseStatus.COMPLETED)
            .build();
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(completedCase));
        when(sharedMemory.get(CASE_ID, "facts")).thenReturn(ExtractedFacts.builder().build());
        when(sharedMemory.get(CASE_ID, "laws")).thenReturn(ApplicableLaw.builder().build());
        when(sharedMemory.get(CASE_ID, "risk")).thenReturn(
            RiskAnalysis.builder().overallScore(65).riskLevel("HIGH").build());
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(STUB_ANSWER);

        ChatResponse response = contractChatService.chat(CASE_ID, QUESTION);

        assertEquals(CASE_ID, response.getCaseId());
        assertEquals(QUESTION, response.getQuestion());
        assertEquals(STUB_ANSWER, response.getAnswer());
        assertNotNull(response.getResponseTimeMs());
        verify(chatClient).prompt();
    }

    @Test
    void chat_nonCompletedCase_throwsIllegalArgumentException() {
        Case inProgress = Case.builder()
            .id(CASE_ID)
            .status(CaseStatus.ANALYZING)
            .build();
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(inProgress));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> contractChatService.chat(CASE_ID, QUESTION));

        assertEquals("Chat is only available for completed analyses", ex.getMessage());
        verifyNoInteractions(chatClient);
    }

    @Test
    void chat_analysisDataMissing_throwsIllegalArgumentException() {
        Case completedCase = Case.builder()
            .id(CASE_ID)
            .status(CaseStatus.COMPLETED)
            .build();
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(completedCase));
        when(sharedMemory.get(CASE_ID, "facts")).thenReturn(ExtractedFacts.builder().build());
        when(sharedMemory.get(CASE_ID, "laws")).thenReturn(null);
        when(sharedMemory.get(CASE_ID, "risk")).thenReturn(RiskAnalysis.builder().build());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> contractChatService.chat(CASE_ID, QUESTION));

        assertTrue(ex.getMessage().contains("Analysis data not available"));
        verifyNoInteractions(chatClient);
    }

    @Test
    void chat_caseNotFound_throwsRuntimeException() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> contractChatService.chat(CASE_ID, QUESTION));

        assertTrue(ex.getMessage().contains("Case not found: " + CASE_ID));
        verifyNoInteractions(chatClient);
    }
}
