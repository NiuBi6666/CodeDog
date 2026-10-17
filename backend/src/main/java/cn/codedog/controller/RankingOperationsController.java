package cn.codedog.controller;
import cn.codedog.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.*;
@RestController public class RankingOperationsController {
 private final RankingPointsService points;private final RankingImportWorkflow workflow;private final RankingXlsxParser parser;private final StudentRankingAuthService students;private final AuditService audit;
 public RankingOperationsController(RankingPointsService p,RankingImportWorkflow w,RankingXlsxParser x,StudentRankingAuthService s,AuditService a){points=p;workflow=w;parser=x;students=s;audit=a;}
 @GetMapping("/api/rankings/admin/operations/students/{id}/ledger") public Object ledger(@PathVariable String id,@RequestParam(defaultValue="0")int page,Principal p){return points.ledger(p.getName(),id,page);}
 @GetMapping("/api/public/rankings/student-ledger") public Object studentLedger(@RequestParam(defaultValue="0")int page,HttpServletRequest r){var s=students.current(r);return points.ledger(s.ownerUsername(),s.studentId(),page);}
 @GetMapping("/api/rankings/admin/operations/reconciliations") public Object reports(Principal p){return points.reports(p.getName());}
 @PostMapping("/api/rankings/admin/operations/reconciliations") public Object reconcile(Principal p,HttpServletRequest r){var v=points.reconcile(p.getName());audit.detail(v);audit.record("ranking_reconciliation_executed",r);return v;}
 @PostMapping("/api/rankings/admin/operations/import-previews") public Object preview(@RequestParam(defaultValue="spreadsheet-points")String campId,@RequestParam(defaultValue="表格积分")String campName,@RequestParam("files")List<MultipartFile> files,Principal p,HttpServletRequest r){var v=workflow.preview(p.getName(),parser.parse(campId,campName,files));audit.detail(Map.of("files",files.stream().map(audit::fileMetadata).toList(),"previewId",v.get("previewId")));audit.record("ranking_import_preview_created",r);return v;}
 @PostMapping("/api/rankings/admin/operations/import-previews/{id}/confirm") public Object confirm(@PathVariable String id,Principal p,HttpServletRequest r){var v=workflow.confirm(p.getName(),id);audit.target("IMPORT_BATCH",v.batchId());audit.detail(Map.of("changed",v.changedRows(),"rejected",v.rejectedRows(),"errors",v.errors()));audit.record("ranking_import_confirmed",r);return v;}
 @GetMapping("/api/rankings/admin/operations/import-batches") public Object batches(Principal p){return workflow.batches(p.getName());}
 @PostMapping("/api/rankings/admin/operations/import-batches/{id}/reverse") public Object reverse(@PathVariable long id,@RequestBody Reason reason,Principal p,HttpServletRequest r){var v=workflow.reverse(p.getName(),id,reason.reason());audit.change("IMPORT_BATCH",id,Map.of("status","COMPLETED"),Map.of("status","REVERSED","reason",reason.reason()));audit.detail(v);audit.record("ranking_import_reversed",r);return v;}

 @PostMapping("/api/rankings/admin/operations/reconciliations/repair") public Object repair(@RequestBody Repair body,Principal p,HttpServletRequest r){var v=points.repair(p.getName(),body.studentId(),body.expectedSource(),body.expectedLedger(),body.reason());audit.change("POINT_LEDGER",body.studentId(),Map.of("ledgerBalance",body.expectedLedger()),Map.of("ledgerBalance",body.expectedSource(),"reason",body.reason()));audit.record("ranking_ledger_repaired",r);return v;}
 public record Repair(String studentId,long expectedSource,long expectedLedger,String reason){}
 public record Reason(String reason){}
}
