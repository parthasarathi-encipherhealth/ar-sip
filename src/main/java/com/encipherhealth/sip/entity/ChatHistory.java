package com.encipherhealth.sip.entity;

import com.encipherhealth.sip.dto.ConversationDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_history")
public class ChatHistory {
    @Id
    private String id;
    private String callId;
    private String patientId;
    private List<ConversationDto> chat;
    
    public ChatHistory(String callId, String patientId, List<ConversationDto> chat) {
        this.callId = callId;
        this.patientId = patientId;
        this.chat = chat != null ? chat : new ArrayList<>();
    }
    
    public static class Fields {
        public static final String id = "id";
        public static final String callId = "callId";
        public static final String patientId = "patientId";
        public static final String chat = "chat";
    }
}
