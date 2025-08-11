package com.encipherhealth.sip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

@Slf4j
@Service
public class AsteriskAriService {

    @Value("${asterisk.host}")
    private String asteriskHost;
    
    @Value("${asterisk.httpPort}")
    private String httpPort;
    
    @Value("${asterisk.ariUser}")
    private String ariUser;
    
    @Value("${asterisk.ariPassword}")
    private String ariPassword;
    
    @Value("${asterisk.ariApp}")
    private String ariApp;
    
    @Value("${call.recording.path}")
    private String recordingPath;
    
    @Value("${call.recording.format}")
    private String recordingFormat;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<String, String> channelToCallId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> callEndKeyCount = new ConcurrentHashMap<>();

    private WebSocketClient wsClient;

    public AsteriskAriService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() throws Exception {
        log.info("Initializing Asterisk ARI Service for app: {}", ariApp);
        String wsUri = String.format("ws://%s:%s/ari/events?api_key=%s:%s&app=%s",
                asteriskHost, httpPort, ariUser, ariPassword, ariApp);
        startWebSocket(wsUri);
    }

    private void startWebSocket(String wsUri) throws Exception {
        log.info("Starting WebSocket connection to: {}", wsUri);
        
        wsClient = new WebSocketClient(new URI(wsUri)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.info("Connected to Asterisk ARI events WebSocket");
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonNode event = mapper.readTree(message);
                    String eventType = event.get("type").asText();
                    log.debug("Received ARI event: {}", eventType);

                    switch (eventType) {
                        case "StasisStart" -> {
                            String channelId = event.get("channel").get("id").asText();
                            String callerId = event.get("channel").get("caller").get("number").asText();
                            log.info("StasisStart - Channel: {}, Caller: {}", channelId, callerId);
                            
                            // Answer the channel
                            answerChannel(channelId);
                            
                            // Start recording
                            startRecording(channelId);
                            
                            // Send initial greeting
                            sendInitialGreeting(channelId);
                        }
                        case "RecordingFinished" -> {
                            String recordingName = event.get("recording").get("name").asText();
                            String channelId = event.get("channel").get("id").asText();
                            log.info("Recording finished: {} for channel: {}", recordingName, channelId);
                            
                            // Process the recording asynchronously
                            CompletableFuture.runAsync(() -> {
                                try {
                                    processRecording(recordingName, channelId);
                                } catch (Exception e) {
                                    log.error("Error processing recording: {}", e.getMessage(), e);
                                }
                            });
                        }
                        case "ChannelDestroyed" -> {
                            String channelId = event.get("channel").get("id").asText();
                            log.info("Channel destroyed: {}", channelId);
                            cleanupChannel(channelId);
                        }
                        case "ChannelDtmfReceived" -> {
                            String channelId = event.get("channel").get("id").asText();
                            String digit = event.get("digit").asText();
                            log.info("DTMF received on channel {}: {}", channelId, digit);
                            handleDtmfInput(channelId, digit);
                        }
                        default -> {
                            log.debug("Unhandled event type: {}", eventType);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing ARI event: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.warn("Disconnected from ARI WebSocket: {} (code: {}, remote: {})", reason, code, remote);
                // Implement reconnection logic if needed
                reconnectWebSocket();
            }

            @Override
            public void onError(Exception ex) {
                log.error("WebSocket error: {}", ex.getMessage(), ex);
            }
        };
        
        try {
            wsClient.connect();
            log.info("WebSocket connection initiated");
        } catch (Exception e) {
            log.error("Failed to connect WebSocket: {}", e.getMessage(), e);
        }
    }

    private void reconnectWebSocket() {
        try {
            Thread.sleep(5000); // Wait 5 seconds before reconnecting
            log.info("Attempting to reconnect WebSocket...");
            String wsUri = String.format("ws://%s:%s/ari/events?api_key=%s:%s&app=%s",
                    asteriskHost, httpPort, ariUser, ariPassword, ariApp);
            startWebSocket(wsUri);
        } catch (Exception e) {
            log.error("Failed to reconnect WebSocket: {}", e.getMessage(), e);
        }
    }

    private void answerChannel(String channelId) {
        try {
            String url = String.format("http://%s:%s/ari/channels/%s/answer?api_key=%s:%s",
                    asteriskHost, httpPort, channelId, ariUser, ariPassword);
            
            Request.put(url).execute().discardContent();
            log.info("Answered channel: {}", channelId);
        } catch (Exception e) {
            log.error("Failed to answer channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void startRecording(String channelId) {
        try {
            String recName = "rec-" + UUID.randomUUID();
            String url = String.format("http://%s:%s/ari/channels/%s/record?format=%s&name=%s&api_key=%s:%s",
                    asteriskHost, httpPort, channelId, recordingFormat, recName, ariUser, ariPassword);
            
            Request.post(url).execute().discardContent();
            log.info("Started recording: {} on channel: {}", recName, channelId);
        } catch (Exception e) {
            log.error("Failed to start recording on channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void sendInitialGreeting(String channelId) {
        try {
            String greeting = "Hello, this is your automated AR caller. Please state your inquiry.";
            playTextToSpeech(channelId, greeting);
            log.info("Sent initial greeting to channel: {}", channelId);
        } catch (Exception e) {
            log.error("Failed to send initial greeting to channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void processRecording(String recordingName, String channelId) {
        try {
            log.info("Processing recording: {} for channel: {}", recordingName, channelId);
            
            // Download the recording
            byte[] audio = downloadRecording(recordingName);
            if (audio.length == 0) {
                log.error("Failed to download recording: {}", recordingName);
                return;
            }
            
            // Transcribe the audio
            String transcript = transcribeWithAzure(audio);
            if (transcript == null || transcript.trim().isEmpty()) {
                log.warn("Empty transcript for recording: {}", recordingName);
                return;
            }
            
            log.info("Transcript for channel {}: {}", channelId, transcript);
            
            // Get AI response
            String aiResponse = getAiResponse(transcript);
            log.info("AI response for channel {}: {}", channelId, aiResponse);
            
            // Act on the AI response
            actOnAiResponse(aiResponse, channelId);
            
        } catch (Exception e) {
            log.error("Error processing recording {} for channel {}: {}", recordingName, channelId, e.getMessage(), e);
        }
    }

    private byte[] downloadRecording(String recordingName) {
        try {
            Path recordingFilePath = Paths.get(recordingPath, recordingName + "." + recordingFormat);
            if (!Files.exists(recordingFilePath)) {
                log.error("Recording file not found: {}", recordingFilePath);
                return new byte[0];
            }
            
            byte[] audio = Files.readAllBytes(recordingFilePath);
            log.info("Downloaded recording: {} ({} bytes)", recordingName, audio.length);
            return audio;
        } catch (Exception e) {
            log.error("Failed to download recording {}: {}", recordingName, e.getMessage(), e);
            return new byte[0];
        }
    }

    private String transcribeWithAzure(byte[] audio) {
        // This would integrate with Azure Speech Services
        // For now, return a placeholder
        log.info("Transcribing audio of {} bytes", audio.length);
        return "placeholder transcript";
    }

    private String getAiResponse(String transcript) {
        // This would integrate with your AI service
        // For now, return a placeholder
        log.info("Getting AI response for transcript: {}", transcript);
        return "say: Thank you for your inquiry. I will process this information.";
    }

    private void actOnAiResponse(String aiResponse, String channelId) {
        try {
            if (aiResponse.startsWith("say:")) {
                String text = aiResponse.substring(4).trim();
                playTextToSpeech(channelId, text);
            } else if (aiResponse.startsWith("play:")) {
                String digits = aiResponse.substring(5).trim();
                sendDtmf(channelId, digits);
            } else if ("end".equalsIgnoreCase(aiResponse.trim())) {
                hangupChannel(channelId);
            } else {
                // Default: just say the full response text
                playTextToSpeech(channelId, aiResponse);
            }
        } catch (Exception e) {
            log.error("Error acting on AI response for channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void playTextToSpeech(String channelId, String text) {
        try {
            // This would integrate with Azure TTS or other TTS service
            log.info("Playing TTS on channel {}: {}", channelId, text);
            
            // For now, just log the action
            // In a real implementation, you would:
            // 1. Convert text to speech
            // 2. Save audio file
            // 3. Play it on the channel
        } catch (Exception e) {
            log.error("Failed to play TTS on channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void sendDtmf(String channelId, String digits) {
        try {
            String url = String.format("http://%s:%s/ari/channels/%s/dtmf?digits=%s&api_key=%s:%s",
                    asteriskHost, httpPort, channelId, digits, ariUser, ariPassword);
            
            Request.post(url).execute().discardContent();
            log.info("Sent DTMF '{}' on channel: {}", digits, channelId);
        } catch (Exception e) {
            log.error("Failed to send DTMF on channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void handleDtmfInput(String channelId, String digit) {
        try {
            log.info("Handling DTMF input '{}' on channel: {}", digit, channelId);
            
            // Process DTMF input based on your IVR logic
            // This could trigger different actions based on the digit pressed
            
        } catch (Exception e) {
            log.error("Error handling DTMF input on channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void hangupChannel(String channelId) {
        try {
            String url = String.format("http://%s:%s/ari/channels/%s?api_key=%s:%s",
                    asteriskHost, httpPort, channelId, ariUser, ariPassword);
            
            Request.delete(url).execute().discardContent();
            log.info("Hung up channel: {}", channelId);
        } catch (Exception e) {
            log.error("Failed to hangup channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void cleanupChannel(String channelId) {
        try {
            channelToCallId.remove(channelId);
            callEndKeyCount.remove(channelId);
            log.info("Cleaned up channel: {}", channelId);
        } catch (Exception e) {
            log.error("Error cleaning up channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    public void makeCall(String toNumber, String callbackUrl) {
        try {
            log.info("Making call to: {} with callback: {}", toNumber, callbackUrl);
            
            // This would integrate with your SIP provider (Sonetel)
            // For now, just log the action
            log.info("Call initiated to: {}", toNumber);
            
        } catch (Exception e) {
            log.error("Failed to make call to {}: {}", toNumber, e.getMessage(), e);
        }
    }

    public void endCall(String callId) {
        try {
            log.info("Ending call: {}", callId);
            
            // Find the channel associated with this call ID and hang it up
            channelToCallId.entrySet().stream()
                .filter(entry -> entry.getValue().equals(callId))
                .findFirst()
                .ifPresent(entry -> {
                    String channelId = entry.getKey();
                    hangupChannel(channelId);
                });
            
        } catch (Exception e) {
            log.error("Failed to end call {}: {}", callId, e.getMessage(), e);
        }
    }
}

