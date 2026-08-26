package ru.aigul.mts_service.auth;

import javax.security.auth.Subject;
import java.security.Principal;

public class RolePrincipal implements Principal {
    private String role;

    public RolePrincipal(String role) {
        this.role = role;
    }

    @Override
    public String getName() {
        return role;
    }

    @Override
    public boolean implies(Subject subject) {
        return Principal.super.implies(subject);
    }
}
