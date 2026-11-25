package com.taller.mecanico.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento_inventario",
        indexes = {
                @Index(name = "ix_movimiento_repuesto", columnList = "repuesto_id, fecha"),
                @Index(name = "ix_movimiento_venta", columnList = "venta_id"),
                @Index(name = "ix_movimiento_compra", columnList = "compra_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "repuesto")
@EqualsAndHashCode(of = "id")
public class MovimientoInventario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repuesto_id", nullable = false)
    private Repuesto repuesto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "stock_resultante", nullable = false)
    private Integer stockResultante;

    @Column(name = "costo_unitario", precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    @Column(nullable = false, length = 40)
    private String documento;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "compra_id")
    private Long compraId;

    public int getEntrada() {
        return tipo.isEntrada() ? cantidad : 0;
    }

    public int getSalida() {
        return tipo.isEntrada() ? 0 : cantidad;
    }
}
