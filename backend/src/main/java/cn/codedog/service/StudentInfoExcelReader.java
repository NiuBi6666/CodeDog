package cn.codedog.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.InputStream;
import java.util.*;

@Service
public class StudentInfoExcelReader {
    private static final int MAX_ROWS = 10000;
    public record StudentInfo(String userId, String name) {}
    public record Parsed(List<StudentInfo> students) {}
    public Parsed parse(MultipartFile file) {
        try (Workbook book = open(file)) {
            if (book.getNumberOfSheets() == 0) throw invalid("Excel 中没有工作表。");
            Sheet sheet = book.getSheetAt(0); DataFormatter formatter = new DataFormatter(Locale.CHINA);
            Row header = sheet.getRow(0);
            int idColumn = column(header, formatter, "用户ID", "用户 ID"), nameColumn = column(header, formatter, "姓名");
            if (idColumn == nameColumn) throw invalid("用户ID和姓名必须是不同的列。");
            LinkedHashMap<String, StudentInfo> students = new LinkedHashMap<>();
            for (int rowNumber = 1; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
                if (rowNumber > MAX_ROWS) throw invalid("每次最多上传 10000 名学员。");
                Row row = sheet.getRow(rowNumber); if (row == null) continue;
                String userId = text(row.getCell(idColumn), formatter), name = text(row.getCell(nameColumn), formatter);
                if (userId.isBlank() && name.isBlank()) continue;
                if (userId.isBlank() || name.isBlank()) throw invalid("第 " + (rowNumber + 1) + " 行的用户ID和姓名必须同时填写。");
                if (userId.length() > 100 || name.length() > 100) throw invalid("第 " + (rowNumber + 1) + " 行的用户ID或姓名过长。");
                if (students.putIfAbsent(userId, new StudentInfo(userId, name)) != null) throw invalid("用户ID【" + userId + "】重复，请处理后再上传。");
            }
            if (students.isEmpty()) throw invalid("未找到学员数据，请检查第 1 行表头和数据。");
            return new Parsed(List.copyOf(students.values()));
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw invalid("无法读取 Excel，请上传包含“用户ID”和“姓名”两列的未加密 .xlsx 或 .xls 文件。"); }
    }
    private int column(Row header, DataFormatter formatter, String... labels) {
        if (header == null) throw invalid("第 1 行必须是表头，并包含“用户ID”和“姓名”。");
        for (int i = 0; i < header.getLastCellNum(); i++) { String value = formatter.formatCellValue(header.getCell(i)).strip(); for (String label : labels) if (label.equals(value)) return i; }
        throw invalid("模板缺少【" + labels[0] + "】列。");
    }
    private Workbook open(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw invalid("请上传不超过 10 MB 的 Excel 文件。");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) throw invalid("仅支持 .xlsx 和 .xls 文件。");
        try (InputStream input = file.getInputStream()) { return WorkbookFactory.create(input); }
    }
    private String text(Cell cell, DataFormatter formatter) { return cell == null ? "" : formatter.formatCellValue(cell).strip(); }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}
