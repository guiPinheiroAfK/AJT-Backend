package com.AJTBackend.service;

import com.AJTBackend.config.DocumentoHash;
import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.FiltroPassageiroDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.DocumentoJaCadastradoException;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.repository.PassageiroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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
 * documento ja cadastrado em outro passageiro (com ou sem pontuacao) no cadastro,
 * no PUT e no PATCH, e o documento mascarado quando quem le e o MOTORISTA.
 *
 * como rodar: teste unitario com o repositorio mockado e o perfil simulado no
 * SecurityContextHolder. sem spring, sem banco. roda com "mvn test".
 *
 * por que existe: CPF invalido gravado so aparece quando alguem precisa dele
 * (nota, check-in), e documento vazando pro motorista e problema de LGPD.
 */
class PassageiroServiceTest {

    private static final String CPF_VALIDO = "529.982.247-25";

    private final PassageiroRepository repository = mock(PassageiroRepository.class);
    // chave de teste (32 bytes em Base64), a mesma ideia da AJT_CRYPTO_KEY
    private final DocumentoHash documentoHash = new DocumentoHash("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    private final PassageiroService service = new PassageiroService(repository, new UsuarioLogado(), documentoHash);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void logarComo(String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    private static Passageiro passageiro(Long id, Long versao, String nome, String tipo, String documento) {
        return Passageiro.builder().id(id).versao(versao).nome(nome).tipoDocumento(tipo).documento(documento).build();
    }

