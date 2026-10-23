package com.AJTBackend.repository;

import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/*
 * o que testa: a busca de usuario pelo username (achar e nao achar), a
 * checagem de existencia, a busca de quem esta vinculado a um cadastro de
 * motorista (HU07, V11) e as constraints de username unico e de no maximo
 * um usuario por motorista.
 *
 * como rodar: precisa de docker (herda de RepositorioTestBase). "mvn test -Dtest=UsuarioRepositoryTest".
 *
 * por que existe: o login depende de findByUsername e o filtro das OS do
 * motorista depende do vinculo por motorista_id. o caso triste (usuario
 * inexistente, motorista sem vinculo) e o que impede a lista de vazar dados.
 */
class UsuarioRepositoryTest extends RepositorioTestBase {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Test
    void deveAcharUsuario_quandoUsernameExiste() {
        // given
        usuarioRepository.save(usuario("ana", Role.ATENDENTE, null));

        // when
        Optional<Usuario> achado = usuarioRepository.findByUsername("ana");

        // then
        assertThat(achado).isPresent();
        assertThat(achado.get().getRole()).isEqualTo(Role.ATENDENTE);
    }

    @Test
    void deveRetornarVazio_quandoUsernameNaoExiste() {
        // given
        usuarioRepository.save(usuario("ana", Role.ATENDENTE, null));

        // when
        Optional<Usuario> achado = usuarioRepository.findByUsername("inexistente");

        // then
        assertThat(achado).isEmpty();
    }

    @Test
    void deveRetornarTrue_quandoUsernameJaCadastrado() {
        // given
        usuarioRepository.save(usuario("ana", Role.ATENDENTE, null));

        // when
        boolean existe = usuarioRepository.existsByUsername("ana");

        // then
        assertThat(existe).isTrue();
    }

    @Test
    void deveRetornarFalse_quandoUsernameNaoCadastrado() {
        // given
        usuarioRepository.save(usuario("ana", Role.ATENDENTE, null));

        // when
        boolean existe = usuarioRepository.existsByUsername("bruno");

        // then
        assertThat(existe).isFalse();
    }

    @Test
    void deveAcharUsuarioVinculadoAoMotorista_quandoHaVinculo() {
        // given
        Motorista motorista = motoristaRepository.save(Motorista.builder().nome("Joao").cnh("cnh-100").build());
        usuarioRepository.save(usuario("joao", Role.MOTORISTA, motorista));

        // when
        Optional<Usuario> achado = usuarioRepository.findByMotoristaId(motorista.getId());

        // then
        assertThat(achado).isPresent();
        assertThat(achado.get().getUsername()).isEqualTo("joao");
    }

    @Test
    void deveRetornarVazio_quandoMotoristaSemUsuarioVinculado() {
        // given
        Motorista motorista = motoristaRepository.save(Motorista.builder().nome("Maria").cnh("cnh-200").build());

        // when
        Optional<Usuario> achado = usuarioRepository.findByMotoristaId(motorista.getId());

        // then
        assertThat(achado).isEmpty();
    }

    @Test
    void deveRecusarUsernameDuplicado_quandoOutroUsuarioJaUsaOMesmo() {
        // given
        usuarioRepository.saveAndFlush(usuario("ana", Role.ATENDENTE, null));

        // when
        Throwable erro = catchThrowable(() -> usuarioRepository.saveAndFlush(usuario("ana", Role.GERENTE, null)));

        // then
        assertThat(erro).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRecusarSegundoVinculo_quandoMotoristaJaTemUsuario() {
        // given
        Motorista motorista = motoristaRepository.save(Motorista.builder().nome("Joao").cnh("cnh-100").build());
        usuarioRepository.saveAndFlush(usuario("joao", Role.MOTORISTA, motorista));

        // when
        Throwable erro = catchThrowable(() -> usuarioRepository.saveAndFlush(usuario("joao2", Role.MOTORISTA, motorista)));

        // then
        assertThat(erro).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Usuario usuario(String username, Role role, Motorista motorista) {
        return Usuario.builder()
                .nome("Usuario " + username)
                .username(username)
                .senha("hash-qualquer")
                .role(role)
                .motorista(motorista)
                .build();
    }
}
