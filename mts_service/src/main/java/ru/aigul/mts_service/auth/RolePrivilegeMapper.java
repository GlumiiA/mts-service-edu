package ru.aigul.mts_service.auth;


import org.springframework.stereotype.Component;
import ru.aigul.mts_service.model.Privilege;
import ru.aigul.mts_service.model.Role;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RolePrivilegeMapper {
    public Set<String> getPrivilegesForRole(String roleName) {
        try{
            Role role = Role.valueOf(roleName);
            return role.getPrivileges().stream()
                    .map(Privilege::name)
                    .collect(Collectors.toSet());
        }catch(IllegalArgumentException e){
            System.err.println(e.getMessage());
            return Set.of();
        }
    }

    public Set<String> getPrivilegesForRole(Set<String> roles) {
        return roles.stream()
                .flatMap(roleName -> getPrivilegesForRole(roleName).stream())
                .collect(Collectors.toSet());
    }

    public Role getRole(String roleName) {
        try{
            return Role.valueOf(roleName);
        }catch(IllegalArgumentException e){
            System.err.println(e.getMessage());
            return null;
        }
    }

    public Set<String> getAllRoles() {
        return Arrays.stream(Role.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    public boolean hasPrivilege(String roleName, String privilegeName) {
        Set<String> allPrivileges = getPrivilegesForRole(roleName);
        return allPrivileges.contains(privilegeName);

    }

}
