package br.com.agenteslocal.window;

import javafx.animation.PauseTransition;
import javafx.scene.control.TextInputControl;
import javafx.util.Duration;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.Consumer;

/** Opens Windows voice typing with the chat composer already focused. */
public final class WindowsVoiceTyping {
    private WindowsVoiceTyping() {}

    public static void open(TextInputControl input, Consumer<String> onError) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            onError.accept("A digitação por voz está disponível no aplicativo Windows.");
            return;
        }
        input.requestFocus();
        // Let JavaFX transfer native keyboard focus before sending Windows+H.
        PauseTransition focusDelay = new PauseTransition(Duration.millis(180));
        focusDelay.setOnFinished(event -> {
            try {
                Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_WINDOWS);
                try {
                    robot.keyPress(KeyEvent.VK_H);
                    robot.keyRelease(KeyEvent.VK_H);
                } finally {
                    robot.keyRelease(KeyEvent.VK_WINDOWS);
                }
            } catch (AWTException | SecurityException error) {
                onError.accept("Não foi possível abrir a digitação por voz do Windows. "
                        + "Selecione o campo de mensagem e pressione Windows + H.");
            }
        });
        focusDelay.play();
    }
}
