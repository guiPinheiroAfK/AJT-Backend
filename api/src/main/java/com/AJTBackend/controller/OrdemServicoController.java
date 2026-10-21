package com.AJTBackend.controller;

import com.AJTBackend.dto.FiltroOrdemServicoDTO;
import com.AJTBackend.dto.OrdemServicoDoDiaDTO;
import com.AJTBackend.dto.OrdemServicoRequestDTO;
import com.AJTBackend.dto.OrdemServicoResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.service.OrdemServicoService;
import com.AJTBackend.service.RelatorioOrdemServicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Ordens de serviço", description = "Agrupam transfers de um motorista e um veículo em um dia.")
@RestController
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;
    private final RelatorioOrdemServicoService relatorioOrdemServicoService;

    @Operation(summary = "Lista ordens de serviço (paginado, com filtros opcionais)",
            description = "Filtros somados (E): ?status=, ?dataInicio= e ?dataFim= (AAAA-MM-DD), ?motoristaId= e ?veiculoId=. "
                    + "O perfil MOTORISTA só recebe as próprias OS (o motoristaId informado é ignorado).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida"), @ApiResponse(responseCode = "400", description = "Período inválido (início depois do fim)")})
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<OrdemServicoResponseDTO>> listar(@ParameterObject FiltroOrdemServicoDTO filtro,
                                                                             @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ordemServicoService.listar(filtro, pageable));
    }

    @Operation(summary = "Minhas ordens de serviço do dia, com as paradas (perfil MOTORISTA)",
            description = "Sem ?data usa hoje (fuso de São Paulo). Usa o cadastro de motorista ligado ao usuário logado; "
                    + "sem vínculo, devolve lista vazia.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida (pode vir vazia)"), @ApiResponse(responseCode = "403", description = "Só para o perfil MOTORISTA")})
    @GetMapping("/minhas")
    public ResponseEntity<List<OrdemServicoDoDiaDTO>> minhasDoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(ordemServicoService.minhasDoDia(data));
    }

    @Operation(summary = "Busca ordem de serviço por ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ordemServicoService.buscarPorId(id));
    }

    @Operation(summary = "Busca ordens de serviço por status")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Página devolvida (pode vir vazia)")})
    @GetMapping("/buscar")
    public ResponseEntity<PaginaResponseDTO<OrdemServicoResponseDTO>> buscarPorStatus(@RequestParam StatusOrdemServico status,
                                                                    @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ordemServicoService.buscarPorStatus(status, pageable));
    }

    @Operation(summary = "Relatório em PDF da ordem de serviço (para imprimir e entregar ao motorista)",
            description = "Traz motorista, veículo, roteiro de paradas e transfers com passageiros. Não traz valores nem documentos.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "PDF gerado", content = @Content(mediaType = "application/pdf")), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> relatorioPdf(@PathVariable Long id) {
        byte[] pdf = relatorioOrdemServicoService.gerar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // inline: o navegador abre o PDF numa aba (dali da pra imprimir ou baixar)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("ordem-servico-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    @Operation(summary = "Cria ordem de serviço")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Criado"), @ApiResponse(responseCode = "400", description = "Dados inválidos ou registro duplicado"), @ApiResponse(responseCode = "404", description = "Recurso relacionado não encontrado")})
    @PostMapping
    public ResponseEntity<OrdemServicoResponseDTO> criar(@Valid @RequestBody OrdemServicoRequestDTO dto) {
        OrdemServicoResponseDTO criado = ordemServicoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @Operation(summary = "Atualiza ordem de serviço (substituição total)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PutMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> atualizar(@PathVariable Long id,
                                                              @Valid @RequestBody OrdemServicoRequestDTO dto) {
        return ResponseEntity.ok(ordemServicoService.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza ordem de serviço parcialmente (só os campos enviados)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PatchMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                     @Validated(OnPatch.class) @RequestBody OrdemServicoRequestDTO dto) {
        return ResponseEntity.ok(ordemServicoService.atualizarParcial(id, dto));
    }

    @Operation(summary = "Exclui ordem de serviço")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Excluído"), @ApiResponse(responseCode = "404", description = "Não encontrado"), @ApiResponse(responseCode = "409", description = "Registro em uso por outro recurso")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        ordemServicoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
