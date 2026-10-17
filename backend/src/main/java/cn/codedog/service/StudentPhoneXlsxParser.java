package cn.codedog.service;

import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StudentPhoneXlsxParser {
  public record PhoneRow(int rowNumber, String studentId, String studentName, String phone, String error) {}
  public List<PhoneRow> parse(MultipartFile file) {
    if (file == null || file.isEmpty()) throw invalid("请选择手机号 Excel 文件");
    if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      throw invalid("仅支持 .xlsx 文件");
    if (file.getSize() > 10L*1024*1024) throw invalid("文件不能超过 10 MB");
    try (var input=file.getInputStream(); Workbook workbook=WorkbookFactory.create(input)) {
      if (!(workbook instanceof XSSFWorkbook) || workbook.getNumberOfSheets()==0) throw invalid("请选择标准 .xlsx 文件");
      Sheet sheet=workbook.getSheetAt(0);
      Row header=sheet.getRow(0);
      if (header==null) throw invalid("缺少表头：用户ID、姓名、手机号");
      var formatter=new DataFormatter(Locale.CHINA);
      List<Set<String>> headers=List.of(Set.of("用户ID","学生ID","学员ID","ID"), Set.of("姓名","学生姓名","学员姓名"), Set.of("手机号","手机号码"));
      for (int i=0;i<3;i++) if (!headers.get(i).contains(text(header.getCell(i),formatter).replace(" ","")))
        throw invalid("A、B、C 列必须依次为用户ID、姓名、手机号");
      if (sheet.getLastRowNum()>5000) throw invalid("一次最多导入 5000 行，请分批上传");
      List<PhoneRow> rows=new ArrayList<>();
      for (int i=1;i<=sheet.getLastRowNum();i++) {
        Row row=sheet.getRow(i); if(row==null)continue;
        String id="", name="", phone="";
        try {
          id=text(row.getCell(0),formatter); name=text(row.getCell(1),formatter); phone=text(row.getCell(2),formatter);
          if(id.isEmpty()&&name.isEmpty()&&phone.isEmpty())continue;
          String error=id.isEmpty()||id.length()>100 ? "学生 ID 不能为空且不能超过 100 字符"
            : name.isEmpty()||name.length()>100 ? "姓名不能为空且不能超过 100 字符"
            : !phone.matches("^1\\d{10}$") ? "手机号必须为 11 位有效数字" : null;
          rows.add(new PhoneRow(i+1,id,name,phone,error));
        } catch(ResponseStatusException e) { rows.add(new PhoneRow(i+1,id,name,phone,e.getReason())); }
      }
      if(rows.isEmpty())throw invalid("没有可导入的手机号数据");
      return List.copyOf(rows);
    } catch(ResponseStatusException e) { throw e; }
    catch(Exception e) { throw invalid("Excel 读取失败，请检查文件格式"); }
  }
  private String text(Cell cell,DataFormatter formatter) {
    if(cell==null||cell.getCellType()==CellType.BLANK)return "";
    if(cell.getCellType()==CellType.FORMULA||cell.getCellType()==CellType.ERROR)throw invalid("不支持公式或错误单元格，请粘贴为值");
    if(cell.getCellType()==CellType.NUMERIC) {
      try { return BigDecimal.valueOf(cell.getNumericCellValue()).toBigIntegerExact().toString(); }
      catch(ArithmeticException e) { throw invalid("ID 和手机号必须是整数或文本"); }
    }
    return formatter.formatCellValue(cell).trim();
  }
  private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message); }
}
