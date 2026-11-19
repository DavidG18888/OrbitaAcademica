package com.mycompany.orbitaacademica.service;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Service
public class InicioService {

    private static final String[] DDL = {
        "CREATE TABLE IF NOT EXISTS Usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT UNIQUE NOT NULL, nombre TEXT NOT NULL, correo TEXT UNIQUE, clave TEXT NOT NULL, rol TEXT NOT NULL, estado TEXT NOT NULL DEFAULT 'ACTIVO')",
        "CREATE TABLE IF NOT EXISTS Carreras (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE, nombre TEXT NOT NULL, estado TEXT DEFAULT 'ACTIVO')",
        "CREATE TABLE IF NOT EXISTS Estudiantes (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER, codigo TEXT UNIQUE, nombre TEXT, apellido TEXT, carrera TEXT, estado TEXT DEFAULT 'ACTIVO', carrera_id INTEGER, correo TEXT, telefono TEXT)",
        "CREATE TABLE IF NOT EXISTS Docentes (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER, codigo TEXT UNIQUE, nombre TEXT, apellido TEXT, especialidad TEXT, estado TEXT DEFAULT 'ACTIVO', correo TEXT, telefono TEXT)",
        "CREATE TABLE IF NOT EXISTS Periodos (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, fecha_inicio TEXT, fecha_fin TEXT, estado TEXT DEFAULT 'ACTIVO')",
        "CREATE TABLE IF NOT EXISTS Asignaturas (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE, nombre TEXT NOT NULL, uv INTEGER, carrera_id INTEGER, estado TEXT DEFAULT 'ACTIVO')",
        "CREATE TABLE IF NOT EXISTS Secciones (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT, asignatura_id INTEGER, docente_id INTEGER, periodo_id INTEGER, aula TEXT, cupo INTEGER, dia TEXT, hora_inicio TEXT, hora_fin TEXT)",
        "CREATE TABLE IF NOT EXISTS Matriculas (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER NOT NULL, seccion_id INTEGER NOT NULL, fecha TEXT DEFAULT (date('now')), estado TEXT DEFAULT 'ACTIVA', UNIQUE(estudiante_id, seccion_id))",
        "CREATE TABLE IF NOT EXISTS Calificaciones (id INTEGER PRIMARY KEY AUTOINCREMENT, matricula_id INTEGER UNIQUE NOT NULL, parcial1 REAL, parcial2 REAL, parcial3 REAL)",
        "CREATE TABLE IF NOT EXISTS Asistencia (id INTEGER PRIMARY KEY AUTOINCREMENT, matricula_id INTEGER NOT NULL, fecha TEXT NOT NULL, estado TEXT NOT NULL, UNIQUE(matricula_id, fecha))"
    };

    private static final String[] ALTER = {
        "ALTER TABLE Estudiantes ADD COLUMN carrera_id INTEGER",
        "ALTER TABLE Estudiantes ADD COLUMN correo TEXT",
        "ALTER TABLE Estudiantes ADD COLUMN telefono TEXT",
        "ALTER TABLE Docentes ADD COLUMN correo TEXT",
        "ALTER TABLE Docentes ADD COLUMN telefono TEXT"
    };

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public InicioService(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    // Preparar la base de datos al iniciar el sistema
    @PostConstruct
    public void iniciar() {
        for (String e : preparar()) {
            System.err.println("[Orbita] " + e);
        }
    }

    // Crea las tablas que falten (cada sentencia es independiente) y el administrador.
    // Devuelve la lista de errores encontrados (vacía si todo salió bien).
    public List<String> preparar() {

        List<String> errores = new ArrayList<>();

        for (String ddl : DDL) {
            try {
                dao.ejecutarSQL(ddl);
            } catch (Exception e) {
                errores.add(ddl.substring(0, Math.min(70, ddl.length())) + " -> " + e.getMessage());
            }
        }

        // Columnas nuevas en instalaciones anteriores (si ya existen, se ignora el error)
        for (String alter : ALTER) {
            try {
                dao.ejecutarSQL(alter);
            } catch (Exception ignorado) {
            }
        }

        try {
            String usuario = env("ADMIN_USER", "admin");
            String clave = env("ADMIN_PASSWORD", "OrbitaAdmin2026!");

            if (dao.consultar("SELECT id FROM Usuarios WHERE usuario=?", usuario).isEmpty()) {
                dao.ejecutarSQL(
                        "INSERT INTO Usuarios(usuario,nombre,correo,clave,rol) VALUES(?,?,?,?,?)",
                        usuario, "Administrador", "admin@orbita.local", encoder.encode(clave), "ADMIN");
                System.out.println("Administrador creado correctamente.");
            }
        } catch (Exception e) {
            errores.add("Administrador -> " + e.getMessage());
        }

        return errores;
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