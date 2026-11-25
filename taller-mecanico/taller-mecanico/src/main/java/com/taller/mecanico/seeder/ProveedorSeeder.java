package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Proveedor;
import com.taller.mecanico.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProveedorSeeder {
    static final List<Proveedor> PROVEEDORES = List.of(
            proveedor("20601111111", "AUTOPARTES DEL SUR S.A.C.", "Av. Industrial 1450, Arequipa", "054221100"),
            proveedor("20602222222", "LUBRICANTES ANDINOS E.I.R.L.", "Jr. Los Pinos 230, Juliaca", "051332211"),
            proveedor("20603333333", "FRENOS Y EMBRAGUES PERU S.A.C.", "Av. Colonial 2210, Lima", "015443322"),
            proveedor("20604444444", "IMPORTADORA REPUESTOS ORIENTE S.R.L.", "Av. Grau 980, Cusco", "084245566"),
            proveedor("20605555555", "BATERIAS Y ELECTRICIDAD AUTOMOTRIZ S.A.C.", "Av. Tacna 615, Puno", "051366778"));

    private final ProveedorRepository repository;

    @Transactional
    public int sembrar() {
        Set<String> existentes = repository.findAll().stream().map(Proveedor::getRuc).collect(Collectors.toSet());
        List<Proveedor> nuevos = new ArrayList<>();
        for (Proveedor p : PROVEEDORES) {
            if (!existentes.contains(p.getRuc())) {
                nuevos.add(Proveedor.builder().ruc(p.getRuc()).razonSocial(p.getRazonSocial())
                        .direccion(p.getDireccion()).telefono(p.getTelefono()).build());
            }
        }
        repository.saveAll(nuevos);
        return nuevos.size();
    }

    private static Proveedor proveedor(String ruc, String razonSocial, String direccion, String telefono) {
        return Proveedor.builder().ruc(ruc).razonSocial(razonSocial).direccion(direccion).telefono(telefono).build();
    }
}
