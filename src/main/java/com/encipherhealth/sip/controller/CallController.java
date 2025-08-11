package com.encipherhealth.sip.controller;

import com.encipherhealth.sip.service.CallHelperService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/ar")
public class CallController {
    
    @Value("${ngrok.domain:localhost:8080}")
    private String BASE_URL;
    
    private final CallHelperService callHelperService;

    public CallController(CallHelperService callHelperService) {
        this.callHelperService = callHelperService;
    }

    @GetMapping("/connect")
    public String call(@RequestParam(value = "patientId", required = false) String patientId, 
                      HttpServletRequest request) {
        try {
            log.info("Call connection request received for patientId: {}", patientId);
            String callId = callHelperService.callInitializer(patientId, request);
            log.info("Call initialized successfully with callId: {}", callId);
            return callId;
        } catch (Exception e) {
            log.error("Exception occurred during call connection for patientId: {}", patientId, e);
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping(value = "/asterisk", produces = "application/json")
    public String handleAsteriskAction(HttpServletRequest request, 
                                     @RequestParam(value = "patientId", required = false) String patientId) {
        try {
            log.info("Asterisk action request received for patientId: {}", patientId);
            String response = callHelperService.handleAsteriskAction(request, patientId);
            log.info("Asterisk action handled successfully for patientId: {}", patientId);
            return response;
        } catch (Exception e) {
            log.error("Exception occurred in handleAsteriskAction for patientId: {}", patientId, e);
            throw new RuntimeException("Failed to handle Asterisk action", e);
        }
    }

    @PostMapping(value = "/gather", produces = "application/json")
    public String handleGather(@RequestParam(value = "SpeechResult", required = false) String speech, 
                              HttpServletRequest request, 
                              @RequestParam(value = "patientId", required = false) String patientId) {
        try {
            log.info("Gather action request received for patientId: {} with speech: {}", patientId, speech);
            String response = callHelperService.handleGatherAction(speech, request, patientId);
            log.info("Gather action handled successfully for patientId: {}", patientId);
            return response;
        } catch (Exception e) {
            log.error("Exception occurred in handleGatherAction for patientId: {}", patientId, e);
            throw new RuntimeException("Failed to handle gather action", e);
        }
    }

    @GetMapping("/end/{callId}")
    public String endCallById(@PathVariable("callId") String callId) {
        try {
            log.info("End call request received for callId: {}", callId);
            String response = callHelperService.endCall(callId);
            log.info("Call ended successfully for callId: {}", callId);
            return response;
        } catch (Exception e) {
            log.error("Exception occurred while ending call: {}", callId, e);
            throw new RuntimeException("Failed to end call", e);
        }
    }

    @GetMapping("/endAllLiveCalls")
    public String endAllLiveCalls() {
        try {
            log.info("End all live calls request received");
            String response = callHelperService.endAllCall();
            log.info("All live calls ended successfully");
            return response;
        } catch (Exception e) {
            log.error("Exception occurred while ending all live calls", e);
            throw new RuntimeException("Failed to end all live calls", e);
        }
    }

    @GetMapping("/getAllLiveCalls")
    public Page<Object> getAllLiveCalls() {
        try {
            log.info("Get all live calls request received");
            Page<Object> response = callHelperService.getAllLiveCalls();
            log.info("Retrieved {} live calls", response.getTotalElements());
            return response;
        } catch (Exception e) {
            log.error("Exception occurred while getting all live calls", e);
            throw new RuntimeException("Failed to get all live calls", e);
        }
    }

    @GetMapping("/getAllCallDetails")
    public Page<Object> getAllCallDetails() {
        try {
            log.info("Get all call details request received");
            Page<Object> response = callHelperService.getAllCallDetails();
            log.info("Retrieved {} call details", response.getTotalElements());
            return response;
        } catch (Exception e) {
            log.error("Exception occurred while getting all call details", e);
            throw new RuntimeException("Failed to get all call details", e);
        }
    }

    @GetMapping("/callStatus")
    public Object getCallStatus(@RequestParam("callId") String callId) {
        try {
            log.info("Call status request received for callId: {}", callId);
            Object response = callHelperService.getCallStatus(callId);
            log.info("Call status retrieved successfully for callId: {}", callId);
            return response;
        } catch (Exception e) {
            log.error("Exception occurred while getting call status for callId: {}", callId, e);
            return null;
        }
    }

    @GetMapping("/health")
    public String health() {
        log.info("Health check request received");
        return "Asterisk IVR System is running";
    }
}
