package com.dakshina.llmgateway;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        when(anthropicClient.complete("hi")).thenReturn("hello there");

        mockMvc.perform(post("/v1/chat")
                        .header("X-Gateway-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("hello there"));
    }
}