package org.dce.systemmodel.validate;

import org.dce.systemmodel.designation.DesignationParser;
import org.dce.systemmodel.exception.ModelBuildException;
import org.dce.systemmodel.exception.ValidationIssue;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.SystemModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class SystemModelValidator {

    private static final Pattern LETTER_STAR = Pattern.compile("^[A-Za-z]$");
    private static final Pattern NUMERIC_DESIGNATION = Pattern.compile("^\\d+$");

    private SystemModelValidator() {
    }

    public static void validateStrict(SystemModel model) {
        List<ValidationIssue> issues = validate(model, false);
        if (!issues.isEmpty()) {
            throw new ModelBuildException(issues.getFirst().message(), issues);
        }
    }

    public static List<ValidationIssue> validatePartial(SystemModel model) {
        return validate(model, true);
    }

    private static List<ValidationIssue> validate(SystemModel model, boolean partialOnlyLoggedBodies) {
        List<ValidationIssue> errors = new ArrayList<>();
        for (BodyNode b : model.bodies().values()) {
            if (b.orbitParent() == null) {
                errors.add(new ValidationIssue(
                        ValidationIssue.IssueKind.INVALID_PARENT_REF,
                        b.bodyId(), "orbitParent", "body " + b.bodyId() + " missing orbit parent"));
            }
            if (b.kind() == BodyKind.MOON) {
                validateMoonDesignation(model, b, errors);
            }
        }
        validateBcdStarAncestors(model, errors);
        return errors;
    }

    private static void validateMoonDesignation(SystemModel model, BodyNode moon, List<ValidationIssue> errors) {
        String hostDesig = DesignationParser.moonHostDesignation(moon.bodyName());
        if (hostDesig == null || !NUMERIC_DESIGNATION.matcher(hostDesig).matches()) {
            return;
        }
        if (!hasAncestorWithDesignation(model, moon.bodyId(), hostDesig)) {
            errors.add(new ValidationIssue(
                    ValidationIssue.IssueKind.DESIGNATION_HIERARCHY_MISMATCH,
                    moon.bodyId(),
                    "designation",
                    moon.bodyName() + " requires ancestor planet " + hostDesig));
        }
    }

    private static void validateBcdStarAncestors(SystemModel model, List<ValidationIssue> errors) {
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() != BodyKind.STAR) {
                continue;
            }
            String label = DesignationParser.shortLabelFromName(b.bodyName());
            if (label == null || label.length() != 1 || !LETTER_STAR.matcher(label).matches()) {
                continue;
            }
            char letter = Character.toUpperCase(label.charAt(0));
            if (letter < 'B' || letter > 'D') {
                continue;
            }
            for (char required = 'A'; required < letter; required++) {
                if (!hasStarLetter(model, required)) {
                    errors.add(new ValidationIssue(
                            ValidationIssue.IssueKind.DESIGNATION_HIERARCHY_MISMATCH,
                            b.bodyId(),
                            "designation",
                            "star " + label + " requires ancestor star " + required));
                }
            }
        }
    }

    private static boolean hasStarLetter(SystemModel model, char letter) {
        String target = String.valueOf(letter);
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() != BodyKind.STAR) {
                continue;
            }
            String label = DesignationParser.shortLabelFromName(b.bodyName());
            if (target.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAncestorWithDesignation(SystemModel model, int bodyId, String hostDesig) {
        Set<Integer> visited = new HashSet<>();
        Integer cur = model.hierarchy().parentOf(bodyId);
        while (cur != null && visited.add(cur)) {
            BodyNode parent = model.body(cur).orElse(null);
            if (parent != null) {
                String label = DesignationParser.shortLabelFromName(parent.bodyName());
                if (hostDesig.equalsIgnoreCase(label)) {
                    return true;
                }
            }
            if (model.barycentre(cur).isPresent()) {
                cur = model.hierarchy().parentOf(cur);
                continue;
            }
            cur = model.hierarchy().parentOf(cur);
        }
        return false;
    }
}
