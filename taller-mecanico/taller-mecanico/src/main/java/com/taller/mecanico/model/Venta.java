package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venta",
        uniqueConstraints = @UniqueConstraint(name = "uk_venta_comprobante", columnNames = "comprobante"),
        indexes = @Index(name = "ix_venta_fecha", columnList = "fecha"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"cliente", "detalles"})
@EqualsAndHashCode(of = "id")
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private TipoComprobante tipoComprobante;

    @NotBlank
    @Column(nullable = false, length = 4)
    private String serie;

    @NotNull
    @Column(nullable = false)
    private Integer numero;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String comprobante;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fecha;

    @NotNull(message = "Selecciona un cliente")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "aplica_igv", nullable = false)
    private boolean aplicaIgv;

    @Column(name = "precios_incluyen_igv", nullable = false)
    private boolean preciosIncluyenIgv;

    @NotNull
    @Column(name = "tasa_igv", nullable = false, precision = 5, scale = 4)
    private BigDecimal tasaIgv;

    @NotNull
    @Column(name = "base_imponible", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoVenta estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    @Size(max = 255)
    @Column(length = 255)
    private String observacion;

    @Valid
    @NotEmpty(message = "Agrega al menos un repuesto al carrito")
    @Builder.Default
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("item ASC")
    private List<VentaDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(VentaDetalle detalle) {
        detalle.setVenta(this);
        detalle.setItem(detalles.size() + 1);
        detalles.add(detalle);
    }

    public boolean isAnulada() {
        return estado == EstadoVenta.ANULADA;
    }

    public static String formatearComprobante(String serie, int numero) {
        return serie + "-" + String.format("%08d", numero);
    }
}
