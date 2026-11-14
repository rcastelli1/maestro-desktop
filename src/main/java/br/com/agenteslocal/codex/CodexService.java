package br.com.agenteslocal.codex;

import br.com.agenteslocal.model.WorkspaceData.Agent;
import br.com.agenteslocal.model.WorkspaceData.Project;
import br.com.agenteslocal.model.WorkspaceData.Task;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CodexService {
    private final Gson gson = new Gson();
    private final CodexModelCatalog modelCatalog = new CodexModelCatalog();
    private final Map<String, Process> running = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private String configuredExecutable = "";

    public record Event(String kind, String text, String sessionId) {}

    public boolean available() {
        return executable() != null;
    }

    public List<String> availableModels() {
        return modelCatalog.availableModels();
    }

    public String defaultModel() {
        return modelCatalog.defaultModel();
    }

    public void setConfiguredExecutable(String executable) {
        configuredExecutable = executable == null ? "" : executable.trim();
    }

    public String executable() {
        if (isExecutableFile(configuredExecutable)) return configuredExecutable;
        String configured = System.getenv("AGENTES_CODEX_PATH");
        if (isExecutableFile(configured)) return configured;
        String vscode = vscodeExecutable();
        if (vscode != null) return vscode;
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "codex.cmd" : "codex";
        for (String directory : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) continue;
            Path candidate = Path.of(directory, name);
            if (Files.isRegularFile(candidate)) return candidate.toString();
        }
        if (name.equals("codex.cmd")) {
            String nvm = System.getenv("NVM_SYMLINK");
            if (nvm != null && isExecutableFile(Path.of(nvm, name).toString()))
                return Path.of(nvm, name).toString();
            String appData = System.getenv("APPDATA");
            if (appData != null && isExecutableFile(Path.of(appData, "npm", name).toString()))
                return Path.of(appData, "npm", name).toString();
            String nvmDefault = Path.of("C:\\nvm4w", "nodejs", name).toString();
            if (isExecutableFile(nvmDefault)) return nvmDefault;
        }
        return null;
    }

    private String vscodeExecutable() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        String override = System.getenv("VSCODE_EXTENSIONS");
        Path extensions = override == null || override.isBlank()
                ? Path.of(System.getProperty("user.home"), ".vscode", "extensions") : Path.of(override);
        if (!Files.isDirectory(extensions)) return null;
        try (var installed = Files.list(extensions)) {
            return installed.filter(path -> path.getFileName().toString().startsWith("openai.chatgpt-"))
                    .sorted((left, right) -> right.getFileName().toString()
                            .compareToIgnoreCase(left.getFileName().toString()))
                    .map(path -> path.resolve("bin").resolve("windows-x86_64").resolve("codex.exe"))
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .findFirst().orElse(null);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private boolean isExecutableFile(String path) {
        if (path == null || path.isBlank()) return false;
        try { return Files.isRegularFile(Path.of(path)); }
        catch (RuntimeException ignored) { return false; }
    }

    public void cancel(String taskId) {
        cancelled.add(taskId);
        Process process = running.get(taskId);
        if (process != null) stopProcessTree(process);
    }

    public void clearCancellation(String taskId) {
        cancelled.remove(taskId);
    }

    private void stopProcessTree(Process process) {
        try {
            process.descendants().forEach(child -> {
                try { child.destroyForcibly(); }
                catch (RuntimeException ignored) { }
            });
        } catch (RuntimeException ignored) { }
        process.destroyForcibly();
    }

    public void run(Project project, Agent agent, Task task, String knowledge, String sharedContext,
                    List<String> attachments, String chatSessionId,
                    Consumer<Event> callback) {
        if (cancelled.remove(task.id)) return;
        String executable = executable();
        if (executable == null) {
            callback.accept(new Event("error", "Codex CLI não encontrado. Configure o caminho em AGENTES_CODEX_PATH.", ""));
            return;
        }
        if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder))) {
            callback.accept(new Event("error", "Selecione uma pasta válida para o projeto antes de enviar tarefas.", ""));
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("exec");
        boolean resume = chatSessionId != null && !chatSessionId.isBlank();
        boolean writable = agent.canEditFiles && !"bloquear".equals(project.writePolicy);
        if (resume) command.add("resume");
        command.add("--json");
        command.add("--skip-git-repo-check");
        if (!resume) {
            command.add("--sandbox");
            command.add(writable ? "workspace-write" : "read-only");
            command.add("-C");
            command.add(project.folder);
        }
        String requestedModel = agent.model == null || agent.model.isBlank()
                || agent.model.equals("Padrão do Codex") ? modelCatalog.defaultModel() : agent.model;
        if (!requestedModel.isBlank()) {
            command.add("--model");
            command.add(requestedModel);
        }
        if (agent.reasoningEffort != null && !agent.reasoningEffort.equals("Padrão")) {
            command.add("-c");
            command.add("model_reasoning_effort=" + gson.toJson(agent.reasoningEffort));
        }
        for (var mcp : project.mcps) {
            if (!mcp.enabled || !mcp.name.matches("[A-Za-z0-9_-]+")) continue;
            if (mcp.url != null && !mcp.url.isBlank()) {
                command.add("-c");
                command.add("mcp_servers." + mcp.name + ".url=" + gson.toJson(mcp.url));
            } else if (!mcp.command.isBlank()) {
                command.add("-c");
                command.add("mcp_servers." + mcp.name + ".command=" + gson.toJson(mcp.command));
                command.add("-c");
                command.add("mcp_servers." + mcp.name + ".args=" + gson.toJson(
                        mcp.arguments.isBlank() ? List.of() : List.of(mcp.arguments.split("\\R"))));
            }
        }
        for (String attachment : attachments) {
            String lower = attachment.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
                command.add("--image");
                command.add(attachment);
            }
        }
        if (resume) command.add(chatSessionId);
        command.add("-");

        boolean privateChat = project.chatFor(task).isPrivate();
        String prompt = "Você está atuando como o agente '" + agent.name + "' no projeto '" + project.name + "'.\n"
                + "Sua função: " + agent.role + "\n"
                + "Instruções do agente: " + agent.instructions + "\n"
                + (writable ? "Você pode editar arquivos somente dentro da pasta do projeto.\n"
                        : "Trabalhe em modo de leitura. Não altere arquivos.\n")
                + (sharedContext.isBlank() ? "" : "\n" + sharedContext + "\n")
                + (knowledge.isBlank() ? "" : "\nTrechos relevantes da base de conhecimento:\n" + knowledge)
                + "\nSe uma decisão do usuário for indispensável para continuar, termine sua resposta com "
                + "<maestro-question>{\"question\":\"pergunta objetiva\",\"options\":[{\"title\":\"opção 1\",\"description\":\"consequência\"},{\"title\":\"opção 2\",\"description\":\"consequência\"}],\"recommended\":0}</maestro-question>. "
                + "Use esse formato apenas quando precisar esperar uma escolha; não inclua o bloco em respostas normais.\n"
                + "Ao iniciar uma etapa importante, envie uma atualização breve ao usuário sobre a ação observável "
                + "e, quando houver, o resultado. Não exponha raciocínio interno.\n"
                + (privateChat
                    ? "Esta é uma conversa individual. Responda somente ao usuário nesta conversa. "
                        + "Não encaminhe tarefas nem chame outros agentes.\n"
                    : "Se quiser passar trabalho para outros agentes deste projeto, mencione @Nome na resposta final "
                        + "na ordem em que devem atuar. O Maestro iniciará o primeiro e agendará os demais em sequência, "
                        + "após a conclusão de cada etapa. Use @Nome somente para agentes que devem executar uma tarefa; "
                        + "não use @Nome apenas como referência.\n")
                + "\nSolicitação do usuário:\n" + task.prompt;

        Process process = null;
        StringBuilder stderr = new StringBuilder();
        CodexProgress progress = new CodexProgress();
        String sessionId = "";
        boolean turnFailed = false;
        boolean turnCompleted = false;
        String failureDetail = "";
        String lastError = "";
        try {
            process = new ProcessBuilder(command).directory(Path.of(project.folder).toFile()).start();
            running.put(task.id, process);
            if (cancelled.contains(task.id)) stopProcessTree(process);
            Process active = process;
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                        active.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stderr.length() < 8_000) stderr.append(line).append('\n');
                    }
                } catch (IOException ignored) { }
            }, "codex-stderr");
            errorReader.setDaemon(true);
            errorReader.start();
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(prompt);
                writer.flush();
            }
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonObject event;
                    try {
                        event = JsonParser.parseString(line).getAsJsonObject();
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    String type = string(event, "type");
                    for (CodexProgress.Update update : progress.accept(event))
                        callback.accept(new Event(update.kind(), update.text(), sessionId));
                    if (type.equals("thread.started")) {
                        sessionId = string(event, "thread_id");
                        callback.accept(new Event("session", "", sessionId));
                    } else if (type.equals("turn.completed")) {
                        turnCompleted = true;
                    } else if (type.equals("turn.failed")) {
                        turnFailed = true;
                        failureDetail = CodexFailure.detail(event);
                    } else if (type.equals("error")) {
                        lastError = CodexFailure.detail(event);
                    }
                }
            }
            int exit = process.waitFor();
            String finalAnswer = progress.finalMessage();
            if (turnFailed) {
                String detail = !failureDetail.isBlank() ? failureDetail : lastError;
                callback.accept(new Event("error", detail.isBlank()
                        ? "O Codex informou uma falha nesta tarefa." : detail, sessionId));
            } else if (!turnCompleted && !lastError.isBlank()) {
                callback.accept(new Event("error", lastError, sessionId));
            } else if (exit == 0 && !finalAnswer.isBlank()) {
                callback.accept(new Event("complete", finalAnswer, sessionId));
            } else if (exit == 0) {
                callback.accept(new Event("complete", "Tarefa finalizada pelo Codex. Veja as alterações na tela Arquivos.", sessionId));
            } else {
                String details = stderr.toString().trim();
                callback.accept(new Event("error", !lastError.isBlank() ? lastError
                        : details.isBlank() ? "Codex terminou com código " + exit : details, sessionId));
            }
        } catch (IOException e) {
            callback.accept(new Event("error", "Não foi possível iniciar o Codex: " + e.getMessage(), sessionId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.accept(new Event("error", "Tarefa interrompida.", sessionId));
        } finally {
            running.remove(task.id);
            cancelled.remove(task.id);
            if (process != null && process.isAlive()) process.destroy();
        }
    }

    private static String string(JsonObject parent, String key) {
        return parent.has(key) && !parent.get(key).isJsonNull() ? parent.get(key).getAsString() : "";
    }
}
