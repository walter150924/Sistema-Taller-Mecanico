package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "vehiculo",
        uniqueConstraints = @UniqueConstraint(name = "uk_vehiculo_placa", columnNames = "placa"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "cliente")
@EqualsAndHashCode(of = "id")
public class Vehiculo {
    public static final int ANIO_MINIMO = 1950;
    public static final int ANIO_MAXIMO = 2100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "La placa es obligatoria")
    @Pattern(regexp = "[A-Z0-9]{3}-[0-9]{3}", message = "La placa debe tener el formato ABC-123")
    @Column(nullable = false, length = 7)
    private String placa;

    @NotBlank(message = "La marca es obligatoria")
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String marca;

    @NotBlank(message = "El modelo es obligatorio")
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String modelo;

    @NotNull(message = "El año es obligatorio")
    @Min(value = ANIO_MINIMO, message = "El año no puede ser menor a 1950")
    @Max(value = ANIO_MAXIMO, message = "El año no es valido")
    @Column(nullable = false)
    private Integer anio;

    @Size(max = 30)
    @Column(length = 30)
    private String color;

    @NotNull(message = "El kilometraje es obligatorio")
    @Min(value = 0, message = "El kilometraje no puede ser negativo")
    @Column(nullable = false)
    private Integer kilometraje;

    @NotNull(message = "Selecciona el propietario")
    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    public String getDescripcion() {
        return marca + " " + modelo + " " + anio;
    }

    public void actualizarKilometraje(int kilometrajeLeido) {
        if (kilometrajeLeido > kilometraje) {
            kilometraje = kilometrajeLeido;
        }
    }
}
