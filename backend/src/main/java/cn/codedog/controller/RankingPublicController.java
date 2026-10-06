package cn.codedog.controller;
import cn.codedog.service.*;
import cn.codedog.model.RankingPayload;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
@RestController @RequestMapping("/api/public/rankings")
public class RankingPublicController {
  private final RankingBoardService boards;
  private final RankingDeviceService devices;
  private final RankingImportService imports;
  private final StudentRankingAuthService studentAuth;
  public RankingPublicController(RankingBoardService boards, RankingDeviceService devices,
                                 RankingImportService imports, StudentRankingAuthService studentAuth) {
    this.boards=boards;this.devices=devices;this.imports=imports;this.studentAuth=studentAuth;
  }
  @GetMapping("/catalog") public RankingPayload.Catalog catalog(@RequestParam(required=false)String teacherId){return boards.catalog(teacherId);}
  @GetMapping public RankingPayload.Board board(@RequestParam String campId,@RequestParam(required=false)String classId,@RequestParam(defaultValue="class")String scope,@RequestParam(required=false)String teacherId){return boards.board(teacherId,campId,classId,scope);}
  @GetMapping("/all") public RankingPayload.Board all(HttpServletRequest request){return studentAuth.board(request);}
  @GetMapping("/students/{studentId}/opportunities") public RankingPayload.OpportunitySummary opportunities(@PathVariable String studentId, HttpServletRequest request){return studentAuth.opportunities(request, studentId);}
  @GetMapping("/extension/status")
  public RankingPayload.ExtensionStatus status(){return new RankingPayload.ExtensionStatus(true,Instant.now());}
  @GetMapping("/extension/session")
  public RankingPayload.ExtensionSession session(@RequestHeader(value="Authorization",required=false)String authorization){return devices.session(authorization);}
  @PostMapping("/extension/bootstrap") @ResponseStatus(HttpStatus.CREATED)
  public RankingPayload.Connection bootstrap(@RequestBody BootstrapRequest body){return devices.bootstrap(body.crmTeacherId(),body.deviceName());}
  @PostMapping("/extension/connect") @ResponseStatus(HttpStatus.CREATED)
  public RankingPayload.Connection connect(@RequestBody ConnectionRequest body){return devices.connect(body.code(),body.deviceName());}
  @PostMapping("/extension/import")
  public RankingPayload.ImportSummary importData(@RequestHeader(value="Authorization",required=false)String authorization,@RequestBody RankingPayload payload){String owner=devices.authenticateToken(authorization);return imports.importData(payload,"EXTENSION","CRM Chrome 扩展",owner);}
  @PostMapping("/extension/contacts")
  public RankingPayload.ExternalContactSyncSummary syncContacts(@RequestHeader(value="Authorization",required=false)String authorization,@RequestBody RankingPayload.ExternalContactSync payload)
  {String owner=devices.authenticateToken(authorization);return imports.syncExternalContacts(payload,owner);}
  public record BootstrapRequest(String crmTeacherId,String deviceName){}
  public record ConnectionRequest(String code,String deviceName){}
}
