package com.AJTBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ABERTA";
}
