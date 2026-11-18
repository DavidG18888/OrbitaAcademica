package com.mycompany.orbitaacademica.dao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import java.net.URI;
import java.net.http.*;
import java.util.*;
@Repository
public class TursoDAO {
 private final String rawUrl=System.getenv("TURSO_DATABASE_URL");
 private final String token=System.getenv("TURSO_AUTH_TOKEN");
 private final HttpClient client=HttpClient.newHttpClient();
 private final ObjectMapper mapper=new ObjectMapper();
 private String endpoint(){
  if(rawUrl==null||rawUrl.isBlank()) throw new IllegalStateException("Falta TURSO_DATABASE_URL");
  String u=rawUrl.trim().replaceAll("/+$","");
  if(u.startsWith("libsql://")) u="https://"+u.substring(9);
  if(!u.startsWith("http://")&&!u.startsWith("https://")) throw new IllegalStateException("URL de Turso inválida");
  return u+"/v2/pipeline";
 }
 public List<Map<String,Object>> consultar(String sql,Object... vals){
  JsonNode result=ejecutar(sql,vals).path("result");
  JsonNode cols=result.path("cols"), rows=result.path("rows");
  List<Map<String,Object>> out=new ArrayList<>();
  for(JsonNode row:rows){Map<String,Object> m=new LinkedHashMap<>();
   for(int i=0;i<cols.size();i++){String name=cols.get(i).path("name").asText("col"+i); JsonNode v=row.path(i).path("value"); m.put(name,v.isNull()||v.isMissingNode()?null:v.asText());}
   out.add(m);
  } return out;
 }
 public void ejecutarSQL(String sql,Object... vals){ejecutar(sql,vals);}
 private JsonNode ejecutar(String sql,Object... vals){
  try{
   List<Map<String,Object>> args=new ArrayList<>();
   for(Object v:vals){Map<String,Object>a=new HashMap<>(); if(v==null)a.put("type","null"); else if(v instanceof Integer||v instanceof Long)a.put("type","integer"); else if(v instanceof Number)a.put("type","float"); else a.put("type","text"); if(v!=null)a.put("value",v.toString()); args.add(a);}
   Map<String,Object> stmt=Map.of("sql",sql,"args",args);
   String body=mapper.writeValueAsString(Map.of("requests",List.of(Map.of("type","execute","stmt",stmt),Map.of("type","close"))));
   HttpRequest.Builder b=HttpRequest.newBuilder().uri(URI.create(endpoint())).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
   if(token!=null&&!token.isBlank()) b.header("Authorization","Bearer "+token);
   HttpResponse<String> r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
   if(r.statusCode()<200||r.statusCode()>299) throw new RuntimeException("Turso HTTP "+r.statusCode()+": "+r.body());
   JsonNode root=mapper.readTree(r.body()); JsonNode response=root.path("results").get(0).path("response");
   if(response.has("error"))throw new RuntimeException(response.path("error").path("message").asText());
   return response;
  }catch(Exception e){throw new RuntimeException("Error Turso: "+e.getMessage(),e);}
 }
}
