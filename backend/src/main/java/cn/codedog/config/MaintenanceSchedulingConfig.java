package cn.codedog.config;
import org.springframework.context.annotation.Configuration;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling @ConditionalOnProperty(name="codedog.maintenance-enabled",havingValue="true",matchIfMissing=true)
public class MaintenanceSchedulingConfig {}
