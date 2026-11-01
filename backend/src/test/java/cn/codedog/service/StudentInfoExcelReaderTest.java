package cn.codedog.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class StudentInfoExcelReaderTest {
    private final StudentInfoExcelReader reader = new StudentInfoExcelReader();

    @Test void parsesIdAndNameColumnsAndIgnoresOtherColumns() throws Exception {
        var book = new XSSFWorkbook(); var sheet = book.createSheet();
        sheet.createRow(0).createCell(0).setCellValue("用户ID");
        sheet.getRow(0).createCell(1).setCellValue("姓名");
        sheet.getRow(0).createCell(2).setCellValue("手机号");
        sheet.createRow(1).createCell(0).setCellValue("1001"); sheet.getRow(1).createCell(1).setCellValue("张三");
        sheet.createRow(2).createCell(0).setCellValue("1002"); sheet.getRow(2).createCell(1).setCellValue("李四");
        var out = new ByteArrayOutputStream(); book.write(out); book.close();
        var parsed = reader.parse(new MockMultipartFile("file","students.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray()));
        assertThat(parsed.students()).extracting(StudentInfoExcelReader.StudentInfo::userId).containsExactly("1001","1002");
        assertThat(parsed.students()).extracting(StudentInfoExcelReader.StudentInfo::name).containsExactly("张三","李四");
    }

    @Test void rejectsMissingHeadersAndDuplicateIds() throws Exception {
        assertThatThrownBy(() -> reader.parse(new MockMultipartFile("file","bad.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",new byte[]{1})))
            .hasMessageContaining("无法读取 Excel");
        var book = new XSSFWorkbook(); var sheet = book.createSheet();
        sheet.createRow(0).createCell(0).setCellValue("用户ID"); sheet.getRow(0).createCell(1).setCellValue("姓名");
        sheet.createRow(1).createCell(0).setCellValue("1001"); sheet.getRow(1).createCell(1).setCellValue("张三");
        sheet.createRow(2).createCell(0).setCellValue("1001"); sheet.getRow(2).createCell(1).setCellValue("李四");
        var out = new ByteArrayOutputStream(); book.write(out); book.close();
        assertThatThrownBy(() -> reader.parse(new MockMultipartFile("file","students.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray())))
            .hasMessageContaining("用户ID【1001】重复");
    }
}
