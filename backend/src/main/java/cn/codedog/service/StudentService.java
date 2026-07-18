package cn.codedog.service;

import cn.codedog.model.Student;
import cn.codedog.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StudentService {
    public static final int MAX_QUERIES = 500;
    private final StudentRepository repository;

    public StudentService(StudentRepository repository) { this.repository = repository; }

    public QueryResult query(String mode, List<?> rawValues) {
        if (!Set.of("name", "id").contains(mode) || rawValues == null)
            throw new DocumentService.ValidationException("查询参数无效");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (Object raw : rawValues) {
            String value = String.valueOf(raw == null ? "" : raw).replaceAll("\\s+", "").trim();
            if (!value.isEmpty()) unique.add(value);
        }
        if (unique.isEmpty()) throw new DocumentService.ValidationException("请至少提供一个查询值");
        if (unique.size() > MAX_QUERIES) throw new DocumentService.ValidationException("单次最多查询 500 条记录");
        List<String> values = List.copyOf(unique);
        List<QueryItem> results = new ArrayList<>();
        int found = 0;
        int ambiguous = 0;
        if (mode.equals("id")) {
            Map<String, Student> byId = new HashMap<>();
            repository.findByUserIdIn(values).forEach(student -> byId.put(student.getUserId(), student));
            for (String value : values) {
                Student student = byId.get(value);
                if (student != null) found++;
                results.add(new QueryItem(null, value, student, null));
            }
        } else {
            Map<String, List<Student>> byName = new HashMap<>();
            repository.findByNameInOrderByNameAscGradeAscClassNameAscUserIdAsc(values)
                .forEach(student -> byName.computeIfAbsent(student.getName(), ignored -> new ArrayList<>()).add(student));
            for (String value : values) {
                List<Student> matches = byName.getOrDefault(value, List.of());
                if (!matches.isEmpty()) found++;
                if (matches.size() > 1) ambiguous++;
                results.add(new QueryItem(value, null, null, matches));
            }
        }
        return new QueryResult(mode, results, new Summary(results.size(), found, results.size() - found, ambiguous));
    }

    public record QueryItem(String inputName, String inputId, Student student, List<Student> matches) {}
    public record Summary(int total, int found, int missing, int ambiguous) {}
    public record QueryResult(String mode, List<QueryItem> results, Summary summary) {}
}
