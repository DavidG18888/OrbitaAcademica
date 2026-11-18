/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.orbitaacademica.dao;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CrearAdministrador {

    public static void main(String[] args) {

        TursoDAO dao = new TursoDAO();

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        String nuevaClave = encoder.encode("admin123");

        try {

            dao.ejecutarSQL(
                    "UPDATE Usuarios SET clave = ? WHERE usuario = ?",
                    nuevaClave,
                    "admin"
            );

            System.out.println("Contraseña actualizada correctamente.");
            System.out.println("Usuario: admin");
            System.out.println("Contraseña: admin123");

        } catch (Exception e) {

            System.out.println("Error al cambiar la contraseña:");
            e.printStackTrace();

        }
    }
}