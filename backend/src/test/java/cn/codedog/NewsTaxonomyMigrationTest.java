
package cn.codedog;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
class NewsTaxonomyMigrationTest {
 @Test void preservesOriginalsAndConvertsLegacyCategories(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:taxonomy-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE news_sources(id BIGINT PRIMARY KEY,source_name VARCHAR(80),category VARCHAR(30))");
  db.execute("CREATE TABLE news_articles(id BIGINT PRIMARY KEY,source_name VARCHAR(80),category VARCHAR(30),published_at TIMESTAMP,title_en TEXT,content_en TEXT)");
  db.update("INSERT INTO news_sources(id,category) VALUES(7,'科学')");
  db.update("INSERT INTO news_articles(id,category,published_at,title_en,content_en) VALUES(9,'健康',CURRENT_TIMESTAMP,'Original title','Original unchanged paragraphs')");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V139__news_taxonomy.sql")).execute(ds);
  assertThat(db.queryForObject("SELECT category FROM news_sources WHERE id=7",String.class)).isEqualTo("科技");
  var article=db.queryForMap("SELECT * FROM news_articles WHERE id=9");
  assertThat(article.get("category")).isEqualTo("生活");
  assertThat(article.get("sub_tag")).isEqualTo("医疗健康");
  assertThat(article.get("lang")).isEqualTo("en");
  assertThat(article.get("content_en")).isEqualTo("Original unchanged paragraphs");
  assertThat(article.get("title_en")).isEqualTo("Original title");
 }
}
