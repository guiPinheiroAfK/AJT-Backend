package com.AJTBackend.model;

import jakarta.persistence.*;
import com.AJTBackend.model.enums.StatusTransfer;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transfers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_transfer", nullable = false)
    private LocalDate dataTransfer;

    @Column(name = "hora_transfer", nullable = false)
    private LocalTime horaTransfer;

    @Column(nullable = false, length = 100)
    private String origem;

    @Column(nullable = false, length = 100)
    private String destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusTransfer status = StatusTransfer.AGUARDANDO_OS;

    @Column(name = "valor_base", precision = 10, scale = 2)
    private BigDecimal valorBase;

    @Column(name = "valor_original", precision = 10, scale = 2)
    private BigDecimal valorOriginal;

    @Column(name = "moeda_origem", length = 10)
    private String moedaOrigem;

    @Column(name = "os_id")
    private Long osId;

    @OneToMany(mappedBy = "transfer", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PontoColeta> pontosColeta = new ArrayList<>();
}
