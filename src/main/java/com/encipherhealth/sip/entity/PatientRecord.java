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
@Document(collection = "patient_records")
public class PatientRecord {
    @Id
    private String id;
    private String name;
    private String phoneNumber;
    private String callId;
    private List<String> dos; // Dates of Service
    private List<String> billId;
    private String status;
    private String createdAt;
    
    public PatientRecord(String id, String name, String phoneNumber, List<String> dos, List<String> billId) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.dos = dos;
        this.billId = billId;
        this.status = "ACTIVE";
        this.createdAt = java.time.LocalDateTime.now().toString();
    }
}
