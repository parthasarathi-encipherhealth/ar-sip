package com.encipherhealth.sip.service;

import com.encipherhealth.sip.component.WebSocketHandler;
import com.encipherhealth.sip.dto.ConversationDto;
import com.encipherhealth.sip.dto.WebSocketResponse;
import com.encipherhealth.sip.entity.ChatHistory;
import com.encipherhealth.sip.entity.Keyword;
import com.encipherhealth.sip.entity.PatientRecord;
import com.encipherhealth.sip.enums.CommonEnums;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CallHelperService {
    
    private final WebSocketHandler webSocketHandler;
    private final AiCallService aiCallService;
    private final DbService dbService;
    private final AsteriskAriService asteriskAriService;
    
    private final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> chatHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PatientRecord> patientRecordConcurrentHashMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> callEndKeyCount = new ConcurrentHashMap<>();
    
    private List<String> callEndKeyWords;
    
    @Value("${sonetel.username}")
    private String sonetelUsername;
    
    @Value("${sonetel.password}")
    private String sonetelPassword;
    
    @Value("${sonetel.domain}")
    private String sonetelDomain;

    public CallHelperService(WebSocketHandler webSocketHandler, 
                           AiCallService aiCallService, 
                           DbService dbService, 
                           AsteriskAriService asteriskAriService) {
        this.webSocketHandler = webSocketHandler;
        this.aiCallService = aiCallService;
        this.dbService = dbService;
        this.asteriskAriService = asteriskAriService;
    }

    public void triggerCall(PatientRecord patientRecord, String prompt, List<String> callCutKeyWords) {
        try {
            log.info("Triggering call for patient: {}", patientRecord.getId());
            
            patientRecordConcurrentHashMap.put(patientRecord.getId(), patientRecord);
            this.callEndKeyWords = callCutKeyWords;
            chatHistory.put(patientRecord.getId(), "conversation");
            promptCache.put(patientRecord.getId(), prompt);
            
            String callbackUrl = "sip://" + sonetelDomain + "/" + patientRecord.getId();
            String callId = UUID.randomUUID().toString();
            
            // Make the call using Asterisk ARI
            asteriskAriService.makeCall(patientRecord.getPhoneNumber(), callbackUrl);
            
            log.info("Call has been triggered for patientId: {} with callId: {}", patientRecord.getId(), callId);
            callEndKeyCount.put(callId, new AtomicInteger(0));
            
            CompletableFuture.runAsync(() -> {
                try {
                    patientRecord.setCallId(callId);
                    dbService.save(patientRecord, PatientRecord.class.getSimpleName());
                    
                    ChatHistory chatHistory = new ChatHistory(callId, patientRecord.getId(), new ArrayList<>());
                    dbService.save(chatHistory, ChatHistory.class.getSimpleName());
                    
                    log.info("Patient record and chat history saved for callId: {}", callId);
                } catch (Exception e) {
                    log.error("Error saving patient record or chat history for callId: {}", callId, e);
                }
            });
            
        } catch (Exception e) {
            log.error("Error triggering call for patient: {}", patientRecord.getId(), e);
            throw new RuntimeException("Failed to trigger call", e);
        }
    }

    public String callInitializer(String patientId, jakarta.servlet.http.HttpServletRequest request) {
        try {
            log.info("Initializing call for patientId: {}", patientId);
            
            // Load call end keywords from database
            this.callEndKeyWords = dbService.findOne(
                new Query(Criteria.where(Keyword.Fields.type).is(CommonEnums.endCall.toString())), 
                Keyword.class, 
                Keyword.class.getSimpleName()
            ).getKeywords();
            
            String host = request.getHeader("Host");
            String callbackUrl = "https://" + host + "/ar/asterisk?patientId=" + patientId;
            
            // Generate a unique call ID
            String callId = UUID.randomUUID().toString();
            
            // Make the call using Asterisk ARI
            asteriskAriService.makeCall(patientId, callbackUrl);
            
            CompletableFuture.runAsync(() -> {
                try {
                    ChatHistory chatHistory = new ChatHistory(callId, patientId, new ArrayList<>());
                    dbService.save(chatHistory, ChatHistory.class.getSimpleName());
                    log.info("Chat history saved for callId: {}", callId);
                } catch (Exception e) {
                    log.error("Error saving chat history for callId: {}", callId, e);
                }
            });
            
            log.info("Call has been initialized for callId: {}", callId);
            return callId;
            
        } catch (Exception e) {
            log.error("Error initializing call for patientId: {}", patientId, e);
            throw new RuntimeException("Failed to initialize call", e);
        }
    }

    public String handleAsteriskAction(jakarta.servlet.http.HttpServletRequest request, String patientId) {
        try {
            String host = request.getHeader("Host");
            String callId = request.getParameter("CallSid");
            String baseUrl = "https://" + host;
            
            log.info("Incoming Asterisk request from host: {} for patient: {}", host, patientId);

            if (callId != null) {
                webSocketHandler.sendToSession(callId, new WebSocketResponse(
                    UUID.randomUUID().toString(), "Typing", "", "ivr"));
            }
            
            // Return Asterisk ARI response format
            return String.format("""
                {
                    "type": "gather",
                    "input": "speech",
                    "action": "%s/ar/gather?patientId=%s",
                    "method": "POST",
                    "timeout": 4
                }
                """, baseUrl, patientId);
                
        } catch (Exception e) {
            log.error("Exception occurred in handleAsteriskAction for patient: {}", patientId, e);
            throw new RuntimeException("Failed to handle Asterisk action", e);
        }
    }

    public String handleGatherAction(String speech, jakarta.servlet.http.HttpServletRequest request, String patientId) {
        try {
            String host = request.getHeader("Host");
            String baseUrl = "https://" + host;
            String callId = request.getParameter("CallSid");
            
            log.info("IVR said: {} for patient: {}", speech, patientId);

            if (speech == null || speech.isBlank()) {
                return String.format("""
                    {
                        "type": "redirect",
                        "url": "%s/ar/asterisk?patientId=%s"
                    }
                    """, baseUrl, patientId);
            }
            
            // Update chat history
            chatHistory.put(patientId, chatHistory.get(patientId) + "\n" + "ivr : " + speech);
            
            if (callId != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        WebSocketResponse webSocketResponse = new WebSocketResponse(
                            UUID.randomUUID().toString(), "Message received", 
                            speech.replace("pan", "pTan"), "ivr");
                        webSocketHandler.sendToSession(callId, webSocketResponse);
                        
                        log.info("WebSocket response sent for callId: {} ivr: {}", callId, webSocketResponse);
                        
                        ConversationDto dto = new ConversationDto(CommonEnums.ivr.toString(), speech);
                        dbService.updateCollectionList(
                            new Query(Criteria.where(ChatHistory.Fields.id).is(callId)),
                            ChatHistory.Fields.chat,
                            dto,
                            ChatHistory.class,
                            ChatHistory.class.getSimpleName()
                        );
                    } catch (Exception e) {
                        log.error("Error processing WebSocket response for callId: {}", callId, e);
                    }
                });
            }
            
            // Check for end keywords
            long matchCount = callEndKeyWords.stream()
                .filter(keyword -> speech.toLowerCase().contains(keyword.toLowerCase()))
                .count();
                
            if (matchCount >= 1) {
                log.info("End keyword matched, ending call: {}", callId);
                return reachedEnd(patientId, callId, baseUrl);
            }
            
            // Get AI response
            String prompt = promptCache.get(patientId);
            String gptResponse = aiCallService.getGptResponse(prompt + "\n" + chatHistory.get(patientId));
            log.info("AI bot response: {} for patient: {}", gptResponse, patientId);
            
            chatHistory.put(patientId, chatHistory.get(patientId) + "\n" + "ar: " + gptResponse);
            
            if (gptResponse.toLowerCase().startsWith("play")) {
                String digits = gptResponse.substring(5).trim();
                
                if (callId != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            WebSocketResponse webSocketResponse = new WebSocketResponse(
                                UUID.randomUUID().toString(), "Message received", 
                                digits.replace("w", "").replace("W", ""), "ar");
                            webSocketHandler.sendToSession(callId, webSocketResponse);
                            
                            log.info("WebSocket sent for callId: {} ar: {}", callId, webSocketResponse);
                            
                            ConversationDto dto = new ConversationDto(CommonEnums.ar.toString(), digits);
                            dbService.updateCollectionList(
                                new Query(Criteria.where(ChatHistory.Fields.id).is(callId)),
                                ChatHistory.Fields.chat,
                                dto,
                                ChatHistory.class,
                                ChatHistory.class.getSimpleName()
                            );
                        } catch (Exception e) {
                            log.error("Error processing WebSocket response for callId: {}", callId, e);
                        }
                    });
                }
                
                return String.format("""
                    {
                        "type": "play",
                        "digits": "%s",
                        "redirect": {
                            "url": "%s/ar/asterisk?patientId=%s",
                            "method": "POST"
                        }
                    }
                    """, digits, baseUrl, patientId);
                    
            } else if (gptResponse.toLowerCase().startsWith("say")) {
                String phrase = gptResponse.substring(4).trim();
                
                if (callId != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            WebSocketResponse webSocketResponse = new WebSocketResponse(
                                UUID.randomUUID().toString(), "Message received", phrase, "ar");
                            webSocketHandler.sendToSession(callId, webSocketResponse);
                            
                            log.info("WebSocket say message for callId: {} ar: {}", callId, webSocketResponse);
                            
                            ConversationDto dto = new ConversationDto(CommonEnums.ar.toString(), phrase);
                            dbService.updateCollectionList(
                                new Query(Criteria.where(ChatHistory.Fields.id).is(callId)),
                                ChatHistory.Fields.chat,
                                dto,
                                ChatHistory.class,
                                ChatHistory.class.getSimpleName()
                            );
                        } catch (Exception e) {
                            log.error("Error processing WebSocket response for callId: {}", callId, e);
                        }
                    });
                }
                
                return String.format("""
                    {
                        "type": "say",
                        "voice": "Polly.Matthew",
                        "language": "en-US",
                        "text": "%s",
                        "redirect": {
                            "url": "%s/ar/asterisk?patientId=%s",
                            "method": "POST"
                        }
                    }
                    """, phrase, baseUrl, patientId);
                    
            } else {
                return String.format("""
                    {
                        "type": "redirect",
                        "url": "%s/ar/asterisk?patientId=%s"
                    }
                    """, baseUrl, patientId);
            }
            
        } catch (Exception e) {
            log.error("Exception occurred in handleGatherAction for patient: {}", patientId, e);
            throw new RuntimeException("Failed to handle gather action", e);
        }
    }

    public String endCall(String callId) {
        try {
            log.info("Ending call: {}", callId);
            asteriskAriService.endCall(callId);
            return "Call Ended";
        } catch (Exception e) {
            log.error("Error ending call: {}", callId, e);
            throw new RuntimeException("Failed to end call", e);
        }
    }

    public String endAllCall() {
        try {
            log.info("Ending all live calls");
            
            // This would need to be implemented based on your Asterisk setup
            // For now, return a placeholder message
            int count = 0;
            
            promptCache.clear();
            log.info("All live calls ended. Total count: {}", count);
            return "Total live calls ended: " + count;
            
        } catch (Exception e) {
            log.error("Failed to end all live calls", e);
            throw new RuntimeException("Error ending live calls", e);
        }
    }

    public Page<Object> getAllLiveCalls() {
        try {
            log.info("Getting all live calls");
            
            // This would need to be implemented based on your Asterisk setup
            // For now, return an empty page
            List<Object> liveCalls = new ArrayList<>();
            Pageable pageable = PageRequest.of(0, liveCalls.isEmpty() ? 1 : liveCalls.size());
            
            return new PageImpl<>(liveCalls, pageable, liveCalls.size());
            
        } catch (Exception e) {
            log.error("Error getting all live calls", e);
            throw new RuntimeException("Failed to get live calls", e);
        }
    }

    public Page<Object> getAllCallDetails() {
        try {
            log.info("Getting all call details");
            
            // This would need to be implemented based on your Asterisk setup
            // For now, return an empty page
            List<Object> allCalls = new ArrayList<>();
            Pageable pageable = PageRequest.of(0, allCalls.isEmpty() ? 1 : allCalls.size());
            
            return new PageImpl<>(allCalls, pageable, allCalls.size());
            
        } catch (Exception e) {
            log.error("Error getting all call details", e);
            throw new RuntimeException("Failed to get call details", e);
        }
    }

    public Object getCallStatus(String callId) {
        try {
            log.info("Getting call status for callId: {}", callId);
            
            // This would need to be implemented based on your Asterisk setup
            // For now, return a placeholder
            return "Call status placeholder for: " + callId;
            
        } catch (Exception e) {
            log.error("Error checking call status for callId: {}", callId, e);
            return null;
        }
    }

    private String reachedEnd(String patientId, String callId, String baseUrl) {
        try {
            if (callEndKeyCount.get(callId).get() < patientRecordConcurrentHashMap.get(patientId).getDos().size() + 1) {
                CompletableFuture.runAsync(() -> {
                    try {
                        callEndKeyCount.put(callId, new AtomicInteger(callEndKeyCount.get(callId).incrementAndGet()));
                        
                        if (callId != null) {
                            webSocketHandler.sendToSession(callId, new WebSocketResponse(
                                UUID.randomUUID().toString(), "Message received", "2", "ar"));
                        }
                        
                        ConversationDto dto = new ConversationDto(CommonEnums.ar.toString(), "2");
                        dbService.updateCollectionList(
                            new Query(Criteria.where(ChatHistory.Fields.id).is(callId)),
                            ChatHistory.Fields.chat,
                            dto,
                            ChatHistory.class,
                            ChatHistory.class.getSimpleName()
                        );
                    } catch (Exception e) {
                        log.error("Error processing end sequence for callId: {}", callId, e);
                    }
                });

                String digit = "2";
                return String.format("""
                    {
                        "type": "play",
                        "digits": "%s",
                        "redirect": {
                            "url": "%s/ar/asterisk?patientId=%s",
                            "method": "POST"
                        }
                    }
                    """, digit, baseUrl, patientId);
            }
            
            endCall(callId);
            log.info("Call ended for patientId: {} with callId: {}", patientId, callId);
            return "";
            
        } catch (Exception e) {
            log.error("Error in reachedEnd for patientId: {} and callId: {}", patientId, callId, e);
            return "";
        }
    }
}
