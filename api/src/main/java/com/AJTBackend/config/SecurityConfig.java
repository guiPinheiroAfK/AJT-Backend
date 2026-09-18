package com.AJTBackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * matriz de acesso por perfil:
 *
 * | Recurso                                    | Leitura (GET) | Escrita (POST/PUT/PATCH)      | DELETE          |
 * |--------------------------------------------|---------------|-------------------------------|-----------------|
 * | usuarios                                   | ADMIN         | ADMIN                         | ADMIN           |
 * | motoristas, veiculos, ordens-servico       | todos         | ADMIN, GERENTE                | ADMIN, GERENTE  |
 * | paradas-os                                 | todos         | ADMIN, GERENTE (+ MOTORISTA   | ADMIN, GERENTE  |
 * |                                            |               |   no PATCH, so statusParada)  |                 |
 * | passageiros, transfers, pontos-coleta      | todos         | ADMIN, GERENTE, ATENDENTE     | ADMIN, GERENTE  |
 * | auditoria (so leitura)                     | ADMIN, GERENTE| -                             | -               |
 * | cotacao, auth/me, auth/senha               | todos         | todos                         | -               |
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";
    private static final String MOTORISTA = "MOTORISTA";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthErrorHandler jsonAuthErrorHandler;

    @Value("${ajt.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless com Bearer token, sem cookie de sessao
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // preflight CORS
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll() // so habilitado no profile dev
                        .requestMatchers("/api/auth/**").authenticated()

                        .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                        .requestMatchers("/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

                        .requestMatchers("/api/motoristas/**", "/api/veiculos/**", "/api/ordens-servico/**")
                            .hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers(HttpMethod.PATCH, "/api/paradas-os/*").hasAnyRole(ADMIN, GERENTE, MOTORISTA)
                        .requestMatchers("/api/paradas-os/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/api/passageiros/**", "/api/transfers/**", "/api/pontos-coleta/**")
                            .hasAnyRole(ADMIN, GERENTE, ATENDENTE)

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAuthErrorHandler)
                        .accessDeniedHandler(jsonAuthErrorHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false); // token vai no header, nao em cookie
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * o filtro e um @Component, entao o Spring Boot tambem o registraria na
     * cadeia de servlets. Desliga esse registro pra ele rodar so dentro do
     * spring security (evita consulta duplicada ao banco).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
