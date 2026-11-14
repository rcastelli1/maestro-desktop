package br.com.agenteslocal.git;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GitService {
    public record Change(String path, String status, boolean staged, boolean untracked) {}

    public boolean isRepository(Path folder) {
        return run(folder, "rev-parse", "--is-inside-work-tree").trim().equals("true");
    }

    public String branch(Path folder) {
        String branch = run(folder, "branch", "--show-current").trim();
        return branch.isBlank() ? "sem branch" : branch;
    }

    public List<Change> changes(Path folder) {
        List<Change> result = new ArrayList<>();
        String output = run(folder, "status", "--porcelain=v1", "-z", "--untracked-files=all");
        int cursor = 0;
        while (cursor < output.length()) {
            int end = output.indexOf('\0', cursor);
            if (end < 0) break;
            String entry = output.substring(cursor, end);
            cursor = end + 1;
            if (entry.length() < 4) continue;
            String code = entry.substring(0, 2);
            String path = entry.substring(3);
            if (code.indexOf('R') >= 0 || code.indexOf('C') >= 0) {
                int originalEnd = output.indexOf('\0', cursor);
                if (originalEnd < 0) break;
                cursor = originalEnd + 1;
            }
            boolean untracked = code.equals("??");
            result.add(new Change(path, code, !untracked && code.charAt(0) != ' ', untracked));
        }
        return result;
    }

    public String diff(Path folder, Change change) {
        if (change.untracked()) {
            Path file = folder.resolve(change.path()).normalize();
            if (!file.startsWith(folder.normalize()) || !Files.isRegularFile(file)) return "Arquivo indisponível.";
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                return text.length() > 120_000 ? text.substring(0, 120_000) + "\n… arquivo truncado" : text;
            } catch (IOException e) {
                return "Não foi possível abrir o arquivo: " + e.getMessage();
            }
        }
        String unstaged = run(folder, "diff", "--", change.path());
        String staged = run(folder, "diff", "--cached", "--", change.path());
        String text = (staged.isBlank() ? "" : "ALTERAÇÕES PREPARADAS\n" + staged + "\n")
                + (unstaged.isBlank() ? "" : "ALTERAÇÕES NÃO PREPARADAS\n" + unstaged);
        return text.isBlank() ? "Sem diferença textual para exibir." : text;
    }

    public String stage(Path folder, Change change) {
        return run(folder, "add", "--", change.path());
    }

    public String unstage(Path folder, Change change) {
        return run(folder, "restore", "--staged", "--", change.path());
    }

    public String discard(Path folder, Change change) {
        if (change.untracked()) return "Arquivos novos devem ser apagados manualmente.";
        return run(folder, "restore", "--source=HEAD", "--staged", "--worktree", "--", change.path());
    }

    public String commit(Path folder, String message) {
        return run(folder, "commit", "-m", message);
    }

    private String run(Path folder, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(folder.toString());
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (var input = process.getInputStream()) {
                    byte[] buffer = new byte[8_192];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (collected.size() < 200_000) {
                            collected.write(buffer, 0, Math.min(count, 200_000 - collected.size()));
                        }
                    }
                } catch (IOException ignored) { }
            }, "git-output");
            reader.setDaemon(true);
            reader.start();
            boolean completed = process.waitFor(20, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "O comando Git excedeu o tempo limite.";
            }
            reader.join(1_000);
            String output = collected.toString(StandardCharsets.UTF_8);
            return output.length() > 180_000 ? output.substring(0, 180_000) + "\n… resultado truncado" : output;
        } catch (IOException e) {
            return "Git indisponível: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Comando Git interrompido.";
        }
    }
}
