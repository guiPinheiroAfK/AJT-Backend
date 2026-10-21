package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.model.enums.AcaoParada;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * o que testa: o PDF da OS (RF013) em cada variacao que aparece na operacao — OS ainda sem
 * motorista, veiculo, paradas e transfers; OS completa com parada sem horario/acao, transfer
 * sem passageiros e motorista sem telefone; OS inexistente ou de outro motorista (404). le o
 * texto do PDF gerado pra conferir o que sai impresso.
 *
 * como rodar: teste unitario com repositorios e AcessoMotorista mockados; gera o PDF em
 * memoria com o OpenPDF. sem spring, sem banco. "mvn test".
 *
 * por que existe: o papel e o que o motorista leva; campo nulo nao pode derrubar a geracao
 * (500) nem sair como "null" impresso.
 */
class RelatorioOrdemServicoServiceTest {

    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final ParadaOsRepository paradaOsRepository = mock(ParadaOsRepository.class);
    private final AcessoMotorista acessoMotorista = mock(AcessoMotorista.class);
    private final RelatorioOrdemServicoService service = new RelatorioOrdemServicoService(
            ordemServicoRepository, paradaOsRepository, new UsuarioLogado(), acessoMotorista);

    private static String texto(byte[] pdf) throws IOException {
        PdfReader leitor = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(leitor).getTextFromPage(1);
        } finally {
            leitor.close();
        }
    }

    @Test
    void osInexistenteOuDeOutroMotoristaLanca404() {
        when(ordemServicoRepository.findById(9L)).thenReturn(Optional.empty());
        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(OrdemServico.builder().id(1L).build()));
        when(acessoMotorista.podeVer(any())).thenReturn(false);

        assertThatThrownBy(() -> service.gerar(9L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.gerar(1L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void osVaziaSaiComAvisosEmVezDeNull() throws IOException {
        OrdemServico vazia = OrdemServico.builder().id(2L).dataServico(null).status(null).build();
        when(ordemServicoRepository.findById(2L)).thenReturn(Optional.of(vazia));
        when(acessoMotorista.podeVer(any())).thenReturn(true);
        when(paradaOsRepository.findByOrdemServicoIdOrderByOrdemParadaAsc(2L)).thenReturn(List.of());

        String texto = texto(service.gerar(2L));

        assertThat(texto).contains("Ordem de Serviço nº 2", "Não definido", "Nenhuma parada cadastrada", "Nenhum transfer vinculado");
        assertThat(texto).doesNotContain("null");
    }

    @Test
    void osCompletaListaRoteiroETransfersComCamposVazios() throws IOException {
        Transfer comPassageiros = Transfer.builder().id(12L).horaTransfer(LocalTime.of(9, 0)).origem("Aeroporto").destino("Hotel")
                .passageiros(Set.of(Passageiro.builder().id(1L).nome("Bia").build(), Passageiro.builder().id(2L).nome("Ana").build()))
                .build();
        Transfer semPassageiros = Transfer.builder().id(11L).horaTransfer(null).origem("Rodoviaria").destino(null).build();
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(LocalDate.of(2026, 10, 21)).status(StatusOrdemServico.ABERTA)
                .motorista(Motorista.builder().id(5L).nome("Carlos").telefone(null).build())
                .veiculo(Veiculo.builder().id(6L).label("Van 01").placa("ABC1D23").capacidade(15).build())
                .transfers(new ArrayList<>(List.of(comPassageiros, semPassageiros)))
                .build();
        ParadaOs comTudo = ParadaOs.builder().id(1L).ordemParada(1).localParada("Aeroporto").horarioPrevisto(LocalTime.of(8, 45))
                .acao(AcaoParada.EMBARQUE).transfers(Set.of(comPassageiros, semPassageiros)).build();
        ParadaOs semNada = ParadaOs.builder().id(2L).ordemParada(2).localParada("Hotel").build();

        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));
        when(acessoMotorista.podeVer(any())).thenReturn(true);
        when(paradaOsRepository.findByOrdemServicoIdOrderByOrdemParadaAsc(3L)).thenReturn(List.of(comTudo, semNada));

        String texto = texto(service.gerar(3L));

        assertThat(texto).contains("21/10/2026", "ABERTA", "Carlos", "Van 01", "ABC1D23", "15 lugares");
        assertThat(texto).doesNotContain("Tel."); // sem telefone, sem rotulo solto
        assertThat(texto).contains("08:45", "EMBARQUE", "#11, #12"); // transfers da parada em ordem de id
        assertThat(texto).contains("2 pax: Ana, Bia"); // passageiros em ordem alfabetica
        assertThat(texto).contains("Rodoviaria");
        assertThat(texto).doesNotContain("null");
    }
}
