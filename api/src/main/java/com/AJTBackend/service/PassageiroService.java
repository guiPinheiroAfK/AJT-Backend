package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.FiltroPassageiroDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.Especificacoes;
import com.AJTBackend.repository.PassageiroRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassageiroService {

    private static final Logger log = LoggerFactory.getLogger(PassageiroService.class);

    private final PassageiroRepository passageiroRepository;
    private final UsuarioLogado usuarioLogado;

    // RF015: nome e nacionalidade sao opcionais (contem, sem diferenciar maiusculas)
    public PaginaResponseDTO<PassageiroResponseDTO> listar(FiltroPassageiroDTO filtro, Pageable pageable) {
        Specification<Passageiro> spec = Specification.allOf(
                Especificacoes.contem("nome", filtro.nome()),
                Especificacoes.contem("nacionalidade", filtro.nacionalidade()));
        return PaginaResponseDTO.de(passageiroRepository.findAll(spec, pageable), this::toResponseDTO);
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

    public List<PassageiroResponseDTO> listarPorTransfer(Long transferId) {
        return passageiroRepository.findByTransferId(transferId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional
    public PassageiroResponseDTO criar(PassageiroRequestDTO dto) {
        validarDocumento(dto.tipoDocumento(), dto.documento());
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

        validarDocumento(dto.tipoDocumento(), dto.documento());
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
        // valida o estado final: o PATCH pode trocar so o tipo ou so o numero
        if (dto.tipoDocumento() != null || dto.documento() != null) {
            validarDocumento(passageiro.getTipoDocumento(), passageiro.getDocumento());
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
        // motorista so precisa do nome pra embarcar o passageiro; o documento (LGPD) vai mascarado
        String documento = usuarioLogado.temPerfil(Role.MOTORISTA) ? mascarar(p.getDocumento()) : p.getDocumento();
        return new PassageiroResponseDTO(
                p.getId(),
                p.getNome(),
                p.getTipoDocumento(),
                documento,
                p.getNacionalidade()
        );
    }

    // por enquanto so o CPF tem regra conhecida (os outros documentos variam por pais/estado)
    static void validarDocumento(String tipo, String documento) {
        if ("CPF".equalsIgnoreCase(tipo) && !cpfValido(documento)) {
            throw new RegraNegocioException("CPF inválido: confira os 11 dígitos.");
        }
    }

    // aceita com ou sem pontuacao; confere os 2 digitos verificadores
    static boolean cpfValido(String cpf) {
        String digitos = cpf == null ? "" : cpf.replaceAll("[.\\-\\s]", "");
        if (!digitos.matches("\\d{11}") || digitos.chars().distinct().count() == 1) {
            return false; // tamanho errado, letras, ou tudo igual (111.111.111-11 passa na conta mas nao existe)
        }
        return digitoVerificador(digitos, 9) == digitos.charAt(9) - '0'
                && digitoVerificador(digitos, 10) == digitos.charAt(10) - '0';
    }

    private static int digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    // mostra so os 2 ultimos caracteres: "130.124.189-03" -> "************03"
    static String mascarar(String documento) {
        if (documento == null || documento.length() <= 2) {
            return "**";
        }
        return "*".repeat(documento.length() - 2) + documento.substring(documento.length() - 2);
    }
}
