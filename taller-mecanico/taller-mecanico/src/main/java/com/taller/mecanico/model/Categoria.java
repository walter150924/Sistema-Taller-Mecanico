package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "categoria",
        uniqueConstraints = @UniqueConstraint(name = "uk_categoria_codigo", columnNames = "codigo_categoria"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
    @Column(nullable = false, length = 255)
    private String nombre;

    @NotBlank(message = "El codigo de categoria es obligatorio")
    @Size(max = 255)
    @Column(name = "codigo_categoria", nullable = false, length = 255)
    private String codigoCategoria;

    @Size(max = 255)
    @Column(length = 255)
    private String descripcion;
}
