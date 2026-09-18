package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.AuditoriaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.model.LogAuditoria;
import com.AJTBackend.repository.LogAuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * trilha de auditoria das operacoes de escrita (quem fez o que e quando).
 * registrar() participa da transacao de quem chama: se a operacao de negocio
 * der rollback, o registro de auditoria some junto (nao fica log de algo que
 * nao aconteceu).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final UsuarioLogado usuarioLogado;

    @Transactional
    public void registrar(String tabela, Long registroId, String acao) {
        String mensagem = acao + " (por " + usuarioLogado.usernameOuSistema() + ")";
        logAuditoriaRepository.save(LogAuditoria.builder()
                .tabelaAfetada(tabela)
                .registroId(registroId)
                .mensagem(mensagem)
                .dataHora(LocalDateTime.now())
                .build());
    }

    /**
     * registra uma atualizacao; se o status mudou, a mensagem diz de qual pra qual
     * ("Transfer atualizado: status AGUARDANDO_OS -> CONFIRMADO").
     */
    @Transactional
    public void registrarAtualizacao(String tabela, Long registroId, String acao,
                                     Enum<?> statusAnterior, Enum<?> statusAtual) {
        String descricao = statusAnterior == statusAtual
                ? acao
                : acao + ": status " + statusAnterior + " -> " + statusAtual;
        registrar(tabela, registroId, descricao);
    }

    public PaginaResponseDTO<AuditoriaResponseDTO> listar(String tabela, Pageable pageable) {
        var pagina = (tabela == null || tabela.isBlank())
                ? logAuditoriaRepository.findAll(pageable)
                : logAuditoriaRepository.findByTabelaAfetada(tabela, pageable);
        return PaginaResponseDTO.de(pagina, this::toResponseDTO);
    }

    private AuditoriaResponseDTO toResponseDTO(LogAuditoria log) {
        return new AuditoriaResponseDTO(log.getId(), log.getTabelaAfetada(), log.getRegistroId(),
                log.getMensagem(), log.getDataHora());
    }
}
