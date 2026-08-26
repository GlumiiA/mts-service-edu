package ru.aigul.mts_service.integration.jca;

import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ConfigProperty;
import jakarta.resource.spi.Connector;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.transaction.xa.XAResource;
@Getter
@Setter
@Slf4j
@Connector(
        displayName = "Taiga Resource Adapter",
        description = "JCA Resource Adapter for Taiga API integration",
        vendorName = "MTS Service"
)
public class TaigaResourceAdapter implements ResourceAdapter {

    @ConfigProperty(description = "Taiga API token")
    private String apiToken;

    @ConfigProperty(description = "Taiga project ID")
    private long projectId;

    @ConfigProperty(description = "Request timeout in milliseconds")
    private long requestTimeoutMs = 10000;

    @Override
    public void start(BootstrapContext bootstrapContext) {
        log.info("Taiga Resource Adapter started");
    }

    @Override
    public void stop() {
        log.info("Taiga Resource Adapter stopped");
    }

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec activationSpec) {
    }

    @Override
    public void endpointDeactivation(MessageEndpointFactory endpointFactory, ActivationSpec activationSpec) {
    }

    @Override
    public XAResource[] getXAResources(ActivationSpec[] activationSpecs) {
        return new XAResource[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaigaResourceAdapter that = (TaigaResourceAdapter) o;

        if (projectId != that.projectId) return false;
        if (requestTimeoutMs != that.requestTimeoutMs) return false;
        return apiToken != null ? apiToken.equals(that.apiToken) : that.apiToken == null;
    }

    @Override
    public int hashCode() {
        int result = apiToken != null ? apiToken.hashCode() : 0;
        result = 31 * result + Long.hashCode(projectId);
        result = 31 * result + Long.hashCode(requestTimeoutMs);
        return result;
    }
}




