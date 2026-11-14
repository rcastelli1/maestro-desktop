package br.com.agenteslocal.codex;

import br.com.agenteslocal.model.WorkspaceData.Agent;
import br.com.agenteslocal.model.WorkspaceData.Project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves explicit @mentions in the order they appear in an agent's final message. */
public final class AgentHandoff {
    private AgentHandoff() { }

    private record Mention(Agent agent, int position) { }

    public static Agent firstRecipient(Project project, Agent sender, String answer) {
        List<Agent> recipients = recipients(project, sender, answer);
        return recipients.isEmpty() ? null : recipients.getFirst();
    }

    public static List<Agent> recipients(Project project, Agent sender, String answer) {
        if (answer == null || answer.isBlank()) return List.of();
        String visible = answer.replaceAll("(?s)```.*?```", " ");
        List<Mention> matches = new ArrayList<>();
        for (Agent candidate : project.agents) {
            if (candidate == null || candidate.name == null || candidate.name.isBlank()
                    || (sender != null && candidate.id.equals(sender.id))) continue;
            Pattern mention = Pattern.compile("(?<![\\p{L}\\p{N}._-])@" + Pattern.quote(candidate.name.trim())
                    + "(?![\\p{L}\\p{N}_-])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher match = mention.matcher(visible);
            while (match.find()) {
                matches.add(new Mention(candidate, match.start()));
            }
        }
        matches.sort(Comparator.comparingInt(Mention::position)
                .thenComparing((left, right) -> Integer.compare(right.agent().name.length(), left.agent().name.length())));
        List<Agent> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int previousPosition = -1;
        for (Mention match : matches) {
            if (match.position() == previousPosition) continue;
            previousPosition = match.position();
            if (seen.add(match.agent().id)) ordered.add(match.agent());
        }
        return ordered;
    }
}
