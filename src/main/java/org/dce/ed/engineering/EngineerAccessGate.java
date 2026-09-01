package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.dce.ed.ui.EdoUi;

/**
 * Whether a goal's next blueprint grades are locked by engineer access rank (the Grade N Access
 * bar at the workshop), independent of module craft progress and materials.
 */
public final class EngineerAccessGate {

    private EngineerAccessGate() {
    }

    /**
     * @param grade         first remaining grade no current engineer can apply
     * @param accessRank    best access rank among engineers who offer that grade
     * @param engineer      engineer who currently has that best rank (may still be too low)
     * @param summary       short status label, e.g. {@code Locked G4}
     * @param detail        hover explanation
     */
    public record Block(int grade, int accessRank, String engineer, String summary, String detail) {
    }

    public static Optional<Block> blockingGrade(EngineeringGoal goal,
                                                EngineeringDatabase database,
                                                EngineerReputationTracker ranks) {
        if (goal == null || goal.isComplete() || database == null || ranks == null) {
            return Optional.empty();
        }
        int from = goal.getFromGrade();
        int target = goal.getTargetGrade();
        if (target <= from) {
            return Optional.empty();
        }
        String elsewhere = "";
        for (int grade = from + 1; grade <= target; grade++) {
            BlueprintGrade bp = gradeOf(database, goal, grade);
            if (bp == null || bp.getEngineers().isEmpty()) {
                continue;
            }
            List<String> engineers = bp.getEngineers();
            int best = ranks.bestRank(engineers);
            String who = bestNamed(engineers, ranks);
            if (best >= grade) {
                if (elsewhere.isBlank()) {
                    elsewhere = ableEngineers(engineers, ranks, grade);
                }
                continue;
            }
            String summary = "Locked G" + grade;
            StringBuilder detail = new StringBuilder();
            detail.append(bp.getName()).append(" G").append(grade)
                    .append(" needs Grade ").append(grade).append(" Access");
            if (!who.isBlank()) {
                detail.append(". ").append(who).append(" is Grade ").append(best).append(" Access");
            }
            detail.append(".\n\nBuy a cheap unused module from Outfitting (a small pulse laser or 1D ")
                    .append("shield booster), fit it, and roll G1–G").append(Math.max(1, grade - 1))
                    .append(" on that throwaway at their workshop.");
            detail.append("\n\nDo not re-engineer modules that are already done, and do not switch ")
                    .append("this module to a different blueprint — that wipes current grades.");
            if (!elsewhere.isBlank()) {
                detail.append("\n\n").append(elsewhere).append('.');
            }
            return Optional.of(new Block(grade, best, who, summary, detail.toString()));
        }
        return Optional.empty();
    }

    /**
     * Hover text that wraps as a paragraph (~300px) instead of one screen-wide line.
     */
    public static String htmlTooltip(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String tipBg = EdoUi.htmlHex(EdoUi.Internal.DARK_22);
        String tipFg = EdoUi.htmlHex(EdoUi.Internal.MENU_FG_LIGHT);
        String body = EdoUi.escapeHtmlMinimal(detail).replace("\n", "<br>");
        return "<html><!--edo-access-tip--><body style='width:300px;background-color:" + tipBg
                + ";color:" + tipFg + ";'>" + body + "</body></html>";
    }

    public static boolean isHtmlTooltip(String tip) {
        return tip != null && tip.contains("edo-access-tip");
    }

    /**
     * Red Goals footnote. One gated engineer stays generic; two or more are named.
     */
    public static String footnote(Collection<String> engineerNames) {
        List<String> names = new ArrayList<>();
        if (engineerNames != null) {
            for (String name : engineerNames) {
                if (name != null && !name.isBlank()) {
                    names.add(name.trim());
                }
            }
        }
        if (names.size() <= 1) {
            return "* This engineer may need higher grade access first";
        }
        return "* " + joinAnd(names) + " may need higher grade access first";
    }

    private static String joinAnd(List<String> names) {
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(i == names.size() - 1 ? ", and " : ", ");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    private static BlueprintGrade gradeOf(EngineeringDatabase database, EngineeringGoal goal, int grade) {
        for (BlueprintGrade bp : database.gradesFor(goal.getModuleType(), goal.getBlueprintName())) {
            if (bp != null && !bp.isExperimental() && bp.getGrade() == grade) {
                return bp;
            }
        }
        return null;
    }

    private static String bestNamed(List<String> engineers, EngineerReputationTracker ranks) {
        String bestName = "";
        int best = -1;
        for (String name : engineers) {
            if (name == null || name.isBlank()) {
                continue;
            }
            int rank = ranks.rank(name);
            if (rank > best) {
                best = rank;
                bestName = name;
            }
        }
        return bestName;
    }

    private static String ableEngineers(List<String> engineers, EngineerReputationTracker ranks, int grade) {
        List<String> able = new ArrayList<>();
        for (String name : engineers) {
            if (name != null && !name.isBlank() && ranks.rank(name) >= grade) {
                able.add(name);
            }
        }
        if (able.isEmpty()) {
            return "";
        }
        return "G" + grade + " is available from " + String.join(", ", able);
    }
}
