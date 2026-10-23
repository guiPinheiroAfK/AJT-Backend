package com.ajtbackend.service;

import com.ajtbackend.config.UsuarioLogado;
import com.ajtbackend.dto.PainelResumoDTO;
import com.ajtbackend.model.enums.Role;
import com.ajtbackend.model.enums.StatusOrdemServico;
import com.ajtbackend.model.enums.StatusTransfer;
import com.ajtbackend.repository.OrdemServicoRepository;
import com.ajtbackend.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * indicadores do painel (HU08, RF012). sao consultas agregadas (GROUP BY / SUM)
 * em vez de buscar registros e contar no frontend.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PainelService {

    // a operacao e no Brasil: o "hoje" nao pode depender do fuso do servidor (container costuma estar em UTC)
    static final ZoneId FUSO_OPERACAO = ZoneId.of("America/Sao_Paulo");

    private final TransferRepository transferRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final UsuarioLogado usuarioLogado;

    /** resumo da data informada; sem data, usa o dia de hoje no fuso de Sao Paulo. */
    public PainelResumoDTO resumo(LocalDate data) {
        LocalDate dia = data != null ? data : LocalDate.now(FUSO_OPERACAO);

        Map<StatusTransfer, Long> transfers =
                porStatus(StatusTransfer.class, transferRepository.contarPorStatusNaData(dia));
        Map<StatusOrdemServico, Long> ordens =
                porStatus(StatusOrdemServico.class, ordemServicoRepository.contarPorStatusNaData(dia));

        // faturamento e informacao financeira: so quem gerencia ve (a HU08 e do gerente)
        BigDecimal faturamentoDia = null;
        BigDecimal faturamentoMes = null;
        if (usuarioLogado.temPerfil(Role.ADMIN) || usuarioLogado.temPerfil(Role.GERENTE)) {
            faturamentoDia = transferRepository.somarValorBaseNoPeriodo(dia, dia, StatusTransfer.CANCELADO);
            faturamentoMes = transferRepository.somarValorBaseNoPeriodo(dia.withDayOfMonth(1), dia, StatusTransfer.CANCELADO);
        }

        return new PainelResumoDTO(dia,
                soma(transfers), transfers,
                soma(ordens), ordens,
                faturamentoDia, faturamentoMes);
    }

    // transforma as linhas (status, total) num mapa com todos os status, 0 nos que nao vieram
    private static <E extends Enum<E>> Map<E, Long> porStatus(Class<E> tipo, List<Object[]> linhas) {
        Map<E, Long> mapa = new EnumMap<>(tipo);
        for (E status : tipo.getEnumConstants()) {
            mapa.put(status, 0L);
        }
        for (Object[] linha : linhas) {
            mapa.put(tipo.cast(linha[0]), ((Number) linha[1]).longValue());
        }
        return mapa;
    }

    private static long soma(Map<?, Long> mapa) {
        return mapa.values().stream().mapToLong(Long::longValue).sum();
    }
}
