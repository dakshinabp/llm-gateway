package com.dakshina.llmgateway;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatEndpointTest {

    private static final String KEY = "test-gateway-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnthropicClient anthropicClient;

    @Test
    void rejectsRequestWithoutAKey() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnEmptyPrompt() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .header("X-Gateway-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("prompt must not be empty"));
    }

    @Test
    void returnsTheModelAnswer() throws Exception {
        when(anthropicClient.complete("what is 2+2"))
                .thenReturn(new CompletionResult("four", 5, 7));

        mockMvc.perform(post("/v1/chat")
                        .header("X-Gateway-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"what is 2+2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("four"));
    }

    @Test
    void secondIdenticalPromptIsServedFromTheCache() throws Exception {
        when(anthropicClient.complete("name a colour"))
                .thenReturn(new CompletionResult("blue", 3, 2));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/chat")
                            .header("X-Gateway-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"prompt\":\"name a colour\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.response").value("blue"));
        }

        verify(anthropicClient, times(1)).complete("name a colour");
    }
}