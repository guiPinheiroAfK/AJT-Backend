package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.UsuarioRepository;
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

    private static void logadoComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    @Test
    void listarTodosDevolveAPaginaConvertida() {
        // Given
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(maria()), pagina, 1));

        // When
        PaginaResponseDTO<UsuarioResponseDTO> resultado = service.listarTodos(pagina);

        // Then
        assertThat(resultado.conteudo()).extracting(UsuarioResponseDTO::username).containsExactly("maria");
    }

    @Test
    void buscaPorIdDevolveOUsuario() {
        // Given
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // When
        UsuarioResponseDTO encontrado = service.buscarPorId(2L);

        // Then
        assertThat(encontrado.username()).isEqualTo("maria");
        assertThat(encontrado.role()).isEqualTo(Role.ATENDENTE);
    }

    @Test
    void buscaPorIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void buscaPorUsernameInexistenteLanca404ComOUsername() {
        // Given
        when(repository.findByUsername("ninguem")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorUsername("ninguem"))
                .isInstanceOf(UsuarioNaoEncontradoException.class)
                .hasMessageContaining("ninguem");
    }

    @Test
    void cadastroComSenhaEmBrancoLanca400SemGravar() {
        // Given
        UsuarioRequestDTO semSenha = dto("Ana", "ana", "   ", Role.ATENDENTE, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(semSenha))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Senha é obrigatória");
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroSemAtivoNasceAtivoEObrigaTrocarSenha() {
        // Given
        when(repository.existsByUsername("ana")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UsuarioResponseDTO criada = service.criar(dto("Ana", "ana", "senha12345", Role.ATENDENTE, null, null, null));

        // Then
        assertThat(criada.ativo()).isTrue();
        assertThat(criada.trocarSenha()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cadastroRespeitaOAtivoInformado(boolean ativo) {
        // Given
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UsuarioResponseDTO criado = service.criar(dto("Ana", "ana", "senha12345", Role.ATENDENTE, ativo, null, null));

        // Then
        assertThat(criado.ativo()).isEqualTo(ativo);
    }

    @Test
    void putDeUsuarioInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        UsuarioRequestDTO dto = dto("X", "x12", null, Role.ATENDENTE, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void patchDeUsuarioInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        UsuarioRequestDTO dto = dto(null, null, null, null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void excluirUsuarioInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(UsuarioNaoEncontradoException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void putComVersaoVelhaLanca409() {
        // Given
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));
        UsuarioRequestDTO versaoVelha = dto("Maria", "maria", null, Role.ATENDENTE, null, null, 0L);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(2L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void putComMesmoUsernameESenhaEmBrancoNaoMexeNaSenhaNemConsultaDuplicidade() {
        // Given
        Usuario existente = maria();
        when(repository.findById(2L)).thenReturn(Optional.of(existente));

        // When
        UsuarioResponseDTO salvo = service.atualizar(2L, dto("Maria Souza", "maria", "", Role.ATENDENTE, true, null, 1L));

        // Then
        assertThat(salvo.nome()).isEqualTo("Maria Souza");
        assertThat(existente.getSenha()).isEqualTo("hash");
        verify(repository, never()).existsByUsername(any());
    }

    @Test
    void putTrocandoPerfilUsernameESenhaDeOutroUsuarioObrigaTrocaNoProximoLogin() {
        // Given
        Usuario inativo = maria();
        inativo.setAtivo(false);
        when(repository.findById(2L)).thenReturn(Optional.of(inativo));
        when(repository.existsByUsername("maria.s")).thenReturn(false);

        // When
        UsuarioResponseDTO salvo = service.atualizar(2L, dto("Maria", "maria.s", "novasenha1", Role.GERENTE, true, null, null));

        // Then
        assertThat(salvo.role()).isEqualTo(Role.GERENTE);
        assertThat(salvo.ativo()).isTrue();
        assertThat(salvo.username()).isEqualTo("maria.s");
        assertThat(salvo.trocarSenha()).isTrue();
        assertThat(encoder.matches("novasenha1", inativo.getSenha())).isTrue();
    }

    @Test
    void trocarAPropriaSenhaPelaTelaNaoObrigaTrocaDeNovo() {
        // Given
        logadoComo("maria");
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // When
        UsuarioResponseDTO salvo = service.atualizarParcial(2L, dto(null, null, "minhanova1", null, null, null, null));

        // Then
        assertThat(salvo.trocarSenha()).isFalse();
    }

    @Test
    void patchTrocaUsernameELigaAoCadastroDeMotorista() {
        // Given
        Usuario motorista = maria();
        motorista.setRole(Role.MOTORISTA);
        when(repository.findById(2L)).thenReturn(Optional.of(motorista));
        when(repository.existsByUsername("carlos")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).nome("Carlos").build()));
        // o mesmo usuario ja estava ligado a este motorista: religar nao e duplicidade
        when(repository.findByMotoristaId(5L)).thenReturn(Optional.of(motorista));

        // When
        UsuarioResponseDTO salvo = service.atualizarParcial(2L, dto(null, "carlos", null, null, null, 5L, null));

        // Then
        assertThat(salvo.username()).isEqualTo("carlos");
        assertThat(salvo.motoristaId()).isEqualTo(5L);
    }

    @Test
    void ligarAMotoristaInexistenteLanca404() {
        // Given
        Usuario motorista = maria();
        motorista.setRole(Role.MOTORISTA);
        when(repository.findById(2L)).thenReturn(Optional.of(motorista));
        when(motoristaRepository.findById(77L)).thenReturn(Optional.empty());
        UsuarioRequestDTO patch = dto(null, null, null, null, null, 77L, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(2L, patch)).isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void patchComAtivoEPerfilIguaisAosAtuaisNaoMudaNada() {
        // Given
        when(repository.findById(2L)).thenReturn(Optional.of(maria()));

        // When
        UsuarioResponseDTO salvo = service.atualizarParcial(2L, dto(null, null, null, Role.ATENDENTE, true, null, null));

        // Then
        assertThat(salvo.ativo()).isTrue();
        assertThat(salvo.role()).isEqualTo(Role.ATENDENTE);
    }

    @Test
    void adminExcluiOutroUsuario() {
        // Given
        logadoComo("admin");
        Usuario maria = maria();
        when(repository.findById(2L)).thenReturn(Optional.of(maria));

        // When
        service.deletar(2L);

        // Then
        verify(repository).delete(maria);
    }
}
