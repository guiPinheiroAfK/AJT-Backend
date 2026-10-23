package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.UsernameJaCadastradoException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
 * o que testa: as regras sensiveis do UsuarioService: senha sempre em hash,
 * username unico, senha redefinida pelo admin derrubando as sessoes e obrigando
 * troca, as travas da propria conta (nao se excluir, nao se rebaixar, nao se
 * desativar) e o vinculo do usuario MOTORISTA com o cadastro de motorista (HU07).
 *
 * como rodar: teste unitario com repositorios mockados e BCrypt de verdade
 * (custo baixo); o usuario logado e simulado no SecurityContextHolder. sem
 * spring, sem banco. "mvn test".
 *
 * por que existe: um admin que se rebaixa ou se desativa por engano tranca o
 * sistema inteiro; e uma senha gravada em texto puro e vazamento garantido.
 * o restante do CRUD fica no UsuarioServiceCrudTest.
 */
class UsuarioServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final MotoristaRepository motoristaRepository = mock(MotoristaRepository.class);
    private final UsuarioService service = new UsuarioService(usuarioRepository, passwordEncoder, new UsuarioLogado(), motoristaRepository);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private static Usuario usuario(Long id, String username, Role role) {
        return Usuario.builder().id(id).nome("Nome").username(username).senha("hash").role(role)
                .ativo(true).trocarSenha(false).build();
    }

    private static UsuarioRequestDTO cadastro(String username, String senha, Role role, Long motoristaId) {
        return new UsuarioRequestDTO("Nome", username, senha, role, null, motoristaId, null);
    }

    private static UsuarioRequestDTO patchSenha(String senha) {
        return new UsuarioRequestDTO(null, null, senha, null, null, null, null);
    }

    private static UsuarioRequestDTO patchRole(Role role) {
        return new UsuarioRequestDTO(null, null, null, role, null, null, null);
    }

    private static UsuarioRequestDTO patchAtivo(boolean ativo) {
        return new UsuarioRequestDTO(null, null, null, null, ativo, null, null);
    }

    private Usuario gravadoPeloSave() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }

    // devolve o proprio usuario logado ("admin", id 1) ja cadastrado
    private Usuario adminLogadoEditandoASiMesmo() {
        logadoComo("admin");
        Usuario admin = usuario(1L, "admin", Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        return admin;
    }

    // devolve a maria (id 2) ja cadastrada, com o admin logado
    private Usuario adminLogadoEditandoAMaria() {
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));
        return maria;
    }

    // ----- cadastro -----

    @ParameterizedTest(name = "senha \"{0}\" e recusada")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveLancarRegraNegocioSemGravar_quandoCadastroNaoTemSenha(String senha) {
        // given
        UsuarioRequestDTO dto = cadastro("maria", senha, Role.ATENDENTE, null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Senha");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveGravarOHashENuncaOTextoPuro_quandoCriaUsuario() {
        // given
        when(usuarioRepository.existsByUsername("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.criar(cadastro("maria", "senha123", Role.ATENDENTE, null));

        // then
        Usuario gravado = gravadoPeloSave();
        assertThat(gravado.getSenha()).isNotEqualTo("senha123");
        assertThat(passwordEncoder.matches("senha123", gravado.getSenha())).isTrue();
    }

    @Test
    void deveGravarOsDadosEObrigarTrocaNoPrimeiroAcesso_quandoCriaUsuario() {
        // given
        when(usuarioRepository.existsByUsername("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        UsuarioResponseDTO criado = service.criar(cadastro("maria", "senha123", Role.ATENDENTE, null));

        // then
        assertThat(criado.username()).isEqualTo("maria");
        assertThat(criado.role()).isEqualTo(Role.ATENDENTE);
        assertThat(criado.trocarSenha()).isTrue();
        assertThat(criado.ativo()).isTrue();
        assertThat(criado.motoristaId()).isNull();
    }

    @Test
    void deveGravarInativo_quandoCadastroTrazAtivoFalso() {
        // given
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        UsuarioRequestDTO dto = new UsuarioRequestDTO("Maria", "maria", "senha123", Role.ATENDENTE, false, null, null);

        // when
        UsuarioResponseDTO criado = service.criar(dto);

        // then
        assertThat(criado.ativo()).isFalse();
        assertThat(gravadoPeloSave().getAtivo()).isFalse();
    }

    @Test
    void deveLancarUsernameJaCadastradoSemGravar_quandoUsernameJaExiste() {
        // given
        when(usuarioRepository.existsByUsername("maria")).thenReturn(true);
        UsuarioRequestDTO dto = cadastro("maria", "senha123", Role.ATENDENTE, null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(UsernameJaCadastradoException.class)
                .hasMessageContaining("maria");
        verify(usuarioRepository, never()).save(any());
    }

    // ----- username no PUT -----

    @Test
    void deveLancarUsernameJaCadastradoSemAlterar_quandoPutTrocaParaUsernameDeOutro() {
        // given
        Usuario maria = usuario(1L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(maria));
        when(usuarioRepository.existsByUsername("joao")).thenReturn(true);
        UsuarioRequestDTO dto = new UsuarioRequestDTO("Maria", "joao", null, Role.ATENDENTE, null, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(UsernameJaCadastradoException.class)
                .hasMessageContaining("joao");
        assertThat(maria.getUsername()).isEqualTo("maria");
        verify(usuarioRepository, never()).flush();
    }

    // ----- senha redefinida -----

    @Test
    void deveDerrubarSessoesEObrigarTroca_quandoAdminRedefineSenhaDeOutro() {
        // given
        Usuario maria = adminLogadoEditandoAMaria();

        // when
        service.atualizarParcial(2L, patchSenha("nova-senha-1"));

        // then
        assertThat(maria.getSenhaAlteradaEm()).isNotNull();
        assertThat(maria.getTrocarSenha()).isTrue();
        assertThat(passwordEncoder.matches("nova-senha-1", maria.getSenha())).isTrue();
        verify(usuarioRepository).flush();
    }

    @Test
    void naoDeveObrigarTrocaDeNovo_quandoUsuarioRedefineAPropriaSenha() {
        // given
        logadoComo("maria");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        maria.setTrocarSenha(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        // when
        service.atualizarParcial(2L, patchSenha("nova-senha-1"));

        // then
        assertThat(maria.getTrocarSenha()).isFalse();
        assertThat(maria.getSenhaAlteradaEm()).isNotNull();
        assertThat(passwordEncoder.matches("nova-senha-1", maria.getSenha())).isTrue();
    }

    @ParameterizedTest(name = "senha \"{0}\" no patch nao muda nada")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveManterASenhaAtual_quandoPatchVemSemSenhaOuEmBranco(String senha) {
        // given
        Usuario maria = adminLogadoEditandoAMaria();

        // when
        service.atualizarParcial(2L, patchSenha(senha));

        // then
        assertThat(maria.getSenha()).isEqualTo("hash");
        assertThat(maria.getSenhaAlteradaEm()).isNull();
        assertThat(maria.getTrocarSenha()).isFalse();
    }

    // ----- travas de auto-protecao -----

    @Test
    void deveLancarRegraNegocioSemApagar_quandoUsuarioTentaSeExcluir() {
        // given
        adminLogadoEditandoASiMesmo();

        // when
        Throwable erro = catchThrowable(() -> service.deletar(1L));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio usuário");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void deveApagar_quandoAdminExcluiOutroUsuario() {
        // given
        Usuario maria = adminLogadoEditandoAMaria();

        // when
        service.deletar(2L);

        // then
        verify(usuarioRepository).delete(maria);
    }

    @Test
    void deveLancarUsuarioNaoEncontradoSemApagar_quandoExcluiIdInexistente() {
        // given
        logadoComo("admin");
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro)
                .isInstanceOf(UsuarioNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void deveLancarRegraNegocioSemAlterar_quandoUsuarioTentaRebaixarOProprioPerfil() {
        // given
        Usuario admin = adminLogadoEditandoASiMesmo();

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patchRole(Role.ATENDENTE)));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio perfil");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        verify(usuarioRepository, never()).flush();
    }

    @Test
    void naoDeveLancarErro_quandoUsuarioMandaOMesmoPerfilQueJaTem() {
        // given
        adminLogadoEditandoASiMesmo();

        // when
        UsuarioResponseDTO resposta = service.atualizarParcial(1L, patchRole(Role.ADMIN));

        // then
        assertThat(resposta.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void deveMudarOPerfil_quandoAdminAlteraPerfilDeOutro() {
        // given
        Usuario maria = adminLogadoEditandoAMaria();

        // when
        UsuarioResponseDTO resposta = service.atualizarParcial(2L, patchRole(Role.ADMIN));

        // then
        assertThat(resposta.role()).isEqualTo(Role.ADMIN);
        assertThat(maria.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void deveLancarRegraNegocioSemAlterar_quandoUsuarioTentaSeDesativar() {
        // given
        Usuario admin = adminLogadoEditandoASiMesmo();

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patchAtivo(false)));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desativar");
        assertThat(admin.getAtivo()).isTrue();
        verify(usuarioRepository, never()).flush();
    }

    @Test
    void deveDesativar_quandoAdminDesativaOutro() {
        // given
        Usuario maria = adminLogadoEditandoAMaria();

        // when
        UsuarioResponseDTO resposta = service.atualizarParcial(2L, patchAtivo(false));

        // then
        assertThat(resposta.ativo()).isFalse();
        assertThat(maria.getAtivo()).isFalse();
    }

    // ----- HU07: vinculo com o cadastro de motorista -----

    @Test
    void deveLigarAoCadastroDeMotorista_quandoCriaUsuarioMotorista() {
        // given
        Motorista carlos = Motorista.builder().id(5L).nome("Carlos").build();
        when(usuarioRepository.existsByUsername("carlos")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(carlos));
        when(usuarioRepository.findByMotoristaId(5L)).thenReturn(Optional.empty());
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        UsuarioResponseDTO criado = service.criar(cadastro("carlos", "senha123", Role.MOTORISTA, 5L));

        // then
        assertThat(criado.motoristaId()).isEqualTo(5L);
        assertThat(gravadoPeloSave().getMotorista()).isSameAs(carlos);
    }

    @Test
    void deveLancarRegraNegocioSemConsultar_quandoPerfilNaoMotoristaTentaSeLigarAMotorista() {
        // given
        when(usuarioRepository.existsByUsername("ana")).thenReturn(false);
        UsuarioRequestDTO dto = cadastro("ana", "senha123", Role.ATENDENTE, 5L);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("perfil Motorista");
        verify(motoristaRepository, never()).findById(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveLancarMotoristaNaoEncontradoSemGravar_quandoLigaAMotoristaInexistente() {
        // given
        when(motoristaRepository.findById(5L)).thenReturn(Optional.empty());
        UsuarioRequestDTO dto = cadastro("carlos", "senha123", Role.MOTORISTA, 5L);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(MotoristaNaoEncontradoException.class)
                .hasMessageContaining("5");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveLancarRegraNegocioSemGravar_quandoMotoristaJaTemOutroUsuario() {
        // given
        when(usuarioRepository.existsByUsername("carlos2")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).nome("Carlos").build()));
        when(usuarioRepository.findByMotoristaId(5L))
                .thenReturn(Optional.of(Usuario.builder().id(3L).username("carlos").build()));
        UsuarioRequestDTO dto = cadastro("carlos2", "senha123", Role.MOTORISTA, 5L);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("carlos");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void naoDeveContarComoConflito_quandoReenviaOMotoristaDoProprioUsuario() {
        // given
        Motorista carlos = Motorista.builder().id(5L).nome("Carlos").build();
        Usuario usuario = usuario(2L, "carlos", Role.MOTORISTA);
        usuario.setMotorista(carlos);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(carlos));
        when(usuarioRepository.findByMotoristaId(5L)).thenReturn(Optional.of(usuario));
        UsuarioRequestDTO patch = new UsuarioRequestDTO(null, null, null, null, null, 5L, null);

        // when
        UsuarioResponseDTO resposta = service.atualizarParcial(2L, patch);

        // then
        assertThat(resposta.motoristaId()).isEqualTo(5L);
    }

    @Test
    void deveDesfazerOVinculo_quandoUsuarioDeixaDeSerMotorista() {
        // given
        Usuario usuario = usuario(2L, "carlos", Role.MOTORISTA);
        usuario.setMotorista(Motorista.builder().id(5L).build());
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));

        // when
        UsuarioResponseDTO atualizado = service.atualizarParcial(2L, patchRole(Role.ATENDENTE));

        // then
        assertThat(atualizado.motoristaId()).isNull();
        assertThat(atualizado.role()).isEqualTo(Role.ATENDENTE);
        assertThat(usuario.getMotorista()).isNull();
    }
}
