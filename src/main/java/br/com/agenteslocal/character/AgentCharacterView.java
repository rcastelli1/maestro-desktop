package br.com.agenteslocal.character;

import br.com.agenteslocal.model.AgentAnimations;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public final class AgentCharacterView extends StackPane {
    public record Design(String id, String name, String outline) { }
    private record EyePlacement(double leftX, double rightX, double y, double width, double height) { }
    private record ReadingProps(Group glasses, Group book, Rotate pageTurn, Rotate bookRock) { }
    private record TypingHand(Group group, Line arm, Circle finger) { }
    private record ComputerProps(Group scene, TypingHand leftHand, TypingHand rightHand,
                                 Line cupArm, Group cup, Group steam,
                                 Rotate cupTilt, Rectangle cursor) { }

    public static final List<Design> DESIGNS = List.of(
            new Design("orbe", "Orbe", ""),
            new Design("gota", "Gota", ""),
            new Design("cubo", "Cubo", ""),
            new Design("astro", "Astro", "M50 20 L58 39 L80 40 L64 55 L70 79 L50 67 L30 79 L36 55 L20 40 L42 39 Z"),
            new Design("nuvem", "Nuvem", "M25 76 C13 76 11 58 23 54 C20 39 33 31 45 37 C57 23 75 33 75 48 C89 53 86 76 73 76 Z"),
            new Design("prisma", "Prisma", "M50 24 L78 73 Q81 81 73 82 L27 82 Q19 81 22 73 Z"),
            new Design("pingo", "Pingo", "M50 19 C61 36 73 48 73 64 C73 77 63 84 50 84 C37 84 27 77 27 64 C27 48 39 36 50 19 Z"),
            new Design("folha", "Folha", "M24 73 C23 42 40 27 76 27 C78 62 64 81 33 82 Q24 82 24 73 Z"),
            new Design("faísca", "Faísca", "M55 20 L31 55 L45 55 L39 82 L72 46 L56 46 Z"),
            new Design("hexa", "Hexa", "M34 29 L66 29 L78 55 L66 82 L34 82 L22 55 Z"));

    public static String normalized(String id) {
        return DESIGNS.stream().anyMatch(design -> design.id().equals(id)) ? id : "orbe";
    }

    private static EyePlacement eyesFor(String id) {
        return switch (id) {
            case "gota" -> new EyePlacement(41, 59, 64, 8.5, 15);
            case "cubo", "hexa" -> new EyePlacement(40, 60, 58, 8.5, 15);
            case "astro" -> new EyePlacement(44, 56, 53, 7, 13);
            case "nuvem" -> new EyePlacement(40, 60, 56, 8.5, 15);
            case "prisma" -> new EyePlacement(43, 57, 61, 7, 13);
            case "pingo" -> new EyePlacement(42, 58, 62, 8, 14);
            case "folha" -> new EyePlacement(43, 61, 56, 8, 14);
            case "faísca" -> new EyePlacement(43, 57, 55, 7, 12);
            default -> new EyePlacement(40, 60, 52, 8.5, 15);
        };
    }

    private final List<Timeline> animations = new ArrayList<>();

    public AgentCharacterView(String visualId, double size, boolean animate) {
        this(visualId, size, animate, false, null, null);
    }

    public AgentCharacterView(String visualId, double size, boolean animate, boolean paused) {
        this(visualId, size, animate, paused, null, null);
    }

    public AgentCharacterView(String visualId, double size, boolean animate, boolean paused, String agentColor) {
        this(visualId, size, animate, paused, agentColor, null);
    }

    public AgentCharacterView(String visualId, double size, boolean animate, boolean paused,
                              String agentColor, String agentEyeColor) {
        this(visualId, size, animate, paused, agentColor, agentEyeColor, false);
    }

    public AgentCharacterView(String visualId, double size, boolean animate, boolean paused,
                              String agentColor, String agentEyeColor, boolean working) {
        this(visualId, size, animate, paused, agentColor, agentEyeColor, working, new AgentAnimations());
    }

    public AgentCharacterView(String visualId, double size, boolean animate, boolean paused,
                              String agentColor, String agentEyeColor, boolean working,
                              AgentAnimations choices) {
        AgentAnimations enabled = choices == null ? new AgentAnimations() : choices;
        String id = normalized(visualId);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setAlignment(Pos.CENTER);
        setPickOnBounds(false);

        Pane artboard = new Pane();
        artboard.setMinSize(100, 100);
        artboard.setPrefSize(100, 100);
        artboard.setMaxSize(100, 100);
        artboard.setScaleX(size / 100.0);
        artboard.setScaleY(size / 100.0);
        getChildren().add(artboard);

        Color white = Color.web("#FFFFFF");
        Color paper = Color.web("#F2F2F2");
        Color charcoal = Color.web("#0A0A0A");
        Color tint = tint(agentColor);
        Color bodyColor = tint == null ? (id.equals("gota") ? Color.web("#CFCFCF") : paper) : tint;
        Color detailColor = tint == null ? paper : tint.interpolate(white, .25);
        Color eyeColor = agentEyeColor == null || agentEyeColor.isBlank()
                ? tint == null && (id.equals("cubo") || id.equals("hexa")) ? paper : Color.web("#14171b")
                : Color.web(agentEyeColor);
        EyePlacement eyePlacement = eyesFor(id);
        double eyeY = eyePlacement.y();
        double leftEyeX = eyePlacement.leftX();
        double rightEyeX = eyePlacement.rightX();

        Ellipse shadow = new Ellipse(50, 90, 20, 3.5);
        shadow.setFill(Color.rgb(255, 255, 255, 0.14));
        Group body = new Group();
        Rotate bodyTilt = new Rotate(0, 50, 56);
        Scale bodyScale = new Scale(1, 1, 50, 83);
        Scale breathing = new Scale(1, 1, 50, 58);
        body.getTransforms().addAll(bodyTilt, bodyScale, breathing);
        artboard.getChildren().addAll(shadow, body);

        Circle live = null;
        if (id.equals("orbe")) {
            Line antenna = new Line(50, 30, 50, 19);
            antenna.setStroke(detailColor);
            antenna.setStrokeWidth(3);
            antenna.setStrokeLineCap(StrokeLineCap.ROUND);
            live = new Circle(50, 16, 4, tint == null ? white : detailColor);
            body.getChildren().addAll(antenna, live);
        } else if (id.equals("cubo")) {
            body.getChildren().addAll(roundRect(38, 22, 6, 14, 3, detailColor),
                    roundRect(56, 22, 6, 14, 3, detailColor));
        }

        double armY = id.equals("gota") ? 58 : id.equals("cubo") ? 55 : 53;
        double leftX = id.equals("orbe") ? 10 : 9;
        double rightX = id.equals("orbe") ? 73 : 74;
        Rectangle leftArm = roundRect(leftX, armY, 17, 9, 4.5, bodyColor);
        Rectangle rightArm = roundRect(rightX, armY, 17, 9, 4.5, bodyColor);
        leftArm.setOpacity(0);
        rightArm.setOpacity(0);
        leftArm.setTranslateX(30);
        rightArm.setTranslateX(-30);
        Rotate leftTurn = new Rotate(0, leftX + 17, armY + 4.5);
        Rotate rightTurn = new Rotate(id.equals("gota") ? -72 : 0, rightX, armY + 4.5);
        leftArm.getTransforms().add(leftTurn);
        rightArm.getTransforms().add(rightTurn);
        body.getChildren().addAll(leftArm, rightArm);

        if (id.equals("orbe")) {
            body.getChildren().add(new Circle(50, 56, 27, bodyColor));
        } else if (id.equals("gota")) {
            SVGPath drop = path("M50 22 C58 36 77 50 77 68 A27 27 0 0 1 23 68 C23 50 42 36 50 22 Z", bodyColor);
            SVGPath shine = path("M33 62 C33 56 36 51 40 47", Color.TRANSPARENT);
            shine.setStroke(Color.rgb(255, 255, 255, 0.55));
            shine.setStrokeWidth(3);
            shine.setStrokeLineCap(StrokeLineCap.ROUND);
            body.getChildren().addAll(drop, shine);
        } else if (id.equals("cubo")) {
            Rectangle cube = roundRect(23, 33, 54, 52, 20,
                    tint == null ? Color.web("#161616") : bodyColor);
            cube.setStroke(detailColor);
            cube.setStrokeWidth(3);
            body.getChildren().add(cube);
        } else {
            SVGPath silhouette = path(DESIGNS.stream().filter(design -> design.id().equals(id))
                    .findFirst().orElse(DESIGNS.get(3)).outline(),
                    id.equals("hexa") && tint == null ? Color.web("#161616") : bodyColor);
            if (id.equals("hexa")) {
                silhouette.setStroke(detailColor);
                silhouette.setStrokeWidth(3);
            }
            body.getChildren().add(silhouette);
            if (id.equals("faísca")) {
                // A small central face joins the two narrow sides of the lightning bolt.
                Ellipse face = new Ellipse(50, 55, 18, 12);
                face.setFill(bodyColor);
                body.getChildren().add(face);
            }
        }

        Group eyes = new Group();
        Group leftEye = eye(leftEyeX, eyeY, eyePlacement.width(), eyePlacement.height(), eyeColor);
        Group rightEye = eye(rightEyeX, eyeY, eyePlacement.width(), eyePlacement.height(), eyeColor);
        Scale leftBlink = new Scale(1, 1, leftEyeX, eyeY);
        Scale rightBlink = new Scale(1, 1, rightEyeX, eyeY);
        leftEye.getTransforms().add(leftBlink);
        rightEye.getTransforms().add(rightBlink);
        eyes.getChildren().addAll(leftEye, rightEye);
        Scale eyeClosure = new Scale(1, 1, 50, eyeY);
        eyes.getTransforms().add(eyeClosure);
        Group sleepGaze = new Group(eyes);
        body.getChildren().add(sleepGaze);

        Circle sleepBubble = new Circle(69, eyeY + 9, 5, Color.rgb(255, 255, 255, 0.82));
        sleepBubble.setStroke(Color.web("#BDBDBD"));
        sleepBubble.setStrokeWidth(1);
        sleepBubble.setOpacity(0);
        sleepBubble.setScaleX(0);
        sleepBubble.setScaleY(0);
        body.getChildren().add(sleepBubble);
        Group bubbleBurst = bubbleBurst(69, eyeY + 9);
        body.getChildren().add(bubbleBurst);
        Group sneeze = new Group(new Circle(77, eyeY - 7, 1.7, detailColor),
                new Circle(83, eyeY - 2, 1.2, detailColor), new Circle(78, eyeY + 3, 1, detailColor));
        sneeze.setOpacity(0);
        artboard.getChildren().add(sneeze);

        ReadingProps reading = null;
        if (working && enabled.read) {
            reading = readingProps(eyePlacement, bodyColor);
            body.getChildren().addAll(reading.glasses(), reading.book());
            if (animate) {
                reading.glasses().setOpacity(0);
                reading.book().setOpacity(0);
                reading.book().setTranslateY(6);
            } else {
                leftArm.setOpacity(1);
                rightArm.setOpacity(1);
                leftArm.setTranslateX(5);
                rightArm.setTranslateX(-5);
                eyes.setTranslateY(2.5);
            }
        }

        ComputerProps computer = null;
        if (working && enabled.computer) {
            computer = computerProps(bodyColor);
            if (!animate && reading == null) {
                computer.scene().setOpacity(1);
                computer.leftHand().group().setOpacity(1);
                computer.rightHand().group().setOpacity(1);
                eyes.setTranslateY(3);
            } else computer.scene().setOpacity(0);
            body.getChildren().add(computer.scene());
        }

        if (paused && enabled.sleep) {
            eyeClosure.setY(.1);
            sleepBubble.setScaleX(.4);
            sleepBubble.setScaleY(.4);
            sleepBubble.setOpacity(.65);
            if (live != null) live.setOpacity(.4);
        }
        if (!animate) return;
        if (enabled.floatMotion) animations.add(bobbing(body, bodyScale, shadow));
        if (paused && enabled.sleep) {
            animations.add(sleeping(sleepBubble, bubbleBurst, eyeClosure, sleepGaze,
                    body, bodyTilt, breathing));
        } else {
            if (enabled.blink) animations.add(blink(leftBlink, rightBlink));
            if (live != null && enabled.pulse) animations.add(pulse(live));
            if (enabled.wave || enabled.sneeze || (!working && enabled.sleep)
                    || reading != null || computer != null)
                animations.add(gestures(id, leftArm, rightArm, leftTurn, rightTurn,
                        body, bodyTilt, breathing, eyes, eyeClosure, sleepGaze,
                        sleepBubble, bubbleBurst, sneeze, reading, computer, working, enabled));
        }
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) animations.forEach(Animation::stop);
            else {
                for (Timeline animation : animations) animation.play();
                // Match the staggered phases of the three original SVG characters.
                int phase = id.equals("gota") ? 1 : id.equals("cubo") ? 2 : 0;
                if (enabled.floatMotion) animations.getFirst().jumpTo(Duration.seconds(phase * 0.8));
            }
        });
    }

    private static Group eye(double x, double y, double width, double height, Color color) {
        Rectangle eye = roundRect(x - width / 2, y - height / 2,
                width, height, width / 2, color);
        return new Group(eye);
    }

    private static ReadingProps readingProps(EyePlacement placement, Color bodyColor) {
        Color frame = bodyColor.getBrightness() < .52
                ? Color.web("#f0e8d8") : Color.web("#252a30");
        double gap = placement.rightX() - placement.leftX();
        double lensWidth = Math.min(placement.width() + 6, gap - 2);
        double lensHeight = placement.height() + 4;
        Group glasses = new Group();
        for (double center : new double[] {placement.leftX(), placement.rightX()}) {
            Rectangle lens = roundRect(center - lensWidth / 2, placement.y() - lensHeight / 2,
                    lensWidth, lensHeight, 5, Color.TRANSPARENT);
            lens.setStroke(frame);
            lens.setStrokeWidth(1.8);
            glasses.getChildren().add(lens);
        }
        Line bridge = new Line(placement.leftX() + lensWidth / 2, placement.y() - 1,
                placement.rightX() - lensWidth / 2, placement.y() - 1);
        bridge.setStroke(frame);
        bridge.setStrokeWidth(1.8);
        bridge.setStrokeLineCap(StrokeLineCap.ROUND);
        glasses.getChildren().add(bridge);

        int top = (int) Math.max(68, Math.min(72, placement.y() + 11));
        Group book = new Group();
        SVGPath cover = path("M23 " + (top - 2) + " L49 " + (top + 1) + " L50 " + (top + 3)
                + " L51 " + (top + 1) + " L77 " + (top - 2) + " L77 " + (top + 18)
                + " L51 " + (top + 21) + " L50 " + (top + 22) + " L49 " + (top + 21)
                + " L23 " + (top + 18) + " Z", Color.web("#222a34"));
        SVGPath leftPage = path("M25 " + top + " Q37 " + (top - 2) + " 49 " + (top + 2)
                + " L49 " + (top + 18) + " Q37 " + (top + 14) + " 25 " + (top + 17)
                + " Z", Color.web("#eee4cf"));
        SVGPath rightPage = path("M51 " + (top + 2) + " Q63 " + (top - 2) + " 75 " + top
                + " L75 " + (top + 17) + " Q63 " + (top + 14) + " 51 " + (top + 18)
                + " Z", Color.web("#fbf4e5"));
        book.getChildren().addAll(cover, leftPage, rightPage);
        Color ink = Color.web("#b2a995");
        for (int index = 0; index < 2; index++) {
            Line leftText = new Line(30, top + 5 + index * 4, 43, top + 6 + index * 4);
            Line rightText = new Line(57, top + 6 + index * 4, 70, top + 5 + index * 4);
            leftText.setStroke(ink);
            rightText.setStroke(ink);
            leftText.setStrokeWidth(1);
            rightText.setStrokeWidth(1);
            book.getChildren().addAll(leftText, rightText);
        }
        Line spine = new Line(50, top + 3, 50, top + 19);
        spine.setStroke(Color.web("#b7a887"));
        spine.setStrokeWidth(1.3);
        Group corner = new Group(path("M64 " + (top + 1) + " L74 " + top
                + " L74 " + (top + 10) + " Q70 " + (top + 4) + " 64 " + (top + 1)
                + " Z", Color.web("#d8c9aa")));
        Rotate pageTurn = new Rotate(0, 74, top + 1);
        corner.getTransforms().add(pageTurn);
        book.getChildren().addAll(spine, corner);
        Rotate bookRock = new Rotate(0, 50, top + 14);
        book.getTransforms().add(bookRock);
        return new ReadingProps(glasses, book, pageTurn, bookRock);
    }

    private static ComputerProps computerProps(Color bodyColor) {
        Color bezel = Color.web("#a7b4c2");
        Color screenColor = Color.web("#132333");
        Color glow = Color.web("#7cc6d8");
        Group scene = new Group();

        Rectangle monitor = roundRect(23, 67, 47, 18, 3, Color.web("#273643"));
        monitor.setStroke(bezel);
        monitor.setStrokeWidth(1.8);
        Rectangle display = roundRect(26, 69, 41, 13, 1.5, screenColor);
        Line code1 = new Line(29, 72, 48, 72);
        Line code2 = new Line(32, 75, 56, 75);
        Line code3 = new Line(29, 78, 44, 78);
        for (Line code : List.of(code1, code2, code3)) {
            code.setStroke(glow);
            code.setStrokeWidth(1.3);
            code.setOpacity(.72);
        }
        Rectangle cursor = roundRect(58, 71, 2, 5, .5, Color.web("#d7f3ee"));
        Rectangle stand = roundRect(44, 85, 5, 4, 1, bezel);
        Rectangle base = roundRect(38, 88, 17, 2, 1, bezel);
        Rectangle keyboard = roundRect(27, 90, 45, 6, 2, Color.web("#384755"));
        keyboard.setStroke(bezel);
        keyboard.setStrokeWidth(1);
        for (int column = 0; column < 9; column++) {
            Line key = new Line(30 + column * 4.4, 92, 32 + column * 4.4, 92);
            key.setStroke(Color.web("#c0d0da"));
            key.setStrokeWidth(.9);
            scene.getChildren().add(key);
        }
        // Short forearms emerge below the monitor; the tapping fingertips move,
        // while their upper ends stay fixed instead of sliding across the screen.
        TypingHand leftHand = typingHand(35, 41, bodyColor);
        TypingHand rightHand = typingHand(64, 57, bodyColor);

        Ellipse saucer = new Ellipse(80, 89, 9, 1.7);
        saucer.setFill(Color.web("#9ca9b4"));
        SVGPath cupBody = path("M74 76 L86 76 L84 86 Q80 90 76 86 Z", Color.web("#eee7d9"));
        Ellipse coffee = new Ellipse(80, 76, 6, 1.7);
        coffee.setFill(Color.web("#8b5b3d"));
        SVGPath handle = path("M85 78 Q93 77 91 82 Q90 85 85 84", Color.TRANSPARENT);
        handle.setStroke(Color.web("#eee7d9"));
        handle.setStrokeWidth(2.4);
        handle.setStrokeLineCap(StrokeLineCap.ROUND);
        Group steam = new Group();
        for (double x : new double[] {77, 82}) {
            SVGPath wisp = path("M" + x + " 73 Q" + (x - 3) + " 70 " + x + " 67 Q"
                    + (x + 3) + " 64 " + x + " 61", Color.TRANSPARENT);
            wisp.setStroke(Color.rgb(240, 244, 242, .78));
            wisp.setStrokeWidth(1.3);
            wisp.setStrokeLineCap(StrokeLineCap.ROUND);
            steam.getChildren().add(wisp);
        }
        Group cup = new Group(handle, cupBody, coffee, steam);
        Rotate cupTilt = new Rotate(0, 80, 79);
        cup.getTransforms().add(cupTilt);
        Line cupArm = new Line(68, 72, 78, 80);
        cupArm.setStroke(bodyColor);
        cupArm.setStrokeWidth(6);
        cupArm.setStrokeLineCap(StrokeLineCap.ROUND);
        cupArm.setOpacity(0);

        scene.getChildren().addAll(0, List.of(monitor, display, code1, code2, code3,
                cursor, stand, base, keyboard));
        scene.getChildren().addAll(leftHand.group(), rightHand.group(), saucer, cupArm, cup);
        return new ComputerProps(scene, leftHand, rightHand, cupArm, cup, steam, cupTilt, cursor);
    }

    private static TypingHand typingHand(double startX, double endX, Color color) {
        Line arm = new Line(startX, 84, endX, 89);
        arm.setStroke(color);
        arm.setStrokeWidth(5.5);
        arm.setStrokeLineCap(StrokeLineCap.ROUND);
        Circle finger = new Circle(endX, 89, 3.2, color);
        Group group = new Group(arm, finger);
        group.setOpacity(0);
        return new TypingHand(group, arm, finger);
    }

    private static Group bubbleBurst(double x, double y) {
        Group burst = new Group();
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * index / 4;
            Line ray = new Line(x + Math.cos(angle) * 10, y + Math.sin(angle) * 10,
                    x + Math.cos(angle) * 16, y + Math.sin(angle) * 16);
            ray.setStroke(Color.web("#F5F5F5"));
            ray.setStrokeWidth(2);
            ray.setStrokeLineCap(StrokeLineCap.ROUND);
            burst.getChildren().add(ray);
        }
        burst.setOpacity(0);
        burst.setScaleX(.5);
        burst.setScaleY(.5);
        return burst;
    }

    private static Color tint(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Color.web(value); }
        catch (IllegalArgumentException ignored) { return Color.web("#6c9ed6"); }
    }

    private static Rectangle roundRect(double x, double y, double width, double height, double radius, Color fill) {
        Rectangle rectangle = new Rectangle(x, y, width, height);
        rectangle.setArcWidth(radius * 2);
        rectangle.setArcHeight(radius * 2);
        rectangle.setFill(fill);
        return rectangle;
    }

    private static SVGPath path(String content, Color fill) {
        SVGPath shape = new SVGPath();
        shape.setContent(content);
        shape.setFill(fill);
        return shape;
    }

    private static Timeline bobbing(Group body, Scale bodyScale, Ellipse shadow) {
        Timeline animation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(body.translateYProperty(), 0),
                        new KeyValue(bodyScale.xProperty(), 1), new KeyValue(bodyScale.yProperty(), 1),
                        new KeyValue(shadow.scaleXProperty(), 1), new KeyValue(shadow.opacityProperty(), 1)),
                new KeyFrame(Duration.seconds(1.2),
                        eased(body.translateYProperty(), -4), eased(bodyScale.xProperty(), .97),
                        eased(bodyScale.yProperty(), 1.04), eased(shadow.scaleXProperty(), .84),
                        eased(shadow.opacityProperty(), .6)),
                new KeyFrame(Duration.seconds(2.4),
                        eased(body.translateYProperty(), 0), eased(bodyScale.xProperty(), 1),
                        eased(bodyScale.yProperty(), 1), eased(shadow.scaleXProperty(), 1),
                        eased(shadow.opacityProperty(), 1)));
        animation.setCycleCount(Animation.INDEFINITE);
        return animation;
    }

    private static Timeline blink(Scale left, Scale right) {
        Timeline animation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(left.yProperty(), 1), new KeyValue(right.yProperty(), 1)),
                new KeyFrame(Duration.seconds(3.822), eased(left.yProperty(), 1), eased(right.yProperty(), 1)),
                new KeyFrame(Duration.seconds(3.948), eased(left.yProperty(), .08), eased(right.yProperty(), .08)),
                new KeyFrame(Duration.seconds(4.074), eased(left.yProperty(), 1), eased(right.yProperty(), 1)),
                new KeyFrame(Duration.seconds(4.2), new KeyValue(left.yProperty(), 1), new KeyValue(right.yProperty(), 1)));
        animation.setCycleCount(Animation.INDEFINITE);
        return animation;
    }

    private static Timeline pulse(Circle light) {
        Timeline animation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(light.opacityProperty(), 1)),
                new KeyFrame(Duration.seconds(.8), eased(light.opacityProperty(), .35)),
                new KeyFrame(Duration.seconds(1.6), eased(light.opacityProperty(), 1)));
        animation.setCycleCount(Animation.INDEFINITE);
        return animation;
    }

    private static List<KeyFrame> readingFrames(ReadingProps props, Group eyes,
                                                Rectangle leftArm, Rectangle rightArm) {
        Group book = props.book();
        Group glasses = props.glasses();
        Rotate page = props.pageTurn();
        Rotate rock = props.bookRock();
        return List.of(
                new KeyFrame(Duration.ZERO, new KeyValue(book.opacityProperty(), 0),
                        new KeyValue(book.translateYProperty(), 6),
                        new KeyValue(glasses.opacityProperty(), 0),
                        new KeyValue(page.angleProperty(), 0), new KeyValue(rock.angleProperty(), 0)),
                new KeyFrame(Duration.seconds(.18),
                        new KeyValue(leftArm.opacityProperty(), 1, Interpolator.DISCRETE),
                        new KeyValue(rightArm.opacityProperty(), 1, Interpolator.DISCRETE)),
                new KeyFrame(Duration.seconds(.50), eased(book.opacityProperty(), 1),
                        eased(book.translateYProperty(), 0), eased(glasses.opacityProperty(), 1),
                        eased(leftArm.translateXProperty(), 5), eased(rightArm.translateXProperty(), -5),
                        eased(eyes.translateXProperty(), -2), eased(eyes.translateYProperty(), 2.5)),
                new KeyFrame(Duration.seconds(1.25), eased(eyes.translateXProperty(), 2),
                        eased(rock.angleProperty(), 1)),
                new KeyFrame(Duration.seconds(2.15), eased(eyes.translateXProperty(), -2),
                        eased(rock.angleProperty(), -1)),
                new KeyFrame(Duration.seconds(2.50), eased(page.angleProperty(), -32)),
                new KeyFrame(Duration.seconds(2.85), eased(page.angleProperty(), 0),
                        eased(eyes.translateXProperty(), 2)),
                new KeyFrame(Duration.seconds(3.05), eased(book.opacityProperty(), 1),
                        eased(glasses.opacityProperty(), 1), eased(book.translateYProperty(), 0)),
                new KeyFrame(Duration.seconds(3.50), eased(book.opacityProperty(), 0),
                        eased(book.translateYProperty(), 6), eased(glasses.opacityProperty(), 0),
                        eased(leftArm.translateXProperty(), 30),
                        eased(rightArm.translateXProperty(), -30),
                        eased(eyes.translateXProperty(), 0), eased(eyes.translateYProperty(), 0),
                        eased(rock.angleProperty(), 0)),
                new KeyFrame(Duration.seconds(3.58),
                        new KeyValue(leftArm.opacityProperty(), 0, Interpolator.DISCRETE),
                        new KeyValue(rightArm.opacityProperty(), 0, Interpolator.DISCRETE)));
    }

    private static List<KeyFrame> computerFrames(ComputerProps props, Group eyes, double start) {
        Group scene = props.scene();
        Group left = props.leftHand().group();
        Group right = props.rightHand().group();
        Line leftArm = props.leftHand().arm();
        Line rightArm = props.rightHand().arm();
        Circle leftFinger = props.leftHand().finger();
        Circle rightFinger = props.rightHand().finger();
        Group cup = props.cup();
        Group steam = props.steam();
        Line arm = props.cupArm();
        Rotate tilt = props.cupTilt();
        Rectangle cursor = props.cursor();
        return List.of(
                new KeyFrame(at(0, start), new KeyValue(scene.opacityProperty(), 0),
                        new KeyValue(left.opacityProperty(), 0), new KeyValue(right.opacityProperty(), 0),
                        new KeyValue(leftArm.endYProperty(), 84),
                        new KeyValue(rightArm.endYProperty(), 84),
                        new KeyValue(leftFinger.centerYProperty(), 84),
                        new KeyValue(rightFinger.centerYProperty(), 84),
                        new KeyValue(arm.opacityProperty(), 0),
                        new KeyValue(arm.endXProperty(), 68), new KeyValue(arm.endYProperty(), 72),
                        new KeyValue(cup.translateXProperty(), 0),
                        new KeyValue(cup.translateYProperty(), 0),
                        new KeyValue(tilt.angleProperty(), 0),
                        new KeyValue(steam.opacityProperty(), 0),
                        new KeyValue(steam.translateYProperty(), 0),
                        new KeyValue(cursor.opacityProperty(), 0)),
                new KeyFrame(at(.35, start), eased(scene.opacityProperty(), 1),
                        eased(eyes.translateXProperty(), -2), eased(eyes.translateYProperty(), 3),
                        eased(steam.opacityProperty(), .8), eased(steam.translateYProperty(), -2)),
                new KeyFrame(at(.65, start), eased(left.opacityProperty(), 1),
                        eased(right.opacityProperty(), 1),
                        eased(leftArm.endYProperty(), 89), eased(rightArm.endYProperty(), 89),
                        eased(leftFinger.centerYProperty(), 89), eased(rightFinger.centerYProperty(), 89),
                        eased(cursor.opacityProperty(), 1)),
                new KeyFrame(at(1.05, start), eased(leftArm.endYProperty(), 91),
                        eased(leftFinger.centerYProperty(), 91),
                        eased(rightArm.endYProperty(), 89), eased(rightFinger.centerYProperty(), 89),
                        eased(cursor.opacityProperty(), 0),
                        eased(steam.translateYProperty(), -5), eased(steam.opacityProperty(), .4)),
                new KeyFrame(at(1.45, start), eased(leftArm.endYProperty(), 89),
                        eased(leftFinger.centerYProperty(), 89),
                        eased(rightArm.endYProperty(), 91), eased(rightFinger.centerYProperty(), 91),
                        eased(cursor.opacityProperty(), 1),
                        eased(steam.translateYProperty(), 0), eased(steam.opacityProperty(), .9)),
                new KeyFrame(at(1.85, start), eased(leftArm.endYProperty(), 91),
                        eased(leftFinger.centerYProperty(), 91),
                        eased(rightArm.endYProperty(), 89), eased(rightFinger.centerYProperty(), 89),
                        eased(cursor.opacityProperty(), 0),
                        eased(steam.translateYProperty(), -4), eased(steam.opacityProperty(), .5)),
                new KeyFrame(at(2.25, start), eased(leftArm.endYProperty(), 89),
                        eased(leftFinger.centerYProperty(), 89),
                        eased(rightArm.endYProperty(), 91), eased(rightFinger.centerYProperty(), 91),
                        eased(cursor.opacityProperty(), 1),
                        eased(steam.translateYProperty(), 0), eased(steam.opacityProperty(), .9),
                        eased(eyes.translateXProperty(), -2),
                        eased(arm.opacityProperty(), 0),
                        eased(arm.endXProperty(), 68), eased(arm.endYProperty(), 72)),
                new KeyFrame(at(2.48, start), eased(leftArm.endYProperty(), 84),
                        eased(rightArm.endYProperty(), 84),
                        eased(leftFinger.centerYProperty(), 84),
                        eased(rightFinger.centerYProperty(), 84),
                        eased(left.opacityProperty(), 0), eased(right.opacityProperty(), 0),
                        eased(arm.opacityProperty(), 1),
                        eased(arm.endXProperty(), 78), eased(arm.endYProperty(), 80),
                        eased(eyes.translateXProperty(), 4),
                        eased(cup.translateXProperty(), 0), eased(cup.translateYProperty(), 0)),
                new KeyFrame(at(2.90, start), eased(cup.translateXProperty(), -14),
                        eased(cup.translateYProperty(), -9),
                        eased(arm.endXProperty(), 64), eased(arm.endYProperty(), 72),
                        eased(steam.opacityProperty(), .4)),
                new KeyFrame(at(3.20, start), eased(tilt.angleProperty(), -18),
                        eased(cup.translateYProperty(), -11),
                        eased(arm.endYProperty(), 70),
                        eased(eyes.translateYProperty(), 0)),
                new KeyFrame(at(3.55, start), eased(tilt.angleProperty(), -12),
                        eased(cup.translateYProperty(), -10),
                        eased(steam.opacityProperty(), 0)),
                new KeyFrame(at(4.05, start), eased(tilt.angleProperty(), 0),
                        eased(cup.translateXProperty(), 0), eased(cup.translateYProperty(), 0),
                        eased(arm.endXProperty(), 78), eased(arm.endYProperty(), 80),
                        eased(arm.opacityProperty(), 1),
                        eased(left.opacityProperty(), 0), eased(right.opacityProperty(), 0),
                        eased(leftArm.endYProperty(), 84),
                        eased(rightArm.endYProperty(), 84),
                        eased(leftFinger.centerYProperty(), 84),
                        eased(rightFinger.centerYProperty(), 84),
                        eased(eyes.translateXProperty(), -2), eased(eyes.translateYProperty(), 3)),
                new KeyFrame(at(4.35, start), eased(arm.endXProperty(), 68),
                        eased(arm.endYProperty(), 72), eased(arm.opacityProperty(), 0),
                        eased(steam.opacityProperty(), .9),
                        eased(leftArm.endYProperty(), 89), eased(rightArm.endYProperty(), 89),
                        eased(leftFinger.centerYProperty(), 89), eased(rightFinger.centerYProperty(), 89),
                        eased(left.opacityProperty(), 1), eased(right.opacityProperty(), 1)),
                new KeyFrame(at(4.75, start), eased(leftArm.endYProperty(), 91),
                        eased(leftFinger.centerYProperty(), 91),
                        eased(rightArm.endYProperty(), 89), eased(rightFinger.centerYProperty(), 89),
                        eased(cursor.opacityProperty(), 0),
                        eased(steam.translateYProperty(), -4)),
                new KeyFrame(at(5.10, start), eased(leftArm.endYProperty(), 89),
                        eased(leftFinger.centerYProperty(), 89),
                        eased(rightArm.endYProperty(), 91), eased(rightFinger.centerYProperty(), 91),
                        eased(cursor.opacityProperty(), 1),
                        eased(steam.translateYProperty(), 0)),
                new KeyFrame(at(5.40, start), eased(scene.opacityProperty(), 1),
                        eased(left.opacityProperty(), 1), eased(right.opacityProperty(), 1)),
                new KeyFrame(at(5.75, start), eased(scene.opacityProperty(), 0),
                        eased(left.opacityProperty(), 0), eased(right.opacityProperty(), 0),
                        eased(leftArm.endYProperty(), 84), eased(rightArm.endYProperty(), 84),
                        eased(leftFinger.centerYProperty(), 84),
                        eased(rightFinger.centerYProperty(), 84),
                        eased(eyes.translateXProperty(), 0), eased(eyes.translateYProperty(), 0),
                        eased(cursor.opacityProperty(), 0), eased(steam.opacityProperty(), 0)));
    }

    private static Timeline sleeping(Circle bubble, Group burst, Scale closure, Group gaze,
                                     Group body, Rotate tilt, Scale breathing) {
        Timeline animation = new Timeline();
        animation.getKeyFrames().addAll(sleepFrames(0, true, bubble, burst, closure, gaze,
                body, tilt, breathing));
        animation.setCycleCount(Animation.INDEFINITE);
        return animation;
    }

    private static KeyFrame breathFrame(double second, Circle bubble, Scale breathing,
                                        double bubbleSize, double opacity, double inhale) {
        return new KeyFrame(Duration.seconds(second), eased(bubble.scaleXProperty(), bubbleSize),
                eased(bubble.scaleYProperty(), bubbleSize), eased(bubble.opacityProperty(), opacity),
                eased(breathing.xProperty(), 1 + .012 * inhale),
                eased(breathing.yProperty(), 1 + .027 * inhale));
    }

    private static List<KeyFrame> sleepFrames(double offset, boolean paused, Circle bubble,
                                               Group burst, Scale closure, Group gaze,
                                               Group body, Rotate tilt, Scale breathing) {
        List<KeyFrame> frames = new ArrayList<>();
        frames.add(new KeyFrame(Duration.seconds(offset),
                new KeyValue(closure.yProperty(), paused ? .08 : 1),
                new KeyValue(gaze.translateXProperty(), 0),
                new KeyValue(gaze.translateYProperty(), paused ? 3 : 0),
                new KeyValue(bubble.scaleXProperty(), 0), new KeyValue(bubble.scaleYProperty(), 0),
                new KeyValue(bubble.opacityProperty(), 0),
                new KeyValue(burst.opacityProperty(), 0),
                new KeyValue(burst.scaleXProperty(), .5), new KeyValue(burst.scaleYProperty(), .5),
                new KeyValue(breathing.xProperty(), 1), new KeyValue(breathing.yProperty(), 1),
                new KeyValue(body.translateXProperty(), 0), new KeyValue(tilt.angleProperty(), 0)));
        frames.add(new KeyFrame(Duration.seconds(offset + .25),
                eased(closure.yProperty(), .08), eased(gaze.translateYProperty(), 3)));
        // Five slow breaths over twenty seconds. The body expands with the bubble.
        frames.add(breathFrame(offset + .25, bubble, breathing, .45, .76, 0));
        for (int cycle = 0; cycle < 5; cycle++) {
            frames.add(breathFrame(offset + 1.65 + 3.8 * cycle, bubble, breathing,
                    1.15 + .06 * cycle, .84 + .01 * cycle, 1));
            frames.add(breathFrame(offset + 3.55 + 3.8 * cycle, bubble, breathing,
                    .72 + .06 * cycle, .77, 0));
        }
        frames.add(breathFrame(offset + 20.0, bubble, breathing, 1.55, .9, 1));
        frames.add(new KeyFrame(Duration.seconds(offset + 20.0),
                eased(burst.opacityProperty(), 0), eased(burst.scaleXProperty(), .5),
                eased(burst.scaleYProperty(), .5)));
        frames.add(breathFrame(offset + 20.25, bubble, breathing, 1.82, 0, 0));
        frames.add(new KeyFrame(Duration.seconds(offset + 20.25),
                eased(closure.xProperty(), 1), eased(closure.yProperty(), .08),
                eased(gaze.translateYProperty(), 3),
                eased(body.translateXProperty(), 0), eased(tilt.angleProperty(), 0),
                eased(burst.opacityProperty(), 1), eased(burst.scaleXProperty(), 1.15),
                eased(burst.scaleYProperty(), 1.15)));
        frames.add(new KeyFrame(Duration.seconds(offset + 20.50),
                eased(closure.xProperty(), 1.18), eased(closure.yProperty(), 1.25),
                eased(gaze.translateYProperty(), -2),
                eased(body.translateXProperty(), -3), eased(tilt.angleProperty(), -9),
                eased(burst.opacityProperty(), .45), eased(burst.scaleXProperty(), 1.5),
                eased(burst.scaleYProperty(), 1.5)));
        frames.add(new KeyFrame(Duration.seconds(offset + 20.70),
                eased(closure.xProperty(), 1.1), eased(closure.yProperty(), 1.08),
                eased(body.translateXProperty(), 2),
                eased(tilt.angleProperty(), 6), eased(burst.opacityProperty(), 0),
                eased(burst.scaleXProperty(), 1.7), eased(burst.scaleYProperty(), 1.7)));
        frames.add(new KeyFrame(Duration.seconds(offset + 21.00),
                eased(closure.xProperty(), 1), eased(closure.yProperty(), 1),
                eased(gaze.translateXProperty(), 5),
                eased(body.translateXProperty(), 0), eased(tilt.angleProperty(), 0)));
        frames.add(new KeyFrame(Duration.seconds(offset + 22.00),
                eased(gaze.translateXProperty(), 5)));
        frames.add(new KeyFrame(Duration.seconds(offset + 22.30),
                eased(gaze.translateXProperty(), -5)));
        frames.add(new KeyFrame(Duration.seconds(offset + 23.30),
                eased(gaze.translateXProperty(), -5)));
        frames.add(new KeyFrame(Duration.seconds(offset + 23.60),
                eased(gaze.translateXProperty(), 0)));
        frames.add(new KeyFrame(Duration.seconds(offset + 24.10), eased(closure.yProperty(), .82),
                eased(gaze.translateYProperty(), -.5)));
        frames.add(new KeyFrame(Duration.seconds(offset + 24.60), eased(closure.yProperty(), .52),
                eased(gaze.translateYProperty(), 1)));
        frames.add(new KeyFrame(Duration.seconds(offset + 25.10), eased(closure.yProperty(), .25),
                eased(gaze.translateYProperty(), 2)));
        frames.add(new KeyFrame(Duration.seconds(offset + 25.55), eased(closure.yProperty(), .08),
                eased(gaze.translateYProperty(), 3)));
        frames.add(new KeyFrame(Duration.seconds(offset + 25.85),
                new KeyValue(closure.yProperty(), .08), new KeyValue(gaze.translateXProperty(), 0),
                new KeyValue(gaze.translateYProperty(), 3), new KeyValue(bubble.opacityProperty(), 0),
                new KeyValue(burst.opacityProperty(), 0),
                new KeyValue(breathing.xProperty(), 1), new KeyValue(breathing.yProperty(), 1),
                new KeyValue(body.translateXProperty(), 0), new KeyValue(tilt.angleProperty(), 0)));
        return frames;
    }

    private static Timeline gestures(String id, Rectangle leftArm, Rectangle rightArm,
                                     Rotate left, Rotate right, Group body, Rotate tilt,
                                     Scale breathing, Group eyes, Scale eyeClosure, Group gaze,
                                     Circle bubble, Group burst, Group sneeze, ReadingProps reading,
                                     ComputerProps computer,
                                     boolean working, AgentAnimations enabled) {
        boolean waveLeft = id.equals("gota");
        double readingLead = reading == null ? 0 : 4;
        double lead = readingLead + (computer == null ? 0 : 4.2);
        Rectangle wavingArm = waveLeft ? leftArm : rightArm;
        Rotate waving = waveLeft ? left : right;
        double rest = 0;
        double raised = waveLeft ? 58 : -58;
        double tucked = waveLeft ? 30 : -30;
        Timeline animation = new Timeline();
        List<KeyFrame> frames = animation.getKeyFrames();
        frames.add(new KeyFrame(Duration.ZERO,
                new KeyValue(waving.angleProperty(), rest),
                new KeyValue(wavingArm.opacityProperty(), 0),
                new KeyValue(wavingArm.translateXProperty(), tucked),
                new KeyValue(eyes.translateXProperty(), 0),
                new KeyValue(eyes.translateYProperty(), 0),
                new KeyValue(body.translateXProperty(), 0), new KeyValue(tilt.angleProperty(), 0),
                new KeyValue(eyeClosure.yProperty(), 1), new KeyValue(bubble.opacityProperty(), 0),
                new KeyValue(bubble.scaleXProperty(), 0), new KeyValue(bubble.scaleYProperty(), 0),
                new KeyValue(sneeze.opacityProperty(), 0), new KeyValue(sneeze.translateXProperty(), 0)));
        if (reading != null) frames.addAll(readingFrames(reading, eyes, leftArm, rightArm));
        if (computer != null) frames.addAll(computerFrames(computer, eyes, readingLead + .4));
        if (enabled.wave) {
            frames.addAll(List.of(
                    new KeyFrame(at(2.72, lead),
                            new KeyValue(wavingArm.opacityProperty(), 1, Interpolator.DISCRETE),
                            eased(wavingArm.translateXProperty(), tucked), eased(waving.angleProperty(), rest)),
                    new KeyFrame(at(3.02, lead), eased(wavingArm.translateXProperty(), 0),
                            eased(waving.angleProperty(), raised * .55)),
                    new KeyFrame(at(3.28, lead), eased(waving.angleProperty(), raised)),
                    new KeyFrame(at(3.48, lead), eased(waving.angleProperty(), raised * .48)),
                    new KeyFrame(at(3.68, lead), eased(waving.angleProperty(), raised)),
                    new KeyFrame(at(4.0, lead), eased(waving.angleProperty(), rest),
                            eased(wavingArm.translateXProperty(), 0)),
                    new KeyFrame(at(4.42, lead),
                            new KeyValue(wavingArm.opacityProperty(), 0, Interpolator.DISCRETE),
                            eased(wavingArm.translateXProperty(), tucked))));
        }
        if (enabled.sneeze) {
            frames.addAll(List.of(
                    new KeyFrame(at(6.0, lead), eased(body.translateXProperty(), 0),
                            eased(tilt.angleProperty(), 0), eased(eyeClosure.yProperty(), 1),
                            eased(sneeze.opacityProperty(), 0), eased(sneeze.translateXProperty(), 0)),
                    new KeyFrame(at(6.18, lead), eased(body.translateXProperty(), -3),
                            eased(tilt.angleProperty(), -6), eased(eyeClosure.yProperty(), .1)),
                    new KeyFrame(at(6.34, lead), eased(body.translateXProperty(), 3),
                            eased(tilt.angleProperty(), 5), eased(sneeze.opacityProperty(), 1)),
                    new KeyFrame(at(6.64, lead), eased(body.translateXProperty(), 0),
                            eased(tilt.angleProperty(), 0), eased(eyeClosure.yProperty(), 1),
                            eased(sneeze.opacityProperty(), 0), eased(sneeze.translateXProperty(), 10))));
        }
        if (!working && enabled.sleep) {
            frames.addAll(sleepFrames(8.75 + lead, false, bubble, burst,
                    eyeClosure, gaze, body, tilt, breathing));
            frames.addAll(List.of(
                    new KeyFrame(at(34.90, lead), eased(eyeClosure.yProperty(), .08),
                            eased(gaze.translateYProperty(), 3), eased(gaze.translateXProperty(), 0)),
                    new KeyFrame(at(35.40, lead), eased(eyeClosure.yProperty(), 1),
                            eased(gaze.translateYProperty(), 0), eased(gaze.translateXProperty(), 0))));
        }
        double end;
        if (!working && enabled.sleep) end = lead + 35.50;
        else {
            end = Math.max(2, reading != null ? 3.85 : 0);
            if (computer != null) end = Math.max(end, readingLead + 6.6);
            if (enabled.wave) end = Math.max(end, lead + 4.65);
            if (enabled.sneeze) end = Math.max(end, lead + 7.25);
        }
        frames.add(new KeyFrame(Duration.seconds(end),
                new KeyValue(waving.angleProperty(), rest),
                new KeyValue(wavingArm.opacityProperty(), 0),
                new KeyValue(wavingArm.translateXProperty(), tucked),
                new KeyValue(eyes.translateXProperty(), 0),
                new KeyValue(eyes.translateYProperty(), 0),
                new KeyValue(body.translateXProperty(), 0), new KeyValue(tilt.angleProperty(), 0),
                new KeyValue(eyeClosure.yProperty(), 1), new KeyValue(bubble.opacityProperty(), 0),
                new KeyValue(bubble.scaleXProperty(), 0), new KeyValue(bubble.scaleYProperty(), 0),
                new KeyValue(sneeze.opacityProperty(), 0), new KeyValue(sneeze.translateXProperty(), 0)));
        animation.setCycleCount(Animation.INDEFINITE);
        return animation;
    }

    private static KeyValue eased(javafx.beans.value.WritableValue<Number> property, double value) {
        return new KeyValue(property, value, Interpolator.EASE_BOTH);
    }

    private static Duration at(double seconds, double offset) {
        return Duration.seconds(seconds + offset);
    }
}
