package com.encipherhealth.sip.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "keywords")
public class Keyword {
    @Id
    private String id;
    private String type;
    private List<String> keywords;
    
    public static class Fields {
        public static final String type = "type";
        public static final String keywords = "keywords";
    }
}
