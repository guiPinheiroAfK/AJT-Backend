package com.AJTBackend.service;

import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.repository.PassageiroRepository;
import com.AJTBackend.dto.PaginaResponseDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassageiroService {

    private static final Logger log = LoggerFactory.getLogger(PassageiroService.class);

    private final PassageiroRepository passageiroRepository;

    public PaginaResponseDTO<PassageiroResponseDTO> listarTodos(Pageable pageable) {
        return PaginaResponseDTO.de(passageiroRepository.findAll(pageable), this::toResponseDTO);
    }

    public PassageiroResponseDTO buscarPorId(Long id) {
        Passageiro passageiro = passageiroRepository.findById(id)
                .orElseThrow(() -> new PassageiroNaoEncontradoException(id));
        return toResponseDTO(passageiro);
    }

    public PaginaResponseDTO<PassageiroResponseDTO> buscarPorNacionalidade(String nacionalidade, Pageable pageable) {
        return PaginaResponseDTO.de(passageiroRepository.findByNacionalidadeIgnoreCase(nacionalidade, pageable),
                this::toResponseDTO);
    }

    @Transactional
    public PassageiroResponseDTO criar(PassageiroRequestDTO dto) {
        Passageiro passageiro = Passageiro.builder()
                .nome(dto.nome())
                .tipoDocumento(dto.tipoDocumento())
                .documento(dto.documento())
                .nacionalidade(dto.nacionalidade())
                .build();

        Passageiro salvo = passageiroRepository.save(passageiro);
        log.info("Passageiro criado: id={}", salvo.getId());
        return toResponseDTO(salvo);
    }

    @Transactional
    public PassageiroResponseDTO atualizar(Long id, PassageiroRequestDTO dto) {
        Passageiro passageiro = passageiroRepository.findById(id)
                .orElseThrow(() -> new PassageiroNaoEncontradoException(id));

        passageiro.setNome(dto.nome());
        passageiro.setTipoDocumento(dto.tipoDocumento());
        passageiro.setDocumento(dto.documento());
        passageiro.setNacionalidade(dto.nacionalidade());

        return toResponseDTO(passageiro);
    }

    @Transactional
    public PassageiroResponseDTO atualizarParcial(Long id, PassageiroRequestDTO dto) {
        Passageiro passageiro = passageiroRepository.findById(id)
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

        return toResponseDTO(passageiro);
    }

    @Transactional
    public void deletar(Long id) {
        if (!passageiroRepository.existsById(id)) {
            throw new PassageiroNaoEncontradoException(id);
        }
        passageiroRepository.deleteById(id);
        log.info("Passageiro removido: id={}", id);
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
