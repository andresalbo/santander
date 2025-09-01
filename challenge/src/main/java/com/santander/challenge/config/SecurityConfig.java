package com.santander.challenge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  // ---- JWT decoder (Keycloak/Auth0/etc. por JWK set) ----
  @Bean
  JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder
        .withJwkSetUri("https://TU-IDP/realms/xxx/protocol/openid-connect/certs")
        .build();
  }

  // ---- Converter: mapea claim "roles" -> ROLE_* (opcional) ----
  @Bean
  JwtAuthenticationConverter jwtAuthConverter() {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");   // o "realm_access.roles"
    roles.setAuthorityPrefix("ROLE_");

    var conv = new JwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(roles);
    return conv;
  }

  // ---- Cadena 1: Swagger abierto ----
  @Bean
  @Order(1)
  SecurityFilterChain swagger(HttpSecurity http) throws Exception {
    http
      .securityMatcher("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html")
      .authorizeHttpRequests(a -> a.anyRequest().permitAll())
      .csrf(csrf -> csrf.disable());
    return http.build();
  }

  // ---- Cadena 2: API protegida por JWT ----
  @Bean
  @Order(2)
  SecurityFilterChain api(HttpSecurity http, JwtAuthenticationConverter jwtAuthConverter) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/public/**").permitAll()
        // Si usás ROLES del claim "roles":
        .requestMatchers("/admin/**").hasRole("admin")
        // Si usás SCOPES, descomentá esta y QUITÁ el converter de roles
        // .requestMatchers("/api/**").hasAuthority("SCOPE_read")
        .anyRequest().authenticated())
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))); // << enchufado

    return http.build();
  }
}
  