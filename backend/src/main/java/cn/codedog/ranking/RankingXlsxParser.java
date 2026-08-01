package cn.codedog.ranking;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class RankingXlsxParser {
  private static final int MAX_FILES=20,MAX_ROWS=50_000;
  private static final long MAX_BYTES=10L*1024*1024;
  private static final List<Column> COLUMNS=List.of(
    c(0,"A","用户ID"),c(1,"B","姓名"),c(11,"L","班级名称"),c(12,"M","课程名称"),c(15,"P","是否完课"),
    c(20,"U","课中OJ题总数"),c(21,"V","课中OJ题提交数"),c(22,"W","课中OJ题通过数"),
    c(23,"X","课中客观题总数"),c(24,"Y","课中客观题提交数"),c(25,"Z","课中客观题通过数"),
    c(28,"AC","课后作业OJ题总数"),c(29,"AD","课后作业OJ题提交数"),c(30,"AE","课后作业OJ题通过数"),
    c(31,"AF","课后作业客观题总数"),c(32,"AG","课后作业客观题提交数"),c(33,"AH","课后作业客观题通过数"));

  public RankingPayload parse(String campValue,String campNameValue,List<MultipartFile> files){
    String camp=required(campValue,"营期 ID"),campName=required(campNameValue,"营期名称");
    if(files==null||files.isEmpty())throw invalid("请选择至少一个 Excel 文件");
    if(files.size()>MAX_FILES)throw invalid("一次最多上传 "+MAX_FILES+" 个文件");
    Map<String,ClassBuilder> classes=new LinkedHashMap<>();int rows=0;
    for(MultipartFile file:files)rows+=parseFile(camp,file,classes);
    if(rows>MAX_ROWS)throw invalid("全部文件合计不能超过 "+MAX_ROWS+" 行");
    return new RankingPayload(camp,campName,classes.values().stream().map(ClassBuilder::build).toList());
  }

  private int parseFile(String camp,MultipartFile file,Map<String,ClassBuilder> classes){
    String name=file==null||file.getOriginalFilename()==null?"未命名.xlsx":file.getOriginalFilename();
    if(file==null||file.isEmpty())throw invalid(name+"：文件为空");
    if(!name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))throw invalid(name+"：仅支持 .xlsx 文件");
    if(file.getSize()>MAX_BYTES)throw invalid(name+"：文件不能超过 10MB");
    try(InputStreamCloseable ignored=new InputStreamCloseable();Workbook workbook=WorkbookFactory.create(file.getInputStream())){
      if(!(workbook instanceof XSSFWorkbook))throw invalid(name+"：仅支持标准 .xlsx 文件");
      if(workbook.getNumberOfSheets()==0)throw invalid(name+"：没有工作表");
      Sheet sheet=workbook.getSheetAt(0);Row header=sheet.getRow(0);if(header==null)throw invalid(name+"：缺少表头");
      DataFormatter formatter=new DataFormatter(Locale.CHINA);
      for(Column column:COLUMNS){String actual=value(header.getCell(column.index),formatter);if(!column.header.equals(actual))throw invalid(name+"："+column.letter+" 列表头必须是“"+column.header+"”");}
      int count=0;
      for(int i=1;i<=sheet.getLastRowNum();i++){
        Row row=sheet.getRow(i);if(row==null)continue;
        Map<String,String> values=new HashMap<>();for(Column column:COLUMNS)values.put(column.letter,value(row.getCell(column.index),formatter));
        if(values.values().stream().allMatch(String::isBlank))continue;
        String studentId=required(values.get("A"),name+" 第 "+(i+1)+" 行学员 ID");
        String studentName=required(values.get("B"),name+" 第 "+(i+1)+" 行学员姓名");
        String className=required(values.get("L"),name+" 第 "+(i+1)+" 行班级名称");
        String lessonName=required(values.get("M"),name+" 第 "+(i+1)+" 行课程名称");
        String classId="xlsx-"+digest(camp+"|"+className).substring(0,24);
        String lessonId="xlsx-"+digest(camp+"|"+className+"|"+lessonName).substring(0,24);
        var inclass=counts(values,"U","V","W","X","Y","Z",name,i+1);
        var homework=counts(values,"AC","AD","AE","AF","AG","AH",name,i+1);
        var result=new RankingPayload.StudentResult(studentId,studentName,completion(values.get("P"),name,i+1),inclass,homework);
        classes.computeIfAbsent(classId,id->new ClassBuilder(classId,className)).add(lessonId,lessonName,result);count++;
      }
      if(count==0)throw invalid(name+"：没有可导入的数据");return count;
    }catch(ResponseStatusException error){throw error;}catch(Exception error){throw invalid(name+"：Excel 读取失败（"+error.getMessage()+"）");}
  }

  private RankingPayload.Counts counts(Map<String,String> v,String ta,String sa,String pa,String tb,String sb,String pb,String file,int row){
    return new RankingPayload.Counts(number(v.get(ta),file,row,ta)+number(v.get(tb),file,row,tb),number(v.get(sa),file,row,sa)+number(v.get(sb),file,row,sb),number(v.get(pa),file,row,pa)+number(v.get(pb),file,row,pb));
  }
  private int number(String value,String file,int row,String column){if(value==null||value.isBlank())return 0;try{double n=Double.parseDouble(value);if(n<0||n!=Math.rint(n))throw new NumberFormatException();return Math.toIntExact((long)n);}catch(RuntimeException e){throw invalid(file+"：第 "+row+" 行 "+column+" 列必须是非负整数");}}
  private double completion(String value,String file,int row){String n=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(List.of("是","已完课","完成","true","yes").contains(n))return 100;if(List.of("否","未完课","未完成","false","no","").contains(n))return 0;try{boolean percent=n.endsWith("%");double d=Double.parseDouble(n.replace("%",""));if(!percent&&d>=0&&d<=1)d*=100;if(!Double.isFinite(d)||d<0||d>100)throw new NumberFormatException();return d;}catch(Exception e){throw invalid(file+"：第 "+row+" 行 P 列完课率无效");}}
  private String value(Cell cell,DataFormatter formatter){if(cell==null||cell.getCellType()==CellType.BLANK)return "";if(cell.getCellType()==CellType.FORMULA)throw invalid("Excel 不支持公式单元格");return formatter.formatCellValue(cell).trim();}
  private String required(String value,String label){String n=value==null?"":value.trim();if(n.isEmpty())throw invalid(label+"不能为空");return n;}
  private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
  private static Column c(int index,String letter,String header){return new Column(index,letter,header);}private record Column(int index,String letter,String header){}
  private static final class ClassBuilder{final String id,name;final Map<String,LessonBuilder> lessons=new LinkedHashMap<>();ClassBuilder(String id,String name){this.id=id;this.name=name;}void add(String id,String name,RankingPayload.StudentResult student){lessons.computeIfAbsent(id,k->new LessonBuilder(id,name)).students.put(student.studentId(),student);}RankingPayload.ClassData build(){return new RankingPayload.ClassData(id,name,lessons.values().stream().map(LessonBuilder::build).toList());}}
  private static final class LessonBuilder{final String id,name;final Map<String,RankingPayload.StudentResult> students=new LinkedHashMap<>();LessonBuilder(String id,String name){this.id=id;this.name=name;}RankingPayload.LessonData build(){return new RankingPayload.LessonData(id,name,null,null,List.copyOf(students.values()));}}
  private static final class InputStreamCloseable implements AutoCloseable{public void close(){}}
}
