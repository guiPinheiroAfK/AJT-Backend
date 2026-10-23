package com.ajtbackend.service;

import com.ajtbackend.config.UsuarioLogado;
import com.ajtbackend.dto.AuditoriaResponseDTO;
import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.model.LogAuditoria;
import com.ajtbackend.model.enums.StatusTransfer;
import com.ajtbackend.repository.LogAuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

    private static final PageRequest PAGINA = PageRequest.of(0, 10);

    private final LogAuditoriaRepository repository = mock(LogAuditoriaRepository.class);
    private final AuditoriaService service = new AuditoriaService(repository, new UsuarioLogado());

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveGravarTabelaRegistroUsuarioEData_quandoHaUsuarioLogado() {
        // given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("maria", null, List.of()));

        // when
        service.registrar("transfers", 7L, "Transfer criado");

        // then
        LogAuditoria gravado = logGravado();
        assertThat(gravado.getTabelaAfetada()).isEqualTo("transfers");
        assertThat(gravado.getRegistroId()).isEqualTo(7L);
        assertThat(gravado.getMensagem()).isEqualTo("Transfer criado (por maria)");
        assertThat(gravado.getDataHora()).isNotNull();
    }

    @Test
    void deveRegistrarComoSistema_quandoNaoHaUsuarioAutenticado() {
        // when
        service.registrar("ordens_servico", 1L, "Ordem de serviço criada");

        // then
        assertThat(logGravado().getMensagem()).isEqualTo("Ordem de serviço criada (por sistema)");
    }

    @Test
    void deveDizerDeQualStatusParaQual_quandoStatusMudou() {
        // when
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.AGUARDANDO_OS, StatusTransfer.CONFIRMADO);

        // then
        assertThat(logGravado().getMensagem())
                .isEqualTo("Transfer atualizado: status AGUARDANDO_OS -> CONFIRMADO (por sistema)");
    }

    @Test
    void naoDeveMencionarStatus_quandoStatusNaoMudou() {
        // when
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO);

        // then
        assertThat(logGravado().getMensagem()).isEqualTo("Transfer atualizado (por sistema)");
    }

    @Test
    void deveDizerDeQualValorParaQual_quandoValorMudou() {
        // when
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO,
                new BigDecimal("150.00"), new BigDecimal("180.5"));

        // then
        assertThat(logGravado().getMensagem())
                .isEqualTo("Transfer atualizado: valor 150.00 -> 180.50 (por sistema)");
    }

    @Test
    void deveMencionarStatusEValor_quandoOsDoisMudaram() {
        // when
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.AGUARDANDO_OS, StatusTransfer.CONFIRMADO, null, new BigDecimal("90"));

        // then
        assertThat(logGravado().getMensagem())
                .isEqualTo("Transfer atualizado: status AGUARDANDO_OS -> CONFIRMADO; valor sem valor -> 90.00 (por sistema)");
    }

    @Test
    void deveMostrarSemValor_quandoValorFoiApagado() {
        // when
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO, new BigDecimal("150"), null);

        // then
        assertThat(logGravado().getMensagem()).isEqualTo("Transfer atualizado: valor 150.00 -> sem valor (por sistema)");
    }

    @Test
    void naoDeveMencionarValor_quandoValorEstaAusenteAntesEDepois() {
        // when
        service.registrarAtualizacao("transfers", 6L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO, null, null);

        // then
        assertThat(logGravado().getMensagem()).doesNotContain("valor");
    }

    @Test
    void naoDeveContarComoMudanca_quandoMesmoValorTemEscalaDiferente() {
        // when
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO,
                new BigDecimal("150.0"), new BigDecimal("150.00"));

        // then
        assertThat(logGravado().getMensagem()).isEqualTo("Transfer atualizado (por sistema)");
    }

    @Test
    void deveBuscarOHistoricoDoRegistro_quandoListaComTabelaERegistroId() {
        // given
        when(repository.findByTabelaAfetadaAndRegistroId(eq("transfers"), eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

        // when
        service.listar("transfers", 7L, PAGINA);

        // then
        verify(repository).findByTabelaAfetadaAndRegistroId("transfers", 7L, PAGINA);
    }

    @Test
    void deveUsarAConsultaFiltrada_quandoListaSoComTabela() {
        // given
        LogAuditoria log = LogAuditoria.builder().id(1L).tabelaAfetada("transfers").registroId(7L)
                .mensagem("x").dataHora(LocalDateTime.now()).build();
        when(repository.findByTabelaAfetada(eq("transfers"), any())).thenReturn(new PageImpl<>(List.of(log), PAGINA, 1));

        // when
        PaginaResponseDTO<AuditoriaResponseDTO> pagina = service.listar("transfers", null, PAGINA);

        // then
        assertThat(pagina.conteudo()).hasSize(1);
        assertThat(pagina.conteudo().get(0).registroId()).isEqualTo(7L);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    void deveUsarFindAll_quandoListaSemTabela() {
        // given
        when(repository.findAll(PAGINA)).thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

        // when
        PaginaResponseDTO<AuditoriaResponseDTO> pagina = service.listar(null, null, PAGINA);

        // then
        assertThat(pagina.conteudo()).isEmpty();
        verify(repository).findAll(PAGINA);
    }

    @Test
    void deveIgnorarORegistroIdEUsarFindAll_quandoTabelaEstaEmBranco() {
        // given
        when(repository.findAll(PAGINA)).thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

        // when
        PaginaResponseDTO<AuditoriaResponseDTO> pagina = service.listar("  ", 7L, PAGINA);

        // then
        assertThat(pagina.conteudo()).isEmpty();
        verify(repository).findAll(PAGINA);
        verify(repository, never()).findByTabelaAfetadaAndRegistroId(any(), any(), any());
    }

    private LogAuditoria logGravado() {
        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
