package cn.codedog.ranking;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestController @RequestMapping("/api/public/rankings")
public class RankingPublicController {
  private final RankingService service;
  public RankingPublicController(RankingService service){this.service=service;}
  @GetMapping("/catalog") public RankingPayload.Catalog catalog(@RequestParam(required=false)String teacherId){return service.catalog(teacherId);}
  @GetMapping public RankingPayload.Board board(@RequestParam String campId,@RequestParam(required=false)String classId,@RequestParam(defaultValue="class")String scope,@RequestParam(required=false)String teacherId){return service.board(teacherId,campId,classId,scope);}
  @GetMapping("/extension/status")
  public RankingPayload.ExtensionStatus status(){return new RankingPayload.ExtensionStatus(true,Instant.now());}
  @GetMapping("/extension/session")
  public RankingPayload.ExtensionSession session(@RequestHeader(value="Authorization",required=false)String authorization){return service.session(authorization);}
  @PostMapping("/extension/bootstrap") @ResponseStatus(HttpStatus.CREATED)
  public RankingPayload.Connection bootstrap(@RequestBody BootstrapRequest body){return service.bootstrap(body.crmTeacherId(),body.deviceName());}
  @PostMapping("/extension/connect") @ResponseStatus(HttpStatus.CREATED)
  public RankingPayload.Connection connect(@RequestBody ConnectionRequest body){return service.connect(body.code(),body.deviceName());}
  @PostMapping("/extension/import")
  public RankingPayload.ImportSummary importData(@RequestHeader(value="Authorization",required=false)String authorization,@RequestBody RankingPayload payload){String owner=service.authenticateToken(authorization);return service.importData(payload,"EXTENSION","CRM Chrome 扩展",owner);}
  public record BootstrapRequest(String crmTeacherId,String deviceName){}
  public record ConnectionRequest(String code,String deviceName){}
}
