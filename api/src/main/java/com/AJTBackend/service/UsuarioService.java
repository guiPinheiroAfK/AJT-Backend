package com.AJTBackend.service;

import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.exception.UsernameJaCadastradoException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public UsuarioResponseDTO buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));
        return toResponseDTO(usuario);
    }

    public UsuarioResponseDTO buscarPorUsername(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(0L));
        return toResponseDTO(usuario);
    }

    @Transactional
    public UsuarioResponseDTO criar(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByUsername(dto.username())) {
            log.warn("Tentativa de cadastro com username ja existente: {}", dto.username());
            throw new UsernameJaCadastradoException(dto.username());
        }

        Usuario usuario = Usuario.builder()
                .nome(dto.nome())
                .username(dto.username())
                .senha(passwordEncoder.encode(dto.senha())) // hash, nunca texto plano
                .role(dto.role())
                .build();

        Usuario salvo = usuarioRepository.save(usuario);
        log.info("Usuario criado: id={}, username={}, role={}", salvo.getId(), salvo.getUsername(), salvo.getRole());
        return toResponseDTO(salvo);
    }

    @Transactional
    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        if (!dto.role().equals(usuario.getRole())) {
            log.info("Role do usuario {} alterada: {} -> {}", usuario.getUsername(), usuario.getRole(), dto.role());
        }

        usuario.setNome(dto.nome());
        usuario.setUsername(dto.username());
        usuario.setRole(dto.role());

        if (dto.senha() != null && !dto.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(dto.senha()));
        }

        return toResponseDTO(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponseDTO atualizarParcial(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        if (dto.nome() != null) {
            usuario.setNome(dto.nome());
        }
        if (dto.username() != null) {
            if (!dto.username().equals(usuario.getUsername())
                    && usuarioRepository.existsByUsername(dto.username())) {
                throw new UsernameJaCadastradoException(dto.username());
            }
            usuario.setUsername(dto.username());
        }
        if (dto.senha() != null && !dto.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(dto.senha()));
        }
        if (dto.role() != null) {
            if (!dto.role().equals(usuario.getRole())) {
                log.info("Role do usuario {} alterada: {} -> {}", usuario.getUsername(), usuario.getRole(), dto.role());
            }
            usuario.setRole(dto.role());
        }

        return toResponseDTO(usuarioRepository.save(usuario));
    }

    @Transactional
    public void deletar(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new UsuarioNaoEncontradoException(id);
        }
        usuarioRepository.deleteById(id);
        log.info("Usuario removido: id={}", id);
    }

    private UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getUsername(),
                usuario.getRole(),
                usuario.getAtivo(),
                usuario.getUltimoLogin(),
                usuario.getCriadoEm()
        );
    }
}