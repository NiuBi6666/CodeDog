package cn.codedog;
import cn.codedog.news.migration.V138__news_namespace;
import cn.codedog.service.PasswordRecoveryCipher;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class NewsNamespaceMigrationTest {
 private JdbcDataSource schema(){
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
  new ResourceDatabasePopulator(java.util.stream.IntStream.rangeClosed(132,137).mapToObj(v->new ClassPathResource("db/migration/"+switch(v){case 132->"V132__lens_news.sql";case 133->"V133__lens_ai_settings.sql";case 134->"V134__lens_translation_mode.sql";case 135->"V135__lens_collector_settings.sql";case 136->"V136__lens_pipeline_speed.sql";default->"V137__lens_on_demand_translation.sql";})).toArray(ClassPathResource[]::new)).execute(ds);
  return ds;
 }
 @Test void keepsDataRewrapsCredentialsAndCanResume()throws Exception{
  var ds=schema();var db=new JdbcTemplate(ds);var cipher=new PasswordRecoveryCipher(Base64.getEncoder().encodeToString(new byte[32]));
  db.update("INSERT INTO lens_ai_settings VALUES(1,'https://example.com','model',?,CURRENT_TIMESTAMP)",cipher.encrypt("test-api-key","lens.ai.api-key.v1"));
  db.update("INSERT INTO lens_collector_settings(id,repository,workflow,api_url,github_credential,webhook_credential) VALUES(1,'owner/repo','lens-crawl.yml','https://example.com',?,?)",cipher.encrypt("test-token","lens.collector.github.v1"),cipher.encrypt("test-webhook-secret","lens.collector.webhook.v1"));
  long sources=db.queryForObject("SELECT COUNT(*) FROM lens_sources",Long.class);
  try(var c=ds.getConnection()){new V138__news_namespace(cipher).migrateConnection(c);new V138__news_namespace(cipher).migrateConnection(c);}
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_sources",Long.class)).isEqualTo(sources);
  assertThat(cipher.decrypt(db.queryForObject("SELECT api_key_ciphertext FROM news_ai_settings",String.class),"news.ai.api-key.v1")).isEqualTo("test-api-key");
  var config=db.queryForMap("SELECT * FROM news_collector_settings");
  assertThat(config.get("workflow")).isEqualTo("news-crawl.yml");
  assertThat(cipher.decrypt((String)config.get("github_credential"),"news.collector.github.v1")).isEqualTo("test-token");
  assertThat(cipher.decrypt((String)config.get("webhook_credential"),"news.collector.webhook.v1")).isEqualTo("test-webhook-secret");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE 'lens_%'",Long.class)).isZero();
 }
 @Test void invalidKeyFailsBeforeRenamingAnyTables()throws Exception{
  var ds=schema();var db=new JdbcTemplate(ds);var cipher=new PasswordRecoveryCipher(Base64.getEncoder().encodeToString(new byte[32]));
  db.update("INSERT INTO lens_ai_settings VALUES(1,'https://example.com','model',?,CURRENT_TIMESTAMP)",cipher.encrypt("test-api-key","lens.ai.api-key.v1"));
  try(var c=ds.getConnection()){assertThatThrownBy(()->new V138__news_namespace(new PasswordRecoveryCipher("")).migrateConnection(c)).isInstanceOf(RuntimeException.class);}
  assertThat(db.queryForObject("SELECT COUNT(*) FROM lens_sources",Long.class)).isPositive();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE 'news_%'",Long.class)).isZero();
 }
}
