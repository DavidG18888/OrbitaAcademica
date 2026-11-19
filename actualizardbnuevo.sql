-- =============================================================
-- ÓRBITA ACADÉMICA - actualizardbnuevo.sql
-- Migración NO destructiva de cuotas generales a cuotas por sección.
-- Conserva los registros existentes de Cuotas y PagosCuota.
-- Los registros históricos anteriores quedan con seccion_id NULL.
-- =============================================================
PRAGMA foreign_keys=OFF;

CREATE TABLE IF NOT EXISTS Cuotas_nueva (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 seccion_id INTEGER,
 concepto TEXT NOT NULL,
 periodo_mes TEXT NOT NULL,
 monto REAL NOT NULL CHECK(monto>=0),
 fecha_limite TEXT,
 instrucciones TEXT,
 activa INTEGER NOT NULL DEFAULT 1 CHECK(activa IN (0,1)),
 creada_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(seccion_id,concepto,periodo_mes),
 FOREIGN KEY(seccion_id) REFERENCES Secciones(id)
);
INSERT INTO Cuotas_nueva(id,seccion_id,concepto,periodo_mes,monto,fecha_limite,instrucciones,activa,creada_en)
SELECT id,NULL,concepto,periodo_mes,monto,fecha_limite,instrucciones,activa,creada_en FROM Cuotas;

CREATE TABLE IF NOT EXISTS PagosCuota_nueva (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 cuota_id INTEGER NOT NULL,
 estudiante_id INTEGER NOT NULL,
 comprobante_url TEXT NOT NULL,
 nombre_archivo TEXT,
 estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','APROBADO','RECHAZADO')),
 observacion TEXT,
 fecha_pago TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 revisado_por INTEGER,
 fecha_revision TEXT,
 UNIQUE(cuota_id,estudiante_id),
 FOREIGN KEY(cuota_id) REFERENCES Cuotas_nueva(id),
 FOREIGN KEY(estudiante_id) REFERENCES Estudiantes(id),
 FOREIGN KEY(revisado_por) REFERENCES Usuarios(id)
);
INSERT INTO PagosCuota_nueva(id,cuota_id,estudiante_id,comprobante_url,nombre_archivo,estado,observacion,fecha_pago,revisado_por,fecha_revision)
SELECT id,cuota_id,estudiante_id,comprobante_url,nombre_archivo,estado,observacion,fecha_pago,revisado_por,fecha_revision FROM PagosCuota;
DROP TABLE PagosCuota;
DROP TABLE Cuotas;
ALTER TABLE Cuotas_nueva RENAME TO Cuotas;
ALTER TABLE PagosCuota_nueva RENAME TO PagosCuota;
CREATE INDEX IF NOT EXISTS idx_cuotas_seccion_activa ON Cuotas(seccion_id,activa,periodo_mes);
CREATE INDEX IF NOT EXISTS idx_pagos_estudiante_estado ON PagosCuota(estudiante_id,estado);
PRAGMA foreign_keys=ON;

-- Verificación
SELECT name FROM sqlite_master WHERE type='table' AND name IN ('Cuotas','PagosCuota');
PRAGMA table_info(Cuotas);
