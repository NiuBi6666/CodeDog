package cn.codedog.ranking;
import cn.codedog.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.List;
@RestController @RequestMapping("/api/rankings/admin")
public class RankingAdminController {
  private final RankingService service;private final RankingXlsxParser xlsx;private final AuditService audit;
  public RankingAdminController(RankingService service,RankingXlsxParser xlsx,AuditService audit){this.service=service;this.xlsx=xlsx;this.audit=audit;}
  @PostMapping("/pairing-codes") public RankingPayload.PairingCode pairing(Principal p,HttpServletRequest r){var value=service.createPairingCode(p.getName());audit.record("ranking_pairing_code_created",r);return value;}
  @GetMapping("/devices") public List<RankingPayload.Device> devices(Principal p){return service.devices(p.getName());}
  @DeleteMapping("/devices/{id}") public void revoke(@PathVariable long id,Principal p,HttpServletRequest r){service.revoke(id,p.getName());audit.record("ranking_device_revoked:"+id,r);}
  @PostMapping(value="/imports/xlsx",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public RankingPayload.ImportSummary xlsx(@RequestParam String campId,@RequestParam String campName,@RequestParam("files")List<MultipartFile> files,Principal p,HttpServletRequest r){var result=service.importData(xlsx.parse(campId,campName,files),"XLSX",files.size()+" 个 Excel 文件",p.getName());audit.record("ranking_xlsx_import:"+result.batchId(),r);return result;}
}
