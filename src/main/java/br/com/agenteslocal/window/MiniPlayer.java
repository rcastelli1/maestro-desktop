package br.com.agenteslocal.window;

import br.com.agenteslocal.character.AgentCharacterView;
import br.com.agenteslocal.model.AgentAnimations;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The supplied 520 × 92 capsule, adapted to show real Maestro tasks. */
public final class MiniPlayer {
    public record Entry(String agentId, String visualId, String color, String eyeColor,
                        AgentAnimations animations, String name, String activity,
                        String taskId, boolean running, boolean resumable) { }

    private final Stage window = new Stage(StageStyle.TRANSPARENT);
    private final HBox capsule = new HBox(16);
    private final StackPane portrait = new StackPane();
    private final StackPane info = new StackPane();
    private final HBox markers = new HBox(4);
    private final HBox statusLine = new HBox(8);
    private final HBox activityLine = new HBox(4);
    private final Circle liveDot = new Circle(3, Color.WHITE);
    private final Label status = new Label();
    private final Label activity = new Label();
    private final HBox typingDots = new HBox(3);
    private final Circle ring = new Circle(28, Color.TRANSPARENT);
    private final Button action = new Button();
    private final Timeline rotation;
    private final Timeline ringPulse;
    private final Timeline livePulse;
    private final List<Timeline> dotMotions = new ArrayList<>();
    private final Supplier<List<Entry>> entries;
    private final Supplier<Boolean> animationsEnabled;
    private final Runnable openMain;
    private final Consumer<Entry> stopTask;
    private final Consumer<Entry> resumeQueue;
    private Entry selected;
    private String shownKey = "";
    private Boolean shownActionRunning;
    private int selectedIndex;
    private int markerCount;
    private boolean positioned;
    private boolean ready;
    private boolean requestedVisible;
    private double dragX;
    private double dragY;

    public MiniPlayer(Stage main, Supplier<List<Entry>> entries, Supplier<Boolean> animationsEnabled,
                      Runnable openMain, Consumer<Entry> stopTask, Consumer<Entry> resumeQueue,
                      String stylesheet) {
        this.entries = entries;
        this.animationsEnabled = animationsEnabled;
        this.openMain = openMain;
        this.stopTask = stopTask;
        this.resumeQueue = resumeQueue;
        window.setTitle("maestro-mini-player");
        window.setAlwaysOnTop(true);
        window.setResizable(false);
        // Transparent space around the pill keeps the reference's soft drop shadow visible.
        window.setWidth(560);
        window.setHeight(132);

        portrait.setMinSize(76, 76);
        portrait.setPrefSize(76, 76);
        portrait.setMaxSize(76, 76);

        status.getStyleClass().add("mini-status");
        status.setTextOverrun(OverrunStyle.ELLIPSIS);
        status.setMaxWidth(235);
        activity.getStyleClass().add("mini-activity");
        activity.setTextOverrun(OverrunStyle.ELLIPSIS);
        activity.setMinWidth(0);
        activity.setMaxWidth(265);
        statusLine.getChildren().addAll(liveDot, status);
        statusLine.setAlignment(Pos.CENTER_LEFT);
        activityLine.getChildren().addAll(activity, typingDots);
        activityLine.setAlignment(Pos.CENTER_LEFT);
        VBox lines = new VBox(6, statusLine, activityLine);
        lines.setAlignment(Pos.CENTER_LEFT);
        lines.setMaxWidth(Double.MAX_VALUE);
        info.getChildren().addAll(lines, markers);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        markers.setMinWidth(Region.USE_PREF_SIZE);
        markers.setMaxWidth(Region.USE_PREF_SIZE);
        markers.setAlignment(Pos.CENTER_RIGHT);
        StackPane.setAlignment(markers, Pos.TOP_RIGHT);
        StackPane.setMargin(markers, new Insets(7, 0, 0, 0));

        for (int i = 0; i < 3; i++) {
            Circle dot = new Circle(2, Color.web("#A8A8A8"));
            typingDots.getChildren().add(dot);
            Timeline motion = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(dot.translateYProperty(), 0),
                            new KeyValue(dot.opacityProperty(), .4)),
                    new KeyFrame(Duration.millis(360), eased(dot.translateYProperty(), -3),
                            eased(dot.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(720), eased(dot.translateYProperty(), 0),
                            eased(dot.opacityProperty(), .4)),
                    new KeyFrame(Duration.millis(1200), new KeyValue(dot.translateYProperty(), 0),
                            new KeyValue(dot.opacityProperty(), .4)));
            motion.setCycleCount(Animation.INDEFINITE);
            dotMotions.add(motion);
        }

