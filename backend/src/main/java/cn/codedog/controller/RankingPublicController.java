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
  private final AuditService audit;
  public RankingPublicController(RankingBoardService boards, RankingDeviceService devices,
                                 RankingImportService imports, StudentRankingAuthService studentAuth,
                                 AuditService audit) {
    this.boards=boards;this.devices=devices;this.imports=imports;this.studentAuth=studentAuth;this.audit=audit;
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
  public RankingPayload.Connection bootstrap(@RequestBody BootstrapRequest body,HttpServletRequest request){
    RankingPayload.Connection value=devices.bootstrap(body.crmTeacherId(),body.deviceName());
    audit.owner(value.username());audit.actor("EXTENSION_DEVICE",String.valueOf(value.deviceId()),body.deviceName());
    audit.change("EXTENSION_DEVICE",value.deviceId(),java.util.Map.of(),java.util.Map.of(
      "deviceName",body.deviceName(),"ownerUsername",value.username(),"crmTeacherId",value.crmTeacherId(),"revoked",false));
    audit.record("ranking_extension_bootstrap:"+value.deviceId(),request);
    return value;
  }
  @PostMapping("/extension/connect") @ResponseStatus(HttpStatus.CREATED)
  public RankingPayload.Connection connect(@RequestBody ConnectionRequest body,HttpServletRequest request){
    RankingPayload.Connection value=devices.connect(body.code(),body.deviceName());
    audit.owner(value.username());audit.actor("EXTENSION_DEVICE",String.valueOf(value.deviceId()),body.deviceName());
    audit.change("EXTENSION_DEVICE",value.deviceId(),java.util.Map.of(),java.util.Map.of(
      "deviceName",body.deviceName(),"ownerUsername",value.username(),"crmTeacherId",value.crmTeacherId(),"revoked",false));
    audit.record("ranking_extension_connected:"+value.deviceId(),request);
    return value;
  }
  @PostMapping("/extension/import")
  public RankingPayload.ImportSummary importData(@RequestHeader(value="Authorization",required=false)String authorization,
                                                  @RequestBody RankingPayload payload,HttpServletRequest request){
    String owner=devices.authenticateToken(authorization);
    RankingPayload.ImportSummary result=imports.importData(payload,"EXTENSION","CRM Chrome 扩展",owner);
    audit.owner(owner);audit.target("IMPORT_BATCH",result.batchId());
    audit.detail(java.util.Map.of("batchId",result.batchId(),"source","CRM Chrome 扩展",
      "campId",payload.campId(),"campName",payload.campName(),"receivedRows",result.receivedRows(),
      "changedRows",result.changedRows(),"unchangedRows",result.unchangedRows(),
      "rejectedRows",result.rejectedRows(),"errorCount",result.errors().size(),
      "errorSummary",errorSummary(result.errors())));
    audit.record("ranking_extension_import:"+result.batchId(),request);
    return result;
  }
  @PostMapping("/extension/contacts")
  public RankingPayload.ExternalContactSyncSummary syncContacts(@RequestHeader(value="Authorization",required=false)String authorization,
      @RequestBody RankingPayload.ExternalContactSync payload,HttpServletRequest request) {
    String owner=devices.authenticateToken(authorization);
    RankingPayload.ExternalContactSyncSummary result=imports.syncExternalContacts(payload,owner);
    audit.owner(owner);audit.detail(java.util.Map.of("received",result.received(),"inserted",result.inserted(),
      "unchanged",result.unchanged(),"conflicts",result.conflicts(),"errorCount",result.errors().size()));
    audit.record("ranking_extension_contacts_synced",request);
    return result;
  }
  private java.util.Map<String,Long> errorSummary(java.util.List<RankingPayload.RowError> errors) {
    java.util.Map<String,Long> summary=new java.util.LinkedHashMap<>();
    for(RankingPayload.RowError error:errors) summary.merge(error.message(),1L,Long::sum);
    return summary;
  }
  public record BootstrapRequest(String crmTeacherId,String deviceName){}
  public record ConnectionRequest(String code,String deviceName){}
}
