package br.com.agenteslocal.chat;

import br.com.agenteslocal.model.WorkspaceData.Agent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatMentions {
    private ChatMentions() { }

    public static Agent firstRecipient(List<Agent> agents, String text) {
        Agent recipient = null;
        int firstAt = Integer.MAX_VALUE;
        int longestName = -1;
        for (Agent candidate : agents) {
            if (candidate.name == null || candidate.name.isBlank()) continue;
            Pattern pattern = Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])@" + Pattern.quote(candidate.name)
                            + "(?=$|[^\\p{L}\\p{N}_-])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher match = pattern.matcher(text);
            if (!match.find()) continue;
            int at = match.start();
            if (at < firstAt || (at == firstAt && candidate.name.length() > longestName)) {
                recipient = candidate;
                firstAt = at;
                longestName = candidate.name.length();
            }
        }
        return recipient;
    }
}
