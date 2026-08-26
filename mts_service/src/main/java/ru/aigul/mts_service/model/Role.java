package ru.aigul.mts_service.model;

import java.util.Set;

import static ru.aigul.mts_service.model.Privilege.*;

public enum Role {
    USER(Set.of(CATALOG_READ, APPLICATION_CREATE, APPLICATION_READ_OWN, ACCOUNT_TOPUP)),
    MANAGER(Set.of(CATALOG_READ, APPLICATION_READ_ALL, APPLICATION_APPROVE, APPLICATION_REJECT)),
    ADMIN(Set.of(CATALOG_READ, APPLICATION_READ_ALL, TARIFF_MANAGE, SERVICE_MANAGE, USER_MANAGE, SEARCH_REINDEX));

    private final Set<Privilege> privileges;

    Role(Set<Privilege> privileges) {
        this.privileges = privileges;
    }

    public Set<Privilege> getPrivileges() {
        return privileges;
    }
}
