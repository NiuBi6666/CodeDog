package cn.codedog.exams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.*;

@Service
public class ExamService {
    private final ExamRepository exams;
    private final ExamScoreRepository scores;
    private final ExamExcelReader reader;
    private final ObjectMapper json;
    private final ExamQueryAliasRepository aliases;
    private final ExamCodeGenerator codes;
    private final TransactionTemplate transactions;
    public ExamService(ExamRepository exams,ExamScoreRepository scores,ExamExcelReader reader,ObjectMapper json,
                       ExamQueryAliasRepository aliases,ExamCodeGenerator codes,PlatformTransactionManager manager){
        this.exams=exams;this.scores=scores;this.reader=reader;this.json=json;this.aliases=aliases;this.codes=codes;
        this.transactions=new TransactionTemplate(manager);
    }
    public record AdminExam(long id,String title,String publicId,String queryPath,List<String> scoreLabels,int studentCount,boolean enabled,Instant createdAt,String createdBy){}
    public record ExamList(List<AdminExam> exams,long total,int page,int pageCount){}
    public record PublicExam(String title,List<String> scoreLabels){}
    public record QueryResult(List<String> scores){}

    // Each allocation attempt is a separate transaction, so a rare unique-key
    // collision can be retried without retaining a failed transaction.
    public AdminExam create(MultipartFile file,ExamExcelReader.Mapping mapping,String username){
        var parsed=reader.parse(file,mapping);
        for(int attempt=0;attempt<8;attempt++){
            String code=codes.next();
            if(aliases.existsById(code))continue;
            try{
                return transactions.execute(status->{
                    Exam exam=new Exam();
                    exam.title=parsed.title();exam.scoreLabels=encode(parsed.labels());exam.studentCount=parsed.students().size();exam.createdBy=username;
                    exams.saveAndFlush(exam);
                    aliases.saveAndFlush(new ExamQueryAlias(code,exam.id));
                    exam.queryCode=code;
                    List<ExamScore> rows=new ArrayList<>();
                    parsed.students().forEach((name,values)->{
                        ExamScore row=new ExamScore();row.examId=exam.id;row.studentName=name;row.scoreValues=encode(values);rows.add(row);
                    });
                    scores.saveAll(rows);
                    return dto(exam);
                });
            }catch(DataIntegrityViolationException failure){
                if(!aliases.existsById(code))throw failure;
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"暂时无法生成查询链接，请重试。");
    }

    public void initializeLinks(){
        for(Long id:exams.findIdsWithoutQueryCode())initializeLink(id);
    }
    private void initializeLink(Long id){
        for(int attempt=0;attempt<8;attempt++){
            String code=codes.next();
            if(aliases.existsById(code))continue;
            try{
                transactions.executeWithoutResult(status->{
                    Exam exam=exams.findLockedById(id).orElseThrow(()->missing("考试不存在。"));
                    if(exam.queryCode!=null)return;
                    if(exam.id>0&&exam.id<=0xffffffffL){
                        String legacy=String.format(Locale.ROOT,"%08x",exam.id);
                        var previous=aliases.findById(legacy);
                        if(previous.isPresent()&&!previous.get().examId.equals(exam.id))throw new IllegalStateException("Legacy exam link conflict");
                        if(previous.isEmpty())aliases.saveAndFlush(new ExamQueryAlias(legacy,exam.id));
                    }
                    aliases.saveAndFlush(new ExamQueryAlias(code,exam.id));
                    exam.queryCode=code;
                });
                return;
            }catch(DataIntegrityViolationException failure){if(!aliases.existsById(code))throw failure;}
        }
        throw new IllegalStateException("Unable to allocate exam query code");
    }

    public AdminExam changeLink(long id,String suffix,String expectedSuffix){
        if(!ExamCodeGenerator.valid(suffix))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"后缀必须为 8 位，且同时包含大写字母、小写字母和数字。");
        if(expectedSuffix==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请刷新考试列表后重试。");
        try{
            return transactions.execute(status->{
                Exam exam=exams.findLockedById(id).orElseThrow(()->missing("考试不存在。"));
                if(!Objects.equals(exam.queryCode,expectedSuffix))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"链接已被其他管理员修改，请取消本次修改后重试。");
                var previous=aliases.findById(suffix);
                if(previous.isPresent()&&!previous.get().examId.equals(id))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"该后缀已被其他考试使用，请换一个。");
                if(previous.isEmpty())aliases.saveAndFlush(new ExamQueryAlias(suffix,id));
                exam.queryCode=suffix;
                exams.saveAndFlush(exam);
                return dto(exam);
            });
        }catch(DataIntegrityViolationException failure){
            var previous=aliases.findById(suffix);
            if(previous.isPresent()&&!previous.get().examId.equals(id))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"该后缀已被其他考试使用，请换一个。");
            throw failure;
        }
    }

    @Transactional(readOnly=true)
    public ExamList list(int page){
        var result=exams.findAll(PageRequest.of(Math.max(0,page),20,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return new ExamList(result.map(this::dto).getContent(),result.getTotalElements(),result.getNumber(),result.getTotalPages());
    }
    @Transactional
    public AdminExam status(long id,boolean enabled){
        Exam exam=exams.findLockedById(id).orElseThrow(()->missing("考试不存在。"));
        exam.enabled=enabled;
        return dto(exam);
    }
    @Transactional(readOnly=true)
    public PublicExam info(String token){Exam exam=available(token);return new PublicExam(exam.title,decode(exam.scoreLabels));}
    @Transactional(readOnly=true)
    public QueryResult query(String token,String rawName){
        Exam exam=available(token);
        if(rawName==null||rawName.isBlank()||rawName.strip().length()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入完整姓名。");
        String name=rawName.strip();
        ExamScore row=scores.findByExamIdAndStudentName(exam.id,name).orElseThrow(()->missing("未查到成绩，请核对姓名后重试。"));
        if(!row.studentName.equals(name))throw missing("未查到成绩，请核对姓名后重试。");
        return new QueryResult(decode(row.scoreValues));
    }
    private Exam available(String token){
        if(token==null||!token.matches("(?:[A-Za-z0-9]{8}|[0-9a-f]{32})"))throw missing("查询链接不存在。");
        Exam exam=(token.length()==8
            ? aliases.findById(token).filter(alias->alias.code.equals(token)).flatMap(alias->exams.findById(alias.examId))
            : exams.findByPublicId(token)).orElseThrow(()->missing("查询链接不存在。"));
        if(!exam.enabled)throw new ResponseStatusException(HttpStatus.GONE,"本次考试已暂停查询，请联系老师。");
        return exam;
    }
    private AdminExam dto(Exam e){
        if(e.queryCode==null)throw new IllegalStateException("Exam query code has not been initialized");
        return new AdminExam(e.id,e.title,e.queryCode,"/exam/"+e.queryCode,decode(e.scoreLabels),e.studentCount,e.enabled,e.createdAt,e.createdBy);
    }
    private String encode(List<String> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private List<String> decode(String value){try{return json.readValue(value,new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private ResponseStatusException missing(String text){return new ResponseStatusException(HttpStatus.NOT_FOUND,text);}
}
