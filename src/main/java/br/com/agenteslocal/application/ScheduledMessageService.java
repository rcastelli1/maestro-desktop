package br.com.agenteslocal.application;

import br.com.agenteslocal.data.WorkspaceRepository;
import br.com.agenteslocal.model.WorkspaceData;
import br.com.agenteslocal.model.WorkspaceData.Project;
import br.com.agenteslocal.model.WorkspaceData.ScheduledMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Shared scheduling use case for the desktop UI and mobile API. */
public final class ScheduledMessageService {
    private final WorkspaceRepository repository;
    private final Supplier<WorkspaceData> workspace;

    public ScheduledMessageService(WorkspaceRepository repository, Supplier<WorkspaceData> workspace) {
        this.repository = repository;
        this.workspace = workspace;
    }

    public void schedule(Project project, ScheduledMessage message) throws IOException {
        validate(project, message);
        project.scheduledMessages.add(message);
        try {
            repository.save(workspace.get());
        } catch (IOException | RuntimeException error) {
            project.scheduledMessages.remove(message);
            throw error;
        }
    }

    public boolean cancel(Project project, ScheduledMessage message,
                          Predicate<String> usedOutsideWorkspace) throws IOException {
        int position = project.scheduledMessages.indexOf(message);
        if (position < 0) return false;
        project.scheduledMessages.remove(position);
        try {
            repository.save(workspace.get());
        } catch (IOException | RuntimeException error) {
            project.scheduledMessages.add(position, message);
            throw error;
        }
        for (String path : message.attachments) {
            boolean used = project.allMessages().stream().anyMatch(item -> item.attachments != null
                    && item.attachments.contains(path))
                    || project.scheduledMessages.stream().anyMatch(item -> item.attachments.contains(path))
                    || usedOutsideWorkspace.test(path);
            if (!used) try {
                repository.discardAttachment(project.id, Path.of(path));
            } catch (IOException ignored) {
                // The message is already cancelled. A failed cleanup must not restore it.
            }
        }
        return true;
    }

    private void validate(Project project, ScheduledMessage message) throws IOException {
        if (project.chat(message.chatId) == null || project.agent(message.agentId) == null)
            throw new IOException("Conversa ou agente não encontrado.");
        String text = message.text == null ? "" : message.text.trim();
        List<String> files = message.attachments == null ? List.of() : message.attachments;
        if ((text.isBlank() && files.isEmpty()) || text.length() > 20_000)
            throw new IOException("Escreva uma mensagem ou anexe um arquivo; use até 20 mil caracteres.");
        if (files.size() > 4) throw new IOException("Use no máximo quatro anexos.");
        long bytes = 0;
        for (String path : files) {
            if (path == null || !Files.isRegularFile(Path.of(path)))
                throw new IOException("Um anexo não está disponível.");
            bytes += Files.size(Path.of(path));
            if (bytes > 8_000_000) throw new IOException("Os anexos devem somar até 8 MB.");
        }
        if (!List.of("time", "shortReset", "weeklyReset").contains(message.kind))
            throw new IOException("Tipo de agendamento inválido.");
        if ("time".equals(message.kind) && message.dueAt <= System.currentTimeMillis())
            throw new IOException("Escolha uma data e horário futuros.");
    }
}
