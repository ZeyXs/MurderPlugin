package fr.zeyx.murder.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public final class CommandArgs {

    private CommandArgs() {
    }

    public static String joinArgs(String[] args, int startIndex) {
        return joinArgs(args, startIndex, "_");
    }

    public static String joinArgs(String[] args, int startIndex, String delimiter) {
        if (args == null || startIndex >= args.length) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(delimiter == null ? "_" : delimiter);
        for (int i = startIndex; i < args.length; i++) {
            String value = args[i];
            if (value != null && !value.isEmpty()) {
                joiner.add(value);
            }
        }
        return joiner.toString();
    }

    public static List<String> filterByPrefix(List<String> candidates, String prefix) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (prefix == null || prefix.isEmpty()) {
            return candidates;
        }
        String lowerPrefix = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase().startsWith(lowerPrefix)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
