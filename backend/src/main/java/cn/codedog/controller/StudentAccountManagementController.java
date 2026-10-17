package cn.codedog.controller;

import cn.codedog.service.*;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rankings/admin")
public class StudentAccountManagementController {
  private final StudentAccountManagementService accounts;
  private final StudentPhoneXlsxParser parser;
  private final AuditService audit;
  public StudentAccountManagementController(StudentAccountManagementService accounts,StudentPhoneXlsxParser parser,AuditService audit) {
    this.accounts=accounts;this.parser=parser;this.audit=audit;
  }
  @GetMapping("/students/account-status")
  public List<StudentAccountManagementService.AccountStatus> statuses(Principal p,HttpServletRequest request) {
    var result=accounts.statuses(p.getName());
    audit.detail(Map.of("returnedCount",result.size()));audit.record("ranking_student_account_status_read",request);return result;
  }
  @GetMapping("/students/{studentId}/phone")
  public StudentAccountManagementService.PhoneInfo phone(@PathVariable String studentId,Principal p,HttpServletRequest request) {
    var result=accounts.phone(p.getName(),studentId);
    audit.target("STUDENT_ACCOUNT",studentId);audit.record("ranking_student_phone_read:"+studentId,request);return result;
  }
  @PutMapping("/students/{studentId}/phone")
  public StudentAccountManagementService.PhoneInfo edit(@PathVariable String studentId,@RequestBody PhoneChange body,Principal p,HttpServletRequest request) {
    audit.target("STUDENT_ACCOUNT",studentId);
    var result=accounts.edit(p.getName(),studentId,body.phone(),body.expectedPhone());
    audit.record("ranking_student_phone_updated:"+studentId,request);return result;
  }
  @PostMapping(value="/imports/phones",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public StudentAccountManagementService.ImportResult importPhones(@RequestParam("file") MultipartFile file,Principal p,HttpServletRequest request) {
    audit.detail(Map.of("file",audit.fileMetadata(file)));
    var result=accounts.importRows(p.getName(),parser.parse(file));
    audit.target("STUDENT_PHONE_IMPORT",result.batchId());
    audit.detail(Map.of("batchId",result.batchId(),"receivedRows",result.receivedRows(),"createdRows",result.createdRows(),"updatedRows",result.updatedRows(),"unchangedRows",result.unchangedRows(),"rejectedRows",result.rejectedRows(),"errors",result.errors()));
    audit.record("ranking_student_phones_imported:"+result.batchId(),request);return result;
  }
  public record PhoneChange(String phone,String expectedPhone) {}
}
