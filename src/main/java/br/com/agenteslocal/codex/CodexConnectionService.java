package br.com.agenteslocal.codex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CodexConnectionService {
    public record Status(String version, boolean connected, String method, String detail) { }

    public Status status(String executable) {
        if (executable == null) return new Status("", false, "", "Codex CLI não encontrado.");
        try {
            String versionOutput = run(executable, 10, "--version").output().trim();
            String[] lines = versionOutput.split("\\R");
            String version = lines.length == 0 ? "" : lines[lines.length - 1];
            Result auth = run(executable, 10, "login", "status");
            String detail = auth.output().trim();
            String lower = detail.toLowerCase();
            String method = lower.contains("chatgpt") ? "ChatGPT" : lower.contains("api key") ? "Chave de API" : "Outra autenticação";
            return new Status(version, auth.exitCode() == 0, method, detail);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Status("", false, "", e.getMessage());
        }
    }

    public String login(String executable) throws IOException, InterruptedException {
        if (executable == null) throw new IOException("Codex CLI não encontrado.");
        Result result = run(executable, 180, "login");
        if (result.exitCode() != 0) throw new IOException(result.output().trim());
        return result.output().trim();
    }

    private Result run(String executable, int timeoutSeconds, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (var input = process.getInputStream()) {
                byte[] bytes = new byte[4096];
                int count;
                while ((count = input.read(bytes)) != -1) {
                    if (output.size() < 40_000) output.write(bytes, 0, Math.min(count, 40_000 - output.size()));
                }
            } catch (IOException ignored) { }
        }, "codex-connection-output");
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("O Codex não respondeu dentro do tempo esperado.");
        }
        reader.join(1_000);
        return new Result(process.exitValue(), output.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String output) { }
}
