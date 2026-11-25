package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "orden_servicio",
        uniqueConstraints = @UniqueConstraint(name = "uk_orden_codigo", columnNames = "codigo"),
        indexes = @Index(name = "ix_orden_estado", columnList = "estado"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"vehiculo", "mecanico", "servicios", "repuestos"})
@EqualsAndHashCode(of = "id")
public class OrdenServicio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String codigo;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDateTime fechaIngreso;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @NotNull(message = "Selecciona el vehiculo")
    @ManyToOne(optional = false)
    @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;

    @ManyToOne
    @JoinColumn(name = "mecanico_id")
    private Usuario mecanico;

    @NotNull(message = "Ingresa el kilometraje")
    @Min(value = 0, message = "El kilometraje no puede ser negativo")
    @Column(name = "kilometraje_ingreso", nullable = false)
    private Integer kilometrajeIngreso;

    @NotBlank(message = "Describe el problema que reporta el cliente")
    @Size(max = 500, message = "Maximo 500 caracteres")
    @Column(name = "problema_reportado", nullable = false, length = 500)
    private String problemaReportado;

    @Size(max = 1000, message = "Maximo 1000 caracteres")
    @Column(length = 1000)
    private String diagnostico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrden estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    @Builder.Default
    @Column(name = "total_servicios", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalServicios = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_repuestos", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRepuestos = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "base_imponible", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Builder.Default
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LineaServicio> servicios = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LineaRepuesto> repuestos = new ArrayList<>();

    public Cliente getCliente() {
        return vehiculo.getCliente();
    }

    public String getNombreMecanico() {
        return mecanico == null ? "Sin asignar" : mecanico.getNombreCompleto();
    }

    public boolean estaAsignadaA(Usuario usuario) {
        return mecanico != null && mecanico.getId().equals(usuario.getId());
    }

    public void agregarServicio(Servicio servicio) {
        servicios.add(LineaServicio.builder().orden(this).servicio(servicio).precio(servicio.getPrecio()).build());
    }

    public Optional<LineaRepuesto> lineaDe(Repuesto repuesto) {
        return repuestos.stream().filter(l -> l.getRepuesto().getId().equals(repuesto.getId())).findFirst();
    }

    public void agregarRepuesto(Repuesto repuesto, int cantidad) {
        lineaDe(repuesto).ifPresentOrElse(
                linea -> linea.setCantidad(linea.getCantidad() + cantidad),
                () -> repuestos.add(LineaRepuesto.builder().orden(this).repuesto(repuesto)
                        .cantidad(cantidad).precioUnitario(repuesto.getPrecio()).build()));
    }

    public static String formatearCodigo(long numero) {
        return String.format("OS-%06d", numero);
    }
}
