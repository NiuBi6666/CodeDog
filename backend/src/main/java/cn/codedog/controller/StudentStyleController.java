package cn.codedog.controller;
import cn.codedog.service.StudentStyleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/public/rankings/student-style")
public class StudentStyleController {
  private final StudentStyleService styles;
  public StudentStyleController(StudentStyleService styles) {this.styles=styles;}
  @GetMapping public StudentStyleService.State read(HttpServletRequest request) {return styles.read(request);}
  @PutMapping public StudentStyleService.State save(@RequestBody StudentStyleService.Selection selection,HttpServletRequest request) {return styles.save(selection,request);}
}
