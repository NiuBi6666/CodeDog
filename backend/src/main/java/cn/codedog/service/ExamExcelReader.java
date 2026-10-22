package cn.codedog.service;
import cn.codedog.model.*;
import cn.codedog.dao.*;
import cn.codedog.service.RankingScore;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ExamExcelReader {
    static final int MAX_ROWS=10000, MAX_COLS=256, MAX_SCORES=20;
    private static final Pattern QUESTION=Pattern.compile("^第([1-9][0-9]{0,2})题得分$");
    public record SheetChoice(int index,String name) {}
    public record Column(int index,String letter,String label) {}
    public record Template(String type,int nameColumn,int submissionColumn,List<Integer> scoreColumns,List<String> scoreLabels,boolean defaultLayout) {}
    public record Inspection(List<SheetChoice> sheets,int sheetIndex,int headerRow,List<Column> columns,List<List<String>> samples,Template template) {}
    public record Mapping(String title,int sheetIndex,int headerRow,int nameColumn,List<Integer> scoreColumns,List<String> scoreLabels,List<Integer> queryColumns) {
        public Mapping(String title,int sheetIndex,int headerRow,int nameColumn,List<Integer> scoreColumns,List<String> scoreLabels){this(title,sheetIndex,headerRow,nameColumn,scoreColumns,scoreLabels,null);}
    }
    public record Parsed(String title,List<String> labels,Map<String,List<String>> students,String resultMode,Set<String> absentNames,List<String> queryLabels) {
        public Parsed(String title,List<String> labels,Map<String,List<String>> students,String resultMode,Set<String> absentNames){this(title,labels,students,resultMode,absentNames,null);}
    }

    public Inspection inspect(MultipartFile file,int sheetIndex,int headerRow) {return inspect(file,sheetIndex,headerRow,false);}
    public Inspection inspect(MultipartFile file,int sheetIndex,int headerRow,boolean manual) {
        try (Workbook book=open(file)) {
            Sheet sheet=sheet(book,sheetIndex,headerRow);
            DataFormatter format=formatter();
            List<SheetChoice> sheets=new ArrayList<>();
            for(int i=0;i<book.getNumberOfSheets();i++) sheets.add(new SheetChoice(i,book.getSheetName(i)));
            List<Column> columns=columns(sheet,headerRow,format);
            Template template=manual?null:template(columns,sheet,headerRow,format);
            List<List<String>> samples=new ArrayList<>();
            for(int r=headerRow;r<=sheet.getLastRowNum()&&samples.size()<5;r++){
                Row row=sheet.getRow(r); if(row==null) continue;
                List<String> values=new ArrayList<>();
                for(int c=0;c<columns.size();c++) values.add(text(row.getCell(c),format));
                if(values.stream().anyMatch(v->!v.isBlank())) samples.add(values);
            }
            return new Inspection(sheets,sheetIndex,headerRow,columns,samples,template);
        } catch(ResponseStatusException e){throw e;}
        catch(Exception e){throw invalid("无法读取 Excel，请上传未加密的 .xlsx 或 .xls 文件。");}
    }

    public Parsed parse(MultipartFile file,Mapping mapping) {
        if(mapping==null) throw invalid("请配置考试名称、姓名列和成绩列。");
        if(mapping.queryColumns()!=null)return parseCustom(file,mapping);
        String title=clean(mapping.title(),120,"考试名称");
        try(Workbook book=open(file)){
            Sheet sheet=sheet(book,mapping.sheetIndex(),mapping.headerRow());
            DataFormatter format=formatter();
            Template template=template(columns(sheet,mapping.headerRow(),format),sheet,mapping.headerRow(),format);
            // Recognized templates always use server-selected score columns,
            // regardless of a stale or modified client mapping.
            int nameColumn=template==null?mapping.nameColumn():template.nameColumn();
            List<Integer> selectedColumns=template==null?mapping.scoreColumns():template.scoreColumns();
            List<String> rawLabels=template==null?mapping.scoreLabels():template.scoreLabels();
            int limit=template==null?MAX_SCORES:MAX_COLS;
            if(nameColumn<0||nameColumn>=MAX_COLS) throw invalid("请选择姓名列。");
            if(selectedColumns==null||selectedColumns.isEmpty()||selectedColumns.size()>limit||rawLabels==null||rawLabels.size()!=selectedColumns.size())
                throw invalid("请选择 1 至 "+limit+" 列成绩，并填写对应名称。");
            Set<Integer> selected=new HashSet<>();
            List<String> labels=new ArrayList<>();
            for(int i=0;i<selectedColumns.size();i++){
                Integer c=selectedColumns.get(i);
                if(c==null||c<0||c>=MAX_COLS||c==nameColumn||!selected.add(c))
                    throw invalid("姓名列和成绩列不能重复，成绩列也不能重复选择。");
                labels.add(clean(rawLabels.get(i),64,"成绩名称"));
            }
            if(new HashSet<>(labels).size()!=labels.size()) throw invalid("成绩名称不能重复。");
            Map<String,List<String>> students=new LinkedHashMap<>();
            Set<String> absentNames=new HashSet<>();
            Map<String,Integer> seen=new HashMap<>();
            for(int r=mapping.headerRow();r<=sheet.getLastRowNum();r++){
                Row row=sheet.getRow(r); if(row==null) continue;
                String name=text(row.getCell(nameColumn),format).strip();
                String submitted=template==null?"":text(row.getCell(template.submissionColumn()),format);
                boolean absent=template!=null&&"NaT".equalsIgnoreCase(submitted.strip());
                List<String> values=new ArrayList<>();
                boolean any=!submitted.isBlank();
                for(int c:selectedColumns){
                    Cell cell=row.getCell(c);
                    String value=text(cell,format).strip();
                    if(!value.isEmpty()) any=true;
                    if(absent) continue;
                    checkCell(cell,r);
                    if(value.length()>80) throw invalid("第 "+(r+1)+" 行成绩内容过长，请确认选中了成绩列。");
                    values.add(template!=null&&template.type().equals("full")&&c!=selectedColumns.getFirst()&&value.isBlank()?"":displayScore(cell,value,template==null));
                }
                if(name.isBlank()&&!any) continue;
                checkCell(row.getCell(nameColumn),r);
                if(template!=null) checkCell(row.getCell(template.submissionColumn()),r);
                if(name.isBlank()) throw invalid("第 "+(r+1)+" 行有成绩但没有姓名，请补全后上传。");
                if(name.length()>100) throw invalid("第 "+(r+1)+" 行姓名过长，请确认姓名列。");
                if(seen.containsKey(name)) throw invalid("第 "+seen.get(name)+" 行与第 "+(r+1)+" 行姓名重复（"+name+"），请处理重名后再上传。");
                seen.put(name,r+1);
                students.put(name,values);
                if(absent) absentNames.add(name);
            }
            if(students.isEmpty()) throw invalid("未找到学员，请核对表头行和姓名列。");
            return new Parsed(title,labels,students,template==null?"legacy":template.type(),absentNames);
        } catch(ResponseStatusException e){throw e;}
        catch(Exception e){throw invalid("无法读取 Excel，请检查文件格式及单元格内容。");}
    }

    private Parsed parseCustom(MultipartFile file,Mapping mapping) {
        String title=clean(mapping.title(),120,"考试名称");
        try(Workbook book=open(file)){
            Sheet sheet=sheet(book,mapping.sheetIndex(),mapping.headerRow());
            DataFormatter format=formatter();
            var headers=columns(sheet,mapping.headerRow(),format);
            var query=validateColumns(mapping.queryColumns(),headers.size(),"搜索项");
            var display=validateColumns(mapping.scoreColumns(),headers.size(),"显示项");
            var queryLabels=query.stream().map(i->columnLabel(headers,i)).toList();
            var displayLabels=display.stream().map(i->columnLabel(headers,i)).toList();
            Map<String,List<String>> students=new LinkedHashMap<>();
            Map<String,Integer> seen=new HashMap<>();
            var rowJson=new com.fasterxml.jackson.databind.ObjectMapper();
            for(int n=mapping.headerRow();n<=sheet.getLastRowNum();n++){
                Row row=sheet.getRow(n);if(row==null)continue;
                boolean any=false;
                for(int c=0;c<headers.size();c++)if(!text(row.getCell(c),format).isBlank()){any=true;break;}
                if(!any)continue;
                List<String> values=new ArrayList<>(),keys=new ArrayList<>();
                for(int c:query){
                    checkCell(row.getCell(c),n);String v=text(row.getCell(c),format);
                    if(v.isBlank()||v.length()>2000)throw invalid("第 "+(n+1)+" 行搜索项【"+columnLabel(headers,c)+"】为空或超过 2000 字，请补全后上传。");
                    keys.add(v);
                }
                String key=ExamSearchKey.of(keys);
                if(seen.containsKey(key))throw invalid("第 "+seen.get(key)+" 行与第 "+(n+1)+" 行的搜索条件组合重复，请增加搜索列或处理重复数据。");
                for(int c:display){
                    checkCell(row.getCell(c),n);String v=text(row.getCell(c),format);
                    if(v.length()>2000)throw invalid("第 "+(n+1)+" 行显示内容超过 2000 字，请缩短后上传。");
                    values.add(v);
                }
                String encoded=rowJson.writeValueAsString(values);
                if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000)throw invalid("第 "+(n+1)+" 行显示内容过多，请减少显示项。");
                seen.put(key,n+1);students.put(key,List.copyOf(values));
            }
            if(students.isEmpty())throw invalid("未找到数据，请核对工作表和表头行。");
            return new Parsed(title,displayLabels,students,"custom",Set.of(),queryLabels);
        }catch(ResponseStatusException e){throw e;}
        catch(Exception e){throw invalid("无法读取 Excel，请检查文件格式及单元格内容。");}
    }
    private List<Integer> validateColumns(List<Integer> values,int count,String label){
        if(values==null||values.isEmpty()||values.size()>MAX_COLS)throw invalid("请至少勾选一个"+label+"，最多 256 列。");
        Set<Integer> seen=new HashSet<>();
        for(Integer value:values)if(value==null||value<0||value>=count||!seen.add(value))throw invalid(label+"包含不存在或重复的列，请重新选择。");
        return List.copyOf(values);
    }
    private String columnLabel(List<Column> headers,int index){
        Column col=headers.get(index);String label=col.label();
        if(label.isBlank())return col.letter()+"列（无表头）";
        if(label.length()>64)throw invalid(col.letter()+" 列表头超过 64 字，请缩短后上传。");
        if(headers.stream().filter(c->c.label().equals(label)).count()>1)return label+"（"+col.letter()+"列）";
        return label;
    }

    private List<Column> columns(Sheet sheet,int headerRow,DataFormatter format){
        Row header=sheet.getRow(headerRow-1);
        int count=header==null?0:header.getLastCellNum();
        for(int r=headerRow;r<=sheet.getLastRowNum();r++){
            Row row=sheet.getRow(r);if(row!=null)count=Math.max(count,row.getLastCellNum());
        }
        if(count<1||count>MAX_COLS)throw invalid("请选择包含表头的行，最多支持 256 列。");
        List<Column> columns=new ArrayList<>();
        for(int c=0;c<count;c++)columns.add(new Column(c,CellReference.convertNumToColString(c),text(header==null?null:header.getCell(c),format)));
        return columns;
    }
    private Template template(List<Column> columns,Sheet sheet,int headerRow,DataFormatter format){
        if(columns.size()>16&&Set.of("用户姓名","姓名").contains(columns.get(1).label())
                &&columns.get(14).label().equals("提交时间")&&Set.of("总得分","总成绩").contains(columns.get(16).label())){
            requiredColumn(columns,Set.of("用户姓名","姓名"),"用户姓名");
            requiredColumn(columns,Set.of("提交时间"),"提交时间");
            requiredColumn(columns,Set.of("总得分","总成绩"),"总得分");
            List<Column> details=new ArrayList<>();
            for(Column column:columns){
                if(column.index()<=16)continue;
                boolean hasData=false;
                for(int r=headerRow;r<=sheet.getLastRowNum();r++){
                    Row row=sheet.getRow(r);
                    if(row!=null&&!text(row.getCell(column.index()),format).isBlank()){hasData=true;break;}
                }
                if(hasData)details.add(column);
            }
            details.sort(Comparator.comparingInt((Column c)->{
                var match=QUESTION.matcher(c.label());
                return match.matches()?Integer.parseInt(match.group(1)):1000+c.index();
            }));
            List<Integer> scores=new ArrayList<>(List.of(16));
            List<String> labels=new ArrayList<>(List.of("总成绩"));
            for(Column column:details){
                scores.add(column.index());
                labels.add(column.label().isBlank()?column.letter()+"列成绩":column.label());
            }
            if(new HashSet<>(labels).size()!=labels.size())throw invalid("成绩列名称重复，请检查 Q 列后的表头。");
            return new Template(details.isEmpty()?"simple":"full",1,14,scores,labels,true);
        }
        boolean candidate=columns.stream().anyMatch(c->Set.of("提交时间","总得分","总成绩").contains(c.label())||QUESTION.matcher(c.label()).matches());
        if(!candidate)return null;
        // A lone total-score column in a custom legacy sheet is still supported.
        boolean hasSubmission=columns.stream().anyMatch(c->c.label().equals("提交时间"));
        boolean hasQuestion=columns.stream().anyMatch(c->QUESTION.matcher(c.label()).matches());
        if(!hasSubmission&&!hasQuestion)return null;
        int name=requiredColumn(columns,Set.of("用户姓名","姓名"),"用户姓名");
        int submission=requiredColumn(columns,Set.of("提交时间"),"提交时间");
        int total=requiredColumn(columns,Set.of("总得分","总成绩"),"总得分");
        TreeMap<Integer,Integer> questions=new TreeMap<>();
        for(Column column:columns){
            var match=QUESTION.matcher(column.label());
            if(match.matches()){
                int number=Integer.parseInt(match.group(1));
                if(questions.putIfAbsent(number,column.index())!=null)throw invalid("第 "+number+" 题得分列重复，请检查表头。");
            }
        }
        List<Integer> scores=new ArrayList<>(List.of(total));
        List<String> labels=new ArrayList<>(List.of("总成绩"));
        questions.forEach((number,index)->{scores.add(index);labels.add("第"+number+"题得分");});
        return new Template(questions.isEmpty()?"simple":"full",name,submission,scores,labels,false);
    }
    private int requiredColumn(List<Column> columns,Set<String> names,String label){
        var matches=columns.stream().filter(c->names.contains(c.label())).toList();
        if(matches.size()!=1)throw invalid("模板需要唯一的【"+label+"】列，请检查表头。");
        return matches.getFirst().index();
    }
    private Workbook open(MultipartFile file) throws Exception {
        if(file==null||file.isEmpty()||file.getSize()>10*1024*1024) throw invalid("请上传不超过 10 MB 的 Excel 文件。");
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if(!name.endsWith(".xlsx")&&!name.endsWith(".xls")) throw invalid("仅支持 .xlsx 和 .xls 文件。");
        try(InputStream in=file.getInputStream()){return WorkbookFactory.create(in);}
    }
    private Sheet sheet(Workbook book,int index,int header){
        if(book.getNumberOfSheets()>50) throw invalid("工作表过多，最多支持 50 个工作表。");
        if(index<0||index>=book.getNumberOfSheets()||header<1||header>100) throw invalid("工作表或表头行无效，表头行应在 1 至 100 之间。");
        Sheet sheet=book.getSheetAt(index);
        if(sheet.getLastRowNum()>=MAX_ROWS+header) throw invalid("每次最多上传 10000 行学员，请拆分后上传。");
        return sheet;
    }
    private DataFormatter formatter(){DataFormatter f=new DataFormatter(Locale.CHINA);f.setUseCachedValuesForFormulaCells(true);return f;}
    private String text(Cell cell,DataFormatter f){
        if(cell==null) return "";
        return f.formatCellValue(cell).strip();
    }
    private void checkCell(Cell c,int row){
        if(c!=null&&(c.getCellType()==CellType.ERROR||(c.getCellType()==CellType.FORMULA&&c.getCachedFormulaResultType()==CellType.ERROR)))
            throw invalid("第 "+(row+1)+" 行有 Excel 错误值，请修正后上传。");
    }
    private String displayScore(Cell cell,String value,boolean legacy){
        if(value.isBlank()) return "暂无成绩";
        if(!legacy)return value;
        try {if(new java.math.BigDecimal(value).compareTo(java.math.BigDecimal.ZERO)==0)return "未参考";} catch(NumberFormatException ignored){}
        if(cell!=null&&(cell.getCellType()==CellType.NUMERIC||(cell.getCellType()==CellType.FORMULA&&cell.getCachedFormulaResultType()==CellType.NUMERIC))&&cell.getNumericCellValue()==0) return "未参考";
        return value;
    }
    private String clean(String value,int max,String label){
        if(value==null||value.isBlank()||value.strip().length()>max) throw invalid(label+"不能为空且不能超过 "+max+" 个字符。");
        return value.strip();
    }
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
}
