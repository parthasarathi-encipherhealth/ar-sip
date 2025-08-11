package com.encipherhealth.sip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class AiCallService {
    
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 10000;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${azure.openai.api.key}")
    private String apiKey;
    
    @Value("${azure.openai.api.url}")
    private String apiUrl;
    
    @Value("${azure.openai.model}")
    private String model;

    public AiCallService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String getGptResponse(String prompt) {
        String traceId = UUID.randomUUID().toString();
        int retryCount = 0;
        
        log.info("Getting GPT response for traceId: {}, prompt length: {}", traceId, prompt.length());
        
        while (retryCount < MAX_RETRIES) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                Map<String, Object> body = new HashMap<>();
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", 
                    "You are an AR caller, calling an insurance company to get the claim status. " +
                    "Respond with either 'say: [text]' to speak text, 'play: [digits]' to play DTMF tones, " +
                    "or 'end' to end the call."));
                messages.add(Map.of("role", "user", "content", prompt));

                body.put("messages", messages);
                body.put("model", model);
                body.put("temperature", 0.0);
                body.put("max_tokens", 16000);
                body.put("top_p", 0.95);
                body.put("frequency_penalty", 0);
                body.put("presence_penalty", 0);

                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode choicesNode = root.path("choices");
                    
                    if (choicesNode.isArray() && choicesNode.size() > 0) {
                        JsonNode firstChoice = choicesNode.get(0);
                        JsonNode messageNode = firstChoice.path("message");
                        JsonNode contentNode = messageNode.path("content");
                        
                        String responseText = contentNode.asText();
                        log.info("GPT response received for traceId: {}, response: {}", traceId, responseText);
                        return responseText;
                    } else {
                        log.warn("No choices found in GPT response for traceId: {}", traceId);
                        return "say: Sorry, I couldn't process your request.";
                    }
                } else {
                    log.warn("GPT API returned non-OK status for traceId: {}, status: {}", traceId, response.getStatusCode());
                }
                
            } catch (Exception e) {
                retryCount++;
                log.error("Error getting GPT response for traceId: {}, attempt: {}, error: {}", 
                    traceId, retryCount, e.getMessage(), e);
                
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Thread interrupted while waiting for retry for traceId: {}", traceId);
                        return "Request interrupted";
                    }
                } else {
                    log.error("Failed to get GPT response after {} retries for traceId: {}", MAX_RETRIES, traceId);
                    return "say: Sorry, I couldn't process your request after multiple attempts.";
                }
            }
        }
        
        log.error("Failed to get GPT response after all retries for traceId: {}", traceId);
        return "say: Sorry, I couldn't process your request.";
    }
}
