package br.com.agenteslocal.codex;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts Codex CLI JSONL events into short, user-facing progress updates. */
public final class CodexProgress {
    public record Update(String kind, String text) { }
    private final Map<String, String> runningCommands = new HashMap<>();
    private String pendingAgentMessage = "";
    private String lastUpdate = "";

    public List<Update> accept(JsonObject event) {
        List<Update> updates = new ArrayList<>();
        String type = string(event, "type");
        if (type.equals("turn.started")) add(updates, "Analisando o pedido...");

        if (!type.startsWith("item.")) return updates;
        JsonObject item = object(event, "item");
        String itemType = string(item, "type");
        if (type.equals("item.completed") && itemType.equals("agent_message")) {
            String message = string(item, "text").trim();
            if (!message.isBlank()) {
                if (!pendingAgentMessage.isBlank()) add(updates, "note", pendingAgentMessage);
                pendingAgentMessage = message;
            }
            return updates;
        }

        if (type.equals("item.started") && !itemType.equals("reasoning")) {
            if (!pendingAgentMessage.isBlank()) {
                add(updates, "note", pendingAgentMessage);
                pendingAgentMessage = "";
            }
            switch (itemType) {
                case "command_execution" -> {
                    String stage = commandStage(string(item, "command"));
                    runningCommands.put(string(item, "id"), stage);
                    add(updates, stage);
                }
                case "file_change" -> add(updates, "Editando arquivos do projeto...");
                case "mcp_tool_call" -> {
                    String tool = string(item, "tool");
                    add(updates, tool.isBlank() ? "Usando uma integração..."
                            : "Usando a ferramenta " + compact(tool, 60) + "...");
                }
                case "web_search" -> add(updates, "Pesquisando na web...");
                case "plan", "plan_update" -> add(updates, "Organizando os próximos passos...");
                default -> { }
            }
        } else if (type.equals("item.completed")) {
            if (itemType.equals("command_execution")) {
                String stage = runningCommands.remove(string(item, "id"));
                String status = string(item, "status");
                Integer exitCode = number(item, "exit_code");
                if (exitCode != null && exitCode != 0 || status.equals("failed")) {
                    add(updates, "Um comando falhou; verificando o resultado...");
                } else if (stage != null && stage.equals("Executando testes...")) {
                    add(updates, "Testes concluídos.");
                } else if (stage != null && stage.equals("Compilando o projeto...")) {
                    add(updates, "Compilação concluída.");
                }
            } else if (itemType.equals("file_change")) {
                add(updates, "Arquivos atualizados.");
            }
        }
        return updates;
    }

    public String finalMessage() {
        return pendingAgentMessage;
    }

    private String commandStage(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*(mvn|gradle|npm|pnpm|yarn|pytest|cargo|go|dotnet)[^\\r\\n]{0,100}\\btest\\b.*"))
            return "Executando testes...";
        if (lower.contains("javac") || lower.contains("mvn package") || lower.contains("gradle build")
                || lower.contains("npm run build") || lower.contains("cargo build"))
            return "Compilando o projeto...";
        if (lower.contains("apply_patch") || lower.contains("set-content") || lower.contains("writealltext"))
            return "Editando arquivos do projeto...";
        if (lower.contains("git status") || lower.contains("git diff") || lower.contains("git log"))
            return "Verificando alterações no Git...";
        if (lower.matches("(?s).*(\\brg\\b|\\bgrep\\b|get-content|\\bcat\\b|read_text_file|\\bls\\b|get-childitem).*"))
            return "Lendo arquivos do projeto...";
        return "Executando uma ferramenta no projeto...";
    }

    private void add(List<Update> updates, String text) {
        add(updates, "activity", text);
    }

    private void add(List<Update> updates, String kind, String text) {
        String clean = compact(text.trim(), 1_200);
        if (!clean.isBlank() && !clean.equals(lastUpdate)) {
            updates.add(new Update(kind, clean));
            lastUpdate = clean;
        }
    }

    private String compact(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit - 1) + "…" : text;
    }

    private JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private String string(JsonObject parent, String key) {
        try { return parent.has(key) && !parent.get(key).isJsonNull() ? parent.get(key).getAsString() : ""; }
        catch (RuntimeException ignored) { return ""; }
    }

    private Integer number(JsonObject parent, String key) {
        try { return parent.has(key) && !parent.get(key).isJsonNull() ? parent.get(key).getAsInt() : null; }
        catch (RuntimeException ignored) { return null; }
    }
}
