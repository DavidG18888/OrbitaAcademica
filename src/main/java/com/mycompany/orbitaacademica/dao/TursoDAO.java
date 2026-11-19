package com.mycompany.orbitaacademica.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class TursoDAO {

    private final String url = System.getenv("TURSO_DATABASE_URL");
    private final String token = System.getenv("TURSO_AUTH_TOKEN");

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // Consultar registros de la base de datos
    public List<Map<String, Object>> consultar(String sql, Object... valores) {

        JsonNode resultado = ejecutar(sql, valores);

        List<Map<String, Object>> filas = new ArrayList<>();

        JsonNode columnas = resultado.path("cols");
        JsonNode registros = resultado.path("rows");

        for (JsonNode fila : registros) {

            Map<String, Object> datos = new LinkedHashMap<>();

            for (int i = 0; i < columnas.size(); i++) {

                String nombre = columnas.get(i)
                        .path("name")
                        .asText("col" + i);

                JsonNode celda = fila.path(i);
                JsonNode valorNodo = celda.path("value");
                String tipo = celda.path("type")
                        .asText(columnas.get(i).path("type").asText("text"))
                        .toLowerCase(Locale.ROOT);

                Object valor;
                if (valorNodo.isMissingNode() || valorNodo.isNull() || "null".equals(tipo)) {
                    valor = null;
                } else {
                    String texto = valorNodo.asText();
                    try {
                        valor = switch (tipo) {
                            case "integer", "int" -> Long.valueOf(texto);
                            case "float", "real", "numeric" -> Double.valueOf(texto);
                            case "boolean" -> "1".equals(texto) || Boolean.parseBoolean(texto);
                            default -> texto;
                        };
                    } catch (NumberFormatException ex) {
                        // Si Turso entrega un tipo inesperado, conservar el dato original.
                        valor = texto;
                    }
                }

                datos.put(nombre, valor);
            }

            filas.add(datos);
        }

        return filas;
    }

    // Ejecutar INSERT, UPDATE o DELETE
    public void ejecutarSQL(String sql, Object... valores) {
        ejecutar(sql, valores);
    }

    // Convertir URL de Turso a HTTPS
    private String convertirURL(String direccion) {

        direccion = direccion.trim();

        if (direccion.startsWith("libsql://")) {
            direccion = direccion.replaceFirst(
                    "^libsql://",
                    "https://"
            );
        }

        if (!direccion.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "La URL de Turso debe comenzar con libsql:// o https://"
            );
        }

        return direccion.replaceAll("/+$", "");
    }

    // Enviar consultas a Turso
    private JsonNode ejecutar(String sql, Object... valores) {

        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar TURSO_DATABASE_URL en Render."
            );
        }

        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar TURSO_AUTH_TOKEN en Render."
            );
        }

        try {

            List<Map<String, Object>> argumentos = new ArrayList<>();

            for (Object valor : valores) {

                Map<String, Object> argumento = new HashMap<>();

                if (valor == null) {

                    argumento.put("type", "null");

                } else if (valor instanceof Integer
                        || valor instanceof Long
                        || valor instanceof Short
                        || valor instanceof Byte) {

                    argumento.put("type", "integer");
                    argumento.put("value", valor.toString());

                } else if (valor instanceof Float
                        || valor instanceof Double) {

                    argumento.put("type", "float");
                    argumento.put("value", valor.toString());

                } else {

                    argumento.put("type", "text");
                    argumento.put("value", valor.toString());
                }

                argumentos.add(argumento);
            }

            Map<String, Object> sentencia = new HashMap<>();

            sentencia.put("sql", sql);
            sentencia.put("args", argumentos);

            Map<String, Object> ejecutar = Map.of(
                    "type", "execute",
                    "stmt", sentencia
            );

            Map<String, Object> cerrar = Map.of(
                    "type", "close"
            );

            Map<String, Object> solicitud = Map.of(
                    "requests", List.of(ejecutar, cerrar)
            );

            String cuerpo = mapper.writeValueAsString(solicitud);

            String direccion = convertirURL(url) + "/v2/pipeline";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(direccion))
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Content-Type", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(cuerpo)
                    )
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200
                    || response.statusCode() > 299) {

                throw new RuntimeException(
                        "Turso respondió HTTP "
                        + response.statusCode()
                        + ": "
                        + response.body()
                );
            }

            JsonNode respuesta = mapper.readTree(response.body());

            JsonNode resultados = respuesta.path("results");

            if (resultados.isEmpty()) {
                throw new RuntimeException(
                        "Turso no devolvió resultados: "
                        + response.body()
                );
            }

            JsonNode primero = resultados.get(0);

            if (primero.has("error")) {
                throw new RuntimeException(
                        primero.path("error")
                                .path("message")
                                .asText("Error desconocido de Turso")
                );
            }

            JsonNode respuestaSQL = primero.path("response");

            if (respuestaSQL.has("error")) {

                throw new RuntimeException(
                        respuestaSQL.path("error")
                                .path("message")
                                .asText("Error desconocido de Turso")
                );
            }

            JsonNode resultado = respuestaSQL.path("result");

            if (resultado.isMissingNode()
                    || resultado.isNull()) {

                throw new RuntimeException(
                        "Turso no devolvió el resultado de la consulta."
                );
            }

            return resultado;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error al comunicarse con Turso: "
                    + e.getMessage(),
                    e
            );
        }
    }
}