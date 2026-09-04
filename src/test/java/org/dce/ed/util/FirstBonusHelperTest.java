package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

class FirstBonusHelperTest {

    @Test
    void unmappedPlanetAppliesBonus() {
        assertTrue(FirstBonusHelper.firstBonusApplies(Boolean.FALSE));
    }

    @Test
    void mappedPlanetSuppressesBonus() {
        assertFalse(FirstBonusHelper.firstBonusApplies(Boolean.TRUE));
    }

    @Test
    void unknownMappingSuppressesBonus() {
        assertFalse(FirstBonusHelper.firstBonusApplies((Boolean) null));
    }

    @Test
    void bodyInfoUsesWasMappedNotFootfall() {
        BodyInfo body = new BodyInfo();
        body.setWasMapped(Boolean.TRUE);
        body.setWasFootfalled(Boolean.FALSE);
        assertFalse(FirstBonusHelper.firstBonusApplies(body));

        body.setWasMapped(Boolean.FALSE);
        assertTrue(FirstBonusHelper.firstBonusApplies(body));
    }

    @Test
    void cachedBodyUsesWasMapped() {
        CachedBody body = new CachedBody();
        body.wasMapped = Boolean.TRUE;
        body.wasFootfalled = Boolean.FALSE;
        assertFalse(FirstBonusHelper.firstBonusApplies(body));

        body.wasMapped = Boolean.FALSE;
        assertTrue(FirstBonusHelper.firstBonusApplies(body));
    }
}
