package com.encipherhealth.sip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketResponse {
    private String id;
    private String status;
    private String message;
    private String source;
}
