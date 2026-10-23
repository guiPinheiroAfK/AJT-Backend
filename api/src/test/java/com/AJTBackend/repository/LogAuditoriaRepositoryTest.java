package com.AJTBackend.repository;

import com.AJTBackend.model.LogAuditoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: o historico de auditoria filtrado por tabela e pelo registro
 * (tabela + registro_id, indice da V8), sem misturar registros de outra
 * tabela com o mesmo id.
 *
 * como rodar: precisa de docker (herda de RepositorioTestBase). "mvn test -Dtest=LogAuditoriaRepositoryTest".
 *
 * por que existe: o id 1 de transfers e o id 1 de motoristas sao coisas
 * diferentes. se o filtro olhasse so o registro_id, o historico de um
 * transfer mostraria alteracoes de um motorista.
 */
class LogAuditoriaRepositoryTest extends RepositorioTestBase {

    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    @Test
    void deveTrazerSoOHistoricoDoRegistro_quandoOutraTabelaTemOMesmoId() {
        // given
        logAuditoriaRepository.save(log("transfers", 1L, "transfer 1 criado"));
        logAuditoriaRepository.save(log("transfers", 1L, "transfer 1 alterado"));
        logAuditoriaRepository.save(log("transfers", 2L, "transfer 2 criado"));
        logAuditoriaRepository.save(log("motoristas", 1L, "motorista 1 criado"));

        // when
        Page<LogAuditoria> pagina = logAuditoriaRepository.findByTabelaAfetadaAndRegistroId("transfers", 1L, PageRequest.of(0, 10));

        // then
        assertThat(pagina.getContent())
                .extracting(LogAuditoria::getMensagem)
                .containsExactlyInAnyOrder("transfer 1 criado", "transfer 1 alterado");
    }

    @Test
    void deveRetornarPaginaVazia_quandoRegistroNaoTemHistorico() {
        // given
        logAuditoriaRepository.save(log("transfers", 1L, "transfer 1 criado"));

        // when
        Page<LogAuditoria> pagina = logAuditoriaRepository.findByTabelaAfetadaAndRegistroId("transfers", 99L, PageRequest.of(0, 10));

        // then
        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    void deveTrazerTodosDaTabela_quandoFiltraSoPorTabela() {
        // given
        logAuditoriaRepository.save(log("transfers", 1L, "transfer 1 criado"));
        logAuditoriaRepository.save(log("transfers", 2L, "transfer 2 criado"));
        logAuditoriaRepository.save(log("motoristas", 1L, "motorista 1 criado"));

        // when
        Page<LogAuditoria> pagina = logAuditoriaRepository.findByTabelaAfetada("transfers", PageRequest.of(0, 10));

        // then
        assertThat(pagina.getContent())
                .extracting(LogAuditoria::getTabelaAfetada)
                .containsOnly("transfers")
                .hasSize(2);
    }

    private LogAuditoria log(String tabela, Long registroId, String mensagem) {
        return LogAuditoria.builder()
                .tabelaAfetada(tabela)
                .registroId(registroId)
                .mensagem(mensagem)
                .dataHora(LocalDateTime.of(2026, 10, 1, 12, 0))
                .build();
    }
}
