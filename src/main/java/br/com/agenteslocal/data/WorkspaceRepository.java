package br.com.agenteslocal.data;

import br.com.agenteslocal.model.WorkspaceData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class WorkspaceRepository {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path home;
    private final Path dataFile;

    public WorkspaceRepository() {
        String override = System.getenv("AGENTES_LOCAL_HOME");
        Path selectedHome = override == null || override.isBlank()
                ? Path.of(System.getProperty("user.home"), ".agentes-local")
                : Path.of(override);
        home = selectedHome;
        dataFile = home.resolve("workspace.json");
    }

    WorkspaceRepository(Path home) {
        this.home = home;
        dataFile = home.resolve("workspace.json");
    }

    public WorkspaceData load() throws IOException {
        Files.createDirectories(home);
        if (!Files.exists(dataFile)) return new WorkspaceData();
        try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            WorkspaceData data = gson.fromJson(reader, WorkspaceData.class);
            if (data == null) data = new WorkspaceData();
            data.normalize();
            for (WorkspaceData.Project project : data.projects) {
                project.agents.forEach(agent -> agent.status = "disponível");
                project.tasks.stream().filter(task -> task.status.equals("Em andamento"))
                        .forEach(task -> task.status = "Interrompida");
                project.tasks.stream().filter(task -> task.status.equals("Interrompendo"))
                        .forEach(task -> task.status = "Cancelada");
            }
            return data;
        }
    }

    public void save(WorkspaceData data) throws IOException {
        Files.createDirectories(home);
        Path temporary = home.resolve("workspace.json.tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        }
        try {
            Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path storeAttachment(String projectId, Path source) throws IOException {
        return copyInto(projectId, "attachments", source);
    }

    public Path storeAttachment(String projectId, String filename, byte[] content) throws IOException {
        Path directory = home.resolve("projects").resolve(projectId).resolve("attachments");
        Files.createDirectories(directory);
        String safeName = filename.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        Path destination = directory.resolve(WorkspaceData.id() + "_" + safeName);
        return Files.write(destination, content);
    }

    public void discardAttachment(String projectId, Path attachment) throws IOException {
        Path directory = home.resolve("projects").resolve(projectId).resolve("attachments")
                .toAbsolutePath().normalize();
        Path target = attachment.toAbsolutePath().normalize();
        if (!target.startsWith(directory)) throw new IOException("Anexo fora da pasta do projeto");
        Files.deleteIfExists(target);
    }

    public Path storeKnowledge(String projectId, Path source) throws IOException {
        return copyInto(projectId, "knowledge", source);
    }

    private Path copyInto(String projectId, String area, Path source) throws IOException {
        Path directory = home.resolve("projects").resolve(projectId).resolve(area);
        Files.createDirectories(directory);
        String safeName = source.getFileName().toString().replaceAll("[^\\p{L}\\p{N}._-]", "_");
        Path destination = directory.resolve(WorkspaceData.id() + "_" + safeName);
        Files.copy(source, destination);
        return destination;
    }

    public Path home() {
        return home;
    }

    public void exportBackup(Path destination, char[] password) throws IOException {
        Path output = destination.toAbsolutePath().normalize();
        BackupCrypto.encrypt(output, password, encrypted -> {
            ZipOutputStream zip = new ZipOutputStream(encrypted);
            try (var files = Files.walk(home)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    if (Files.isSymbolicLink(file) || file.toAbsolutePath().normalize().equals(output)) continue;
                    String name = home.relativize(file).toString().replace('\\', '/');
                    // Only project data is portable. Never export pairing keys, Firebase sessions,
                    // notification tokens, local access tokens, or in-flight request markers.
                    if (!name.equals("workspace.json") && !name.startsWith("projects/")) continue;
                    if (name.endsWith(".tmp")) continue;
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
                zip.finish();
                zip.flush();
            }
        });
    }
}
