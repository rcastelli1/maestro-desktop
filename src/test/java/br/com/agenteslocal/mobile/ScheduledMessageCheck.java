package br.com.agenteslocal.mobile;

import br.com.agenteslocal.data.WorkspaceRepository;
import br.com.agenteslocal.model.WorkspaceData;
import br.com.agenteslocal.model.WorkspaceData.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/** Run directly to check the schedule API and its on-disk persistence. */
public final class ScheduledMessageCheck {
    public static void main(String[] args) throws Exception {
        WorkspaceRepository repository = new WorkspaceRepository();
        WorkspaceData workspace = new WorkspaceData();
        Project project = Project.create("Teste", "", System.getProperty("java.io.tmpdir"));
        workspace.projects.add(project);
        ChatThread chat = project.activeChat();
        MobileApi api = new MobileApi(() -> workspace, repository, new MobileApi.Actions() {
            public boolean send(Project p, ChatThread c, Agent a, String text,
                                ChatMessage reply, List<String> attachments) { return true; }
            public void approve(Project p, Approval a, boolean accept) { }
            public void answer(Project p, Approval a, int choice, String response, boolean skip) { }
            public void stop(Project p, Task t, boolean next) { }
            public boolean editQueued(Project p, Task t, String text) { return false; }
            public boolean deleteQueued(Project p, Task t) { return false; }
            public boolean moveQueued(Project p, Task t, String direction) { return false; }
            public void changed(Project p) { }
            public void registerPush(String token) { }
            public void unregisterPush(String token) { }
        });
        String path = "/api/projects/" + project.id + "/chats/" + chat.id + "/scheduled";
        JsonObject body = new JsonObject();
        body.addProperty("text", "Lembrar o Programador");
        body.addProperty("agentId", project.agents.getFirst().id);
        body.addProperty("kind", "time");
        body.addProperty("dueAt", System.currentTimeMillis() + 120_000);
        JsonObject file = new JsonObject();
        file.addProperty("name", "lembrete.txt");
        file.addProperty("base64", Base64.getEncoder().encodeToString("conteúdo do arquivo".getBytes()));
        JsonArray attachments = new JsonArray(); attachments.add(file);
        body.add("attachments", attachments);
        var created = api.handle(new MobileBridgeServer.Request("POST", path, body));
        if (created.status() != 201 || project.scheduledMessages.size() != 1)
            throw new AssertionError("POST não criou agendamento");
        Path stored = Path.of(project.scheduledMessages.getFirst().attachments.getFirst());
        if (!Files.isRegularFile(stored) || !new String(Files.readAllBytes(stored)).equals("conteúdo do arquivo"))
            throw new AssertionError("Anexo não foi guardado para envio posterior");
        if (repository.load().projects.getFirst().scheduledMessages.size() != 1)
            throw new AssertionError("Agendamento não foi persistido");
        JsonObject reset = body.deepCopy();
        reset.addProperty("kind", "shortReset");
        reset.remove("dueAt");
        reset.remove("attachments");
        if (api.handle(new MobileBridgeServer.Request("POST", path, reset)).status() != 201
                || project.scheduledMessages.get(1).dueAt != 0)
            throw new AssertionError("Agendamento para reset não ficou pendente");
        var listed = api.handle(new MobileBridgeServer.Request("GET", path, null));
        if (listed.status() != 200 || !new String(listed.content()).contains("Lembrar o Programador"))
            throw new AssertionError("GET não listou agendamento");
        String cancel = path + "/" + project.scheduledMessages.getFirst().id + "/cancel";
        var cancelled = api.handle(new MobileBridgeServer.Request("POST", cancel, new JsonObject()));
        if (cancelled.status() != 200 || repository.load().projects.getFirst().scheduledMessages.size() != 1
                || Files.exists(stored))
            throw new AssertionError("Cancelamento não foi persistido");
        System.out.println("Agendamento por data/reset, anexo, persistência e cancelamento: OK");
    }
}
