package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Categoria;
import com.taller.mecanico.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CategoriaSeeder {
    static final List<String> NOMBRES = List.of(
            "Aceites y lubricantes", "Filtros", "Frenos", "Suspension", "Direccion",
            "Encendido", "Bujias", "Baterias", "Sistema electrico", "Iluminacion",
            "Refrigeracion", "Transmision", "Embrague", "Escape", "Correas y cadenas",
            "Neumaticos", "Plumillas", "Liquidos y aditivos", "Rodamientos", "Empaquetaduras");

    private static final List<String> PLANTILLAS_DESCRIPCION = List.of(
            "Repuestos de %s",
            "Linea de %s para autos y camionetas",
            "%s de marcas originales y alternativas",
            "Stock de %s para el taller");

    private final CategoriaRepository repository;

    @Transactional
    public int sembrar() {
        Set<String> existentes = repository.findAll().stream()
                .map(Categoria::getCodigoCategoria)
                .collect(Collectors.toSet());

        List<Categoria> nuevas = new ArrayList<>();
        for (int i = 0; i < NOMBRES.size(); i++) {
            String codigo = String.format("CAT-%03d", i + 1);
            if (existentes.contains(codigo)) {
                continue;
            }
            String nombre = NOMBRES.get(i);

            String plantilla = PLANTILLAS_DESCRIPCION.get(i % PLANTILLAS_DESCRIPCION.size());
            String descripcion = i % 7 == 6
                    ? null
                    : String.format(plantilla, plantilla.startsWith("%s") ? nombre : nombre.toLowerCase());
            nuevas.add(Categoria.builder()
                    .codigoCategoria(codigo)
                    .nombre(nombre)
                    .descripcion(descripcion)
                    .build());
        }
        repository.saveAll(nuevas);
        return nuevas.size();
    }
}
