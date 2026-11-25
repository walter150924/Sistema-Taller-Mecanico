package com.taller.mecanico.seguridad;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CifradorClave {
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 65_536;
    private static final int BYTES_SAL = 16;
    private static final int BITS_CLAVE = 256;
    private static final String SEPARADOR = ":";

    private final SecureRandom azar = new SecureRandom();
    private final Base64.Encoder codificador = Base64.getEncoder();
    private final Base64.Decoder decodificador = Base64.getDecoder();

    public String cifrar(String clave) {
        byte[] sal = new byte[BYTES_SAL];
        azar.nextBytes(sal);
        return ITERACIONES + SEPARADOR + codificador.encodeToString(sal)
                + SEPARADOR + codificador.encodeToString(derivar(clave, sal, ITERACIONES));
    }

    public boolean coincide(String clave, String claveCifrada) {
        String[] partes = claveCifrada.split(SEPARADOR);
        int iteraciones = Integer.parseInt(partes[0]);
        byte[] sal = decodificador.decode(partes[1]);
        byte[] esperado = decodificador.decode(partes[2]);
        return MessageDigest.isEqual(esperado, derivar(clave, sal, iteraciones));
    }

    private byte[] derivar(String clave, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(clave.toCharArray(), sal, iteraciones, BITS_CLAVE);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la contraseña", e);
        } finally {
            especificacion.clearPassword();
        }
    }
}
