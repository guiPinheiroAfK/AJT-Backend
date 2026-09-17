package com.AJTBackend.model;

import jakarta.persistence.*;
import com.AJTBackend.model.enums.StatusOrdemServico;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordens_servico")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdemServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_servico", nullable = false)
    private LocalDate dataServico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motorista_id")
    private Motorista motorista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veiculo_id")
    private Veiculo veiculo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusOrdemServico status = StatusOrdemServico.ABERTA;

    @OneToMany(mappedBy = "ordemServico", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ParadaOs> paradas = new ArrayList<>();
}
