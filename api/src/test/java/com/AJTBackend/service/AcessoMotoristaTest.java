package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    void naoDeveSerRestrito_quandoPerfilEhGerente() {
        // given
        logadoComo("GERENTE");

        // when
        boolean restrito = acesso.restrito();

        // then
        assertThat(restrito).isFalse();
    }

    @ParameterizedTest(name = "gerente ve a OS do motorista {0}")
    @NullSource
    @ValueSource(longs = 7L)
    void deveVerQualquerOs_quandoPerfilEhGerente(Long motoristaDaOs) {
        // given
        logadoComo("GERENTE");

        // when
        boolean podeVer = acesso.podeVer(osDoMotorista(motoristaDaOs));

        // then
        assertThat(podeVer).isTrue();
    }

    @Test
    void deveSerRestrito_quandoPerfilEhMotorista() {
        // given
        logadoComo("MOTORISTA");
        ligadoAoMotorista(5L);

        // when
        boolean restrito = acesso.restrito();

        // then
        assertThat(restrito).isTrue();
    }

    @Test
    void deveDevolverOCadastroLigado_quandoMotoristaTemVinculo() {
        // given
        logadoComo("MOTORISTA");
        ligadoAoMotorista(5L);

        // when
        Optional<Long> motoristaId = acesso.motoristaId();

        // then
        assertThat(motoristaId).contains(5L);
    }

    @Test
    void deveVerAPropriaOs_quandoMotoristaDaOsEhOLigadoAoUsuario() {
        // given
        logadoComo("MOTORISTA");
        ligadoAoMotorista(5L);

        // when
        boolean podeVer = acesso.podeVer(osDoMotorista(5L));

        // then
        assertThat(podeVer).isTrue();
    }

    @Test
    void naoDeveVerAOsDeOutro_quandoMotoristaDaOsEhOutroCadastro() {
        // given
        logadoComo("MOTORISTA");
        ligadoAoMotorista(5L);

        // when
        boolean podeVer = acesso.podeVer(osDoMotorista(7L));

        // then
        assertThat(podeVer).isFalse();
    }

    @Test
    void naoDeveVerAOs_quandoOsAindaNaoTemMotorista() {
        // given
        logadoComo("MOTORISTA");
        ligadoAoMotorista(5L);

        // when
        boolean podeVer = acesso.podeVer(osDoMotorista(null));

        // then
        assertThat(podeVer).isFalse();
    }

    @Test
    void naoDeveTerCadastro_quandoMotoristaNaoTemVinculo() {
        // given
        logadoComo("MOTORISTA");
        ligadoAoMotorista(null);

        // when
        Optional<Long> motoristaId = acesso.motoristaId();

        // then
        assertThat(motoristaId).isEmpty();
    }

    @Test
    void naoDeveVerNenhumaOs_quandoMotoristaNaoTemVinculo() {
        // given
        logadoComo("MOTORISTA");
        ligadoAoMotorista(null);

        // when
        boolean podeVer = acesso.podeVer(osDoMotorista(5L));

        // then
        assertThat(podeVer).isFalse();
    }
}
