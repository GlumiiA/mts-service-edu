package ru.aigul.mts_service.service.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Record implementation for 1C integration operations.
 * 
 * Represents data being exchanged between MTS and 1C systems.
 * Follows JCA CCI Record pattern for standardized integration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCCCIRecord {

    private String recordName;
    private String recordDescription;
    
    // Payload data
    private String operationType; // CREATE_APPLICATION, CHECK_APPLICATION, etc.
    private String payload; // JSON payload for the operation
    private String externalId; // 1C external identifier (if response)

    public void setRecordName(String name) {
        this.recordName = name;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordShortDescription(String description) {
        this.recordDescription = description;
    }

    public String getRecordShortDescription() {
        return recordDescription;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}


