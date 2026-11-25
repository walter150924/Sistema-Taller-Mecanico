package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Categoria;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.repository.CategoriaRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RepuestoSeeder {
    private record Plantilla(String nombre, String categoria, double precio,
                             String presentacionA, String presentacionB, double factorB) {}

    private static final List<Plantilla> PLANTILLAS = List.of(
            new Plantilla("Aceite de motor 20W-50", "Aceites y lubricantes", 28.00, "1 galon", "balde 5 galones", 4.6),
            new Plantilla("Aceite sintetico 5W-30", "Aceites y lubricantes", 45.00, "1 litro", "1 galon", 3.5),
            new Plantilla("Filtro de aceite", "Filtros", 18.00, "Toyota", "Hyundai", 1.1),
            new Plantilla("Filtro de aire", "Filtros", 35.00, "Toyota", "Kia", 1.1),
            new Plantilla("Filtro de combustible", "Filtros", 30.00, "gasolina", "diesel", 1.6),
            new Plantilla("Pastillas de freno delanteras", "Frenos", 85.00, "sedan", "camioneta", 1.5),
            new Plantilla("Zapatas de freno", "Frenos", 70.00, "sedan", "camioneta", 1.4),
            new Plantilla("Disco de freno", "Frenos", 140.00, "sedan", "camioneta", 1.5),
            new Plantilla("Liquido de frenos DOT 4", "Liquidos y aditivos", 22.00, "500 ml", "1 litro", 1.8),
            new Plantilla("Amortiguador delantero", "Suspension", 180.00, "sedan", "camioneta", 1.6),
            new Plantilla("Terminal de direccion", "Direccion", 55.00, "izquierdo", "derecho", 1.0),
            new Plantilla("Bujia de iridio", "Bujias", 38.00, "unidad", "juego x4", 3.8),
            new Plantilla("Bateria 12V", "Baterias", 320.00, "13 placas", "15 placas", 1.3),
            new Plantilla("Foco halogeno H4", "Iluminacion", 25.00, "unidad", "par", 1.9),
            new Plantilla("Refrigerante", "Refrigeracion", 30.00, "1 galon", "balde 5 galones", 4.5),
            new Plantilla("Bomba de agua", "Refrigeracion", 210.00, "1.5 L", "2.0 L", 1.2),
            new Plantilla("Kit de embrague", "Embrague", 450.00, "sedan", "camioneta", 1.5),
            new Plantilla("Faja de distribucion", "Correas y cadenas", 120.00, "kit simple", "kit con tensor", 1.8),
            new Plantilla("Plumilla limpiaparabrisas", "Plumillas", 20.00, "18 pulgadas", "22 pulgadas", 1.2),
            new Plantilla("Rodaje de rueda", "Rodamientos", 75.00, "delantero", "posterior", 1.1));

    private final RepuestoRepository repository;
    private final CategoriaRepository categoriaRepository;

    @Transactional
    public int sembrar() {
        Set<String> existentes = repository.findAll().stream()
                .map(Repuesto::getCodigoRepuesto)
                .collect(Collectors.toSet());
        Map<String, Categoria> categorias = categoriaRepository.findAll().stream()
                .collect(Collectors.toMap(Categoria::getNombre, Function.identity(), (a, b) -> a));

        List<Repuesto> nuevos = new ArrayList<>();
        int numero = 0;
        for (Plantilla p : PLANTILLAS) {
            for (boolean esB : new boolean[]{false, true}) {
                numero++;
                String codigo = String.format("REP-%03d", numero);
                if (existentes.contains(codigo)) {
                    continue;
                }
                String presentacion = esB ? p.presentacionB() : p.presentacionA();
                BigDecimal precio = BigDecimal.valueOf(esB ? p.precio() * p.factorB() : p.precio())
                        .setScale(2, RoundingMode.HALF_UP);

                Categoria categoria = categorias.get(p.categoria());

                nuevos.add(Repuesto.builder()
                        .codigoRepuesto(codigo)
                        .nombre(p.nombre() + " " + presentacion)
                        .descripcion(numero % 6 == 0 ? null : p.nombre() + ", presentacion " + presentacion)
                        .precio(precio)
                        .categoria(categoria)
                        .build());
            }
        }
        repository.saveAll(nuevos);
        return nuevos.size();
    }
}