        ring.setStroke(Color.WHITE);
        ring.setStrokeWidth(1.5);
        ring.setMouseTransparent(true);
        ringPulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(ring.scaleXProperty(), 1),
                        new KeyValue(ring.scaleYProperty(), 1), new KeyValue(ring.opacityProperty(), .32)),
                new KeyFrame(Duration.seconds(1.26), eased(ring.scaleXProperty(), 1.43),
                        eased(ring.scaleYProperty(), 1.43), eased(ring.opacityProperty(), 0)),
                new KeyFrame(Duration.seconds(1.8), new KeyValue(ring.scaleXProperty(), 1.43),
                        new KeyValue(ring.scaleYProperty(), 1.43), new KeyValue(ring.opacityProperty(), 0)));
        ringPulse.setCycleCount(Animation.INDEFINITE);
        livePulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(liveDot.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(800), eased(liveDot.opacityProperty(), .35)),
                new KeyFrame(Duration.millis(1600), eased(liveDot.opacityProperty(), 1)));
        livePulse.setCycleCount(Animation.INDEFINITE);

        action.getStyleClass().add("mini-action");
        action.setMinSize(56, 56);
        action.setPrefSize(56, 56);
        action.setMaxSize(56, 56);
        action.setFocusTraversable(false);
        action.setOnAction(event -> {
            if (selected != null && selected.running() && !selected.taskId().isBlank()) stopTask.accept(selected);
            else if (selected != null && selected.resumable()) resumeQueue.accept(selected);
            else openMain.run();
        });
        action.setOnMouseEntered(event -> scaleButton(1.07));
        action.setOnMouseExited(event -> scaleButton(1));
        action.setOnMousePressed(event -> scaleButton(.93));
        action.setOnMouseReleased(event -> scaleButton(action.isHover() ? 1.07 : 1));
        StackPane actionWrap = new StackPane(ring, action);
        actionWrap.setMinSize(56, 56);
        actionWrap.setPrefSize(56, 56);
        actionWrap.setMaxSize(56, 56);

        capsule.getChildren().addAll(portrait, info, actionWrap);
        capsule.setAlignment(Pos.CENTER_LEFT);
        capsule.setPadding(new Insets(0, 18, 0, 14));
        capsule.setMinSize(520, 92);
        capsule.setPrefSize(520, 92);
        capsule.setMaxSize(520, 92);
        capsule.getStyleClass().add("mini-capsule");
        capsule.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !action.isHover()) openMain.run();
        });
        capsule.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            dragX = event.getScreenX() - window.getX();
            dragY = event.getScreenY() - window.getY();
        });
        capsule.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (action.isHover()) return;
            window.setX(event.getScreenX() - dragX);
            window.setY(event.getScreenY() - dragY);
            positioned = true;
        });

        StackPane canvas = new StackPane(capsule);
        canvas.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(canvas, 560, 132, Color.TRANSPARENT);
        scene.getStylesheets().add(stylesheet);
        window.setScene(scene);

        rotation = new Timeline(new KeyFrame(Duration.seconds(4.2), event -> {
            selectedIndex++;
            refresh();
        }));
        rotation.setCycleCount(Animation.INDEFINITE);

        // The invisible warm-up allows the Windows no-activate style to be installed
        // before a browser or another application is in the foreground.
        window.setOpacity(0);
        window.show();
        javafx.application.Platform.runLater(() -> {
            WindowsTitleBar.makeOverlayNonActivating(window.getTitle());
            window.hide();
            window.setOpacity(1);
            ready = true;
            main.requestFocus();
            if (requestedVisible) show(main);
        });
    }

    public void show(Stage main) {
        requestedVisible = true;
        if (!ready) return;
        if (!positioned) {
            Rectangle2D bounds = Screen.getScreensForRectangle(main.getX(), main.getY(),
                    Math.max(1, main.getWidth()), Math.max(1, main.getHeight())).stream()
                    .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
            window.setX(bounds.getMaxX() - window.getWidth() - 4);
            window.setY(bounds.getMaxY() - window.getHeight() - 4);
        }
        refresh();
        if (window.isShowing()) return;
        window.setOpacity(0);
        window.show();
        WindowsTitleBar.makeOverlayNonActivating(window.getTitle());
        window.setOpacity(1);
        if (animationsEnabled.get()) entrance();
        else capsule.setOpacity(1);
        if (selected != null && selected.running() && animationsEnabled.get()) startActiveAnimations();
    }

    public void hide() {
        requestedVisible = false;
        if (!ready) return;
        rotation.stop();
        ringPulse.stop();
        livePulse.stop();
        dotMotions.forEach(Animation::stop);
        if (window.isShowing()) window.hide();
        portrait.getChildren().clear();
        shownKey = "";
    }

    public boolean isShowing() {
        return window.isShowing();
    }

    public void close() {
        hide();
        window.close();
    }

    public void refresh() {
        List<Entry> available = entries.get();
        if (available.isEmpty()) {
            selected = new Entry("", "orbe", "#6c9ed6", "#14171b", new AgentAnimations(),
                    "maestro", "Nenhuma tarefa em andamento", "", false, false);
            selectedIndex = 0;
        } else selected = available.get(Math.floorMod(selectedIndex, available.size()));

        boolean active = selected.running();
        status.setText(active ? "AGENTE " + String.format(Locale.ROOT, "%02d", Math.floorMod(selectedIndex, available.size()) + 1)
                + " · " + selected.name().toUpperCase(Locale.ROOT)
                : selected.resumable() ? "PAUSADO · " + available.size() + " AGENTES"
                : "EM ESPERA · " + available.size() + " AGENTES");
        status.setTooltip(new Tooltip(selected.name()));
        activity.setText(active ? selected.activity() : selected.resumable() ? "Toque para retomar" : "Toque para abrir o Maestro");
        typingDots.setManaged(active);
        typingDots.setVisible(active);
        liveDot.setFill(active ? Color.WHITE : Color.web("#6E6E6E"));
        if (shownActionRunning == null || shownActionRunning != active) {
            action.setGraphic(active ? stopGraphic() : playGraphic());
            shownActionRunning = active;
        }
        action.setTooltip(new Tooltip(active ? "Parar a tarefa e pausar a fila"
                : selected.resumable() ? "Retomar a fila do agente" : "Abrir Maestro"));
        ring.setVisible(active);
        updateMarkers(available.size(), active);

        String key = selected.agentId() + ":" + selected.visualId() + ":" + selected.color() + ":" + selected.eyeColor()
                + ":" + active + ":" + animationsEnabled.get() + ":" + selected.animations().floatMotion
                + selected.animations().blink + selected.animations().pulse
                + selected.animations().wave + selected.animations().sneeze
                + selected.animations().sleep + selected.animations().read
                + selected.animations().computer;
        if (!shownKey.equals(key)) {
            shownKey = key;
            portrait.getChildren().setAll(new AgentCharacterView(selected.visualId(), 76,
                    animationsEnabled.get(), !active, selected.color(), selected.eyeColor(), active,
                    selected.animations()));
            if (window.isShowing() && animationsEnabled.get()) animatePortraitAndText();
        }
        if (window.isShowing()) {
            if (active && animationsEnabled.get()) startActiveAnimations();
            else stopActiveAnimations();
        }
    }

    private void updateMarkers(int count, boolean active) {
        int visible = Math.min(count, 10);
        if (visible != markerCount) {
            markers.getChildren().clear();
            for (int i = 0; i < visible; i++) {
                Rectangle marker = new Rectangle(i == Math.floorMod(selectedIndex, Math.max(1, visible)) && active ? 14 : 4, 4,
                        i == Math.floorMod(selectedIndex, Math.max(1, visible)) && active ? Color.WHITE : Color.web("#444444"));
                marker.setArcWidth(4);
                marker.setArcHeight(4);
                markers.getChildren().add(marker);
            }
            markerCount = visible;
            return;
        }
        for (int i = 0; i < visible; i++) {
            Rectangle marker = (Rectangle) markers.getChildren().get(i);
            boolean selectedMarker = i == Math.floorMod(selectedIndex, Math.max(1, visible)) && active;
            double width = selectedMarker ? 14 : 4;
            Color color = selectedMarker ? Color.WHITE : Color.web("#444444");
            if (Math.abs(marker.getWidth() - width) < .01 && color.equals(marker.getFill())) continue;
            if (animationsEnabled.get() && window.isShowing()) {
                new Timeline(new KeyFrame(Duration.millis(400), eased(marker.widthProperty(), width),
                        new KeyValue(marker.fillProperty(), color, Interpolator.EASE_BOTH))).play();
            } else {
                marker.setWidth(width);
                marker.setFill(color);
            }
        }
    }

    private void startActiveAnimations() {
        if (rotation.getStatus() != Animation.Status.RUNNING) rotation.play();
        if (ringPulse.getStatus() != Animation.Status.RUNNING) ringPulse.play();
        if (livePulse.getStatus() != Animation.Status.RUNNING) livePulse.play();
        for (int i = 0; i < dotMotions.size(); i++) {
            Timeline motion = dotMotions.get(i);
            if (motion.getStatus() != Animation.Status.RUNNING) {
                motion.play();
                motion.jumpTo(Duration.millis(i * 150L));
            }
        }
    }

    private void stopActiveAnimations() {
        rotation.stop();
        ringPulse.stop();
        livePulse.stop();
        dotMotions.forEach(Animation::stop);
    }

    private void entrance() {
        capsule.setOpacity(0);
        capsule.setTranslateY(28);
        capsule.setScaleX(.92);
        capsule.setScaleY(.92);
        Timeline entrance = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(capsule.opacityProperty(), 0),
                        new KeyValue(capsule.translateYProperty(), 28),
                        new KeyValue(capsule.scaleXProperty(), .92), new KeyValue(capsule.scaleYProperty(), .92)),
                new KeyFrame(Duration.millis(560), eased(capsule.opacityProperty(), 1),
                        eased(capsule.translateYProperty(), -2),
                        eased(capsule.scaleXProperty(), 1.015), eased(capsule.scaleYProperty(), 1.015)),
                new KeyFrame(Duration.millis(700), eased(capsule.translateYProperty(), 0),
                        eased(capsule.scaleXProperty(), 1), eased(capsule.scaleYProperty(), 1)));
        entrance.play();
        animatePortraitAndText();
    }

    private void animatePortraitAndText() {
        portrait.setOpacity(0);
        portrait.setScaleX(.4);
        portrait.setScaleY(.4);
        portrait.setRotate(-14);
        Timeline pop = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(portrait.opacityProperty(), 0),
                        new KeyValue(portrait.scaleXProperty(), .4), new KeyValue(portrait.scaleYProperty(), .4),
                        new KeyValue(portrait.rotateProperty(), -14)),
                new KeyFrame(Duration.millis(490), eased(portrait.opacityProperty(), 1),
                        eased(portrait.scaleXProperty(), 1.06), eased(portrait.scaleYProperty(), 1.06),
                        eased(portrait.rotateProperty(), 2)),
                new KeyFrame(Duration.millis(600), eased(portrait.scaleXProperty(), 1),
                        eased(portrait.scaleYProperty(), 1), eased(portrait.rotateProperty(), 0)));
        pop.play();
        info.setOpacity(0);
        info.setTranslateY(8);
        new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(info.opacityProperty(), 0),
                new KeyValue(info.translateYProperty(), 8)),
                new KeyFrame(Duration.millis(500), eased(info.opacityProperty(), 1),
                        eased(info.translateYProperty(), 0))).play();
    }

    private void scaleButton(double value) {
        if (!animationsEnabled.get()) return;
        ScaleTransition change = new ScaleTransition(Duration.millis(150), action);
        change.setToX(value);
        change.setToY(value);
        change.setInterpolator(Interpolator.EASE_BOTH);
        change.play();
    }

    private static Rectangle stopGraphic() {
        Rectangle square = new Rectangle(20, 20, Color.BLACK);
        square.setArcWidth(10);
        square.setArcHeight(10);
        return square;
    }

    private static SVGPath playGraphic() {
        SVGPath triangle = new SVGPath();
        triangle.setContent("M8 5 L20 12 L8 19 Z");
        triangle.setFill(Color.BLACK);
        triangle.setStroke(Color.BLACK);
        triangle.setStrokeWidth(2);
        triangle.setTranslateX(2);
        return triangle;
    }

    private static KeyValue eased(javafx.beans.value.WritableValue<Number> property, double value) {
        return new KeyValue(property, value, Interpolator.EASE_BOTH);
    }
}
