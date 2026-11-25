package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "proveedor",
        uniqueConstraints = @UniqueConstraint(name = "uk_proveedor_ruc", columnNames = "ruc"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "(10|15|17|20)\\d{9}", message = "El RUC debe tener 11 digitos y empezar con 10, 15, 17 o 20")
    @Column(nullable = false, length = 11)
    private String ruc;

    @NotBlank(message = "La razon social es obligatoria")
    @Size(max = 150)
    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Size(max = 200)
    @Column(length = 200)
    private String direccion;

    @Pattern(regexp = "|\\d{6,9}", message = "El telefono debe tener entre 6 y 9 digitos")
    @Column(length = 9)
    private String telefono;
}
