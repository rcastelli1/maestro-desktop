package br.com.agenteslocal.chat;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.TextFlow;

/** Rich chat text with mouse selection and native clipboard shortcuts. */
public final class SelectableMessageText extends TextFlow {
    private final String plainText;
    private final Path selection = new Path();
    private int anchor;
    private int selectionStart;
    private int selectionEnd;

    public SelectableMessageText(String plainText) {
        this.plainText = plainText == null ? "" : plainText;
        selection.setManaged(false);
        selection.setMouseTransparent(true);
        selection.setFill(Color.rgb(108, 158, 214, .43));
        selection.setStroke(null);
        getChildren().add(selection);
        setFocusTraversable(true);
        setCursor(Cursor.TEXT);
        widthProperty().addListener((observable, before, after) -> updateSelection());

        setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            requestFocus();
            int index = indexAt(event.getX(), event.getY());
            if (event.getClickCount() >= 3) {
                select(0, this.plainText.length());
            } else if (event.getClickCount() == 2) {
                selectWord(index);
            } else {
                anchor = index;
                select(index, index);
            }
            event.consume();
        });
        setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            select(anchor, indexAt(event.getX(), event.getY()));
            event.consume();
        });
        setOnKeyPressed(event -> {
            if (!event.isShortcutDown()) return;
            if (event.getCode() == KeyCode.C) {
                copySelectionOrAll();
                event.consume();
            } else if (event.getCode() == KeyCode.A) {
                select(0, this.plainText.length());
                event.consume();
            }
        });
    }

    public String selectedText() {
        return plainText.substring(selectionStart, selectionEnd);
    }

    public void copySelectionOrAll() {
        copy(selectedText().isEmpty() ? plainText : selectedText());
    }

    public static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private int indexAt(double x, double y) {
        int index = hitTest(new Point2D(x, y)).getInsertionIndex();
        return Math.max(0, Math.min(plainText.length(), index));
    }

    private void select(int first, int last) {
        selectionStart = Math.max(0, Math.min(first, last));
        selectionEnd = Math.min(plainText.length(), Math.max(first, last));
        updateSelection();
    }

    private void selectWord(int index) {
        int start = Math.max(0, Math.min(index, plainText.length()));
        int end = start;
        while (start > 0 && Character.isLetterOrDigit(plainText.charAt(start - 1))) start--;
        while (end < plainText.length() && Character.isLetterOrDigit(plainText.charAt(end))) end++;
        anchor = start;
        select(start, end);
    }

    private void updateSelection() {
        if (selectionStart >= selectionEnd || getScene() == null) {
            selection.getElements().clear();
            return;
        }
        selection.getElements().setAll(rangeShape(selectionStart, selectionEnd));
    }
}
