package com.AJTBackend.config;

import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: o filtro que le o header Authorization em cada requisicao
 * (JwtAuthenticationFilter) — confere que a role usada vem do banco (e nao
 * do token), que usuario inativo/removido/com senha trocada nao autentica,
 * e que token invalido nem chega a consultar o repositorio.
 *
 * como rodar: teste unitario com mocks (UsuarioRepository mockado com
 * mockito). sem spring context, sem banco, sem docker. roda com "mvn test".
 *
 * por que existe: esse filtro e o que garante que desativar um usuario ou
 * trocar seu perfil valha na hora, mesmo com um token ainda "valido" em
 * mãos. sem esse teste, uma mudanca aqui poderia voltar a confiar cegamente
 * na role gravada dentro do token (o bug de seguranca que a matriz de
 * permissoes do SecurityConfig foi desenhada pra evitar).
 */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = new JwtService("chave-de-teste-com-mais-de-32-caracteres-0123456789", 60_000);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, usuarioRepository);

    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void autenticaComRoleVindaDoBancoENaoDoToken() throws Exception {
        // token diz ADMIN, mas no banco o usuario foi rebaixado pra ATENDENTE
        String token = jwtService.gerarToken("joao", Role.ADMIN);
        when(usuarioRepository.findByUsername("joao")).thenReturn(Optional.of(usuario("joao", Role.ATENDENTE, true)));

        filter.doFilter(requisicao("Bearer " + token), new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("joao");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ATENDENTE");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void naoAutenticaUsuarioInativo() throws Exception {
        String token = jwtService.gerarToken("joao", Role.ADMIN);
        when(usuarioRepository.findByUsername("joao")).thenReturn(Optional.of(usuario("joao", Role.ADMIN, false)));

        filter.doFilter(requisicao("Bearer " + token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // segue a cadeia; o SecurityConfig devolve 401
    }

    @Test
    void naoAutenticaUsuarioRemovido() throws Exception {
        String token = jwtService.gerarToken("joao", Role.ADMIN);
        when(usuarioRepository.findByUsername("joao")).thenReturn(Optional.empty());

        filter.doFilter(requisicao("Bearer " + token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void naoAutenticaTokenEmitidoAntesDaTrocaDeSenha() throws Exception {
        String token = jwtService.gerarToken("joao", Role.ADMIN);
        Usuario usuario = usuario("joao", Role.ADMIN, true);
        usuario.setSenhaAlteradaEm(LocalDateTime.now().plusSeconds(5));
        when(usuarioRepository.findByUsername("joao")).thenReturn(Optional.of(usuario));

        filter.doFilter(requisicao("Bearer " + token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenInvalidoNaoConsultaOBanco() throws Exception {
        filter.doFilter(requisicao("Bearer token.invalido.xyz"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(usuarioRepository, never()).findByUsername(anyString());
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void semHeaderSegueSemAutenticar() throws Exception {
        filter.doFilter(requisicao(null), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(usuarioRepository, never()).findByUsername(anyString());
    }

    private static MockHttpServletRequest requisicao(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transfers");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private static Usuario usuario(String username, Role role, boolean ativo) {
        return Usuario.builder().id(1L).nome("Teste").username(username).senha("hash").role(role).ativo(ativo).build();
    }
}
