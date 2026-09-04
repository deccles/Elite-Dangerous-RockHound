package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.dce.ed.ui.EdoUi;

/**
 * Whether a goal's next blueprint grades are locked by engineer access rank (the Grade N Access
 * bar at the workshop).
 *
 * <p>Workshop rolls on this module raise that bar, so a G0 (or otherwise still-rollable) part is
 * not gated just because a later grade is above current access. The warning is for when this
 * module can no longer be used to climb — typically it is already past what this engineer can
 * apply, often because another engineer did the earlier grades.
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
        for (int grade = from + 1; grade <= target; grade++) {
            BlueprintGrade bp = gradeOf(database, goal, grade);
            if (bp == null || bp.getEngineers().isEmpty()) {
                continue;
            }
            List<String> engineers = bp.getEngineers();
            int best = ranks.bestRank(engineers);
            String who = bestNamed(engineers, ranks);
            if (best >= grade) {
                continue;
            }
            if (canRaiseAccessOnThisModule(goal, database, ranks, engineers, from, grade)) {
                continue;
            }
            String summary = "Locked G" + grade;
            StringBuilder detail = new StringBuilder();
            detail.append(bp.getName()).append(" G").append(grade)
                    .append(" needs Grade ").append(grade).append(" Access");
            if (!who.isBlank()) {
                detail.append(". ").append(who).append(" is Grade ").append(best).append(" Access");
            }
            detail.append('.');
            if (grade > 1) {
                detail.append("\n\nAdd a goal that engineers from G0 to G").append(grade - 1)
                        .append(" to account for the additional materials.");
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

    /**
     * True when an engineer who offers {@code lockedGrade} can still roll an earlier remaining
     * grade on this module, which raises their access bar at the workshop.
     */
    private static boolean canRaiseAccessOnThisModule(EngineeringGoal goal,
                                                      EngineeringDatabase database,
                                                      EngineerReputationTracker ranks,
                                                      List<String> lockedGradeEngineers,
                                                      int from,
                                                      int lockedGrade) {
        if (from + 1 >= lockedGrade) {
            return false;
        }
        for (String engineer : lockedGradeEngineers) {
            if (engineer == null || engineer.isBlank()) {
                continue;
            }
            int rank = ranks.rank(engineer);
            for (int g = from + 1; g < lockedGrade; g++) {
                BlueprintGrade step = gradeOf(database, goal, g);
                if (step == null || !namedIn(step.getEngineers(), engineer)) {
                    continue;
                }
                if (rank >= g) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean namedIn(List<String> engineers, String who) {
        String key = EngineerReputationTracker.normalize(who);
        if (key.isEmpty() || engineers == null) {
            return false;
        }
        for (String name : engineers) {
            if (key.equals(EngineerReputationTracker.normalize(name))) {
                return true;
            }
        }
        return false;
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

}
