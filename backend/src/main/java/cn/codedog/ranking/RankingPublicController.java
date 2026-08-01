package cn.codedog.ranking;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/public/rankings")
public class RankingPublicController {
  private final RankingService service;
  public RankingPublicController(RankingService service){this.service=service;}
  @GetMapping("/catalog") public RankingPayload.Catalog catalog(){return service.catalog();}
  @GetMapping public RankingPayload.Board board(@RequestParam String campId,@RequestParam(required=false)String classId,@RequestParam(defaultValue="class")String scope){return service.board(campId,classId,scope);}
  @PostMapping("/extension/connect") @ResponseStatus(HttpStatus.CREATED)
  public RankingPayload.Connection connect(@RequestBody ConnectionRequest body){return service.connect(body.code(),body.deviceName());}
  @PostMapping("/extension/import")
  public RankingPayload.ImportSummary importData(@RequestHeader(value="Authorization",required=false)String authorization,@RequestBody RankingPayload payload){String owner=service.authenticateToken(authorization);return service.importData(payload,"EXTENSION","CRM Chrome 扩展",owner);}
  public record ConnectionRequest(String code,String deviceName){}
}
