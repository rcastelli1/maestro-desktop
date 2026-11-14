package br.com.agenteslocal.chat;

import br.com.agenteslocal.model.WorkspaceData.ChatMessage;
import br.com.agenteslocal.model.WorkspaceData.ChatThread;
import br.com.agenteslocal.model.WorkspaceData.Project;
import br.com.agenteslocal.model.WorkspaceData.Task;

import java.util.List;

/** The order in Project.tasks is the execution order for each agent's pending work. */
public final class QueuedMessages {
    private QueuedMessages() { }

    public static boolean pending(Task task) {
        return "Na fila".equals(task.status) || "Pausada".equals(task.status);
    }

    public static List<Task> list(Project project) {
        return project.tasks.stream().filter(QueuedMessages::pending).toList();
    }

    public static ChatMessage userMessage(Project project, Task task) {
        ChatThread chat = project.chat(task.chatId);
        if (chat == null) return null;
        return chat.messages.stream().filter(message -> "user".equals(message.kind)
                && task.id.equals(message.taskId)).findFirst().orElse(null);
    }

    public static boolean move(Project project, Task task, String direction) {
        if (!pending(task)) return false;
        int current = project.tasks.indexOf(task);
        if (current < 0) return false;
        int target = -1;
        if ("up".equals(direction) || "top".equals(direction)) {
            for (int index = current - 1; index >= 0; index--) {
                Task candidate = project.tasks.get(index);
                if (pending(candidate) && task.agentId.equals(candidate.agentId)) {
                    target = index;
                    if ("up".equals(direction)) break;
                }
            }
        } else if ("down".equals(direction)) {
            for (int index = current + 1; index < project.tasks.size(); index++) {
                Task candidate = project.tasks.get(index);
                if (pending(candidate) && task.agentId.equals(candidate.agentId)) {
                    target = index;
                    break;
                }
            }
        }
        if (target < 0) return false;
        project.tasks.remove(current);
        project.tasks.add(target, task);
        return true;
    }
}
