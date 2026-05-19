package ru.aigul.mts_service.integration1С;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCResponseDTO {
    
    private String status;
    private String externalId;
    private String errorCode;
    private String errorMessage;
    private String balance;
    private String timestamp;
}
