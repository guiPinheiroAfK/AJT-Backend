package com.AJTBackend.service;

import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.repository.PassageiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PassageiroService {

    private final PassageiroRepository repository;

    public List<PassageiroResponseDTO> listarTodos() {
        return repository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PassageiroResponseDTO buscarPorId(Long id) {
        Passageiro passageiro = repository.findById(id)
                .orElseThrow(() -> new PassageiroNaoEncontradoException(id));
        return toResponseDTO(passageiro);
    }

    public List<PassageiroResponseDTO> buscarPorNacionalidade(String nacionalidade) {
        return repository.findByNacionalidadeIgnoreCase(nacionalidade)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PassageiroResponseDTO criar(PassageiroRequestDTO dto) {
        Passageiro passageiro = Passageiro.builder()
                .nome(dto.nome())
                .tipoDocumento(dto.tipoDocumento())
                .documento(dto.documento())
                .nacionalidade(dto.nacionalidade())
                .build();

        Passageiro salvo = repository.save(passageiro);
        return toResponseDTO(salvo);
    }

    public PassageiroResponseDTO atualizar(Long id, PassageiroRequestDTO dto) {
        Passageiro passageiro = repository.findById(id)
                .orElseThrow(() -> new PassageiroNaoEncontradoException(id));

        passageiro.setNome(dto.nome());
        passageiro.setTipoDocumento(dto.tipoDocumento());
        passageiro.setDocumento(dto.documento());
        passageiro.setNacionalidade(dto.nacionalidade());

        Passageiro atualizado = repository.save(passageiro);
        return toResponseDTO(atualizado);
    }

    public PassageiroResponseDTO atualizarParcial(Long id, PassageiroRequestDTO dto) {
        Passageiro passageiro = repository.findById(id)
                .orElseThrow(() -> new PassageiroNaoEncontradoException(id));

        if (dto.nome() != null) {
            passageiro.setNome(dto.nome());
        }
        if (dto.tipoDocumento() != null) {
            passageiro.setTipoDocumento(dto.tipoDocumento());
        }
        if (dto.documento() != null) {
            passageiro.setDocumento(dto.documento());
        }
        if (dto.nacionalidade() != null) {
            passageiro.setNacionalidade(dto.nacionalidade());
        }

        Passageiro atualizado = repository.save(passageiro);
        return toResponseDTO(atualizado);
    }

    public void deletar(Long id) {
        if (!repository.existsById(id)) {
            throw new PassageiroNaoEncontradoException(id);
        }
        repository.deleteById(id);
    }

    private PassageiroResponseDTO toResponseDTO(Passageiro p) {
        return new PassageiroResponseDTO(
                p.getId(),
                p.getNome(),
                p.getTipoDocumento(),
                p.getDocumento(),
                p.getNacionalidade()
        );
    }
}
