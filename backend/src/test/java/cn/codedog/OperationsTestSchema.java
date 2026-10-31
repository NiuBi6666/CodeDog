package cn.codedog;
import org.springframework.jdbc.core.JdbcTemplate;
public class OperationsTestSchema {
 public static void create(JdbcTemplate jdbc){
  try{for(String resource:java.util.List.of("V120__points_operations.sql","V121__recovery_and_cancellation.sql","V123__ownership_and_completeness.sql","V124__monthly_rules_messages.sql","V125__recovery_drill_reports.sql","V131__student_graduation.sql")){String sql=new String(OperationsTestSchema.class.getResourceAsStream("/db/migration/"+resource).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   for(String command:sql.split(";")){String s=command.trim();if(s.startsWith("CREATE TABLE"))jdbc.execute(s.replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS ").replace("ENGINE=InnoDB",""));}
  }}catch(Exception e){throw new IllegalStateException(e);}
 }
}
