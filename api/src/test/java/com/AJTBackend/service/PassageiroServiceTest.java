package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.FiltroPassageiroDTO;
import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.repository.PassageiroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    @ValueSource(strings = {"529.982.247-25", "52998224725", "529 982 247 25", "100.000.001-08"})
    void cpfComDigitosCertosEhValido(String cpf) {
        assertThat(PassageiroService.cpfValido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "529.982.247-24", "529.982.247-15", "111.111.111-11", "5299822472a", ""})
    void cpfErradoEhInvalido(String cpf) {
        assertThat(PassageiroService.cpfValido(cpf)).isFalse();
    }

    @Test
    void cpfNuloEhInvalidoEDocumentoCurtoViraSoAsteriscos() {
        assertThat(PassageiroService.cpfValido(null)).isFalse();
        assertThat(PassageiroService.mascarar(null)).isEqualTo("**");
        assertThat(PassageiroService.mascarar("12")).isEqualTo("**");
        assertThat(PassageiroService.mascarar("XP9988776")).isEqualTo("*******76");
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

    // ----- leitura, PUT, PATCH e exclusao -----

    @Test
    void listagensEBuscaInexistente() {
        var pagina = PageRequest.of(0, 5);
        Passageiro ana = Passageiro.builder().id(1L).nome("Ana").tipoDocumento("RG").documento("12345").build();
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ana), pagina, 1));
        when(repository.findByNacionalidadeIgnoreCase("argentina", pagina)).thenReturn(new PageImpl<>(List.of(ana), pagina, 1));
        when(repository.findByTransferId(7L)).thenReturn(List.of(ana));
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.listar(new FiltroPassageiroDTO("an", null), pagina).conteudo()).hasSize(1);
        assertThat(service.buscarPorNacionalidade("argentina", pagina).totalElementos()).isEqualTo(1);
        assertThat(service.listarPorTransfer(7L)).extracting(PassageiroResponseDTO::nome).containsExactly("Ana");
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(PassageiroNaoEncontradoException.class);
    }

    @Test
    void putValidaExistenciaVersaoECpf() {
        Passageiro ana = Passageiro.builder().id(1L).versao(2L).nome("Ana").tipoDocumento("RG").documento("12345").build();
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(9L, new PassageiroRequestDTO("Ana", "RG", "1", null, null)))
                .isInstanceOf(PassageiroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizar(1L, new PassageiroRequestDTO("Ana", "RG", "1", null, 1L)))
                .isInstanceOf(ConflitoDeVersaoException.class);
        assertThatThrownBy(() -> service.atualizar(1L, new PassageiroRequestDTO("Ana", "CPF", "123", null, null)))
                .isInstanceOf(RegraNegocioException.class);

        var salvo = service.atualizar(1L, new PassageiroRequestDTO("Ana Paula", "CPF", "529.982.247-25", "Brasileira", 2L));
        assertThat(salvo.nome()).isEqualTo("Ana Paula");
        assertThat(salvo.nacionalidade()).isEqualTo("Brasileira");
    }

    @Test
    void patchDeDocumentoENacionalidadeEPatchSoDeNomeNaoValidaCpf() {
        Passageiro ana = Passageiro.builder().id(1L).nome("Ana").tipoDocumento("CPF").documento("123").build(); // cpf antigo invalido
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // so o nome: nao revalida o documento que ja estava gravado
        assertThat(service.atualizarParcial(1L, new PassageiroRequestDTO("Ana Maria", null, null, null, null)).nome()).isEqualTo("Ana Maria");

        var corrigido = service.atualizarParcial(1L, new PassageiroRequestDTO(null, null, "529.982.247-25", "Argentina", null));
        assertThat(corrigido.documento()).isEqualTo("529.982.247-25");
        assertThat(corrigido.nacionalidade()).isEqualTo("Argentina");

        assertThatThrownBy(() -> service.atualizarParcial(9L, new PassageiroRequestDTO("X", null, null, null, null)))
                .isInstanceOf(PassageiroNaoEncontradoException.class);
    }

    @Test
    void excluirInexistenteLanca404EExistenteApaga() {
        when(repository.existsById(9L)).thenReturn(false);
        when(repository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(PassageiroNaoEncontradoException.class);
        service.deletar(1L);
        verify(repository).deleteById(1L);
    }
}
