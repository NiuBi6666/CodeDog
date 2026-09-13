package cn.codedog.exams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public ExamService(ExamRepository exams,ExamScoreRepository scores,ExamExcelReader reader,ObjectMapper json){
        this.exams=exams;this.scores=scores;this.reader=reader;this.json=json;
    }
    public record AdminExam(long id,String title,String publicId,String queryPath,List<String> scoreLabels,int studentCount,boolean enabled,Instant createdAt,String createdBy){}
    public record ExamList(List<AdminExam> exams,long total,int page,int pageCount){}
    public record PublicExam(String title,List<String> scoreLabels){}
    public record QueryResult(List<String> scores){}

    @Transactional
    public AdminExam create(MultipartFile file,ExamExcelReader.Mapping mapping,String username){
        var parsed=reader.parse(file,mapping);
        Exam exam=new Exam();
        exam.title=parsed.title();exam.scoreLabels=encode(parsed.labels());exam.studentCount=parsed.students().size();exam.createdBy=username;
        exams.saveAndFlush(exam);
        List<ExamScore> rows=new ArrayList<>();
        parsed.students().forEach((name,values)->{
            ExamScore row=new ExamScore();row.examId=exam.id;row.studentName=name;row.scoreValues=encode(values);rows.add(row);
        });
        scores.saveAll(rows);
        return dto(exam);
    }
    @Transactional(readOnly=true)
    public ExamList list(int page){
        var result=exams.findAll(PageRequest.of(Math.max(0,page),20,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return new ExamList(result.map(this::dto).getContent(),result.getTotalElements(),result.getNumber(),result.getTotalPages());
    }
    @Transactional
    public AdminExam status(long id,boolean enabled){
        Exam exam=exams.findById(id).orElseThrow(()->missing("考试不存在。"));
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
        // Retain strict matching even if a database is configured with a broader collation.
        if(!row.studentName.equals(name))throw missing("未查到成绩，请核对姓名后重试。");
        return new QueryResult(decode(row.scoreValues));
    }
    private Exam available(String token){
        if(token==null||!token.matches("(?:[0-9a-f]{8}|[0-9a-f]{32})"))throw missing("查询链接不存在。");
        Exam exam=(token.length()==8 ? exams.findById(Long.parseLong(token,16)) : exams.findByPublicId(token))
            .orElseThrow(()->missing("查询链接不存在。"));
        if(!exam.enabled) throw new ResponseStatusException(HttpStatus.GONE,"本次考试已暂停查询，请联系老师。");
        return exam;
    }
    // The primary key gives every exam a stable, collision-free eight-character alias.
    // Keep the original UUID stored so previously shared links continue to resolve.
    private String shortToken(Exam e){
        if(e.id<1||e.id>0xffffffffL)throw new IllegalStateException("Exam short-link capacity exceeded");
        return String.format(Locale.ROOT,"%08x",e.id);
    }
    private AdminExam dto(Exam e){String token=shortToken(e);return new AdminExam(e.id,e.title,token,"/exam/"+token,decode(e.scoreLabels),e.studentCount,e.enabled,e.createdAt,e.createdBy);}
    private String encode(List<String> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private List<String> decode(String value){try{return json.readValue(value,new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private ResponseStatusException missing(String text){return new ResponseStatusException(HttpStatus.NOT_FOUND,text);}
}
