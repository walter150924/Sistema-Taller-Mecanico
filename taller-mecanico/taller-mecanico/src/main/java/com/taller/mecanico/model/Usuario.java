package com.taller.mecanico.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "usuario",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_nombre", columnNames = "nombre_usuario"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "claveCifrada")
@EqualsAndHashCode(of = "id")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El usuario es obligatorio")
    @Pattern(regexp = "[a-z0-9._]{4,30}",
            message = "Usa de 4 a 30 caracteres: minusculas, numeros, punto o guion bajo")
    @Column(name = "nombre_usuario", nullable = false, length = 30)
    private String nombreUsuario;

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
    @Column(name = "nombre_completo", nullable = false, length = 120)
    private String nombreCompleto;

    @Column(name = "clave_cifrada", nullable = false, length = 200)
    private String claveCifrada;

    @NotNull(message = "Selecciona un rol")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    public boolean esMecanico() {
        return rol == Rol.MECANICO;
    }

    public boolean tiene(Permiso permiso) {
        return activo && permiso.permiteA(rol);
    }
}
