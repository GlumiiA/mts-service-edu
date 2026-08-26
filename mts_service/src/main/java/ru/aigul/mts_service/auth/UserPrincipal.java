package ru.aigul.mts_service.auth;

import javax.security.auth.Subject;
import java.security.Principal;

public class UserPrincipal implements Principal {
    private final String username;

    public UserPrincipal(final String username) {
        this.username = username;
    }

    @Override
    public boolean implies(Subject subject) {
        return Principal.super.implies(subject);
    }

    @Override
    public String getName() {
        return username;
    }
}
