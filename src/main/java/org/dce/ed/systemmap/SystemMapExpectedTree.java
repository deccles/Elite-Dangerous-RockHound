package org.dce.ed.systemmap;

import java.util.List;

/**
 * Machine-readable journal contract for a system map fixture ({@code *-expected-tree.json}).
 */
public final class SystemMapExpectedTree {

    public String systemName;
    public Long systemAddress;
    public List<Integer> scanBarycentreIds;
    public String mapExcludedNote;
    public List<BodyEntry> bodies;

    public static final class BodyEntry {
        public int id;
        public String shortName;
        public Integer immediateParentBodyId;
        public Boolean parentIsBarycentre;
        public String expectedResolve;
    }
}
