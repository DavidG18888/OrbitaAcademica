package com.mycompany.orbitaacademica.dao;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerarClave {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        String clave = "admin123";

        String hash = encoder.encode(clave);

        System.out.println("Contraseña: " + clave);
        System.out.println("Hash generado:");
        System.out.println(hash);
    }
}