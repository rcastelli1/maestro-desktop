package br.com.agenteslocal.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkspaceData {
    public List<Project> projects = new ArrayList<>();
    public String activeProjectId = "";
    public String codexPath = "";
    public boolean reduceAnimations = false;

    public void normalize() {
        if (projects == null) projects = new ArrayList<>();
        if (activeProjectId == null) activeProjectId = "";
        if (codexPath == null) codexPath = "";
        for (Project project : projects) project.normalize();
    }

    public Project activeProject() {
        return projects.stream().filter(p -> p.id.equals(activeProjectId)).findFirst().orElse(null);
    }

    public static String id() {
        return UUID.randomUUID().toString();
    }

    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public static final class Project {
        public String id = WorkspaceData.id();
        public String name = "";
        public String description = "";
        public String folder = "";
        public List<Agent> agents = new ArrayList<>();
        /** Kept only to migrate projects saved before conversations were separated. */
        public List<ChatMessage> messages = new ArrayList<>();
        public List<ChatThread> chats = new ArrayList<>();
        public String activeChatId = "";
        public List<Task> tasks = new ArrayList<>();
        public List<ScheduledMessage> scheduledMessages = new ArrayList<>();
        public List<Approval> approvals = new ArrayList<>();
        public List<KnowledgeFile> knowledge = new ArrayList<>();
        public List<McpServer> mcps = new ArrayList<>();
        public String writePolicy = "aprovar";

        public void normalize() {
            if (id == null || id.isBlank()) id = WorkspaceData.id();
            if (name == null) name = "";
            if (description == null) description = "";
            if (folder == null) folder = "";
            if (agents == null) agents = new ArrayList<>();
            if (messages == null) messages = new ArrayList<>();
            if (chats == null) chats = new ArrayList<>();
            if (activeChatId == null) activeChatId = "";
            if (tasks == null) tasks = new ArrayList<>();
            if (scheduledMessages == null) scheduledMessages = new ArrayList<>();
            scheduledMessages.forEach(ScheduledMessage::normalize);
            if (approvals == null) approvals = new ArrayList<>();
            if (knowledge == null) knowledge = new ArrayList<>();
            if (mcps == null) mcps = new ArrayList<>();
            if (!List.of("aprovar", "permitir", "bloquear").contains(writePolicy)) writePolicy = "aprovar";
            for (KnowledgeFile file : knowledge) file.normalize();
            for (McpServer server : mcps) server.normalize();
            for (Approval approval : approvals) approval.normalize();
            if (chats.isEmpty()) {
                ChatThread original = new ChatThread();
                original.name = "Chat principal";
                chats.add(original);
            }
            for (ChatThread chat : chats) chat.normalize();
            ChatThread original = chats.getFirst();
            if (!messages.isEmpty()) {
                for (ChatMessage message : messages) {
                    if (original.messages.stream().noneMatch(existing -> existing.id.equals(message.id)))
                        original.messages.add(message);
                }
                messages.clear();
            }
            if (chat(activeChatId) == null) activeChatId = original.id;
            for (Task task : tasks) {
                if (task.chatId == null || task.chatId.isBlank() || chat(task.chatId) == null)
                    task.chatId = original.id;
                if (task.delegatedFromMessageId == null) task.delegatedFromMessageId = "";
                if (task.followupAgentIds == null) task.followupAgentIds = new ArrayList<>();
                if (task.handoffPlan == null) task.handoffPlan = "";
            }
            for (int agentIndex = 0; agentIndex < agents.size(); agentIndex++) {
                Agent agent = agents.get(agentIndex);
                if (agent.color == null || !agent.color.matches("(?i)#[0-9a-f]{6}"))
                    agent.color = "#6c9ed6";
                if (agent.eyeColor == null || !agent.eyeColor.matches("(?i)#[0-9a-f]{6}"))
                    agent.eyeColor = "#14171b";
                if (agent.visualId == null || agent.visualId.isBlank())
                    agent.visualId = Agent.VISUAL_IDS.get(agentIndex % Agent.VISUAL_IDS.size());
                if (agent.animations == null) agent.animations = new AgentAnimations();
                if (agent.reasoningEffort == null || agent.reasoningEffort.isBlank()) agent.reasoningEffort = "Padrão";
                if (agent.chatSessions == null) agent.chatSessions = new HashMap<>();
                if (agent.sessionId != null && !agent.sessionId.isBlank()) {
                    agent.chatSessions.putIfAbsent(original.id, agent.sessionId);
                    agent.sessionId = "";
                }
            }
        }

        public ChatThread activeChat() {
            if (chats == null || chats.isEmpty()) normalize();
            ChatThread selected = chat(activeChatId);
            return selected == null ? chats.getFirst() : selected;
        }

        public ChatThread chat(String chatId) {
            if (chatId == null) return null;
            return chats.stream().filter(chat -> chat.id.equals(chatId)).findFirst().orElse(null);
        }

        public ChatThread chatFor(Task task) {
            ChatThread selected = task == null ? null : chat(task.chatId);
            return selected == null ? activeChat() : selected;
        }

        public List<ChatMessage> allMessages() {
            return chats.stream().flatMap(chat -> chat.messages.stream()).toList();
        }

        public Agent agent(String agentId) {
            return agents.stream().filter(a -> a.id.equals(agentId)).findFirst().orElse(null);
        }

        public Task task(String taskId) {
            return tasks.stream().filter(t -> t.id.equals(taskId)).findFirst().orElse(null);
        }

        public static Project create(String name, String description, String folder) {
            Project p = new Project();
            p.name = name.trim();
            p.description = description.trim();
            p.folder = folder.trim();
            p.agents.add(Agent.template("Planejador", "Organiza objetivos, tarefas e prioridades.",
                    "Planeje o trabalho em passos claros. Não altere arquivos.", "#6c9ed6", false));
            p.agents.add(Agent.template("Programador", "Escreve e altera código no projeto.",
                    "Implemente mudanças no código do projeto com cuidado e explique o que foi feito.", "#76bb8b", true));
            p.agents.add(Agent.template("Revisor", "Revisa planos e código antes da entrega.",
                    "Revise o trabalho, aponte problemas concretos e proponha correções. Não altere arquivos.", "#d7a352", false));
            p.agents.add(Agent.template("Pesquisador", "Investiga o projeto e resume fontes.",
                    "Pesquise nos arquivos disponíveis e identifique evidências. Não altere arquivos.", "#a88ad2", false));
            p.agents.add(Agent.template("Redator", "Escreve textos e documentação.",
                    "Escreva textos claros e documentação útil para o projeto.", "#d486aa", true));
            p.agents.add(Agent.template("Testador", "Analisa qualidade e falhas.",
                    "Investigue falhas e relate como reproduzi-las. Não altere arquivos.", "#72c3c5", false));
            p.normalize();
            return p;
        }
    }

    public static final class ChatThread {
        public String id = WorkspaceData.id();
        public String name = "Nova conversa";
        public String privateAgentId = "";
        public String createdAt = WorkspaceData.now();
        public List<ChatMessage> messages = new ArrayList<>();

        public void normalize() {
            if (id == null || id.isBlank()) id = WorkspaceData.id();
            if (name == null || name.isBlank()) name = "Nova conversa";
            if (privateAgentId == null) privateAgentId = "";
            if (createdAt == null) createdAt = WorkspaceData.now();
            if (messages == null) messages = new ArrayList<>();
        }

        public boolean isPrivate() {
            return privateAgentId != null && !privateAgentId.isBlank();
        }
    }

    /** A message waiting for a local time or a Codex rate-limit window to renew. */
    public static final class ScheduledMessage {
        public String id = WorkspaceData.id();
        public String chatId = "";
        public String agentId = "";
        public String text = "";
        public String replyToMessageId = "";
        public List<String> attachments = new ArrayList<>();
        public String kind = "time";
        public long dueAt;
        public long createdAt = System.currentTimeMillis();
        public long lastAttemptAt;
        public String lastError = "";

        public void normalize() {
            if (id == null || id.isBlank()) id = WorkspaceData.id();
            if (chatId == null) chatId = "";
            if (agentId == null) agentId = "";
            if (text == null) text = "";
            if (replyToMessageId == null) replyToMessageId = "";
            if (attachments == null) attachments = new ArrayList<>();
            if (kind == null) kind = "time";
            if (lastError == null) lastError = "";
        }
    }

    public static final class Agent {
        public static final List<String> VISUAL_IDS = List.of("orbe", "gota", "cubo", "astro", "nuvem",
                "prisma", "pingo", "folha", "faísca", "hexa");
        public String id = WorkspaceData.id();
        public String name = "";
        public String role = "";
        public String instructions = "";
        public String color = "#6c9ed6";
        public String eyeColor = "#14171b";
        public String visualId = "";
        public AgentAnimations animations = new AgentAnimations();
        public String model = "Padrão do Codex";
        public String reasoningEffort = "Padrão";
        public String sessionId = "";
        public Map<String, String> chatSessions = new HashMap<>();
        public boolean canEditFiles = false;
        public boolean canUseKnowledge = true;
        public String status = "disponível";

        public static Agent template(String name, String role, String instructions, String color, boolean canEditFiles) {
            Agent a = new Agent();
            a.name = name;
            a.role = role;
            a.instructions = instructions;
            a.color = color;
            a.canEditFiles = canEditFiles;
            return a;
        }
    }

    public static final class ChatMessage {
        public String id = WorkspaceData.id();
        public String agentId = "";
        public String sender = "";
        public String text = "";
        public String time = WorkspaceData.now();
        public String kind = "system";
        public String taskId = "";
        public String progressType = "";
        public String replyToMessageId = "";
        public String replySender = "";
        public String replyText = "";
        public List<String> attachments = new ArrayList<>();

        public static ChatMessage of(String kind, String agentId, String sender, String text) {
            ChatMessage m = new ChatMessage();
            m.kind = kind;
            m.agentId = agentId;
            m.sender = sender;
            m.text = text;
            return m;
        }
    }

    public static final class Task {
        public String id = WorkspaceData.id();
        public String chatId = "";
        public String title = "";
        public String agentId = "";
        public String prompt = "";
        public String delegatedFromMessageId = "";
        public int delegationDepth = 0;
        public List<String> followupAgentIds = new ArrayList<>();
        public String handoffPlan = "";
        public List<String> attachments = new ArrayList<>();
        public String status = "Na fila";
        public int progress = 0;
        public String createdAt = WorkspaceData.now();
        public String updatedAt = WorkspaceData.now();
    }

    public static final class Approval {
        public String id = WorkspaceData.id();
        public String taskId = "";
        public String agentId = "";
        public String kind = "permission";
        public String title = "";
        public String detail = "";
        public String actionPreview = "";
        public String risk = "Médio";
        public String status = "Pendente";
        public String createdAt = WorkspaceData.now();
        public List<QuestionChoice> choices = new ArrayList<>();
        public int recommendedChoice = 0;
        public int selectedChoice = 0;
        public String responseDraft = "";
        public String response = "";

        public void normalize() {
            if (id == null || id.isBlank()) id = WorkspaceData.id();
            if (kind == null || kind.isBlank()) kind = "permission";
            if (title == null) title = "";
            if (detail == null) detail = "";
            if (actionPreview == null) actionPreview = "";
            if (risk == null) risk = "Médio";
            if (status == null) status = "Pendente";
            if (choices == null) choices = new ArrayList<>();
            if (responseDraft == null) responseDraft = "";
            if (response == null) response = "";
        }
    }

    public static final class QuestionChoice {
        public String title = "";
        public String description = "";

        public QuestionChoice() { }

        public QuestionChoice(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    public static final class KnowledgeFile {
        public String id = WorkspaceData.id();
        public String name = "";
        public String storedPath = "";
        public String addedAt = WorkspaceData.now();
        public long size = 0;
        public String indexedPath = "";
        public String status = "Indexado";
        public String error = "";
        public int chunkCount = 0;

        public void normalize() {
            if (id == null || id.isBlank()) id = WorkspaceData.id();
            if (name == null) name = "";
            if (storedPath == null) storedPath = "";
            if (indexedPath == null) indexedPath = "";
            if (status == null) status = "Indexado";
            if (error == null) error = "";
        }
    }

    public static final class McpServer {
        public String id = WorkspaceData.id();
        public String name = "";
        public String command = "";
        public String arguments = "";
        public String url = "";
        public boolean enabled = true;

        public void normalize() {
            if (id == null || id.isBlank()) id = WorkspaceData.id();
            if (name == null) name = "";
            if (command == null) command = "";
            if (arguments == null) arguments = "";
            if (url == null) url = "";
        }
    }
}
