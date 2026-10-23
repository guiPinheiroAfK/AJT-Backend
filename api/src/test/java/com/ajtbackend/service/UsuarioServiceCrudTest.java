package com.ajtbackend.service;

import com.ajtbackend.config.UsuarioLogado;
import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.dto.UsuarioRequestDTO;
import com.ajtbackend.dto.UsuarioResponseDTO;
import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.MotoristaNaoEncontradoException;
import com.ajtbackend.exception.UsuarioNaoEncontradoException;
import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.Usuario;
import com.ajtbackend.model.enums.Role;
import com.ajtbackend.repository.MotoristaRepository;
import com.ajtbackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
 * o que testa: o restante das regras do UsuarioService (o UsuarioServiceTest cobre as
 * protecoes da propria conta e o vinculo basico) — buscas inexistentes (404), cadastro
 * ativo por padrao, PUT completo, PATCH de username e de motorista, manter o mesmo perfil
 * e o mesmo username, religar o mesmo motorista ao mesmo usuario, motorista inexistente
 * (404), senha em branco que nao muda nada, a propria senha sem obrigar troca, versao
 * velha (409) e exclusao.
 *
 * como rodar: teste unitario com repositorios mockados e BCrypt de verdade (custo baixo);
 * o usuario logado e simulado no SecurityContextHolder. sem spring, sem banco. "mvn test".
 *
 * por que existe: e a tela de quem controla o acesso a todo o resto do sistema.
 */
class UsuarioServiceCrudTest {

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final MotoristaRepository motoristaRepository = mock(MotoristaRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UsuarioService service = new UsuarioService(repository, encoder, new UsuarioLogado(), motoristaRepository);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Usuario maria() {
        return Usuario.builder().id(2L).versao(1L).nome("Maria").username("maria").senha("hash")
                .role(Role.ATENDENTE).ativo(true).trocarSenha(false).build();
    }

    private static Usuario mariaMotorista() {
        Usuario motorista = maria();
        motorista.setRole(Role.MOTORISTA);
        return motorista;
    }

    private static UsuarioRequestDTO dto(String nome, String username, String senha, Role role, Boolean ativo, Long motoristaId, Long versao) {
        return new UsuarioRequestDTO(nome, username, senha, role, ativo, motoristaId, versao);
    }

    private void usuarioNaoExiste() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
    }

    // ----- cadastro -----

    @ParameterizedTest(name = "ativo = {0} e respeitado")
    @ValueSource(booleans = {true, false})
    void deveRespeitarOAtivo_quandoCadastroInformaAtivo(boolean ativo) {
        // given
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        UsuarioResponseDTO criado = service.criar(dto("Ana", "ana", "senha12345", Role.ATENDENTE, ativo, null, null));

        // then
        assertThat(criado.ativo()).isEqualTo(ativo);
    }

    @Test
    void deveLancarErroDeSenhaObrigatoria_quandoCadastroTemSenhaEmBranco() {
        // when
        Throwable erro = catchThrowable(() -> service.criar(dto("Ana", "ana", "   ", Role.ATENDENTE, null, null, null)));

        // then
        assertThat(erro).hasMessageContaining("Senha é obrigatória");
        verify(repository, never()).save(any());
    }

    @Test
    void deveNascerAtivoEObrigarTrocaDeSenha_quandoCadastroNaoInformaAtivo() {
        // given
        when(repository.existsByUsername("ana")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        UsuarioResponseDTO criada = service.criar(dto("Ana", "ana", "senha12345", Role.ATENDENTE, null, null, null));

        // then
        assertThat(criada.ativo()).isTrue();
        assertThat(criada.trocarSenha()).isTrue();
    }

    // ----- leitura -----

    @Test
    void deveDevolverAPagina_quandoListaTodos() {
        // given
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(maria()), pagina, 1));

        // when
        PaginaResponseDTO<UsuarioResponseDTO> resultado = service.listarTodos(pagina);

