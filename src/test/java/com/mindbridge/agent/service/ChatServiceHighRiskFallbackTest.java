package com.mindbridge.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.ChatMessage;
import com.mindbridge.agent.domain.ChatSession;
import com.mindbridge.agent.domain.PsychologicalReport;
import com.mindbridge.agent.domain.UserAccount;
import com.mindbridge.agent.dto.ChatRequest;
import com.mindbridge.agent.dto.ChatStreamEvent;
import com.mindbridge.agent.repository.ChatMessageRepository;
import com.mindbridge.agent.repository.ChatSessionRepository;
import com.mindbridge.agent.repository.PsychologicalReportRepository;
import com.mindbridge.agent.repository.UserAccountRepository;
import com.mindbridge.agent.service.agent.AgentRuntimeService;
import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.memory.ShortTermMemoryService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;

@ExtendWith(MockitoExtension.class)
class ChatServiceHighRiskFallbackTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private PsychologicalReportRepository reportRepository;
    @Mock
    private ToolOrchestrationService toolOrchestrationService;
    @Mock
    private ShortTermMemoryService shortTermMemoryService;
    @Mock
    private AgentRuntimeService agentRuntimeService;
    @Mock
    private AiClient aiClient;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                userAccountRepository,
                chatSessionRepository,
                chatMessageRepository,
                reportRepository,
                new MindBridgeProperties(),
                toolOrchestrationService,
                new PrivacySanitizer(),
                shortTermMemoryService,
                agentRuntimeService,
                aiClient
        );
    }

    @Test
    void highRiskInputReturnsFallbackWithoutCallingAgentsOrLlm() {
        UserAccount user = new UserAccount();
        user.setUsername("test-user");
        user.setDisplayName("Test User");
        user.setPassword("encoded-password");
        user.setRoles(java.util.Set.of("ROLE_USER"));

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.save(any(PsychologicalReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ServerSentEvent<ChatStreamEvent>> events = chatService
                .streamChat(1L, new ChatRequest(null, "我不想活了"))
                .collectList()
                .block();

        assertNotNull(events);
        String reply = events.stream()
                .map(ServerSentEvent::data)
                .filter(Objects::nonNull)
                .map(ChatStreamEvent::content)
                .filter(Objects::nonNull)
                .filter(content -> !content.isBlank())
                .collect(Collectors.joining());

        assertEquals(ChatService.HIGH_RISK_FALLBACK_REPLY, reply);
        assertTrue(reply.contains("12356"));
        assertTrue(reply.contains("心理援助热线"));

        verifyNoInteractions(agentRuntimeService);
        verifyNoInteractions(aiClient);
        verify(reportRepository).save(any(PsychologicalReport.class));
    }
}