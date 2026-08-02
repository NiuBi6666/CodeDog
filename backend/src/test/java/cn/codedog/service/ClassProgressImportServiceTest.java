package cn.codedog.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassProgressImportServiceTest {
    private final ClassProgressImportService service = new ClassProgressImportService();

    @Test
    void parsesMultipleClassFilesAndSelectedColumns() throws Exception {
        var result = service.parse(List.of(
            workbook("one.xlsx", "一班", null, false),
            workbook("two.xlsx", "二班", null, false)));

        assertThat(result.fileCount()).isEqualTo(2);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.columns()).hasSize(28);
        assertThat(result.columns()).extracting(ClassProgressImportService.ColumnDefinition::key)
            .containsExactly("A", "B", "L", "M", "P", "Q", "R", "U", "V", "W", "X", "Y", "Z",
                "AC", "AD", "AE", "AF", "AG", "AH", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS");
        assertThat(result.classes()).extracting(ClassProgressImportService.ClassData::className)
            .containsExactly("一班", "二班");
        assertThat(result.classes().getFirst().rows().getFirst().values())
            .containsEntry("A", "1961457066")
            .containsEntry("B", "测试学员")
            .containsEntry("U", "5")
            .containsEntry("AS", "2026-07-20 12:00:00");
    }

    @Test
    void rejectsMixedClassesInOneFile() throws Exception {
        assertThatThrownBy(() -> service.parse(List.of(workbook("mixed.xlsx", "一班", "二班", false))))
            .isInstanceOf(ClassProgressImportService.ValidationException.class)
            .hasMessageContaining("同一文件包含多个班级");
    }

    @Test
    void rejectsUnexpectedSelectedHeader() throws Exception {
        assertThatThrownBy(() -> service.parse(List.of(workbook("wrong.xlsx", "一班", null, true))))
            .isInstanceOf(ClassProgressImportService.ValidationException.class)
            .hasMessageContaining("U 列表头应为");
    }

    private MockMultipartFile workbook(String fileName, String className,
                                       String secondClass, boolean wrongHeader) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("班级数据导出");
            Row header = sheet.createRow(0);
            String[] headers = new String[45];
            for (int index = 0; index < headers.length; index++) headers[index] = "未使用" + index;
            header(headers, 0, "用户ID");
            header(headers, 1, "姓名");
            header(headers, 11, "班级名称");
            header(headers, 12, "课程名称");
            header(headers, 15, "是否完课");
            header(headers, 16, "有效到课时长");
            header(headers, 17, "调课状态");
            header(headers, 20, wrongHeader ? "错误表头" : "课中OJ题总数");
            header(headers, 21, "课中OJ题提交数");
            header(headers, 22, "课中OJ题通过数");
            header(headers, 23, "课中客观题总数");
            header(headers, 24, "课中客观题提交数");
            header(headers, 25, "课中客观题通过数");
            header(headers, 28, "课后作业OJ题总数");
            header(headers, 29, "课后作业OJ题提交数");
            header(headers, 30, "课后作业OJ题通过数");
            header(headers, 31, "课后作业客观题总数");
            header(headers, 32, "课后作业客观题提交数");
            header(headers, 33, "课后作业客观题通过数");
            header(headers, 36, "课后拓展OJ题总数");
            header(headers, 37, "课后拓展OJ题提交数");
            header(headers, 38, "课后拓展OJ题通过数");
            header(headers, 39, "课后拓展客观题总数");
            header(headers, 40, "课后拓展客观题提交数");
            header(headers, 41, "课后拓展客观题通过数");
            header(headers, 42, "是否观看");
            header(headers, 43, "累计观看时长");
            header(headers, 44, "观看最晚结束时间");
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);

            createDataRow(sheet.createRow(1), className, "1961457066");
            if (secondClass != null) createDataRow(sheet.createRow(2), secondClass, "1961457067");
            workbook.write(output);
            return new MockMultipartFile("files", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private void createDataRow(Row row, String className, String studentId) {
        row.createCell(0).setCellValue(studentId);
        row.createCell(1).setCellValue("测试学员");
        row.createCell(11).setCellValue(className);
        row.createCell(12).setCellValue("P4-二分算法");
        row.createCell(15).setCellValue("是");
        row.createCell(16).setCellValue("120分");
        row.createCell(17).setCellValue("正常");
        for (int index : new int[]{20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33,
            36, 37, 38, 39, 40, 41}) row.createCell(index).setCellValue(index == 20 ? 5 : 0);
        row.createCell(42).setCellValue("是");
        row.createCell(43).setCellValue("30分");
        row.createCell(44).setCellValue("2026-07-20 12:00:00");
    }

    private void header(String[] headers, int index, String value) {
        headers[index] = value;
    }
}
