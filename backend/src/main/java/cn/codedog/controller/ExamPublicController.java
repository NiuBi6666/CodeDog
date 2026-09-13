package cn.codedog.controller;
import cn.codedog.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/public/exams")
public class ExamPublicController {
    private final ExamService service;
    private final Map<String,ArrayDeque<Long>> attempts=new LinkedHashMap<>(128,.75f,true);
    public ExamPublicController(ExamService service){this.service=service;}
    @GetMapping("/{token}")
    public ResponseEntity<ExamService.PublicExam> info(@PathVariable String token){return noStore(service.info(token));}
    public record Query(String name){}
    @PostMapping("/{token}/query")
    public ResponseEntity<ExamService.QueryResult> query(@PathVariable String token,@RequestBody Query body,HttpServletRequest request){
        limit(request);
        return noStore(service.query(token,body.name()));
    }
    private <T> ResponseEntity<T> noStore(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag","noindex, nofollow").body(value);}
    private synchronized void limit(HttpServletRequest request){
        String ip=request.getHeader("X-Real-IP");
        if(ip==null||ip.length()>80)ip=request.getRemoteAddr();
        long now=System.nanoTime();
        ArrayDeque<Long> queue=attempts.computeIfAbsent(ip,key->new ArrayDeque<>());
        while(!queue.isEmpty()&&now-queue.peekFirst()>60_000_000_000L)queue.removeFirst();
        if(queue.size()>=30)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"查询次数较多，请一分钟后重试。");
        queue.addLast(now);
        if(attempts.size()>10000)attempts.remove(attempts.keySet().iterator().next());
    }
}
