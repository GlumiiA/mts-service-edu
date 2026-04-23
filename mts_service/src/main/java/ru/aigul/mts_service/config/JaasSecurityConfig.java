package ru.aigul.mts_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.jaas.AuthorityGranter;
import org.springframework.security.authentication.jaas.JaasAuthenticationProvider;
import org.springframework.security.authentication.jaas.JaasNameCallbackHandler;
import org.springframework.security.authentication.jaas.JaasPasswordCallbackHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.aigul.mts_service.auth.RolePrincipal;
import ru.aigul.mts_service.auth.RolePrivilegeMapper;
import ru.aigul.mts_service.auth.XmlUserStore;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class JaasSecurityConfig {

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
        http
                .authenticationProvider(jaasAuthenticationProvider)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(publicPostEndpoints).permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated())
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
            if (!(principal instanceof RolePrincipal)) {
                return Set.of();
            }

            String roleName = principal.getName();
            Set<String> authorities = new HashSet<>();
            authorities.add("ROLE_" + roleName);
            authorities.addAll(rolePrivilegeMapper.getPrivilegesForRole(roleName));
            return authorities;
        }
    }
}
