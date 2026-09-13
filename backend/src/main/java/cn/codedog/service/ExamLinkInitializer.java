package cn.codedog.service;
import cn.codedog.model.*;
import cn.codedog.dao.*;
import cn.codedog.service.RankingScore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExamLinkInitializer implements ApplicationRunner {
    private final ExamService service;
    public ExamLinkInitializer(ExamService service){this.service=service;}
    @Override public void run(ApplicationArguments args){service.initializeLinks();}
}
