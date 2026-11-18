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

                String nombre = columnas.get(i).path("name").asText("col" + i);

                JsonNode valorNodo = fila.path(i).path("value");

                Object valor = valorNodo.isMissingNode() || valorNodo.isNull()
                        ? null
                        : valorNodo.asText();

                datos.put(nombre, valor);
            }

            filas.add(datos);
        }

        return filas;
    }

    // Ejecutar instrucciones INSERT, UPDATE o DELETE
    public void ejecutarSQL(String sql, Object... valores) {
        ejecutar(sql, valores);
    }

    // Enviar la consulta a Turso
    private JsonNode ejecutar(String sql, Object... valores) {

        if (url == null || token == null) {
            throw new IllegalStateException(
                    "Configure TURSO_DATABASE_URL y TURSO_AUTH_TOKEN en las variables de entorno."
            );
        }

        try {

            List<Map<String, Object>> argumentos = new ArrayList<>();

            for (Object valor : valores) {

                Map<String, Object> argumento = new HashMap<>();

                if (valor == null) {
                    argumento.put("type", "null");

                } else if (valor instanceof Number) {
                    argumento.put("type", "integer");
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

            String direccion = url.replaceAll("/$", "") + "/v2/pipeline";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(direccion))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new RuntimeException(
                        "Turso respondió " + response.statusCode() + ": " + response.body()
                );
            }

            JsonNode respuesta = mapper.readTree(response.body());

            JsonNode resultado = respuesta
                    .path("results")
                    .get(0)
                    .path("response");

            if (resultado.has("error")) {
                throw new RuntimeException(
                        resultado.path("error").path("message").asText()
                );
            }

            return resultado.path("result");

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error al comunicarse con Turso: " + e.getMessage(),
                    e
            );
        }
    }

}