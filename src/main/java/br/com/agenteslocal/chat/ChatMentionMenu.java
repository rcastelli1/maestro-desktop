package br.com.agenteslocal.chat;

import br.com.agenteslocal.character.AgentCharacterView;
import br.com.agenteslocal.model.WorkspaceData.Agent;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Autocomplete for @agent mentions in the chat composer. */
public final class ChatMentionMenu {
    private record Mention(int start, int caret, String query) { }

    private final TextField input;
    private final List<Agent> agents;
    private final Consumer<Agent> onChosen;
    private final Popup popup = new Popup();
    private final VBox panel = new VBox(6);
    private final ListView<Agent> results = new ListView<>();
    private final Label empty = new Label("Nenhum agente encontrado");
    private Mention mention;

    public ChatMentionMenu(TextField input, List<Agent> agents, Consumer<Agent> onChosen,
                           String stylesheet) {
        this.input = input;
        this.agents = List.copyOf(agents);
        this.onChosen = onChosen;

        Label title = new Label("MENCIONAR AGENTE");
        title.getStyleClass().add("mention-heading");
        panel.getStyleClass().add("mention-panel");
        results.getStyleClass().add("mention-results");
        results.setFixedCellSize(46);
        results.setPrefWidth(330);
        results.setFocusTraversable(false);
        results.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Agent agent, boolean blank) {
                super.updateItem(agent, blank);
                setText(null);
                if (blank || agent == null) {
                    setGraphic(null);
                    return;
                }
                AgentCharacterView avatar = new AgentCharacterView(agent.visualId, 27, false,
                        false, agent.color, agent.eyeColor, false, agent.animations);
                Label name = new Label(agent.name);
                name.getStyleClass().add("mention-name");
                Label role = new Label(agent.role == null ? "" : agent.role);
                role.getStyleClass().add("mention-role");
                role.setMaxWidth(235);
                role.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                VBox labels = new VBox(1, name, role);
                HBox line = new HBox(10, avatar, labels);
                line.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(labels, Priority.ALWAYS);
                setGraphic(line);
            }
        });
        results.setOnMouseClicked(event -> {
            Node target = event.getPickResult().getIntersectedNode();
            while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
            if (target instanceof ListCell<?> cell && cell.getItem() instanceof Agent agent)
                choose(agent);
        });
        empty.getStyleClass().add("mention-empty");
        panel.getChildren().addAll(title, results, empty);
        panel.getStylesheets().add(stylesheet);
        popup.getContent().add(panel);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        input.textProperty().addListener((observable, before, after) -> Platform.runLater(this::refresh));
        input.caretPositionProperty().addListener((observable, before, after) -> Platform.runLater(this::refresh));
        input.sceneProperty().addListener((observable, before, scene) -> {
            if (scene == null) popup.hide();
        });
    }

    public void refresh() {
        if (!input.isFocused() || input.getScene() == null) { hide(); return; }
        Mention current = mentionAt(input.getText(), input.getCaretPosition());
        if (current == null) { hide(); return; }
        String query = normalized(current.query());
        List<Agent> matches = agents.stream()
                .filter(agent -> normalized(agent.name).contains(query)).toList();
        // Once a full mention is followed by ordinary text, the menu is finished.
        boolean completed = agents.stream().anyMatch(agent -> {
            String name = normalized(agent.name);
            return query.length() > name.length() && query.startsWith(name)
                    && !Character.isLetterOrDigit(query.charAt(name.length()))
                    && query.charAt(name.length()) != '_';
        });
        if (completed && matches.isEmpty()) { hide(); return; }

        mention = current;
        results.getItems().setAll(matches);
        boolean found = !matches.isEmpty();
        results.setVisible(found);
        results.setManaged(found);
        empty.setVisible(!found);
        empty.setManaged(!found);
        if (found) results.getSelectionModel().selectFirst();
        double height = found ? 55 + Math.min(matches.size(), 6) * 46 : 73;
        results.setPrefHeight(Math.min(matches.size(), 6) * 46 + 8);
        panel.setPrefHeight(height);
        Bounds bounds = input.localToScreen(input.getBoundsInLocal());
        if (bounds == null) { hide(); return; }
        double x = bounds.getMinX();
        double y = bounds.getMinY() - height - 10;
        if (popup.isShowing()) {
            popup.setAnchorX(x);
            popup.setAnchorY(y);
        } else popup.show(input, x, y);
    }

    public boolean handleKey(KeyEvent event) {
        if (!popup.isShowing()) return false;
        KeyCode key = event.getCode();
        if (key == KeyCode.DOWN) {
            results.getSelectionModel().selectNext();
            results.scrollTo(results.getSelectionModel().getSelectedIndex());
        } else if (key == KeyCode.UP) {
            results.getSelectionModel().selectPrevious();
            results.scrollTo(results.getSelectionModel().getSelectedIndex());
        } else if (key == KeyCode.ENTER || key == KeyCode.TAB) {
            Agent selected = results.getSelectionModel().getSelectedItem();
            if (selected == null) hide();
            else choose(selected);
        } else if (key == KeyCode.ESCAPE) hide();
        else return false;
        event.consume();
        return true;
    }

    private void choose(Agent agent) {
        Mention active = mention;
        if (active == null) return;
        String text = input.getText();
        if (active.start() < 0 || active.caret() > text.length()) { hide(); return; }
        String suffix = text.substring(active.caret());
        String insertion = "@" + agent.name + (suffix.startsWith(" ") ? "" : " ");
        input.replaceText(active.start(), active.caret(), insertion);
        input.positionCaret(active.start() + insertion.length());
        onChosen.accept(agent);
        hide();
        input.requestFocus();
    }

    private void hide() {
        mention = null;
        popup.hide();
    }

    private static Mention mentionAt(String text, int caret) {
        int end = Math.min(Math.max(caret, 0), text.length());
        int at = text.lastIndexOf('@', end - 1);
        if (at < 0) return null;
        if (at > 0) {
            char before = text.charAt(at - 1);
            if (Character.isLetterOrDigit(before) || before == '_') return null;
        }
        String query = text.substring(at + 1, end);
        if (query.startsWith(" ") || query.indexOf('@') >= 0) return null;
        return new Mention(at, end, query);
    }

    private static String normalized(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
