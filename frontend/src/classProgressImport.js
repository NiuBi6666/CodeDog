export function assignmentCompletion(row, totalKey, passedKey) {
  const count = (key) => {
    const number = Number(String(row?.values?.[key] ?? "").trim().replaceAll(",", ""));
    return Number.isFinite(number) ? number : 0;
  };
  return count(passedKey) < count(totalKey) ? "incomplete" : "complete";
}

export function filterImportedRows(rows, keyword, filters = {}) {
  const value = String(keyword || "").trim().toLowerCase();
  const { inClassCompletion = "", afterClassCompletion = "", ...columnFilters } = filters || {};
  const activeFilters = Object.entries(columnFilters).filter(([, expected]) => String(expected || "").trim());
  return (rows || []).filter((row) => {
    const matchesKeyword = !value || ["A", "B", "L", "M"].some((key) =>
      String(row.values?.[key] || "").toLowerCase().includes(value));
    const matchesColumns = activeFilters.every(([key, expected]) =>
      String(row.values?.[key] || "").trim() === String(expected).trim());
    const matchesInClass = !inClassCompletion ||
      assignmentCompletion(row, "U", "W") === inClassCompletion;
    const matchesAfterClass = !afterClassCompletion ||
      assignmentCompletion(row, "AC", "AE") === afterClassCompletion;
    return matchesKeyword && matchesColumns && matchesInClass && matchesAfterClass;
  });
}

export function uniqueColumnValues(rows, key) {
  return [...new Set((rows || [])
    .map((row) => String(row.values?.[key] || "").trim())
    .filter(Boolean))];
}

export function buildImportMatrix(columns, rows) {
  const header = (columns || []).map((column) => `${column.key} ${column.label}`);
  const values = (rows || []).map((row) =>
    (columns || []).map((column) => String(row.values?.[column.key] || "")));
  return [header, ...values].map((row) => row.join("\t")).join("\n");
}

export function matrixToCsv(matrix) {
  return "\uFEFF" + String(matrix || "").split("\n").map((line) =>
    line.split("\t").map((cell) => `"${cell.replaceAll('"', '""')}"`).join(",")).join("\r\n");
}
