package com.AJTBackend.model;

import com.AJTBackend.config.CriptografiaConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "passageiros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Passageiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "tipo_documento", nullable = false, length = 20)
    private String tipoDocumento;

    // cifrado com AES-256-GCM antes de ir pro banco (LGPD)
    @Convert(converter = CriptografiaConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String documento;

    @Column(length = 50)
    private String nacionalidade;
}