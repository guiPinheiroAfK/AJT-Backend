package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.AuditoriaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.model.LogAuditoria;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.LogAuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: a trilha de auditoria (AuditoriaService) — grava tabela,
 * id do registro, mensagem com o usuario logado e a data, cai em "sistema"
 * quando nao ha usuario autenticado, e escolhe a consulta certa ao listar
 * (com ou sem filtro por tabela), alem da mensagem de mudanca de status
 * ("status A -> B") usada por transfers e ordens de servico.
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

    @Test
    void registraComUsuarioLogadoEData() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("maria", null, List.of()));

        service.registrar("transfers", 7L, "Transfer criado");

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        LogAuditoria gravado = captor.getValue();
        assertThat(gravado.getTabelaAfetada()).isEqualTo("transfers");
        assertThat(gravado.getRegistroId()).isEqualTo(7L);
        assertThat(gravado.getMensagem()).isEqualTo("Transfer criado (por maria)");
        assertThat(gravado.getDataHora()).isNotNull();
    }

    @Test
    void semUsuarioAutenticadoRegistraComoSistema() {
        service.registrar("ordens_servico", 1L, "Ordem de servico criada");

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMensagem()).endsWith("(por sistema)");
    }

    @Test
    void atualizacaoComMudancaDeStatusDizDeQualParaQual() {
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.AGUARDANDO_OS, StatusTransfer.CONFIRMADO);

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMensagem())
                .isEqualTo("Transfer atualizado: status AGUARDANDO_OS -> CONFIRMADO (por sistema)");
    }

    @Test
    void atualizacaoSemMudancaDeStatusNaoMencionaStatus() {
        service.registrarAtualizacao("transfers", 5L, "Transfer atualizado",
                StatusTransfer.CONFIRMADO, StatusTransfer.CONFIRMADO);

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMensagem()).isEqualTo("Transfer atualizado (por sistema)");
    }

    @Test
    void listaComFiltroDeTabelaUsaAConsultaFiltrada() {
        var pageable = PageRequest.of(0, 10);
        LogAuditoria log = LogAuditoria.builder().id(1L).tabelaAfetada("transfers").registroId(7L)
                .mensagem("x").dataHora(LocalDateTime.now()).build();
        when(repository.findByTabelaAfetada(eq("transfers"), any())).thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        PaginaResponseDTO<AuditoriaResponseDTO> pagina = service.listar("transfers", pageable);

        assertThat(pagina.conteudo()).hasSize(1);
        assertThat(pagina.conteudo().get(0).registroId()).isEqualTo(7L);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    void listaSemFiltroUsaFindAll() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service.listar(null, pageable).conteudo()).isEmpty();
        assertThat(service.listar("  ", pageable).conteudo()).isEmpty();
    }
}
