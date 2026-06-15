package ru.aigul.mts_service.integration.jca;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ManagedConnection;
import lombok.AllArgsConstructor;
@AllArgsConstructor
public class TaigaConnectionFactory {

    private final TaigaManagedConnectionFactory managedConnectionFactory;
    private final ConnectionManager connectionManager;

    public TaigaConnection getConnection() throws ResourceException {
        TaigaConnectionSpec spec = new TaigaConnectionSpec(
                managedConnectionFactory.getApiToken(),
                managedConnectionFactory.getProjectId(),
                managedConnectionFactory.getRequestTimeoutMs()
        );
        return getConnection(spec);
    }

    public TaigaConnection getConnection(TaigaConnectionSpec spec) throws ResourceException {
        if (connectionManager != null) {
            Object conn = connectionManager.allocateConnection(
                    managedConnectionFactory,
                    spec
            );
            if (conn instanceof TaigaConnection) {
                return (TaigaConnection) conn;
            }
            throw new ResourceException("Invalid connection type from managed connection");
        } else {
            ManagedConnection mc = managedConnectionFactory.createManagedConnection(null, spec);
            Object conn = mc.getConnection(null, spec);
            if (conn instanceof TaigaConnection) {
                return (TaigaConnection) conn;
            }
            throw new ResourceException("Invalid connection type from managed connection");
        }
    }
}





