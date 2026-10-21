package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.repository.PassageiroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: as regras do PassageiroService que nao sao so CRUD — validacao
 * do CPF (digitos verificadores, com ou sem pontuacao) no cadastro e no PATCH,
 * e o documento mascarado quando quem le e o MOTORISTA.
 *
 * como rodar: teste unitario com o repositorio mockado e o perfil simulado no
 * SecurityContextHolder. sem spring, sem banco. roda com "mvn test".
 *
 * por que existe: CPF invalido gravado so aparece quando alguem precisa dele
 * (nota, check-in), e documento vazando pro motorista e problema de LGPD.
 */
class PassageiroServiceTest {

    private final PassageiroRepository repository = mock(PassageiroRepository.class);
    private final PassageiroService service = new PassageiroService(repository, new UsuarioLogado());

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void logarComo(String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "529 982 247 25"})
    void cpfComDigitosCertosEhValido(String cpf) {
        assertThat(PassageiroService.cpfValido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "529.982.247-24", "111.111.111-11", "5299822472a", ""})
    void cpfErradoEhInvalido(String cpf) {
        assertThat(PassageiroService.cpfValido(cpf)).isFalse();
    }

    @Test
    void cadastroComCpfInvalidoRetorna400SemGravar() {
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", "123", "Brasileira", null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF inválido");
        verify(repository, never()).save(any());
    }

    @Test
    void passaporteNaoPassaPelaRegraDoCpf() {
        PassageiroRequestDTO dto = new PassageiroRequestDTO("John", "PASSAPORTE", "XP9988776", "Americana", null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.criar(dto).documento()).isEqualTo("XP9988776");
    }

    @Test
    void patchQueTrocaSoOTipoParaCpfValidaONumeroQueJaEstava() {
        Passageiro existente = Passageiro.builder().id(1L).nome("Ana").tipoDocumento("RG").documento("123").build();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizarParcial(1L, new PassageiroRequestDTO(null, "CPF", null, null, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void motoristaRecebeODocumentoMascarado() {
        logarComo("MOTORISTA");
        when(repository.findById(1L)).thenReturn(Optional.of(Passageiro.builder().id(1L).nome("Ana")
                .tipoDocumento("CPF").documento("529.982.247-25").build()));

        assertThat(service.buscarPorId(1L).documento()).isEqualTo("************25");
    }

    @Test
    void atendenteRecebeODocumentoCompleto() {
        logarComo("ATENDENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(Passageiro.builder().id(1L).nome("Ana")
                .tipoDocumento("CPF").documento("529.982.247-25").build()));

        assertThat(service.buscarPorId(1L).documento()).isEqualTo("529.982.247-25");
    }
}
