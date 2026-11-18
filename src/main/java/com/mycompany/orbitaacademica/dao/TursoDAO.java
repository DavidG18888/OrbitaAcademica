package com.mycompany.orbitaacademica.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Repository
public class TursoDAO {
    private final String url = System.getenv("TURSO_DATABASE_URL");
    private final String token = System.getenv("TURSO_AUTH_TOKEN");
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Map<String,Object>> consultar(String sql, Object... valores) {
        JsonNode result = ejecutar(sql, valores);
        JsonNode cols = result.path("cols");
        JsonNode rows = result.path("rows");
        List<Map<String,Object>> salida = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String,Object> map = new LinkedHashMap<>();
            for (int i=0; i<cols.size(); i++) {
                String name = cols.get(i).path("name").asText("col"+i);
                JsonNode val = row.path(i).path("value");
                map.put(name, val.isMissingNode() || val.isNull() ? null : val.asText());
            }
            salida.add(map);
        }
        return salida;
    }

    public void ejecutarSQL(String sql, Object... valores) { ejecutar(sql, valores); }

    private String endpoint() {
        if (url == null || url.isBlank()) throw new IllegalStateException("Falta TURSO_DATABASE_URL");
        String address = url.trim().replaceFirst("^libsql://", "https://").replaceFirst("^http://", "https://");
        if (!address.startsWith("https://")) throw new IllegalArgumentException("La URL debe usar libsql:// o https://");
        return address.replaceAll("/+$", "") + "/v2/pipeline";
    }

    private JsonNode ejecutar(String sql, Object... valores) {
        if (token == null || token.isBlank()) throw new IllegalStateException("Falta TURSO_AUTH_TOKEN");
        try {
            List<Map<String,Object>> args = new ArrayList<>();
            for (Object value : valores) {
                Map<String,Object> a = new LinkedHashMap<>();
                if (value == null) a.put("type", "null");
                else if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) { a.put("type","integer"); a.put("value", value.toString()); }
                else if (value instanceof Float || value instanceof Double) { a.put("type","float"); a.put("value",value.toString()); }
                else { a.put("type","text"); a.put("value",value.toString()); }
                args.add(a);
            }
            Map<String,Object> stmt = new LinkedHashMap<>(); stmt.put("sql",sql); stmt.put("args",args);
            Map<String,Object> req1 = Map.of("type","execute","stmt",stmt);
            Map<String,Object> body = Map.of("requests",List.of(req1,Map.of("type","close")));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                    .header("Authorization","Bearer "+token.trim())
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()<200 || response.statusCode()>299) throw new RuntimeException("HTTP "+response.statusCode()+" de Turso: "+response.body());
            JsonNode json = mapper.readTree(response.body());
            JsonNode results = json.path("results");
            if (!results.isArray() || results.isEmpty()) throw new RuntimeException("Respuesta vacía de Turso: "+response.body());
            JsonNode responseNode = results.get(0).path("response");
            if (responseNode.has("error")) throw new RuntimeException(responseNode.path("error").path("message").asText());
            JsonNode result = responseNode.path("result");
            if (result.isMissingNode() || result.isNull()) throw new RuntimeException("Turso no devolvió result: "+response.body());
            return result;
        } catch (Exception e) { throw new RuntimeException("Error al comunicarse con Turso: "+e.getMessage(),e); }
    }
}
