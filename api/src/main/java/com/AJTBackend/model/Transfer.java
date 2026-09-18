package com.AJTBackend.model;

import com.AJTBackend.model.enums.StatusTransfer;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // ordem de servico que agrupa este transfer (null enquanto aguarda OS)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "os_id")
    private OrdemServico ordemServico;

    // passageiros deste transfer (tabela de juncao transfer_passageiros)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "transfer_passageiros",
            joinColumns = @JoinColumn(name = "transfer_id"),
            inverseJoinColumns = @JoinColumn(name = "passageiro_id")
    )
    @Builder.Default
    private Set<Passageiro> passageiros = new HashSet<>();

    @OneToMany(mappedBy = "transfer", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PontoColeta> pontosColeta = new ArrayList<>();
}
