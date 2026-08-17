package com.AJTBackend.service;

import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.exception.UsernameJaCadastradoException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

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

    public UsuarioResponseDTO criar(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByUsername(dto.username())) {
            throw new UsernameJaCadastradoException(dto.username());
        }

        Usuario usuario = Usuario.builder()
                .nome(dto.nome())
                .username(dto.username())
                .senha(passwordEncoder.encode(dto.senha())) // hash, nunca texto plano
                .role(dto.role())
                .build();

        return toResponseDTO(usuarioRepository.save(usuario));
    }

    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        usuario.setNome(dto.nome());
        usuario.setUsername(dto.username());
        usuario.setRole(dto.role());

        if (dto.senha() != null && !dto.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(dto.senha()));
        }

        return toResponseDTO(usuarioRepository.save(usuario));
    }

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
            usuario.setRole(dto.role());
        }

        return toResponseDTO(usuarioRepository.save(usuario));
    }

    public void deletar(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new UsuarioNaoEncontradoException(id);
        }
        usuarioRepository.deleteById(id);
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