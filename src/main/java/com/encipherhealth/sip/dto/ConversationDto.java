package com.encipherhealth.sip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {
    private String bot;
    private String message;
    private String timestamp;
    
    public ConversationDto(String bot, String message) {
        this.bot = bot;
        this.message = message;
        this.timestamp = java.time.LocalDateTime.now().toString();
    }
}
