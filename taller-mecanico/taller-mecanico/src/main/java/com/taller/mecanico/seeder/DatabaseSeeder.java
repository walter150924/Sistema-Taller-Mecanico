package com.taller.mecanico.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class DatabaseSeeder implements ApplicationRunner {
    private final CategoriaSeeder categoriaSeeder;
    private final RepuestoSeeder repuestoSeeder;
    private final ClienteSeeder clienteSeeder;
    private final ProveedorSeeder proveedorSeeder;
    private final InventarioSeeder inventarioSeeder;
    private final VentaSeeder ventaSeeder;
    private final TallerSeeder tallerSeeder;

    @Override
    public void run(ApplicationArguments args) {
        int categorias = categoriaSeeder.sembrar();
        int repuestos = repuestoSeeder.sembrar();
        int clientes = clienteSeeder.sembrar();
        int proveedores = proveedorSeeder.sembrar();
        int apertura = inventarioSeeder.sembrar();
        int ventas = ventaSeeder.sembrar();
        int vehiculos = tallerSeeder.sembrar();
        log.info("Seeder: {} categorias, {} repuestos, {} clientes, {} proveedores, {} compras de apertura, "
                + "{} ventas y {} vehiculos nuevos registrados.", categorias, repuestos, clientes, proveedores, apertura, ventas, vehiculos);
    }
}
