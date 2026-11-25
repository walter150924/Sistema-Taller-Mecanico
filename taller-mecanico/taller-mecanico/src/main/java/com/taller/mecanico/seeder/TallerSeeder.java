package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.model.Rol;
import com.taller.mecanico.model.Servicio;
import com.taller.mecanico.model.Vehiculo;
import com.taller.mecanico.repository.ClienteRepository;
import com.taller.mecanico.repository.VehiculoRepository;
import com.taller.mecanico.service.ServicioService;
import com.taller.mecanico.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class TallerSeeder {
    private static final int VEHICULOS_DE_PRUEBA = 30;
    private static final long SEMILLA = 2026L;
    private static final String CLAVE_DE_PRUEBA = "taller123";
    private static final String LETRAS_PLACA = "ABCDEFGHJKLMNPRSTUVWXYZ";

    private record ModeloVehiculo(String marca, String modelo) {}

    private static final List<ModeloVehiculo> MODELOS = List.of(
            new ModeloVehiculo("Toyota", "Yaris"), new ModeloVehiculo("Toyota", "Corolla"),
            new ModeloVehiculo("Toyota", "Hilux"), new ModeloVehiculo("Hyundai", "Accent"),
            new ModeloVehiculo("Hyundai", "Tucson"), new ModeloVehiculo("Kia", "Rio"),
            new ModeloVehiculo("Kia", "Sportage"), new ModeloVehiculo("Nissan", "Sentra"),
            new ModeloVehiculo("Nissan", "Frontier"), new ModeloVehiculo("Chevrolet", "Sail"),
            new ModeloVehiculo("Suzuki", "Swift"), new ModeloVehiculo("Mitsubishi", "L200"));

    private static final List<String> COLORES = List.of("Blanco", "Plata", "Negro", "Rojo", "Azul", "Gris");

    private record ServicioBase(String nombre, String descripcion, String precio) {
        Servicio crear() {
            return Servicio.builder().nombre(nombre).descripcion(descripcion).precio(new BigDecimal(precio)).build();
        }
    }

    private static final List<ServicioBase> SERVICIOS = List.of(
            new ServicioBase("Cambio de aceite y filtro", "Incluye revision de niveles", "40.00"),
            new ServicioBase("Mantenimiento preventivo 10 000 km", "Revision general segun manual", "180.00"),
            new ServicioBase("Cambio de pastillas de freno", "Eje delantero o posterior", "60.00"),
            new ServicioBase("Rectificado de discos", "Por par de discos", "80.00"),
            new ServicioBase("Alineamiento y balanceo", "Cuatro ruedas", "70.00"),
            new ServicioBase("Afinamiento electronico", "Limpieza de inyectores y cambio de bujias", "150.00"),
            new ServicioBase("Diagnostico con escaner", "Lectura y borrado de codigos de falla", "50.00"),
            new ServicioBase("Cambio de embrague", "Mano de obra de desmontaje y montaje", "250.00"),
            new ServicioBase("Cambio de faja de distribucion", "Incluye tensor si corresponde", "200.00"),
            new ServicioBase("Mantenimiento del sistema de refrigeracion", "Purga y cambio de refrigerante", "90.00"),
            new ServicioBase("Revision del sistema electrico", "Bateria, alternador y arranque", "60.00"),
            new ServicioBase("Cambio de amortiguadores", "Por par", "120.00"));

    private final UsuarioService usuarioService;
    private final ServicioService servicioService;
    private final ClienteRepository clienteRepository;
    private final VehiculoRepository vehiculoRepository;

    @Transactional
    public int sembrar() {
        usuarioService.registrarSiNoExiste("recepcion", "Rosa Flores Diaz", Rol.RECEPCIONISTA, CLAVE_DE_PRUEBA);
        usuarioService.registrarSiNoExiste("jquispe", "Juan Quispe Mamani", Rol.MECANICO, CLAVE_DE_PRUEBA);
        usuarioService.registrarSiNoExiste("pmamani", "Pedro Mamani Condori", Rol.MECANICO, CLAVE_DE_PRUEBA);
        SERVICIOS.forEach(base -> servicioService.registrarSiNoExiste(base.crear()));
        return sembrarVehiculos();
    }

    private int sembrarVehiculos() {
        if (vehiculoRepository.count() > 0) {
            return 0;
        }
        List<Cliente> clientes = clienteRepository.findAll(Sort.by("id"));
        Random azar = new Random(SEMILLA);
        int cantidad = Math.min(VEHICULOS_DE_PRUEBA, clientes.size());
        for (int i = 0; i < cantidad; i++) {
            ModeloVehiculo modelo = MODELOS.get(azar.nextInt(MODELOS.size()));
            vehiculoRepository.save(Vehiculo.builder()
                    .placa(placa(i, azar))
                    .marca(modelo.marca())
                    .modelo(modelo.modelo())
                    .anio(2008 + azar.nextInt(17))
                    .color(COLORES.get(azar.nextInt(COLORES.size())))
                    .kilometraje(5_000 + azar.nextInt(195_000))
                    .cliente(clientes.get(i))
                    .build());
        }
        return cantidad;
    }

    private static String placa(int indice, Random azar) {
        char primera = LETRAS_PLACA.charAt(azar.nextInt(LETRAS_PLACA.length()));
        char segunda = LETRAS_PLACA.charAt(azar.nextInt(LETRAS_PLACA.length()));
        return String.format("%c%c%d-%03d", primera, segunda, indice % 10, 100 + indice * 29 % 900);
    }
}
