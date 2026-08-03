package cn.codedog.ranking;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class RankingCorsConfig implements WebMvcConfigurer {
  @Override public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/public/rankings/extension/**")
      .allowedOrigins("https://sk-crm.codemao.cn").allowedMethods("GET", "POST", "OPTIONS")
      .allowedHeaders("Authorization", "Content-Type").maxAge(3600);
  }
}
