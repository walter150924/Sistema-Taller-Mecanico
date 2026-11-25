package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Compra;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.Proveedor;
import com.taller.mecanico.model.TipoDocumentoCompra;
import com.taller.mecanico.repository.ProveedorRepository;
import com.taller.mecanico.service.CompraService;
import com.taller.mecanico.service.CompraService.LineaCompra;
import com.taller.mecanico.service.CompraService.NuevaCompra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@RequiredArgsConstructor
class ComprasSimuladas {
    static final BigDecimal PRECIO_ECONOMICO = new BigDecimal("100");

    private final CompraService compraService;
    private final ProveedorRepository proveedorRepository;
    private final Map<String, Integer> correlativos = new HashMap<>();

    static int cantidadReposicion(Repuesto p, Random azar) {
        return p.getPrecio().compareTo(PRECIO_ECONOMICO) < 0 ? 24 + azar.nextInt(37) : 3 + azar.nextInt(6);
    }

    Compra comprar(Map<Repuesto, Integer> cantidades, LocalDateTime fecha, Random azar) {
        List<Proveedor> proveedores = proveedorRepository.findAll();
        Proveedor proveedor = proveedores.get(azar.nextInt(proveedores.size()));
        int correlativo = correlativos.merge(proveedor.getRuc(), 100 + azar.nextInt(400), (a, b) -> a + 1 + azar.nextInt(20));

        List<LineaCompra> lineas = cantidades.entrySet().stream()
                .map(e -> new LineaCompra(e.getKey().getId(), e.getValue(), costo(e.getKey(), azar)))
                .toList();
        return compraService.registrar(new NuevaCompra(
                proveedor.getRuc(), proveedor.getRazonSocial(),
                TipoDocumentoCompra.FACTURA,
                "F" + String.format("%03d", 1 + proveedor.getRuc().charAt(10) % 3) + "-" + String.format("%06d", correlativo),
                fecha.toLocalDate(), true, false, null, lineas), fecha);
    }

    private static BigDecimal costo(Repuesto p, Random azar) {
        double margen = 0.60 + azar.nextDouble() * 0.15;
        return p.getPrecio().divide(new BigDecimal("1.18"), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(margen)).setScale(2, RoundingMode.HALF_UP)
                .max(new BigDecimal("0.10"));
    }
}
