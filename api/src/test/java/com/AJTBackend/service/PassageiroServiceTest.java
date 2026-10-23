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
import static org.assertj.core.api.Assertions.catchThrowable;
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
    private static final Pageable PAGINA = PageRequest.of(0, 5);

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

    @ParameterizedTest(name = "\"{0}\" e valido")
    @ValueSource(strings = {"529.982.247-25", "52998224725", "529 982 247 25", "100.000.001-08"})
    void deveAceitarOCpf_quandoDigitosVerificadoresBatem(String cpf) {
        // when
        boolean valido = PassageiroService.cpfValido(cpf);

        // then
        assertThat(valido).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" e invalido")
    @ValueSource(strings = {"123", "529.982.247-24", "529.982.247-15", "111.111.111-11", "5299822472a", ""})
    void deveRecusarOCpf_quandoDigitosOuFormatoEstaoErrados(String cpf) {
        // when
        boolean valido = PassageiroService.cpfValido(cpf);

        // then
        assertThat(valido).isFalse();
    }

    @Test
    void deveRecusarOCpf_quandoEhNulo() {
        // when
        boolean valido = PassageiroService.cpfValido(null);

        // then
        assertThat(valido).isFalse();
    }

    @Test
    void deveDevolverSoAsteriscos_quandoDocumentoEhNulo() {
        // when
        String mascarado = PassageiroService.mascarar(null);

        // then
        assertThat(mascarado).isEqualTo("**");
    }

    @Test
    void deveDevolverSoAsteriscos_quandoDocumentoTemAteDoisCaracteres() {
        // when
        String mascarado = PassageiroService.mascarar("12");

        // then
        assertThat(mascarado).isEqualTo("**");
    }

    @Test
    void deveMostrarSoOsDoisUltimos_quandoDocumentoEhLongo() {
        // when
        String mascarado = PassageiroService.mascarar("XP9988776");

        // then
        assertThat(mascarado).isEqualTo("*******76");
    }

    // ----- cadastro -----

    @Test
    void deveLancarRegraNegocioSemGravar_quandoCadastroTemCpfInvalido() {
        // given
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", "123", "Brasileira", null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF inválido");
        verify(repository, never()).save(any());
    }

    @Test
    void deveGravarOsDadosInformados_quandoCadastroTemCpfValido() {
        // given
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", CPF_VALIDO, "Brasileira", null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        PassageiroResponseDTO criado = service.criar(dto);

        // then
        assertThat(criado.nome()).isEqualTo("Ana");
        assertThat(criado.documento()).isEqualTo(CPF_VALIDO);
        Passageiro gravado = gravadoPeloSave();
        assertThat(gravado.getNome()).isEqualTo("Ana");
        assertThat(gravado.getTipoDocumento()).isEqualTo("CPF");
        assertThat(gravado.getDocumento()).isEqualTo(CPF_VALIDO);
        assertThat(gravado.getNacionalidade()).isEqualTo("Brasileira");
    }

    @Test
    void naoDeveValidarComoCpf_quandoDocumentoEhPassaporte() {
        // given
        PassageiroRequestDTO dto = new PassageiroRequestDTO("John", "PASSAPORTE", "XP9988776", "Americana", null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        PassageiroResponseDTO criado = service.criar(dto);

        // then
        assertThat(criado.documento()).isEqualTo("XP9988776");
        Passageiro gravado = gravadoPeloSave();
        assertThat(gravado.getTipoDocumento()).isEqualTo("PASSAPORTE");
        assertThat(gravado.getDocumento()).isEqualTo("XP9988776");
    }

    // ----- documento duplicado -----

    @Test
    void deveLancarDuplicadoSemGravar_quandoCadastroUsaDocumentoDeOutroPassageiro() {
        // given: o mesmo CPF ja existe, gravado sem pontuacao
        when(repository.existsByDocumentoHash(documentoHash.calcular("CPF", "52998224725"))).thenReturn(true);
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Outra", "CPF", CPF_VALIDO, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(DocumentoJaCadastradoException.class)
                .hasMessage("Já existe passageiro cadastrado com este CPF.");
        verify(repository, never()).save(any());
    }

    @Test
    void deveGravarOHashDoDocumento_quandoCadastroEhValido() {
        // given
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.criar(new PassageiroRequestDTO("Ana", "CPF", CPF_VALIDO, null, null));

        // then
        assertThat(gravadoPeloSave().getDocumentoHash()).isEqualTo(documentoHash.calcular("CPF", CPF_VALIDO));
    }

    @Test
    void deveLancarDuplicadoSemGravar_quandoPutUsaDocumentoDeOutroPassageiro() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "RG", "12345")));
        when(repository.existsByDocumentoHashAndIdNot(documentoHash.calcular("CPF", CPF_VALIDO), 1L)).thenReturn(true);
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", CPF_VALIDO, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto));

        // then
        assertThat(erro).isInstanceOf(DocumentoJaCadastradoException.class);
        verify(repository, never()).flush();
    }

    @Test
    void naoDeveContarComoDuplicado_quandoPutMantemOProprioDocumento() {
        // given: so o proprio registro tem o documento (a consulta ignora o id 1)
        Passageiro ana = passageiro(1L, null, "Ana", "CPF", CPF_VALIDO);
        when(repository.findById(1L)).thenReturn(Optional.of(ana));

        // when
        service.atualizar(1L, new PassageiroRequestDTO("Ana Paula", "CPF", CPF_VALIDO, null, null));

        // then
        verify(repository).existsByDocumentoHashAndIdNot(documentoHash.calcular("CPF", CPF_VALIDO), 1L);
        assertThat(ana.getDocumentoHash()).isEqualTo(documentoHash.calcular("CPF", CPF_VALIDO));
        verify(repository).flush();
    }

    @Test
    void deveLancarDuplicadoSemGravar_quandoPatchTrocaPeloDocumentoDeOutroPassageiro() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "CPF", "100.000.001-08")));
        when(repository.existsByDocumentoHashAndIdNot(documentoHash.calcular("CPF", CPF_VALIDO), 1L)).thenReturn(true);
        PassageiroRequestDTO patch = new PassageiroRequestDTO(null, null, CPF_VALIDO, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch));

        // then
        assertThat(erro).isInstanceOf(DocumentoJaCadastradoException.class);
        verify(repository, never()).flush();
    }

    @Test
    void naoDeveConsultarDuplicado_quandoPatchMudaSoONome() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "CPF", CPF_VALIDO)));

        // when
        service.atualizarParcial(1L, new PassageiroRequestDTO("Ana Maria", null, null, null, null));

        // then
        verify(repository, never()).existsByDocumentoHashAndIdNot(any(), any());
    }

    // ----- perfil: documento mascarado ou completo -----

    @Test
    void deveValidarONumeroQueJaEstava_quandoPatchTrocaSoOTipoParaCpf() {
        // given
        Passageiro existente = passageiro(1L, null, "Ana", "RG", "123");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        PassageiroRequestDTO patch = new PassageiroRequestDTO(null, "CPF", null, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF inválido");
        verify(repository, never()).flush();
    }

    @Test
    void deveMascararODocumento_quandoQuemLeEhMotorista() {
        // given
        logarComo("MOTORISTA");
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "CPF", CPF_VALIDO)));

        // when
        PassageiroResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.documento()).isEqualTo("************25");
        assertThat(resposta.nome()).isEqualTo("Ana");
    }

    @Test
    void deveMostrarODocumentoCompleto_quandoQuemLeEhAtendente() {
        // given
        logarComo("ATENDENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(passageiro(1L, null, "Ana", "CPF", CPF_VALIDO)));

        // when
        PassageiroResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.documento()).isEqualTo(CPF_VALIDO);
    }

    // ----- leitura -----

    @Test
    void deveDevolverAPaginaDoRepositorio_quandoListaComFiltro() {
        // given
        Passageiro ana = passageiro(1L, null, "Ana", "RG", "12345");
        when(repository.findAll(ArgumentMatchers.<Specification<Passageiro>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ana), PAGINA, 1));

        // when
        PaginaResponseDTO<PassageiroResponseDTO> resultado = service.listar(new FiltroPassageiroDTO("an", null), PAGINA);

        // then
        assertThat(resultado.conteudo()).extracting(PassageiroResponseDTO::nome).containsExactly("Ana");
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void deveDevolverAPaginaDoRepositorio_quandoBuscaPorNacionalidade() {
        // given
        Passageiro ana = passageiro(1L, null, "Ana", "RG", "12345");
        when(repository.findByNacionalidadeIgnoreCase("argentina", PAGINA))
                .thenReturn(new PageImpl<>(List.of(ana), PAGINA, 1));

        // when
        PaginaResponseDTO<PassageiroResponseDTO> resultado = service.buscarPorNacionalidade("argentina", PAGINA);

        // then
        assertThat(resultado.conteudo()).extracting(PassageiroResponseDTO::nome).containsExactly("Ana");
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void deveDevolverOsPassageirosDoTransfer_quandoListaPorTransfer() {
        // given
        Passageiro ana = passageiro(1L, null, "Ana", "RG", "12345");
        when(repository.findByTransferId(7L)).thenReturn(List.of(ana));

        // when
        List<PassageiroResponseDTO> resultado = service.listarPorTransfer(7L);

        // then
        assertThat(resultado).extracting(PassageiroResponseDTO::nome).containsExactly("Ana");
    }

    @Test
    void deveLancarPassageiroNaoEncontrado_quandoBuscaIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro)
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
    }

    // ----- PUT -----

    @Test
    void deveLancarPassageiroNaoEncontrado_quandoPutEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "RG", "1", null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void deveLancarConflitoSemAlterar_quandoPutTrazVersaoDesatualizada() {
        // given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Outro Nome", "RG", "1", null, 1L);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(ana.getNome()).isEqualTo("Ana");
        verify(repository, never()).flush();
    }

    @Test
    void deveLancarRegraNegocioSemAlterar_quandoPutTrazCpfInvalido() {
        // given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana", "CPF", "123", null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF inválido");
        assertThat(ana.getDocumento()).isEqualTo("12345");
        verify(repository, never()).flush();
    }

    @Test
    void deveAtualizarTodosOsCamposEGravarNaHora_quandoPutEhValido() {
        // given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO dto = new PassageiroRequestDTO("Ana Paula", "CPF", CPF_VALIDO, "Brasileira", 2L);

        // when
        PassageiroResponseDTO salvo = service.atualizar(1L, dto);

        // then
        assertThat(salvo.nome()).isEqualTo("Ana Paula");
        assertThat(salvo.tipoDocumento()).isEqualTo("CPF");
        assertThat(salvo.documento()).isEqualTo(CPF_VALIDO);
        assertThat(salvo.nacionalidade()).isEqualTo("Brasileira");
        assertThat(ana.getNome()).isEqualTo("Ana Paula");
        verify(repository).flush();
    }

    // ----- PATCH -----

    @Test
    void naoDeveRevalidarODocumentoGravado_quandoPatchMudaSoONome() {
        // given
        // cpf antigo invalido ja gravado
        Passageiro ana = passageiro(1L, null, "Ana", "CPF", "123");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO patch = new PassageiroRequestDTO("Ana Maria", null, null, null, null);

        // when
        PassageiroResponseDTO resposta = service.atualizarParcial(1L, patch);

        // then
        assertThat(resposta.nome()).isEqualTo("Ana Maria");
        assertThat(resposta.documento()).isEqualTo("123");
        assertThat(resposta.tipoDocumento()).isEqualTo("CPF");
        verify(repository).flush();
    }

    @Test
    void deveCorrigirOCpfEManterONome_quandoPatchTrazDocumentoENacionalidade() {
        // given
        Passageiro ana = passageiro(1L, null, "Ana", "CPF", "123");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO patch = new PassageiroRequestDTO(null, null, CPF_VALIDO, "Argentina", null);

        // when
        PassageiroResponseDTO corrigido = service.atualizarParcial(1L, patch);

        // then
        assertThat(corrigido.documento()).isEqualTo(CPF_VALIDO);
        assertThat(corrigido.nacionalidade()).isEqualTo("Argentina");
        assertThat(corrigido.nome()).isEqualTo("Ana");
        verify(repository).flush();
    }

    @Test
    void deveLancarPassageiroNaoEncontrado_quandoPatchEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        PassageiroRequestDTO patch = new PassageiroRequestDTO("X", null, null, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(9L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void deveLancarConflitoSemAlterar_quandoPatchTrazVersaoDesatualizada() {
        // given
        Passageiro ana = passageiro(1L, 2L, "Ana", "RG", "12345");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        PassageiroRequestDTO patch = new PassageiroRequestDTO("Outro Nome", null, null, null, 1L);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(ana.getNome()).isEqualTo("Ana");
        verify(repository, never()).flush();
    }

    // ----- exclusao -----

    @Test
    void deveLancarPassageiroNaoEncontradoSemApagar_quandoExcluiIdInexistente() {
        // given
        when(repository.existsById(9L)).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro)
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deveApagarOPassageiro_quandoExcluiIdExistente() {
        // given
        when(repository.existsById(1L)).thenReturn(true);

        // when
        service.deletar(1L);

        // then
        verify(repository).deleteById(1L);
    }
}
