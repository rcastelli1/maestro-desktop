package br.com.agenteslocal.terminal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** One persistent PowerShell process with its own working directory and output. */
public final class TerminalSession implements AutoCloseable {
    private static final int MAX_OUTPUT = 200_000;
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");

    private final String id = UUID.randomUUID().toString();
    private final String projectId;
    private final String title;
    private final String marker = "__MAESTRO_CWD_" + id.replace("-", "") + "__";
    private final Process process;
    private final BufferedWriter input;
    private final Consumer<TerminalSession> onChange;
    private final StringBuilder output = new StringBuilder();
    private final AtomicInteger pendingCommands = new AtomicInteger();
    private volatile String currentDirectory;
    private volatile boolean closed;

    public TerminalSession(String projectId, String title, Path folder,
                           Consumer<TerminalSession> onChange) throws IOException {
        if (!Files.isDirectory(folder)) throw new IOException("A pasta do projeto não existe: " + folder);
        this.projectId = projectId;
        this.title = title;
        this.currentDirectory = folder.toAbsolutePath().normalize().toString();
        this.onChange = onChange;
        process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-Command", "-")
                .directory(folder.toFile()).redirectErrorStream(true).start();
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        append("PowerShell · " + currentDirectory + "\n\n");
        Thread outputReader = new Thread(this::readOutput, "maestro-terminal-" + id.substring(0, 8));
        outputReader.setDaemon(true);
        outputReader.start();
    }

    public String id() { return id; }
    public String projectId() { return projectId; }
    public String title() { return title; }
    public String currentDirectory() { return currentDirectory; }
    public boolean running() { return !closed && process.isAlive(); }
    public boolean busy() { return pendingCommands.get() > 0 && running(); }

    public synchronized String output() { return output.toString(); }

    public synchronized void send(String command) throws IOException {
        if (command == null || command.isBlank()) return;
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0)
            throw new IOException("Envie um comando por vez.");
        if (!running()) throw new IOException("Este terminal foi encerrado. Abra outro terminal para continuar.");
        append("PS " + currentDirectory + "> " + command + "\n");
        pendingCommands.incrementAndGet();
        try {
            input.write(command);
            input.newLine();
            input.write("[Console]::Out.WriteLine('" + marker + "' + (Get-Location).Path)");
            input.newLine();
            input.flush();
        } catch (IOException exception) {
            pendingCommands.decrementAndGet();
            throw exception;
        }
    }

    private void readOutput() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = ANSI.matcher(line).replaceAll("");
                if (clean.startsWith(marker)) {
                    String folder = clean.substring(marker.length()).trim();
                    if (!folder.isBlank()) currentDirectory = folder;
                    pendingCommands.updateAndGet(count -> Math.max(0, count - 1));
                    onChange.accept(this);
                } else {
                    append(clean + "\n");
                }
            }
        } catch (IOException exception) {
            if (!closed) append("Não foi possível ler a saída do terminal: " + exception.getMessage() + "\n");
        } finally {
            closed = true;
            pendingCommands.set(0);
            append("\nSessão encerrada.\n");
        }
    }

    private synchronized void append(String text) {
        output.append(text);
        if (output.length() > MAX_OUTPUT) output.delete(0, output.length() - MAX_OUTPUT);
        onChange.accept(this);
    }

    @Override public void close() {
        closed = true;
        try { input.close(); } catch (IOException ignored) { }
        process.destroy();
    }
}
