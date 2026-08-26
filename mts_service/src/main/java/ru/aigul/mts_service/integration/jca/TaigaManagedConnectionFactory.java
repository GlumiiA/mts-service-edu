package ru.aigul.mts_service.integration.jca;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterAssociation;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.PrintWriter;
import java.io.Serial;
import javax.security.auth.Subject;
import java.util.Set;
@Getter
@Setter
@Slf4j
public class TaigaManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {

    @Serial
    private static final long serialVersionUID = 1L;

    private String apiToken;
    private long projectId;
    private long requestTimeoutMs = 10000;
    private WebClient webClient;
    private PrintWriter logWriter;

    @Setter
    private ResourceAdapter resourceAdapter;

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo cxRequestInfo)
            throws ResourceException {

        TaigaConnectionSpec spec = (TaigaConnectionSpec) cxRequestInfo;
        if (spec == null) {
            spec = new TaigaConnectionSpec(apiToken, projectId, requestTimeoutMs);
        }

        if (webClient == null) {
            throw new ResourceException("WebClient is not configured");
        }

        TaigaManagedConnection mc = new TaigaManagedConnection(webClient, spec);
        if (logWriter != null) {
            mc.setLogWriter(logWriter);
        }
        log.debug("Created TaigaManagedConnection with projectId={}", spec.getProjectId());
        return mc;
    }

    public ManagedConnection matchManagedConnections(Set connectionSet, Subject subject,
                                                      ConnectionRequestInfo cxRequestInfo) {
        for (Object obj : connectionSet) {
            if (obj instanceof TaigaManagedConnection mc) {
                log.debug("Matched existing TaigaManagedConnection");
                return mc;
            }
        }
        return null;
    }

    public Object createConnectionFactory(ConnectionManager cxManager) {
        return new TaigaConnectionFactory(this, cxManager);
    }

    public Object createConnectionFactory() {
        return new TaigaConnectionFactory(this, null);
    }

    @Override
    public ResourceAdapter getResourceAdapter() {
        return resourceAdapter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaigaManagedConnectionFactory that = (TaigaManagedConnectionFactory) o;

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







