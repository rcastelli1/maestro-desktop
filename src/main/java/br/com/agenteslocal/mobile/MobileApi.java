package br.com.agenteslocal.mobile;

import br.com.agenteslocal.application.ScheduledMessageService;
import br.com.agenteslocal.chat.ChatMentions;
import br.com.agenteslocal.chat.QueuedMessages;
import br.com.agenteslocal.data.WorkspaceRepository;
import br.com.agenteslocal.model.WorkspaceData;
import br.com.agenteslocal.model.WorkspaceData.Agent;
import br.com.agenteslocal.model.WorkspaceData.Approval;
import br.com.agenteslocal.model.WorkspaceData.ChatMessage;
import br.com.agenteslocal.model.WorkspaceData.ChatThread;
import br.com.agenteslocal.model.WorkspaceData.Project;
import br.com.agenteslocal.model.WorkspaceData.ScheduledMessage;
import br.com.agenteslocal.model.WorkspaceData.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

/** Explicit, path-free view of desktop data and a few controlled mobile actions. */
public final class MobileApi implements MobileBridgeServer.Handler {
    public interface Actions {
        boolean send(Project project, ChatThread chat, Agent agent, String text,
                     ChatMessage reply, List<String> attachments);
        void approve(Project project, Approval approval, boolean accept);
        void answer(Project project, Approval question, int choice, String response, boolean skip);
        void stop(Project project, Task task, boolean next);
        boolean editQueued(Project project, Task task, String text);
        boolean deleteQueued(Project project, Task task);
        boolean moveQueued(Project project, Task task, String direction);
        void changed(Project project);
        void registerPush(String token) throws IOException;
        void unregisterPush(String token) throws IOException;
    }

    private final Supplier<WorkspaceData> workspace;
    private final WorkspaceRepository repository;
    private final ScheduledMessageService scheduledMessages;
    private final Actions actions;

    public MobileApi(Supplier<WorkspaceData> workspace, WorkspaceRepository repository, Actions actions) {
        this.workspace = workspace;
        this.repository = repository;
        this.scheduledMessages = new ScheduledMessageService(repository, workspace);
        this.actions = actions;
    }

