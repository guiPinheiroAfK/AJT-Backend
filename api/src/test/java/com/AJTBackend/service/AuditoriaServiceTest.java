package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.AuditoriaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.model.LogAuditoria;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.LogAuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: a trilha de auditoria (AuditoriaService) — grava tabela,
 * id do registro, mensagem com o usuario logado e a data, cai em "sistema"
 * quando nao ha usuario autenticado, e escolhe a consulta certa ao listar
 * (com ou sem filtro por tabela), alem da mensagem de mudanca de status
 * ("status A -> B") usada por transfers e ordens de servico e a de mudanca
 * de valor ("valor X -> Y", RF014), e o filtro pelo historico de um registro.
 *
 * como rodar: teste unitario com o repositorio mockado. o usuario logado e
 * simulado no SecurityContextHolder. sem spring, sem banco, sem docker.
 * roda com "mvn test".
 *
 * por que existe: a auditoria e o que responde "quem mexeu nisso e quando".
 * se a mensagem perder o usuario ou o filtro por tabela quebrar, o problema
 * so aparece quando alguem precisar investigar algo — tarde demais.
 */
class AuditoriaServiceTest {

    private final LogAuditoriaRepository repository = mock(LogAuditoriaRepository.class);
    private final AuditoriaService service = new AuditoriaService(repository, new UsuarioLogado());

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private LogAuditoria gravado() {
        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void registraComUsuarioLogadoEData() {
        // Given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("maria", null, List.of()));

        // When
        service.registrar("transfers", 7L, "Transfer criado");

        // Then
        LogAuditoria log = gravado();
        assertThat(log.getTabelaAfetada()).isEqualTo("transfers");
        assertThat(log.getRegistroId()).isEqualTo(7L);
        assertThat(log.getMensagem()).isEqualTo("Transfer criado (por maria)");
        assertThat(log.getDataHora()).isNotNull();
    }

    @Test
    void semUsuarioAutenticadoRegistraComoSistema() {
        // When
        service.registrar("ordens_servico", 1L, "Ordem de servico criada");

        // Then
        assertThat(gravado().getMensagem()).isEqualTo("Ordem de servico criada (por sistema)");
    }

    @Test
    void atualizacaoComMudancaDeStatusDizDeQualParaQual() {
        // When
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.AGUARDANDO_OS, StatusTransfer.CONFIRMADO);

        // Then
        assertThat(gravado().getMensagem())
                .isEqualTo("Transfer atualizado: status AGUARDANDO_OS -> CONFIRMADO (por sistema)");
    }

    @Test
    void atualizacaoSemMudancaDeStatusNaoMencionaStatus() {
        // When
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO);

        // Then
        assertThat(gravado().getMensagem()).isEqualTo("Transfer atualizado (por sistema)");
    }

    @Test
    void atualizacaoComMudancaDeValorDizDeQualParaQual() {
        // When
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO,
                new BigDecimal("150.00"), new BigDecimal("180.5"));

        // Then
        assertThat(gravado().getMensagem()).isEqualTo("Transfer atualizado: valor 150.00 -> 180.50 (por sistema)");
    }

    @Test
    void atualizacaoComStatusEValorMencionaOsDois() {
        // When
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.AGUARDANDO_OS, StatusTransfer.CONFIRMADO, null, new BigDecimal("90"));

        // Then
        assertThat(gravado().getMensagem())
                .isEqualTo("Transfer atualizado: status AGUARDANDO_OS -> CONFIRMADO; valor sem valor -> 90.00 (por sistema)");
    }

    @Test
    void valorApagadoApareceComoSemValor() {
        // When
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO, new BigDecimal("150"), null);

        // Then
        assertThat(gravado().getMensagem()).isEqualTo("Transfer atualizado: valor 150.00 -> sem valor (por sistema)");
    }

    @Test
    void valorAusenteAntesEDepoisNaoContaComoMudanca() {
        // When
        service.registrarAtualizacao("transfers", 6L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO, null, null);

        // Then
        assertThat(gravado().getMensagem()).isEqualTo("Transfer atualizado (por sistema)");
    }

    @Test
    void mesmoValorComEscalaDiferenteNaoContaComoMudanca() {
        // When
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO,
                new BigDecimal("150.0"), new BigDecimal("150.00"));

        // Then
        assertThat(gravado().getMensagem()).isEqualTo("Transfer atualizado (por sistema)");
    }

    @Test
    void listaComTabelaERegistroIdDevolveOHistoricoDeUmRegistro() {
        // Given
        var pageable = PageRequest.of(0, 10);
        LogAuditoria log = LogAuditoria.builder().id(3L).tabelaAfetada("transfers").registroId(7L)
                .mensagem("Transfer criado (por maria)").dataHora(LocalDateTime.now()).build();
        when(repository.findByTabelaAfetadaAndRegistroId("transfers", 7L, pageable))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        // When
        PaginaResponseDTO<AuditoriaResponseDTO> pagina = service.listar("transfers", 7L, pageable);

        // Then
        assertThat(pagina.conteudo()).singleElement().satisfies(item -> {
            assertThat(item.registroId()).isEqualTo(7L);
            assertThat(item.mensagem()).isEqualTo("Transfer criado (por maria)");
        });
        verify(repository, never()).findAll(pageable);
    }

    @Test
    void listaComFiltroDeTabelaUsaAConsultaFiltrada() {
        // Given
        var pageable = PageRequest.of(0, 10);
        LogAuditoria log = LogAuditoria.builder().id(1L).tabelaAfetada("transfers").registroId(7L)
                .mensagem("x").dataHora(LocalDateTime.now()).build();
        when(repository.findByTabelaAfetada(eq("transfers"), any())).thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        // When
        PaginaResponseDTO<AuditoriaResponseDTO> pagina = service.listar("transfers", null, pageable);

        // Then
        assertThat(pagina.conteudo()).hasSize(1);
        assertThat(pagina.conteudo().get(0).registroId()).isEqualTo(7L);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void listaSemTabelaUsaFindAllMesmoComRegistroId(String tabela) {
        // Given
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // When
        PaginaResponseDTO<AuditoriaResponseDTO> pagina = service.listar(tabela, 7L, pageable);

        // Then
        assertThat(pagina.conteudo()).isEmpty();
        verify(repository).findAll(pageable);
    }
}
