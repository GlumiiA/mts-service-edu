package ru.aigul.mts_service.integration.jca;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionMetaData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
@Slf4j
@RequiredArgsConstructor
public class TaigaManagedConnection implements ManagedConnection {

    private final WebClient taigaWebClient;
    private final TaigaConnectionSpec spec;
    private final List<ConnectionEventListener> eventListeners = new ArrayList<>();
    private PrintWriter logWriter;
    private boolean destroyed = false;
    private TaigaConnectionImpl connection;

    @Override
    public Object getConnection(Subject subject, ConnectionRequestInfo cxRequestInfo)
            throws ResourceException {
        if (destroyed) {
            throw new ResourceException("Managed connection is destroyed");
        }

        if (connection == null) {
            connection = new TaigaConnectionImpl(this, taigaWebClient, spec);
        }
        return connection;
    }

    @Override
    public void destroy() {
        destroyed = true;
        if (connection != null) {
            connection.invalidate();
        }
        log.debug("TaigaManagedConnection destroyed");
    }

    @Override
    public void cleanup() {
        if (connection != null) {
            connection.invalidate();
            connection = null;
        }
        log.debug("TaigaManagedConnection cleaned up");
    }

    @Override
    public void associateConnection(Object connection) throws ResourceException {
        if (!(connection instanceof TaigaConnectionImpl)) {
            throw new ResourceException("Invalid connection type");
        }
        TaigaConnectionImpl taigaConnection = (TaigaConnectionImpl) connection;
        taigaConnection.setManagedConnection(this);
        this.connection = taigaConnection;
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        eventListeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        eventListeners.remove(listener);
    }

    @Override
    public XAResource getXAResource() {
        return null;
    }

    @Override
    public jakarta.resource.spi.LocalTransaction getLocalTransaction() {
        return null;
    }

    @Override
    public ManagedConnectionMetaData getMetaData() {
        return new TaigaManagedConnectionMetaData();
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    protected void fireConnectionClosedEvent() {
        jakarta.resource.spi.ConnectionEvent event =
            new jakarta.resource.spi.ConnectionEvent(this, jakarta.resource.spi.ConnectionEvent.CONNECTION_CLOSED);
        for (ConnectionEventListener listener : eventListeners) {
            listener.connectionClosed(event);
        }
    }
}




