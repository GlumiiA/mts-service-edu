package ru.aigul.mts_service.integration1С;

import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class OneCCCIInteractionSpec {

    private String operationType;
    private String timeout;
    private boolean functionNameBasedExecution = true;

    public OneCCCIInteractionSpec(String operationType) {
        this.operationType = operationType;
        this.timeout = "30000";
    }
}


