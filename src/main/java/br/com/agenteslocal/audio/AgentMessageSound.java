package br.com.agenteslocal.audio;

import javafx.scene.media.AudioClip;

import java.net.URL;

/** Plays the bundled sounds for agent messages and completed tasks. */
public final class AgentMessageSound {
    private AudioClip messageClip;
    private AudioClip completionClip;
    private boolean unavailable;

    public void playMessage() {
        play(false);
    }

    public void playCompletion() {
        play(true);
    }

    private void play(boolean completion) {
        if (unavailable) return;
        try {
            AudioClip clip = completion ? completionClip : messageClip;
            if (clip == null) {
                String path = completion ? "/sounds/agent_complete.mp3" : "/sounds/agent_message.mp3";
                URL resource = AgentMessageSound.class.getResource(path);
                if (resource == null) return;
                clip = new AudioClip(resource.toExternalForm());
                if (completion) completionClip = clip;
                else messageClip = clip;
            }
            clip.play();
        } catch (RuntimeException error) {
            // Audio devices may be unavailable. Chat delivery must still work.
            unavailable = true;
        }
    }

    public void stop() {
        if (messageClip != null) messageClip.stop();
        if (completionClip != null) completionClip.stop();
    }
}
