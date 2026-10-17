package cn.codedog;
import org.junit.jupiter.api.Test;import org.springframework.boot.test.context.runner.ApplicationContextRunner;import cn.codedog.config.MaintenanceSchedulingConfig;import static org.assertj.core.api.Assertions.*;
class MaintenanceSchedulingConfigTest {
 @Test void isolatedEnvironmentCanDisableAllScheduledTasks(){new ApplicationContextRunner().withUserConfiguration(MaintenanceSchedulingConfig.class).withPropertyValues("codedog.maintenance-enabled=false").run(c->assertThat(c).doesNotHaveBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));}
 @Test void productionKeepsSchedulingEnabledByDefault(){new ApplicationContextRunner().withUserConfiguration(MaintenanceSchedulingConfig.class).run(c->assertThat(c).hasBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));}
}
