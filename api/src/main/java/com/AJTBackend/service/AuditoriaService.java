package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.AuditoriaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.model.LogAuditoria;
import com.AJTBackend.repository.LogAuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        registrarAtualizacao(tabela, registroId, acao, statusAnterior, statusAtual, null, null);
    }

    /**
     * mesma coisa, mas tambem registra a mudanca de valor (em BRL), pedida no RF014:
     * "Transfer atualizado: status AGUARDANDO_OS -> CONFIRMADO; valor 150.00 -> 180.00".
     * a comparacao usa compareTo pra 150.0 e 150.00 nao contarem como mudanca.
     */
    @Transactional
    public void registrarAtualizacao(String tabela, Long registroId, String acao,
                                     Enum<?> statusAnterior, Enum<?> statusAtual,
                                     BigDecimal valorAnterior, BigDecimal valorAtual) {
        List<String> mudancas = new ArrayList<>();
        if (statusAnterior != statusAtual) {
            mudancas.add("status " + statusAnterior + " -> " + statusAtual);
        }
        if (valorMudou(valorAnterior, valorAtual)) {
            mudancas.add("valor " + formatarValor(valorAnterior) + " -> " + formatarValor(valorAtual));
        }
        String descricao = mudancas.isEmpty() ? acao : acao + ": " + String.join("; ", mudancas);
        registrar(tabela, registroId, descricao);
    }

    /**
     * tabela filtra por recurso; tabela + registroId devolve o historico de um registro
     * so (ex: o historico de um transfer, HU11). registroId sozinho nao faz sentido
     * (ids se repetem entre tabelas), entao e ignorado sem tabela.
     */
    public PaginaResponseDTO<AuditoriaResponseDTO> listar(String tabela, Long registroId, Pageable pageable) {
        Page<LogAuditoria> pagina;
        if (tabela == null || tabela.isBlank()) {
            pagina = logAuditoriaRepository.findAll(pageable);
        } else if (registroId != null) {
            pagina = logAuditoriaRepository.findByTabelaAfetadaAndRegistroId(tabela, registroId, pageable);
        } else {
            pagina = logAuditoriaRepository.findByTabelaAfetada(tabela, pageable);
        }
        return PaginaResponseDTO.de(pagina, this::toResponseDTO);
    }

    private static boolean valorMudou(BigDecimal anterior, BigDecimal atual) {
        if (anterior == null || atual == null) {
            return anterior != atual;
        }
        return anterior.compareTo(atual) != 0;
    }

    private static String formatarValor(BigDecimal valor) {
        return valor == null ? "sem valor" : valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private AuditoriaResponseDTO toResponseDTO(LogAuditoria log) {
        return new AuditoriaResponseDTO(log.getId(), log.getTabelaAfetada(), log.getRegistroId(),
                log.getMensagem(), log.getDataHora());
    }
}
