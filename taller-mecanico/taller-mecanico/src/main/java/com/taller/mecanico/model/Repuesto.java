package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "repuesto",
        uniqueConstraints = @UniqueConstraint(name = "uk_repuesto_codigo", columnNames = "codigo_repuesto"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Repuesto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
    @Column(nullable = false, length = 255)
    private String nombre;

    @NotBlank(message = "El codigo de repuesto es obligatorio")
    @Size(max = 255)
    @Column(name = "codigo_repuesto", nullable = false, length = 255)
    private String codigoRepuesto;

    @Size(max = 255)
    @Column(length = 255)
    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener como maximo 8 enteros y 2 decimales")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer stock = 0;

    @Builder.Default
    @Min(value = 0, message = "El stock minimo no puede ser negativo")
    @Max(value = 99999, message = "El stock minimo maximo es 99999")
    @Column(name = "stock_minimo", nullable = false, columnDefinition = "integer default 5")
    private Integer stockMinimo = 5;

    @Column(name = "costo_promedio", precision = 12, scale = 4)
    private BigDecimal costoPromedio;

    public boolean isSinStock() {
        return stock == null || stock <= 0;
    }

    public boolean isStockBajo() {
        return !isSinStock() && stock <= stockMinimo;
    }
}
