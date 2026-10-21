package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * relatorio em PDF de uma ordem de servico (HU10, RF013), pra entregar impresso
 * ao motorista: dados da OS, motorista, veiculo, roteiro (paradas em ordem) e os
 * transfers com os passageiros.
 *
 * de proposito NAO entram valores (o papel fica com o motorista) nem documentos
 * dos passageiros (sao dados cifrados, RNF007).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelatorioOrdemServicoService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SEM_INFO = "-";

    private static final Color COR_CABECALHO = new Color(31, 41, 55);
    private static final Font TITULO = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 10);
    private static final Font NEGRITO = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font CABECALHO_TABELA = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font RODAPE = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

    private final OrdemServicoRepository ordemServicoRepository;
    private final ParadaOsRepository paradaOsRepository;
    private final UsuarioLogado usuarioLogado;
    private final AcessoMotorista acessoMotorista;

    public byte[] gerar(Long osId) {
        OrdemServico os = ordemServicoRepository.findById(osId)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(osId));
        // HU07: motorista so imprime as OS dele
        if (!acessoMotorista.podeVer(os)) {
            throw new OrdemServicoNaoEncontradoException(osId);
        }
        List<ParadaOs> paradas = paradaOsRepository.findByOrdemServicoIdOrderByOrdemParadaAsc(osId);
        List<Transfer> transfers = os.getTransfers().stream()
                .sorted(Comparator.comparing(Transfer::getHoraTransfer, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(documento, saida);
        documento.open();

        documento.add(new Paragraph("AJT Viagens e Turismo", NORMAL));
        documento.add(new Paragraph("Ordem de Serviço nº " + os.getId(), TITULO));
        documento.add(Chunk.NEWLINE);

        documento.add(dadosDaOs(os));

        documento.add(secao("Roteiro"));
        documento.add(paradas.isEmpty()
                ? new Paragraph("Nenhuma parada cadastrada nesta ordem de serviço.", NORMAL)
                : tabelaParadas(paradas));

        documento.add(secao("Transfers"));
        documento.add(transfers.isEmpty()
                ? new Paragraph("Nenhum transfer vinculado a esta ordem de serviço.", NORMAL)
                : tabelaTransfers(transfers));

        documento.add(Chunk.NEWLINE);
        documento.add(Chunk.NEWLINE);
        documento.add(new Paragraph("Assinatura do motorista: ____________________________________", NORMAL));
        documento.add(Chunk.NEWLINE);
        documento.add(new Paragraph("Emitido em " + LocalDateTime.now(PainelService.FUSO_OPERACAO).format(DATA_HORA)
                + " por " + usuarioLogado.usernameOuSistema(), RODAPE));

        documento.close();
        return saida.toByteArray();
    }

    private PdfPTable dadosDaOs(OrdemServico os) {
        Motorista motorista = os.getMotorista();
        Veiculo veiculo = os.getVeiculo();

        PdfPTable tabela = new PdfPTable(new float[]{1.2f, 3f});
        tabela.setWidthPercentage(100);
        linha(tabela, "Data", os.getDataServico() != null ? os.getDataServico().format(DATA) : SEM_INFO);
        linha(tabela, "Status", os.getStatus() != null ? os.getStatus().name() : SEM_INFO);
        linha(tabela, "Motorista", motorista == null ? "Não definido"
                : motorista.getNome() + (motorista.getTelefone() != null ? "  •  Tel.: " + motorista.getTelefone() : ""));
        linha(tabela, "Veículo", veiculo == null ? "Não definido"
                : veiculo.getLabel() + "  •  Placa " + veiculo.getPlaca() + "  •  " + veiculo.getCapacidade() + " lugares");
        return tabela;
    }

    private PdfPTable tabelaParadas(List<ParadaOs> paradas) {
        PdfPTable tabela = new PdfPTable(new float[]{0.5f, 0.9f, 3.2f, 1.3f, 1.3f});
        tabela.setWidthPercentage(100);
        cabecalho(tabela, "#", "Horário", "Local", "Ação", "Transfers");
        for (ParadaOs parada : paradas) {
            celula(tabela, String.valueOf(parada.getOrdemParada()));
            celula(tabela, hora(parada.getHorarioPrevisto()));
            celula(tabela, parada.getLocalParada());
            celula(tabela, parada.getAcao() != null ? parada.getAcao().name() : SEM_INFO);
            celula(tabela, parada.getTransfers().isEmpty() ? SEM_INFO
                    : parada.getTransfers().stream().map(Transfer::getId).sorted().map(id -> "#" + id).collect(Collectors.joining(", ")));
        }
        return tabela;
    }

    private PdfPTable tabelaTransfers(List<Transfer> transfers) {
        PdfPTable tabela = new PdfPTable(new float[]{0.6f, 0.8f, 2f, 2f, 2.8f});
        tabela.setWidthPercentage(100);
        cabecalho(tabela, "#", "Hora", "Origem", "Destino", "Passageiros");
        for (Transfer transfer : transfers) {
            celula(tabela, "#" + transfer.getId());
            celula(tabela, hora(transfer.getHoraTransfer()));
            celula(tabela, transfer.getOrigem());
            celula(tabela, transfer.getDestino());
            celula(tabela, passageiros(transfer));
        }
        return tabela;
    }

    private static String passageiros(Transfer transfer) {
        if (transfer.getPassageiros().isEmpty()) {
            return SEM_INFO;
        }
        String nomes = transfer.getPassageiros().stream().map(Passageiro::getNome).sorted()
                .collect(Collectors.joining(", "));
        return transfer.getPassageiros().size() + " pax: " + nomes;
    }

    private static Paragraph secao(String titulo) {
        Paragraph paragrafo = new Paragraph(titulo, SUBTITULO);
        paragrafo.setSpacingBefore(14);
        paragrafo.setSpacingAfter(6);
        return paragrafo;
    }

    private static void linha(PdfPTable tabela, String rotulo, String valor) {
        PdfPCell celulaRotulo = new PdfPCell(new Phrase(rotulo, NEGRITO));
        celulaRotulo.setPadding(5);
        tabela.addCell(celulaRotulo);
        celula(tabela, valor);
    }

    private static void cabecalho(PdfPTable tabela, String... titulos) {
        for (String titulo : titulos) {
            PdfPCell celula = new PdfPCell(new Phrase(titulo, CABECALHO_TABELA));
            celula.setBackgroundColor(COR_CABECALHO);
            celula.setHorizontalAlignment(Element.ALIGN_LEFT);
            celula.setPadding(5);
            tabela.addCell(celula);
        }
        tabela.setHeaderRows(1); // repete o cabecalho se a tabela quebrar de pagina
    }

    private static void celula(PdfPTable tabela, String texto) {
        PdfPCell celula = new PdfPCell(new Phrase(texto != null ? texto : SEM_INFO, NORMAL));
        celula.setPadding(5);
        tabela.addCell(celula);
    }

    private static String hora(LocalTime hora) {
        return hora != null ? hora.format(HORA) : SEM_INFO;
    }
}
