package ru.aigul.mts_service.integration.jca;

import jakarta.resource.spi.ManagedConnectionMetaData;
import jakarta.resource.ResourceException;

/**
 * Metadata for Taiga managed connection.
 */
public class TaigaManagedConnectionMetaData implements ManagedConnectionMetaData {

    @Override
    public String getEISProductName() throws ResourceException {
        return "Taiga";
    }

    @Override
    public String getEISProductVersion() throws ResourceException {
        return "1.0";
    }

    @Override
    public int getMaxConnections() throws ResourceException {
        return 0; // Unlimited
    }

    @Override
    public String getUserName() throws ResourceException {
        return "Taiga API User";
    }
}

