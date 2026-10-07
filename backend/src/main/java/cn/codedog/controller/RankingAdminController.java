package cn.codedog.controller;
import cn.codedog.service.*;
import cn.codedog.model.RankingPayload;
import cn.codedog.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.List;
@RestController @RequestMapping("/api/rankings/admin")
public class RankingAdminController {
  private final RankingImportService imports;private final RankingDeviceService devices;private final RankingXlsxParser xlsx;private final AuditService audit;
  public RankingAdminController(RankingImportService imports,RankingDeviceService devices,RankingXlsxParser xlsx,AuditService audit){this.imports=imports;this.devices=devices;this.xlsx=xlsx;this.audit=audit;}
  @PostMapping("/pairing-codes") public RankingPayload.PairingCode pairing(Principal p,HttpServletRequest r){
    var value=devices.createPairingCode(p.getName());
    audit.detail(java.util.Map.of("expiresAt",value.expiresAt(),"pairingCode","[REDACTED]"));
    audit.record("ranking_pairing_code_created",r);return value;
  }
  @GetMapping("/devices") public List<RankingPayload.Device> devices(Principal p){return devices.devices(p.getName());}
  @DeleteMapping("/devices/{id}") public void revoke(@PathVariable long id,Principal p,HttpServletRequest r){
    var before=devices.devices(p.getName()).stream().filter(value->value.id()==id).findFirst().orElse(null);
    devices.revoke(id,p.getName());
    audit.change("EXTENSION_DEVICE",id,before==null?java.util.Map.of():java.util.Map.of("deviceName",before.deviceName(),"revoked",before.revoked()),java.util.Map.of("revoked",true));
    audit.record("ranking_device_revoked:"+id,r);
  }
  @PostMapping(value="/imports/xlsx",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public RankingPayload.ImportSummary xlsx(@RequestParam String campId,@RequestParam String campName,@RequestParam("files")List<MultipartFile> files,Principal p,HttpServletRequest r){
    var metadata=files.stream().map(audit::fileMetadata).toList();
    var result=imports.importData(xlsx.parse(campId,campName,files),"XLSX",files.size()+" 个 Excel 文件",p.getName());
    audit.target("IMPORT_BATCH",result.batchId());
    audit.detail(java.util.Map.of("campId",campId,"campName",campName,"files",metadata,"receivedRows",result.receivedRows(),"changedRows",result.changedRows(),"unchangedRows",result.unchangedRows(),"rejectedRows",result.rejectedRows(),"errorCount",result.errors().size()));
    audit.record("ranking_xlsx_import:"+result.batchId(),r);return result;
  }
}
