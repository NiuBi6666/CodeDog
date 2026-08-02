package cn.codedog.service;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ClassProgressImportService {
    private static final int MAX_FILES = 20;
    private static final int MAX_ROWS_PER_FILE = 10_000;
    private static final int MAX_TOTAL_ROWS = 50_000;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private static final List<ColumnSpec> COLUMN_SPECS = List.of(
        column(0, "A", "用户ID", "学员ID", "学员信息"),
        column(1, "B", "姓名", "学员姓名", "学员信息"),
        column(11, "L", "班级名称", "班级名称", "学员信息"),
        column(12, "M", "课程名称", "课程名称", "学员信息"),
        column(15, "P", "是否完课", "是否完课", "课堂情况"),
        column(16, "Q", "有效到课时长", "有效到课时长", "课堂情况"),
        column(17, "R", "调课状态", "调课状态", "课堂情况"),
        column(20, "U", "课中OJ题总数", "OJ题总数", "课中题目"),
        column(21, "V", "课中OJ题提交数", "OJ题提交数", "课中题目"),
        column(22, "W", "课中OJ题通过数", "OJ题通过数", "课中题目"),
        column(23, "X", "课中客观题总数", "客观题总数", "课中题目"),
        column(24, "Y", "课中客观题提交数", "客观题提交数", "课中题目"),
        column(25, "Z", "课中客观题通过数", "客观题通过数", "课中题目"),
        column(28, "AC", "课后作业OJ题总数", "OJ题总数", "课后作业"),
        column(29, "AD", "课后作业OJ题提交数", "OJ题提交数", "课后作业"),
        column(30, "AE", "课后作业OJ题通过数", "OJ题通过数", "课后作业"),
        column(31, "AF", "课后作业客观题总数", "客观题总数", "课后作业"),
        column(32, "AG", "课后作业客观题提交数", "客观题提交数", "课后作业"),
        column(33, "AH", "课后作业客观题通过数", "客观题通过数", "课后作业"),
        column(36, "AK", "课后拓展OJ题总数", "OJ题总数", "课后拓展"),
        column(37, "AL", "课后拓展OJ题提交数", "OJ题提交数", "课后拓展"),
        column(38, "AM", "课后拓展OJ题通过数", "OJ题通过数", "课后拓展"),
        column(39, "AN", "课后拓展客观题总数", "客观题总数", "课后拓展"),
        column(40, "AO", "课后拓展客观题提交数", "客观题提交数", "课后拓展"),
        column(41, "AP", "课后拓展客观题通过数", "客观题通过数", "课后拓展"),
        column(42, "AQ", "是否观看", "是否观看", "课程回放"),
        column(43, "AR", "累计观看时长", "累计观看时长", "课程回放"),
        column(44, "AS", "观看最晚结束时间", "最晚结束时间", "课程回放")
    );

    public ImportResult parse(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw new ValidationException("请选择至少一个 Excel 文件");
        if (files.size() > MAX_FILES) throw new ValidationException("一次最多上传 " + MAX_FILES + " 个班级文件");

        List<ClassData> classes = new ArrayList<>();
        Set<String> classNames = new LinkedHashSet<>();
        int totalRows = 0;
        for (MultipartFile file : files) {
            ClassData classData = parseFile(file);
            if (!classNames.add(classData.className()))
                throw new ValidationException("班级“" + classData.className() + "”重复上传");
            totalRows += classData.rowCount();
            if (totalRows > MAX_TOTAL_ROWS)
                throw new ValidationException("全部文件合计不能超过 " + MAX_TOTAL_ROWS + " 行");
            classes.add(classData);
        }
        List<ColumnDefinition> columns = COLUMN_SPECS.stream()
            .map(spec -> new ColumnDefinition(spec.key(), spec.label(), spec.group()))
            .toList();
        return new ImportResult(columns, List.copyOf(classes), classes.size(), totalRows);
    }

    private ClassData parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ValidationException("上传文件不能为空");
        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new ValidationException(fileName + "：仅支持 .xlsx 文件");
        if (file.getSize() > MAX_FILE_BYTES)
            throw new ValidationException(fileName + "：文件不能超过 10MB");

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (!(workbook instanceof XSSFWorkbook))
                throw new ValidationException(fileName + "：仅支持标准 .xlsx 文件");
            return parseWorkbook(workbook, fileName);
        } catch (ValidationException error) {
            throw error;
        } catch (EncryptedDocumentException error) {
            throw new ValidationException(fileName + "：不支持加密的 Excel 文件");
        } catch (IOException | RuntimeException error) {
            throw new ValidationException(fileName + "：文件损坏或不是有效的 Excel 文件");
        }
    }

    private ClassData parseWorkbook(Workbook workbook, String fileName) {
        if (workbook.getNumberOfSheets() == 0) throw new ValidationException(fileName + "：没有工作表");
        Sheet sheet = workbook.getSheetAt(0);
        Row header = sheet.getRow(0);
        if (header == null) throw new ValidationException(fileName + "：缺少首行表头");
        DataFormatter formatter = new DataFormatter(Locale.CHINA);

        for (ColumnSpec spec : COLUMN_SPECS) {
            String actual = cellText(header.getCell(spec.index()), formatter, fileName, 1, spec.key()).trim();
            if (!actual.equals(spec.expectedHeader()))
                throw new ValidationException(fileName + "：" + spec.key() + " 列表头应为“"
                    + spec.expectedHeader() + "”，实际为“" + (actual.isEmpty() ? "空" : actual) + "”");
        }

        List<RowData> rows = new ArrayList<>();
        Set<String> courseNames = new LinkedHashSet<>();
        String className = null;
        int lastRow = sheet.getLastRowNum();
        for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            boolean hasValue = false;
            for (ColumnSpec spec : COLUMN_SPECS) {
                String value = cellText(row.getCell(spec.index()), formatter, fileName, rowIndex + 1, spec.key()).trim();
                values.put(spec.key(), value);
                hasValue |= !value.isEmpty();
            }
            if (!hasValue) continue;
            require(values, "A", fileName, rowIndex + 1, "学员ID");
            require(values, "B", fileName, rowIndex + 1, "学员姓名");
            require(values, "L", fileName, rowIndex + 1, "班级名称");
            require(values, "M", fileName, rowIndex + 1, "课程名称");

            String rowClassName = values.get("L");
            if (className == null) className = rowClassName;
            else if (!className.equals(rowClassName))
                throw new ValidationException(fileName + "：同一文件包含多个班级（第 " + (rowIndex + 1) + " 行）");
            courseNames.add(values.get("M"));
            rows.add(new RowData(Collections.unmodifiableMap(values)));
            if (rows.size() > MAX_ROWS_PER_FILE)
                throw new ValidationException(fileName + "：单个文件不能超过 " + MAX_ROWS_PER_FILE + " 行数据");
        }
        if (rows.isEmpty()) throw new ValidationException(fileName + "：没有可导入的学员数据");
        return new ClassData(fileName, className, List.copyOf(courseNames), rows.size(), List.copyOf(rows));
    }

    private String cellText(Cell cell, DataFormatter formatter, String fileName, int row, String column) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA)
            throw new ValidationException(fileName + "：" + column + row + " 不支持公式");
        return formatter.formatCellValue(cell);
    }

    private void require(Map<String, String> values, String key, String fileName, int row, String label) {
        if (values.getOrDefault(key, "").isBlank())
            throw new ValidationException(fileName + "：第 " + row + " 行缺少" + label);
    }

    private String safeFileName(String original) {
        String value = original == null ? "未命名文件.xlsx" : original.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isEmpty()) value = "未命名文件.xlsx";
        return value.length() > 120 ? value.substring(value.length() - 120) : value;
    }

    private static ColumnSpec column(int index, String key, String expectedHeader, String label, String group) {
        return new ColumnSpec(index, key, expectedHeader, label, group);
    }

    private record ColumnSpec(int index, String key, String expectedHeader, String label, String group) {}
    public record ColumnDefinition(String key, String label, String group) {}
    public record RowData(Map<String, String> values) {}
    public record ClassData(String fileName, String className, List<String> courseNames,
                            int rowCount, List<RowData> rows) {}
    public record ImportResult(List<ColumnDefinition> columns, List<ClassData> classes,
                               int fileCount, int rowCount) {}

    public static final class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }
}
