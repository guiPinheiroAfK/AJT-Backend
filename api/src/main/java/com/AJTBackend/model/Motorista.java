package com.AJTBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "motoristas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Motorista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 20)
    private String cnh;

    @Column(length = 20)
    private String telefone;

    @Column(name = "latitude_atual")
    private Double latitudeAtual;

    @Column(name = "longitude_atual")
    private Double longitudeAtual;

    @OneToMany(mappedBy = "motorista", fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrdemServico> ordensServico = new ArrayList<>();
}