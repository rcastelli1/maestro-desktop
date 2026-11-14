package br.com.agenteslocal.window;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Renders a small, bundled subset of Lucide's SVG geometry as JavaFX shapes. */
public final class UiIcons {
    private static final JsonObject ICONS = load();

    private UiIcons() {}

    public static Node icon(String name, double size) {
        JsonArray parts = ICONS.getAsJsonArray(name);
        if (parts == null) throw new IllegalArgumentException("Unknown icon: " + name);
        Group drawing = new Group();
        for (var part : parts) {
            JsonArray entry = part.getAsJsonArray();
            String kind = entry.get(0).getAsString();
            JsonObject attributes = entry.get(1).getAsJsonObject();
            Shape shape = switch (kind) {
                case "path" -> {
                    SVGPath path = new SVGPath();
                    path.setContent(attributes.get("d").getAsString());
                    yield path;
                }
                case "circle" -> new Circle(number(attributes, "cx"), number(attributes, "cy"),
                        number(attributes, "r"));
                case "ellipse" -> new Ellipse(number(attributes, "cx"), number(attributes, "cy"),
                        number(attributes, "rx"), number(attributes, "ry"));
                case "rect" -> {
                    Rectangle rect = new Rectangle(number(attributes, "x"), number(attributes, "y"),
                            number(attributes, "width"), number(attributes, "height"));
                    double radius = number(attributes, "rx");
                    rect.setArcWidth(radius * 2);
                    rect.setArcHeight(radius * 2);
                    yield rect;
                }
                default -> throw new IllegalArgumentException("Unsupported icon shape: " + kind);
            };
            shape.setFill(Color.TRANSPARENT);
            shape.setStroke(Color.web("#aaa9a7"));
            shape.setStrokeWidth(1.9);
            shape.setStrokeLineCap(StrokeLineCap.ROUND);
            shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
            shape.getStyleClass().add("ui-icon-shape");
            drawing.getChildren().add(shape);
        }
        drawing.getTransforms().add(new Scale(size / 24, size / 24, 0, 0));
        Pane container = new Pane(drawing);
        container.setMinSize(size, size);
        container.setPrefSize(size, size);
        container.setMaxSize(size, size);
        container.setMouseTransparent(true);
        container.getStyleClass().add("ui-icon");
        return container;
    }

    private static double number(JsonObject attributes, String name) {
        return attributes.has(name) ? attributes.get(name).getAsDouble() : 0;
    }

    private static JsonObject load() {
        try (var input = UiIcons.class.getResourceAsStream("/icons/lucide.json")) {
            if (input == null) throw new IllegalStateException("Lucide icons are missing");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load Lucide icons", error);
        }
    }
}
