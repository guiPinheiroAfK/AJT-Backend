package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private static UsuarioRequestDTO dto(String nome, String username, String senha, Role role, Boolean ativo, Long motoristaId, Long versao) {
        return new UsuarioRequestDTO(nome, username, senha, role, ativo, motoristaId, versao);
    }

    @Test
    void cadastroRespeitaOAtivoInformado() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.criar(dto("Ana", "ana", "senha12345", Role.ATENDENTE, true, null, null)).ativo()).isTrue();
        assertThat(service.criar(dto("Bia", "bia", "senha12345", Role.ATENDENTE, false, null, null)).ativo()).isFalse();
    }

    @Test
    void listaEBuscas() {
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(maria()), pagina, 1));
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.findByUsername("ninguem")).thenReturn(Optional.empty());

        assertThat(service.listarTodos(pagina).conteudo()).hasSize(1);
        assertThat(service.buscarPorId(2L).username()).isEqualTo("maria");
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(UsuarioNaoEncontradoException.class);
        assertThatThrownBy(() -> service.buscarPorUsername("ninguem")).isInstanceOf(UsuarioNaoEncontradoException.class)
                .hasMessageContaining("ninguem");
    }

    @Test
    void cadastroComSenhaEmBrancoLanca400ESemAtivoNasceAtivo() {
        assertThatThrownBy(() -> service.criar(dto("Ana", "ana", "   ", Role.ATENDENTE, null, null, null)))
                .hasMessageContaining("Senha é obrigatória");

        when(repository.existsByUsername("ana")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var criada = service.criar(dto("Ana", "ana", "senha12345", Role.ATENDENTE, null, null, null));
        assertThat(criada.ativo()).isTrue();
        assertThat(criada.trocarSenha()).isTrue();

        assertThat(service.criar(dto("Bia", "bia", "senha12345", Role.ATENDENTE, false, null, null)).ativo()).isFalse();
    }

    @Test
    void edicaoDeUsuarioInexistenteLanca404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(9L, dto("X", "x12", null, Role.ATENDENTE, null, null, null)))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto(null, null, null, null, null, null, null)))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void versaoVelhaLanca409() {
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        assertThatThrownBy(() -> service.atualizar(2L, dto("Maria", "maria", null, Role.ATENDENTE, null, null, 0L)))
                .isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void putCompletoComMesmoUsernameMesmoPerfilESenhaEmBranco() {
        Usuario existente = maria();
        when(repository.findById(2L)).thenReturn(Optional.of(existente));

        var salvo = service.atualizar(2L, dto("Maria Souza", "maria", "", Role.ATENDENTE, true, null, 1L));

        assertThat(salvo.nome()).isEqualTo("Maria Souza");
        assertThat(existente.getSenha()).isEqualTo("hash"); // senha em branco nao mexe na atual
        verify(repository, never()).existsByUsername(any()); // mesmo username: nem consulta
    }

    @Test
    void putTrocandoPerfilEReativandoDeOutroUsuario() {
        Usuario inativo = maria();
        inativo.setAtivo(false);
        when(repository.findById(2L)).thenReturn(Optional.of(inativo));
        when(repository.existsByUsername("maria.s")).thenReturn(false);

        var salvo = service.atualizar(2L, dto("Maria", "maria.s", "novasenha1", Role.GERENTE, true, null, null));

        assertThat(salvo.role()).isEqualTo(Role.GERENTE);
        assertThat(salvo.ativo()).isTrue();
        assertThat(salvo.username()).isEqualTo("maria.s");
        assertThat(salvo.trocarSenha()).isTrue(); // senha definida pelo admin: troca no proximo login
    }

    @Test
    void trocarAPropriaSenhaPelaTelaNaoObrigaTrocaDeNovo() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("maria", null, List.of()));
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        assertThat(service.atualizarParcial(2L, dto(null, null, "minhanova1", null, null, null, null)).trocarSenha()).isFalse();
    }

    @Test
    void patchDeUsernameEDeMotorista() {
        Usuario motorista = maria();
        motorista.setRole(Role.MOTORISTA);
        when(repository.findById(2L)).thenReturn(Optional.of(motorista));
        when(repository.existsByUsername("carlos")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).nome("Carlos").build()));
        // o mesmo usuario ja estava ligado a este motorista: religar nao e duplicidade
        when(repository.findByMotoristaId(5L)).thenReturn(Optional.of(motorista));

        var salvo = service.atualizarParcial(2L, dto(null, "carlos", null, null, null, 5L, null));

        assertThat(salvo.username()).isEqualTo("carlos");
        assertThat(salvo.motoristaId()).isEqualTo(5L);
    }

    @Test
    void motoristaInexistenteNoVinculoLanca404() {
        Usuario motorista = maria();
        motorista.setRole(Role.MOTORISTA);
        when(repository.findById(2L)).thenReturn(Optional.of(motorista));
        when(motoristaRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarParcial(2L, dto(null, null, null, null, null, 77L, null)))
                .isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void ativoIgualAoAtualNaoMudaNadaEExclusaoDeOutroApaga() {
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        assertThat(service.atualizarParcial(2L, dto(null, null, null, Role.ATENDENTE, true, null, null)).ativo()).isTrue();

        service.deletar(2L);
        verify(repository).delete(any(Usuario.class));
    }
}
