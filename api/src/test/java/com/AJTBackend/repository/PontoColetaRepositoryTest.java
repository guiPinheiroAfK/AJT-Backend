package com.AJTBackend.repository;

import com.AJTBackend.model.PontoColeta;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusTransfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: os pontos de coleta de um transfer voltam na ordem de parada
 * (ordem_parada crescente) e so os do transfer pedido, e um transfer sem
 * pontos devolve lista vazia.
 *
 * como rodar: precisa de docker (herda de RepositorioTestBase). "mvn test -Dtest=PontoColetaRepositoryTest".
 *
 * por que existe: a rota de pontos de coleta mostra a sequencia da rota ao
 * motorista. se a ordenacao ou o filtro por transfer quebrarem, a rota sai
 * embaralhada sem nenhum erro aparente.
 */
class PontoColetaRepositoryTest extends RepositorioTestBase {

    @Autowired
    private PontoColetaRepository pontoColetaRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void deveRetornarPontosEmOrdemDeParada_quandoTransferTemVarios() {
        // given
        Transfer transfer = transferRepository.save(transfer());
        pontoColetaRepository.save(ponto(transfer, 2, "Centro"));
        pontoColetaRepository.save(ponto(transfer, 1, "Bairro Alto"));
        pontoColetaRepository.save(ponto(transfer, 3, "Hotel"));

        // when
        List<PontoColeta> pontos = pontoColetaRepository.findByTransferIdOrderByOrdemParadaAsc(transfer.getId());

        // then
        assertThat(pontos)
                .extracting(PontoColeta::getLocalColeta)
                .containsExactly("Bairro Alto", "Centro", "Hotel");
    }

    @Test
    void naoDeveTrazerPontosDeOutroTransfer_quandoConsultaUmTransfer() {
        // given
        Transfer alvo = transferRepository.save(transfer());
        Transfer outro = transferRepository.save(transfer());
        pontoColetaRepository.save(ponto(alvo, 1, "Aeroporto"));
        pontoColetaRepository.save(ponto(outro, 1, "Rodoviaria"));

        // when
        List<PontoColeta> pontos = pontoColetaRepository.findByTransferIdOrderByOrdemParadaAsc(alvo.getId());

        // then
        assertThat(pontos)
                .extracting(PontoColeta::getLocalColeta)
                .containsExactly("Aeroporto");
    }

    @Test
    void deveRetornarListaVazia_quandoTransferSemPontos() {
        // given
        Transfer semPontos = transferRepository.save(transfer());

        // when
        List<PontoColeta> pontos = pontoColetaRepository.findByTransferIdOrderByOrdemParadaAsc(semPontos.getId());

        // then
        assertThat(pontos).isEmpty();
    }

    private Transfer transfer() {
        return Transfer.builder()
                .dataTransfer(LocalDate.of(2026, 10, 1))
                .horaTransfer(LocalTime.NOON)
                .origem("Aeroporto")
                .destino("Hotel")
                .status(StatusTransfer.CONFIRMADO)
                .valorBase(BigDecimal.TEN)
                .build();
    }

    private PontoColeta ponto(Transfer transfer, int ordem, String local) {
        return PontoColeta.builder()
                .transfer(transfer)
                .localColeta(local)
                .ordemParada(ordem)
                .horarioPrevisto(LocalTime.NOON)
                .latitude(new BigDecimal("-23.5505200"))
                .longitude(new BigDecimal("-46.6333094"))
                .build();
    }
}
