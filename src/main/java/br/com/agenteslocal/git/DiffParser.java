package br.com.agenteslocal.git;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns Git's unified patch into numbered rows for the file viewer. */
public final class DiffParser {
    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@(.*)$");
    private static final int MAX_LINES = 1_000;

    public record Line(int oldNumber, int newNumber, char kind, String text) { }
    public record SideLine(Line left, Line right) { }
    public record Result(List<Line> lines, int added, int removed, int hunks, boolean truncated) { }

    public Result parse(String source) {
        List<Line> lines = new ArrayList<>();
        int oldNumber = 0;
        int newNumber = 0;
        int added = 0;
        int removed = 0;
        int hunks = 0;
        boolean inHunk = false;
        boolean truncated = false;
        for (String raw : source.split("\\R", -1)) {
            if (lines.size() >= MAX_LINES) { truncated = true; break; }
            Matcher hunk = HUNK.matcher(raw);
            if (hunk.matches()) {
                oldNumber = Integer.parseInt(hunk.group(1));
                newNumber = Integer.parseInt(hunk.group(2));
                inHunk = true;
                hunks++;
                lines.add(new Line(0, 0, 'h', raw));
            } else if (raw.equals("ALTERAÇÕES PREPARADAS") || raw.equals("ALTERAÇÕES NÃO PREPARADAS")) {
                inHunk = false;
                lines.add(new Line(0, 0, 's', raw));
            } else if (raw.startsWith("diff --git ") || raw.startsWith("index ") || raw.startsWith("--- ")
                    || raw.startsWith("+++ ") || raw.startsWith("new file mode ")
                    || raw.startsWith("deleted file mode ") || raw.startsWith("rename ")
                    || raw.startsWith("similarity index ") || raw.startsWith("\\ No newline at end of file")) {
                // Git metadata is represented by the file toolbar instead.
            } else if (inHunk && raw.startsWith("+")) {
                lines.add(new Line(0, newNumber++, '+', raw.substring(1)));
                added++;
            } else if (inHunk && raw.startsWith("-")) {
                lines.add(new Line(oldNumber++, 0, '-', raw.substring(1)));
                removed++;
            } else if (inHunk && raw.startsWith(" ")) {
                lines.add(new Line(oldNumber++, newNumber++, ' ', raw.substring(1)));
            } else if (!inHunk && !raw.isEmpty()) {
                lines.add(new Line(0, ++newNumber, ' ', raw));
            } else if (!inHunk && raw.isEmpty() && newNumber > 0) {
                lines.add(new Line(0, ++newNumber, ' ', ""));
            }
        }
        if (lines.isEmpty()) lines.add(new Line(0, 0, 'm', "Nenhuma diferença textual para exibir."));
        return new Result(List.copyOf(lines), added, removed, hunks, truncated);
    }

    public Result source(String content) {
        List<Line> lines = new ArrayList<>();
        int number = 1;
        boolean truncated = false;
        for (String raw : content.split("\\R", -1)) {
            if (lines.size() >= MAX_LINES) { truncated = true; break; }
            lines.add(new Line(0, number++, ' ', raw));
        }
        return new Result(List.copyOf(lines), 0, 0, 0, truncated);
    }

    public List<SideLine> sideBySide(List<Line> lines) {
        List<SideLine> result = new ArrayList<>();
        List<Line> removed = new ArrayList<>();
        List<Line> added = new ArrayList<>();
        for (Line line : lines) {
            if (line.kind() == '-') removed.add(line);
            else if (line.kind() == '+') added.add(line);
            else {
                flush(result, removed, added);
                result.add(new SideLine(line, line));
            }
        }
        flush(result, removed, added);
        return result;
    }

    private void flush(List<SideLine> result, List<Line> removed, List<Line> added) {
        int count = Math.max(removed.size(), added.size());
        for (int index = 0; index < count; index++) {
            result.add(new SideLine(index < removed.size() ? removed.get(index) : null,
                    index < added.size() ? added.get(index) : null));
        }
        removed.clear();
        added.clear();
    }
}
