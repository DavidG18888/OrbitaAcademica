package com.mycompany.orbitaacademica.service;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class InicioService {
    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;
    public InicioService(TursoDAO dao, BCryptPasswordEncoder encoder) { this.dao=dao; this.encoder=encoder; }

    @PostConstruct
    public void preparar() {
        try {
            String[] ddl = {
              "CREATE TABLE IF NOT EXISTS Usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT UNIQUE NOT NULL, nombre TEXT NOT NULL, correo TEXT UNIQUE, clave TEXT NOT NULL, rol TEXT NOT NULL DEFAULT 'ADMIN', estado TEXT NOT NULL DEFAULT 'ACTIVO')",
              "CREATE TABLE IF NOT EXISTS Estudiantes (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER, codigo TEXT UNIQUE, nombre TEXT NOT NULL, apellido TEXT NOT NULL, carrera TEXT, telefono TEXT, correo TEXT, estado TEXT DEFAULT 'ACTIVO')",
              "CREATE TABLE IF NOT EXISTS Docentes (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER, codigo TEXT UNIQUE, nombre TEXT NOT NULL, apellido TEXT NOT NULL, especialidad TEXT, telefono TEXT, correo TEXT, estado TEXT DEFAULT 'ACTIVO')",
              "CREATE TABLE IF NOT EXISTS Carreras (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE, nombre TEXT NOT NULL, descripcion TEXT, duracion INTEGER, estado TEXT DEFAULT 'ACTIVO')",
              "CREATE TABLE IF NOT EXISTS Asignaturas (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE, nombre TEXT NOT NULL, carrera_id INTEGER, docente_id INTEGER, creditos INTEGER, estado TEXT DEFAULT 'ACTIVO')",
              "CREATE TABLE IF NOT EXISTS Periodos (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, fecha_inicio TEXT, fecha_fin TEXT, estado TEXT DEFAULT 'ACTIVO')",
              "CREATE TABLE IF NOT EXISTS Matriculas (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER, asignatura_id INTEGER, periodo_id INTEGER, fecha TEXT, estado TEXT DEFAULT 'ACTIVO')",
              "CREATE TABLE IF NOT EXISTS Horarios (id INTEGER PRIMARY KEY AUTOINCREMENT, asignatura_id INTEGER, docente_id INTEGER, periodo_id INTEGER, dia TEXT, hora_inicio TEXT, hora_fin TEXT, aula TEXT)",
              "CREATE TABLE IF NOT EXISTS Calificaciones (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER, asignatura_id INTEGER, periodo_id INTEGER, nota REAL, observacion TEXT)",
              "CREATE TABLE IF NOT EXISTS Asistencias (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER, asignatura_id INTEGER, fecha TEXT, estado TEXT, observacion TEXT)"
            };
            for (String sql : ddl) dao.ejecutarSQL(sql);
            String admin = env("ADMIN_USER", "admin");
            String pass = env("ADMIN_PASSWORD", "admin123");
            if (dao.consultar("SELECT id FROM Usuarios WHERE usuario=?", admin).isEmpty()) {
                dao.ejecutarSQL("INSERT INTO Usuarios(usuario,nombre,correo,clave,rol,estado) VALUES(?,?,?,?,?,?)", admin,"Administrador","admin@orbita.local",encoder.encode(pass),"ADMIN","ACTIVO");
                System.out.println("Administrador inicial creado: "+admin+" (configure ADMIN_PASSWORD en Render)");
            }
            System.out.println("Esquema Órbita Académica preparado.");
        } catch (Exception e) { System.err.println("No se pudo preparar Turso: "+e.getMessage()); e.printStackTrace(); }
    }
    private String env(String key,String fallback) { String value=System.getenv(key); return value==null||value.isBlank()?fallback:value; }
}
