package ru.aigul.mts_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.jaas.AuthorityGranter;
import org.springframework.security.authentication.jaas.JaasAuthenticationProvider;
import org.springframework.security.authentication.jaas.JaasNameCallbackHandler;
import org.springframework.security.authentication.jaas.JaasPasswordCallbackHandler;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.aigul.mts_service.auth.RolePrincipal;
import ru.aigul.mts_service.auth.RolePrivilegeMapper;
import ru.aigul.mts_service.auth.UserPrincipal;
import ru.aigul.mts_service.auth.XmlUserStore;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class JaasSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(JaasSecurityConfig.class);

    @Value("${app.security.public-post-endpoints}")
    private String publicPostEndpoints;

    @Value("${app.security.public-endpoints}")
    private String publicEndpoints;

    private final RolePrivilegeMapper rolePrivilegeMapper;
    private final XmlUserStore xmlUserStore;

    public JaasSecurityConfig(RolePrivilegeMapper rolePrivilegeMapper, XmlUserStore xmlUserStore) {
        this.rolePrivilegeMapper = rolePrivilegeMapper;
        this.xmlUserStore = xmlUserStore;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JaasAuthenticationProvider jaasAuthenticationProvider() throws Exception {
        JaasAuthenticationProvider provider = new JaasAuthenticationProvider();

        provider.setLoginConfig(new ClassPathResource("jaas.conf"));
        provider.setLoginContextName("MyApplicationHTTP");

        provider.setCallbackHandlers(
                new org.springframework.security.authentication.jaas.JaasAuthenticationCallbackHandler[] {
                        new JaasNameCallbackHandler(),
                        new JaasPasswordCallbackHandler()
                });

        provider.setAuthorityGranters(new AuthorityGranter[] {
                new RolePrivilegeAuthorityGranter(rolePrivilegeMapper)
        });

        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JaasAuthenticationProvider jaasAuthenticationProvider) throws Exception {
        // Parse endpoint patterns from configuration strings
        String[] publicPostEndpointsArray = publicPostEndpoints.split(",");
        String[] publicEndpointsArray = publicEndpoints.split(",");
        
        // Trim and filter out empty patterns
        java.util.List<String> publicPostList = new java.util.ArrayList<>();
        for (String endpoint : publicPostEndpointsArray) {
            String trimmed = endpoint.trim();
            if (!trimmed.isEmpty() && trimmed.startsWith("/")) {
                publicPostList.add(trimmed);
            }
        }
        
        java.util.List<String> publicList = new java.util.ArrayList<>();
        for (String endpoint : publicEndpointsArray) {
            String trimmed = endpoint.trim();
            if (!trimmed.isEmpty() && trimmed.startsWith("/")) {
                publicList.add(trimmed);
            }
        }
        
        http
                .authenticationProvider(jaasAuthenticationProvider)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> {
                    // Add public POST endpoints
                    if (!publicPostList.isEmpty()) {
                        // Match POST requests for configured public POST endpoints
                        authz.requestMatchers(HttpMethod.POST, publicPostList.toArray(new String[0])).permitAll();
                    }
                    // Add public GET/other endpoints  
                    for (String pattern : publicList) {
                        authz.requestMatchers(pattern).permitAll();
                    }
                    // All other requests require authentication
                    authz.anyRequest().authenticated();
                })
                .httpBasic(httpBasic -> {
                });

        return http.build();
    }

    private static class RolePrivilegeAuthorityGranter implements AuthorityGranter {

        private final RolePrivilegeMapper rolePrivilegeMapper;

        RolePrivilegeAuthorityGranter(RolePrivilegeMapper rolePrivilegeMapper) {
            this.rolePrivilegeMapper = rolePrivilegeMapper;
        }

        @Override
        public Set<String> grant(Principal principal) {
            if (principal == null) {
                return Set.of();
            }

            // Log principal information to help diagnose authentication issues
            try {
                log.debug("AuthorityGranter.grant principalType={} name={}", principal.getClass().getName(), principal.getName());
            } catch (Exception e) {
                log.debug("AuthorityGranter.grant principal toString={}", String.valueOf(principal));
            }

            if (principal instanceof UserPrincipal) {
                return Set.of();
            }
            if (!(principal instanceof RolePrincipal)) {
                log.debug("AuthorityGranter.grant skips non-role principalType={} name={}",
                        principal.getClass().getName(), principal.getName());
                return Set.of();
            }

            String roleName = principal.getName();
            Set<String> authorities = new HashSet<>();
            if (roleName != null && !roleName.isEmpty()) {
                authorities.add("ROLE_" + roleName);
                authorities.addAll(rolePrivilegeMapper.getPrivilegesForRole(roleName));
            } else {
                log.warn("AuthorityGranter.grant received principal with empty name: {}", principal);
            }
            return authorities;
        }
    }
}
