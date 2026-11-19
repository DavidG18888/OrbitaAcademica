package com.mycompany.orbitaacademica.service;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        "CREATE TABLE IF NOT EXISTS Asistencia (id INTEGER PRIMARY KEY AUTOINCREMENT, matricula_id INTEGER NOT NULL, fecha TEXT NOT NULL, estado TEXT NOT NULL, UNIQUE(matricula_id, fecha))",
        "CREATE TABLE IF NOT EXISTS HorariosSeccion (id INTEGER PRIMARY KEY AUTOINCREMENT, seccion_id INTEGER NOT NULL, tipo TEXT NOT NULL DEFAULT 'RECURRENTE', dia_semana TEXT, fecha TEXT, hora_inicio TEXT NOT NULL, hora_fin TEXT NOT NULL, aula TEXT, FOREIGN KEY(seccion_id) REFERENCES Secciones(id) ON DELETE CASCADE)"
    };

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public InicioService(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    // Columnas que debe tener cada tabla (se leen de las sentencias DDL): {nombre, tipo}
    public static Map<String, List<String[]>> esperadas() {
        Map<String, List<String[]>> m = new LinkedHashMap<>();
        for (String ddl : DDL) {
            int a = ddl.indexOf('('), b = ddl.lastIndexOf(')');
            String tabla = ddl.substring("CREATE TABLE IF NOT EXISTS ".length(), a).trim();
            List<String> partes = new ArrayList<>();
            StringBuilder actual = new StringBuilder();
            int prof = 0;
            for (char ch : ddl.substring(a + 1, b).toCharArray()) {
                if (ch == '(') prof++;
                if (ch == ')') prof--;
                if (ch == ',' && prof == 0) {
                    partes.add(actual.toString().trim());
                    actual.setLength(0);
                } else {
                    actual.append(ch);
                }
            }
            partes.add(actual.toString().trim());
            List<String[]> cols = new ArrayList<>();
            for (String p : partes) {
                String[] t = p.split("\\s+");
                if (t[0].toUpperCase().startsWith("UNIQUE") || t[0].equals("id")) continue;
                cols.add(new String[]{t[0], t.length > 1 ? t[1] : "TEXT"});
            }
            m.put(tabla, cols);
        }
        return m;
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

        // Si una tabla ya existía con otra estructura, agrega las columnas que falten
        // (si la columna ya existe, Turso responde con error y se ignora)
        for (Map.Entry<String, List<String[]>> t : esperadas().entrySet()) {
            if (t.getKey().equals("Usuarios")) continue;
            for (String[] c : t.getValue()) {
                try {
                    dao.ejecutarSQL("ALTER TABLE " + t.getKey() + " ADD COLUMN " + c[0] + " " + c[1]);
                } catch (Exception ignorado) {
                }
            }
        }

        // Columnas adicionales de la versión académica actual. Los errores por columnas ya existentes se ignoran.
        String[] migraciones = {
            "ALTER TABLE Secciones ADD COLUMN estado TEXT NOT NULL DEFAULT 'ACTIVA'",
            "ALTER TABLE Periodos ADD COLUMN matricula_inicio TEXT",
            "ALTER TABLE Periodos ADD COLUMN matricula_fin TEXT",
            "ALTER TABLE Calificaciones ADD COLUMN puntos_extra REAL NOT NULL DEFAULT 0",
            "ALTER TABLE Calificaciones ADD COLUMN cerrada INTEGER NOT NULL DEFAULT 0"
        };
        for (String sql : migraciones) {
            try { dao.ejecutarSQL(sql); } catch (Exception ignorado) { }
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