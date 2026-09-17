package com.AJTBackend.service;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.UsernameJaCadastradoException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public PaginaResponseDTO<UsuarioResponseDTO> listarTodos(Pageable pageable) {
        return PaginaResponseDTO.de(usuarioRepository.findAll(pageable), this::toResponseDTO);
    }

    public UsuarioResponseDTO buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));
        return toResponseDTO(usuario);
    }

    public UsuarioResponseDTO buscarPorUsername(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(username));
        return toResponseDTO(usuario);
    }

    @Transactional
    public UsuarioResponseDTO criar(UsuarioRequestDTO dto) {
        if (dto.senha() == null || dto.senha().isBlank()) {
            throw new RegraNegocioException("Senha é obrigatória no cadastro");
        }
        if (usuarioRepository.existsByUsername(dto.username())) {
            log.warn("Tentativa de cadastro com username ja existente: {}", dto.username());
            throw new UsernameJaCadastradoException(dto.username());
        }

        Usuario usuario = Usuario.builder()
                .nome(dto.nome())
                .username(dto.username())
                .senha(passwordEncoder.encode(dto.senha())) // hash, nunca texto plano
                .role(dto.role())
                .ativo(dto.ativo() == null || dto.ativo())
                .trocarSenha(true) // senha definida por outra pessoa: usuario troca no primeiro acesso
                .build();

        Usuario salvo = usuarioRepository.save(usuario);
        log.info("Usuario criado: id={}, username={}, role={}", salvo.getId(), salvo.getUsername(), salvo.getRole());
        return toResponseDTO(salvo);
    }

    @Transactional
    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        validarUsernameDisponivel(usuario, dto.username());
        aplicarRole(usuario, dto.role());
        if (dto.ativo() != null) {
            aplicarAtivo(usuario, dto.ativo());
        }

        usuario.setNome(dto.nome());
        usuario.setUsername(dto.username());
        redefinirSenhaSeInformada(usuario, dto.senha());

        return toResponseDTO(usuario);
    }

    @Transactional
    public UsuarioResponseDTO atualizarParcial(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        if (dto.nome() != null) {
            usuario.setNome(dto.nome());
        }
        if (dto.username() != null) {
            validarUsernameDisponivel(usuario, dto.username());
            usuario.setUsername(dto.username());
        }
        if (dto.role() != null) {
            aplicarRole(usuario, dto.role());
        }
        if (dto.ativo() != null) {
            aplicarAtivo(usuario, dto.ativo());
        }
        redefinirSenhaSeInformada(usuario, dto.senha());

        return toResponseDTO(usuario);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        if (ehUsuarioLogado(usuario)) {
            throw new RegraNegocioException("Você não pode excluir o próprio usuário");
        }

        usuarioRepository.delete(usuario);
        log.info("Usuario removido: id={}, username={}", id, usuario.getUsername());
    }

    private void validarUsernameDisponivel(Usuario usuario, String novoUsername) {
        if (!novoUsername.equals(usuario.getUsername()) && usuarioRepository.existsByUsername(novoUsername)) {
            throw new UsernameJaCadastradoException(novoUsername);
        }
    }

    private void aplicarRole(Usuario usuario, Role novaRole) {
        if (novaRole == usuario.getRole()) {
            return;
        }
        if (ehUsuarioLogado(usuario)) {
            throw new RegraNegocioException("Você não pode alterar o próprio perfil");
        }
        log.info("Role do usuario {} alterada: {} -> {}", usuario.getUsername(), usuario.getRole(), novaRole);
        usuario.setRole(novaRole);
    }

    private void aplicarAtivo(Usuario usuario, boolean ativo) {
        if (!ativo && ehUsuarioLogado(usuario)) {
            throw new RegraNegocioException("Você não pode desativar o próprio usuário");
        }
        if (!usuario.getAtivo().equals(ativo)) {
            log.info("Usuario {} {}", usuario.getUsername(), ativo ? "reativado" : "desativado");
        }
        usuario.setAtivo(ativo);
    }

    // senha redefinida pelo admin: derruba sessoes abertas e obriga troca no proximo login
    private void redefinirSenhaSeInformada(Usuario usuario, String senha) {
        if (senha == null || senha.isBlank()) {
            return;
        }
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setSenhaAlteradaEm(LocalDateTime.now());
        usuario.setTrocarSenha(!ehUsuarioLogado(usuario));
        log.info("Senha do usuario {} redefinida", usuario.getUsername());
    }

    private boolean ehUsuarioLogado(Usuario usuario) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && usuario.getUsername().equals(auth.getName());
    }

    private UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getUsername(),
                usuario.getRole(),
                usuario.getAtivo(),
                usuario.getTrocarSenha(),
                usuario.getUltimoLogin(),
                usuario.getCriadoEm()
        );
    }
}
