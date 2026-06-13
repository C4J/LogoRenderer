package com.commander4j.logorenderer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a drawing-element id, returns the set of raw-source line indices that
 * participate in its data chain: the element row itself, the FL row that
 * targets it, the source VAR/FD row, and any further VAR/FD rows referenced
 * recursively by {@code %NF} or {@code %(N,...)$} tokens in those formulas.
 *
 * Used by the UI to highlight every related row at once when the user clicks
 * a visual element on the canvas.
 */
public final class ChainWalker
{

    private static final int MAX_DEPTH = 20;

    private static final Pattern FIELD_TOKEN = Pattern.compile("%(\\d+)F");
    private static final Pattern PAREN_REF   = Pattern.compile("%\\((\\d+)\\s*,");

    private ChainWalker() {}

    /**
     * Compute the set of line indices in {@code rawLines} that belong to the
     * chain rooted at drawing element {@code elementId}.
     */
    public static Set<Integer> indicesFor(int elementId, LlfLayout layout, List<String> rawLines)
    {
        Set<Integer> elementIds = new HashSet<>();
        elementIds.add(elementId);

        Set<Integer> sourceIds = new HashSet<>();
        Deque<Integer> queue   = new ArrayDeque<>();

        int rootSrc = layout.getSourceForElement(elementId);
        if (rootSrc >= 0)
        {
            sourceIds.add(rootSrc);
            queue.add(rootSrc);
        }

        int depth = 0;
        while (!queue.isEmpty() && depth++ < MAX_DEPTH)
        {
            int current = queue.poll();
            String formula = layout.getUvarFormula(current);
            if (formula == null) continue; // FD leaf — nothing to recurse into

            Matcher mf = FIELD_TOKEN.matcher(formula);
            while (mf.find())
            {
                int id = parseIntOrNeg(mf.group(1));
                if (id >= 0 && sourceIds.add(id)) queue.add(id);
            }
            Matcher mp = PAREN_REF.matcher(formula);
            while (mp.find())
            {
                int id = parseIntOrNeg(mp.group(1));
                if (id >= 0 && sourceIds.add(id)) queue.add(id);
            }
        }

        Set<Integer> indices = new LinkedHashSet<>();
        for (int i = 0; i < rawLines.size(); i++)
        {
            String line = rawLines.get(i);
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (matchesElementRow(trimmed, elementIds))        indices.add(i);
            else if (matchesFlTarget(trimmed, elementIds))     indices.add(i);
            else if (matchesIdPrefixed(trimmed, "VAR,", sourceIds)) indices.add(i);
            else if (matchesIdPrefixed(trimmed, "FD,",  sourceIds)) indices.add(i);
            else if (matchesIdPrefixed(trimmed, "UVAR,", sourceIds)) indices.add(i);
            else if (matchesIdPrefixed(trimmed, "QUE,", sourceIds)) indices.add(i);
            else if (matchesIdPrefixed(trimmed, "QA,",  sourceIds)) indices.add(i);
            else if (matchesIdPrefixed(trimmed, "BD,",  sourceIds)) indices.add(i);
        }
        return indices;
    }

    /**
     * Return the raw-line index of the drawing element with the given id
     * (the {@code T/S/C/G/L} row), or -1 if no such row is present.
     */
    public static int elementRowIndex(int elementId, List<String> rawLines)
    {
        Set<Integer> one = new HashSet<>();
        one.add(elementId);
        for (int i = 0; i < rawLines.size(); i++)
        {
            String line = rawLines.get(i);
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (matchesElementRow(trimmed, one)) return i;
        }
        return -1;
    }

    /** Match T,N,... / S,N,... / C,N,... / G,N,... / L,N,... where N is in ids. */
    private static boolean matchesElementRow(String line, Set<Integer> ids)
    {
        if (line.length() < 3 || line.charAt(1) != ',') return false;
        char t = Character.toUpperCase(line.charAt(0));
        if (t != 'T' && t != 'S' && t != 'C' && t != 'G' && t != 'L') return false;
        int id = parseIdAfter(line, 2);
        return id >= 0 && ids.contains(id);
    }

    /** Match FL,src,tgt where tgt is in ids (the link that feeds the element). */
    private static boolean matchesFlTarget(String line, Set<Integer> ids)
    {
        if (!line.regionMatches(true, 0, "FL,", 0, 3)) return false;
        int firstComma = line.indexOf(',');
        int secondComma = line.indexOf(',', firstComma + 1);
        if (secondComma < 0) return false;
        int tgt = parseIntOrNeg(line.substring(secondComma + 1).trim());
        return tgt >= 0 && ids.contains(tgt);
    }

    /** Match PREFIX,N,... where N is in ids. PREFIX includes trailing comma. */
    private static boolean matchesIdPrefixed(String line, String prefix, Set<Integer> ids)
    {
        if (!line.regionMatches(true, 0, prefix, 0, prefix.length())) return false;
        int id = parseIdAfter(line, prefix.length());
        return id >= 0 && ids.contains(id);
    }

    /** Parse the integer immediately after {@code start} up to the next comma or EOL. */
    private static int parseIdAfter(String line, int start)
    {
        int end = line.indexOf(',', start);
        String token = (end < 0) ? line.substring(start) : line.substring(start, end);
        return parseIntOrNeg(token.trim());
    }

    private static int parseIntOrNeg(String s)
    {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }
}