    @Override public MobileBridgeServer.Response handle(MobileBridgeServer.Request request) throws Exception {
        String method = request.method();
        String path = request.path();
        int queryAt = path.indexOf('?');
        String query = queryAt < 0 ? "" : path.substring(queryAt + 1);
        if (queryAt >= 0) path = path.substring(0, queryAt);
        String afterMessageId = "";
        if (!query.isEmpty()) {
            if (!query.startsWith("after=") || !query.substring(6).matches("[A-Za-z0-9_-]{1,80}"))
                throw fail(400, "Marcador de mensagens invalido.");
            afterMessageId = query.substring(6);
        }
        String[] parts = path.split("/");
        if (parts.length == 4 && "api".equals(parts[1]) && "notifications".equals(parts[2])
                && "POST".equals(method)) {
            String token = value(request.body(), "token").trim();
            try {
                if ("register".equals(parts[3])) actions.registerPush(token);
                else if ("unregister".equals(parts[3])) actions.unregisterPush(token);
                else throw fail(404, "Rota não encontrada.");
            } catch (IllegalArgumentException | IllegalStateException error) {
                throw fail(400, error.getMessage());
            }
            JsonObject result = object(); result.addProperty("ok", true);
            return json(200, result);
        }
        if (parts.length >= 3 && parts.length <= 5 && "api".equals(parts[1])
                && "snapshot".equals(parts[2]) && "GET".equals(method)) {
            JsonObject result = object();
            JsonArray projects = new JsonArray();
            workspace.get().projects.forEach(item -> projects.add(projectSummary(item)));
            result.add("projects", projects);
            if (parts.length >= 4) {
                Project selected = workspace.get().projects.stream()
                        .filter(item -> item.id.equals(parts[3])).findFirst()
                        .orElseThrow(() -> fail(404, "Projeto não encontrado."));
                JsonObject details = object();
                details.add("project", projectSummary(selected));
                details.addProperty("reduceAnimations", workspace.get().reduceAnimations);
                JsonArray agents = new JsonArray();
                selected.agents.forEach(agent -> agents.add(agentJson(agent)));
                details.add("agents", agents);
                JsonArray tasks = new JsonArray();
                selected.tasks.stream().sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                        .limit(100).forEach(task -> tasks.add(taskJson(task)));
                details.add("tasks", tasks);
                details.add("queue", queueJson(selected));
                JsonArray approvals = new JsonArray();
                selected.approvals.stream().filter(approval -> "Pendente".equals(approval.status))
                        .forEach(approval -> approvals.add(approvalJson(approval)));
                details.add("approvals", approvals);
                result.add("details", details);
                JsonArray chats = new JsonArray();
                selected.chats.forEach(chat -> chats.add(chatJson(chat)));
                result.add("chats", chats);
                if (parts.length == 5) {
                    ChatThread selectedChat = selected.chat(parts[4]);
                    if (selectedChat == null) throw fail(404, "Conversa não encontrada.");
                    JsonArray messages = new JsonArray();
                    addRecentMessages(messages, selectedChat.messages, afterMessageId);
                    result.add("messages", messages);
                }
            }
            return json(200, result);
        }
        if (parts.length == 3 && "api".equals(parts[1]) && "projects".equals(parts[2]) && "GET".equals(method)) {
            JsonArray projects = new JsonArray();
            for (Project project : workspace.get().projects) projects.add(projectSummary(project));
            JsonObject result = object();
            result.add("projects", projects);
            return json(200, result);
        }
        if (parts.length < 4 || !"api".equals(parts[1]) || !"projects".equals(parts[2]))
            throw fail(404, "Rota não encontrada.");
        Project project = workspace.get().projects.stream().filter(item -> item.id.equals(parts[3]))
                .findFirst().orElseThrow(() -> fail(404, "Projeto não encontrado."));

        if (parts.length == 4 && "GET".equals(method)) {
            JsonObject result = object();
            result.add("project", projectSummary(project));
            result.addProperty("reduceAnimations", workspace.get().reduceAnimations);
            JsonArray agents = new JsonArray();
            project.agents.forEach(agent -> agents.add(agentJson(agent)));
            result.add("agents", agents);
            JsonArray tasks = new JsonArray();
            project.tasks.stream().sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                    .limit(100).forEach(task -> tasks.add(taskJson(task)));
            result.add("tasks", tasks);
            result.add("queue", queueJson(project));
            JsonArray approvals = new JsonArray();
            project.approvals.stream().filter(approval -> "Pendente".equals(approval.status))
                    .forEach(approval -> approvals.add(approvalJson(approval)));
            result.add("approvals", approvals);
            return json(200, result);
        }
        if (parts.length == 5 && "chats".equals(parts[4])) {
            if ("GET".equals(method)) {
                JsonArray chats = new JsonArray();
                project.chats.forEach(chat -> chats.add(chatJson(chat)));
                JsonObject result = object(); result.add("chats", chats);
                return json(200, result);
            }
            if ("POST".equals(method)) {
                String privateAgentId = value(request.body(), "privateAgentId").trim();
                String name = value(request.body(), "name").trim();
                if (!privateAgentId.isBlank()) {
                    Agent agent = project.agent(privateAgentId);
                    if (agent == null) throw fail(404, "Agente não encontrado.");
                    ChatThread existing = project.chats.stream().filter(chat -> privateAgentId.equals(chat.privateAgentId))
                            .findFirst().orElse(null);
                    if (existing != null) return json(200, chatJson(existing));
                    name = "Conversa com " + agent.name;
                } else {
                    if (name.isBlank() || name.length() > 80) throw fail(400, "Informe um nome de até 80 caracteres.");
                    String proposed = name;
                    if (project.chats.stream().anyMatch(chat -> chat.name.equalsIgnoreCase(proposed)))
                        throw fail(409, "Já existe uma conversa com este nome.");
                }
                ChatThread chat = new ChatThread(); chat.name = name; chat.privateAgentId = privateAgentId;
                project.chats.add(chat);
                repository.save(workspace.get());
                actions.changed(project);
                return json(201, chatJson(chat));
            }
        }
        if (parts.length == 7 && "chats".equals(parts[4]) && "messages".equals(parts[6])) {
            ChatThread chat = project.chat(parts[5]);
            if (chat == null) throw fail(404, "Conversa não encontrada.");
            if ("GET".equals(method)) {
                JsonArray messages = new JsonArray();
                addRecentMessages(messages, chat.messages, afterMessageId);
                JsonObject result = object(); result.add("messages", messages);
                return json(200, result);
            }
            if ("POST".equals(method)) return sendMessage(project, chat, request.body());
        }
        if (parts.length >= 7 && "chats".equals(parts[4]) && "scheduled".equals(parts[6])) {
            ChatThread chat = project.chat(parts[5]);
            if (chat == null) throw fail(404, "Conversa não encontrada.");
            if (parts.length == 7 && "GET".equals(method)) {
                JsonArray pending = new JsonArray();
                project.scheduledMessages.stream().filter(item -> chat.id.equals(item.chatId))
                        .forEach(item -> pending.add(scheduledJson(item)));
                JsonObject result = object(); result.add("scheduled", pending);
                return json(200, result);
            }
            if (parts.length == 7 && "POST".equals(method)) {
                JsonObject body = request.body();
                String text = value(body, "text").trim();
                JsonArray files = body.has("attachments") && body.get("attachments").isJsonArray()
                        ? body.getAsJsonArray("attachments") : new JsonArray();
                if ((text.isBlank() && files.isEmpty()) || text.length() > 20_000)
                    throw fail(400, "Escreva uma mensagem ou anexe um arquivo. O texto aceita até 20 mil caracteres.");
                if (files.size() > 4) throw fail(400, "Envie no máximo quatro anexos por mensagem.");
                String kind = value(body, "kind");
                if (!List.of("time", "shortReset", "weeklyReset").contains(kind))
                    throw fail(400, "Tipo de agendamento inválido.");
                Agent agent = chat.isPrivate() ? project.agent(chat.privateAgentId)
                        : project.agent(value(body, "agentId"));
                if (agent == null) throw fail(400, "Selecione um agente para receber a mensagem.");
                String replyId = value(body, "replyToMessageId");
                ChatMessage reply = replyId.isBlank() ? null : chat.messages.stream()
                        .filter(item -> item.id.equals(replyId)).findFirst().orElse(null);
                if (!replyId.isBlank() && reply == null)
                    throw fail(404, "Mensagem citada não encontrada.");
                if (!chat.isPrivate()) {
                    Agent replyOwner = reply == null ? null : project.agent(reply.agentId);
                    if (replyOwner != null) agent = replyOwner;
                    Agent mentioned = ChatMentions.firstRecipient(project.agents, text);
                    if (mentioned != null) agent = mentioned;
                }
                long dueAt = 0;
                if ("time".equals(kind)) {
                    try { dueAt = body.get("dueAt").getAsLong(); }
                    catch (RuntimeException error) { throw fail(400, "Data e hora inválidas."); }
                    if (dueAt <= System.currentTimeMillis()) throw fail(400, "Escolha um horário futuro.");
                }
                ScheduledMessage scheduled = new ScheduledMessage();
                scheduled.chatId = chat.id;
                scheduled.agentId = agent.id;
                scheduled.text = text;
                scheduled.replyToMessageId = replyId;
                scheduled.kind = kind;
                scheduled.dueAt = dueAt;
                try {
                    int totalBytes = 0;
                    for (JsonElement element : files) {
                        if (!element.isJsonObject()) throw fail(400, "Anexo inválido.");
                        JsonObject file = element.getAsJsonObject();
                        String name = value(file, "name").trim();
                        String encoded = value(file, "base64");
                        if (name.isBlank() || name.length() > 200 || encoded.length() > 11_000_000)
                            throw fail(400, "Nome ou tamanho do anexo inválido.");
                        byte[] bytes;
                        try { bytes = Base64.getDecoder().decode(encoded); }
                        catch (IllegalArgumentException error) { throw fail(400, "Conteúdo do anexo inválido."); }
                        totalBytes += bytes.length;
                        if (totalBytes > 8_000_000) throw fail(413, "Anexos grandes demais.");
                        scheduled.attachments.add(repository.storeAttachment(project.id, name, bytes).toString());
                    }
                    scheduledMessages.schedule(project, scheduled);
                } catch (IOException | RuntimeException error) {
                    for (String storedPath : scheduled.attachments) try {
                        repository.discardAttachment(project.id, Path.of(storedPath));
                    } catch (IOException ignored) { }
                    throw error;
                }
                actions.changed(project);
                return json(201, scheduledJson(scheduled));
            }
            if (parts.length == 9 && "cancel".equals(parts[8]) && "POST".equals(method)) {
                ScheduledMessage scheduled = project.scheduledMessages.stream()
                        .filter(item -> chat.id.equals(item.chatId) && item.id.equals(parts[7]))
                        .findFirst().orElseThrow(() -> fail(404, "Agendamento não encontrado."));
                scheduledMessages.cancel(project, scheduled, ignoredPath -> false);
                actions.changed(project);
                JsonObject result = object(); result.addProperty("cancelled", true);
                return json(200, result);
            }
        }
        if (parts.length == 6 && "attachments".equals(parts[4]) && "GET".equals(method))
            return attachment(project, parts[5]);
        if (parts.length == 7 && "approvals".equals(parts[4]) && "decision".equals(parts[6])
                && "POST".equals(method)) {
            Approval approval = project.approvals.stream().filter(item -> item.id.equals(parts[5]))
                    .findFirst().orElseThrow(() -> fail(404, "Aprovação não encontrada."));
            if (!"Pendente".equals(approval.status)) throw fail(409, "Esta decisão já foi tomada.");
            if ("question".equals(approval.kind)) {
                int choice = intValue(request.body(), "choice", approval.recommendedChoice);
                String response = value(request.body(), "response").trim();
                boolean skip = boolValue(request.body(), "skip");
                if (!skip && (choice < 0 || choice > approval.choices.size()))
                    throw fail(400, "Escolha inválida.");
                if (!skip && choice == approval.choices.size() && response.isBlank())
                    throw fail(400, "Escreva uma resposta.");
                actions.answer(project, approval, choice, response, skip);
            } else actions.approve(project, approval, boolValue(request.body(), "accept"));
            if ("Pendente".equals(approval.status))
                throw fail(409, "Não foi possível concluir a decisão. Confira o Codex no computador.");
            return json(200, approvalJson(approval));
        }
        if (parts.length == 7 && "tasks".equals(parts[4]) && "stop".equals(parts[6])
                && "POST".equals(method)) {
            Task task = project.task(parts[5]);
            if (task == null) throw fail(404, "Tarefa não encontrada.");
            if (!"Em andamento".equals(task.status)) throw fail(409, "Esta tarefa não está em andamento.");
            actions.stop(project, task, boolValue(request.body(), "next"));
            return json(200, taskJson(task));
        }
        if (parts.length == 5 && "queue".equals(parts[4]) && "GET".equals(method)) {
            JsonObject result = object(); result.add("queue", queueJson(project));
            return json(200, result);
        }
        if (parts.length == 7 && "queue".equals(parts[4]) && "POST".equals(method)) {
            Task task = project.task(parts[5]);
            if (task == null || !QueuedMessages.pending(task))
                throw fail(409, "Esta mensagem já começou ou saiu da fila.");
            boolean changed;
            switch (parts[6]) {
                case "edit" -> {
                    String text = value(request.body(), "text");
                    if (text.length() > 20_000) throw fail(400, "Mensagem grande demais.");
                    changed = actions.editQueued(project, task, text);
                }
                case "delete" -> changed = actions.deleteQueued(project, task);
                case "move" -> {
                    String direction = value(request.body(), "direction");
                    if (!List.of("up", "down", "top").contains(direction))
                        throw fail(400, "Direção inválida.");
                    changed = actions.moveQueued(project, task, direction);
                }
                default -> throw fail(404, "Rota não encontrada.");
            }
            if (!changed) throw fail(409, "Não foi possível alterar este item da fila.");
            JsonObject result = object(); result.add("queue", queueJson(project));
            return json(200, result);
        }
        throw fail(404, "Rota não encontrada.");
    }

