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
        "CREATE TABLE IF NOT EXISTS Periodos (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, fecha_inicio TEXT, fecha_fin TEXT, costo_matricula REAL NOT NULL DEFAULT 0, estado TEXT DEFAULT 'ACTIVO')",
        "CREATE TABLE IF NOT EXISTS Asignaturas (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE, nombre TEXT NOT NULL, uv INTEGER, carrera_id INTEGER, estado TEXT DEFAULT 'ACTIVO')",
        "CREATE TABLE IF NOT EXISTS Secciones (id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT, asignatura_id INTEGER, docente_id INTEGER, periodo_id INTEGER, modalidad TEXT DEFAULT 'Presencial', aula TEXT, cupo INTEGER, dia TEXT, hora_inicio TEXT, hora_fin TEXT, fecha_inicio TEXT, fecha_fin TEXT, matricula_inicio TEXT, matricula_fin TEXT, estado TEXT DEFAULT 'ACTIVA')",
        "CREATE TABLE IF NOT EXISTS Matriculas (id INTEGER PRIMARY KEY AUTOINCREMENT, estudiante_id INTEGER NOT NULL, seccion_id INTEGER NOT NULL, fecha TEXT DEFAULT (date('now')), estado TEXT DEFAULT 'ACTIVA', fecha_retiro TEXT, motivo_retiro TEXT, UNIQUE(estudiante_id, seccion_id))",
        "CREATE TABLE IF NOT EXISTS Calificaciones (id INTEGER PRIMARY KEY AUTOINCREMENT, matricula_id INTEGER UNIQUE NOT NULL, parcial1 REAL, parcial2 REAL, parcial3 REAL, puntos_extra REAL DEFAULT 0, fecha_cierre TEXT, observacion TEXT)",
        "CREATE TABLE IF NOT EXISTS Asistencia (id INTEGER PRIMARY KEY AUTOINCREMENT, matricula_id INTEGER NOT NULL, fecha TEXT NOT NULL, estado TEXT NOT NULL, UNIQUE(matricula_id, fecha))",
        "CREATE TABLE IF NOT EXISTS EventosAcademicos (id INTEGER PRIMARY KEY AUTOINCREMENT, titulo TEXT NOT NULL, descripcion TEXT, fecha_inicio TEXT NOT NULL, fecha_fin TEXT, lugar TEXT, imagen_url TEXT, estado TEXT NOT NULL DEFAULT 'ACTIVO', creado_por INTEGER)",
        "CREATE TABLE IF NOT EXISTS TareasSeccion (id INTEGER PRIMARY KEY AUTOINCREMENT, seccion_id INTEGER NOT NULL, parcial INTEGER NOT NULL CHECK(parcial IN (1,2,3)), titulo TEXT NOT NULL, descripcion TEXT, puntaje REAL NOT NULL CHECK(puntaje > 0), fecha_entrega TEXT, archivo_url TEXT, nombre_archivo TEXT, permite_entrega INTEGER NOT NULL DEFAULT 1, creada_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (seccion_id) REFERENCES Secciones(id) ON DELETE CASCADE)",
        "CREATE TABLE IF NOT EXISTS EntregasTarea (id INTEGER PRIMARY KEY AUTOINCREMENT, tarea_id INTEGER NOT NULL, estudiante_id INTEGER NOT NULL, archivo_url TEXT, nombre_archivo TEXT, comentario TEXT, nota_obtenida REAL, retroalimentacion TEXT, estado TEXT NOT NULL DEFAULT 'ENTREGADA', entregada_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, calificada_en TEXT, UNIQUE(tarea_id, estudiante_id), FOREIGN KEY (tarea_id) REFERENCES TareasSeccion(id) ON DELETE CASCADE, FOREIGN KEY (estudiante_id) REFERENCES Estudiantes(id))",
        "CREATE TABLE IF NOT EXISTS PagosMatriculaPeriodo (id INTEGER PRIMARY KEY AUTOINCREMENT, periodo_id INTEGER NOT NULL, estudiante_id INTEGER NOT NULL, monto REAL NOT NULL DEFAULT 0, comprobante_url TEXT, nombre_archivo TEXT, estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('EXONERADO','PENDIENTE','APROBADO','RECHAZADO')), observacion TEXT, fecha_pago TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, revisado_por INTEGER, fecha_revision TEXT, UNIQUE(periodo_id, estudiante_id), FOREIGN KEY (periodo_id) REFERENCES Periodos(id), FOREIGN KEY (estudiante_id) REFERENCES Estudiantes(id))"
    };

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public InicioService(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

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
                String[] t = p.split("\s+");
                String u = t[0].toUpperCase();
                if (u.startsWith("UNIQUE") || u.startsWith("FOREIGN") || u.startsWith("CHECK") || t[0].equals("id")) continue;
                cols.add(new String[]{t[0], t.length > 1 ? t[1] : "TEXT"});
            }
            m.put(tabla, cols);
        }
        return m;
    }

    @PostConstruct
    public void iniciar() {
        for (String e : preparar()) {
            System.err.println("[Orbita] " + e);
        }
    }

    public List<String> preparar() {
        List<String> errores = new ArrayList<>();

        for (String ddl : DDL) {
            try {
                dao.ejecutarSQL(ddl);
            } catch (Exception e) {
                errores.add(ddl.substring(0, Math.min(70, ddl.length())) + " -> " + e.getMessage());
            }
        }

        for (Map.Entry<String, List<String[]>> t : esperadas().entrySet()) {
            if (t.getKey().equals("Usuarios")) continue;
            for (String[] c : t.getValue()) {
                try {
                    dao.ejecutarSQL("ALTER TABLE " + t.getKey() + " ADD COLUMN " + c[0] + " " + c[1]);
                } catch (Exception ignorado) {}
            }
        }

        migrarTicketsSiEsNecesario();
        migrarCuotasGeneralesPorParcial();

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

    private void migrarTicketsSiEsNecesario() {
        try {
            List<Map<String, Object>> info = dao.consultar("SELECT sql FROM sqlite_master WHERE type='table' AND name='Tickets'");
            if (!info.isEmpty()) {
                String sql = String.valueOf(info.get(0).get("sql"));
                if (sql != null && sql.contains("ESPERANDO_ESTUDIANTE")) {
                    dao.ejecutarSQL("PRAGMA foreign_keys = OFF");
                    dao.ejecutarSQL("CREATE TABLE Tickets_new ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "estudiante_id INTEGER NOT NULL, "
                            + "asunto TEXT NOT NULL, "
                            + "categoria TEXT, "
                            + "descripcion TEXT NOT NULL, "
                            + "estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','EN_PROCESO','RESUELTO','RECHAZADO')), "
                            + "prioridad TEXT NOT NULL DEFAULT 'NORMAL', "
                            + "asignado_a INTEGER, "
                            + "creado_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                            + "actualizado_en TEXT)");
                    dao.ejecutarSQL("INSERT INTO Tickets_new(id,estudiante_id,asunto,categoria,descripcion,estado,prioridad,asignado_a,creado_en,actualizado_en) "
                            + "SELECT id,estudiante_id,asunto,categoria,descripcion, "
                            + "CASE UPPER(estado) WHEN 'ABIERTO' THEN 'PENDIENTE' WHEN 'ESPERANDO_ESTUDIANTE' THEN 'EN_PROCESO' WHEN 'CERRADO' THEN 'RESUELTO' WHEN 'EN_PROCESO' THEN 'EN_PROCESO' WHEN 'RESUELTO' THEN 'RESUELTO' WHEN 'RECHAZADO' THEN 'RECHAZADO' ELSE 'PENDIENTE' END, "
                            + "prioridad,asignado_a,creado_en,actualizado_en FROM Tickets");
                    dao.ejecutarSQL("DROP TABLE Tickets");
                    dao.ejecutarSQL("ALTER TABLE Tickets_new RENAME TO Tickets");
                    dao.ejecutarSQL("CREATE INDEX IF NOT EXISTS idx_tickets_estado_fecha ON Tickets(estado, creado_en)");
                    dao.ejecutarSQL("PRAGMA foreign_keys = ON");
                }
            }
        } catch (Exception ignored) {}
    }

    private void migrarCuotasGeneralesPorParcial() {
        try {
            List<Map<String, Object>> info = dao.consultar("SELECT sql FROM sqlite_master WHERE type='table' AND name='Cuotas'");
            if (!info.isEmpty()) {
                String sql = String.valueOf(info.get(0).get("sql"));
                if (sql != null && !sql.contains("periodo_id")) {
                    dao.ejecutarSQL("PRAGMA foreign_keys = OFF");
                    dao.ejecutarSQL("DROP TABLE IF EXISTS PagosCuota");
                    dao.ejecutarSQL("DROP TABLE IF EXISTS Cuotas");
                    dao.ejecutarSQL("CREATE TABLE Cuotas ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "periodo_id INTEGER NOT NULL, "
                            + "parcial INTEGER NOT NULL CHECK (parcial IN (1,2,3)), "
                            + "concepto TEXT NOT NULL, "
                            + "monto REAL NOT NULL CHECK (monto >= 0), "
                            + "fecha_limite TEXT, "
                            + "instrucciones TEXT, "
                            + "activa INTEGER NOT NULL DEFAULT 1 CHECK (activa IN (0,1)), "
                            + "creada_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                            + "UNIQUE (periodo_id, parcial), "
                            + "FOREIGN KEY (periodo_id) REFERENCES Periodos(id))");
                    dao.ejecutarSQL("CREATE TABLE PagosCuota ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "cuota_id INTEGER NOT NULL, "
                            + "estudiante_id INTEGER NOT NULL, "
                            + "comprobante_url TEXT NOT NULL, "
                            + "nombre_archivo TEXT, "
                            + "estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO')), "
                            + "observacion TEXT, "
                            + "fecha_pago TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                            + "revisado_por INTEGER, "
                            + "fecha_revision TEXT, "
                            + "UNIQUE (cuota_id, estudiante_id), "
                            + "FOREIGN KEY (cuota_id) REFERENCES Cuotas(id), "
                            + "FOREIGN KEY (estudiante_id) REFERENCES Estudiantes(id))");
                    dao.ejecutarSQL("PRAGMA foreign_keys = ON");
                }
            }
        } catch (Exception ignored) {}
    }

    private String env(String key, String def) {
        String valor = System.getenv(key);
        if (valor == null || valor.isBlank()) {
            return def;
        }
        return valor;
    }
}
