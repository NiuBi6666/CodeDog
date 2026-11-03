package cn.codedog.news;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@Configuration
@ConditionalOnProperty(name="codedog.maintenance-enabled",havingValue="true",matchIfMissing=true)
public class NewsPipeline {
 private final NewsService news;
 public NewsPipeline(NewsService news){this.news=news;}
 private ThreadPoolTaskScheduler scheduler(String name){var s=new ThreadPoolTaskScheduler();s.setPoolSize(1);s.setThreadNamePrefix(name);s.setWaitForTasksToCompleteOnShutdown(false);s.setAwaitTerminationSeconds(15);return s;}
 @Bean public ThreadPoolTaskScheduler newsDispatchScheduler(){return scheduler("news-dispatch-");}
 @Bean public ThreadPoolTaskScheduler newsIngestScheduler(){return scheduler("news-ingest-");}
 @Bean public ThreadPoolTaskScheduler newsTitleScheduler(){var s=scheduler("news-title-");s.setPoolSize(5);return s;}
 @Scheduled(fixedDelay=100,initialDelay=22000,scheduler="newsTitleScheduler") public void titles(){news.translateTitlesNext();}
 @Scheduled(fixedDelay=100,initialDelay=22050,scheduler="newsTitleScheduler") public void titles1(){if(news.parallelTitles())news.translateTitlesNext();}
 @Scheduled(fixedDelay=100,initialDelay=22100,scheduler="newsTitleScheduler") public void titles2(){if(news.parallelTitles())news.translateTitlesNext();}
 @Scheduled(fixedDelay=100,initialDelay=22150,scheduler="newsTitleScheduler") public void titles3(){if(news.parallelTitles())news.translateTitlesNext();}
 @Scheduled(fixedDelay=100,initialDelay=22200,scheduler="newsTitleScheduler") public void titles4(){if(news.parallelTitles())news.translateTitlesNext();}
 @Bean public ThreadPoolTaskScheduler newsTranslateScheduler(){return scheduler("news-translate-");}
 @Scheduled(fixedDelay=2000,initialDelay=20000,scheduler="newsDispatchScheduler") public void dispatch(){news.dispatchQueued();}
 @Scheduled(fixedDelay=2000,initialDelay=20000,scheduler="newsIngestScheduler") public void ingest(){news.ingestBatches();}
 @Scheduled(fixedDelay=100,initialDelay=25000,scheduler="newsTranslateScheduler") public void translate(){news.translateNext();}
}
