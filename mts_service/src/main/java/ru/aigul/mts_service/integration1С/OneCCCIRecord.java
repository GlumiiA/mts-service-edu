package ru.aigul.mts_service.integration1С;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCCCIRecord {

    private String recordName;
    private String recordDescription;

    private String operationType;
    private String payload;
    private String externalId;

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}


