package AJTBackend.controller;

import AJTBackend.dto.VeiculoRequestDTO;
import AJTBackend.dto.VeiculoResponseDTO;
import AJTBackend.service.VeiculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/veiculos")
@RequiredArgsConstructor
public class VeiculoController {

    private final VeiculoService veiculoService;

    @GetMapping
    public ResponseEntity<List<VeiculoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(veiculoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VeiculoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(veiculoService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<VeiculoResponseDTO> buscarPorPlaca(@RequestParam String placa) {
        return ResponseEntity.ok(veiculoService.buscarPorPlaca(placa));
    }

    @PostMapping
    public ResponseEntity<VeiculoResponseDTO> criar(@RequestBody VeiculoRequestDTO dto) {
        VeiculoResponseDTO criado = veiculoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VeiculoResponseDTO> atualizar(@PathVariable Long id,
                                                        @RequestBody VeiculoRequestDTO dto) {
        return ResponseEntity.ok(veiculoService.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VeiculoResponseDTO> atualizarParcial(@PathVariable Long id,
                                                               @RequestBody VeiculoRequestDTO dto) {
        return ResponseEntity.ok(veiculoService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        veiculoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}