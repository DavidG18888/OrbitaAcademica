package com.mycompany.orbitaacademica.service;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class InicioService {

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public InicioService(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    // Preparar la base de datos al iniciar el sistema
    @PostConstruct
    public void preparar() {

        try {

            // Tabla de usuarios
            dao.ejecutarSQL(
                    "CREATE TABLE IF NOT EXISTS Usuarios ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "usuario TEXT UNIQUE NOT NULL, "
                    + "nombre TEXT NOT NULL, "
                    + "correo TEXT UNIQUE, "
                    + "clave TEXT NOT NULL, "
                    + "rol TEXT NOT NULL, "
                    + "estado TEXT NOT NULL DEFAULT 'ACTIVO')"
            );

            // Tabla de estudiantes
            dao.ejecutarSQL(
                    "CREATE TABLE IF NOT EXISTS Estudiantes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "usuario_id INTEGER, "
                    + "codigo TEXT UNIQUE, "
                    + "nombre TEXT, "
                    + "apellido TEXT, "
                    + "carrera TEXT, "
                    + "estado TEXT DEFAULT 'ACTIVO')"
            );

            // Tabla de docentes
            dao.ejecutarSQL(
                    "CREATE TABLE IF NOT EXISTS Docentes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "usuario_id INTEGER, "
                    + "codigo TEXT UNIQUE, "
                    + "nombre TEXT, "
                    + "apellido TEXT, "
                    + "especialidad TEXT, "
                    + "estado TEXT DEFAULT 'ACTIVO')"
            );

            // Tabla de periodos académicos
            dao.ejecutarSQL(
                    "CREATE TABLE IF NOT EXISTS Periodos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nombre TEXT, "
                    + "fecha_inicio TEXT, "
                    + "fecha_fin TEXT, "
                    + "estado TEXT DEFAULT 'ACTIVO')"
            );

            // Obtener credenciales del administrador
            String usuario = env("ADMIN_USER", "admin");
            String clave = env("ADMIN_PASSWORD", "OrbitaAdmin2026!");

            // Crear administrador si no existe
            if (dao.consultar(
                    "SELECT id FROM Usuarios WHERE usuario=?",
                    usuario
            ).isEmpty()) {

                dao.ejecutarSQL(
                        "INSERT INTO Usuarios(usuario,nombre,correo,clave,rol) "
                        + "VALUES(?,?,?,?,?)",
                        usuario,
                        "Administrador",
                        "admin@orbita.local",
                        encoder.encode(clave),
                        "ADMIN"
                );

                System.out.println("Administrador creado correctamente.");
            }

        } catch (Exception e) {

            System.err.println(
                    "No se pudo preparar Turso: " + e.getMessage()
            );

        }

    }

    // Leer variables de entorno
    private String env(String key, String def) {

        String valor = System.getenv(key);

        if (valor == null || valor.isBlank()) {
            return def;
        }

        return valor;
    }

}