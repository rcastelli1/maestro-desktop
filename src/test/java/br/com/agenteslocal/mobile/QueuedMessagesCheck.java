package br.com.agenteslocal.mobile;

import br.com.agenteslocal.chat.QueuedMessages;
import br.com.agenteslocal.data.WorkspaceRepository;
import br.com.agenteslocal.model.WorkspaceData;
import br.com.agenteslocal.model.WorkspaceData.*;
import com.google.gson.JsonObject;

import java.util.List;

/** Run directly with AGENTES_LOCAL_HOME set to an isolated test directory. */
public final class QueuedMessagesCheck {
    public static void main(String[] args) throws Exception {
        WorkspaceData workspace = new WorkspaceData();
        Project project = Project.create("Fila", "", System.getProperty("java.io.tmpdir"));
        workspace.projects.add(project);
        ChatThread chat = project.activeChat();
        Agent agent = project.agents.getFirst();
        Task first = add(project, chat, agent, "Primeira");
        Task second = add(project, chat, agent, "Segunda");
        Task third = add(project, chat, agent, "Terceira");
        if (!QueuedMessages.move(project, third, "top") || project.tasks.getFirst() != third)
            throw new AssertionError("Prioridade não foi alterada");
        if (!QueuedMessages.move(project, third, "down") || project.tasks.get(1) != third)
            throw new AssertionError("A ordem para baixo falhou");
        WorkspaceRepository repository = new WorkspaceRepository();
        MobileApi api = new MobileApi(() -> workspace, repository, new MobileApi.Actions() {
            public boolean send(Project p, ChatThread c, Agent a, String text,
                                ChatMessage reply, List<String> attachments) { return false; }
            public void approve(Project p, Approval a, boolean accept) { }
            public void answer(Project p, Approval a, int choice, String response, boolean skip) { }
            public void stop(Project p, Task t, boolean next) { }
            public boolean editQueued(Project p, Task t, String text) {
                ChatMessage message = QueuedMessages.userMessage(p, t);
                if (message == null) return false;
                message.text = text;
                t.prompt = text;
                try { repository.save(workspace); } catch (Exception error) { throw new RuntimeException(error); }
                return true;
            }
            public boolean deleteQueued(Project p, Task t) {
                ChatMessage message = QueuedMessages.userMessage(p, t);
                if (message == null) return false;
                p.chat(t.chatId).messages.remove(message);
                p.tasks.remove(t);
                try { repository.save(workspace); } catch (Exception error) { throw new RuntimeException(error); }
                return true;
            }
            public boolean moveQueued(Project p, Task t, String direction) {
                return QueuedMessages.move(p, t, direction);
            }
            public void changed(Project p) { }
            public void registerPush(String token) { }
            public void unregisterPush(String token) { }
        });
        String base = "/api/projects/" + project.id + "/queue/";
        JsonObject move = new JsonObject(); move.addProperty("direction", "up");
        if (api.handle(new MobileBridgeServer.Request("POST", base + second.id + "/move", move)).status() != 200
                || project.tasks.get(1) != second)
            throw new AssertionError("Mudança de prioridade pela API falhou");
        JsonObject edit = new JsonObject(); edit.addProperty("text", "Segunda editada");
        if (api.handle(new MobileBridgeServer.Request("POST", base + second.id + "/edit", edit)).status() != 200
                || !"Segunda editada".equals(QueuedMessages.userMessage(project, second).text))
            throw new AssertionError("Edição pela API falhou");
        if (api.handle(new MobileBridgeServer.Request("POST", base + second.id + "/delete", new JsonObject())).status() != 200
                || project.tasks.contains(second))
            throw new AssertionError("Exclusão pela API falhou");
        String response = new String(api.handle(new MobileBridgeServer.Request("GET",
                "/api/projects/" + project.id + "/queue", null)).content());
        if (response.contains(second.id) || !response.contains(first.id) || !response.contains(third.id))
            throw new AssertionError("Lista compartilhada da fila está incorreta");
        if (repository.load().projects.getFirst().tasks.size() != 2)
            throw new AssertionError("Alterações não foram persistidas");
        System.out.println("Prioridade, edição, exclusão e API compartilhada: OK");
    }

    private static Task add(Project project, ChatThread chat, Agent agent, String text) {
        Task task = new Task();
        task.chatId = chat.id;
        task.agentId = agent.id;
        task.title = text;
        task.prompt = text;
        project.tasks.add(task);
        ChatMessage message = ChatMessage.of("user", agent.id, "Você", text);
        message.taskId = task.id;
        chat.messages.add(message);
        return task;
    }
}
