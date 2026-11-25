package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "compra",
        uniqueConstraints = @UniqueConstraint(name = "uk_compra_codigo", columnNames = "codigo"),
        indexes = @Index(name = "ix_compra_fecha", columnList = "fecha"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"proveedor", "detalles"})
@EqualsAndHashCode(of = "id")
public class Compra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String codigo;

    @NotNull
    @Column(nullable = false)
    private Integer numero;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fecha;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private TipoDocumentoCompra tipoDocumento;

    @NotBlank
    @Size(max = 20)
    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @NotNull
    @Column(name = "fecha_documento", nullable = false)
    private LocalDate fechaDocumento;

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
    @Column(nullable = false, length = 12)
    private EstadoCompra estado;

    @Size(max = 255)
    @Column(length = 255)
    private String observacion;

    @Builder.Default
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("item ASC")
    private List<CompraDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(CompraDetalle detalle) {
        detalle.setCompra(this);
        detalle.setItem(detalles.size() + 1);
        detalles.add(detalle);
    }

    public boolean isAnulada() {
        return estado == EstadoCompra.ANULADA;
    }

    public static String formatearCodigo(int numero) {
        return "CP-" + String.format("%08d", numero);
    }
}
