package ru.aigul.mts_service.config;

import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class XaDataSourceWrapper implements DataSource {

    private final XADataSource xaDataSource;
    private final TransactionManager transactionManager;
    private final Map<Transaction, Connection> activeConnections = new ConcurrentHashMap<>();

    public XaDataSourceWrapper(XADataSource xaDataSource, TransactionManager transactionManager) {
        this.xaDataSource = xaDataSource;
        this.transactionManager = transactionManager;
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            Transaction tx = transactionManager.getTransaction();
            if (tx != null) {
                Connection cached = activeConnections.get(tx);
                if (cached != null && !cached.isClosed()) {
                    return cached;
                }

                XAConnection xaConn = xaDataSource.getXAConnection();
                tx.enlistResource(xaConn.getXAResource());

                Connection conn = xaConn.getConnection();
                activeConnections.put(tx, conn);

                tx.registerSynchronization(new Synchronization() {
                    @Override
                    public void beforeCompletion() {}

                    @Override
                    public void afterCompletion(int status) {
                        activeConnections.remove(tx);
                        try { xaConn.close(); } catch (SQLException ignored) {}
                    }
                });

                return conn;
            }
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to enlist XA resource in JTA transaction", e);
        }

        return xaDataSource.getXAConnection().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() { return null; }

    @Override
    public void setLogWriter(PrintWriter out) {}

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() { return 0; }

    @Override
    public Logger getParentLogger() { return Logger.getLogger("XaDataSourceWrapper"); }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) { return false; }
}