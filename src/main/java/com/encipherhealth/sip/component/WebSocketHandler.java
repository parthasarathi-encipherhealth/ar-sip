package com.encipherhealth.sip.component;

import com.encipherhealth.sip.dto.WebSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String callId = getCallIdFromSession(session);
        log.info("WebSocket connection established for callId: {}", callId);
        if (callId != null && !callId.isEmpty()) {
            addSession(callId, session);
            log.info("WebSocket connected for callId: {}. Total sessions: {}", callId, sessions.size());
        } else {
            log.error("No callId found in WebSocket connection. Closing session.");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception e) {
                log.error("Error closing session: ", e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String callId = getCallIdFromSession(session);
        if (callId != null) {
            removeSession(callId);
            log.info("WebSocket disconnected for callId: {}. Status: {}. Total sessions: {}", callId, status, sessions.size());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String callId = getCallIdFromSession(session);
        log.error("Transport error for callId: {}. Error: {}", callId, exception.getMessage());
    }

    public void addSession(String callId, WebSocketSession session) {
        sessions.put(callId, session);
        log.info("Session added for callId: {}. Total sessions: {}", callId, sessions.size());
    }

    public WebSocketSession getSession(String callId) {
        return sessions.get(callId);
    }

    public void removeSession(String callId) {
        WebSocketSession removed = sessions.remove(callId);
        if (removed != null) {
            log.info("Session removed for callId: {}. Total sessions: {}", callId, sessions.size());
        }
    }

    public void sendToSession(String callId, WebSocketResponse payload) {
        WebSocketSession session = getSession(callId);
        if (session == null) {
            log.error("No session found for callId: {}", callId);
            return;
        }

        if (!session.isOpen()) {
            log.error("Session is closed for callId: {}", callId);
            removeSession(callId);
            return;
        }

        try {
            String json = mapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
            log.debug("Message sent to callId: {}", callId);
        } catch (Exception e) {
            log.error("Error sending message to callId: {}. Error: {}", callId, e.getMessage());
            removeSession(callId);
        }
    }

    private String getCallIdFromSession(WebSocketSession session) {
        if (session == null || session.getUri() == null) {
            return null;
        }

        // Try to get from URI first
        String callId = getCallIdFromUri(session.getUri());
        if (callId != null) {
            return callId;
        }

        // Fallback to handshake attributes
        Map<String, Object> attributes = session.getAttributes();
        if (attributes.containsKey("callId")) {
            return (String) attributes.get("callId");
        }
        return null;
    }

    private String getCallIdFromUri(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }

        String query = uri.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && "callId".equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
