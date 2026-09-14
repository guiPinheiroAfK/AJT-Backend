package com.AJTBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "paradas_os")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParadaOs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "os_id", nullable = false)
    private OrdemServico ordemServico;

    @Column(name = "ordem_parada", nullable = false)
    private Integer ordemParada;

    @Column(name = "local_parada", nullable = false, length = 100)
    private String localParada;

    private Double latitude;

    private Double longitude;

    @Column(name = "horario_previsto")
    private LocalTime horarioPrevisto;

    @Column(length = 50)
    private String acao;

    @Column(name = "status_parada", length = 20)
    @Builder.Default
    private String statusParada = "PENDENTE";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "parada_os_transfers",
            joinColumns = @JoinColumn(name = "parada_os_id"),
            inverseJoinColumns = @JoinColumn(name = "transfer_id")
    )
    @Builder.Default
    private Set<Transfer> transfers = new HashSet<>();
}
