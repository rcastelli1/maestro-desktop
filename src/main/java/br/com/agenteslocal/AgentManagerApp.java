package br.com.agenteslocal;

import br.com.agenteslocal.application.ScheduledMessageService;
import br.com.agenteslocal.codex.CodexService;
import br.com.agenteslocal.character.AgentCharacterView;
import br.com.agenteslocal.chat.ChatMentionMenu;
import br.com.agenteslocal.chat.ChatMentions;
import br.com.agenteslocal.chat.QueuedMessages;
import br.com.agenteslocal.chat.SelectableMessageText;
import br.com.agenteslocal.codex.CodexConnectionService;
import br.com.agenteslocal.codex.CodexUsageService;
import br.com.agenteslocal.codex.AgentHandoff;
import br.com.agenteslocal.codex.PluginService;
import br.com.agenteslocal.codex.SharedProjectContext;
import br.com.agenteslocal.data.WorkspaceRepository;
import br.com.agenteslocal.git.GitService;
import br.com.agenteslocal.git.DiffParser;
import br.com.agenteslocal.knowledge.KnowledgeService;
import br.com.agenteslocal.model.WorkspaceData;
import br.com.agenteslocal.model.AgentAnimations;
import br.com.agenteslocal.model.WorkspaceData.Agent;
import br.com.agenteslocal.model.WorkspaceData.Approval;
import br.com.agenteslocal.model.WorkspaceData.ChatMessage;
import br.com.agenteslocal.model.WorkspaceData.ChatThread;
import br.com.agenteslocal.model.WorkspaceData.KnowledgeFile;
import br.com.agenteslocal.model.WorkspaceData.McpServer;
import br.com.agenteslocal.model.WorkspaceData.Project;
import br.com.agenteslocal.model.WorkspaceData.ScheduledMessage;
import br.com.agenteslocal.model.WorkspaceData.QuestionChoice;
import br.com.agenteslocal.model.WorkspaceData.Task;
import br.com.agenteslocal.mobile.MobileApi;
import br.com.agenteslocal.mobile.MobileBridgeServer;
import br.com.agenteslocal.mobile.FirebaseRemoteBridge;
import br.com.agenteslocal.mobile.PushNotificationService;
import br.com.agenteslocal.mobile.FirebaseRemoteClient;
import br.com.agenteslocal.mobile.FirebaseRemoteSettings;
import br.com.agenteslocal.terminal.TerminalSession;
import br.com.agenteslocal.window.WindowsTitleBar;
import br.com.agenteslocal.window.MiniPlayer;
import br.com.agenteslocal.window.UiIcons;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.geometry.Rectangle2D;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AgentManagerApp extends Application {
    private static final int MAX_AUTOMATIC_HANDOFFS = 6;
    private final WorkspaceRepository repository = new WorkspaceRepository();
    private final KnowledgeService knowledgeService = new KnowledgeService();
    private final CodexService codexService = new CodexService();
    private final SharedProjectContext sharedProjectContext = new SharedProjectContext();
    private final CodexConnectionService connectionService = new CodexConnectionService();
    private final CodexUsageService usageService = new CodexUsageService();
    private final PluginService pluginService = new PluginService();
    private final GitService gitService = new GitService();
    private final DiffParser diffParser = new DiffParser();
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final ExecutorService scheduleWorker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "maestro-scheduled-usage");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService pluginWorkers = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "maestro-plugin-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, CompletableFuture<Void>> taskRuns = new HashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    private final Map<String, ChatMessage> replyDrafts = new HashMap<>();
    private final Map<String, List<String>> pendingAttachments = new HashMap<>();
    private final Map<String, List<TerminalSession>> terminalsByProject = new HashMap<>();
    private final Map<String, String> selectedTerminalByProject = new HashMap<>();
    private final Map<String, Integer> terminalNumbers = new HashMap<>();
    private final AtomicBoolean terminalRefreshQueued = new AtomicBoolean();
    private final AtomicLong terminalUpdateVersion = new AtomicLong();
    private final Map<String, Circle> terminalTabDots = new HashMap<>();
    private WorkspaceData data;
    private final ScheduledMessageService scheduledMessages = new ScheduledMessageService(repository, () -> data);
    private Stage stage;
    private MiniPlayer miniPlayer;
    private MobileBridgeServer mobileBridge;
    private String mobileBridgeError = "";
    private MobileApi mobileApi;
    private PushNotificationService pushNotifications;
    private FirebaseRemoteSettings remoteSettings;
    private FirebaseRemoteBridge remoteBridge;
    private String remoteBridgeError = "";
    private boolean remoteLoginBusy;
    private BorderPane root;
    private String screen = "projects";
    private String selectedAgentId = "";
    private String selectedFile = "";
    private String filesProjectId = "";
    private String fileDiffMode = "Unificado";
    private double fileChangesScrollPosition = 0;
    private final List<String> openFiles = new ArrayList<>();
    private final Set<String> expandedFileFolders = new HashSet<>();
    private final Set<String> shownChatMessages = new HashSet<>();
    private final List<Animation> chatAnimations = new ArrayList<>();
    private final Map<String, Long> chatProgressStartedAt = new HashMap<>();
    private String lastChatId = "";
    private VBox chatTimeline;
    private VBox chatUsageHost;
    private ScrollPane chatHistory;
    private TextArea terminalOutputView;
    private Label terminalDirectoryView;
    private Label terminalStatusView;
    private TextField terminalCommandView;
    private Button terminalRunView;
    private String terminalViewSessionId = "";
    private String agentFilter = "Todos";
    private String taskFilter = "Tudo";
    private String pluginFilter = "Todos";
    private PluginService.Listing pluginListing;
    private String pluginListingExecutable = "";
    private String pluginListingError = "";
    private long pluginListingUpdatedAt;
    private boolean pluginLoading;
    private String settingsSection = "Segurança e permissões";
    private String selectedMcpId = "";
    private CodexUsageService.UsageSnapshot usageSnapshot;
    private String usageError = "";
    private String usageExecutable = "";
    private long usageLastFetchedAt;
    private boolean usageLoading;
    private boolean resetApplying;
    private Timeline scheduledTimer;
    private boolean scheduledUsageLoading;
    private long scheduledUsageLastAttemptAt;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        try (InputStream sora = getClass().getResourceAsStream("/fonts/Sora.ttf");
             InputStream mono = getClass().getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf")) {
            if (sora != null) Font.loadFont(sora, 14);
            if (mono != null) Font.loadFont(mono, 11);
        } catch (IOException error) {
            System.err.println("Maestro: fontes do mini player indisponíveis: " + error.getMessage());
        }
        try {
            data = repository.load();
        } catch (IOException e) {
            data = new WorkspaceData();
            alert("Dados locais", "Não foi possível carregar os dados: " + e.getMessage());
        }
        codexService.setConfiguredExecutable(data.codexPath);
        try {
            remoteSettings = FirebaseRemoteSettings.load(repository.home());
            if (!remoteSettings.configured()) {
                Path configFile = Path.of(System.getProperty("user.home"), "Projetos", "Pessoais",
                        "maestro-mobile", "src", "firebaseConfig.local.ts");
                if (Files.isRegularFile(configFile)) remoteSettings.importWebConfig(configFile);
            }
        }
        catch (IOException error) { remoteBridgeError = error.getMessage(); }
        try { pushNotifications = new PushNotificationService(repository.home()); }
        catch (IOException error) { mobileBridgeError = error.getMessage(); }
        try {
            mobileApi = new MobileApi(() -> data, repository, new MobileApi.Actions() {
                @Override public boolean send(Project project, ChatThread chat, Agent agent, String text,
                                              ChatMessage reply, List<String> attachments) {
                    return sendChat(project, chat, agent, text, reply, true, attachments);
                }
                @Override public void approve(Project project, Approval approval, boolean accept) {
                    if (accept) AgentManagerApp.this.approve(project, approval);
                    else reject(project, approval);
                }
                @Override public void answer(Project project, Approval question, int choice, String response, boolean skip) {
                    question.selectedChoice = choice;
                    question.responseDraft = response;
                    answerQuestion(project, question, skip, true);
                }
                @Override public void stop(Project project, Task task, boolean next) {
                    stopTask(project, task, next);
                }
                @Override public boolean editQueued(Project project, Task task, String text) {
                    return editQueuedMessage(project, task, text);
                }
                @Override public boolean deleteQueued(Project project, Task task) {
                    return deleteQueuedMessage(project, task);
                }
                @Override public boolean moveQueued(Project project, Task task, String direction) {
                    return moveQueuedMessage(project, task, direction);
                }
                @Override public void changed(Project project) {
                    if (data.activeProjectId.equals(project.id)) show(screen);
                }
                @Override public void registerPush(String token) throws IOException {
                    if (pushNotifications == null) throw new IOException("Notificações indisponíveis neste computador.");
                    pushNotifications.register(token);
                }
                @Override public void unregisterPush(String token) throws IOException {
                    if (pushNotifications != null) pushNotifications.unregister(token);
                }
            });
            mobileBridge = new MobileBridgeServer(repository.home(), mobileApi);
            mobileBridge.start();
            if (!mobileBridge.running()) mobileBridgeError = mobileBridge.error();
        } catch (IOException error) {
            mobileBridgeError = error.getMessage();
        }
        root = new BorderPane();
        root.getStyleClass().add("app-root");
        Scene scene = new Scene(root, 1440, 860);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setTitle("maestro");
        stage.getIcons().add(new Image(getClass().getResource("/brand/maestro-icon.png").toExternalForm()));
        stage.setMinWidth(1020);
        stage.setMinHeight(680);
        stage.setScene(scene);
        show(data.activeProject() == null ? "projects" : "overview");
        stage.show();
        WindowsTitleBar.apply(stage.getTitle());
        miniPlayer = new MiniPlayer(stage, this::miniPlayerEntries, () -> !data.reduceAnimations,
                this::restoreMainWindow,
                entry -> {
                    Project current = project();
                    Task task = current == null ? null : current.task(entry.taskId());
                    if (task != null) stopTask(current, task, false);
                }, entry -> {
                    Project current = project();
                    Agent agent = current == null ? null : current.agent(entry.agentId());
                    if (agent != null) resumeAgentQueue(current, agent);
                }, getClass().getResource("/app.css").toExternalForm());
        stage.requestFocus();
        stage.focusedProperty().addListener((obs, oldValue, newValue) -> Platform.runLater(this::syncMiniPlayer));
        stage.iconifiedProperty().addListener((obs, oldValue, newValue) -> Platform.runLater(this::syncMiniPlayer));
        stage.showingProperty().addListener((obs, oldValue, newValue) -> Platform.runLater(this::syncMiniPlayer));
        Platform.runLater(this::resumeQueuedTasks);
        scheduledTimer = new Timeline(new KeyFrame(Duration.seconds(10), event -> processScheduledMessages()));
        scheduledTimer.setCycleCount(Animation.INDEFINITE);
        scheduledTimer.play();
        Platform.runLater(this::processScheduledMessages);
        if (remoteSettings != null && remoteSettings.signedIn()) restoreRemoteBridge();
    }

    @Override
    public void stop() {
        if (scheduledTimer != null) scheduledTimer.stop();
        if (mobileBridge != null) mobileBridge.close();
        if (remoteBridge != null) remoteBridge.close();
        if (pushNotifications != null) pushNotifications.close();
        if (miniPlayer != null) miniPlayer.close();
        terminalsByProject.values().forEach(sessions -> sessions.forEach(TerminalSession::close));
        workers.shutdownNow();
        scheduleWorker.shutdownNow();
        pluginWorkers.shutdownNow();
    }

    private Project project() {
        return data.activeProject();
    }

    private void save() {
        try {
            repository.save(data);
        } catch (IOException e) {
            alert("Salvar dados", "Não foi possível salvar: " + e.getMessage());
        }
        if (miniPlayer != null) {
            Runnable refreshPlayer = () -> {
                if (miniPlayer != null && miniPlayer.isShowing()) miniPlayer.refresh();
            };
            if (Platform.isFxApplicationThread()) refreshPlayer.run();
            else Platform.runLater(refreshPlayer);
        }
    }

    private List<MiniPlayer.Entry> miniPlayerEntries() {
        Project current = project();
        if (current == null) return List.of();
        List<MiniPlayer.Entry> entries = new ArrayList<>();
        for (Agent agent : current.agents) {
            Task active = current.tasks.stream().filter(task -> agent.id.equals(task.agentId)
                    && List.of("Em andamento", "Interrompendo").contains(task.status)).findFirst().orElse(null);
            boolean resumable = "disponível".equals(agent.status) && current.tasks.stream().anyMatch(task -> agent.id.equals(task.agentId)
                    && "Pausada".equals(task.status));
            String activity = active == null ? "Disponível · " + current.name : active.title;
            entries.add(new MiniPlayer.Entry(agent.id, agent.visualId, agent.color, agent.eyeColor,
                    agent.animations, agent.name, activity,
                    active == null ? "" : active.id, active != null && "Em andamento".equals(active.status),
                    resumable));
        }
        entries.sort((first, second) -> Boolean.compare(second.running(), first.running()));
        if (entries.stream().anyMatch(MiniPlayer.Entry::running))
            return entries.stream().filter(MiniPlayer.Entry::running).toList();
        if (entries.stream().anyMatch(MiniPlayer.Entry::resumable))
            return entries.stream().filter(MiniPlayer.Entry::resumable).toList();
        return entries;
    }

    private void syncMiniPlayer() {
        if (miniPlayer == null) return;
        boolean childDialogOpen = Window.getWindows().stream().anyMatch(window ->
                window.isShowing() && window instanceof Stage child && child.getOwner() == stage);
        if (stage.isShowing() && (stage.isIconified() || !stage.isFocused()) && !childDialogOpen)
            miniPlayer.show(stage);
        else miniPlayer.hide();
    }

    private void restoreMainWindow() {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
        miniPlayer.hide();
    }

    private void show(String nextScreen) {
        String previousScreen = screen;
        Map<String, Double> previousChatPositions = new HashMap<>();
        boolean composerFocused = false;
        int composerCaret = 0;
        if (previousScreen.equals("chat") && nextScreen.equals("chat") && chatTimeline != null) {
            for (Node entry : chatTimeline.getChildren()) {
                if (entry.getUserData() instanceof String key && entry.getScene() != null)
                    previousChatPositions.put(key, entry.localToScene(0, 0).getY());
            }
            Node focus = root.getScene() == null ? null : root.getScene().getFocusOwner();
            if (focus instanceof TextField field && field.getStyleClass().contains("chat-input")) {
                composerFocused = true;
                composerCaret = field.getCaretPosition();
            }
        }
        screen = nextScreen;
        Project project = project();
        if (project == null && !nextScreen.equals("projects")) {
            screen = "projects";
        }
        if (previousScreen.equals("chat") && !screen.equals("chat")) stopChatAnimations();
        root.setLeft(sidebar());
        Node content = switch (screen) {
            case "overview" -> overview();
            case "chat" -> chat();
            case "agents" -> agents();
            case "tasks" -> tasks();
            case "files" -> files();
            case "terminals" -> terminals();
            case "knowledge" -> knowledge();
            case "integrations", "plugins" -> plugins();
            case "mcps" -> mcps();
            case "models" -> models();
            case "settings" -> settings();
            default -> projects();
        };
        root.setCenter(content);
        if (screen.equals("chat") && previousScreen.equals("chat")) {
            boolean restoreFocus = composerFocused;
            int caret = composerCaret;
            Platform.runLater(() -> {
                if (restoreFocus && root.lookup(".chat-input") instanceof TextField input) {
                    input.requestFocus();
                    input.positionCaret(Math.min(caret, input.getLength()));
                }
                animateChatShift(previousChatPositions);
            });
        }
        if (data.reduceAnimations || previousScreen.equals(screen)) return;
        FadeTransition fade = new FadeTransition(Duration.millis(180), content);
        fade.setFromValue(0.55);
        fade.setToValue(1);
        fade.play();
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), content);
        slide.setFromY(10);
        slide.setToY(0);
        slide.play();
    }

    private void animateChatShift(Map<String, Double> previousPositions) {
        if (data.reduceAnimations || previousPositions.isEmpty() || chatTimeline == null) return;
        root.applyCss();
        root.layout();
        double referenceY = Double.NEGATIVE_INFINITY;
        double offset = 0;
        for (Node entry : chatTimeline.getChildren()) {
            if (!(entry.getUserData() instanceof String key) || entry.getScene() == null) continue;
            Double previousY = previousPositions.get(key);
            if (previousY == null || previousY < 60 || previousY > root.getHeight() - 95) continue;
            if (previousY > referenceY) {
                referenceY = previousY;
                offset = previousY - entry.localToScene(0, 0).getY();
            }
        }
        if (Math.abs(offset) < 2 || Math.abs(offset) > 380) return;
        chatTimeline.setTranslateY(offset);
        TranslateTransition shift = new TranslateTransition(Duration.millis(310), chatTimeline);
        shift.setFromY(offset);
        shift.setToY(0);
        shift.setInterpolator(Interpolator.EASE_OUT);
        shift.play();
        chatAnimations.add(shift);
    }

    private VBox sidebar() {
        VBox bar = new VBox(4);
        bar.getStyleClass().add("sidebar");
        bar.setPrefWidth(252);
        bar.setMinWidth(230);

        ImageView logo = new ImageView(new Image(getClass().getResource("/brand/maestro-icon.png").toExternalForm()));
        logo.setFitWidth(27);
        logo.setFitHeight(27);
        logo.setPreserveRatio(true);
        Label title = label("maestro", "brand-title");
        HBox brand = row(12, logo, title);
        brand.getStyleClass().add("brand");
        bar.getChildren().add(brand);

        bar.getChildren().add(label("PROJETO", "eyebrow"));
        Project project = project();
        Button projectButton = button(project == null ? "+ Escolher projeto" : project.name, "project-selector", () -> show("projects"));
        projectButton.setMaxWidth(Double.MAX_VALUE);
        bar.getChildren().add(projectButton);
        bar.getChildren().add(spacer(8));

        if (project != null) {
            bar.getChildren().addAll(nav("layout-dashboard", "Visão geral", "overview"),
                    nav("message-circle", "Chat", "chat"));
            if (screen.equals("chat")) bar.getChildren().add(chatThreadList(project));
            bar.getChildren().addAll(
                    nav("bot", "Agentes", "agents"),
                    nav("list-todo", "Tarefas", "tasks"),
                    nav("files", "Arquivos", "files"),
                    nav("terminal", "Terminais", "terminals"),
                    nav("database", "Conhecimento", "knowledge"),
                    nav("plug-zap", "MCPs e plugins", "plugins"),
                    nav("cpu", "Modelos", "models"));
        }
        bar.getChildren().add(nav("settings", "Configurações", "settings"));
        Region fill = new Region();
        VBox.setVgrow(fill, Priority.ALWAYS);
        bar.getChildren().add(fill);
        bar.getChildren().add(label("OUTROS PROJETOS", "eyebrow"));
        for (Project other : data.projects) {
            if (project != null && other.id.equals(project.id)) continue;
            Button b = button(other.name, "sidebar-project", () -> {
                data.activeProjectId = other.id;
                selectedAgentId = "";
                selectedFile = "";
                save();
                show("overview");
            });
            b.setMaxWidth(Double.MAX_VALUE);
            bar.getChildren().add(b);
        }
        if (data.projects.isEmpty()) bar.getChildren().add(label("Nenhum projeto ainda", "muted"));
        HBox status = row(8, dot(codexService.available() ? "#76bb8b" : "#d7a352"),
                label(codexService.available() ? "Codex disponível" : "Codex não encontrado", "sidebar-status"));
        status.getStyleClass().add("sidebar-footer");
        bar.getChildren().add(status);
        return bar;
    }

    private Node chatThreadList(Project project) {
        VBox section = new VBox(3);
        section.getStyleClass().add("chat-thread-section");
        Button create = button("+", "chat-thread-create", () -> createChat(project));
        create.setTooltip(new Tooltip("Criar conversa em grupo"));
        section.getChildren().add(row(5, label("CONVERSAS", "eyebrow"), grow(), create));
        VBox entries = new VBox(3);
        long groupCount = project.chats.stream().filter(chat -> !chat.isPrivate()).count();
        for (ChatThread chat : project.chats) {
            if (chat.isPrivate()) continue;
            Button open = button(shorten(chat.name, 23),
                    chat.id.equals(project.activeChatId) ? "chat-thread-button selected" : "chat-thread-button",
                    () -> selectChat(project, chat));
            open.setGraphic(UiIcons.icon("message-circle", 14));
            open.setTooltip(new Tooltip(chat.name));
            open.setMinWidth(0);
            open.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(open, Priority.ALWAYS);
            Button rename = button("✎", "chat-thread-rename", () -> renameChat(project, chat));
            rename.setTooltip(new Tooltip("Renomear conversa"));
            HBox line = row(2, open, rename);
            line.getStyleClass().add("chat-thread-row");
            entries.getChildren().add(line);
        }
        if (groupCount == 0) entries.getChildren().add(label("Nenhuma conversa em grupo", "tiny-muted"));
        Label individualTitle = label("INDIVIDUAIS", "eyebrow");
        VBox.setMargin(individualTitle, new Insets(10, 0, 4, 0));
        entries.getChildren().add(individualTitle);
        for (Agent agent : project.agents) {
            ChatThread privateChat = project.chats.stream()
                    .filter(chat -> agent.id.equals(chat.privateAgentId)).findFirst().orElse(null);
            Button open = button(shorten(agent.name, 22),
                    privateChat != null && privateChat.id.equals(project.activeChatId)
                            ? "chat-thread-button selected" : "chat-thread-button",
                    () -> openPrivateChat(project, agent));
            open.setGraphic(smallAvatar(agent, 9));
            open.setTooltip(new Tooltip("Conversa individual com " + agent.name));
            open.setMaxWidth(Double.MAX_VALUE);
            entries.getChildren().add(open);
        }
        for (ChatThread chat : project.chats) {
            if (!chat.isPrivate() || project.agent(chat.privateAgentId) != null) continue;
            Button open = button(shorten(chat.name + " (agente removido)", 27),
                    chat.id.equals(project.activeChatId) ? "chat-thread-button selected" : "chat-thread-button",
                    () -> selectChat(project, chat));
            open.setMaxWidth(Double.MAX_VALUE);
            entries.getChildren().add(open);
        }
        ScrollPane list = new ScrollPane(entries);
        list.setFitToWidth(true);
        list.setPrefHeight(Math.min(260, groupCount * 37 + project.agents.size() * 35 + 44));
        list.setMaxHeight(260);
        list.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        list.getStyleClass().add("chat-thread-scroll");
        section.getChildren().add(list);
        return section;
    }

    private void createChat(Project project) {
        Dialog<ButtonType> prompt = dialog("Nova conversa");
        long groupCount = project.chats.stream().filter(chat -> !chat.isPrivate()).count();
        TextField name = field("Nova conversa " + (groupCount + 1), "Nome da conversa");
        prompt.getDialogPane().setContent(new VBox(8, label("Nome", "row-title"), name));
        if (prompt.showAndWait().orElse(ButtonType.CANCEL).getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
        String title = name.getText().trim();
        if (title.isBlank()) { alert("Conversa", "Informe um nome para a conversa."); return; }
        if (project.chats.stream().anyMatch(chat -> !chat.isPrivate() && chat.name.equalsIgnoreCase(title))) {
            alert("Conversa", "Já existe uma conversa com esse nome.");
            return;
        }
        ChatThread chat = new ChatThread();
        chat.name = title;
        project.chats.add(chat);
        selectChat(project, chat);
    }

    private void openPrivateChat(Project project, Agent agent) {
        ChatThread chat = project.chats.stream()
                .filter(existing -> agent.id.equals(existing.privateAgentId)).findFirst().orElse(null);
        if (chat == null) {
            chat = new ChatThread();
            chat.name = "Conversa com " + agent.name;
            chat.privateAgentId = agent.id;
            project.chats.add(chat);
        }
        selectChat(project, chat);
    }

    private void renameChat(Project project, ChatThread chat) {
        Dialog<ButtonType> prompt = dialog("Renomear conversa");
        TextField name = field(chat.name, "Nome da conversa");
        prompt.getDialogPane().setContent(new VBox(8, label("Nome", "row-title"), name));
        if (prompt.showAndWait().orElse(ButtonType.CANCEL).getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
        String title = name.getText().trim();
        if (title.isBlank()) { alert("Conversa", "Informe um nome para a conversa."); return; }
        if (project.chats.stream().anyMatch(other -> other != chat && other.name.equalsIgnoreCase(title))) {
            alert("Conversa", "Já existe uma conversa com esse nome.");
            return;
        }
        chat.name = title;
        save();
        show("chat");
    }

    private void selectChat(Project project, ChatThread chat) {
        project.activeChatId = chat.id;
        selectedAgentId = "";
        save();
        show("chat");
    }

    private Button nav(String icon, String title, String page) {
        Label caption = label(title, "nav-text");
        caption.setWrapText(false);
        HBox content = row(8, UiIcons.icon(icon, 18), caption);
        Project project = project();
        if (project != null && (page.equals("chat") || page.equals("tasks"))) {
            long count = project.approvals.stream().filter(approval -> approval.status.equals("Pendente")
                    && (page.equals("tasks") || ("question".equals(approval.kind)
                    && project.activeChat().id.equals(project.chatFor(project.task(approval.taskId)).id)))).count();
            if (count > 0) {
                content.getChildren().addAll(grow(), label(String.valueOf(count),
                        "nav-count", page.equals("chat") ? "chat-count" : "tasks-count"));
                content.setPrefWidth(210);
            }
        }
        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(34);
        button.setPrefHeight(34);
        button.setMaxHeight(34);
        button.getStyleClass().add("nav-button");
        if (screen.equals(page) || (page.equals("plugins") && screen.equals("mcps"))) button.getStyleClass().add("selected");
        button.setOnAction(event -> show(page));
        return button;
    }

    private Node projects() {
        VBox page = page();
        ImageView banner = new ImageView(new Image(getClass().getResource("/brand/logo-principal.png").toExternalForm()));
        banner.setViewport(new Rectangle2D(250, 247, 830, 215));
        banner.setFitWidth(580);
        banner.setPreserveRatio(true);
        HBox brandBanner = row(0, banner);
        brandBanner.getStyleClass().add("maestro-banner");
        page.getChildren().add(brandBanner);
        HBox header = row(16, heading("Projetos", "Selecione uma pasta de trabalho e reúna agentes, conversas e arquivos."),
                grow(), button("+ Novo projeto", "primary-button", () -> editProject(null)));
        page.getChildren().add(header);
        if (data.projects.isEmpty()) {
            VBox empty = card();
            empty.getStyleClass().add("empty-large");
            empty.getChildren().addAll(label("Seu espaço de trabalho começa aqui", "section-title"),
                    label("Crie um projeto e escolha a pasta onde os agentes vão trabalhar.", "muted"),
                    button("Criar primeiro projeto", "primary-button", () -> editProject(null)));
            page.getChildren().add(empty);
        } else {
            FlowPane grid = new FlowPane(16, 16);
            for (Project project : data.projects) {
                VBox card = card();
                card.setPrefWidth(350);
                card.setMinHeight(190);
                card.getChildren().addAll(label(project.name, "card-title"),
                        label(project.description.isBlank() ? "Sem descrição" : project.description, "muted"),
                        label(project.folder.isBlank() ? "Pasta ainda não vinculada" : project.folder, "tiny-muted"),
                        growV(), row(8, badge(project.agents.size() + " agentes"),
                                badge(project.tasks.size() + " tarefas"), grow(),
                                button("Abrir", "outline-button", () -> {
                                    data.activeProjectId = project.id;
                                    save();
                                    show("overview");
                                })));
                grid.getChildren().add(card);
            }
            page.getChildren().add(grid);
        }
        return scroll(page);
    }

    private void editProject(Project existing) {
        if (existing != null && existing.agents.stream().anyMatch(agent -> !agent.status.equals("disponível"))) {
            alert("Projeto em uso", "Aguarde os agentes terminarem antes de alterar a pasta do projeto.");
            return;
        }
        Dialog<ButtonType> dialog = dialog(existing == null ? "Novo projeto" : "Editar projeto");
        TextField name = field(existing == null ? "" : existing.name, "Nome do projeto");
        TextArea description = area(existing == null ? "" : existing.description, "Objetivo do projeto", 3);
        TextField folder = field(existing == null ? "" : existing.folder, "Pasta no computador");
        Button browse = button("Escolher pasta", "outline-button", () -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Selecione a pasta do projeto");
            java.io.File selected = chooser.showDialog(stage);
            if (selected != null) folder.setText(selected.getAbsolutePath());
        });
        VBox form = new VBox(12, label("Nome", "field-label"), name,
                label("Descrição", "field-label"), description,
                label("Pasta de trabalho", "field-label"), folder, browse);
        form.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(form);
        dialog.showAndWait().ifPresent(result -> {
            if (result.getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
            if (name.getText().isBlank()) { alert("Projeto", "Informe um nome para o projeto."); return; }
            String path = folder.getText().trim();
            if (!path.isBlank() && !Files.isDirectory(Path.of(path))) {
                alert("Pasta inválida", "Selecione uma pasta que existe no computador.");
                return;
            }
            if (existing == null) {
                Project created = Project.create(name.getText(), description.getText(), path);
                data.projects.add(created);
                data.activeProjectId = created.id;
            } else {
                if (!existing.folder.equals(path)) {
                    existing.agents.forEach(agent -> {
                        agent.sessionId = "";
                        agent.chatSessions.clear();
                    });
                }
                existing.name = name.getText().trim();
                existing.description = description.getText().trim();
                existing.folder = path;
            }
            save();
            show("overview");
        });
    }

    private Node overview() {
        Project project = project();
        VBox page = page();
        HBox header = row(14, heading(project.name,
                project.description.isBlank() ? "Acompanhe tudo o que acontece neste projeto." : project.description),
                grow(), button("Configurações", "outline-button", () -> editProject(project)),
                button("Abrir chat", "primary-button", () -> show("chat")));
        page.getChildren().add(header);

        long active = project.agents.stream().filter(a -> !a.status.equals("disponível")).count();
        long open = project.tasks.stream().filter(t -> !t.status.equals("Concluída") && !t.status.equals("Cancelada")).count();
        long pending = project.approvals.stream().filter(a -> a.status.equals("Pendente")).count();
        HBox metrics = row(16,
                metric("Agentes", String.valueOf(project.agents.size()), active + " ativos agora", "green"),
                metric("Tarefas abertas", String.valueOf(open), "no projeto", ""),
                metric("Aprovações pendentes", String.valueOf(pending), pending > 0 ? "Aguardando você" : "Tudo em dia", "amber"),
                metric("Conhecimento", String.valueOf(project.knowledge.size()), "arquivos indexados", ""));
        page.getChildren().add(metrics);

        HBox columns = row(18);
        VBox left = new VBox(18);
        VBox right = new VBox(18);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setMinWidth(400);
        right.setMinWidth(340);
        columns.getChildren().addAll(left, right);

        VBox taskCard = card();
        taskCard.getChildren().add(row(8, label("Tarefas", "section-title"), grow(),
                link("Ver todas", () -> show("tasks"))));
        if (project.tasks.isEmpty()) taskCard.getChildren().add(emptyText("Nenhuma tarefa ainda. Envie uma mensagem no chat."));
        project.tasks.stream().sorted(Comparator.comparing((Task t) -> t.createdAt).reversed()).limit(5)
                .forEach(t -> taskCard.getChildren().add(taskRow(project, t)));
        left.getChildren().add(taskCard);

        VBox activity = card();
        activity.setMinHeight(260);
        activity.getChildren().add(label("Atividade recente", "section-title"));
        if (project.allMessages().isEmpty()) activity.getChildren().add(emptyText("As ações dos agentes aparecerão aqui."));
        project.allMessages().stream().sorted(Comparator.comparing((ChatMessage m) -> m.time).reversed()).limit(6)
                .forEach(m -> activity.getChildren().add(activityRow(m, 92)));
        left.getChildren().add(activity);

        VBox approvalCard = card();
        approvalCard.getChildren().add(label("Pedidos de aprovação", "section-title amber-text"));
        List<Approval> approvals = project.approvals.stream().filter(a -> a.status.equals("Pendente")).toList();
        if (approvals.isEmpty()) approvalCard.getChildren().add(emptyText("Nenhuma aprovação pendente."));
        approvals.stream().limit(2).forEach(a -> approvalCard.getChildren().add(approvalInline(project, a)));
        right.getChildren().add(approvalCard);

        VBox agentCard = card();
        agentCard.getChildren().add(row(8, label("Agentes do projeto", "section-title"), grow(),
                link("+ Adicionar", () -> editAgent(null))));
        project.agents.stream().limit(6).forEach(agent -> agentCard.getChildren().add(row(12,
                avatar(agent), new VBox(2, label(agent.name, "row-title"), label(agent.role, "tiny-muted")),
                grow(), label(agent.status, agent.status.equals("disponível") ? "muted" : "green-text"))));
        right.getChildren().add(agentCard);

        VBox integrationCard = card();
        integrationCard.getChildren().addAll(label("Integrações ativas", "section-title"),
                row(8, badge("Terminal"), badge("Sistema de arquivos"), badge("Git"),
                        badge(project.mcps.stream().filter(m -> m.enabled).count() + " MCPs")));
        right.getChildren().add(integrationCard);
        page.getChildren().add(columns);
        return scroll(page);
    }

    private Node taskRow(Project project, Task task) {
        Agent agent = project.agent(task.agentId);
        HBox row = row(12, agent == null ? dot("#888888") : smallAvatar(agent, 12),
                label(shorten(task.title, 55), "row-title"), grow(),
                label(agent == null ? "—" : agent.name, "tiny-muted"),
                badge(task.status));
        row.getStyleClass().add("list-row");
        return row;
    }

    private HBox activityRow(ChatMessage message, int maxLength) {
        Label time = label(message.time.length() >= 16 ? message.time.substring(11) : message.time, "tiny-muted");
        time.setMinWidth(40);
        time.setMaxWidth(40);
        time.setWrapText(false);
        Label summary = label(shorten(message.sender + " · " + message.text, maxLength), "activity-text");
        summary.setMinWidth(0);
        HBox.setHgrow(summary, Priority.ALWAYS);
        return row(10, time, summary);
    }

    private Node approvalInline(Project project, Approval approval) {
        VBox box = new VBox(9);
        box.getStyleClass().add("approval-inline");
        if ("question".equals(approval.kind)) {
            box.getStyleClass().add("question-inline");
            box.getChildren().addAll(label(approval.title, "row-title"),
                    label("O agente precisa da sua resposta para continuar.", "muted"),
                    button("Responder", "primary-button", () -> show("tasks")));
            return box;
        }
        box.getChildren().addAll(label(approval.title, "row-title"),
                label(approval.detail, "muted"), row(8,
                        button("Aprovar", "primary-button", () -> approve(project, approval)),
                        button("Negar", "outline-button", () -> reject(project, approval))));
        return box;
    }

    private VBox metric(String title, String value, String subtitle, String tint) {
        VBox box = card();
        box.getStyleClass().add("metric-card");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().addAll(label(title, "muted"), label(value, "metric-value"),
                label(subtitle, tint.isBlank() ? "tiny-muted" : tint + "-text"));
        return box;
    }

    private VBox page() {
        VBox page = new VBox(22);
        page.getStyleClass().add("page");
        return page;
    }

    private VBox card() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox heading(String title, String subtitle) {
        return new VBox(6, label(title, "page-title"), label(subtitle, "subtitle"));
    }

    private ScrollPane scroll(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.getStyleClass().add("main-scroll");
        return pane;
    }

    private Label label(String text, String... classes) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll(classes);
        return label;
    }

    private Label badge(String text) {
        return label(text, "badge");
    }

    private Label emptyText(String text) {
        Label label = label(text, "muted");
        label.setPadding(new Insets(12, 0, 0, 0));
        return label;
    }

    private Button button(String text, String style, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll(style.split(" "));
        String iconName = switch (text) {
            case "+" -> "plus";
            case "✎" -> "pencil";
            case "✕", "×" -> "x";
            case "↩" -> "reply";
            case "→" -> "send-horizontal";
            case "↺" -> "refresh-cw";
            case "Buscar" -> "search";
            case "Escolher pasta" -> "folder-open";
            case "Escolher arquivos" -> "files";
            case "Reindexar", "Reindexar tudo", "Atualizar" -> "refresh-cw";
            case "Reverter" -> "rotate-ccw";
            case "Aprovar", "Preparar" -> "check";
            case "Negar" -> "x";
            case "Enviar" -> "send-horizontal";
            case "Pular" -> "skip-forward";
            case "Editar", "Pedir ajuste" -> "pencil";
            case "Conversar", "Abrir chat" -> "message-circle";
            case "Parar" -> "square";
            default -> text.startsWith("+ ") ? "plus" : null;
        };
        if (iconName != null) {
            button.setGraphic(UiIcons.icon(iconName, style.contains("chat-send") ? 19 : 16));
            if (text.startsWith("+ ")) button.setText(text.substring(2));
            else if (text.length() == 1) button.setText("");
        }
        if (text.equals("Executar ↵")) {
            button.setText("Executar");
            button.setGraphic(UiIcons.icon("terminal", 16));
        }
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button link(String text, Runnable action) {
        return button(text, "link-button", action);
    }

    private HBox row(double gap, Node... children) {
        HBox row = new HBox(gap, children);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Region grow() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private Region growV() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    private Region spacer(double height) {
        Region region = new Region();
        region.setMinHeight(height);
        return region;
    }

    private Circle dot(String color) {
        Circle dot = new Circle(5, Color.web(color));
        dot.getStyleClass().add("status-dot");
        return dot;
    }

    private StackPane avatar(Agent agent) {
        return new AgentCharacterView(agent.visualId, 48, !data.reduceAnimations, false,
                agent.color, agent.eyeColor, agentWorking(agent), agent.animations);
    }

    private boolean agentWorking(Agent agent) {
        return "trabalhando".equals(agent.status) || "lendo".equals(agent.status);
    }

    private TextField field(String value, String prompt) {
        TextField field = new TextField(value);
        field.setPromptText(prompt);
        field.getStyleClass().add("input-field");
        return field;
    }

    private TextArea area(String value, String prompt, int rows) {
        TextArea area = new TextArea(value);
        area.setPromptText(prompt);
        area.setPrefRowCount(rows);
        area.setWrapText(true);
        area.getStyleClass().add("input-area");
        return area;
    }

    private Dialog<ButtonType> dialog(String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,
                new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE));
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dark-dialog");
        dialog.getDialogPane().setPrefWidth(520);
        return dialog;
    }

    private void alert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (stage != null) alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("dark-dialog");
        alert.showAndWait();
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("dark-dialog");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private String shorten(String text, int max) {
        if (text == null) return "";
        String single = text.replace('\n', ' ').replace('\r', ' ');
        return single.length() <= max ? single : single.substring(0, max - 1) + "…";
    }

    private Node chat() {
        Project project = project();
        ChatThread chat = project.activeChat();
        Agent privateAgent = chat.isPrivate() ? project.agent(chat.privateAgentId) : null;
        String chatKey = project.id + ":" + chat.id;
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("chat-layout");

        VBox center = new VBox();
        center.getStyleClass().add("chat-center");
        center.addEventFilter(KeyEvent.KEY_PRESSED, event -> pasteChatAttachment(project, event));
        VBox messages = new VBox(18);
        messages.getStyleClass().add("messages");
        boolean animateNew = chat.id.equals(lastChatId);
        renderChatTimeline(project, chat, messages, chat.messages, animateNew, true);
        lastChatId = chat.id;

        TextField input = field(drafts.getOrDefault(chatKey, ""), chat.isPrivate()
                ? "Escreva para " + (privateAgent == null ? "este agente" : privateAgent.name)
                : "Escreva uma mensagem. Use @ para chamar um agente");
        input.getStyleClass().add("chat-input");
        List<Agent> mentionable = chat.isPrivate()
                ? privateAgent == null ? List.of() : List.of(privateAgent)
                : project.agents;
        ChatMentionMenu mentions = new ChatMentionMenu(input, mentionable,
                agent -> selectedAgentId = agent.id,
                getClass().getResource("/app.css").toExternalForm());
        input.textProperty().addListener((o, before, after) -> drafts.put(chatKey, after));
        input.setOnKeyPressed(event -> {
            if (mentions.handleKey(event)) return;
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();
                sendChat(project, selectedChatAgent(project), input.getText());
            }
        });

        String chatTitleText = chat.isPrivate() && privateAgent != null ? "Conversa com " + privateAgent.name : chat.name;
        String subtitle = chat.isPrivate()
                ? privateAgent == null ? "Agente removido do projeto" : "Conversa individual · só você e " + privateAgent.name
                : project.agents.stream().limit(3).map(a -> a.name).reduce((a, b) -> a + ", " + b)
                        .orElse("Sem agentes") + " · " + project.agents.size() + " agentes";
        VBox headerTitle = new VBox(2, label(chatTitleText, "chat-title"), label(subtitle, "chat-subtitle"));
        HBox participants = new HBox(-6);
        participants.getStyleClass().add("chat-participants");
        if (privateAgent != null) participants.getChildren().add(smallAvatar(privateAgent, 14));
        for (Agent agent : chat.isPrivate() ? List.<Agent>of() : project.agents.stream().limit(3).toList()) {
            Button person = new Button();
            person.setGraphic(smallAvatar(agent, 14));
            person.getStyleClass().add("participant-button");
            person.setTooltip(new Tooltip("Chamar " + agent.name));
            person.setOnAction(event -> {
                selectedAgentId = agent.id;
                input.insertText(input.getCaretPosition(), "@" + agent.name + " ");
                input.requestFocus();
            });
            participants.getChildren().add(person);
        }
        TextField search = field("", "Buscar na conversa");
        search.getStyleClass().add("chat-search");
        search.setVisible(false);
        search.setManaged(false);
        search.textProperty().addListener((o, before, after) -> {
            String query = after.trim().toLowerCase(Locale.ROOT);
            List<ChatMessage> results = chat.messages.stream().filter(message -> query.isBlank()
                    || message.text.toLowerCase(Locale.ROOT).contains(query)
                    || message.sender.toLowerCase(Locale.ROOT).contains(query)).toList();
            renderChatTimeline(project, chat, messages, results, false, query.isBlank());
            if (!query.isBlank() && messages.getChildren().isEmpty())
                messages.getChildren().add(label("Nenhuma mensagem encontrada.", "muted"));
        });
        Button searchButton = new Button();
        searchButton.setGraphic(UiIcons.icon("search", 18));
        searchButton.getStyleClass().add("chat-header-icon");
        searchButton.setTooltip(new Tooltip("Buscar mensagens"));
        searchButton.setOnAction(event -> {
            boolean open = !search.isVisible();
            search.setVisible(open);
            search.setManaged(open);
            participants.setVisible(!open);
            participants.setManaged(!open);
            if (open) search.requestFocus();
            else search.clear();
        });
        HBox header = row(18, headerTitle, grow(), participants, search, searchButton);
        long scheduledCount = project.scheduledMessages.stream().filter(item -> chat.id.equals(item.chatId)).count();
        Button pendingSchedules = button("Agendadas " + scheduledCount, "link-button",
                () -> showScheduledDialog(project, chat, input.getText()));
        pendingSchedules.setTooltip(new Tooltip("Ver e cancelar mensagens agendadas"));
        header.getChildren().add(header.getChildren().size() - 2, pendingSchedules);
        header.getStyleClass().add("chat-header");
        center.getChildren().add(header);

        ScrollPane history = scroll(messages);
        history.getStyleClass().add("chat-history");
        chatTimeline = messages;
        chatHistory = history;
        history.setVvalue(1);
        messages.setAlignment(Pos.BOTTOM_LEFT);
        messages.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> history.getViewportBounds().getHeight(), history.viewportBoundsProperty()));
        VBox.setVgrow(history, Priority.ALWAYS);
        center.getChildren().add(history);

        VBox composer = new VBox(7);
        composer.getStyleClass().add("composer-area");
        List<Task> queued = QueuedMessages.list(project);
        if (!queued.isEmpty()) {
            VBox queuePanel = new VBox(6);
            queuePanel.getStyleClass().add("chat-queue");
            queuePanel.setMaxWidth(760);
            queuePanel.getChildren().add(label("FILA DO PROJETO · " + queued.size(), "eyebrow"));
            VBox queueRows = new VBox(4);
            for (Task task : queued) {
                Agent owner = project.agent(task.agentId);
                ChatThread origin = project.chat(task.chatId);
                ChatMessage userMessage = QueuedMessages.userMessage(project, task);
                Label preview = label(shorten(userMessage == null ? task.title
                        : copyableMessageText(userMessage), 90), "chat-queue-text");
                preview.setWrapText(true);
                Label detail = label((owner == null ? "Agente removido" : owner.name) + " · "
                        + (origin == null ? "Conversa removida" : origin.name)
                        + ("Pausada".equals(task.status) ? " · Pausada" : ""), "tiny-muted");
                VBox content = new VBox(2, preview, detail);
                HBox.setHgrow(content, Priority.ALWAYS);
                Button up = button("↑", "chat-queue-action", () -> moveQueuedMessage(project, task, "up"));
                up.setTooltip(new Tooltip("Aumentar prioridade para este agente"));
                Button down = button("↓", "chat-queue-action", () -> moveQueuedMessage(project, task, "down"));
                down.setTooltip(new Tooltip("Diminuir prioridade para este agente"));
                int position = queued.indexOf(task);
                up.setDisable(queued.subList(0, position).stream()
                        .noneMatch(item -> task.agentId.equals(item.agentId)));
                down.setDisable(queued.subList(position + 1, queued.size()).stream()
                        .noneMatch(item -> task.agentId.equals(item.agentId)));
                HBox line = row(5, content, up, down);
                if (userMessage != null) {
                    Button edit = button("Editar", "chat-queue-action", () -> editQueuedDialog(project, task));
                    Button remove = button("Apagar", "chat-queue-action", () -> {
                        if (confirm("Apagar mensagem", "Remover esta mensagem da fila antes de executar?"))
                            deleteQueuedMessage(project, task);
                    });
                    line.getChildren().addAll(edit, remove);
                }
                line.getStyleClass().add("chat-queue-row");
                queueRows.getChildren().add(line);
            }
            ScrollPane queueScroll = scroll(queueRows);
            queueScroll.setPrefHeight(Math.min(156, queued.size() * 51 + 8));
            queuePanel.getChildren().add(queueScroll);
            composer.getChildren().add(new StackPane(queuePanel));
        }
        ChatMessage replying = replyDrafts.get(chatKey);
        if (replying != null) composer.getChildren().add(replyDraftPreview(project, chat, replying));
        Button attach = new Button();
        attach.setGraphic(UiIcons.icon("paperclip", 20));
        attach.getStyleClass().add("chat-attach");
        attach.setTooltip(new Tooltip("Anexar arquivo"));
        attach.setOnAction(event -> attachFiles(project));
        Button send = button("→", "chat-send", () -> sendChat(project, selectedChatAgent(project), input.getText()));
        Button schedule = button("◷", "chat-attach chat-schedule", () -> showScheduledDialog(project, chat, input.getText()));
        schedule.setTooltip(new Tooltip("Agendar envio desta mensagem"));
        HBox composerBar = row(14, attach, input, schedule, send);
        composerBar.getStyleClass().add("chat-composer");
        composerBar.setMaxWidth(760);
        HBox.setHgrow(input, Priority.ALWAYS);
        StackPane composerPosition = new StackPane(composerBar);
        composer.getChildren().add(composerPosition);
        List<String> files = pendingAttachments.getOrDefault(chatKey, List.of());
        if (!files.isEmpty()) {
            FlowPane previews = new FlowPane(8, 8);
            previews.setMaxWidth(760);
            previews.getStyleClass().add("chat-attachment-list");
            for (String file : files) previews.getChildren().add(attachmentPreview(project, chatKey, file, true));
            composer.getChildren().add(new StackPane(previews));
        }
        center.getChildren().add(composer);
        layout.setCenter(center);

        VBox inspector = new VBox(9);
        inspector.getStyleClass().add("chat-inspector");
        inspector.setPrefWidth(300);
        inspector.setMinWidth(0);
        inspector.getChildren().add(label(chat.isPrivate() ? "AGENTE" : "AGENTES", "eyebrow"));
        List<Agent> visibleAgents = chat.isPrivate()
                ? project.agents.stream().filter(agent -> agent.id.equals(chat.privateAgentId)).toList()
                : project.agents;
        for (Agent agent : visibleAgents) {
            boolean workingHere = project.tasks.stream().anyMatch(task -> chat.id.equals(task.chatId)
                    && agent.id.equals(task.agentId) && "Em andamento".equals(task.status));
            String statusText = !agent.status.equals("disponível") && !workingHere
                    ? "em outra conversa" : agent.status;
            Label status = label(statusText, agent.status.equals("disponível") ? "chat-agent-idle" : "chat-agent-busy");
            if (!agent.status.equals("disponível") && agent.color != null)
                status.setStyle("-fx-text-fill: " + agent.color + ";");
            HBox line = row(9, smallAvatar(agent, 12),
                    label(agent.name, "chat-agent-name"), grow(), status);
            line.getStyleClass().add("chat-agent-row");
            inspector.getChildren().add(line);
        }
        inspector.getChildren().add(spacer(3));
        inspector.getChildren().add(label("TAREFAS EM ANDAMENTO", "eyebrow"));
        List<Task> running = project.tasks.stream().filter(t -> chat.id.equals(t.chatId)
                && t.status.equals("Em andamento")).toList();
        if (running.isEmpty()) inspector.getChildren().add(label("Nenhuma tarefa ativa", "tiny-muted"));
        running.stream().limit(4).forEach(task -> {
            Agent owner = project.agent(task.agentId);
            Button stop = button("Parar", "chat-task-stop", () -> showTaskDetails(project, task));
            stop.setTooltip(new Tooltip("Parar esta tarefa ou passar para a próxima da fila"));
            HBox title = row(5, label(shorten(task.title, 27), "chat-task-name"), grow(), stop);
            Node progress = activityBar(task, owner == null || owner.color == null ? "#78b98e" : owner.color);
            VBox item = new VBox(6, title, progress);
            item.getStyleClass().add("chat-task-row");
            inspector.getChildren().add(item);
        });
        inspector.getChildren().add(spacer(3));
        inspector.getChildren().add(label("CONHECIMENTO", "eyebrow"));
        VBox knowledgeBox = card();
        long indexed = project.knowledge.stream().filter(file -> file.status.equals("Indexado")).count();
        knowledgeBox.getChildren().add(label(indexed + (indexed == 1 ? " arquivo indexado" : " arquivos indexados") + " na base do projeto. "
                + "Os agentes consultam esse material ao responder.", "chat-knowledge-text"));
        inspector.getChildren().add(knowledgeBox);
        inspector.getChildren().add(spacer(8));
        String executable = codexService.executable();
        chatUsageHost = new VBox(planUsageCard(executable));
        inspector.getChildren().add(chatUsageHost);
        refreshUsage(executable, false);
        ScrollPane inspectorScroll = new ScrollPane(inspector);
        inspectorScroll.setFitToWidth(true);
        inspectorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        inspectorScroll.setPrefWidth(300);
        inspectorScroll.setMinWidth(300);
        inspectorScroll.setMaxWidth(300);
        inspectorScroll.getStyleClass().add("chat-inspector-scroll");
        layout.setRight(inspectorScroll);
        Platform.runLater(() -> history.setVvalue(1));
        return layout;
    }

    private Node replyDraftPreview(Project project, ChatThread chat, ChatMessage original) {
        VBox summary = new VBox(2,
                label(replySender(original), "chat-reply-sender"),
                label(shorten(replyExcerpt(original), 150), "chat-reply-excerpt"));
        HBox.setHgrow(summary, Priority.ALWAYS);
        Button cancel = button("✕", "chat-reply-cancel", () -> cancelReply(project, chat));
        cancel.setTooltip(new Tooltip("Cancelar resposta"));
        HBox preview = row(10, summary, grow(), cancel);
        preview.getStyleClass().add("chat-reply-draft");
        preview.setMaxWidth(760);
        return new StackPane(preview);
    }

    private void beginReply(Project project, ChatThread chat, ChatMessage original) {
        replyDrafts.put(project.id + ":" + chat.id, original);
        Agent owner = messageOwner(project, original);
        if (!chat.isPrivate() && owner != null) selectedAgentId = owner.id;
        show("chat");
        Platform.runLater(() -> {
            if (root.lookup(".chat-input") instanceof TextField input) input.requestFocus();
        });
    }

    private void cancelReply(Project project, ChatThread chat) {
        replyDrafts.remove(project.id + ":" + chat.id);
        show("chat");
        Platform.runLater(() -> {
            if (root.lookup(".chat-input") instanceof TextField input) input.requestFocus();
        });
    }

    private String replySender(ChatMessage message) {
        return "user".equals(message.kind) ? "Você"
                : message.sender == null || message.sender.isBlank() ? "Mensagem" : message.sender;
    }

    private Agent messageOwner(Project project, ChatMessage message) {
        Agent direct = project.agent(message.agentId);
        if (direct != null) return direct;
        if ("approval".equals(message.kind)) {
            Approval approval = project.approvals.stream()
                    .filter(item -> item.id.equals(message.agentId)).findFirst().orElse(null);
            if (approval != null) return project.agent(approval.agentId);
        }
        return null;
    }

    private String replyExcerpt(ChatMessage message) {
        if (message.text != null && !message.text.isBlank()) return message.text;
        if (message.attachments != null && !message.attachments.isEmpty())
            return attachmentName(message.attachments.getFirst());
        return "Mensagem sem texto";
    }

    private Node replyQuote(ChatMessage message) {
        VBox quote = new VBox(2,
                label(message.replySender == null ? "Mensagem" : message.replySender, "chat-reply-sender"),
                label(shorten(message.replyText, 150), "chat-reply-excerpt"));
        quote.getStyleClass().add("chat-reply-quote");
        if (message.replyToMessageId != null && !message.replyToMessageId.isBlank()) {
            quote.setOnMouseClicked(event -> jumpToMessage(message.replyToMessageId));
            quote.setCursor(javafx.scene.Cursor.HAND);
        }
        return quote;
    }

    private HBox messageFooter(Project project, ChatThread chat, ChatMessage message, String time) {
        Button reply = button("↩", "chat-reply-action", () -> beginReply(project, chat, message));
        reply.setTooltip(new Tooltip("Responder a esta mensagem"));
        reply.setFocusTraversable(false);
        return row(0, reply, grow(), label(time, "message-time"));
    }

    private void jumpToMessage(String messageId) {
        if (chatTimeline == null || chatHistory == null) return;
        Node target = chatTimeline.getChildren().stream().filter(node -> {
            if (!(node.getUserData() instanceof String key)) return false;
            return key.equals(messageId) || key.startsWith(messageId + ":");
        }).findFirst().orElse(null);
        if (target == null) return;
        double viewport = chatHistory.getViewportBounds().getHeight();
        double available = chatTimeline.getLayoutBounds().getHeight() - viewport;
        if (available > 0) {
            double y = target.getBoundsInParent().getMinY();
            chatHistory.setVvalue(Math.max(0, Math.min(1, (y - viewport * .3) / available)));
        }
        target.getStyleClass().add("chat-jump-highlight");
        Timeline clear = new Timeline(new KeyFrame(Duration.seconds(1.3),
                event -> target.getStyleClass().remove("chat-jump-highlight")));
        clear.play();
    }

    private void decorateChatEntry(Node entry, Project project, ChatThread chat, ChatMessage message) {
        MenuItem copy = new MenuItem("Copiar mensagem");
        copy.setOnAction(event -> {
            String selected = selectedMessageText(entry);
            SelectableMessageText.copy(selected.isEmpty() ? copyableMessageText(message) : selected);
        });
        MenuItem reply = new MenuItem("Responder");
        reply.setOnAction(event -> beginReply(project, chat, message));
        ContextMenu menu = new ContextMenu(copy, reply);
        if (message.attachments != null) message.attachments.stream()
                .filter(path -> isImageAttachment(path) && Files.isRegularFile(Path.of(path)))
                .findFirst().ifPresent(path -> {
                    MenuItem copyImage = new MenuItem("Copiar imagem");
                    copyImage.setOnAction(event -> {
                        Image image = new Image(Path.of(path).toUri().toString());
                        if (image.isError()) {
                            alert("Copiar imagem", "Não foi possível abrir esta imagem.");
                            return;
                        }
                        ClipboardContent content = new ClipboardContent();
                        content.putImage(image);
                        Clipboard.getSystemClipboard().setContent(content);
                    });
                    menu.getItems().add(1, copyImage);
                });
        menu.getStyleClass().add("chat-context-menu");
        entry.setOnContextMenuRequested(event -> {
            menu.show(entry, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private String selectedMessageText(Node node) {
        if (node instanceof SelectableMessageText text && !text.selectedText().isEmpty())
            return text.selectedText();
        if (node instanceof javafx.scene.Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) {
                String selected = selectedMessageText(child);
                if (!selected.isEmpty()) return selected;
            }
        return "";
    }

    private String copyableMessageText(ChatMessage message) {
        if (message.text != null && !message.text.isBlank()) return message.text;
        if (message.attachments != null && !message.attachments.isEmpty())
            return message.attachments.stream().map(this::attachmentName)
                    .reduce((first, next) -> first + "\n" + next).orElse("");
        return message.sender == null ? "" : message.sender;
    }

    private Agent selectedChatAgent(Project project) {
        ChatThread chat = project.activeChat();
        if (chat.isPrivate()) return project.agent(chat.privateAgentId);
        Agent selected = project.agent(selectedAgentId);
        return selected == null && !project.agents.isEmpty() ? project.agents.getFirst() : selected;
    }

    private StackPane smallAvatar(Agent agent, double radius) {
        return new AgentCharacterView(agent.visualId, radius * 2, !data.reduceAnimations, false,
                agent.color, agent.eyeColor, agentWorking(agent), agent.animations);
    }

    private void renderChatTimeline(Project project, ChatThread chat, VBox container, List<ChatMessage> entries,
                                    boolean animateNew, boolean showTyping) {
        stopChatAnimations();
        container.getChildren().clear();
        for (int index = 0; index < entries.size();) {
            ChatMessage message = entries.get(index);
            if ("progress".equals(message.kind)) {
                List<ChatMessage> group = new ArrayList<>();
                boolean hasNote = false;
                String taskId = message.taskId;
                String agentId = message.agentId;
                while (index < entries.size()) {
                    ChatMessage next = entries.get(index);
                    if (!"progress".equals(next.kind) || !java.util.Objects.equals(taskId, next.taskId)
                            || !java.util.Objects.equals(agentId, next.agentId)) break;
                    if (hasNote && isProgressNote(next)) break;
                    group.add(next);
                    hasNote |= isProgressNote(next);
                    index++;
                }
                ChatMessage note = group.stream().filter(this::isProgressNote).findFirst().orElse(null);
                if (note != null) {
                    Node entry = progressBubble(project, chat, group, note);
                    decorateChatEntry(entry, project, chat, note);
                    addChatEntry(container, entry, note.id, animateNew);
                }
            } else {
                String visualKey = message.id;
                if ("approval".equals(message.kind)) {
                    Approval approval = project.approvals.stream().filter(a -> a.id.equals(message.agentId))
                            .findFirst().orElse(null);
                    if (approval != null) visualKey += ":" + approval.status;
                }
                Node entry = chatMessage(project, chat, message);
                decorateChatEntry(entry, project, chat, message);
                addChatEntry(container, entry, visualKey, animateNew);
                index++;
            }
        }
        if (showTyping) {
            int visible = 0;
            for (int index = project.tasks.size() - 1; index >= 0 && visible < 1; index--) {
                Task task = project.tasks.get(index);
                if (!chat.id.equals(task.chatId) || !"Em andamento".equals(task.status)) continue;
                Agent agent = project.agent(task.agentId);
                if (agent == null) continue;
                boolean hasNote = chat.messages.stream().anyMatch(message -> task.id.equals(message.taskId)
                        && "progress".equals(message.kind) && isProgressNote(message));
                boolean awaiting = project.approvals.stream().anyMatch(approval -> task.id.equals(approval.taskId)
                        && "Pendente".equals(approval.status));
                if (!hasNote && !awaiting) {
                    addChatEntry(container, typingIndicator(agent), "typing:" + task.id, animateNew);
                    visible++;
                }
            }
        }
    }

    private boolean isProgressNote(ChatMessage message) {
        if ("note".equals(message.progressType)) return true;
        return (message.progressType == null || message.progressType.isBlank())
                && message.text != null && message.text.length() > 85 && !message.text.endsWith("...");
    }

    private Node progressBubble(Project project, ChatThread chat, List<ChatMessage> group,
                                ChatMessage note) {
        Agent agent = project.agent(note.agentId);
        String color = agent == null || agent.color == null ? "#7cbb90" : agent.color;
        VBox bubble = new VBox(7);
        bubble.getStyleClass().addAll("message-bubble", "agent-message");
        bubble.setMaxWidth(560);
        Label author = label(note.sender, "message-author");
        author.setStyle("-fx-text-fill: " + color + ";");
        bubble.getChildren().addAll(author, messageBody(project, note.text));
        boolean afterNote = false;
        ChatMessage latestActivity = null;
        for (ChatMessage message : group) {
            if (message == note) { afterNote = true; continue; }
            if (afterNote && !isProgressNote(message)) latestActivity = message;
        }
        if (latestActivity != null) {
            String text = latestActivity.text.replaceAll("\\.{3}$", "");
            boolean done = text.endsWith("concluídos.") || text.endsWith("concluída.")
                    || text.endsWith("atualizados.");
            Label tool = label(text, "chat-tool-chip");
            tool.setGraphic(UiIcons.icon(done ? "check" : "ellipsis", 13));
            tool.setWrapText(false);
            tool.setTextOverrun(OverrunStyle.ELLIPSIS);
            tool.setMaxWidth(Region.USE_PREF_SIZE);
            bubble.getChildren().add(tool);
        }
        String time = note.time == null ? "" : note.time;
        if (time.length() >= 16) time = time.substring(11, 16);
        bubble.getChildren().add(messageFooter(project, chat, note, time));
        HBox line = row(12, agent == null ? dot(color) : smallAvatar(agent, 16), bubble, grow());
        line.setAlignment(Pos.TOP_LEFT);
        return line;
    }

    private Node typingIndicator(Agent agent) {
        HBox dots = new HBox(4);
        dots.getStyleClass().add("chat-typing-dots");
        for (int index = 0; index < 3; index++) {
            Circle dot = new Circle(3.5, Color.web("#a4a4a3"));
            dots.getChildren().add(dot);
            if (!data.reduceAnimations) {
                FadeTransition pulse = new FadeTransition(Duration.millis(490), dot);
                pulse.setFromValue(0.3);
                pulse.setToValue(1);
                pulse.setAutoReverse(true);
                pulse.setCycleCount(Animation.INDEFINITE);
                pulse.setDelay(Duration.millis(index * 170));
                pulse.play();
                chatAnimations.add(pulse);
            }
        }
        HBox line = row(9, smallAvatar(agent, 14), dots,
                label(agent.name + " está digitando...", "chat-typing-label"), grow());
        line.getStyleClass().add("chat-typing-line");
        return line;
    }

    private void addChatEntry(VBox container, Node node, String id, boolean animateNew) {
        node.setUserData(id);
        container.getChildren().add(node);
        boolean firstAppearance = shownChatMessages.add(id);
        if (animateNew && firstAppearance && !data.reduceAnimations) {
            node.setOpacity(0);
            node.setTranslateY(12);
            FadeTransition fade = new FadeTransition(Duration.millis(260), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
            slide.setFromY(12);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            fade.play();
            slide.play();
        }
    }

    private void stopChatAnimations() {
        chatAnimations.forEach(Animation::stop);
        chatAnimations.clear();
    }

    private SelectableMessageText messageBody(Project project, String body) {
        String content = body == null ? "" : body;
        SelectableMessageText flow = new SelectableMessageText(content);
        flow.getStyleClass().add("message-rich");
        java.util.regex.Matcher mention = java.util.regex.Pattern.compile("@[\\p{L}\\p{N}_-]+")
                .matcher(content);
        int offset = 0;
        while (mention.find()) {
            if (mention.start() > offset) flow.getChildren().add(messageText(content.substring(offset, mention.start()), "#e3e3e1"));
            String found = mention.group();
            Agent agent = project.agents.stream().filter(a -> found.equalsIgnoreCase("@" + a.name)).findFirst().orElse(null);
            flow.getChildren().add(messageText(found, agent == null || agent.color == null ? "#9fc4e8" : agent.color));
            offset = mention.end();
        }
        if (offset < content.length()) flow.getChildren().add(messageText(content.substring(offset), "#e3e3e1"));
        if (content.isEmpty()) flow.getChildren().add(messageText(" ", "#e3e3e1"));
        return flow;
    }

    private Text messageText(String value, String color) {
        Text text = new Text(value);
        text.setFill(Color.web(color));
        text.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
        return text;
    }

    private Node approvedChatCard(Project project, Approval approval) {
        Agent agent = project.agent(approval.agentId);
        Task task = project.task(approval.taskId);
        String color = agent == null || agent.color == null ? "#79bd8d" : agent.color;
        VBox card = new VBox(7);
        card.getStyleClass().add("chat-running-card");
        card.setPrefWidth(560);
        card.setMaxWidth(560);
        Label title = label("Aprovado por você", "chat-running-title");
        title.setGraphic(UiIcons.icon("check", 15));
        String detail = task == null ? approval.title
                : agent == null ? "Tarefa em andamento: " + task.title
                : agent.name + ("Em andamento".equals(task.status) ? " está trabalhando em " : " trabalhou em ")
                        + shorten(task.title, 75);
        card.getChildren().addAll(title, label(detail, "chat-running-detail"));
        if (task != null) card.getChildren().add(activityBar(task, "#80c394"));
        HBox line = row(12, agent == null ? dot(color) : smallAvatar(agent, 16), card, grow());
        line.setAlignment(Pos.TOP_LEFT);
        return line;
    }

    private Node answeredQuestionCard(Approval question) {
        VBox card = new VBox(2);
        card.getStyleClass().add("chat-answered-card");
        card.setPrefWidth(560);
        String number = "Ignorada".equals(question.status) ? "–" : String.valueOf(question.selectedChoice + 1);
        Label marker = label(number, "chat-answer-number");
        Label caption = label("Ignorada".equals(question.status) ? "Você pulou a pergunta" : "Você respondeu",
                "chat-answer-caption");
        Label answer = label(question.response, "chat-answer-text");
        VBox description = new VBox(1, caption, answer);
        HBox.setHgrow(description, Priority.ALWAYS);
        card.getChildren().add(row(9, marker, description, grow(), UiIcons.icon("check", 15)));
        return card;
    }

    private Node activityBar(Task task, String color) {
        StackPane track = new StackPane();
        track.getStyleClass().add("chat-running-track");
        track.setMaxWidth(Double.MAX_VALUE);
        Region fill = new Region();
        fill.getStyleClass().add("chat-running-fill");
        fill.setStyle("-fx-background-color: " + color + ";");
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        track.getChildren().add(fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        DoubleProperty fraction = new SimpleDoubleProperty();
        fill.prefWidthProperty().bind(track.widthProperty().multiply(fraction));
        if (!"Em andamento".equals(task.status)) {
            fraction.set(1);
        } else {
            long started = chatProgressStartedAt.computeIfAbsent(task.id, ignored -> System.nanoTime());
            double elapsed = Math.max(0, (System.nanoTime() - started) / 1_000_000.0);
            double base = Math.max(0.08, Math.min(0.85, task.progress / 100.0));
            double current = base + (0.9 - base) * Math.min(1, elapsed / 15_000.0);
            fraction.set(current);
            if (!data.reduceAnimations && elapsed < 15_000) {
                Timeline timeline = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(fraction, current)),
                        new KeyFrame(Duration.millis(15_000 - elapsed),
                                new KeyValue(fraction, 0.9, Interpolator.LINEAR)));
                timeline.play();
                chatAnimations.add(timeline);
            }
        }
        return track;
    }

    private Node chatMessage(Project project, ChatThread chat, ChatMessage message) {
        if (message.kind.equals("approval")) {
            Approval approval = project.approvals.stream().filter(a -> a.id.equals(message.agentId))
                    .findFirst().orElse(null);
            if (approval == null) return new Region();
            if ("question".equals(approval.kind)) {
                Agent agent = project.agent(approval.agentId);
                Node question = "Pendente".equals(approval.status)
                        ? questionCard(project, approval) : answeredQuestionCard(approval);
                if (question instanceof Region region) region.setMaxWidth(560);
                return row(12, agent == null ? dot("#777777") : smallAvatar(agent, 16), question, grow());
            }
            if ("Aprovado".equals(approval.status)) return approvedChatCard(project, approval);
            VBox bubble = card();
            bubble.getStyleClass().add("chat-approval");
            bubble.setPrefWidth(560);
            bubble.setMaxWidth(560);
            bubble.getChildren().addAll(label("Pedido de aprovação", "amber-text row-title"),
                    label(approval.title, "message-text"), label(approval.detail, "muted"));
            if (approval.status.equals("Pendente")) {
                bubble.getChildren().add(row(9,
                        button("Aprovar", "primary-button", () -> approve(project, approval)),
                        button("Negar", "outline-button", () -> reject(project, approval)),
                        button("Ver detalhes", "link-button", () -> alert("Detalhes da aprovação", approval.detail))));
            } else {
                bubble.getChildren().add(badge(approval.status));
            }
            Agent requester = project.agent(approval.agentId);
            return row(12, requester == null ? dot("#777777") : smallAvatar(requester, 16), bubble, grow());
        }
        if (message.kind.equals("system")) {
            HBox info = row(9, dot("#848484"), label(shorten(message.text, 180), "muted"));
            info.getStyleClass().add("system-message");
            return info;
        }
        if (message.kind.equals("error")) {
            VBox bubble = new VBox(6, label("Falha na tarefa", "chat-error-title"),
                    label(message.text, "chat-error-detail"));
            bubble.getStyleClass().add("chat-error-card");
            bubble.setMaxWidth(560);
            HBox line = row(12, dot("#d77979"), bubble, grow());
            line.setAlignment(Pos.TOP_LEFT);
            return line;
        }
        if (message.kind.equals("progress")) {
            if (isProgressNote(message)) return progressBubble(project, chat, List.of(message), message);
            HBox info = row(8, dot("#777777"), label(message.text, "chat-activity-text"));
            info.getStyleClass().add("system-message");
            return info;
        }
        boolean user = message.kind.equals("user");
        Agent agent = project.agent(message.agentId);
        VBox bubble = new VBox(8);
        bubble.getStyleClass().addAll("message-bubble", user ? "user-message" : "agent-message");
        bubble.setMaxWidth(560);
        Label author = label(message.sender, user ? "message-author-user" : "message-author");
        if (!user && agent != null && agent.color != null) author.setStyle("-fx-text-fill: " + agent.color + ";");
        if (!user) bubble.getChildren().add(author);
        if (message.replyToMessageId != null && !message.replyToMessageId.isBlank())
            bubble.getChildren().add(replyQuote(message));
        if (message.text != null && !message.text.isBlank()) bubble.getChildren().add(messageBody(project, message.text));
        if (!user && !chat.isPrivate()) {
            Agent next = nextHandoffRecipient(project, message);
            boolean alreadySent = project.tasks.stream().anyMatch(task -> message.id.equals(task.delegatedFromMessageId));
            if (next != null && !alreadySent) {
                Button handoff = button("Encaminhar para @" + next.name, "chat-handoff-button",
                        () -> delegateAgentMessage(project, chat, message, false));
                handoff.setTooltip(new Tooltip("Criar uma tarefa para " + next.name + " nesta conversa"));
                bubble.getChildren().add(handoff);
            }
        }
        if (message.attachments != null && !message.attachments.isEmpty()) {
            FlowPane previews = new FlowPane(7, 7);
            previews.getStyleClass().add("chat-attachment-list");
            message.attachments.forEach(path -> previews.getChildren().add(attachmentPreview(project, "", path, false)));
            bubble.getChildren().add(previews);
        }
        String time = message.time == null ? "" : message.time;
        if (time.length() >= 16) time = time.substring(11, 16);
        bubble.getChildren().add(messageFooter(project, chat, message, time));
        HBox line = row(12);
        if (user) {
            line.getChildren().addAll(grow(), bubble);
        } else {
            line.getChildren().addAll(agent == null ? dot("#777777") : smallAvatar(agent, 16), bubble, grow());
            line.setAlignment(Pos.TOP_LEFT);
        }
        return line;
    }

    private void attachFiles(Project project) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Anexar arquivos à mensagem");
        List<java.io.File> selected = chooser.showOpenMultipleDialog(stage);
        if (selected == null || selected.isEmpty()) return;
        String chatKey = project.id + ":" + project.activeChat().id;
        List<String> files = pendingAttachments.computeIfAbsent(chatKey, ignored -> new ArrayList<>());
        for (java.io.File source : selected) {
            try {
                files.add(repository.storeAttachment(project.id, source.toPath()).toString());
            } catch (IOException e) {
                alert("Anexo", "Não foi possível anexar " + source.getName() + ": " + e.getMessage());
            }
        }
        show("chat");
    }

    private void pasteChatAttachment(Project project, KeyEvent event) {
        if (event.getCode() != KeyCode.V || !event.isShortcutDown()) return;
        Clipboard clipboard = Clipboard.getSystemClipboard();
        String chatKey = project.id + ":" + project.activeChat().id;
        if (clipboard.hasImage()) {
            event.consume();
            try {
                Image image = clipboard.getImage();
                if (image == null || image.getPixelReader() == null) throw new IOException("Imagem indisponível");
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                if (width <= 0 || height <= 0 || (long) width * height > 30_000_000)
                    throw new IOException("A imagem é grande demais para ser anexada");
                int[] pixels = new int[width * height];
                PixelReader reader = image.getPixelReader();
                reader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), pixels, 0, width);
                BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                buffer.setRGB(0, 0, width, height, pixels, 0, width);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(buffer, "png", output)) throw new IOException("Não foi possível gerar o PNG");
                String filename = "Captura_de_tela_" + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
                Path stored = repository.storeAttachment(project.id, filename, output.toByteArray());
                pendingAttachments.computeIfAbsent(chatKey, ignored -> new ArrayList<>()).add(stored.toString());
                show("chat");
            } catch (IOException | RuntimeException error) {
                alert("Colar imagem", "Não foi possível anexar a imagem: " + error.getMessage());
            }
        } else if (clipboard.hasFiles()) {
            event.consume();
            List<String> files = pendingAttachments.computeIfAbsent(chatKey, ignored -> new ArrayList<>());
            for (java.io.File source : clipboard.getFiles()) {
                try {
                    files.add(repository.storeAttachment(project.id, source.toPath()).toString());
                } catch (IOException error) {
                    alert("Colar arquivo", "Não foi possível anexar " + source.getName() + ": " + error.getMessage());
                }
            }
            show("chat");
        }
    }

    private boolean isImageAttachment(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private String attachmentName(String path) {
        String name = Path.of(path).getFileName().toString();
        return name.replaceFirst("^[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}_", "").replace('_', ' ');
    }

    private Node attachmentPreview(Project project, String chatKey, String path, boolean pending) {
        VBox card = new VBox(5);
        card.getStyleClass().add("chat-attachment-card");
        String filename = attachmentName(path);
        if (isImageAttachment(path) && Files.isRegularFile(Path.of(path))) {
            Image image = new Image(Path.of(path).toUri().toString(), pending ? 120 : 460,
                    pending ? 90 : 300, true, true);
            if (!image.isError()) {
                ImageView thumbnail = new ImageView(image);
                thumbnail.setFitWidth(pending ? 120 : 460);
                thumbnail.setFitHeight(pending ? 90 : 300);
                thumbnail.setPreserveRatio(true);
                Button open = new Button();
                open.setGraphic(thumbnail);
                open.getStyleClass().add("chat-image-open");
                open.setTooltip(new Tooltip("Abrir imagem"));
                open.setOnAction(event -> showImage(path, filename));
                card.getChildren().add(open);
            }
        }
        Label name = label(shorten(filename, pending ? 26 : 56), "chat-attachment-name");
        name.setTooltip(new Tooltip(filename));
        HBox footer = row(5, name);
        if (pending) {
            Button remove = new Button();
            remove.setGraphic(UiIcons.icon("x", 13));
            remove.getStyleClass().add("chat-attachment-remove");
            remove.setTooltip(new Tooltip("Remover anexo"));
            remove.setOnAction(event -> {
                List<String> files = pendingAttachments.get(chatKey);
                if (files != null) files.remove(path);
                try {
                    repository.discardAttachment(project.id, Path.of(path));
                } catch (IOException error) {
                    alert("Anexo", "Não foi possível remover o arquivo: " + error.getMessage());
                }
                show("chat");
            });
            footer.getChildren().addAll(grow(), remove);
        }
        card.getChildren().add(footer);
        return card;
    }

    private void showImage(String path, String filename) {
        if (!Files.isRegularFile(Path.of(path))) {
            alert("Imagem", "Este arquivo não está mais disponível.");
            return;
        }
        Image image = new Image(Path.of(path).toUri().toString(), 2400, 2400, true, true);
        if (image.isError()) {
            alert("Imagem", "Não foi possível abrir esta imagem.");
            return;
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(900);
        view.setFitHeight(620);
        StackPane canvas = new StackPane(view);
        canvas.getStyleClass().add("chat-image-viewer");
        Dialog<ButtonType> viewer = new Dialog<>();
        viewer.setTitle(filename);
        viewer.initOwner(stage);
        viewer.getDialogPane().getButtonTypes().add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        viewer.getDialogPane().setContent(canvas);
        viewer.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        viewer.getDialogPane().getStyleClass().add("dark-dialog");
        viewer.showAndWait();
    }

    private void sendChat(Project project, Agent selected, String rawText) {
        ChatThread chat = project.activeChat();
        sendChat(project, chat, selected, rawText, replyDrafts.get(project.id + ":" + chat.id));
    }

    private String queuedPrompt(ChatThread chat, ChatMessage user) {
        ChatMessage reply = user.replyToMessageId == null || user.replyToMessageId.isBlank() ? null
                : chat.messages.stream().filter(item -> item.id.equals(user.replyToMessageId))
                .findFirst().orElse(null);
        String replyContext = reply == null ? "" : "Esta mensagem responde a "
                + replySender(reply) + ": " + shorten(replyExcerpt(reply), 4000) + "\n\n";
        String text = user.text == null ? "" : user.text.trim();
        return replyContext + (text.isBlank() ? "Analise os anexos desta mensagem." : text)
                + knowledgeService.attachmentText(user.attachments);
    }

    private boolean editQueuedMessage(Project project, Task task, String text) {
        ChatMessage message = QueuedMessages.userMessage(project, task);
        if (!QueuedMessages.pending(task) || message == null || text == null || text.length() > 20_000
                || (text.isBlank() && message.attachments.isEmpty())) return false;
        ChatThread chat = project.chat(task.chatId);
        if (chat == null) return false;
        message.text = text.trim();
        task.title = message.text.isBlank() ? "Analisar anexos" : shorten(message.text, 72);
        task.prompt = queuedPrompt(chat, message);
        task.updatedAt = WorkspaceData.now();
        save();
        if (data.activeProjectId.equals(project.id) && screen.equals("chat")) show("chat");
        return true;
    }

    private boolean deleteQueuedMessage(Project project, Task task) {
        ChatMessage message = QueuedMessages.userMessage(project, task);
        ChatThread chat = project.chat(task.chatId);
        if (!QueuedMessages.pending(task) || message == null || chat == null) return false;
        project.tasks.remove(task);
        chat.messages.removeIf(item -> item == message || ("system".equals(item.kind)
                && task.id.equals(item.taskId)));
        for (String attachment : message.attachments) {
            boolean referencedElsewhere = project.allMessages().stream()
                    .anyMatch(item -> item.attachments != null && item.attachments.contains(attachment));
            if (!referencedElsewhere) try { repository.discardAttachment(project.id, Path.of(attachment)); }
            catch (IOException ignored) { }
        }
        save();
        if (data.activeProjectId.equals(project.id) && screen.equals("chat")) show("chat");
        return true;
    }

    private boolean moveQueuedMessage(Project project, Task task, String direction) {
        if (!QueuedMessages.move(project, task, direction)) return false;
        save();
        if (data.activeProjectId.equals(project.id) && screen.equals("chat")) show("chat");
        return true;
    }

    private void editQueuedDialog(Project project, Task task) {
        ChatMessage message = QueuedMessages.userMessage(project, task);
        if (message == null || !QueuedMessages.pending(task)) return;
        Dialog<ButtonType> modal = dialog("Editar mensagem na fila");
        TextArea text = area(message.text, "Mensagem para o agente", 5);
        VBox content = new VBox(10, label("O destinatário e os anexos permanecem os mesmos.", "muted"), text);
        modal.getDialogPane().setContent(content);
        ButtonType saveType = modal.getDialogPane().getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
        ((Button) modal.getDialogPane().lookupButton(saveType)).addEventFilter(ActionEvent.ACTION, event -> {
            if (!editQueuedMessage(project, task, text.getText())) {
                alert("Fila", "A mensagem já começou ou o texto está vazio. Informe até 20 mil caracteres.");
                event.consume();
            }
        });
        modal.showAndWait();
    }

    private void showScheduledDialog(Project project, ChatThread chat, String draft) {
        String chatKey = project.id + ":" + chat.id;
        List<String> selectedFiles = new ArrayList<>(pendingAttachments.getOrDefault(chatKey, List.of()));
        List<String> stagedFiles = new ArrayList<>();
        boolean[] committed = { false };
        Dialog<ButtonType> modal = dialog("Mensagens agendadas");
        ButtonType scheduleAction = modal.getDialogPane().getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
        Button scheduleButton = (Button) modal.getDialogPane().lookupButton(scheduleAction);
        scheduleButton.setText("Agendar");
        TextArea message = area(draft, "Mensagem que será enviada", 3);
        VBox attachmentRows = new VBox(5);
        Runnable updateAttachments = () -> {
            attachmentRows.getChildren().clear();
            for (String path : List.copyOf(selectedFiles)) {
                Label name = label(attachmentName(path), "chat-attachment-name");
                Button remove = button("✕", "chat-queue-action", () -> {
                    selectedFiles.remove(path);
                    if (stagedFiles.remove(path)) try {
                        repository.discardAttachment(project.id, Path.of(path));
                    } catch (IOException ignored) { }
                    attachmentRows.getChildren().removeIf(node -> path.equals(node.getUserData()));
                });
                HBox line = row(8, name, grow(), remove);
                line.setUserData(path);
                attachmentRows.getChildren().add(line);
            }
        };
        updateAttachments.run();
        Button addFiles = button("+ Adicionar arquivos", "outline-button", () -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Arquivos para a mensagem agendada");
            List<java.io.File> chosen = chooser.showOpenMultipleDialog(stage);
            if (chosen == null || chosen.isEmpty()) return;
            if (selectedFiles.size() + chosen.size() > 4) {
                alert("Anexos", "Escolha no máximo quatro arquivos por mensagem.");
                return;
            }
            try {
                long total = 0;
                for (String path : selectedFiles) total += Files.size(Path.of(path));
                for (java.io.File file : chosen) total += Files.size(file.toPath());
                if (total > 8_000_000) {
                    alert("Anexos", "Os arquivos da mensagem devem somar até 8 MB.");
                    return;
                }
                for (java.io.File file : chosen) {
                    String stored = repository.storeAttachment(project.id, file.toPath()).toString();
                    selectedFiles.add(stored);
                    stagedFiles.add(stored);
                }
                updateAttachments.run();
            } catch (IOException error) {
                alert("Anexos", "Não foi possível preparar os arquivos: " + error.getMessage());
            }
        });
        ComboBox<String> mode = new ComboBox<>();
        mode.getItems().addAll("Dia e horário", "Reset de 5 horas", "Reset semanal");
        mode.getSelectionModel().selectFirst();
        mode.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker(LocalDate.now());
        date.getStyleClass().add("input-field");
        TextField time = field(LocalTime.now().plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm")), "HH:mm");
        HBox dateTime = row(8, date, time);
        mode.valueProperty().addListener((obs, before, after) -> {
            boolean visible = mode.getSelectionModel().getSelectedIndex() == 0;
            dateTime.setVisible(visible);
            dateTime.setManaged(visible);
        });
        ComboBox<String> recipients = new ComboBox<>();
        project.agents.forEach(agent -> recipients.getItems().add(agent.name));
        Agent selected = chat.isPrivate() ? project.agent(chat.privateAgentId) : selectedChatAgent(project);
        if (selected != null) recipients.getSelectionModel().select(project.agents.indexOf(selected));
        recipients.setMaxWidth(Double.MAX_VALUE);
        recipients.setDisable(chat.isPrivate());
        VBox pending = new VBox(8);
        List<ScheduledMessage> entries = project.scheduledMessages.stream()
                .filter(item -> chat.id.equals(item.chatId)).sorted(Comparator.comparingLong(item -> item.createdAt)).toList();
        if (entries.isEmpty()) pending.getChildren().add(label("Nenhuma mensagem agendada nesta conversa.", "muted"));
        for (ScheduledMessage entry : entries) {
            Agent recipient = project.agent(entry.agentId);
            String destination = recipient == null ? "Agente removido" : recipient.name;
            String preview = entry.text.isBlank() ? entry.attachments.stream().map(this::attachmentName)
                    .reduce((first, next) -> first + ", " + next).orElse("Mensagem") : entry.text;
            Label detail = label(destination + " · " + scheduledWhen(entry) + "\n" + shorten(preview, 90)
                    + (entry.attachments.isEmpty() ? "" : "\n" + entry.attachments.size() + " anexo(s)")
                    + (entry.lastError.isBlank() ? "" : "\n" + entry.lastError), "muted");
            detail.setWrapText(true);
            Button cancel = button("Cancelar", "link-button", () -> {
                try {
                    scheduledMessages.cancel(project, entry,
                            path -> pendingAttachments.values().stream().anyMatch(files -> files.contains(path)));
                } catch (IOException error) {
                    alert("Agendamento", "Não foi possível cancelar: " + error.getMessage());
                    return;
                }
                pending.getChildren().removeIf(node -> node.getUserData() == entry);
                if (screen.equals("chat") && data.activeProjectId.equals(project.id)) show("chat");
            });
            HBox line = row(8, detail, grow(), cancel);
            line.setUserData(entry);
            pending.getChildren().add(line);
        }
        ScrollPane pendingScroll = scroll(pending);
        pendingScroll.setPrefHeight(Math.min(190, 42 + entries.size() * 62));
        VBox content = new VBox(10, label("MENSAGEM", "eyebrow"), message,
                label("ARQUIVOS", "eyebrow"), addFiles, attachmentRows,
                label("DESTINATÁRIO", "eyebrow"), recipients,
                label("ENVIAR", "eyebrow"), mode, dateTime,
                label("AGENDADAS NESTA CONVERSA", "eyebrow"), pendingScroll,
                label("O computador precisa estar ligado com o Maestro aberto. Se estiver fechado no horário, a mensagem será enviada ao reabrir.", "muted"));
        modal.getDialogPane().setContent(content);
        scheduleButton.addEventFilter(ActionEvent.ACTION, event -> {
            String text = message.getText().trim();
            int recipientIndex = recipients.getSelectionModel().getSelectedIndex();
            if ((text.isBlank() && selectedFiles.isEmpty()) || recipientIndex < 0) {
                alert("Agendamento", "Escreva uma mensagem ou anexe um arquivo e selecione um agente.");
                event.consume(); return;
            }
            try {
                long total = 0;
                for (String path : selectedFiles) total += Files.size(Path.of(path));
                if (selectedFiles.size() > 4 || total > 8_000_000)
                    throw new IOException("Use até quatro arquivos, somando no máximo 8 MB.");
            } catch (IOException error) {
                alert("Anexos", error.getMessage());
                event.consume(); return;
            }
            ScheduledMessage entry = new ScheduledMessage();
            entry.chatId = chat.id;
            entry.text = text;
            entry.attachments.addAll(selectedFiles);
            ChatMessage reply = replyDrafts.get(chatKey);
            entry.replyToMessageId = reply == null ? "" : reply.id;
            Agent recipient = project.agents.get(recipientIndex);
            if (!chat.isPrivate()) {
                Agent replyOwner = reply == null ? null : messageOwner(project, reply);
                if (replyOwner != null) recipient = replyOwner;
                Agent mentioned = ChatMentions.firstRecipient(project.agents, text);
                if (mentioned != null) recipient = mentioned;
            }
            entry.agentId = recipient.id;
            entry.kind = switch (mode.getSelectionModel().getSelectedIndex()) {
                case 1 -> "shortReset";
                case 2 -> "weeklyReset";
                default -> "time";
            };
            if ("time".equals(entry.kind)) {
                try {
                    LocalDateTime target = LocalDateTime.of(date.getValue(),
                            LocalTime.parse(time.getText().trim(), DateTimeFormatter.ofPattern("HH:mm")));
                    entry.dueAt = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                } catch (RuntimeException error) {
                    alert("Agendamento", "Informe uma data e um horário válidos no formato HH:mm.");
                    event.consume(); return;
                }
                if (entry.dueAt <= System.currentTimeMillis()) {
                    alert("Agendamento", "Escolha uma data e um horário futuros.");
                    event.consume(); return;
                }
            }
            try { scheduledMessages.schedule(project, entry); }
            catch (IOException error) {
                alert("Agendamento", "Não foi possível salvar: " + error.getMessage());
                event.consume(); return;
            }
            committed[0] = true;
            List<String> composerFiles = pendingAttachments.get(chatKey);
            if (composerFiles != null) composerFiles.removeAll(selectedFiles);
            drafts.remove(chatKey);
            replyDrafts.remove(chatKey);
            if (screen.equals("chat") && data.activeProjectId.equals(project.id)) show("chat");
            processScheduledMessages();
        });
        modal.showAndWait();
        if (!committed[0]) for (String path : stagedFiles) try {
            repository.discardAttachment(project.id, Path.of(path));
        } catch (IOException ignored) { }
    }

    private String scheduledWhen(ScheduledMessage entry) {
        if (entry.dueAt > 0) return Instant.ofEpochMilli(entry.dueAt)
                .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        return "shortReset".equals(entry.kind) ? "Aguardando horário do reset de 5 horas"
                : "Aguardando horário do reset semanal";
    }

    private void processScheduledMessages() {
        long now = System.currentTimeMillis();
        if (screen.equals("chat")) refreshUsage(codexService.executable(), false);
        boolean needsUsage = false;
        for (Project project : data.projects) {
            for (ScheduledMessage entry : new ArrayList<>(project.scheduledMessages)) {
                if (entry.dueAt == 0) { needsUsage = true; continue; }
                if (entry.dueAt > now || now - entry.lastAttemptAt < 60_000) continue;
                ChatThread chat = project.chat(entry.chatId);
                Agent recipient = project.agent(entry.agentId);
                if (chat == null || recipient == null) {
                    entry.lastError = "Conversa ou agente não existe mais.";
                    entry.lastAttemptAt = now;
                    save(); continue;
                }
                if (entry.attachments.stream().anyMatch(path -> !Files.isRegularFile(Path.of(path)))) {
                    entry.lastError = "Um anexo não está mais disponível. Cancele e agende novamente.";
                    entry.lastAttemptAt = now;
                    save(); continue;
                }
                ChatMessage reply = chat.messages.stream().filter(item -> item.id.equals(entry.replyToMessageId))
                        .findFirst().orElse(null);
                project.scheduledMessages.remove(entry);
                try {
                    if (!sendChat(project, chat, recipient, entry.text, reply, true, entry.attachments, true)) {
                        entry.lastError = "Aguardando pasta do projeto e conexão com o Codex.";
                        entry.lastAttemptAt = now;
                        project.scheduledMessages.add(entry);
                        save();
                    }
                } catch (RuntimeException error) {
                    entry.lastError = "Falha no envio: " + error.getMessage();
                    entry.lastAttemptAt = now;
                    project.scheduledMessages.add(entry);
                    save();
                }
            }
        }
        if (needsUsage) resolveScheduledResets(now);
    }

    private void resolveScheduledResets(long now) {
        if (scheduledUsageLoading || now - scheduledUsageLastAttemptAt < 120_000) return;
        scheduledUsageLastAttemptAt = now;
        scheduledUsageLoading = true;
        String executable = codexService.executable();
        scheduleWorker.submit(() -> {
            CodexUsageService.UsageSnapshot snapshot = null;
            String error = "";
            try { snapshot = usageService.read(executable); }
            catch (IOException failure) { error = failure.getMessage(); }
            CodexUsageService.UsageSnapshot result = snapshot;
            String failure = error;
            Platform.runLater(() -> {
                scheduledUsageLoading = false;
                boolean changed = false;
                for (Project project : data.projects) for (ScheduledMessage entry : project.scheduledMessages) {
                    if (entry.dueAt > 0 || "time".equals(entry.kind)) continue;
                    CodexUsageService.Window window = result == null ? null
                            : "weeklyReset".equals(entry.kind) ? result.weeklyWindow() : result.shortWindow();
                    if (window != null && window.resetsAt() != null && window.resetsAt() * 1000 > System.currentTimeMillis()) {
                        entry.dueAt = window.resetsAt() * 1000;
                        entry.lastError = "";
                        changed = true;
                    } else {
                        entry.lastError = failure.isBlank() ? "Horário do reset indisponível no Codex." : failure;
                        changed = true;
                    }
                }
                if (changed) save();
            });
        });
    }

    private void sendChat(Project project, ChatThread chat, Agent selected, String rawText) {
        sendChat(project, chat, selected, rawText, null);
    }

    private void sendChat(Project project, ChatThread chat, Agent selected, String rawText,
                          ChatMessage replyTo) {
        sendChat(project, chat, selected, rawText, replyTo, false, List.of());
    }

    private boolean sendChat(Project project, ChatThread chat, Agent selected, String rawText,
                             ChatMessage replyTo, boolean mobile, List<String> mobileAttachments) {
        return sendChat(project, chat, selected, rawText, replyTo, mobile, mobileAttachments, false);
    }

    private boolean sendChat(Project project, ChatThread chat, Agent selected, String rawText,
                             ChatMessage replyTo, boolean mobile, List<String> mobileAttachments,
                             boolean fixedRecipient) {
        String text = rawText.trim();
        String chatKey = project.id + ":" + chat.id;
        List<String> files = new ArrayList<>(mobile ? mobileAttachments
                : pendingAttachments.getOrDefault(chatKey, List.of()));
        if (text.isBlank() && files.isEmpty()) return false;
        if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder))) {
            if (!mobile) alert("Pasta do projeto", "Vincule uma pasta válida em Configurações antes de enviar a tarefa.");
            return false;
        }
        if (!codexService.available()) {
            if (!mobile) alert("Codex", "Codex CLI não encontrado. Veja a tela Configurações.");
            return false;
        }
        Agent recipient = chat.isPrivate() ? project.agent(chat.privateAgentId) : selected;
        if (!chat.isPrivate() && !fixedRecipient) {
            if (replyTo != null && messageOwner(project, replyTo) != null)
                recipient = messageOwner(project, replyTo);
            Agent mentioned = ChatMentions.firstRecipient(project.agents, text);
            if (mentioned != null) recipient = mentioned;
        }
        if (recipient == null) {
            if (!mobile) alert("Agente", chat.isPrivate() ? "Este agente não está mais no projeto."
                    : "Crie um agente para receber a mensagem.");
            return false;
        }
        if (!mobile) project.activeChatId = chat.id;
        ChatMessage user = ChatMessage.of("user", recipient.id, "Você → " + recipient.name, text);
        if (replyTo != null) {
            user.replyToMessageId = replyTo.id;
            user.replySender = replySender(replyTo);
            user.replyText = shorten(replyExcerpt(replyTo), 240);
        }
        user.attachments.addAll(files);
        chat.messages.add(user);

        Task task = new Task();
        task.chatId = chat.id;
        task.title = text.isBlank() ? "Analisar anexos" : shorten(text, 72);
        task.agentId = recipient.id;
        task.prompt = queuedPrompt(chat, user);
        task.attachments.addAll(files);
        user.taskId = task.id;
        project.tasks.add(task);
        String recipientId = recipient.id;
        if (!mobile) {
            drafts.put(chatKey, "");
            if (replyTo != null) replyDrafts.remove(chatKey, replyTo);
            pendingAttachments.remove(chatKey);
        }

        if (hasPausedQueue(project, recipient)) {
            task.status = "Pausada";
            ChatMessage notice = ChatMessage.of("system", "", "Sistema",
                    "A nova tarefa de " + recipient.name + " ficou na fila pausada. Retome pela tela Tarefas.");
            notice.taskId = task.id;
            chat.messages.add(notice);
        } else if (!"disponível".equals(recipient.status)
                || project.tasks.stream().anyMatch(item -> item != task && recipientId.equals(item.agentId)
                && QueuedMessages.pending(item))) {
            task.status = "Na fila";
            ChatMessage notice = ChatMessage.of("system", "", "Sistema",
                    recipient.name + " receberá sua mensagem quando chegar a vez dela na fila.");
            notice.taskId = task.id;
            chat.messages.add(notice);
            if ("disponível".equals(recipient.status)) startNextQueuedTask(project, recipient);
        } else if (recipient.canEditFiles && "aprovar".equals(project.writePolicy)) {
            task.status = "Aguardando aprovação";
            Approval approval = new Approval();
            approval.taskId = task.id;
            approval.agentId = recipient.id;
            approval.title = "Permitir que " + recipient.name + " edite arquivos?";
            approval.detail = "Esta tarefa poderá alterar arquivos dentro da pasta do projeto: " + task.title;
            approval.actionPreview = project.folder;
            project.approvals.add(approval);
            chat.messages.add(ChatMessage.of("approval", approval.id, recipient.name, approval.title));
        } else {
            startTask(project, recipient, task, files);
        }
        save();
        if (!mobile || (screen.equals("chat") && data.activeProjectId.equals(project.id)
                && project.activeChatId.equals(chat.id))) show("chat");
        return true;
    }

    private void delegateAgentMessage(Project project, ChatThread chat, ChatMessage source, boolean automatic) {
        if (chat.isPrivate() || !"agent".equals(source.kind)
                || project.tasks.stream().anyMatch(task -> source.id.equals(task.delegatedFromMessageId))) return;
        Agent sender = project.agent(source.agentId);
        List<Agent> sequence = handoffSequence(project, source);
        Agent recipient = sequence.isEmpty() ? null : sequence.getFirst();
        if (sender == null || recipient == null) return;
        Task parent = project.task(source.taskId);
        int depth = parent == null ? 1 : parent.delegationDepth + 1;
        if (automatic && depth > MAX_AUTOMATIC_HANDOFFS) {
            chat.messages.add(ChatMessage.of("system", "", "Sistema",
                    "A sequência automática chegou a " + MAX_AUTOMATIC_HANDOFFS
                            + " encaminhamentos. Use o botão na resposta para continuar."));
            return;
        }
        if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder)) || !codexService.available()) {
            chat.messages.add(ChatMessage.of("error", recipient.id, "Sistema",
                    "Não foi possível encaminhar para " + recipient.name
                            + ": confira a pasta do projeto e a conexão com o Codex."));
            if (!automatic) { save(); show(screen); }
            return;
        }

        Task task = new Task();
        task.chatId = chat.id;
        task.agentId = recipient.id;
        task.delegatedFromMessageId = source.id;
        task.delegationDepth = depth;
        sequence.stream().skip(1).map(agent -> agent.id).forEach(task.followupAgentIds::add);
        String sourceText = source.text == null ? "" : source.text;
        task.title = "De " + sender.name + " para " + recipient.name + ": " + shorten(sourceText, 52);
        String originalPlan = parent != null && parent.handoffPlan != null && !parent.handoffPlan.isBlank()
                ? parent.handoffPlan : sourceText;
        task.handoffPlan = originalPlan.length() > 18_000 ? originalPlan.substring(0, 18_000) : originalPlan;
        String recent = sourceText.length() > 12_000 ? sourceText.substring(0, 12_000) : sourceText;
        task.prompt = "O agente " + sender.name + " encaminhou esta tarefa para você na conversa '"
                + chat.name + "'. Execute agora a sua etapa como " + recipient.name
                + ". Use o contexto compartilhado para entender o pedido original e o trabalho anterior. "
                + "Ao concluir, relate o resultado no chat. As próximas etapas já estão programadas em sequência; "
                + "não é preciso mencionar esses agentes de novo para iniciá-las.\n\nPlano original:\n"
                + task.handoffPlan + (sourceText.equals(task.handoffPlan) ? ""
                : "\n\nResultado mais recente de " + sender.name + ":\n" + recent);
        project.tasks.add(task);
        ChatMessage notice = ChatMessage.of("system", "", "Sistema",
                sender.name + " encaminhou uma tarefa para @" + recipient.name + ".");
        notice.taskId = task.id;
        chat.messages.add(notice);
        dispatchDelegatedTask(project, recipient, task);
        if (!automatic) { save(); show(screen); }
    }

    private Agent nextHandoffRecipient(Project project, ChatMessage source) {
        List<Agent> sequence = handoffSequence(project, source);
        return sequence.isEmpty() ? null : sequence.getFirst();
    }

    private List<Agent> handoffSequence(Project project, ChatMessage source) {
        Agent sender = project.agent(source.agentId);
        if (sender == null) return List.of();
        List<Agent> sequence = new ArrayList<>(AgentHandoff.recipients(project, sender, source.text));
        Task parent = project.task(source.taskId);
        if (parent != null && parent.followupAgentIds != null) {
            for (String id : parent.followupAgentIds) {
                Agent planned = project.agent(id);
                if (planned != null && !planned.id.equals(sender.id)
                        && sequence.stream().noneMatch(existing -> existing.id.equals(planned.id)))
                    sequence.add(planned);
            }
        }
        return sequence;
    }

    private void dispatchDelegatedTask(Project project, Agent agent, Task task) {
        ChatThread chat = project.chatFor(task);
        task.updatedAt = WorkspaceData.now();
        if (hasPausedQueue(project, agent)) {
            task.status = "Pausada";
            chat.messages.add(ChatMessage.of("system", "", "Sistema",
                    "A fila de " + agent.name + " está pausada. Retome pela tela Tarefas quando quiser."));
            return;
        }
        if (!"disponível".equals(agent.status)) {
            task.status = "Na fila";
            chat.messages.add(ChatMessage.of("system", "", "Sistema",
                    agent.name + " está ocupado; a tarefa entrará em andamento quando ele terminar."));
            return;
        }
        boolean approved = project.approvals.stream().anyMatch(approval -> task.id.equals(approval.taskId)
                && "Aprovado".equals(approval.status));
        if (agent.canEditFiles && "aprovar".equals(project.writePolicy) && !approved) {
            task.status = "Aguardando aprovação";
            Approval approval = new Approval();
            approval.taskId = task.id;
            approval.agentId = agent.id;
            approval.title = "Permitir que " + agent.name + " edite arquivos?";
            approval.detail = task.delegatedFromMessageId == null || task.delegatedFromMessageId.isBlank()
                    ? "Tarefa enviada no chat: " + task.title
                    : "Tarefa encaminhada por outro agente: " + task.title;
            approval.actionPreview = project.folder;
            project.approvals.add(approval);
            chat.messages.add(ChatMessage.of("approval", approval.id, agent.name, approval.title));
            return;
        }
        startTask(project, agent, task, task.attachments == null ? List.of() : task.attachments);
    }

    private void startNextQueuedTask(Project project, Agent agent) {
        if (!"disponível".equals(agent.status) || hasPausedQueue(project, agent)) return;
        project.tasks.stream().filter(task -> agent.id.equals(task.agentId)
                && "Na fila".equals(task.status)).findFirst()
                .ifPresent(task -> dispatchDelegatedTask(project, agent, task));
    }

    private boolean hasPausedQueue(Project project, Agent agent) {
        return project.tasks.stream().anyMatch(task -> agent.id.equals(task.agentId)
                && "Pausada".equals(task.status));
    }

    private void resumeQueuedTasks() {
        boolean resumed = false;
        for (Project project : data.projects) {
            if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder))) continue;
            for (Agent agent : project.agents) {
                if (hasPausedQueue(project, agent)) continue;
                Task queued = project.tasks.stream().filter(task -> agent.id.equals(task.agentId)
                        && "Na fila".equals(task.status)).findFirst().orElse(null);
                if (queued == null || !"disponível".equals(agent.status)) continue;
                dispatchDelegatedTask(project, agent, queued);
                resumed = true;
            }
        }
        if (resumed) { save(); show(screen); }
    }

    private void approve(Project project, Approval approval) {
        if (!approval.status.equals("Pendente")) return;
        approval.status = "Aprovado";
        Task task = project.task(approval.taskId);
        Agent agent = project.agent(approval.agentId);
        if (task != null && agent != null) {
            if (hasPausedQueue(project, agent)) {
                task.status = "Pausada";
                task.updatedAt = WorkspaceData.now();
                project.chatFor(task).messages.add(ChatMessage.of("system", "", "Sistema",
                        "A tarefa foi aprovada e ficou na fila pausada de " + agent.name + "."));
            } else if (!"disponível".equals(agent.status)) {
                task.status = "Na fila";
                task.updatedAt = WorkspaceData.now();
                project.chatFor(task).messages.add(ChatMessage.of("system", "", "Sistema",
                        "A tarefa foi aprovada e aguarda " + agent.name + " terminar o trabalho atual."));
            } else {
                startTask(project, agent, task,
                        task.attachments == null ? List.of() : task.attachments);
            }
        }
        save();
        show(screen);
    }

    private void reject(Project project, Approval approval) {
        if (!approval.status.equals("Pendente")) return;
        approval.status = "Negado";
        Task task = project.task(approval.taskId);
        if (task != null) { task.status = "Cancelada"; task.updatedAt = WorkspaceData.now(); }
        project.chatFor(task).messages.add(ChatMessage.of("system", "", "Sistema",
                "Você negou a tarefa: " + approval.title));
        Agent agent = project.agent(approval.agentId);
        if (agent != null) startNextQueuedTask(project, agent);
        save();
        show(screen);
    }

    private record QuestionPayload(String preface, String question, List<QuestionChoice> choices, int recommended) { }

    private QuestionPayload parseQuestion(String answer) {
        if (answer == null) return null;
        String opening = "<maestro-question>";
        String closing = "</maestro-question>";
        int start = answer.lastIndexOf(opening);
        if (start < 0) return null;
        int end = answer.indexOf(closing, start + opening.length());
        if (end < 0 || !answer.substring(end + closing.length()).isBlank()) return null;
        String json = answer.substring(start + opening.length(), end).trim();
        if (json.length() > 8_000) return null;
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String question = object.get("question").getAsString().trim();
            JsonArray options = object.getAsJsonArray("options");
            if (question.isBlank() || options == null || options.size() < 2 || options.size() > 4) return null;
            List<QuestionChoice> choices = new ArrayList<>();
            for (JsonElement element : options) {
                JsonObject item = element.getAsJsonObject();
                String title = item.get("title").getAsString().trim();
                String description = item.has("description") ? item.get("description").getAsString().trim() : "";
                if (title.isBlank()) return null;
                choices.add(new QuestionChoice(title, description));
            }
            int recommended = object.has("recommended") ? object.get("recommended").getAsInt() : 0;
            recommended = Math.max(0, Math.min(choices.size() - 1, recommended));
            return new QuestionPayload(answer.substring(0, start).trim(), question, choices, recommended);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void answerQuestion(Project project, Approval question, boolean skip) {
        answerQuestion(project, question, skip, false);
    }

    private void answerQuestion(Project project, Approval question, boolean skip, boolean mobile) {
        if (!"Pendente".equals(question.status)) return;
        Agent agent = project.agent(question.agentId);
        if (agent == null) {
            if (!mobile) alert("Pergunta", "O agente desta pergunta não está mais no projeto.");
            return;
        }
        String response;
        if (skip) {
            response = "Pode prosseguir sem minha escolha. Explique qual caminho adotou.";
        } else if (question.selectedChoice >= question.choices.size()) {
            response = question.responseDraft.trim();
        } else {
            int index = Math.max(0, Math.min(question.choices.size() - 1, question.selectedChoice));
            response = question.choices.get(index).title;
        }
        if (response.isBlank()) {
            if (!mobile) alert("Pergunta", "Escreva uma resposta antes de enviar.");
            return;
        }
        String previousScreen = screen;
        Task waiting = project.task(question.taskId);
        ChatThread chat = project.chatFor(waiting);
        int before = chat.messages.size();
        String previousStatus = question.status;
        String previousResponse = question.response;
        question.status = skip ? "Ignorada" : "Respondida";
        question.response = response;
        String previousTaskStatus = waiting == null ? "" : waiting.status;
        int previousTaskProgress = waiting == null ? 0 : waiting.progress;
        String previousTaskUpdate = waiting == null ? "" : waiting.updatedAt;
        if (waiting != null && "Aguardando resposta".equals(waiting.status)) {
            waiting.status = "Concluída";
            waiting.progress = 100;
            waiting.updatedAt = WorkspaceData.now();
        }
        sendChat(project, chat, agent, "@" + agent.name + " Resposta à pergunta '" + question.title + "': " + response,
                null, mobile, List.of());
        if (chat.messages.size() == before) {
            question.status = previousStatus;
            question.response = previousResponse;
            if (waiting != null) {
                waiting.status = previousTaskStatus;
                waiting.progress = previousTaskProgress;
                waiting.updatedAt = previousTaskUpdate;
            }
            return;
        }
        chat.messages.stream().skip(before).filter(message -> "user".equals(message.kind))
                .findFirst().ifPresent(chat.messages::remove);
        save();
        if (!mobile) show(previousScreen);
    }

    private void startTask(Project project, Agent agent, Task task, List<String> attachments) {
        ChatThread chat = project.chatFor(task);
        Task previousTask = project.tasks.stream()
                .filter(item -> agent.id.equals(item.agentId) && chat.id.equals(item.chatId)
                        && !task.id.equals(item.id))
                .reduce((first, second) -> second).orElse(null);
        if (previousTask != null && "Falhou".equals(previousTask.status)) agent.chatSessions.remove(chat.id);
        task.status = "Em andamento";
        task.progress = 10;
        task.updatedAt = WorkspaceData.now();
        agent.status = agent.canEditFiles && !"bloquear".equals(project.writePolicy) ? "trabalhando" : "lendo";
        String context = knowledgeService.retrieve(project, task.prompt, agent.id);
        String sharedContext = sharedProjectContext.build(project, chat, task);
        String chatSessionId = agent.chatSessions.getOrDefault(chat.id, "");
        List<String> copiedAttachments = new ArrayList<>(attachments);
        CompletableFuture<Void> run = CompletableFuture.runAsync(() -> codexService.run(project, agent, task, context, sharedContext,
                copiedAttachments, chatSessionId,
                event -> Platform.runLater(() -> {
                    if (task.status.equals("Cancelada") || task.status.equals("Interrompendo")) return;
                    switch (event.kind()) {
                        case "session" -> {
                            if (!event.sessionId().isBlank()) agent.chatSessions.put(chat.id, event.sessionId());
                        }
                        case "activity", "note", "progress", "status" -> {
                            task.progress = Math.max(task.progress, 25);
                            ChatMessage update = ChatMessage.of("progress", agent.id, agent.name, event.text());
                            update.taskId = task.id;
                            update.progressType = event.kind().equals("note") ? "note" : "activity";
                            chat.messages.add(update);
                            List<ChatMessage> previous = chat.messages.stream()
                                    .filter(message -> message.kind.equals("progress") && task.id.equals(message.taskId))
                                    .toList();
                            if (previous.size() > 40) chat.messages.remove(previous.getFirst());
                        }
                        case "complete" -> {
                            QuestionPayload question = parseQuestion(event.text());
                            task.status = question == null ? "Concluída" : "Aguardando resposta";
                            task.progress = question == null ? 100 : 90;
                            agent.status = "disponível";
                            if (question == null) {
                                ChatMessage answer = ChatMessage.of("agent", agent.id, agent.name, event.text());
                                answer.taskId = task.id;
                                chat.messages.add(answer);
                                if (pushNotifications != null)
                                    pushNotifications.message(project, chat, agent, answer);
                                delegateAgentMessage(project, chat, answer, true);
                                startNextQueuedTask(project, agent);
                            } else {
                                if (!question.preface().isBlank()) {
                                    ChatMessage preface = ChatMessage.of("agent", agent.id, agent.name,
                                            question.preface());
                                    preface.taskId = task.id;
                                    chat.messages.add(preface);
                                    if (pushNotifications != null)
                                        pushNotifications.message(project, chat, agent, preface);
                                }
                                Approval request = new Approval();
                                request.kind = "question";
                                request.taskId = task.id;
                                request.agentId = agent.id;
                                request.title = question.question();
                                request.choices.addAll(question.choices());
                                request.recommendedChoice = question.recommended();
                                request.selectedChoice = question.recommended();
                                project.approvals.add(request);
                                chat.messages.add(ChatMessage.of("approval", request.id, agent.name, request.title));
                            }
                        }
                        case "error" -> {
                            task.status = "Falhou";
                            agent.status = "disponível";
                            chat.messages.add(ChatMessage.of("error", agent.id, "Codex", event.text()));
                            startNextQueuedTask(project, agent);
                        }
                        default -> { }
                    }
                    task.updatedAt = WorkspaceData.now();
                    save();
                    if (project().id.equals(project.id)) show(screen);
                })), workers);
        taskRuns.put(task.id, run);
        run.whenComplete((ignored, failure) -> Platform.runLater(() -> {
            taskRuns.remove(task.id, run);
            if (failure != null && "Em andamento".equals(task.status)) {
                task.status = "Falhou";
                task.updatedAt = WorkspaceData.now();
                agent.status = "disponível";
                chat.messages.add(ChatMessage.of("error", agent.id, "Codex",
                        "Falha inesperada ao executar a tarefa: " + failure.getMessage()));
                startNextQueuedTask(project, agent);
                save();
                if (project() != null && project().id.equals(project.id)) show(screen);
            }
        }));
    }

    private Node agents() {
        Project project = project();
        VBox page = page();
        page.getChildren().add(row(16,
                heading("Agentes", project.agents.size() + " agentes neste projeto. Cada um tem sua função e modo de acesso."),
                grow(), button("+ Novo agente", "primary-button", () -> editAgent(null))));

        HBox filters = row(8);
        for (String option : List.of("Todos", "Ativos", "Disponíveis")) {
            Button filter = button(option, agentFilter.equals(option) ? "segment selected" : "segment", () -> {
                agentFilter = option;
                show("agents");
            });
            filters.getChildren().add(filter);
        }
        filters.getChildren().add(grow());
        TextField search = field("", "Buscar agente");
        search.setPrefWidth(300);
        filters.getChildren().add(search);
        page.getChildren().add(filters);

        FlowPane grid = new FlowPane(16, 16);
        grid.setPrefWrapLength(1_150);
        for (Agent agent : project.agents) {
            if (agentFilter.equals("Ativos") && agent.status.equals("disponível")) continue;
            if (agentFilter.equals("Disponíveis") && !agent.status.equals("disponível")) continue;
            grid.getChildren().add(agentCard(project, agent));
        }
        page.getChildren().add(grid);
        search.textProperty().addListener((o, before, after) -> {
            grid.getChildren().clear();
            for (Agent agent : project.agents) {
                if (!agent.name.toLowerCase(Locale.ROOT).contains(after.toLowerCase(Locale.ROOT))
                        && !agent.role.toLowerCase(Locale.ROOT).contains(after.toLowerCase(Locale.ROOT))) continue;
                if (agentFilter.equals("Ativos") && agent.status.equals("disponível")) continue;
                if (agentFilter.equals("Disponíveis") && !agent.status.equals("disponível")) continue;
                grid.getChildren().add(agentCard(project, agent));
            }
        });
        return scroll(page);
    }

    private VBox agentCard(Project project, Agent agent) {
        VBox card = card();
        card.getStyleClass().add("agent-card");
        card.setPrefWidth(330);
        card.setMinHeight(290);
        long count = project.tasks.stream().filter(t -> t.agentId.equals(agent.id)).count();
        card.getChildren().add(row(12, avatar(agent),
                new VBox(2, label(agent.name, "card-title"),
                        row(6, dot(agent.status.equals("disponível") ? "#999999" : "#76bb8b"),
                                label(agent.status, agent.status.equals("disponível") ? "muted" : "green-text"))),
                grow(), button("Conversar", "outline-button", () -> openPrivateChat(project, agent))));
        card.getChildren().add(label(agent.role, "message-text"));
        FlowPane capabilities = new FlowPane(7, 7);
        capabilities.getChildren().addAll(badge("Terminal"),
                badge(agent.canEditFiles ? "Arquivos (escrita)" : "Arquivos (leitura)"),
                badge("Git"));
        card.getChildren().add(capabilities);
        card.getChildren().add(label(agent.canEditFiles
                ? "Pede aprovação antes de editar arquivos"
                : "Executa tarefas em modo de leitura", "tiny-muted"));
        card.getChildren().add(growV());
        card.getChildren().add(new Separator());
        card.getChildren().add(row(9, label(agent.model, "tiny-muted"), grow(),
                label(count + " tarefas", "tiny-muted"),
                button("Editar", "outline-button", () -> editAgent(agent))));
        return card;
    }

    private void editAgent(Agent existing) {
        if (existing != null && !existing.status.equals("disponível")) {
            alert("Agente ocupado", "Aguarde a tarefa em andamento terminar antes de editar este agente.");
            return;
        }
        Dialog<ButtonType> dialog = dialog(existing == null ? "Novo agente" : "Editar agente");
        TextField name = field(existing == null ? "" : existing.name, "Nome do agente");
        TextField role = field(existing == null ? "" : existing.role, "O que este agente faz?");
        TextArea instructions = area(existing == null ? "" : existing.instructions,
                "Instruções que serão enviadas ao Codex", 5);
        TextField model = field(existing == null ? "" : existing.model, "Padrão do Codex");
        CheckBox writes = new CheckBox("Pode editar arquivos após minha aprovação");
        writes.setSelected(existing != null && existing.canEditFiles);
        AgentAnimations currentAnimations = existing == null || existing.animations == null
                ? new AgentAnimations() : existing.animations;
        CheckBox floatMotion = animationChoice("Flutuar sempre", currentAnimations.floatMotion);
        CheckBox blink = animationChoice("Piscar", currentAnimations.blink);
        CheckBox pulse = animationChoice("Pulsar antena (Orbe)", currentAnimations.pulse);
        CheckBox wave = animationChoice("Acenar", currentAnimations.wave);
        CheckBox sneeze = animationChoice("Espirrar", currentAnimations.sneeze);
        CheckBox sleep = animationChoice("Dormir e acordar (somente em descanso)", currentAnimations.sleep);
        CheckBox read = animationChoice("Ler com livro e óculos (somente em atividade)", currentAnimations.read);
        CheckBox computer = animationChoice("Digitar no computador e tomar café (somente em atividade)",
                currentAnimations.computer);
        FlowPane animationChoices = new FlowPane(12, 10,
                floatMotion, blink, pulse, wave, sneeze, sleep, read, computer);
        animationChoices.setPrefWrapLength(450);
        ComboBox<String> colorPreset = new ComboBox<>();
        colorPreset.getItems().addAll("Azul", "Verde", "Âmbar", "Roxo", "Rosa", "Turquesa",
                "Vermelho", "Laranja", "Amarelo", "Cinza", "Personalizada");
        String[] colors = {"#6c9ed6", "#76bb8b", "#d7a352", "#a88ad2", "#d486aa", "#72c3c5",
                "#df776f", "#e99b61", "#e4cf71", "#a7afb9"};
        String initialColor = existing == null ? colors[0] : existing.color;
        String initialEyeColor = existing == null ? "#14171b" : existing.eyeColor;
        int currentColor = -1;
        for (int index = 0; index < colors.length; index++) {
            if (colors[index].equalsIgnoreCase(initialColor)) currentColor = index;
        }
        colorPreset.getSelectionModel().select(currentColor < 0 ? colors.length : currentColor);
        colorPreset.setPrefWidth(145);
        TextField colorHex = field(initialColor, "#RRGGBB");
        TextField eyeHex = field(initialEyeColor, "#RRGGBB");
        colorHex.setPrefColumnCount(9);
        eyeHex.setPrefColumnCount(9);
        Region colorSwatch = hexSwatch(initialColor);
        Region eyeSwatch = hexSwatch(initialEyeColor);
        Label colorError = hexError();
        Label eyeError = hexError();
        HBox colorRow = row(9, colorPreset, colorSwatch, colorHex);
        HBox eyeRow = row(9, eyeSwatch, eyeHex);
        HBox.setHgrow(colorHex, Priority.ALWAYS);
        HBox.setHgrow(eyeHex, Priority.ALWAYS);
        ToggleGroup visualGroup = new ToggleGroup();
        FlowPane visuals = new FlowPane(8, 8);
        visuals.setPrefWrapLength(440);
        String selectedVisual = existing == null ? "orbe" : AgentCharacterView.normalized(existing.visualId);
        for (AgentCharacterView.Design design : AgentCharacterView.DESIGNS) {
            ToggleButton option = new ToggleButton();
            option.setUserData(design.id());
            option.setToggleGroup(visualGroup);
            option.setGraphic(visualOptionGraphic(design, initialColor, initialEyeColor, currentAnimations));
            option.getStyleClass().add("visual-option");
            option.setSelected(design.id().equals(selectedVisual));
            visuals.getChildren().add(option);
        }
        Runnable refreshPreviews = () -> {
            String bodyValue = hexColor(colorHex.getText());
            String eyeValue = hexColor(eyeHex.getText());
            if (bodyValue == null || eyeValue == null) return;
            AgentAnimations previewChoices = animationChoices(floatMotion, blink, pulse,
                    wave, sneeze, sleep, read, computer);
            for (Node node : visuals.getChildren()) {
                if (!(node instanceof ToggleButton option)) continue;
                AgentCharacterView.DESIGNS.stream()
                        .filter(design -> design.id().equals(option.getUserData()))
                        .findFirst().ifPresent(design -> option.setGraphic(
                                visualOptionGraphic(design, bodyValue, eyeValue, previewChoices)));
            }
        };
        for (CheckBox choice : List.of(floatMotion, blink, pulse, wave, sneeze, sleep, read, computer))
            choice.selectedProperty().addListener((observable, previous, selected) -> refreshPreviews.run());
        colorPreset.setOnAction(event -> {
            int index = colorPreset.getSelectionModel().getSelectedIndex();
            if (index >= 0 && index < colors.length && !colors[index].equalsIgnoreCase(colorHex.getText()))
                colorHex.setText(colors[index]);
        });
        colorHex.textProperty().addListener((observable, previous, value) -> {
            String valid = hexColor(value);
            updateHexField(colorHex, colorSwatch, colorError, valid);
            int match = colors.length;
            if (valid != null) {
                for (int index = 0; index < colors.length; index++) {
                    if (colors[index].equalsIgnoreCase(valid)) match = index;
                }
            }
            if (colorPreset.getSelectionModel().getSelectedIndex() != match)
                colorPreset.getSelectionModel().select(match);
            refreshPreviews.run();
        });
        eyeHex.textProperty().addListener((observable, previous, value) -> {
            updateHexField(eyeHex, eyeSwatch, eyeError, hexColor(value));
            refreshPreviews.run();
        });
        VBox form = new VBox(11,
                label("Nome", "field-label"), name,
                label("Função", "field-label"), role,
                label("Instruções", "field-label"), instructions,
                label("Modelo (opcional)", "field-label"), model,
                label("Cor do agente", "field-label"), colorRow, colorError,
                label("Cor dos olhos", "field-label"), eyeRow, eyeError,
                label("Visual do agente", "field-label"), visuals,
                label("Animações", "field-label"), animationChoices, writes);
        form.setPadding(new Insets(8));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.setPrefViewportHeight(620);
        dialog.getDialogPane().setContent(formScroll);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane()
                .getButtonTypes().stream().filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .findFirst().orElseThrow());
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (hexColor(colorHex.getText()) == null) {
                updateHexField(colorHex, colorSwatch, colorError, null);
                colorHex.requestFocus();
                event.consume();
            } else if (hexColor(eyeHex.getText()) == null) {
                updateHexField(eyeHex, eyeSwatch, eyeError, null);
                eyeHex.requestFocus();
                event.consume();
            }
        });
        if (existing != null) {
            ButtonType delete = new ButtonType("Excluir", ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().add(0, delete);
            dialog.showAndWait().ifPresent(result -> {
                if (result == delete) {
                    if (!existing.status.equals("disponível")) {
                        alert("Agente ocupado", "Aguarde a tarefa em andamento terminar.");
                    } else if (confirm("Excluir agente", "Excluir " + existing.name + " deste projeto?")) {
                        project().agents.remove(existing);
                        save();
                        show("agents");
                    }
                } else if (result.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    saveAgent(existing, name, role, instructions, model, colorHex, eyeHex, writes, visualGroup,
                            floatMotion, blink, pulse, wave, sneeze, sleep, read, computer);
                }
            });
        } else {
            dialog.showAndWait().ifPresent(result -> {
                if (result.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    saveAgent(new Agent(), name, role, instructions, model, colorHex, eyeHex, writes, visualGroup,
                            floatMotion, blink, pulse, wave, sneeze, sleep, read, computer);
                }
            });
        }
    }

    private CheckBox animationChoice(String text, boolean selected) {
        CheckBox choice = new CheckBox(text);
        choice.setSelected(selected);
        return choice;
    }

    private static String hexColor(String value) {
        if (value == null || !value.trim().matches("(?i)#[0-9a-f]{6}")) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Region hexSwatch(String value) {
        Region swatch = new Region();
        swatch.getStyleClass().add("hex-swatch");
        swatch.setStyle("-fx-background-color: " + value + ";");
        return swatch;
    }

    private Label hexError() {
        Label error = label("Use um hexadecimal no formato #RRGGBB.", "hex-error");
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());
        return error;
    }

    private void updateHexField(TextField input, Region swatch, Label error, String value) {
        boolean valid = value != null;
        error.setVisible(!valid);
        if (valid) {
            input.setStyle("");
            swatch.setStyle("-fx-background-color: " + value + ";");
        } else input.setStyle("-fx-border-color: #df776f;");
    }

    private VBox visualOptionGraphic(AgentCharacterView.Design design, String color,
                                    String eyeColor, AgentAnimations choices) {
        VBox preview = new VBox(4, new AgentCharacterView(design.id(), 68, !data.reduceAnimations,
                false, color, eyeColor, false, choices),
                label(design.name(), "visual-name"));
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    private AgentAnimations animationChoices(CheckBox floatMotion, CheckBox blink, CheckBox pulse,
                                             CheckBox wave, CheckBox sneeze, CheckBox sleep,
                                             CheckBox read, CheckBox computer) {
        AgentAnimations choices = new AgentAnimations();
        choices.floatMotion = floatMotion.isSelected();
        choices.blink = blink.isSelected();
        choices.pulse = pulse.isSelected();
        choices.wave = wave.isSelected();
        choices.sneeze = sneeze.isSelected();
        choices.sleep = sleep.isSelected();
        choices.read = read.isSelected();
        choices.computer = computer.isSelected();
        return choices;
    }

    private void saveAgent(Agent agent, TextField name, TextField role, TextArea instructions,
                           TextField model, TextField colorHex, TextField eyeHex, CheckBox writes,
                           ToggleGroup visualGroup, CheckBox floatMotion, CheckBox blink, CheckBox pulse,
                           CheckBox wave, CheckBox sneeze, CheckBox sleep, CheckBox read,
                           CheckBox computer) {
        if (name.getText().isBlank()) { alert("Agente", "Informe um nome para o agente."); return; }
        boolean alreadyExists = project().agents.stream().anyMatch(a -> a != agent
                && a.name.equalsIgnoreCase(name.getText().trim()));
        if (alreadyExists) { alert("Agente", "Já existe um agente com esse nome."); return; }
        if (agent.canEditFiles != writes.isSelected()) {
            agent.sessionId = "";
            agent.chatSessions.clear();
        }
        agent.name = name.getText().trim();
        agent.role = role.getText().trim();
        agent.instructions = instructions.getText().trim();
        agent.model = model.getText().isBlank() ? "Padrão do Codex" : model.getText().trim();
        agent.color = hexColor(colorHex.getText());
        agent.eyeColor = hexColor(eyeHex.getText());
        agent.visualId = visualGroup.getSelectedToggle() == null ? "orbe"
                : (String) visualGroup.getSelectedToggle().getUserData();
        agent.animations = animationChoices(floatMotion, blink, pulse, wave, sneeze, sleep, read,
                computer);
        agent.canEditFiles = writes.isSelected();
        if (!project().agents.contains(agent)) project().agents.add(agent);
        save();
        show("agents");
    }

    private Node tasks() {
        Project project = project();
        VBox page = page();
        page.setSpacing(0);
        page.setPadding(new Insets(32, 30, 36, 36));
        long pending = project.approvals.stream().filter(a -> a.status.equals("Pendente")).count();
        long working = project.tasks.stream().filter(t -> t.status.equals("Em andamento")).count();
        page.getChildren().add(row(14,
                heading("Tarefas e aprovações", working + " em andamento, " + pending + " itens esperando você."),
                grow(), segmentedTasks()));

        if (!taskFilter.equals("Histórico")) {
            Label eyebrow = label("AGUARDANDO VOCÊ · " + pending, "tasks-eyebrow");
            VBox.setMargin(eyebrow, new Insets(16, 0, 10, 0));
            page.getChildren().add(eyebrow);
            List<Approval> waiting = project.approvals.stream().filter(a -> a.status.equals("Pendente")).toList();
            if (waiting.isEmpty()) {
                VBox empty = card();
                empty.getStyleClass().add("tasks-empty");
                empty.getChildren().add(label("Nenhum item aguardando sua resposta.", "muted"));
                page.getChildren().add(empty);
            } else {
                GridPane cards = new GridPane();
                cards.getStyleClass().add("tasks-approval-grid");
                cards.setHgap(18);
                cards.setVgap(18);
                double[] widths = {32.3, 32.3, 35.4};
                for (int column = 0; column < 3; column++) {
                    ColumnConstraints constraints = new ColumnConstraints();
                    constraints.setPercentWidth(widths[column]);
                    constraints.setHgrow(Priority.ALWAYS);
                    cards.getColumnConstraints().add(constraints);
                }
                for (int index = 0; index < waiting.size(); index++) {
                    VBox card = approvalCard(project, waiting.get(index));
                    card.setMaxWidth(Double.MAX_VALUE);
                    cards.add(card, index % 3, index / 3);
                }
                page.getChildren().add(cards);
            }
        }

        if (!taskFilter.equals("Aprovações")) {
            HBox columns = row(18);
            columns.getStyleClass().add("tasks-lower-grid");
            VBox.setMargin(columns, new Insets(22, 0, 0, 0));
            VBox list = card();
            VBox activity = card();
            list.setSpacing(3);
            activity.setSpacing(3);
            list.getStyleClass().add("tasks-list-card");
            activity.getStyleClass().add("tasks-activity-card");
            HBox.setHgrow(list, Priority.ALWAYS);
            HBox.setHgrow(activity, Priority.ALWAYS);
            list.setMinWidth(0);
            activity.setMinWidth(0);
            list.setPrefWidth(650);
            activity.setPrefWidth(420);
            list.getChildren().add(row(8, label("Tarefas", "section-title"), grow(),
                    label(project.tasks.size() + " no total", "tiny-muted")));
            List<Task> visibleTasks = project.tasks.stream()
                    .filter(task -> !taskFilter.equals("Histórico") || List.of("Concluída", "Falhou", "Cancelada").contains(task.status))
                    .sorted(Comparator.comparing((Task t) -> t.createdAt).reversed()).toList();
            if (visibleTasks.isEmpty()) list.getChildren().add(emptyText(taskFilter.equals("Histórico")
                    ? "Ainda não há tarefas concluídas." : "As tarefas criadas pelo chat aparecerão aqui."));
            visibleTasks.stream()
                    .forEach(task -> list.getChildren().add(taskDetailRow(project, task)));
            boolean live = working > 0;
            HBox activityHeading = row(8, label("Atividade", "section-title"), grow(),
                    dot(live ? "#76bb8b" : "#8f8f8e"),
                    label(live ? "ao vivo" : "atualizado", live ? "green-text" : "tiny-muted"));
            activity.getChildren().add(activityHeading);
            project.allMessages().stream().sorted(Comparator.comparing((ChatMessage m) -> m.time).reversed())
                    .limit(8).forEach(m -> activity.getChildren().add(taskActivityRow(project, m)));
            if (project.allMessages().isEmpty()) activity.getChildren().add(emptyText("Ainda sem atividade."));
            columns.getChildren().addAll(list, activity);
            page.getChildren().add(columns);
        }
        return scroll(page);
    }

    private HBox segmentedTasks() {
        HBox segments = row(2);
        segments.getStyleClass().add("segments");
        for (String option : List.of("Tudo", "Aprovações", "Histórico")) {
            Button button = button(option, taskFilter.equals(option) ? "segment selected" : "segment", () -> {
                taskFilter = option;
                show("tasks");
            });
            segments.getChildren().add(button);
        }
        return segments;
    }

    private VBox approvalCard(Project project, Approval approval) {
        if ("question".equals(approval.kind)) return questionCard(project, approval);
        Agent agent = project.agent(approval.agentId);
        VBox card = card();
        card.setSpacing(12);
        card.getStyleClass().addAll("approval-card", "task-approval-card");
        card.setMinHeight(336);
        card.setPrefHeight(336);
        String risk = approval.risk == null ? "Médio" : approval.risk;
        Label riskLabel = label("Risco " + risk.toLowerCase(Locale.ROOT), "tasks-risk",
                risk.equalsIgnoreCase("baixo") ? "risk-low" : "risk-medium");
        card.getChildren().add(row(10, agent == null ? dot("#888888") : smallAvatar(agent, 12),
                label(agent == null ? "Agente" : agent.name, "tasks-approval-agent"), grow(), riskLabel));
        card.getChildren().addAll(label(approval.title, "tasks-approval-title"),
                label(approval.detail, "tasks-approval-detail"));
        String preview = approval.actionPreview == null || approval.actionPreview.isBlank()
                ? "Pasta do projeto: " + project.folder : approval.actionPreview;
        Label code = label(shorten(preview, 95), "tasks-approval-code");
        code.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(code);
        if (approval.status.equals("Pendente")) {
            Button allow = button("Aprovar", "primary-button", () -> approve(project, approval));
            Button deny = button("Negar", "outline-button", () -> reject(project, approval));
            allow.setMinWidth(Region.USE_PREF_SIZE);
            deny.setMinWidth(Region.USE_PREF_SIZE);
            Button always = button("Sempre\npermitir", "tasks-always-button", () -> alwaysAllow(project, approval));
            always.setMinWidth(56);
            always.setMaxWidth(64);
            card.getChildren().add(row(8,
                    allow, deny, grow(), always,
                    label(relativeTime(approval.createdAt), "tiny-muted")));
        } else {
            card.getChildren().add(badge(approval.status));
        }
        return card;
    }

    private void alwaysAllow(Project project, Approval approval) {
        if (!"Pendente".equals(approval.status)) return;
        if (!confirm("Sempre permitir neste projeto",
                "Todos os agentes com permissão de edição poderão iniciar tarefas de escrita sem pedir aprovação por tarefa. Continuar?")) return;
        project.writePolicy = "permitir";
        project.agents.forEach(agent -> {
            agent.sessionId = "";
            agent.chatSessions.clear();
        });
        approve(project, approval);
    }

    private VBox questionCard(Project project, Approval question) {
        Agent agent = project.agent(question.agentId);
        VBox card = card();
        card.setSpacing(6);
        card.getStyleClass().add("task-question-card");
        card.setMinHeight(336);
        card.setPrefHeight(336);
        Label agentName = label(agent == null ? "Agente" : agent.name, "tasks-approval-agent");
        if (agent != null && agent.color != null) agentName.setStyle("-fx-text-fill: " + agent.color + ";");
        Label questionTitle = label(question.title, "tasks-approval-title");
        questionTitle.setMinWidth(0);
        questionTitle.setTextOverrun(OverrunStyle.CLIP);
        questionTitle.prefWidthProperty().bind(card.widthProperty().subtract(36));
        Label questionTag = label("Pergunta", "question-tag");
        questionTag.setWrapText(false);
        questionTag.setMinWidth(Region.USE_PREF_SIZE);
        card.getChildren().addAll(row(10, agent == null ? dot("#888888") : smallAvatar(agent, 12),
                        agentName, grow(), questionTag), questionTitle);
        if (!"Pendente".equals(question.status)) {
            card.getChildren().addAll(badge(question.status), label(question.response, "tasks-approval-detail"));
            return card;
        }
        List<VBox> optionCards = new ArrayList<>();
        TextField custom = field(question.responseDraft == null ? "" : question.responseDraft,
                "Escreva sua resposta");
        custom.getStyleClass().add("question-custom-input");
        custom.textProperty().addListener((o, before, after) -> question.responseDraft = after);
        custom.setOnAction(event -> answerQuestion(project, question, false));
        for (int index = 0; index <= question.choices.size(); index++) {
            boolean freeText = index == question.choices.size();
            HBox line = row(9, label(String.valueOf(index + 1), "question-number"));
            if (freeText) {
                line.getChildren().add(label("Ou escreva o que quiser...", "question-custom-label"));
            } else {
                QuestionChoice choice = question.choices.get(index);
                Label title = label(choice.title, "question-option-title");
                title.setMinWidth(0);
                title.setTextOverrun(OverrunStyle.CLIP);
                HBox.setHgrow(title, Priority.ALWAYS);
                line.getChildren().add(title);
                if (index == question.recommendedChoice) {
                    Label recommended = label("recomendado", "question-recommended");
                    recommended.setWrapText(false);
                    recommended.setMinWidth(Region.USE_PREF_SIZE);
                    line.getChildren().add(recommended);
                }
            }
            VBox option = new VBox(4, line);
            if (!freeText && question.choices.get(index).description != null
                    && !question.choices.get(index).description.isBlank())
                option.getChildren().add(label(question.choices.get(index).description, "question-option-description"));
            if (freeText) option.getChildren().add(custom);
            option.getStyleClass().add("question-option");
            int selectedIndex = index;
            option.setOnMouseClicked(event -> {
                question.selectedChoice = selectedIndex;
                updateQuestionSelection(optionCards, question, custom);
                save();
                if (freeText) custom.requestFocus();
            });
            optionCards.add(option);
            card.getChildren().add(option);
        }
        updateQuestionSelection(optionCards, question, custom);
        card.getChildren().add(row(8, label("Enter para confirmar", "tiny-muted"), grow(),
                button("Pular", "link-button", () -> answerQuestion(project, question, true)),
                button("Enviar", "primary-button", () -> answerQuestion(project, question, false))));
        return card;
    }

    private void updateQuestionSelection(List<VBox> options, Approval question, TextField custom) {
        for (int index = 0; index < options.size(); index++) {
            VBox option = options.get(index);
            option.getStyleClass().remove("selected");
            if (index == question.selectedChoice) option.getStyleClass().add("selected");
        }
        boolean showCustom = question.selectedChoice >= question.choices.size();
        custom.setVisible(showCustom);
        custom.setManaged(showCustom);
    }

    private Node taskDetailRow(Project project, Task task) {
        Agent agent = project.agent(task.agentId);
        Label title = label(shorten(task.title, 57), "tasks-row-title");
        title.setTooltip(new Tooltip("Conversa: " + project.chatFor(task).name));
        title.setWrapText(false);
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        String state = task.status.startsWith("Aguardando") ? "Aguardando" : task.status;
        Label status = label(state, "tasks-row-status");
        status.setPrefWidth(100);
        status.setMaxWidth(100);
        if (task.status.equals("Em andamento") && agent != null && agent.color != null)
            status.setStyle("-fx-text-fill: " + agent.color + ";");
        else if (task.status.startsWith("Aguardando")) status.getStyleClass().add("tasks-waiting");
        Node progress;
        if (task.status.equals("Em andamento") || task.status.equals("Concluída")) {
            ProgressBar bar = new ProgressBar(Math.max(0, Math.min(100, task.progress)) / 100.0);
            bar.getStyleClass().add("tasks-row-progress");
            bar.setPrefWidth(90);
            bar.setMaxWidth(90);
            bar.setStyle("-fx-accent: " + (task.status.equals("Concluída") ? "#5c5c5c"
                    : agent == null || agent.color == null ? "#76bb8b" : agent.color) + ";");
            progress = bar;
        } else {
            Region blank = new Region();
            blank.setPrefWidth(90);
            progress = blank;
        }
        Label time = label(task.status.equals("Na fila") ? "—" : relativeTime(task.updatedAt), "tasks-row-time");
        time.setPrefWidth(51);
        time.setAlignment(Pos.CENTER_RIGHT);
        HBox line = row(8, agent == null ? dot("#777777") : smallAvatar(agent, 11),
                title, status, progress, time);
        line.getStyleClass().add("tasks-detail-row");
        line.setOnMouseClicked(event -> showTaskDetails(project, task));
        return line;
    }

    private String relativeTime(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return "—";
        try {
            LocalDateTime time = LocalDateTime.parse(timestamp,
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            long minutes = java.time.Duration.between(time, LocalDateTime.now()).toMinutes();
            if (minutes < 1) return "agora";
            if (minutes < 60) return minutes + " min";
            if (minutes < 24 * 60) return (minutes / 60) + " h";
            return timestamp.substring(11, 16);
        } catch (RuntimeException exception) {
            return timestamp.length() >= 16 ? timestamp.substring(11, 16) : timestamp;
        }
    }

    private void showTaskDetails(Project project, Task task) {
        Agent agent = project.agent(task.agentId);
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.initOwner(stage);
        dialog.setTitle("Tarefa");
        dialog.setHeaderText(task.title);
        long queued = agent == null ? 0 : project.tasks.stream().filter(candidate ->
                agent.id.equals(candidate.agentId) && !task.id.equals(candidate.id)
                        && List.of("Na fila", "Pausada").contains(candidate.status)).count();
        dialog.setContentText("Agente: " + (agent == null ? "—" : agent.name)
                + "\nConversa: " + project.chatFor(task).name
                + "\nStatus: " + task.status + "\n\n" + shorten(task.prompt, 450)
                + (task.status.equals("Em andamento") && queued > 0
                    ? "\n\n" + queued + " tarefa(s) na fila. Parar pausa a fila; parar e avançar inicia a próxima." : ""));
        ButtonType stop = new ButtonType("Parar", ButtonBar.ButtonData.OTHER);
        ButtonType stopAndNext = new ButtonType("Parar e iniciar próxima", ButtonBar.ButtonData.OTHER);
        ButtonType resume = new ButtonType("Retomar fila", ButtonBar.ButtonData.OTHER);
        if (task.status.equals("Em andamento")) {
            if (queued > 0) dialog.getButtonTypes().setAll(stop, stopAndNext, ButtonType.CLOSE);
            else dialog.getButtonTypes().setAll(stop, ButtonType.CLOSE);
        } else if (task.status.equals("Pausada") && agent != null && "disponível".equals(agent.status)) {
            dialog.getButtonTypes().setAll(resume, ButtonType.CLOSE);
        } else dialog.getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dark-dialog");
        ButtonType choice = dialog.showAndWait().orElse(ButtonType.CLOSE);
        if (choice == stop) stopTask(project, task, false);
        else if (choice == stopAndNext) stopTask(project, task, true);
        else if (choice == resume) resumeAgentQueue(project, agent);
    }

    private void stopTask(Project project, Task task, boolean startNext) {
        if (!"Em andamento".equals(task.status)) return;
        Agent agent = project.agent(task.agentId);
        if (agent != null) {
            for (Task waiting : project.tasks) {
                if (!agent.id.equals(waiting.agentId) || waiting.id.equals(task.id)) continue;
                if (startNext && "Pausada".equals(waiting.status)) waiting.status = "Na fila";
                else if (!startNext && "Na fila".equals(waiting.status)) waiting.status = "Pausada";
            }
        }
        task.status = "Interrompendo";
        task.updatedAt = WorkspaceData.now();
        ChatThread chat = project.chatFor(task);
        chat.messages.add(ChatMessage.of("system", "", "Sistema",
                "Você interrompeu a tarefa de " + (agent == null ? "um agente" : agent.name)
                        + (startNext ? ". A próxima tarefa da fila começará quando o processo parar."
                        : ". A fila desse agente ficou pausada.")));
        codexService.cancel(task.id);
        CompletableFuture<Void> currentRun = taskRuns.get(task.id);
        Runnable finish = () -> {
            if (!"Interrompendo".equals(task.status)) return;
            task.status = "Cancelada";
            task.updatedAt = WorkspaceData.now();
            codexService.clearCancellation(task.id);
            if (agent != null) {
                if (!startNext) {
                    for (Task waiting : project.tasks) {
                        if (agent.id.equals(waiting.agentId) && "Na fila".equals(waiting.status))
                            waiting.status = "Pausada";
                    }
                }
                agent.status = "disponível";
                if (startNext) startNextQueuedTask(project, agent);
            }
            save();
            if (project() != null && project().id.equals(project.id)) show(screen);
        };
        save();
        show(screen);
        if (currentRun == null) finish.run();
        else currentRun.whenComplete((ignored, error) -> Platform.runLater(finish));
    }

    private void resumeAgentQueue(Project project, Agent agent) {
        if (agent == null || !"disponível".equals(agent.status)) return;
        for (Task task : project.tasks) {
            if (agent.id.equals(task.agentId) && "Pausada".equals(task.status))
                task.status = "Na fila";
        }
        startNextQueuedTask(project, agent);
        save();
        show(screen);
    }

    private Node taskActivityRow(Project project, ChatMessage message) {
        String timestamp = message.time == null ? "" : message.time;
        Label time = label(timestamp.length() >= 16 ? timestamp.substring(11, 16) : timestamp, "tasks-activity-time");
        time.setMinWidth(44);
        Agent agent = project.agent(message.agentId);
        String color = message.kind.equals("approval") ? "#d7a352"
                : agent != null && agent.color != null ? agent.color : "#a3a3a1";
        Circle marker = new Circle(4, Color.web(color));
        Text sender = new Text(message.sender + " ");
        sender.setFill(Color.web(color));
        sender.setStyle("-fx-font-weight: bold;");
        Text text = new Text(shorten(message.text, 125));
        text.setFill(Color.web("#cececc"));
        TextFlow description = new TextFlow(sender, text);
        description.getStyleClass().add("tasks-activity-description");
        HBox.setHgrow(description, Priority.ALWAYS);
        HBox line = row(10, time, marker, description);
        line.setAlignment(Pos.TOP_LEFT);
        line.getStyleClass().add("tasks-activity-row");
        return line;
    }

    private Node terminals() {
        Project project = project();
        VBox page = new VBox(17);
        page.getStyleClass().add("terminal-page");
        Button create = button("+ Novo terminal", "primary-button", () -> openTerminal(project));
        Button external = button("Janela externa", "outline-button", () -> openSystemTerminal(project));
        external.setTooltip(new Tooltip("Abre o Windows Terminal na pasta do projeto para programas interativos"));
        page.getChildren().add(row(14,
                heading("Terminais", "Execute comandos na pasta do projeto em sessões independentes."),
                grow(), external, create));

        if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder))) {
            terminalViewSessionId = "";
            create.setDisable(true);
            external.setDisable(true);
            VBox empty = card();
            empty.getChildren().addAll(label("Pasta do projeto não configurada", "section-title"),
                    label("Escolha uma pasta para que os terminais iniciem nela.", "muted"),
                    button("Escolher pasta", "primary-button", () -> editProject(project)));
            page.getChildren().add(empty);
            return page;
        }

        List<TerminalSession> sessions = terminalsByProject.getOrDefault(project.id, List.of());
        if (sessions.isEmpty()) {
            terminalViewSessionId = "";
            terminalTabDots.clear();
            VBox empty = card();
            empty.getStyleClass().add("terminal-empty");
            empty.getChildren().addAll(label("Nenhum terminal aberto", "section-title"),
                    label("Cada sessão abre em " + project.folder, "muted"),
                    button("Abrir primeiro terminal", "primary-button", () -> openTerminal(project)));
            page.getChildren().add(empty);
            return page;
        }

        TerminalSession selected = selectedTerminal(project.id);
        VBox workspace = new VBox();
        workspace.getStyleClass().add("terminal-workspace");
        VBox.setVgrow(workspace, Priority.ALWAYS);

        HBox tabs = row(6);
        tabs.getStyleClass().add("terminal-tabs");
        terminalTabDots.clear();
        for (TerminalSession session : sessions) {
            Button open = button(session.title(), "terminal-tab-button", () -> {
                selectedTerminalByProject.put(project.id, session.id());
                show("terminals");
            });
            Button close = button("×", "terminal-tab-close", () -> closeTerminal(project, session));
            close.setTooltip(new Tooltip("Fechar " + session.title()));
            Circle statusDot = dot(session.running() ? "#76bb8b" : "#858585");
            terminalTabDots.put(session.id(), statusDot);
            HBox tab = row(4, statusDot, open, close);
            tab.getStyleClass().add("terminal-tab");
            if (selected == session) tab.getStyleClass().add("selected");
            tabs.getChildren().add(tab);
        }
        tabs.getChildren().add(button("+", "terminal-tab-add", () -> openTerminal(project)));
        ScrollPane tabScroll = new ScrollPane(tabs);
        tabScroll.setFitToHeight(true);
        tabScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tabScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabScroll.getStyleClass().add("terminal-tab-scroll");
        tabScroll.setPrefHeight(50);
        tabScroll.setMaxHeight(50);
        workspace.getChildren().add(tabScroll);

        terminalDirectoryView = label(selected.currentDirectory(), "terminal-directory");
        terminalDirectoryView.setTextOverrun(OverrunStyle.ELLIPSIS);
        terminalDirectoryView.setMaxWidth(800);
        terminalDirectoryView.setTooltip(new Tooltip(selected.currentDirectory()));
        terminalStatusView = label(selected.busy() ? "Executando..."
                : selected.running() ? "Pronto" : "Encerrado", "terminal-status");
        HBox location = row(9, label("PS", "terminal-shell-badge"), terminalDirectoryView,
                grow(), terminalStatusView);
        location.getStyleClass().add("terminal-location");
        workspace.getChildren().add(location);

        TextArea output = new TextArea(selected.output());
        terminalOutputView = output;
        output.setEditable(false);
        output.setWrapText(false);
        output.setFocusTraversable(false);
        output.getStyleClass().add("terminal-output");
        terminalViewSessionId = selected.id();
        VBox.setVgrow(output, Priority.ALWAYS);
        workspace.getChildren().add(output);

        TextField command = field("", "Digite um comando e pressione Enter");
        terminalCommandView = command;
        command.getStyleClass().add("terminal-command");
        command.setDisable(!selected.running());
        HBox.setHgrow(command, Priority.ALWAYS);
        Runnable execute = () -> sendTerminalCommand(selected, command);
        command.setOnAction(event -> execute.run());
        Button run = button("Executar ↵", "terminal-run", execute);
        terminalRunView = run;
        run.setDisable(!selected.running());
        HBox composer = row(11, label("❯", "terminal-prompt"), command, run);
        composer.getStyleClass().add("terminal-composer");
        workspace.getChildren().add(composer);
        page.getChildren().add(workspace);
        Platform.runLater(() -> {
            if (output.getScene() != null) {
                output.setScrollTop(Double.MAX_VALUE);
                command.requestFocus();
            }
        });
        return page;
    }

    private TerminalSession selectedTerminal(String projectId) {
        List<TerminalSession> sessions = terminalsByProject.get(projectId);
        if (sessions == null || sessions.isEmpty()) return null;
        String selectedId = selectedTerminalByProject.get(projectId);
        TerminalSession selected = sessions.stream().filter(session -> session.id().equals(selectedId))
                .findFirst().orElse(sessions.getLast());
        selectedTerminalByProject.put(projectId, selected.id());
        return selected;
    }

    private void openTerminal(Project project) {
        if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder))) {
            alert("Pasta do projeto", "Selecione uma pasta válida para abrir o terminal.");
            return;
        }
        int number = terminalNumbers.merge(project.id, 1, Integer::sum);
        try {
            TerminalSession session = new TerminalSession(project.id, "Terminal " + number,
                    Path.of(project.folder).toAbsolutePath().normalize(), this::terminalChanged);
            terminalsByProject.computeIfAbsent(project.id, ignored -> new ArrayList<>()).add(session);
            selectedTerminalByProject.put(project.id, session.id());
            show("terminals");
        } catch (IOException exception) {
            alert("Terminal", "Não foi possível abrir o PowerShell: " + exception.getMessage());
        }
    }

    private void openSystemTerminal(Project project) {
        if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder))) {
            alert("Pasta do projeto", "Selecione uma pasta válida para abrir o terminal.");
            return;
        }
        Path folder = Path.of(project.folder).toAbsolutePath().normalize();
        try {
            new ProcessBuilder("wt.exe", "-w", "new", "new-tab", "-d", folder.toString())
                    .directory(folder.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException exception) {
            alert("Windows Terminal", "Não foi possível abrir uma janela externa: " + exception.getMessage());
        }
    }

    private void closeTerminal(Project project, TerminalSession session) {
        session.close();
        List<TerminalSession> sessions = terminalsByProject.get(project.id);
        if (sessions != null) sessions.remove(session);
        if (session.id().equals(selectedTerminalByProject.get(project.id)))
            selectedTerminalByProject.remove(project.id);
        show("terminals");
    }

    private void sendTerminalCommand(TerminalSession session, TextField command) {
        String text = command.getText().trim();
        if (text.isBlank()) return;
        try {
            session.send(text);
            command.clear();
        } catch (IOException exception) {
            alert("Terminal", exception.getMessage());
        }
    }

    private void terminalChanged(TerminalSession session) {
        terminalUpdateVersion.incrementAndGet();
        queueTerminalRefresh();
    }

    private void queueTerminalRefresh() {
        if (!terminalRefreshQueued.compareAndSet(false, true)) return;
        try { Platform.runLater(() -> {
            long version = terminalUpdateVersion.get();
            updateTerminalView();
            terminalRefreshQueued.set(false);
            if (terminalUpdateVersion.get() != version) queueTerminalRefresh();
        }); }
        catch (IllegalStateException ignored) { terminalRefreshQueued.set(false); }
    }

    private void updateTerminalView() {
            if (!screen.equals("terminals") || project() == null || terminalOutputView == null) return;
            List<TerminalSession> sessions = terminalsByProject.getOrDefault(project().id, List.of());
            for (TerminalSession session : sessions) {
                Circle dot = terminalTabDots.get(session.id());
                if (dot != null) dot.setFill(Color.web(session.running() ? "#76bb8b" : "#858585"));
            }
            TerminalSession selected = selectedTerminal(project().id);
            if (selected == null || !terminalViewSessionId.equals(selected.id())) return;
            String current = selected.output();
            String previous = terminalOutputView.getText();
            if (current.startsWith(previous)) terminalOutputView.appendText(current.substring(previous.length()));
            else terminalOutputView.setText(current);
            terminalOutputView.setScrollTop(Double.MAX_VALUE);
            terminalDirectoryView.setText(selected.currentDirectory());
            terminalDirectoryView.setTooltip(new Tooltip(selected.currentDirectory()));
            terminalStatusView.setText(selected.busy() ? "Executando..."
                    : selected.running() ? "Pronto" : "Encerrado");
            terminalCommandView.setDisable(!selected.running());
            terminalRunView.setDisable(!selected.running());
    }

    private Node files() {
        Project project = project();
        if (project.folder.isBlank() || !Files.isDirectory(Path.of(project.folder))) {
            VBox page = page();
            page.getChildren().addAll(heading("Arquivos", "Explore o projeto e revise as mudanças dos agentes."),
                    emptyText("Vincule uma pasta ao projeto para abrir os arquivos."),
                    button("Escolher pasta", "primary-button", () -> editProject(project)));
            return scroll(page);
        }
        if (!filesProjectId.equals(project.id)) {
            filesProjectId = project.id;
            selectedFile = "";
            openFiles.clear();
            expandedFileFolders.clear();
            fileChangesScrollPosition = 0;
        }
        Path folder = Path.of(project.folder).toAbsolutePath().normalize();
        boolean isGit = gitService.isRepository(folder);
        List<GitService.Change> changes = isGit ? gitService.changes(folder) : List.of();
        Map<String, GitService.Change> byPath = new HashMap<>();
        for (GitService.Change change : changes) byPath.put(change.path().replace('\\', '/'), change);
        if (selectedFile.isBlank() && !changes.isEmpty()) selectedFile = changes.getFirst().path();
        if (!selectedFile.isBlank() && !openFiles.contains(selectedFile)) openFiles.add(selectedFile);
        if (!selectedFile.isBlank()) rememberFileFolders(selectedFile);
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("files-layout");
        layout.setLeft(fileSidebar(project, folder, isGit, changes, byPath));
        layout.setCenter(fileViewer(project, folder, byPath.get(selectedFile.replace('\\', '/'))));
        return layout;
    }

    private Node fileSidebar(Project project, Path folder, boolean isGit, List<GitService.Change> changes,
                             Map<String, GitService.Change> byPath) {
        VBox sidebar = new VBox(7);
        sidebar.getStyleClass().add("files-sidebar");
        sidebar.setPrefWidth(312);
        sidebar.setMinWidth(275);
        sidebar.setMaxWidth(340);
        Label title = label("Arquivos", "files-title");
        title.setWrapText(false);
        Label branch = label(isGit ? gitService.branch(folder) : "Sem Git", "files-branch");
        branch.setWrapText(false);
        branch.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        branch.setMaxWidth(175);
        branch.setTooltip(new Tooltip(branch.getText()));
        HBox top = row(7, title, grow());
        if (isGit) top.getChildren().add(UiIcons.icon("git-branch", 14));
        top.getChildren().add(branch);
        top.getStyleClass().add("files-top");
        sidebar.getChildren().addAll(top, label("CONTROLE DE VERSÃO", "files-eyebrow"));

        VBox version = new VBox(7);
        if (isGit) {
            TextField commit = field("", "Mensagem do commit");
            commit.setMaxWidth(Double.MAX_VALUE);
            long staged = changes.stream().filter(GitService.Change::staged).count();
            Button commitButton = button("Fazer commit de " + staged + " arquivo(s) preparado(s)", "primary-button files-commit", () -> {
                if (commit.getText().isBlank()) { alert("Commit", "Escreva uma mensagem para o commit."); return; }
                if (!confirm("Fazer commit", "Confirmar o commit dos arquivos preparados?")) return;
                String output = gitService.commit(folder, commit.getText().trim());
                if (!output.isBlank()) alert("Git", output);
                show("files");
            });
            commitButton.setDisable(staged == 0);
            commitButton.setMaxWidth(Double.MAX_VALUE);
            version.getChildren().addAll(commit, commitButton);
        } else {
            version.getChildren().add(label("Esta pasta não é um repositório Git.", "tiny-muted"));
        }
        sidebar.getChildren().add(version);

        VBox changed = new VBox(2);
        long staged = changes.stream().filter(GitService.Change::staged).count();
        changed.getChildren().add(label("PREPARADAS · " + staged, "files-eyebrow"));
        changes.stream().filter(GitService.Change::staged).forEach(c -> changed.getChildren().add(fileChangeRow(c)));
        long working = changes.stream().filter(this::hasWorkingChange).count();
        changed.getChildren().add(label("ALTERAÇÕES · " + working, "files-eyebrow"));
        changes.stream().filter(this::hasWorkingChange).forEach(c -> changed.getChildren().add(fileChangeRow(c)));
        if (changes.isEmpty()) changed.getChildren().add(label("Nenhuma alteração", "tiny-muted"));
        ScrollPane changeScroll = new ScrollPane(changed);
        changeScroll.setFitToWidth(true);
        changeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        changeScroll.getStyleClass().add("files-changes-scroll");
        changeScroll.setPrefHeight(Math.min(292, Math.max(95, 56 + changes.size() * 31)));
        changeScroll.setMinHeight(80);
        changeScroll.setVvalue(fileChangesScrollPosition);
        changeScroll.vvalueProperty().addListener((obs, old, value) -> fileChangesScrollPosition = value.doubleValue());
        sidebar.getChildren().add(changeScroll);

        sidebar.getChildren().add(label("EXPLORADOR", "files-eyebrow"));
        TreeItem<Path> rootItem = new TreeItem<>(folder);
        rootItem.setExpanded(true);
        populateFileFolder(rootItem, folder, byPath);
        TreeView<Path> explorer = new TreeView<>(rootItem);
        explorer.setShowRoot(true);
        explorer.getStyleClass().add("files-tree");
        explorer.setCellFactory(view -> new TreeCell<>() {
            @Override protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); setTooltip(null); return; }
                boolean directory = Files.isDirectory(item);
                String name = item.equals(folder) ? project.name : item.getFileName().toString();
                setText(name);
                setGraphic(UiIcons.icon(directory ? "folder-open" : "file", 14));
                setTooltip(new Tooltip(item.toString()));
                getStyleClass().removeAll("files-tree-changed", "files-tree-untracked");
                GitService.Change change = byPath.get(folder.relativize(item).toString().replace('\\', '/'));
                if (change != null) getStyleClass().add(change.untracked() ? "files-tree-untracked" : "files-tree-changed");
            }
        });
        explorer.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item != null && item.getValue() != null && Files.isRegularFile(item.getValue())) {
                openFile(folder.relativize(item.getValue()).toString().replace('\\', '/'));
            }
        });
        VBox.setVgrow(explorer, Priority.ALWAYS);
        sidebar.getChildren().add(explorer);
        return sidebar;
    }

    private Node fileChangeRow(GitService.Change change) {
        String path = change.path().replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1);
        String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        String status = change.untracked() ? "U" : change.status().indexOf('D') >= 0 ? "D"
                : change.status().indexOf('A') >= 0 ? "A" : "M";
        Label marker = label(status, "files-change-marker");
        marker.setWrapText(false);
        Label filename = label(name, "files-change-name");
        filename.setWrapText(false);
        filename.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        HBox.setHgrow(filename, Priority.ALWAYS);
        Label parentLabel = label(parent, "files-change-parent");
        parentLabel.setWrapText(false);
        parentLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        parentLabel.setMaxWidth(74);
        HBox content = row(8, marker, filename, parentLabel);
        Button button = button("", "files-change-button", () -> openFile(path));
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip(path));
        if (path.equals(selectedFile.replace('\\', '/'))) button.getStyleClass().add("selected");
        if (status.equals("U") || status.equals("A")) marker.getStyleClass().add("files-untracked");
        if (status.equals("D")) marker.getStyleClass().add("files-deleted");
        return button;
    }

    private void openFile(String path) {
        selectedFile = path;
        rememberFileFolders(path);
        openFiles.remove(path);
        openFiles.add(path);
        if (openFiles.size() > 4) openFiles.removeFirst();
        show("files");
    }

    private boolean hasWorkingChange(GitService.Change change) {
        return change.untracked() || change.status().length() > 1 && change.status().charAt(1) != ' ';
    }

    private void rememberFileFolders(String path) {
        String normalized = path.replace('\\', '/');
        int slash = normalized.indexOf('/');
        while (slash >= 0) {
            expandedFileFolders.add(normalized.substring(0, slash).replace('/', '\\'));
            slash = normalized.indexOf('/', slash + 1);
        }
    }

    private void populateFileFolder(TreeItem<Path> item, Path folder, Map<String, GitService.Change> byPath) {
        if (!Files.isDirectory(item.getValue())) return;
        item.getChildren().clear();
        try (var stream = Files.list(item.getValue())) {
            stream.filter(path -> !excludedFileFolder(path))
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(200).forEach(path -> {
                        TreeItem<Path> child = new TreeItem<>(path);
                        if (Files.isDirectory(path)) {
                            child.getChildren().add(new TreeItem<>());
                            child.expandedProperty().addListener((obs, old, expanded) -> {
                                String key = folder.relativize(path).toString();
                                if (expanded) { expandedFileFolders.add(key); populateFileFolder(child, folder, byPath); }
                                else expandedFileFolders.remove(key);
                            });
                            if (expandedFileFolders.contains(folder.relativize(path).toString())) child.setExpanded(true);
                        }
                        item.getChildren().add(child);
                    });
        } catch (IOException ignored) { }
    }

    private boolean excludedFileFolder(Path path) {
        String name = path.getFileName().toString();
        return name.equals(".git") || name.equals("target") || name.equals("node_modules")
                || name.equals(".next") || name.equals(".tools") || name.equals(".idea");
    }

    private Node fileViewer(Project project, Path folder, GitService.Change change) {
        BorderPane viewer = new BorderPane();
        viewer.getStyleClass().add("file-viewer");
        VBox top = new VBox(0);
        HBox tabs = new HBox(0);
        tabs.getStyleClass().add("files-tabs");
        if (openFiles.isEmpty()) tabs.getChildren().add(label("Selecione um arquivo", "files-empty-tab"));
        for (String path : openFiles) {
            String name = Path.of(path).getFileName().toString();
            Button tab = button(name, "files-tab", () -> openFile(path));
            tab.setTooltip(new Tooltip(path));
            if (path.equals(selectedFile)) tab.getStyleClass().add("selected");
            tabs.getChildren().add(tab);
        }
        top.getChildren().add(tabs);

        String content = "";
        DiffParser.Result diff;
        boolean source = change == null;
        if (selectedFile.isBlank()) {
            diff = diffParser.source("Selecione um arquivo à esquerda para abrir.");
        } else if (change != null) {
            content = gitService.diff(folder, change);
            diff = change.untracked() ? diffParser.source(content) : diffParser.parse(content);
        } else {
            Path file = folder.resolve(selectedFile).normalize();
            if (!file.startsWith(folder) || !Files.isRegularFile(file)) content = "Arquivo indisponível.";
            else try {
                if (Files.size(file) > 2_000_000) content = "Arquivo grande demais para a prévia.";
                else content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) { content = "Não foi possível ler este arquivo: " + e.getMessage(); }
            diff = diffParser.source(content);
        }
        HBox toolbar = new HBox(7);
        toolbar.getStyleClass().add("files-toolbar");
        Label pathLabel = label(selectedFile.isBlank() ? "Arquivos do projeto" : selectedFile, "files-path");
        pathLabel.setWrapText(false);
        pathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        pathLabel.setMaxWidth(350);
        pathLabel.setTooltip(new Tooltip(selectedFile));
        toolbar.getChildren().addAll(pathLabel, grow());
        if (!source) {
            Label counts = label("+" + diff.added() + "  −" + diff.removed(), "files-diff-count");
            counts.setWrapText(false);
            toolbar.getChildren().add(counts);
        }
        Button unified = button("Unificado", "files-mode-button", () -> { fileDiffMode = "Unificado"; show("files"); });
        Button split = button("Lado a lado", "files-mode-button", () -> { fileDiffMode = "Lado a lado"; show("files"); });
        if (fileDiffMode.equals("Unificado")) unified.getStyleClass().add("selected");
        else split.getStyleClass().add("selected");
        toolbar.getChildren().addAll(unified, split);
        if (change != null) {
            if (!change.untracked() && change.status().indexOf('A') < 0) {
                toolbar.getChildren().add(button("Reverter", "outline-button files-action", () -> {
                    if (!confirm("Reverter alteração", "Descartar todas as alterações de " + change.path() + "?")) return;
                    String result = gitService.discard(folder, change);
                    if (!result.isBlank()) alert("Git", result);
                    show("files");
                }));
            }
            toolbar.getChildren().add(button("Pedir ajuste", "outline-button files-action", () -> {
                drafts.put(project.id + ":" + project.activeChat().id,
                        "@Programador revise e ajuste o arquivo " + change.path() + ": ");
                show("chat");
            }));
            toolbar.getChildren().add(button(change.staged() ? "Retirar" : "Preparar", "primary-button files-action", () -> {
                String result = change.staged() ? gitService.unstage(folder, change) : gitService.stage(folder, change);
                if (!result.isBlank()) alert("Git", result);
                show("files");
            }));
        }
        top.getChildren().add(toolbar);
        viewer.setTop(top);
        viewer.setCenter(fileDiffList(diff));
        String extension = selectedFile.contains(".") ? selectedFile.substring(selectedFile.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT) : "Texto";
        Label status = label(extension + "    UTF-8    " + diff.hunks() + " trecho(s) alterado(s)"
                + (diff.truncated() ? "    Prévia limitada a 1.000 linhas" : ""), "files-status");
        status.setWrapText(false);
        viewer.setBottom(status);
        return viewer;
    }

    private Node fileDiffList(DiffParser.Result diff) {
        if (fileDiffMode.equals("Lado a lado")) {
            ListView<DiffParser.SideLine> list = new ListView<>(FXCollections.observableArrayList(diffParser.sideBySide(diff.lines())));
            list.getStyleClass().add("files-diff-list");
            list.setFixedCellSize(23);
            list.setCellFactory(view -> new ListCell<>() {
                @Override protected void updateItem(DiffParser.SideLine item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(null);
                    if (empty || item == null) setGraphic(null);
                    else {
                        HBox left = fileDiffHalf(item.left());
                        HBox right = fileDiffHalf(item.right());
                        left.getStyleClass().add("files-diff-left");
                        left.prefWidthProperty().bind(list.widthProperty().subtract(20).divide(2));
                        right.prefWidthProperty().bind(list.widthProperty().subtract(20).divide(2));
                        left.maxWidthProperty().bind(left.prefWidthProperty());
                        right.maxWidthProperty().bind(right.prefWidthProperty());
                        setGraphic(row(0, left, right));
                    }
                    getStyleClass().removeAll("files-row-add", "files-row-remove", "files-row-hunk");
                }
            });
            return list;
        }
        ListView<DiffParser.Line> list = new ListView<>(FXCollections.observableArrayList(diff.lines()));
        list.getStyleClass().add("files-diff-list");
        list.setFixedCellSize(23);
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(DiffParser.Line item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("files-row-add", "files-row-remove", "files-row-hunk", "files-row-section");
                if (empty || item == null) { setGraphic(null); setTooltip(null); return; }
                setGraphic(fileDiffHalf(item));
                if (item.kind() == '+') getStyleClass().add("files-row-add");
                else if (item.kind() == '-') getStyleClass().add("files-row-remove");
                else if (item.kind() == 'h') getStyleClass().add("files-row-hunk");
                else if (item.kind() == 's') getStyleClass().add("files-row-section");
                setTooltip(item.text().length() > 240 ? new Tooltip(item.text().substring(0, Math.min(1000, item.text().length()))) : null);
            }
        });
        return list;
    }

    private HBox fileDiffHalf(DiffParser.Line line) {
        String text = line == null ? "" : line.text();
        Label oldNumber = label(line == null || line.oldNumber() == 0 ? "" : Integer.toString(line.oldNumber()), "files-line-number");
        Label newNumber = label(line == null || line.newNumber() == 0 ? "" : Integer.toString(line.newNumber()), "files-line-number");
        oldNumber.setWrapText(false);
        newNumber.setWrapText(false);
        Label code = label(text.length() > 360 ? text.substring(0, 360) + "…" : text, "files-code");
        code.setWrapText(false);
        code.setTextOverrun(OverrunStyle.CLIP);
        code.setMinWidth(0);
        code.setMaxWidth(Double.MAX_VALUE);
        HBox half = row(0, oldNumber, newNumber, code);
        HBox.setHgrow(code, Priority.ALWAYS);
        HBox.setHgrow(half, Priority.ALWAYS);
        half.setMinWidth(0);
        if (line != null && line.kind() == '+') half.getStyleClass().add("files-row-add");
        if (line != null && line.kind() == '-') half.getStyleClass().add("files-row-remove");
        if (line != null && line.kind() == 'h') half.getStyleClass().add("files-row-hunk");
        return half;
    }

    private Node knowledge() {
        Project project = project();
        VBox page = page();
        page.getChildren().add(row(16,
                heading("Base de conhecimento", "Os agentes consultam estas fontes ao responder. O índice fica neste computador."),
                grow(), button("Reindexar tudo", "outline-button", () -> reindexKnowledge(project)),
                button("+ Adicionar fonte", "primary-button", () -> addKnowledge(project))));
        long indexed = project.knowledge.stream().filter(file -> file.status.equals("Indexado")).count();
        int chunks = project.knowledge.stream().mapToInt(file -> file.chunkCount).sum();
        long bytes = 0;
        for (KnowledgeFile file : project.knowledge) {
            if (file.indexedPath != null && !file.indexedPath.isBlank()) {
                try { bytes += Files.size(Path.of(file.indexedPath)); } catch (IOException ignored) { }
            }
        }
        long errors = project.knowledge.stream().filter(file -> file.status.equals("Erro")).count();
        HBox metrics = row(16,
                metric("Itens indexados", String.valueOf(indexed), project.knowledge.size() + " fontes", "green"),
                metric("Trechos", String.valueOf(chunks), "pesquisáveis", ""),
                metric("Tamanho do índice", bytes < 1_000_000
                        ? Math.max(1, bytes / 1024) + " KB"
                        : String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0), "armazenado localmente", ""),
                metric("Precisam de atenção", String.valueOf(errors), "fontes com erro", errors > 0 ? "amber" : "green"));
        page.getChildren().add(metrics);

        HBox columns = row(18);
        VBox left = new VBox(14);
        VBox right = new VBox(14);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(640);
        right.setPrefWidth(440);

        VBox drop = new VBox(6);
        drop.getStyleClass().add("drop-zone");
        drop.getChildren().addAll(label("Arraste arquivos ou pastas para cá", "row-title"),
                label("PDF com texto, DOCX, Markdown, CSV, código e arquivos de texto · até 25 MB por arquivo", "tiny-muted"),
                row(8, button("Escolher arquivos", "outline-button", () -> addKnowledge(project)),
                        button("Escolher pasta", "outline-button", () -> addKnowledgeFolder(project))));
        drop.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        drop.setOnDragDropped(event -> {
            if (event.getDragboard().hasFiles()) {
                importKnowledgePaths(project, event.getDragboard().getFiles().stream().map(java.io.File::toPath).toList());
                event.setDropCompleted(true);
            }
            event.consume();
        });
        left.getChildren().add(drop);

        VBox files = card();
        files.getChildren().add(label("Fontes", "section-title"));
        if (project.knowledge.isEmpty()) files.getChildren().add(emptyText("Nenhuma fonte ainda. Adicione arquivos ou uma pasta."));
        for (KnowledgeFile file : project.knowledge) {
            String kind = file.name.contains(".") ? file.name.substring(file.name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT) : "TXT";
            HBox line = row(10, label(kind, "source-kind"),
                    new VBox(2, label(file.name, "row-title"),
                            label(file.chunkCount + " trechos · " + Math.max(1, file.size / 1024) + " KB", "tiny-muted")),
                    grow(), label(file.status, file.status.equals("Erro") ? "red-text" : "green-text"),
                    button("Reindexar", "outline-button", () -> reindexKnowledge(project, file)),
                    button("Remover", "outline-button", () -> {
                        if (!confirm("Remover fonte", "Remover " + file.name + " da base de conhecimento?")) return;
                        project.knowledge.remove(file);
                        deleteKnowledgeCopy(file);
                        save();
                        show("knowledge");
                    }));
            line.getStyleClass().add("list-row");
            files.getChildren().add(line);
            if (file.status.equals("Erro") && !file.error.isBlank())
                files.getChildren().add(label(file.error, "red-text tiny-muted"));
        }
        left.getChildren().add(files);

        VBox preview = card();
        preview.getChildren().addAll(label("Testar busca", "section-title"),
                label("Veja os trechos que um agente receberia para esta pergunta.", "tiny-muted"));
        TextField query = field("", "Pergunte algo sobre as fontes");
        VBox results = new VBox(9);
        Button search = button("Buscar", "primary-button", () -> {
            results.getChildren().clear();
            List<KnowledgeService.Hit> hits = knowledgeService.search(project, query.getText(), "");
            if (hits.isEmpty()) {
                results.getChildren().add(emptyText("Nenhum trecho encontrado para esta pergunta."));
                return;
            }
            int number = 0;
            for (KnowledgeService.Hit hit : hits) {
                number++;
                VBox result = new VBox(6,
                        row(7, badge(String.valueOf(number)), label(hit.name(), "row-title"), grow(),
                                label(hit.score() + " ocorrências", "green-text")),
                        label(shorten(hit.text(), 310), "message-text"));
                result.getStyleClass().add("search-hit");
                results.getChildren().add(result);
            }
        });
        query.setOnAction(event -> search.fire());
        HBox queryLine = row(8, query, search);
        HBox.setHgrow(query, Priority.ALWAYS);
        preview.getChildren().addAll(queryLine, results);
        right.getChildren().add(preview);

        VBox access = card();
        access.getChildren().addAll(label("Quem pode consultar", "section-title"),
                label("Desligar um agente impede que trechos desta base sejam enviados às suas próximas tarefas.", "tiny-muted"));
        for (Agent agent : project.agents) {
            CheckBox allowed = new CheckBox();
            allowed.setSelected(agent.canUseKnowledge);
            allowed.selectedProperty().addListener((o, before, after) -> { agent.canUseKnowledge = after; save(); });
            access.getChildren().add(row(9, smallAvatar(agent, 12), label(agent.name, "row-title"), grow(), allowed));
        }
        right.getChildren().add(access);
        columns.getChildren().addAll(left, right);
        page.getChildren().add(columns);
        return scroll(page);
    }

    private void addKnowledge(Project project) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Adicionar fontes à base de conhecimento");
        List<java.io.File> selected = chooser.showOpenMultipleDialog(stage);
        if (selected != null) importKnowledgePaths(project, selected.stream().map(java.io.File::toPath).toList());
    }

    private void addKnowledgeFolder(Project project) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Adicionar pasta à base de conhecimento");
        java.io.File selected = chooser.showDialog(stage);
        if (selected != null) importKnowledgePaths(project, List.of(selected.toPath()));
    }

    private void importKnowledgePaths(Project project, List<Path> selected) {
        workers.submit(() -> {
            List<Path> files = new ArrayList<>();
            for (Path path : selected) {
                if (Files.isDirectory(path)) {
                    try (var walk = Files.walk(path, 5)) {
                        walk.filter(Files::isRegularFile).filter(knowledgeService::canIndex)
                                .limit(Math.max(0, 120 - files.size())).forEach(files::add);
                    } catch (IOException ignored) { }
                } else if (knowledgeService.canIndex(path)) files.add(path);
                if (files.size() >= 120) break;
            }
            List<KnowledgeFile> entries = new ArrayList<>();
            for (Path source : files) {
                KnowledgeFile entry = new KnowledgeFile();
                entry.name = source.getFileName().toString();
                try {
                    Path stored = repository.storeKnowledge(project.id, source);
                    entry.storedPath = stored.toString();
                    entry.size = Files.size(stored);
                    try { knowledgeService.index(entry); }
                    catch (IOException e) { entry.status = "Erro"; entry.error = e.getMessage(); }
                    entries.add(entry);
                } catch (IOException ignored) { }
            }
            Platform.runLater(() -> {
                project.knowledge.addAll(entries);
                save();
                if (project() != null && project().id.equals(project.id)) show("knowledge");
                if (entries.isEmpty()) alert("Conhecimento", "Nenhum arquivo compatível foi encontrado.");
            });
        });
    }

    private void reindexKnowledge(Project project) {
        reindexKnowledgeFiles(project, new ArrayList<>(project.knowledge));
    }

    private void reindexKnowledge(Project project, KnowledgeFile file) {
        reindexKnowledgeFiles(project, List.of(file));
    }

    private void reindexKnowledgeFiles(Project project, List<KnowledgeFile> files) {
        workers.submit(() -> {
            for (KnowledgeFile file : files) {
                try { knowledgeService.index(file); }
                catch (IOException e) { file.status = "Erro"; file.error = e.getMessage(); }
            }
            Platform.runLater(() -> {
                save();
                if (project() != null && project().id.equals(project.id)) show("knowledge");
            });
        });
    }

    private void deleteKnowledgeCopy(KnowledgeFile file) {
        Path home = repository.home().toAbsolutePath().normalize();
        for (String path : List.of(file.storedPath, file.indexedPath == null ? "" : file.indexedPath)) {
            if (path.isBlank()) continue;
            try {
                Path target = Path.of(path).toAbsolutePath().normalize();
                if (target.startsWith(home)) Files.deleteIfExists(target);
            } catch (IOException | RuntimeException ignored) { }
        }
    }

    private Node plugins() {
        Project project = project();
        String codex = codexService.executable();
        VBox page = page();
        page.getChildren().add(row(16,
                heading("Plugins e integrações", "Instale habilidades do Codex e gerencie ferramentas conectadas ao projeto."),
                grow(), button("Atualizar", "outline-button", () -> {
                    pluginListingError = "";
                    if (codex != null && !pluginLoading) loadPlugins(codex);
                    show("plugins");
                }),
                button("Instalar de pasta ou link", "outline-button", this::addMarketplace),
                button("Gerenciar MCPs", "primary-button", () -> show("mcps"))));
        HBox columns = row(18);
        VBox left = new VBox(16);
        VBox right = new VBox(16);
        left.setPrefWidth(770);
        right.setPrefWidth(320);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        Label pluginStatus = label("Carregando plugins do Codex...", "muted");
        TextField search = field("", "Buscar plugin por nome ou marketplace");
        search.setPrefWidth(245);
        HBox filters = row(2);
        filters.getStyleClass().add("segments");
        for (String option : List.of("Todos", "Instalados", "Catálogo")) {
            filters.getChildren().add(button(option, pluginFilter.equals(option) ? "segment selected" : "segment", () -> {
                pluginFilter = option;
                show("plugins");
            }));
        }
        left.getChildren().add(row(10, filters, grow(), search));
        left.getChildren().add(pluginStatus);
        FlowPane pluginList = new FlowPane(13, 13);
        left.getChildren().add(pluginList);

        VBox mcpSummary = card();
        long active = project.mcps.stream().filter(mcp -> mcp.enabled).count();
        mcpSummary.getChildren().addAll(label("MCPs do projeto", "section-title"),
                label(active + " ativos de " + project.mcps.size() + " configurados", "muted"),
                label("Cada tarefa recebe os servidores ativos deste projeto.", "tiny-muted"),
                button("Abrir gerenciador", "outline-button", () -> show("mcps")));
        right.getChildren().add(mcpSummary);
        VBox pluginInfo = card();
        pluginInfo.getChildren().addAll(label("Sobre os plugins", "section-title"),
                label("Instalação feita pelo Codex CLI neste computador. Ela vale para sua configuração local, não apenas para este projeto.", "muted"),
                label("A lista inclui as integrações Gmail e Atlassian, além de recursos do Codex como Pages e Sites. Por isso pode ter mais itens que o painel de conexões do Codex.", "tiny-muted"),
                label("Alguns plugins pedem conexão ou login adicional quando usados.", "tiny-muted"));
        right.getChildren().add(pluginInfo);
        columns.getChildren().addAll(left, right);
        page.getChildren().add(columns);
        if (codex == null) {
            pluginStatus.setText("Instale ou configure o Codex CLI para gerenciar plugins.");
        } else {
            if (!codex.equals(pluginListingExecutable)) {
                pluginListing = null;
                pluginListingError = "";
            }
            if (pluginListing != null) {
                PluginService.Listing listing = pluginListing;
                pluginStatus.setText(listing.installed().size() + " instalados · "
                        + listing.available().size() + " no catálogo"
                        + (pluginListingError.isBlank() ? "" : " · Não foi possível atualizar"));
                Runnable render = () -> renderPlugins(pluginList, search.getText(), listing, codex, pluginStatus);
                search.textProperty().addListener((o, before, after) -> render.run());
                render.run();
            } else if (!pluginListingError.isBlank()) {
                pluginStatus.setText("Não foi possível carregar plugins: " + pluginListingError);
            }
            if (!pluginLoading && pluginListingError.isBlank()
                    && (pluginListing == null
                    || System.currentTimeMillis() - pluginListingUpdatedAt > 60_000))
                loadPlugins(codex);
        }
        return scroll(page);
    }

    private void loadPlugins(String codex) {
        pluginLoading = true;
        pluginListingExecutable = codex;
        pluginListingError = "";
        pluginWorkers.submit(() -> {
            PluginService.Listing result = null;
            String failure = "";
            try { result = pluginService.list(codex); }
            catch (Exception error) { failure = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage(); }
            PluginService.Listing listing = result;
            String message = failure;
            Platform.runLater(() -> {
                if (!codex.equals(pluginListingExecutable)) return;
                pluginLoading = false;
                if (listing != null) {
                    pluginListing = listing;
                    pluginListingUpdatedAt = System.currentTimeMillis();
                    pluginListingError = "";
                } else pluginListingError = message;
                if (screen.equals("plugins")) show("plugins");
            });
        });
    }

    private void renderPlugins(FlowPane container, String query, PluginService.Listing listing,
                               String codex, Label status) {
        container.getChildren().clear();
        String search = query.trim().toLowerCase(Locale.ROOT);
        List<PluginService.Plugin> plugins;
        if (pluginFilter.equals("Instalados")) plugins = listing.installed();
        else if (pluginFilter.equals("Catálogo")) plugins = listing.available();
        else {
            plugins = new ArrayList<>(listing.installed());
            Set<String> installedIds = new HashSet<>();
            listing.installed().forEach(plugin -> installedIds.add(plugin.id()));
            listing.available().stream().filter(plugin -> !installedIds.contains(plugin.id()))
                    .forEach(plugins::add);
        }
        List<PluginService.Plugin> matches = plugins.stream().filter(plugin -> search.isBlank()
                || pluginDisplayName(plugin).toLowerCase(Locale.ROOT).contains(search)
                || (pluginDisplayName(plugin).equalsIgnoreCase("Atlassian")
                    && "bitbucket".contains(search))
                || plugin.id().toLowerCase(Locale.ROOT).contains(search)
                || plugin.marketplace().toLowerCase(Locale.ROOT).contains(search)).toList();
        if (matches.isEmpty()) {
            container.getChildren().add(emptyText(pluginFilter.equals("Instalados")
                    ? "Nenhum plugin instalado neste Codex CLI. Veja Todos ou o Catálogo."
                    : "Nenhum plugin encontrado."));
            return;
        }
        matches.stream().limit(30).forEach(plugin -> {
            String displayName = pluginDisplayName(plugin);
            Button action = button(plugin.installed() ? "Remover" : "Instalar", "outline-button", () -> {});
            action.setOnAction(event -> {
                action.setDisable(true);
                status.setText(plugin.installed() ? "Removendo " + displayName + "..."
                        : "Instalando " + displayName + "...");
                pluginWorkers.submit(() -> {
                    try {
                        if (plugin.installed()) pluginService.remove(codex, plugin.id());
                        else pluginService.install(codex, plugin.id());
                        Platform.runLater(() -> {
                            pluginListing = null;
                            pluginListingError = "";
                            if (screen.equals("plugins")) show("plugins");
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            action.setDisable(false);
                            status.setText("Não foi possível alterar o plugin.");
                            alert("Plugins do Codex", e.getMessage());
                        });
                    }
                });
            });
            VBox tile = card();
            tile.getStyleClass().add("plugin-tile");
            tile.setPrefWidth(236);
            tile.setMinHeight(170);
            tile.getChildren().addAll(row(9, pluginIcon(plugin, displayName), label(displayName, "row-title"), grow()),
                    label(pluginDisplayName(plugin).equalsIgnoreCase("Atlassian")
                            ? "Bitbucket · " + plugin.marketplace() : plugin.marketplace(), "muted"),
                    label("versão " + plugin.version(), "tiny-muted"), growV(),
                    row(7, badge(plugin.installed() ? "Instalado" : "Disponível"), grow(), action));
            container.getChildren().add(tile);
        });
        if (matches.size() > 30) container.getChildren().add(
                label("Mostrando 30 de " + matches.size() + ". Use a busca para filtrar.", "tiny-muted"));
    }

    private String pluginDisplayName(PluginService.Plugin plugin) {
        return plugin.id().startsWith("atlassian-rovo@") ? "Atlassian Rovo" : plugin.name();
    }

    private Node pluginIcon(PluginService.Plugin plugin, String displayName) {
        String source = "";
        if (!plugin.iconPath().isBlank()) {
            try { source = Path.of(plugin.iconPath()).toUri().toString(); }
            catch (RuntimeException ignored) { }
        } else if (plugin.id().startsWith("app-6a83901dde988191b3f3cefdcc19acfa@")) {
            var resource = getClass().getResource("/plugin-icons/atlassian.png");
            if (resource != null) source = resource.toExternalForm();
        }
        if (!source.isBlank()) {
            Image image = new Image(source, 32, 32, true, true, false);
            if (!image.isError()) {
                ImageView view = new ImageView(image);
                view.setFitWidth(32);
                view.setFitHeight(32);
                view.setPreserveRatio(true);
                StackPane icon = new StackPane(view);
                icon.getStyleClass().add("plugin-icon");
                icon.setMinSize(35, 35);
                icon.setPrefSize(35, 35);
                icon.setMaxSize(35, 35);
                return icon;
            }
        }
        return label(displayName.isBlank() ? "?"
                : displayName.substring(0, 1).toUpperCase(Locale.ROOT), "plugin-mark");
    }

    private void addMarketplace() {
        String codex = codexService.executable();
        if (codex == null) { alert("Codex", "Codex CLI não encontrado."); return; }
        Dialog<ButtonType> dialog = dialog("Adicionar fonte de plugins");
        TextField source = field("", "Pasta local, owner/repo ou URL Git");
        VBox form = new VBox(10, label("O Codex instala plugins de fontes configuradas como marketplace.", "muted"), source);
        form.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(form);
        dialog.showAndWait().ifPresent(result -> {
            if (result.getButtonData() != ButtonBar.ButtonData.OK_DONE || source.getText().isBlank()) return;
            pluginWorkers.submit(() -> {
                try {
                    pluginService.addMarketplace(codex, source.getText().trim());
                    Platform.runLater(() -> {
                        pluginListing = null;
                        pluginListingError = "";
                        pluginFilter = "Catálogo";
                        show("plugins");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> alert("Fonte de plugins", e.getMessage()));
                }
            });
        });
    }

    private Node mcps() {
        Project project = project();
        VBox page = page();
        long active = project.mcps.stream().filter(mcp -> mcp.enabled).count();
        page.getChildren().add(row(12,
                heading("MCPs", "Servidores que dão ferramentas aos agentes. " + project.mcps.size()
                        + " configurados, " + active + " ativos."), grow(),
                button("Importar JSON", "outline-button", this::importMcps),
                button("+ Adicionar servidor", "primary-button", () -> editMcp(null))));
        if (selectedMcpId.isBlank() && !project.mcps.isEmpty()) selectedMcpId = project.mcps.getFirst().id;
        McpServer selected = project.mcps.stream().filter(mcp -> mcp.id.equals(selectedMcpId)).findFirst().orElse(null);

        HBox columns = row(18);
        VBox list = new VBox(9);
        VBox details = card();
        list.setPrefWidth(410);
        details.setPrefWidth(650);
        details.setMinHeight(590);
        HBox.setHgrow(details, Priority.ALWAYS);
        TextField search = field("", "Buscar servidor");
        list.getChildren().add(search);
        VBox results = new VBox(9);
        list.getChildren().add(results);
        Runnable render = () -> {
            results.getChildren().clear();
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            for (McpServer mcp : project.mcps) {
                if (!mcp.name.toLowerCase(Locale.ROOT).contains(query)) continue;
                boolean remote = mcp.url != null && !mcp.url.isBlank();
                Button tile = new Button();
                tile.getStyleClass().add("mcp-item");
                if (mcp.id.equals(selectedMcpId)) tile.getStyleClass().add("selected");
                tile.setMaxWidth(Double.MAX_VALUE);
                tile.setMinHeight(74);
                tile.setPrefHeight(74);
                tile.setMaxHeight(74);
                StackPane serverIcon = new StackPane(UiIcons.icon(remote ? "link" : "terminal", 18));
                serverIcon.getStyleClass().add("mcp-icon");
                HBox tileContent = row(10, serverIcon,
                        new VBox(3, label(mcp.name, "row-title"),
                                label(shorten(remote ? mcp.url : mcp.command + " " + mcp.arguments.replace('\n', ' '), 43), "tiny-muted")),
                        grow(), label(mcp.enabled ? "Ativo" : "Desligado", mcp.enabled ? "green-text" : "muted"));
                tileContent.setMaxHeight(48);
                tile.setGraphic(tileContent);
                tile.setOnAction(event -> { selectedMcpId = mcp.id; show("mcps"); });
                results.getChildren().add(tile);
            }
            if (results.getChildren().isEmpty()) results.getChildren().add(emptyText("Nenhum servidor encontrado."));
        };
        search.textProperty().addListener((o, before, after) -> render.run());
        render.run();

        if (selected == null) {
            details.getChildren().addAll(label("Selecione um MCP", "section-title"),
                    label("Adicione um servidor local ou remoto para disponibilizar ferramentas aos agentes.", "muted"));
        } else {
            boolean remote = selected.url != null && !selected.url.isBlank();
            CheckBox enabled = new CheckBox("Ativo neste projeto");
            enabled.setSelected(selected.enabled);
            enabled.selectedProperty().addListener((o, before, after) -> {
                selected.enabled = after;
                save();
                show("mcps");
            });
            details.getChildren().addAll(row(9, label(selected.name, "section-title"),
                            badge(remote ? "remoto" : "local"), grow(),
                            button("Testar configuração", "outline-button", () -> alert("MCP: " + selected.name, validateMcp(selected))),
                            button("Editar", "outline-button", () -> editMcp(selected))),
                    enabled,
                    label("CONFIGURAÇÃO", "eyebrow"));
            VBox config = new VBox(8);
            config.getStyleClass().add("code-panel");
            if (remote) {
                config.getChildren().addAll(label("tipo   HTTP", "code-muted"),
                        label("url    " + selected.url, "code-line"));
            } else {
                config.getChildren().addAll(label("tipo       stdio", "code-muted"),
                        label("comando    " + selected.command, "code-line"),
                        label("argumentos " + selected.arguments.replace('\n', ' '), "code-line"));
            }
            details.getChildren().addAll(config, label("ACESSO E ESTADO", "eyebrow"),
                    label(selected.enabled ? "Será passado às próximas tarefas dos agentes." : "Desligado para as próximas tarefas.", "muted"),
                    label("O teste acima valida endereço ou comando. A conexão e as ferramentas são verificadas pelo Codex quando uma tarefa começa.", "tiny-muted"));
        }
        columns.getChildren().addAll(list, details);
        page.getChildren().add(columns);
        return scroll(page);
    }

    private String validateMcp(McpServer mcp) {
        if (mcp.url != null && !mcp.url.isBlank()) {
            try {
                URI url = URI.create(mcp.url);
                if (!List.of("http", "https").contains(url.getScheme()) || url.getHost() == null)
                    return "A URL deve usar http ou https e conter um endereço válido.";
                return "URL válida. A conexão real será tentada pelo Codex na próxima tarefa.";
            } catch (IllegalArgumentException e) {
                return "URL inválida: " + e.getMessage();
            }
        }
        if (mcp.command == null || mcp.command.isBlank()) return "Informe um comando para o servidor local.";
        try {
            Path command = Path.of(mcp.command);
            if (command.isAbsolute() || mcp.command.contains("\\") || mcp.command.contains("/")) {
                return Files.isRegularFile(command) ? "Comando encontrado. O Codex tentará iniciá-lo na próxima tarefa."
                        : "O arquivo do comando não foi encontrado.";
            }
        } catch (RuntimeException e) {
            return "Caminho do comando inválido.";
        }
        String path = System.getenv().getOrDefault("PATH", "");
        for (String directory : path.split(java.io.File.pathSeparator)) {
            for (String extension : List.of("", ".exe", ".cmd", ".bat")) {
                if (!directory.isBlank() && Files.isRegularFile(Path.of(directory, mcp.command + extension)))
                    return "Comando encontrado no PATH. O Codex tentará iniciá-lo na próxima tarefa.";
            }
        }
        return "Comando não encontrado no PATH deste computador.";
    }

    private void importMcps() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar configuração MCP em JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        try (var reader = Files.newBufferedReader(selected.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject servers = root.has("mcpServers") && root.get("mcpServers").isJsonObject()
                    ? root.getAsJsonObject("mcpServers") : root.has("mcp_servers") && root.get("mcp_servers").isJsonObject()
                    ? root.getAsJsonObject("mcp_servers") : root;
            int imported = 0;
            int skipped = 0;
            for (var item : servers.entrySet()) {
                if (!item.getKey().matches("[A-Za-z0-9_-]+") || !item.getValue().isJsonObject()
                        || project().mcps.stream().anyMatch(mcp -> mcp.name.equals(item.getKey()))) {
                    skipped++;
                    continue;
                }
                JsonObject config = item.getValue().getAsJsonObject();
                if (config.has("env")) { skipped++; continue; }
                McpServer mcp = new McpServer();
                mcp.name = item.getKey();
                mcp.command = config.has("command") ? config.get("command").getAsString() : "";
                mcp.url = config.has("url") ? config.get("url").getAsString() : "";
                if (config.has("args") && config.get("args").isJsonArray()) {
                    List<String> args = new ArrayList<>();
                    for (JsonElement arg : config.getAsJsonArray("args")) args.add(arg.getAsString());
                    mcp.arguments = String.join("\n", args);
                }
                if (mcp.command.isBlank() && mcp.url.isBlank()) { skipped++; continue; }
                project().mcps.add(mcp);
                selectedMcpId = mcp.id;
                imported++;
            }
            save();
            alert("Importação MCP", imported + " servidores importados; " + skipped
                    + " ignorados. Entradas com variáveis de ambiente precisam de configuração manual.");
            show("mcps");
        } catch (IOException | RuntimeException e) {
            alert("Importação MCP", "Não foi possível ler o JSON: " + e.getMessage());
        }
    }

    private void editMcp(McpServer existing) {
        Dialog<ButtonType> dialog = dialog(existing == null ? "Novo servidor MCP" : "Editar servidor MCP");
        TextField name = field(existing == null ? "" : existing.name, "Nome sem espaços");
        ComboBox<String> transport = new ComboBox<>();
        transport.getItems().addAll("Local (stdio)", "Remoto (HTTP)");
        transport.setValue(existing != null && existing.url != null && !existing.url.isBlank()
                ? "Remoto (HTTP)" : "Local (stdio)");
        TextField command = field(existing == null ? "" : existing.command,
                "Comando ou caminho do executável");
        TextArea args = area(existing == null ? "" : existing.arguments,
                "Um argumento por linha", 4);
        TextField url = field(existing == null || existing.url == null ? "" : existing.url,
                "https://servidor.example/mcp");
        CheckBox enabled = new CheckBox("Ativo neste projeto");
        enabled.setSelected(existing == null || existing.enabled);
        VBox localFields = new VBox(10, label("Comando", "field-label"), command,
                label("Argumentos", "field-label"), args);
        VBox remoteFields = new VBox(10, label("URL", "field-label"), url);
        Runnable visibility = () -> {
            boolean remote = transport.getValue().equals("Remoto (HTTP)");
            localFields.setVisible(!remote);
            localFields.setManaged(!remote);
            remoteFields.setVisible(remote);
            remoteFields.setManaged(remote);
        };
        transport.valueProperty().addListener((o, before, after) -> visibility.run());
        visibility.run();
        VBox form = new VBox(11, label("Nome", "field-label"), name,
                label("Tipo", "field-label"), transport, localFields, remoteFields, enabled);
        form.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(form);
        ButtonType delete = new ButtonType("Excluir", ButtonBar.ButtonData.LEFT);
        if (existing != null) dialog.getDialogPane().getButtonTypes().add(0, delete);
        dialog.showAndWait().ifPresent(result -> {
            if (result == delete && existing != null) {
                if (confirm("Excluir MCP", "Excluir o servidor " + existing.name + "?")) {
                    project().mcps.remove(existing);
                    if (selectedMcpId.equals(existing.id)) selectedMcpId = "";
                    save();
                    show("mcps");
                }
                return;
            }
            if (result.getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
            if (!name.getText().trim().matches("[A-Za-z0-9_-]+")) {
                alert("Nome do MCP", "Use apenas letras, números, _ e -.");
                return;
            }
            boolean remote = transport.getValue().equals("Remoto (HTTP)");
            if (remote && url.getText().isBlank()) { alert("URL", "Informe a URL do servidor MCP."); return; }
            if (!remote && command.getText().isBlank()) { alert("Comando", "Informe o comando do servidor MCP."); return; }
            if (project().mcps.stream().anyMatch(mcp -> mcp != existing && mcp.name.equals(name.getText().trim()))) {
                alert("Nome do MCP", "Já existe um servidor com esse nome.");
                return;
            }
            McpServer mcp = existing == null ? new McpServer() : existing;
            mcp.name = name.getText().trim();
            mcp.command = remote ? "" : command.getText().trim();
            mcp.arguments = remote ? "" : args.getText().trim();
            mcp.url = remote ? url.getText().trim() : "";
            mcp.enabled = enabled.isSelected();
            if (existing == null) project().mcps.add(mcp);
            selectedMcpId = mcp.id;
            save();
            show("mcps");
        });
    }

    private Node models() {
        Project project = project();
        VBox page = page();
        page.getChildren().add(row(14,
                heading("Modelos e conexão", "Use o login do Codex neste computador e escolha o modelo de cada agente."),
                grow(), button("Testar conexão", "outline-button", () -> show("models"))));

        HBox columns = row(18);
        VBox left = new VBox(16);
        VBox right = new VBox(16);
        left.setPrefWidth(690);
        right.setPrefWidth(380);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        VBox configured = card();
        List<String> detectedModels = codexService.availableModels();
        String detectedDefault = codexService.defaultModel();
        configured.getChildren().addAll(label("Modelos para selecionar", "section-title"),
                label("Mesmas opções do VS Code. O Codex confirma o acesso da sua conta ao executar.", "tiny-muted"));
        List<String> modelNames = new ArrayList<>(List.of("Padrão do Codex", "gpt-6.1-sol",
                "gpt-6-astra", "gpt-6-sol", "gpt-6-luna", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna"));
        detectedModels.stream().filter(model -> !modelNames.contains(model)).forEach(modelNames::add);
        project.agents.stream().map(agent -> agent.model).filter(model -> model != null && !model.isBlank())
                .filter(model -> !modelNames.contains(model)).forEach(modelNames::add);
        for (String modelName : modelNames) {
            long count = project.agents.stream().filter(agent -> modelName.equals(agent.model)).count();
            configured.getChildren().add(row(10, label(modelDisplayName(modelName), "row-title"), grow(),
                    detectedModels.isEmpty() || modelName.equals("Padrão do Codex") || detectedModels.contains(modelName)
                            ? new Region() : label("não listado neste CLI", "tiny-muted"),
                    label(count + (count == 1 ? " agente" : " agentes"), "tiny-muted")));
        }
        left.getChildren().add(configured);

        VBox assignments = card();
        assignments.getChildren().addAll(label("Modelo de cada agente", "section-title"),
                label(detectedDefault.isBlank()
                        ? "Padrão usa a escolha do Codex CLI. Níveis de raciocínio variam por modelo."
                        : "Padrão usará " + detectedDefault + ", disponível no Codex CLI. Níveis de raciocínio variam por modelo.",
                        "tiny-muted"));
        for (Agent agent : project.agents) {
            ComboBox<String> model = new ComboBox<>();
            model.getStyleClass().add("model-select");
            model.getItems().addAll(modelNames);
            model.getItems().add("Outro ID...");
            model.setCellFactory(list -> modelOptionCell());
            model.setButtonCell(modelOptionCell());
            model.setValue(agent.model == null || agent.model.isBlank() ? "Padrão do Codex" : agent.model);
            model.setPrefWidth(195);
            model.setDisable(!agent.status.equals("disponível"));
            model.valueProperty().addListener((o, before, after) -> {
                if (after == null) return;
                if (after.equals("Outro ID...")) {
                    editModelId(agent);
                } else {
                    agent.model = after;
                    save();
                    show("models");
                }
            });
            ComboBox<String> effort = new ComboBox<>();
            effort.getStyleClass().add("model-select");
            effort.getItems().addAll("Padrão", "low", "medium", "high", "xhigh", "max");
            effort.setValue(agent.reasoningEffort == null ? "Padrão" : agent.reasoningEffort);
            effort.setPrefWidth(113);
            effort.setDisable(!agent.status.equals("disponível"));
            effort.valueProperty().addListener((o, before, after) -> {
                if (after == null) return;
                agent.reasoningEffort = after;
                save();
            });
            HBox line = row(10, avatar(agent), label(agent.name, "row-title"), grow(), model, effort);
            line.getStyleClass().add("list-row");
            assignments.getChildren().add(line);
        }
        left.getChildren().add(assignments);

        VBox connection = card();
        String executable = codexService.executable();
        Label connectionState = label(executable == null ? "Codex CLI não encontrado" : "Verificando autenticação...", "row-title");
        Label connectionDetail = label(executable == null
                ? "Escolha o executável do Codex instalado neste computador." : "", "tiny-muted");
        Label version = label("", "tiny-muted");
        Button login = button("Entrar com ChatGPT", "outline-button", () -> {
            connectionState.setText("Aguardando login no navegador...");
            workers.submit(() -> {
                try {
                    connectionService.login(executable);
                    Platform.runLater(() -> { if (screen.equals("models")) show("models"); });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        connectionState.setText("Não foi possível conectar");
                        connectionDetail.setText(e.getMessage());
                    });
                }
            });
        });
        login.setDisable(executable == null);
        Button locate = button("Localizar Codex CLI", "outline-button", () -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Selecionar executável do Codex CLI");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Executável do Codex", "*.cmd", "*.exe"),
                    new FileChooser.ExtensionFilter("Todos os arquivos", "*.*"));
            java.io.File selected = chooser.showOpenDialog(stage);
            if (selected == null) return;
            data.codexPath = selected.getAbsolutePath();
            codexService.setConfiguredExecutable(data.codexPath);
            save();
            show("models");
        });
        connection.getChildren().addAll(label("Conta do Codex", "section-title"), connectionState,
                connectionDetail, version, label(executable == null ? "Caminho ainda não configurado" : executable, "tiny-muted"),
                row(8, login, locate));
        right.getChildren().add(connection);
        if (executable != null) workers.submit(() -> {
            CodexConnectionService.Status status = connectionService.status(executable);
            Platform.runLater(() -> {
                connectionState.setText(status.connected() ? "Conectado por " + status.method() : "Não conectado");
                connectionDetail.setText(status.connected()
                        ? "Autenticação confirmada pelo Codex CLI." : status.detail());
                version.setText(status.version());
                login.setText(status.connected() ? "Reconectar" : "Entrar com ChatGPT");
            });
        });

        refreshUsage(executable, false);
        right.getChildren().add(planUsageCard(executable));
        columns.getChildren().addAll(left, right);
        page.getChildren().add(columns);
        return scroll(page);
    }

    private VBox planUsageCard(String executable) {
        VBox usage = card();
        usage.getStyleClass().add("usage-card");
        Button resets = button("↺", "usage-reset-icon", () -> showResetCredits(executable));
        resets.setTooltip(new Tooltip("Ver resets disponíveis"));
        resets.setDisable(executable == null || usageSnapshot == null || resetApplying);
        Button update = button("Atualizar", "link-button", () -> refreshUsage(executable, true));
        update.setDisable(executable == null || usageLoading);
        usage.getChildren().add(row(8, label("Uso do seu plano", "section-title"), grow(),
                resets, update));
        if (executable == null) {
            usage.getChildren().add(label("Localize o Codex CLI para consultar seus limites.", "muted"));
        } else if (usageSnapshot == null) {
            usage.getChildren().add(label(usageLoading ? "Consultando limites do Codex..."
                    : usageError.isBlank() ? "Ainda não há dados de uso disponíveis." : usageError, "muted"));
        } else {
            usage.getChildren().add(usageWindow("Limite de 5 horas", usageSnapshot.shortWindow()));
            usage.getChildren().add(usageWindow("Limite semanal", usageSnapshot.weeklyWindow()));
            String count = resetApplying ? "Aplicando reset..."
                    : usageSnapshot.availableResets() == null ? "Resets não informados"
                    : usageSnapshot.availableResets() + (usageSnapshot.availableResets() == 1
                    ? " reset disponível" : " resets disponíveis");
            usage.getChildren().add(label(count, "tiny-muted"));
            if (!usageError.isBlank()) usage.getChildren().add(label(usageError, "tiny-muted"));
        }
        return usage;
    }

    private VBox usageWindow(String expectedTitle, CodexUsageService.Window window) {
        VBox section = new VBox(5);
        section.getStyleClass().add("usage-window");
        if (window == null) {
            section.getChildren().addAll(label(expectedTitle, "row-title"),
                    label("O Codex não informou este limite.", "tiny-muted"));
            return section;
        }
        String title = expectedTitle;
        if (expectedTitle.contains("5 horas") && window.durationMinutes() != 300)
            title = "Janela de " + durationLabel(window.durationMinutes());
        if (expectedTitle.contains("semanal") && window.durationMinutes() != 10_080)
            title = "Janela de " + durationLabel(window.durationMinutes());
        int used = (int) Math.round(window.usedPercent());
        ProgressBar bar = new ProgressBar(window.usedPercent() / 100.0);
        bar.getStyleClass().add("usage-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        Label resetLabel = label("Resta " + (100 - used) + "% · Reinicia em "
                + resetTime(window.resetsAt()), "tiny-muted");
        resetLabel.setWrapText(true);
        section.getChildren().addAll(row(6, label(title, "row-title"), grow(),
                        label(used + "% usado", "usage-percent")), bar, resetLabel);
        return section;
    }

    private String durationLabel(int minutes) {
        if (minutes >= 1_440 && minutes % 1_440 == 0) return minutes / 1_440 + " dia(s)";
        if (minutes >= 60 && minutes % 60 == 0) return minutes / 60 + " hora(s)";
        return minutes + " min";
    }

    private String resetTime(Long unixSeconds) {
        if (unixSeconds == null || unixSeconds <= 0) return "horário não informado";
        try {
            return Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
        } catch (RuntimeException ignored) {
            return "horário não informado";
        }
    }

    private void refreshUsage(String executable, boolean force) {
        if (executable == null || usageLoading) return;
        if (!executable.equals(usageExecutable)) {
            usageExecutable = executable;
            usageSnapshot = null;
            usageLastFetchedAt = 0;
        }
        long now = System.currentTimeMillis();
        if (!force && now - usageLastFetchedAt < 120_000) return;
        usageLoading = true;
        usageLastFetchedAt = now;
        scheduleWorker.submit(() -> {
            CodexUsageService.UsageSnapshot latest = null;
            String error = "";
            try { latest = usageService.read(executable); }
            catch (IOException exception) { error = exception.getMessage(); }
            CodexUsageService.UsageSnapshot result = latest;
            String failure = error;
            Platform.runLater(() -> {
                usageLoading = false;
                if (result != null) usageSnapshot = result;
                usageError = failure;
                if (screen.equals("models")) show("models");
                else if (screen.equals("chat") && chatUsageHost != null)
                    chatUsageHost.getChildren().setAll(planUsageCard(executable));
            });
        });
    }

    private void showResetCredits(String executable) {
        if (resetApplying) return;
        if (usageSnapshot == null) {
            alert("Resets", "Atualize os dados de uso para ver os resets disponíveis.");
            return;
        }
        Dialog<ButtonType> modal = dialog("Resets disponíveis");
        modal.getDialogPane().getButtonTypes().setAll(
                new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        VBox content = new VBox(12);
        Integer count = usageSnapshot.availableResets();
        if (count == null) {
            content.getChildren().add(label("O Codex não informou quantos resets estão disponíveis.", "muted"));
        } else if (count == 0) {
            content.getChildren().add(label("Você não tem resets disponíveis no momento.", "muted"));
        } else {
            content.getChildren().add(label(count + (count == 1 ? " reset disponível" : " resets disponíveis"),
                    "section-title"));
            for (CodexUsageService.ResetCredit credit : usageSnapshot.resets()) {
                String title = credit.title().isBlank() ? "Reset do limite do Codex" : credit.title();
                Button choice = button(title, "usage-credit-button", () -> confirmReset(modal, executable,
                        credit.id(), title));
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.setWrapText(true);
                VBox detail = new VBox(4, choice);
                detail.getStyleClass().add("usage-credit-row");
                if (!credit.description().isBlank()) detail.getChildren().add(label(credit.description(), "tiny-muted"));
                if (credit.expiresAt() != null)
                    detail.getChildren().add(label("Expira em " + resetTime(credit.expiresAt()), "tiny-muted"));
                content.getChildren().add(detail);
            }
            if (count > usageSnapshot.resets().size()) {
                Button next = button("Outro reset disponível", "usage-credit-button",
                        () -> confirmReset(modal, executable, null, "Outro reset disponível"));
                next.setMaxWidth(Double.MAX_VALUE);
                content.getChildren().add(next);
            }
        }
        modal.getDialogPane().setContent(content);
        modal.showAndWait();
    }

    private void confirmReset(Dialog<ButtonType> listModal, String executable, String creditId, String title) {
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirm = new ButtonType("Confirmar e usar reset", ButtonBar.ButtonData.OK_DONE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Deseja mesmo usar este reset? Esta ação consome um reset disponível: " + title,
                cancel, confirm);
        confirmation.setTitle("Confirmar uso do reset");
        confirmation.setHeaderText("Usar reset do Codex?");
        confirmation.initOwner(stage);
        confirmation.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        confirmation.getDialogPane().getStyleClass().add("dark-dialog");
        ((Button) confirmation.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
        ((Button) confirmation.getDialogPane().lookupButton(confirm)).setDefaultButton(false);
        if (confirmation.showAndWait().orElse(cancel) != confirm) return;
        listModal.close();
        resetApplying = true;
        if (screen.equals("models")) show("models");
        workers.submit(() -> {
            String outcome = "";
            String error = "";
            try { outcome = usageService.consume(executable, creditId); }
            catch (Exception exception) { error = exception.getMessage() == null
                    ? "Não foi possível aplicar o reset." : exception.getMessage(); }
            String result = outcome;
            String failure = error;
            Platform.runLater(() -> {
                resetApplying = false;
                if (!failure.isBlank()) alert("Reset do Codex", failure);
                else alert("Reset do Codex", switch (result) {
                    case "reset", "alreadyRedeemed" -> "Reset aplicado. Seus limites serão atualizados.";
                    case "nothingToReset" -> "Não há uma janela de limite elegível para reset agora.";
                    case "noCredit" -> "Não há resets disponíveis nesta conta.";
                    default -> "O Codex respondeu: " + result;
                });
                refreshUsage(executable, true);
            });
        });
    }

    private ListCell<String> modelOptionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : modelDisplayName(item));
            }
        };
    }

    private String modelDisplayName(String model) {
        return switch (model) {
            case "Padrão do Codex" -> "Padrão";
            case "gpt-6.1-sol" -> "GPT-6.1 Sol";
            case "gpt-6-astra" -> "GPT-6 Astra";
            case "gpt-6-sol" -> "GPT-6 Sol";
            case "gpt-6-luna" -> "GPT-6 Luna";
            case "gpt-5.6-sol" -> "GPT-5.6 Sol";
            case "gpt-5.6-terra" -> "GPT-5.6 Terra";
            case "gpt-5.6-luna" -> "GPT-5.6 Luna";
            default -> model;
        };
    }

    private void editModelId(Agent agent) {
        Dialog<ButtonType> dialog = dialog("Modelo de " + agent.name);
        TextField id = field(agent.model == null || agent.model.equals("Padrão do Codex") ? "" : agent.model,
                "ID do modelo no Codex");
        VBox form = new VBox(10, label("Informe um modelo disponível para sua conta.", "muted"), id);
        form.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(form);
        dialog.showAndWait().ifPresent(result -> {
            if (result.getButtonData() == ButtonBar.ButtonData.OK_DONE && !id.getText().isBlank()) {
                agent.model = id.getText().trim();
                save();
            }
        });
        show("models");
    }

    private Node settings() {
        VBox page = page();
        page.getChildren().add(heading("Configurações", "Ajustes gerais do app e regras reais de acesso dos agentes."));
        Project project = project();
        HBox layout = row(20);
        VBox menu = new VBox(4);
        menu.setPrefWidth(205);
        menu.setMinWidth(185);
        for (String section : List.of("Geral", "Segurança e permissões", "Celular e acesso remoto",
                "Privacidade e dados", "Armazenamento", "Sobre")) {
            Button item = button(section, settingsSection.equals(section)
                    ? "settings-nav selected" : "settings-nav", () -> {
                settingsSection = section;
                show("settings");
            });
            item.setMaxWidth(Double.MAX_VALUE);
            menu.getChildren().add(item);
        }
        VBox left = new VBox(14);
        VBox right = new VBox(14);
        left.setPrefWidth(440);
        right.setPrefWidth(400);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        switch (settingsSection) {
            case "Geral" -> {
                left.getChildren().add(generalSettingsCard());
                right.getChildren().add(storageSettingsCard());
            }
            case "Celular e acesso remoto" -> {
                left.getChildren().add(remoteSettingsCard());
                right.getChildren().addAll(
                        label("Como abrir no celular", "section-title"),
                        label("1. Execute start.cmd na pasta Pessoais/maestro-mobile e abra o QR code no Expo Go.", "muted"),
                        label("2. No celular, informe o endereço e a chave exibidos aqui.", "muted"),
                        label("3. Mantenha este computador ligado, com o Maestro aberto, enquanto os agentes trabalham.", "muted"),
                        remoteFirebaseCard());
            }
            case "Privacidade e dados" -> {
                left.getChildren().add(privacySettingsCard(project));
                right.getChildren().add(storageSettingsCard());
            }
            case "Armazenamento" -> {
                left.getChildren().add(storageSettingsCard());
                right.getChildren().add(folderSettingsCard(project));
            }
            case "Sobre" -> {
                VBox about = card();
                about.getChildren().addAll(label("maestro", "section-title"),
                        label("Versão 0.1.0 · aplicativo desktop em Java 21 e JavaFX.", "muted"),
                        label("O trabalho dos agentes é executado pelo Codex CLI instalado neste computador.", "tiny-muted"));
                left.getChildren().add(about);
                right.getChildren().add(remoteSettingsCard());
            }
            default -> {
                left.getChildren().addAll(approvalSettingsCard(project), folderSettingsCard(project), generalSettingsCard());
                right.getChildren().addAll(remoteSettingsCard(), privacySettingsCard(project));
            }
        }
        layout.getChildren().addAll(menu, left, right);
        page.getChildren().add(layout);
        return scroll(page);
    }

    private VBox approvalSettingsCard(Project project) {
        VBox box = card();
        box.getChildren().addAll(row(8, label("Aprovações padrão", "section-title"), grow(),
                        label("para este projeto", "tiny-muted")),
                label("Escrever arquivos do projeto", "row-title"));
        HBox policies = row(5);
        for (String option : List.of("permitir", "aprovar", "bloquear")) {
            Button choice = button(option.substring(0, 1).toUpperCase(Locale.ROOT) + option.substring(1),
                    option.equals(project.writePolicy) ? "permission-option selected " + option : "permission-option",
                    () -> {
                        if (project.writePolicy.equals(option)) return;
                        if (project.agents.stream().anyMatch(agent -> !agent.status.equals("disponível"))
                                || project.approvals.stream().anyMatch(approval -> approval.status.equals("Pendente"))) {
                            alert("Permissões", "Conclua as tarefas e aprovações pendentes antes de mudar esta regra.");
                            return;
                        }
                        if (option.equals("permitir") && !confirm("Permitir edições sem aprovação",
                                "Agentes marcados para edição poderão alterar arquivos sem um pedido por tarefa. Continuar?")) return;
                        project.writePolicy = option;
                        project.agents.forEach(agent -> {
                            agent.sessionId = "";
                            agent.chatSessions.clear();
                        });
                        save();
                        show("settings");
                    });
            policies.getChildren().add(choice);
        }
        box.getChildren().addAll(policies,
                label("Permitir inicia tarefas de escrita sem confirmação; Aprovar pede confirmação por tarefa; Bloquear executa todos em leitura.", "tiny-muted"),
                label("Comandos, web e integrações seguem as permissões e o sandbox do Codex CLI. Aprovações por comando exigem uma integração futura com app-server.", "tiny-muted"));
        return box;
    }

    private VBox folderSettingsCard(Project project) {
        VBox box = card();
        box.getChildren().addAll(label("Pasta acessada pelos agentes", "section-title"),
                label(project.folder.isBlank() ? "Nenhuma pasta vinculada" : project.folder, "muted"),
                label("Esta é a pasta de trabalho passada ao Codex. O sandbox do CLI define os limites efetivos de acesso.", "tiny-muted"),
                button("Editar projeto", "outline-button", () -> editProject(project)));
        return box;
    }

    private VBox generalSettingsCard() {
        VBox box = card();
        CheckBox reduce = new CheckBox("Reduzir animações");
        reduce.setSelected(data.reduceAnimations);
        reduce.selectedProperty().addListener((o, before, after) -> { data.reduceAnimations = after; save(); });
        box.getChildren().addAll(label("Geral", "section-title"),
                row(8, label("Tema", "row-title"), grow(), badge("Escuro")),
                row(8, label("Idioma", "row-title"), grow(), badge("Português (Brasil)")),
                reduce, label("Desativa as transições entre telas.", "tiny-muted"));
        return box;
    }

    private VBox remoteSettingsCard() {
        VBox box = card();
        boolean online = mobileBridge != null && mobileBridge.running();
        box.getChildren().addAll(label("Celular e acesso remoto", "section-title"),
                row(8, dot(online ? "#76bb8b" : "#d77979"),
                        label(online ? "Disponível na rede local" : "Servidor indisponível", online ? "green-text" : "amber-text")));
        if (online) {
            List<String> urls = mobileBridge.localUrls();
            box.getChildren().add(label("No celular, use o mesmo Wi-Fi do computador e informe:", "muted"));
            box.getChildren().add(label("Endereço: " + (urls.isEmpty()
                    ? "http://IP-DO-COMPUTADOR:" + MobileBridgeServer.PORT : urls.getFirst()), "message-text"));
            if (urls.size() > 1) box.getChildren().add(label("Outros endereços: "
                    + String.join(" · ", urls.subList(1, urls.size())), "tiny-muted"));
            box.getChildren().add(label("Chave local (16 caracteres): " + mobileBridge.token(), "message-text"));
            box.getChildren().add(row(8,
                    button("Copiar chave local", "outline-button", () -> {
                        ClipboardContent content = new ClipboardContent();
                        content.putString(mobileBridge.token());
                        Clipboard.getSystemClipboard().setContent(content);
                    }),
                    button("Nova chave", "outline-button", () -> {
                        if (!confirm("Nova chave", "Os celulares conectados precisarão informar a nova chave. Continuar?")) return;
                        try { mobileBridge.regenerateToken(); show("settings"); }
                        catch (IOException error) { alert("Celular", "Não foi possível trocar a chave: " + error.getMessage()); }
                    })));
        } else {
            box.getChildren().add(label("O servidor local não iniciou: " + mobileBridgeError, "muted"));
            if (mobileBridge != null) box.getChildren().add(button("Tentar novamente", "outline-button", () -> {
                mobileBridge.start(); mobileBridgeError = mobileBridge.error(); show("settings");
            }));
        }
        box.getChildren().add(label("Este endereço usa apenas a rede local. Para acessar de qualquer lugar, configure o cartão Firebase abaixo. O computador e o Codex precisam permanecer ligados.", "tiny-muted"));
        return box;
    }

    private VBox remoteFirebaseCard() {
        VBox box = card();
        box.getChildren().add(label("Acesso de qualquer lugar · Firebase", "section-title"));
        if (remoteSettings == null) {
            box.getChildren().add(label("Configuração remota indisponível: " + remoteBridgeError, "amber-text"));
            return box;
        }
        box.getChildren().add(label(remoteSettings.configured()
                ? "Projeto Firebase: " + remoteSettings.projectId()
                : "Selecione a configuração do Firebase no computador.", "muted"));
        Label status = label("", "amber-text");
        refreshRemoteStatus(status);
        box.getChildren().add(status);
        if (remoteBridge != null) {
            Timeline statusUpdates = new Timeline(new KeyFrame(Duration.seconds(1.5),
                    event -> refreshRemoteStatus(status)));
            statusUpdates.setCycleCount(Animation.INDEFINITE);
            status.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null) statusUpdates.stop();
                else statusUpdates.play();
            });
        }
        if (remoteBridge != null) box.getChildren().add(button("Verificar conexão", "outline-button", () -> {
            remoteBridge.checkNow().thenRun(() -> Platform.runLater(() -> {
                if ("settings".equals(screen)) show("settings");
            }));
        }));
        if (!remoteBridgeError.isBlank()) box.getChildren().add(label(remoteBridgeError, "amber-text"));
        if (!remoteSettings.configured()) box.getChildren().add(button("Selecionar firebaseConfig.local.ts",
                "outline-button", this::chooseRemoteConfig));
        if (remoteSettings.configured()) {
            box.getChildren().add(row(8,
                    button(remoteSettings.signedIn() ? "Entrar novamente" : "Entrar no Firebase",
                            "outline-button", this::showRemoteLogin),
                    button("Copiar chave remota", "outline-button", () -> {
                        ClipboardContent content = new ClipboardContent();
                        content.putString(remoteSettings.pairingKey());
                        Clipboard.getSystemClipboard().setContent(content);
                        alert("Chave remota", "Chave copiada. Cole somente no aplicativo Maestro do celular.");
                    }),
                    button("Mostrar chave remota", "outline-button", this::showRemotePairingKey)));
            box.getChildren().add(label("O login do computador usa o usuário criado em Authentication. "
                    + "A senha não é guardada. A chave remota tem 44 caracteres e termina em =; não use a chave local acima.", "tiny-muted"));
            if (remoteSettings.signedIn()) box.getChildren().add(button("Desconectar Firebase",
                    "outline-button", this::disconnectRemote));
        }
        return box;
    }

    private void refreshRemoteStatus(Label status) {
        boolean connected = remoteBridge != null && remoteBridge.checked()
                && remoteBridge.lastError().isBlank();
        String message;
        if (remoteBridge != null && remoteBridge.lastError().contains("HTTP 403")) {
            message = "Firestore negou acesso (403). No Firebase Console, abra Firestore Database → Rules "
                    + "e publique as regras do arquivo firebase/firestore.rules deste projeto.";
        } else if (remoteBridge != null && !remoteBridge.lastError().isBlank()) {
            message = "Erro: " + remoteBridge.lastError();
        } else if (connected) {
            message = "Conectado; buscando pedidos do celular";
        } else if (remoteBridge != null) {
            message = "Verificando regras e conexão com Firestore…";
        } else if (remoteLoginBusy) {
            message = "Conectando ao Firebase…";
        } else if (remoteSettings != null && remoteSettings.signedIn()) {
            message = "Sessão salva; aguardando reconexão";
        } else {
            message = "Aguardando login";
        }
        status.setText(message);
        status.getStyleClass().removeAll("green-text", "amber-text");
        status.getStyleClass().add(connected ? "green-text" : "amber-text");
    }

    private void showRemotePairingKey() {
        Dialog<ButtonType> viewer = new Dialog<>();
        viewer.setTitle("Chave remota do Maestro");
        viewer.initOwner(stage);
        viewer.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        viewer.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        viewer.getDialogPane().getStyleClass().add("dark-dialog");
        viewer.getDialogPane().setPrefWidth(550);
        TextField key = field(remoteSettings.pairingKey(), "");
        key.setEditable(false);
        VBox content = new VBox(10, label("Use esta chave de 44 caracteres no modo De qualquer lugar do celular.", "muted"),
                key, label("Não compartilhe a chave. A chave local de 16 caracteres não funciona no Firebase.", "tiny-muted"));
        content.setPadding(new Insets(12));
        viewer.getDialogPane().setContent(content);
        viewer.showAndWait();
    }

    private void chooseRemoteConfig() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecione firebaseConfig.local.ts");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Configuração TypeScript", "*.ts"));
        java.io.File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        try {
            remoteSettings.importWebConfig(selected.toPath());
            remoteBridgeError = "";
            show("settings");
        } catch (Exception error) { alert("Firebase", "Não foi possível ler a configuração: " + error.getMessage()); }
    }

    private void showRemoteLogin() {
        if (remoteSettings == null || !remoteSettings.configured() || remoteLoginBusy) return;
        Dialog<ButtonType> login = dialog("Entrar no Firebase");
        TextField email = field("", "E-mail do usuário criado em Authentication");
        PasswordField password = new PasswordField();
        password.setPromptText("Senha desse usuário");
        password.getStyleClass().add("input-field");
        VBox content = new VBox(10, label("E-mail", "row-title"), email,
                label("Senha", "row-title"), password,
                label("A senha será usada somente para obter a sessão e não será salva.", "tiny-muted"));
        content.setPadding(new Insets(12));
        login.getDialogPane().setContent(content);
        if (login.showAndWait().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE).isEmpty()) return;
        String enteredEmail = email.getText().trim();
        char[] secret = password.getText().toCharArray();
        password.clear();
        if (enteredEmail.isBlank() || secret.length == 0) {
            alert("Firebase", "Informe o e-mail e a senha do usuário Firebase.");
            return;
        }
        remoteLoginBusy = true;
        remoteBridgeError = "";
        show("settings");
        workers.execute(() -> {
            try {
                FirebaseRemoteClient client = new FirebaseRemoteClient(remoteSettings.clientConfig());
                client.signIn(enteredEmail, new String(secret));
                remoteSettings.setRefreshToken(client.refreshToken());
                Platform.runLater(() -> startRemoteBridge(client));
            } catch (Exception error) {
                Platform.runLater(() -> { remoteBridgeError = error.getMessage();
                    remoteLoginBusy = false; show("settings"); });
            } finally { java.util.Arrays.fill(secret, '\0'); }
        });
    }

    private void restoreRemoteBridge() {
        workers.execute(() -> {
            try {
                FirebaseRemoteClient client = new FirebaseRemoteClient(remoteSettings.clientConfig());
                client.restore(remoteSettings.refreshToken());
                remoteSettings.setRefreshToken(client.refreshToken());
                Platform.runLater(() -> startRemoteBridge(client));
            } catch (Exception error) {
                Platform.runLater(() -> { remoteBridgeError = error.getMessage(); show("settings"); });
            }
        });
    }

    private void startRemoteBridge(FirebaseRemoteClient client) {
        if (remoteBridge != null) remoteBridge.close();
        remoteBridge = null;
        try {
            remoteBridge = new FirebaseRemoteBridge(client, mobileApi,
                    remoteSettings.pairingKey(), repository.home());
            remoteBridge.start();
            remoteBridgeError = "";
        } catch (Exception error) { remoteBridgeError = error.getMessage(); }
        remoteLoginBusy = false;
        show("settings");
    }

    private void disconnectRemote() {
        if (!confirm("Desconectar Firebase", "Parar a conexão remota neste computador?")) return;
        if (remoteBridge != null) remoteBridge.close();
        remoteBridge = null;
        try { remoteSettings.clearLogin(); remoteBridgeError = ""; }
        catch (IOException error) { remoteBridgeError = error.getMessage(); }
        show("settings");
    }

    private VBox privacySettingsCard(Project project) {
        VBox box = card();
        box.getChildren().addAll(label("Privacidade e dados", "section-title"),
                label("Histórico de conversas e tarefas: " + project.allMessages().size() + " mensagens em "
                        + project.chats.size() + " conversas, "
                        + project.tasks.size() + " tarefas.", "muted"),
                label("Os dados do aplicativo ficam locais. O texto enviado aos agentes é processado pelo Codex conforme a conta conectada.", "tiny-muted"),
                row(8, button("Exportar dados", "outline-button", this::exportData),
                        button("Apagar histórico do projeto", "danger-button", () -> clearHistory(project))),
                label("O backup inclui dados e arquivos do aplicativo. Sessões próprias do Codex CLI ficam fora dele.", "tiny-muted"));
        return box;
    }

    private VBox storageSettingsCard() {
        VBox box = card();
        box.getChildren().addAll(label("Armazenamento", "section-title"),
                label(repository.home().toString(), "muted"),
                label("Projetos, conversas, anexos e índice de conhecimento são guardados nesta pasta.", "tiny-muted"),
                button("Exportar backup ZIP", "outline-button", this::exportData));
        return box;
    }

    private void exportData() {
        save();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar dados do maestro");
        chooser.setInitialFileName("maestro-backup.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivo ZIP", "*.zip"));
        java.io.File destination = chooser.showSaveDialog(stage);
        if (destination == null) return;
        workers.submit(() -> {
            try {
                repository.exportBackup(destination.toPath());
                Platform.runLater(() -> alert("Backup", "Dados exportados para " + destination.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> alert("Backup", "Não foi possível exportar: " + e.getMessage()));
            }
        });
    }

    private void clearHistory(Project project) {
        if (project.agents.stream().anyMatch(agent -> !agent.status.equals("disponível"))) {
            alert("Histórico", "Aguarde as tarefas em andamento terminarem.");
            return;
        }
        if (!confirm("Apagar histórico", "Apagar as conversas, tarefas e aprovações deste projeto? Esta ação não remove arquivos nem fontes de conhecimento.")) return;
        project.chats.forEach(chat -> chat.messages.clear());
        project.messages.clear();
        project.tasks.clear();
        project.approvals.clear();
        project.agents.forEach(agent -> {
            agent.sessionId = "";
            agent.chatSessions.clear();
        });
        save();
        show("settings");
    }
}
