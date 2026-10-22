package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * o que testa: a regra da HU07 isolada (AcessoMotorista) — quem nao e MOTORISTA ve qualquer OS;
 * o MOTORISTA so ve a OS cujo motorista e o cadastro ligado ao usuario dele; OS ainda sem
 * motorista e motorista sem cadastro ligado nao enxergam nada.
 *
 * como rodar: teste unitario com o UsuarioRepository mockado e o perfil simulado no
 * SecurityContextHolder. sem spring, sem banco. "mvn test".
 *
 * por que existe: OS, paradas e o PDF dependem dessa regra; um "true" a mais aqui mostraria
 * a OS de um motorista na tela de outro.
 */
class AcessoMotoristaTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AcessoMotorista acesso = new AcessoMotorista(new UsuarioLogado(), usuarioRepository);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComo(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "carlos", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void ligadoAoMotorista(Long motoristaId) {
        when(usuarioRepository.findByUsername("carlos")).thenReturn(Optional.of(Usuario.builder().id(1L)
                .username("carlos").motorista(motoristaId != null ? Motorista.builder().id(motoristaId).build() : null)
                .build()));
    }

    private static OrdemServico osDoMotorista(Long motoristaId) {
        return OrdemServico.builder().id(10L)
                .motorista(motoristaId != null ? Motorista.builder().id(motoristaId).build() : null).build();
    }

    @Test
    void gerenteVeQualquerOs() {
        logadoComo("GERENTE");

        assertThat(acesso.restrito()).isFalse();
        assertThat(acesso.podeVer(osDoMotorista(7L))).isTrue();
        assertThat(acesso.podeVer(osDoMotorista(null))).isTrue();
    }

    @Test
    void motoristaSoVeAPropriaOs() {
        logadoComo("MOTORISTA");
        ligadoAoMotorista(5L);

        assertThat(acesso.restrito()).isTrue();
        assertThat(acesso.motoristaId()).contains(5L);
        assertThat(acesso.podeVer(osDoMotorista(5L))).isTrue();
        assertThat(acesso.podeVer(osDoMotorista(7L))).isFalse();
    }

    @Test
    void motoristaNaoVeOsAindaSemMotorista() {
        logadoComo("MOTORISTA");
        ligadoAoMotorista(5L);

        assertThat(acesso.podeVer(osDoMotorista(null))).isFalse();
    }

    @Test
    void motoristaSemCadastroLigadoNaoVeNada() {
        logadoComo("MOTORISTA");
        ligadoAoMotorista(null);

        assertThat(acesso.motoristaId()).isEmpty();
        assertThat(acesso.podeVer(osDoMotorista(5L))).isFalse();
    }
}
