package AJTBackend.controller;

import AJTBackend.dto.PassageiroRequestDTO;
import AJTBackend.dto.PassageiroResponseDTO;
import AJTBackend.service.PassageiroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passageiros")
@RequiredArgsConstructor
public class PassageiroController {

    private final PassageiroService service;

    @GetMapping
    public ResponseEntity<List<PassageiroResponseDTO>> listarTodos() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<PassageiroResponseDTO>> buscarPorNacionalidade(
            @RequestParam String nacionalidade) {
        return ResponseEntity.ok(service.buscarPorNacionalidade(nacionalidade));
    }

    @PostMapping
    public ResponseEntity<PassageiroResponseDTO> criar(
            @Valid @RequestBody PassageiroRequestDTO dto) {
        PassageiroResponseDTO criado = service.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody PassageiroRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> atualizarParcial(
            @PathVariable Long id,
            @RequestBody PassageiroRequestDTO dto) {
        return ResponseEntity.ok(service.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
