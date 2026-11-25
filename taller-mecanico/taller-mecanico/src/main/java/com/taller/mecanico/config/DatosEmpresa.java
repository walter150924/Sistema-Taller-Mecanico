package com.taller.mecanico.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.empresa")
@Getter
@Setter
public class DatosEmpresa {
    private String razonSocial = "MI EMPRESA S.A.C.";
    private String nombreComercial = "Mi Empresa";
    private String ruc = "20000000001";
    private String direccion = "Av. Principal 123, Lima";
    private String telefono = "";
    private String email = "";
}
