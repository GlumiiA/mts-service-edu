package ru.aigul.mts_service.integration.jca;

import jakarta.resource.spi.ConnectionRequestInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Connection request information for Taiga connector.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaigaConnectionSpec implements ConnectionRequestInfo {

    private String apiToken;
    private long projectId;
    private long requestTimeoutMs;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaigaConnectionSpec that = (TaigaConnectionSpec) o;

        if (projectId != that.projectId) return false;
        if (requestTimeoutMs != that.requestTimeoutMs) return false;
        return apiToken != null ? apiToken.equals(that.apiToken) : that.apiToken == null;
    }

    @Override
    public int hashCode() {
        int result = apiToken != null ? apiToken.hashCode() : 0;
        result = 31 * result + (int) (projectId ^ (projectId >>> 32));
        result = 31 * result + (int) (requestTimeoutMs ^ (requestTimeoutMs >>> 32));
        return result;
    }
}

