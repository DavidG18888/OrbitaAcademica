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

        try {

            // Eliminar administrador anterior
            dao.ejecutarSQL(
                    "DELETE FROM Usuarios WHERE usuario = ?",
                    "admin"
            );

            // Crear contraseña cifrada
            String clave = encoder.encode("admin123");

            // Crear nuevo administrador
            dao.ejecutarSQL(
                    "INSERT INTO Usuarios "
                    + "(usuario, nombre, correo, clave, rol, estado) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                    "admin",
                    "Administrador",
                    "admin@orbitaacademica.com",
                    clave,
                    "ADMIN",
                    "ACTIVO"
            );

            System.out.println("--------------------------------");
            System.out.println("ADMINISTRADOR CREADO CORRECTAMENTE");
            System.out.println("Usuario: admin");
            System.out.println("Contraseña: admin123");
            System.out.println("--------------------------------");

        } catch (Exception e) {

            System.out.println("Error al crear administrador:");
            e.printStackTrace();

        }
    }
}