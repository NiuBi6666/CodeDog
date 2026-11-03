package cn.codedog.news.migration;

import cn.codedog.service.PasswordRecoveryCipher;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** One-time compatibility boundary. Historical names are intentionally confined here. */
@Component
public class V138__news_namespace extends BaseJavaMigration {
 private final PasswordRecoveryCipher cipher;
 public V138__news_namespace(PasswordRecoveryCipher cipher){this.cipher=cipher;}
 @Override public boolean canExecuteInTransaction(){return false;}
 @Override public void migrate(Context context)throws Exception{migrateConnection(context.getConnection());}
 private static final List<String> TABLES=List.of("sources","articles","batches","refresh_runs","ai_settings","translation_settings","collector_settings","sync_gate","translation_cache");
 private static Set<String> tables(Connection c)throws SQLException{
  var names=new HashSet<String>();try(var rows=c.getMetaData().getTables(c.getCatalog(),null,"%",new String[]{"TABLE"})){while(rows.next())names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));}return names;
 }
 private String rewrap(String value,String suffix){
  if(value==null||value.isBlank())return value;
  String plaintext;
  try{plaintext=cipher.decrypt(value,"lens."+suffix);}catch(RuntimeException oldContext){plaintext=cipher.decrypt(value,"news."+suffix);}
  return cipher.encrypt(plaintext,"news."+suffix);
 }
 public void migrateConnection(Connection c)throws Exception{
  var names=tables(c);
  for(String suffix:TABLES){
   boolean old=names.contains("lens_"+suffix),current=names.contains("news_"+suffix);
   if(old==current)throw new SQLException("News namespace migration requires exactly one source table per suffix");
  }
  // Validate/decrypt all credentials before any nontransactional MySQL DDL.
  String aiTable=names.contains("lens_ai_settings")?"lens_ai_settings":"news_ai_settings";
  String collectorTable=names.contains("lens_collector_settings")?"lens_collector_settings":"news_collector_settings";
  String ai=null,token=null,secret=null;
  try(var s=c.createStatement();var r=s.executeQuery("SELECT api_key_ciphertext FROM "+aiTable+" WHERE id=1")){if(r.next())ai=rewrap(r.getString(1),"ai.api-key.v1");}
  try(var s=c.createStatement();var r=s.executeQuery("SELECT github_credential,webhook_credential FROM "+collectorTable+" WHERE id=1")){if(r.next()){token=rewrap(r.getString(1),"collector.github.v1");secret=rewrap(r.getString(2),"collector.webhook.v1");}}
  for(String suffix:TABLES)if(names.contains("lens_"+suffix)){
   try(var s=c.createStatement()){s.execute("ALTER TABLE lens_"+suffix+" RENAME TO news_"+suffix);}
  }
  boolean mysql=c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
  var indexes=new HashSet<String>();
  try(var r=c.getMetaData().getIndexInfo(c.getCatalog(),null,"news_articles",false,false)){while(r.next()){String n=r.getString("INDEX_NAME");if(n!=null)indexes.add(n.toLowerCase(Locale.ROOT));}}
  for(String suffix:List.of("status_time","retry","body_demand"))if(indexes.contains("idx_lens_"+suffix)){
   try(var s=c.createStatement()){s.execute(mysql?"ALTER TABLE news_articles RENAME INDEX idx_lens_"+suffix+" TO idx_news_"+suffix:"ALTER INDEX idx_lens_"+suffix+" RENAME TO idx_news_"+suffix);}
  }
  if(ai!=null)try(var s=c.prepareStatement("UPDATE news_ai_settings SET api_key_ciphertext=? WHERE id=1")){s.setString(1,ai);s.executeUpdate();}
  if(token!=null)try(var s=c.prepareStatement("UPDATE news_collector_settings SET github_credential=?,webhook_credential=? WHERE id=1")){s.setString(1,token);s.setString(2,secret);s.executeUpdate();}
  try(var s=c.prepareStatement("UPDATE news_collector_settings SET workflow='news-crawl.yml' WHERE workflow='lens-crawl.yml'")){s.executeUpdate();}
 }
}