        // then
        assertThat(resultado.conteudo()).hasSize(1);
    }

    @Test
    void deveDevolverOUsuario_quandoBuscaIdExistente() {
        // given
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // when
        UsuarioResponseDTO resposta = service.buscarPorId(2L);

        // then
        assertThat(resposta.username()).isEqualTo("maria");
    }

    @Test
    void deveLancarUsuarioNaoEncontrado_quandoBuscaIdInexistente() {
        // given
        usuarioNaoExiste();

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void deveLancarUsuarioNaoEncontrado_quandoBuscaUsernameInexistente() {
        // given
        when(repository.findByUsername("ninguem")).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorUsername("ninguem"));

        // then
        assertThat(erro).isInstanceOf(UsuarioNaoEncontradoException.class).hasMessageContaining("ninguem");
    }

    // ----- edicao e exclusao de inexistente -----

    @Test
    void deveLancarUsuarioNaoEncontrado_quandoPutEmIdInexistente() {
        // given
        usuarioNaoExiste();

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, dto("X", "x12", null, Role.ATENDENTE, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void deveLancarUsuarioNaoEncontrado_quandoPatchEmIdInexistente() {
        // given
        usuarioNaoExiste();

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(9L, dto(null, null, null, null, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void deveLancarUsuarioNaoEncontrado_quandoExcluiIdInexistente() {
        // given
        usuarioNaoExiste();

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    // ----- edicao -----

    @Test
    void deveLancarConflitoDeVersao_quandoPutTrazVersaoVelha() {
        // given
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(2L, dto("Maria", "maria", null, Role.ATENDENTE, null, null, 0L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveManterSenhaENaoConsultarUsername_quandoPutMantemUsernamePerfilESenhaEmBranco() {
        // given
        Usuario existente = maria();
        when(repository.findById(2L)).thenReturn(Optional.of(existente));

        // when
        UsuarioResponseDTO salvo = service.atualizar(2L, dto("Maria Souza", "maria", "", Role.ATENDENTE, true, null, 1L));

        // then
        assertThat(salvo.nome()).isEqualTo("Maria Souza");
        assertThat(existente.getSenha()).isEqualTo("hash"); // senha em branco nao mexe na atual
        verify(repository, never()).existsByUsername(any()); // mesmo username: nem consulta
    }

    @Test
    void deveTrocarPerfilReativarEObrigarTroca_quandoAdminDefineSenhaDeOutroUsuario() {
        // given
        Usuario inativo = maria();
        inativo.setAtivo(false);
        when(repository.findById(2L)).thenReturn(Optional.of(inativo));
        when(repository.existsByUsername("maria.s")).thenReturn(false);

        // when
        UsuarioResponseDTO salvo = service.atualizar(2L, dto("Maria", "maria.s", "novasenha1", Role.GERENTE, true, null, null));

        // then
        assertThat(salvo.role()).isEqualTo(Role.GERENTE);
        assertThat(salvo.ativo()).isTrue();
        assertThat(salvo.username()).isEqualTo("maria.s");
        assertThat(salvo.trocarSenha()).isTrue(); // senha definida pelo admin: troca no proximo login
        assertThat(encoder.matches("novasenha1", inativo.getSenha())).isTrue();
    }

    @Test
    void naoDeveObrigarTrocaDeNovo_quandoUsuarioTrocaAPropriaSenhaPelaTela() {
        // given
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("maria", null, List.of()));
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // when
        UsuarioResponseDTO salvo = service.atualizarParcial(2L, dto(null, null, "minhanova1", null, null, null, null));

        // then
        assertThat(salvo.trocarSenha()).isFalse();
    }

    @Test
    void deveTrocarUsernameEReligarMotorista_quandoPatchTrazOsDoisEVinculoJaEraDele() {
        // given
        Usuario motorista = mariaMotorista();
        when(repository.findById(2L)).thenReturn(Optional.of(motorista));
        when(repository.existsByUsername("carlos")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).nome("Carlos").build()));
        // o mesmo usuario ja estava ligado a este motorista: religar nao e duplicidade
        when(repository.findByMotoristaId(5L)).thenReturn(Optional.of(motorista));

        // when
        UsuarioResponseDTO salvo = service.atualizarParcial(2L, dto(null, "carlos", null, null, null, 5L, null));

        // then
        assertThat(salvo.username()).isEqualTo("carlos");
        assertThat(salvo.motoristaId()).isEqualTo(5L);
    }

    @Test
    void deveLancarMotoristaNaoEncontrado_quandoPatchLigaMotoristaInexistente() {
        // given
        when(repository.findById(2L)).thenReturn(Optional.of(mariaMotorista()));
        when(motoristaRepository.findById(77L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(2L, dto(null, null, null, null, null, 77L, null)));

        // then
        assertThat(erro).isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void naoDeveMudarNada_quandoPatchTrazAtivoEPerfilIguaisAosAtuais() {
        // given
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // when
        UsuarioResponseDTO salvo = service.atualizarParcial(2L, dto(null, null, null, Role.ATENDENTE, true, null, null));

        // then
        assertThat(salvo.ativo()).isTrue();
        assertThat(salvo.role()).isEqualTo(Role.ATENDENTE);
    }

    @Test
    void deveApagar_quandoExcluiOutroUsuario() {
        // given
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // when
        service.deletar(2L);

        // then
        verify(repository).delete(any(Usuario.class));
    }
}
