package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClienteSeeder {
    private static final int CANTIDAD = 40;
    private static final long SEMILLA = 2026L;

    private static final List<String> NOMBRES = List.of(
            "Maria", "Jose", "Lucia", "Carlos", "Ana", "Luis", "Rosa", "Pedro", "Elena", "Jorge",
            "Carmen", "Miguel", "Sofia", "Diego", "Valeria", "Juan", "Camila", "Andres", "Gabriela", "Ricardo",
            "Daniela", "Fernando", "Patricia", "Alejandro", "Milagros", "Renzo", "Claudia", "Hugo", "Fiorella", "Sebastian");

    private static final List<String> APELLIDOS = List.of(
            "Quispe", "Flores", "Garcia", "Rojas", "Mamani", "Torres", "Huaman", "Diaz", "Vargas", "Castillo",
            "Ramirez", "Chavez", "Mendoza", "Gutierrez", "Salazar", "Ramos", "Espinoza", "Ccori", "Paredes", "Silva",
            "Cruz", "Sanchez", "Condori", "Herrera", "Medina", "Aguilar", "Rivera", "Navarro", "Cardenas", "Leon");

    private static final List<String> DOMINIOS = List.of(
            "gmail.com", "hotmail.com", "outlook.com", "yahoo.com", "correo.pe");

    private final ClienteRepository repository;

    @Transactional
    public int sembrar() {
        Set<String> dnisExistentes = repository.findAll().stream()
                .map(Cliente::getDni)
                .collect(Collectors.toSet());

        Random azar = new Random(SEMILLA);
        Set<String> nombresUsados = new HashSet<>();
        Set<String> dnisUsados = new HashSet<>();
        Set<String> correosUsados = new HashSet<>();
        List<Cliente> nuevas = new ArrayList<>();

        int generadas = 0;
        while (generadas < CANTIDAD) {
            String nombre = elegir(azar, NOMBRES);
            String apellido1 = elegir(azar, APELLIDOS);
            String apellido2 = elegir(azar, APELLIDOS);
            if (apellido1.equals(apellido2) || !nombresUsados.add(nombre + apellido1 + apellido2)) {
                continue;
            }

            String dni;
            do {
                dni = String.valueOf(10_000_000 + azar.nextInt(80_000_000));
            } while (!dnisUsados.add(dni));

            String correo;
            do {
                correo = (nombre + "." + apellido1 + azar.nextInt(100)).toLowerCase()
                        + "@" + elegir(azar, DOMINIOS);
            } while (!correosUsados.add(correo));

            boolean conCorreo = azar.nextDouble() < 0.85;
            boolean conTelefono = azar.nextDouble() < 0.70;
            String telefono = "9" + String.format("%08d", azar.nextInt(100_000_000));

            generadas++;
            if (dnisExistentes.contains(dni)) {
                continue;
            }
            nuevas.add(Cliente.builder()
                    .nombre(nombre)
                    .apellido(apellido1 + " " + apellido2)
                    .dni(dni)
                    .email(conCorreo ? correo : null)
                    .telefono(conTelefono ? telefono : null)
                    .build());
        }
        repository.saveAll(nuevas);
        return nuevas.size();
    }

    private static String elegir(Random azar, List<String> opciones) {
        return opciones.get(azar.nextInt(opciones.size()));
    }
}