    private Passageiro gravadoPeloSave() {
        ArgumentCaptor<Passageiro> captor = ArgumentCaptor.forClass(Passageiro.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    // ----- CPF e mascara -----

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "529 982 247 25", "100.000.001-08"})
    void cpfComDigitosCertosEhValido(String cpf) {
        // When
        boolean valido = PassageiroService.cpfValido(cpf);

        // Then
        assertThat(valido).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "529.982.247-24", "529.982.247-15", "111.111.111-11", "5299822472a", ""})
    void cpfErradoEhInvalido(String cpf) {
        // When
        boolean valido = PassageiroService.cpfValido(cpf);

        // Then
        assertThat(valido).isFalse();
    }

    @Test
    void cpfNuloEhInvalido() {
        // When
        boolean valido = PassageiroService.cpfValido(null);

        // Then
        assertThat(valido).isFalse();
    }

    @Test
    void documentoNuloViraSoAsteriscos() {
        // When
        String mascarado = PassageiroService.mascarar(null);

        // Then
        assertThat(mascarado).isEqualTo("**");
    }

    @Test
    void documentoCurtoViraSoAsteriscos() {
        // When
        String mascarado = PassageiroService.mascarar("12");

        // Then
        assertThat(mascarado).isEqualTo("**");
    }

    @Test
    void documentoLongoMostraSoOsDoisUltimosCaracteres() {
        // When
        String mascarado = PassageiroService.mascarar("XP9988776");

        // Then
        assertThat(mascarado).isEqualTo("*******76");
    }

    // ----- cadastro -----

    @Test
    void cadastroComCpfInvalidoRetorna400SemGravar() {
        // Given
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", "123", "Brasileira", null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF inválido");
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroComCpfValidoGravaOsDadosInformados() {
        // Given
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", CPF_VALIDO, "Brasileira", null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        PassageiroResponseDTO criado = service.criar(dto);

        // Then
        assertThat(criado.nome()).isEqualTo("Ana");
        assertThat(criado.documento()).isEqualTo(CPF_VALIDO);
        Passageiro gravado = gravadoPeloSave();
        assertThat(gravado.getNome()).isEqualTo("Ana");
        assertThat(gravado.getTipoDocumento()).isEqualTo("CPF");
        assertThat(gravado.getDocumento()).isEqualTo(CPF_VALIDO);
        assertThat(gravado.getNacionalidade()).isEqualTo("Brasileira");
    }

    @Test
    void passaporteNaoPassaPelaRegraDoCpf() {
        // Given
        PassageiroRequestDTO dto = new PassageiroRequestDTO("John", "PASSAPORTE", "XP9988776", "Americana", null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        PassageiroResponseDTO criado = service.criar(dto);

        // Then
        assertThat(criado.documento()).isEqualTo("XP9988776");
        Passageiro gravado = gravadoPeloSave();
        assertThat(gravado.getTipoDocumento()).isEqualTo("PASSAPORTE");
        assertThat(gravado.getDocumento()).isEqualTo("XP9988776");
    }

    // ----- documento duplicado -----

    @Test
    void cadastroComDocumentoDeOutroPassageiroRetorna400SemGravar() {
        // Given: o mesmo CPF ja existe, gravado sem pontuacao
        when(repository.existsByDocumentoHash(documentoHash.calcular("CPF", "52998224725"))).thenReturn(true);
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Outra", "CPF", CPF_VALIDO, null, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(DocumentoJaCadastradoException.class)
                .hasMessage("Já existe passageiro cadastrado com este CPF.");
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroGravaOHashDoDocumento() {
        // Given
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.criar(new PassageiroRequestDTO("Ana", "CPF", CPF_VALIDO, null, null));

        // Then
        assertThat(gravadoPeloSave().getDocumentoHash()).isEqualTo(documentoHash.calcular("CPF", CPF_VALIDO));
    }

    @Test
    void putComDocumentoDeOutroPassageiroRetorna400SemGravar() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "RG", "12345")));
        when(repository.existsByDocumentoHashAndIdNot(documentoHash.calcular("CPF", CPF_VALIDO), 1L)).thenReturn(true);
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", CPF_VALIDO, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto)).isInstanceOf(DocumentoJaCadastradoException.class);
        verify(repository, never()).flush();
    }

    @Test
    void putMantendoOProprioDocumentoNaoContaComoDuplicado() {
        // Given: so o proprio registro tem o documento (a consulta ignora o id 1)
        Passageiro ana = passageiro(1L, null, "Ana", "CPF", CPF_VALIDO);
        when(repository.findById(1L)).thenReturn(Optional.of(ana));

        // When
        service.atualizar(1L, new PassageiroRequestDTO("Ana Paula", "CPF", CPF_VALIDO, null, null));

        // Then
        verify(repository).existsByDocumentoHashAndIdNot(documentoHash.calcular("CPF", CPF_VALIDO), 1L);
        assertThat(ana.getDocumentoHash()).isEqualTo(documentoHash.calcular("CPF", CPF_VALIDO));
        verify(repository).flush();
    }

    @Test
    void patchQueTrocaODocumentoPeloDeOutroPassageiroRetorna400() {
        // Given
        Passageiro ana = passageiro(1L, null, "Ana", "CPF", "100.000.001-08");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.existsByDocumentoHashAndIdNot(documentoHash.calcular("CPF", CPF_VALIDO), 1L)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, new PassageiroRequestDTO(null, null, CPF_VALIDO, null, null)))
                .isInstanceOf(DocumentoJaCadastradoException.class);
        verify(repository, never()).flush();
    }

    @Test
    void patchSoDeNomeNemConsultaDuplicado() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "CPF", CPF_VALIDO)));

        // When
        service.atualizarParcial(1L, new PassageiroRequestDTO("Ana Maria", null, null, null, null));

        // Then
        verify(repository, never()).existsByDocumentoHashAndIdNot(any(), any());
    }

    // ----- perfil: documento mascarado ou completo -----

    @Test
    void patchQueTrocaSoOTipoParaCpfValidaONumeroQueJaEstava() {
        // Given
        Passageiro existente = passageiro(1L, null, "Ana", "RG", "123");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        PassageiroRequestDTO patch = new PassageiroRequestDTO(null, "CPF", null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, patch))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF inválido");
        verify(repository, never()).flush();
    }

    @Test
    void motoristaRecebeODocumentoMascarado() {
        // Given
        logarComo("MOTORISTA");
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "CPF", CPF_VALIDO)));

        // When
        PassageiroResponseDTO resposta = service.buscarPorId(1L);

        // Then
        assertThat(resposta.documento()).isEqualTo("************25");
        assertThat(resposta.nome()).isEqualTo("Ana");
    }

    @Test
    void atendenteRecebeODocumentoCompleto() {
        // Given
        logarComo("ATENDENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "CPF", CPF_VALIDO)));

        // When
        PassageiroResponseDTO resposta = service.buscarPorId(1L);

        // Then
        assertThat(resposta.documento()).isEqualTo(CPF_VALIDO);
    }

    // ----- leitura -----

    @Test
    void listarComFiltroDevolveAPaginaDoRepositorio() {
        // Given
        Pageable pagina = PageRequest.of(0, 5);
        Passageiro ana = passageiro(1L, null, "Ana", "RG", "12345");
        when(repository.findAll(ArgumentMatchers.<Specification<Passageiro>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ana), pagina, 1));

        // When
        PaginaResponseDTO<PassageiroResponseDTO> resultado = service.listar(new FiltroPassageiroDTO("an", null), pagina);

        // Then
        assertThat(resultado.conteudo()).extracting(PassageiroResponseDTO::nome).containsExactly("Ana");
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void buscarPorNacionalidadeDevolveAPaginaDoRepositorio() {
        // Given
        Pageable pagina = PageRequest.of(0, 5);
        Passageiro ana = passageiro(1L, null, "Ana", "RG", "12345");
        when(repository.findByNacionalidadeIgnoreCase("argentina", pagina))
                .thenReturn(new PageImpl<>(List.of(ana), pagina, 1));

        // When
        PaginaResponseDTO<PassageiroResponseDTO> resultado = service.buscarPorNacionalidade("argentina", pagina);

        // Then
        assertThat(resultado.conteudo()).extracting(PassageiroResponseDTO::nome).containsExactly("Ana");
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void listarPorTransferDevolveOsPassageirosDoTransfer() {
        // Given
        Passageiro ana = passageiro(1L, null, "Ana", "RG", "12345");
        when(repository.findByTransferId(7L)).thenReturn(List.of(ana));

        // When
        List<PassageiroResponseDTO> resultado = service.listarPorTransfer(7L);

        // Then
        assertThat(resultado).extracting(PassageiroResponseDTO::nome).containsExactly("Ana");
    }

    @Test
    void buscarPorIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L))
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
    }

    // ----- PUT -----

    @Test
    void putEmIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "RG", "1", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto))
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void putComVersaoDesatualizadaLancaConflitoSemAlterarOPassageiro() {
        // Given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Outro Nome", "RG", "1", null, 1L);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto))
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(ana.getNome()).isEqualTo("Ana");
        verify(repository, never()).flush();
    }

    @Test
    void putComCpfInvalidoLancaRegraDeNegocioSemAlterarOPassageiro() {
        // Given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", "123", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF inválido");
        assertThat(ana.getDocumento()).isEqualTo("12345");
        verify(repository, never()).flush();
    }

    @Test
    void putValidoAtualizaTodosOsCamposEGravaAgora() {
        // Given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana Paula", "CPF", CPF_VALIDO, "Brasileira", 2L);

        // When
        PassageiroResponseDTO salvo = service.atualizar(1L, dto);

        // Then
        assertThat(salvo.nome()).isEqualTo("Ana Paula");
        assertThat(salvo.tipoDocumento()).isEqualTo("CPF");
        assertThat(salvo.documento()).isEqualTo(CPF_VALIDO);
        assertThat(salvo.nacionalidade()).isEqualTo("Brasileira");
        assertThat(ana.getNome()).isEqualTo("Ana Paula");
        verify(repository).flush();
    }

    // ----- PATCH -----

    @Test
    void patchSoDeNomeNaoRevalidaODocumentoJaGravado() {
        // Given: cpf antigo invalido ja gravado
        Passageiro ana = passageiro(1L, null, "Ana", "CPF", "123");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO patch = new PassageiroRequestDTO("Ana Maria", null, null, null, null);

        // When
        PassageiroResponseDTO resposta = service.atualizarParcial(1L, patch);

        // Then
        assertThat(resposta.nome()).isEqualTo("Ana Maria");
        assertThat(resposta.documento()).isEqualTo("123");
        assertThat(resposta.tipoDocumento()).isEqualTo("CPF");
        verify(repository).flush();
    }

    @Test
    void patchDeDocumentoENacionalidadeCorrigeOCpfEMantemONome() {
        // Given
        Passageiro ana = passageiro(1L, null, "Ana", "CPF", "123");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO patch = new PassageiroRequestDTO(null, null, CPF_VALIDO, "Argentina", null);

        // When
        PassageiroResponseDTO corrigido = service.atualizarParcial(1L, patch);

        // Then
        assertThat(corrigido.documento()).isEqualTo(CPF_VALIDO);
        assertThat(corrigido.nacionalidade()).isEqualTo("Argentina");
        assertThat(corrigido.nome()).isEqualTo("Ana");
        verify(repository).flush();
    }

    @Test
    void patchEmIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        PassageiroRequestDTO patch = new PassageiroRequestDTO("X", null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, patch))
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void patchComVersaoDesatualizadaLancaConflitoSemAlterarOPassageiro() {
        // Given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO patch = new PassageiroRequestDTO("Outro Nome", null, null, null, 1L);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, patch))
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(ana.getNome()).isEqualTo("Ana");
        verify(repository, never()).flush();
    }

    // ----- exclusao -----

    @Test
    void excluirInexistenteLanca404SemApagarNada() {
        // Given
        when(repository.existsById(9L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L))
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).deleteById(any());
    }

    @Test
    void excluirExistenteApagaOPassageiro() {
        // Given
        when(repository.existsById(1L)).thenReturn(true);

        // When
        service.deletar(1L);

        // Then
        verify(repository).deleteById(1L);
    }
}