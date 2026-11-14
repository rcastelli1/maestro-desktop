package br.com.agenteslocal.codex;

import br.com.agenteslocal.model.WorkspaceData.Agent;
import br.com.agenteslocal.model.WorkspaceData.Approval;
import br.com.agenteslocal.model.WorkspaceData.ChatMessage;
import br.com.agenteslocal.model.WorkspaceData.ChatThread;
import br.com.agenteslocal.model.WorkspaceData.Project;
import br.com.agenteslocal.model.WorkspaceData.Task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A bounded snapshot of one conversation shared with its agents at the start of a task. */
public final class SharedProjectContext {
    private static final int MAX_HISTORY_CHARS = 24_000;
    private static final int MAX_MESSAGE_CHARS = 3_000;
    private static final int MAX_TASKS = 20;

    public String build(Project project, Task currentTask) {
        return build(project, project.chatFor(currentTask), currentTask);
    }

    public String build(Project project, ChatThread chat, Task currentTask) {
        List<String> history = new ArrayList<>();
        for (ChatMessage message : chat.messages) {
            if (message == null || message.kind == null) continue;
            if ("user".equals(message.kind) && currentTask.id.equals(message.taskId)) continue;
            String entry = entry(project, message);
            if (!entry.isBlank()) history.add(entry);
        }

        List<String> selected = new ArrayList<>();
        int used = 0;
        int omitted = 0;
        for (int index = history.size() - 1; index >= 0; index--) {
            String entry = history.get(index);
            if (used + entry.length() + 1 > MAX_HISTORY_CHARS) {
                omitted = index + 1;
                break;
            }
            selected.addFirst(entry);
            used += entry.length() + 1;
        }

        StringBuilder context = new StringBuilder();
        if (chat.isPrivate()) {
            context.append("CONTEXTO DA CONVERSA INDIVIDUAL: ").append(clean(chat.name, 100)).append('\n')
                    .append("Este histórico pertence somente à conversa entre o usuário e este agente. "
                            + "Use as mensagens anteriores para continuar o assunto. "
                            + "Não encaminhe a tarefa a outros agentes. Priorize a solicitação atual.\n\n")
                    .append("Conversa individual (em ordem cronológica):\n");
        } else {
            context.append("CONTEXTO COMPARTILHADO DA CONVERSA: ").append(clean(chat.name, 100)).append('\n')
                    .append("Este histórico é visível aos agentes desta conversa. Use-o para entender pedidos, ")
                    .append("decisões e resultados dos colegas. Mensagens dirigidas a outros agentes são contexto, ")
                    .append("não uma ordem para repetir o trabalho deles. Priorize a solicitação atual.\n\n")
                    .append("Conversa do grupo (em ordem cronológica):\n");
        }
        if (omitted > 0) context.append("[As ").append(omitted)
                .append(" mensagens mais antigas foram omitidas por limite de contexto.]\n");
        if (selected.isEmpty()) context.append("[Nenhuma mensagem anterior.]\n");
        else for (String entry : selected) context.append(entry).append('\n');

        List<String> tasks = new ArrayList<>();
        for (Task task : project.tasks) {
            if (task == null || currentTask.id.equals(task.id) || !chat.id.equals(task.chatId)) continue;
            Agent owner = project.agent(task.agentId);
            tasks.add("- " + clean(owner == null ? "Agente removido" : owner.name, 80)
                    + " | " + clean(task.status, 60) + " | " + clean(task.title, 150));
        }
        context.append("\nEstado das tarefas anteriores:\n");
        if (tasks.isEmpty()) context.append("[Nenhuma tarefa anterior.]\n");
        else for (int index = Math.max(0, tasks.size() - MAX_TASKS); index < tasks.size(); index++)
            context.append(tasks.get(index)).append('\n');
        return context.toString();
    }

    private String entry(Project project, ChatMessage message) {
        String text = message.text == null ? "" : message.text.trim();
        String prefix = "[" + clean(message.time, 24) + "] ";
        return switch (message.kind) {
            case "user" -> {
                Agent recipient = project.agent(message.agentId);
                String files = attachments(message.attachments);
                String reply = message.replyToMessageId == null || message.replyToMessageId.isBlank()
                        ? "" : " [Em resposta a " + clean(message.replySender, 80) + ": "
                                + clean(message.replyText, 240) + "]";
                yield prefix + "Você → " + (recipient == null ? "Agente removido" : clean(recipient.name, 80))
                        + reply + ": " + clean(text, MAX_MESSAGE_CHARS) + files;
            }
            case "agent" -> prefix + clean(message.sender, 80) + " (resposta): "
                    + clean(text, MAX_MESSAGE_CHARS);
            case "progress" -> "note".equals(message.progressType)
                    ? prefix + clean(message.sender, 80) + " (atualização): "
                    + clean(text, MAX_MESSAGE_CHARS) : "";
            case "approval" -> approval(project, message, prefix);
            case "system", "error" -> prefix + "Sistema: " + clean(text, 600);
            default -> "";
        };
    }

    private String approval(Project project, ChatMessage message, String prefix) {
        Approval approval = project.approvals.stream()
                .filter(item -> item.id.equals(message.agentId)).findFirst().orElse(null);
        if (approval == null) return "";
        StringBuilder result = new StringBuilder(prefix).append("Decisão: ")
                .append(clean(approval.title, 300)).append(" | ").append(clean(approval.status, 40));
        if (approval.response != null && !approval.response.isBlank())
            result.append(" | Resposta do usuário: ").append(clean(approval.response, 500));
        return result.toString();
    }

    private String attachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (String attachment : attachments) {
            try { names.add(clean(Path.of(attachment).getFileName().toString(), 100)); }
            catch (RuntimeException ignored) { names.add("arquivo anexado"); }
        }
        return " [Anexos: " + String.join(", ", names) + "]";
    }

    private String clean(String value, int limit) {
        if (value == null || value.isBlank()) return "";
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').replace('\0', ' ')
                .replaceAll("\\s+", " ").trim();
        return singleLine.length() <= limit ? singleLine : singleLine.substring(0, limit - 1) + "…";
    }
}
