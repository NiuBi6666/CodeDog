package cn.codedog;
import org.springframework.jdbc.core.JdbcTemplate;
public class OperationsTestSchema {
 public static void create(JdbcTemplate jdbc){
  try{String sql=new String(OperationsTestSchema.class.getResourceAsStream("/db/migration/V120__points_operations.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   for(String command:sql.split(";")){String s=command.trim();if(s.startsWith("CREATE TABLE"))jdbc.execute(s.replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS ").replace("ENGINE=InnoDB",""));}
  }catch(Exception e){throw new IllegalStateException(e);}
 }
}
