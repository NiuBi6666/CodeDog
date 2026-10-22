package cn.codedog.controller;
import cn.codedog.service.*;
import cn.codedog.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@RestController @RequestMapping("/api/admin/exams")
public class ExamAdminController {
    private final ExamService service;
    private final ExamExcelReader reader;
    private final ObjectMapper json;
    private final AuditService audit;
    public ExamAdminController(ExamService service,ExamExcelReader reader,ObjectMapper json,AuditService audit){this.service=service;this.reader=reader;this.json=json;this.audit=audit;}
    @GetMapping
    public ExamService.ExamList list(@RequestParam(defaultValue="0") int page){return service.list(page);}
    @PostMapping(value="/inspect",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamExcelReader.Inspection inspect(@RequestParam MultipartFile file,@RequestParam(defaultValue="0") int sheetIndex,@RequestParam(defaultValue="1") int headerRow,@RequestParam(defaultValue="false") boolean manual,HttpServletRequest request){
        audit.owner(request.getUserPrincipal().getName());
        audit.event("exams","READ","exam_headers_inspected");
        audit.target("EXAM_FILE","headers");
        var result=reader.inspect(file,sheetIndex,headerRow,manual);
        audit.detail(java.util.Map.of("file",audit.fileMetadata(file),"sheetIndex",sheetIndex,"headerRow",headerRow,"manual",manual,"columnCount",result.columns().size()));
        return result;
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExamService.AdminExam create(@RequestParam MultipartFile file,@RequestParam String mapping,Principal principal,HttpServletRequest request){
        ExamExcelReader.Mapping parsed;
        try{parsed=json.readValue(mapping,ExamExcelReader.Mapping.class);}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"导入配置无效，请重新选择搜索项和显示项。");}
        var result=service.create(file,parsed,principal.getName());
        audit.owner(principal.getName());
        audit.event("exams","CREATE","exam_created");
        audit.target("EXAM",result.id());
        audit.change("EXAM",result.id(),java.util.Map.of(),java.util.Map.of("title",result.title(),"queryColumns",parsed.queryColumns()==null?java.util.List.of(parsed.nameColumn()):parsed.queryColumns(),"queryLabels",result.queryLabels()==null?java.util.List.of("学员姓名"):result.queryLabels(),"displayLabels",result.scoreLabels(),"studentCount",result.studentCount()));
        audit.detail(java.util.Map.of("file",audit.fileMetadata(file),"studentCount",result.studentCount()));
        audit.record("exam_created:"+result.id()+":"+result.studentCount(),request);
        return result;
    }
    public record Link(String suffix,String expectedSuffix){}
    @PatchMapping("/{id}/link")
    public ExamService.AdminExam link(@PathVariable long id,@RequestBody Link body,HttpServletRequest request){
        var result=service.changeLink(id,body.suffix(),body.expectedSuffix());
        audit.record("exam_link_changed:"+id,request);
        return result;
    }
    public record Status(Boolean enabled){}
    @PatchMapping("/{id}/status")
    public ExamService.AdminExam status(@PathVariable long id,@RequestBody Status body,HttpServletRequest request){
        if(body.enabled()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择查询状态。");
        var result=service.status(id,body.enabled());
        audit.record("exam_status:"+id+":"+body.enabled(),request);
        return result;
    }
}