    private MobileBridgeServer.Response sendMessage(Project project, ChatThread chat, JsonObject body) throws IOException {
        String text = value(body, "text").trim();
        if (text.length() > 20_000) throw fail(400, "Mensagem grande demais.");
        JsonArray files = body.has("attachments") && body.get("attachments").isJsonArray()
                ? body.getAsJsonArray("attachments") : new JsonArray();
        if (text.isBlank() && files.isEmpty()) throw fail(400, "Escreva uma mensagem ou anexe um arquivo.");
        if (files.size() > 4) throw fail(400, "Envie no máximo quatro anexos por mensagem.");
        Agent agent = chat.isPrivate() ? project.agent(chat.privateAgentId)
                : project.agent(value(body, "agentId"));
        if (agent == null) throw fail(400, "Selecione um agente para receber a mensagem.");
        String replyId = value(body, "replyToMessageId");
        ChatMessage reply = replyId.isBlank() ? null : chat.messages.stream()
                .filter(item -> item.id.equals(replyId)).findFirst().orElse(null);
        if (!replyId.isBlank() && reply == null) throw fail(404, "Mensagem citada não encontrada.");

        List<String> paths = new ArrayList<>();
        int previousMessageCount = chat.messages.size();
        try {
            int totalBytes = 0;
            for (JsonElement element : files) {
                if (!element.isJsonObject()) throw fail(400, "Anexo inválido.");
                JsonObject file = element.getAsJsonObject();
                String name = value(file, "name").trim();
                String encoded = value(file, "base64");
                if (name.isBlank() || name.length() > 200 || encoded.length() > 8_000_000)
                    throw fail(400, "Nome ou tamanho do anexo inválido.");
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode(encoded); }
                catch (IllegalArgumentException error) { throw fail(400, "Conteúdo do anexo inválido."); }
                totalBytes += bytes.length;
                if (totalBytes > 8_000_000) throw fail(413, "Anexos grandes demais.");
                paths.add(repository.storeAttachment(project.id, name, bytes).toString());
            }
            if (!actions.send(project, chat, agent, text, reply, paths))
                throw fail(409, "Não foi possível iniciar a tarefa. Confira a pasta do projeto e a conexão do Codex no computador.");
        } catch (Exception error) {
            for (String path : paths) try { repository.discardAttachment(project.id, Path.of(path)); }
            catch (IOException ignored) { }
            if (error instanceof IOException io) throw io;
            if (error instanceof RuntimeException runtime) throw runtime;
            throw fail(500, "Não foi possível enviar a mensagem.");
        }
        JsonObject result = object(); result.addProperty("sent", true);
        JsonArray added = new JsonArray();
        chat.messages.stream().skip(previousMessageCount)
                .forEach(message -> added.add(messageJson(message)));
        result.add("messages", added);
        result.add("queue", queueJson(project));
        return json(201, result);
    }

    private static JsonObject scheduledJson(ScheduledMessage item) {
        JsonObject json = object();
        json.addProperty("id", item.id);
        json.addProperty("chatId", item.chatId);
        json.addProperty("agentId", item.agentId);
        json.addProperty("text", item.text);
        json.addProperty("replyToMessageId", item.replyToMessageId);
        json.addProperty("kind", item.kind);
        json.addProperty("dueAt", item.dueAt);
        json.addProperty("createdAt", item.createdAt);
        json.addProperty("lastError", item.lastError);
        JsonArray attachments = new JsonArray();
        item.attachments.forEach(path -> {
            String id = Path.of(path).getFileName().toString();
            JsonObject file = object(); file.addProperty("id", id);
            file.addProperty("name", id.contains("_") ? id.substring(id.indexOf('_') + 1) : id);
            attachments.add(file);
        });
        json.add("attachments", attachments);
        return json;
    }

    private MobileBridgeServer.Response attachment(Project project, String filename) throws IOException {
        if (!filename.matches("[A-Za-z0-9_-]+_[^/\\\\]+")) throw fail(404, "Anexo não encontrado.");
        String path = project.allMessages().stream().flatMap(message -> message.attachments == null
                ? java.util.stream.Stream.<String>empty() : message.attachments.stream())
                .filter(item -> Path.of(item).getFileName().toString().equals(filename))
                .findFirst().orElseThrow(() -> fail(404, "Anexo não encontrado."));
        Path directory = repository.home().resolve("projects").resolve(project.id).resolve("attachments")
                .toAbsolutePath().normalize();
        Path file = Path.of(path).toAbsolutePath().normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) throw fail(404, "Anexo não encontrado.");
        if (Files.size(file) > 15_000_000) throw fail(413, "Anexo grande demais.");
        String lower = filename.toLowerCase();
        String type = lower.endsWith(".png") ? "image/png" : lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                ? "image/jpeg" : lower.endsWith(".gif") ? "image/gif" : lower.endsWith(".webp")
                ? "image/webp" : "application/octet-stream";
        return MobileBridgeServer.Response.bytes(type, Files.readAllBytes(file));
    }

    private JsonObject projectSummary(Project project) {
        JsonObject json = object();
        json.addProperty("id", project.id); json.addProperty("name", project.name);
        json.addProperty("description", project.description);
        json.addProperty("agentCount", project.agents.size());
        json.addProperty("activeTaskCount", project.tasks.stream().filter(task -> "Em andamento".equals(task.status)).count());
        json.addProperty("pendingApprovalCount", project.approvals.stream().filter(item -> "Pendente".equals(item.status)).count());
        return json;
    }
    private JsonObject agentJson(Agent agent) {
        JsonObject json = object();
        json.addProperty("id", agent.id); json.addProperty("name", agent.name);
        json.addProperty("role", agent.role); json.addProperty("status", agent.status);
        json.addProperty("color", agent.color); json.addProperty("eyeColor", agent.eyeColor);
        json.addProperty("visualId", agent.visualId); json.addProperty("model", agent.model);
        var choices = agent.animations == null ? new br.com.agenteslocal.model.AgentAnimations() : agent.animations;
        JsonObject animations = object();
        animations.addProperty("floatMotion", choices.floatMotion);
        animations.addProperty("blink", choices.blink);
        animations.addProperty("pulse", choices.pulse);
        animations.addProperty("wave", choices.wave);
        animations.addProperty("sneeze", choices.sneeze);
        animations.addProperty("sleep", choices.sleep);
        animations.addProperty("read", choices.read);
        animations.addProperty("computer", choices.computer);
        json.add("animations", animations);
        return json;
    }
    private JsonObject chatJson(ChatThread chat) {
        JsonObject json = object();
        json.addProperty("id", chat.id); json.addProperty("name", chat.name);
        json.addProperty("privateAgentId", chat.privateAgentId);
        json.addProperty("lastText", chat.messages.isEmpty() ? "" : chat.messages.getLast().text);
        json.addProperty("lastTime", chat.messages.isEmpty() ? "" : chat.messages.getLast().time);
        json.addProperty("messageCount", chat.messages.size());
        return json;
    }
    private JsonObject messageJson(ChatMessage message) {
        JsonObject json = object();
        json.addProperty("id", message.id); json.addProperty("kind", message.kind);
        json.addProperty("agentId", message.agentId); json.addProperty("sender", message.sender);
        json.addProperty("text", message.text); json.addProperty("time", message.time);
        json.addProperty("taskId", message.taskId);
        json.addProperty("replyToMessageId", message.replyToMessageId);
        json.addProperty("replySender", message.replySender); json.addProperty("replyText", message.replyText);
        JsonArray attachments = new JsonArray();
        if (message.attachments != null) message.attachments.forEach(path -> {
            String id = Path.of(path).getFileName().toString();
            JsonObject file = object(); file.addProperty("id", id);
            file.addProperty("name", id.contains("_") ? id.substring(id.indexOf('_') + 1) : id);
            file.addProperty("image", id.toLowerCase().matches(".*\\.(png|jpe?g|gif|webp)"));
            attachments.add(file);
        });
        json.add("attachments", attachments);
        return json;
    }
    private void addRecentMessages(JsonArray target, List<ChatMessage> source, String afterMessageId) {
        int start = Math.max(0, source.size() - 200);
        if (!afterMessageId.isBlank()) {
            for (int index = source.size() - 1; index >= 0; index--) {
                if (source.get(index).id.equals(afterMessageId)) {
                    start = Math.max(start, index + 1);
                    break;
                }
            }
        }
        for (int index = start; index < source.size(); index++)
            target.add(messageJson(source.get(index)));
    }
    private JsonObject taskJson(Task task) {
        JsonObject json = object();
        json.addProperty("id", task.id); json.addProperty("chatId", task.chatId);
        json.addProperty("agentId", task.agentId); json.addProperty("title", task.title);
        json.addProperty("status", task.status); json.addProperty("progress", task.progress);
        json.addProperty("createdAt", task.createdAt); json.addProperty("updatedAt", task.updatedAt);
        return json;
    }
    private JsonArray queueJson(Project project) {
        JsonArray queue = new JsonArray();
        for (Task task : QueuedMessages.list(project)) {
            ChatMessage message = QueuedMessages.userMessage(project, task);
            ChatThread chat = project.chat(task.chatId);
            JsonObject item = object();
            item.addProperty("taskId", task.id);
            item.addProperty("messageId", message == null ? "" : message.id);
            item.addProperty("chatId", task.chatId);
            item.addProperty("chatName", chat == null ? "Conversa removida" : chat.name);
            item.addProperty("agentId", task.agentId);
            item.addProperty("text", message == null ? task.title : message.text);
            String display = message == null ? task.title : message.text;
            if (message != null && (display == null || display.isBlank()) && message.attachments != null)
                display = message.attachments.stream().map(path -> Path.of(path).getFileName().toString())
                        .map(name -> name.contains("_") ? name.substring(name.indexOf('_') + 1) : name)
                        .reduce((first, next) -> first + ", " + next).orElse("Mensagem com anexo");
            item.addProperty("displayText", display);
            item.addProperty("status", task.status);
            item.addProperty("editable", message != null);
            queue.add(item);
        }
        return queue;
    }
    private JsonObject approvalJson(Approval approval) {
        JsonObject json = object();
        json.addProperty("id", approval.id); json.addProperty("taskId", approval.taskId);
        json.addProperty("agentId", approval.agentId); json.addProperty("kind", approval.kind);
        json.addProperty("title", approval.title); json.addProperty("detail", approval.detail);
        json.addProperty("risk", approval.risk); json.addProperty("status", approval.status);
        json.addProperty("recommendedChoice", approval.recommendedChoice);
        JsonArray choices = new JsonArray();
        approval.choices.forEach(choice -> {
            JsonObject item = object(); item.addProperty("title", choice.title);
            item.addProperty("description", choice.description); choices.add(item);
        });
        json.add("choices", choices);
        return json;
    }
    private static String value(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try { return object.get(key).getAsString(); }
        catch (RuntimeException error) { return ""; }
    }
    private static boolean boolValue(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                && object.get(key).getAsBoolean();
    }
    private static int intValue(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException error) { return fallback; }
    }
    private static JsonObject object() { return new JsonObject(); }
    private static MobileBridgeServer.Response json(int status, JsonObject body) {
        return MobileBridgeServer.Response.json(status, body);
    }
    private static MobileBridgeServer.ApiException fail(int status, String message) {
        return new MobileBridgeServer.ApiException(status, message);
    }
}
