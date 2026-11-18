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
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT UNIQUE NOT NULL, nombre TEXT NOT NULL, correo TEXT UNIQUE, clave TEXT NOT NULL, rol TEXT NOT NULL, estado TEXT NOT NULL DEFAULT 'ACTIVO')");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Estudiantes (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER, codigo TEXT UNIQUE, nombre TEXT, apellido TEXT, carrera TEXT, estado TEXT DEFAULT 'ACTIVO')");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Docentes (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER, codigo TEXT UNIQUE, nombre TEXT, apellido TEXT, especialidad TEXT, estado TEXT DEFAULT 'ACTIVO')");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Periodos (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, fecha_inicio TEXT, fecha_fin TEXT, estado TEXT DEFAULT 'ACTIVO')");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Carreras (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE, nombre TEXT NOT NULL, descripcion TEXT, duracion INTEGER, estado TEXT DEFAULT 'ACTIVO')");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Asignaturas (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE, nombre TEXT NOT NULL, carrera_id INTEGER, docente_id INTEGER, creditos INTEGER, estado TEXT DEFAULT 'ACTIVO')");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Matriculas (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER, asignatura_id INTEGER, periodo_id INTEGER, fecha TEXT, estado TEXT DEFAULT 'ACTIVO')");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Horarios (id INTEGER PRIMARY KEY AUTOINCREMENT, asignatura_id INTEGER, docente_id INTEGER, periodo_id INTEGER, dia TEXT, hora_inicio TEXT, hora_fin TEXT, aula TEXT)");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Calificaciones (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER, asignatura_id INTEGER, periodo_id INTEGER, nota REAL, observacion TEXT)");
            dao.ejecutarSQL("CREATE TABLE IF NOT EXISTS Asistencias (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER, asignatura_id INTEGER, fecha TEXT, estado TEXT, observacion TEXT)");
            String usuario=env("ADMIN_USER","admin"); String clave=env("ADMIN_PASSWORD","admin123");
            if(dao.consultar("SELECT id FROM Usuarios WHERE usuario=?",usuario).isEmpty()) {
                dao.ejecutarSQL("INSERT INTO Usuarios(usuario,nombre,correo,clave,rol) VALUES(?,?,?,?,?)",usuario,"Administrador","admin@orbita.local",encoder.encode(clave),"ADMIN");
                System.out.println("Administrador creado.");
            }
            System.out.println("Base de datos Órbita Académica preparada.");
        } catch(Exception e) { System.err.println("No se pudo preparar Turso: "+e.getMessage()); }
    }
    private String env(String key,String def) { String v=System.getenv(key); return v==null||v.isBlank()?def:v; }
}
