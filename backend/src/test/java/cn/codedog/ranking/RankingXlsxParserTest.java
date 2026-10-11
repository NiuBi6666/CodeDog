package cn.codedog.ranking;

import cn.codedog.model.RankingPayload;
import cn.codedog.service.RankingXlsxParser;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class RankingXlsxParserTest {
  @Test
  void parsesCurrentExportColumnsAndCombinesCodeWithObjectiveTasks() throws Exception {
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("班级数据导出");
      Row header = sheet.createRow(0);
      header(header, 0, "用户ID"); header(header, 1, "姓名");
      header(header, 11, "班级名称"); header(header, 12, "课程名称"); header(header, 15, "是否完课");
      header(header, 20, "课中OJ题总数"); header(header, 22, "课中OJ题通过数");
      header(header, 23, "课中客观题总数"); header(header, 25, "课中客观题通过数");
      header(header, 28, "课中应用题通过数");
      header(header, 31, "课后作业OJ题总数"); header(header, 33, "课后作业OJ题通过数");
      header(header, 34, "课后作业客观题总数"); header(header, 36, "课后作业客观题通过数");
      Row row = sheet.createRow(1);
      text(row, 0, "6105387"); text(row, 1, "张睿宸"); text(row, 11, "2503-测试班");
      text(row, 12, "P71-递归算法（二）"); text(row, 15, "是");
      number(row, 20, 4); number(row, 22, 4); number(row, 23, 2); number(row, 25, 2);
      number(row, 31, 2); number(row, 33, 2); number(row, 34, 1); number(row, 36, 1);
      workbook.write(output);

      var file = new MockMultipartFile("files", "班级数据导出.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
      RankingPayload payload = new RankingXlsxParser().parse("spreadsheet-points", "表格积分", List.of(file));
      var student = payload.classes().getFirst().lessons().getFirst().students().getFirst();

      assertThat(student.studentId()).isEqualTo("6105387");
      assertThat(student.studentName()).isEqualTo("张睿宸");
      assertThat(student.completionRate()).isEqualTo(100);
      assertThat(student.inclass()).isEqualTo(new RankingPayload.Counts(6, 6, 6));
      assertThat(student.homework()).isEqualTo(new RankingPayload.Counts(3, 3, 3));
      assertThat(payload.classes().getFirst().className()).isEqualTo("2503-测试班");
      assertThat(payload.classes().getFirst().lessons().getFirst().lessonName()).isEqualTo("P71-递归算法（二）");
    }
  }

  private void header(Row row, int column, String value) { text(row, column, value); }
  private void text(Row row, int column, String value) { row.createCell(column).setCellValue(value); }
  private void number(Row row, int column, int value) { row.createCell(column).setCellValue(value); }
}
