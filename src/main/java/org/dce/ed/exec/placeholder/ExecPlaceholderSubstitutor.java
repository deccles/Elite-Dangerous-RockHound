package org.dce.ed.exec.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.dce.ed.exec.ExecLaunchContext;

/** Expands {@code $SYMBOL} tokens in exec program args to argv values. */
public final class ExecPlaceholderSubstitutor {

    private ExecPlaceholderSubstitutor() {
    }

    /**
     * Tokenize args, resolve each {@code $SYMBOL} to one argv entry (plain text, no quotes in values).
     */
    public static List<String> expandProgramArgs(String programArgs, ExecPlaceholderContext ctx,
            ExecLaunchContext launch, Map<String, String> resolved) {
        List<String> rawTokens = ExecArgsTokenizer.tokenize(programArgs);
        List<String> out = new ArrayList<>(rawTokens.size());
        for (String token : rawTokens) {
            Optional<ExecPlaceholderId> id = ExecPlaceholderId.fromToken(token);
            if (id.isPresent()) {
                out.add(ExecPlaceholderResolver.valueOrUnknown(valueFor(id.get(), ctx, launch, resolved)));
            } else {
                out.add(token);
            }
        }
        return out;
    }

    private static String valueFor(ExecPlaceholderId id, ExecPlaceholderContext ctx, ExecLaunchContext launch,
            Map<String, String> resolved) {
        if (resolved != null && resolved.containsKey(id.name())) {
            return resolved.get(id.name());
        }
        return ExecPlaceholderResolver.resolveOne(ctx, launch, id);
    }
}
