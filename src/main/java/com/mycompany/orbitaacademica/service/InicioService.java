package com.mycompany.orbitaacademica.service;
import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
@Service
public class InicioService {
 private final TursoDAO dao; private final BCryptPasswordEncoder encoder;
 public InicioService(TursoDAO d,BCryptPasswordEncoder e){dao=d;encoder=e;}
 @PostConstruct public void init(){try{
  String[] sql={
  "CREATE TABLE IF NOT EXISTS Usuarios(id INTEGER PRIMARY KEY AUTOINCREMENT,usuario TEXT UNIQUE NOT NULL,nombre TEXT NOT NULL,correo TEXT,clave TEXT NOT NULL,rol TEXT NOT NULL,estado TEXT DEFAULT 'ACTIVO')",
  "CREATE TABLE IF NOT EXISTS Estudiantes(id INTEGER PRIMARY KEY AUTOINCREMENT,usuario_id INTEGER,codigo TEXT UNIQUE,nombre TEXT,apellido TEXT,dni TEXT,carrera TEXT,correo TEXT,telefono TEXT,estado TEXT DEFAULT 'ACTIVO')",
  "CREATE TABLE IF NOT EXISTS Docentes(id INTEGER PRIMARY KEY AUTOINCREMENT,usuario_id INTEGER,codigo TEXT UNIQUE,nombre TEXT,apellido TEXT,especialidad TEXT,correo TEXT,telefono TEXT,estado TEXT DEFAULT 'ACTIVO')",
  "CREATE TABLE IF NOT EXISTS Asignaturas(id INTEGER PRIMARY KEY AUTOINCREMENT,codigo TEXT UNIQUE,nombre TEXT,carrera TEXT,creditos INTEGER,estado TEXT DEFAULT 'ACTIVO')",
  "CREATE TABLE IF NOT EXISTS Periodos(id INTEGER PRIMARY KEY AUTOINCREMENT,nombre TEXT,fecha_inicio TEXT,fecha_fin TEXT,estado TEXT DEFAULT 'ACTIVO')",
  "CREATE TABLE IF NOT EXISTS Matriculas(id INTEGER PRIMARY KEY AUTOINCREMENT,estudiante_id INTEGER,asignatura_id INTEGER,periodo_id INTEGER,seccion TEXT,fecha TEXT,estado TEXT DEFAULT 'ACTIVA')",
  "CREATE TABLE IF NOT EXISTS Calificaciones(id INTEGER PRIMARY KEY AUTOINCREMENT,matricula_id INTEGER,parcial1 REAL,parcial2 REAL,parcial3 REAL,final REAL,observacion TEXT)",
  "CREATE TABLE IF NOT EXISTS Asistencia(id INTEGER PRIMARY KEY AUTOINCREMENT,matricula_id INTEGER,fecha TEXT,estado TEXT,observacion TEXT)",
  "CREATE TABLE IF NOT EXISTS Horarios(id INTEGER PRIMARY KEY AUTOINCREMENT,asignatura_id INTEGER,docente_id INTEGER,seccion TEXT,dia TEXT,hora_inicio TEXT,hora_fin TEXT,aula TEXT)"
  };
  for(String s:sql)dao.ejecutarSQL(s);
  String u=env("ADMIN_USER","admin"), p=env("ADMIN_PASSWORD","OrbitaAdmin2026!");
  if(dao.consultar("SELECT id FROM Usuarios WHERE usuario=?",u).isEmpty())
   dao.ejecutarSQL("INSERT INTO Usuarios(usuario,nombre,correo,clave,rol,estado) VALUES(?,?,?,?,?,?)",u,"Administrador","admin@orbita.local",encoder.encode(p),"ADMIN","ACTIVO");
 }catch(Exception e){System.err.println("Inicio DB: "+e.getMessage());}}
 private String env(String k,String d){String v=System.getenv(k);return v==null||v.isBlank()?d:v;}
}
