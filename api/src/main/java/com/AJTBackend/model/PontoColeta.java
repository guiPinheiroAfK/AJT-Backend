package com.AJTBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "pontos_coleta")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PontoColeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Column(name = "local_coleta", nullable = false, length = 100)
    private String localColeta;

    @Column(name = "ordem_parada")
    private Integer ordemParada;

    @Column(name = "horario_previsto")
    private LocalTime horarioPrevisto;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;
}
