package org.dce.ed.binds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.Map;

import org.dce.ed.CombatTabCommands;
import org.junit.jupiter.api.Test;

class EliteBindsLoaderTest {

    @Test
    void parsesFighterOrderKeyboardBindings() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <Root PresetName="Test" MajorVersion="4" MinorVersion="1">
                  <OrderDefensiveBehaviour>
                    <Primary Device="Keyboard" Key="Key_Numpad_1" />
                    <Secondary Device="{NoDevice}" Key="" />
                  </OrderDefensiveBehaviour>
                  <OrderAggressiveBehaviour>
                    <Primary Device="Keyboard" Key="Key_Numpad_2" />
                    <Secondary Device="{NoDevice}" Key="" />
                  </OrderAggressiveBehaviour>
                  <OrderFocusTarget>
                    <Primary Device="Joystick" Key="Joy_1" />
                    <Secondary Device="Keyboard" Key="Key_Numpad_3" />
                  </OrderFocusTarget>
                  <OrderHoldFire>
                    <Primary Device="{NoDevice}" Key="" />
                    <Secondary Device="{NoDevice}" Key="" />
                  </OrderHoldFire>
                  <OrderFollow>
                    <Primary Device="Keyboard" Key="Key_F">
                      <Modifier Device="Keyboard" Key="Key_LeftControl" />
                    </Primary>
                    <Secondary Device="{NoDevice}" Key="" />
                  </OrderFollow>
                  <OpenOrders>
                    <Primary Device="Keyboard" Key="Key_Numpad_7" />
                    <Secondary Device="{NoDevice}" Key="" />
                  </OpenOrders>
                </Root>
                """;
        Map<String, EliteKeyBinding> map = EliteBindsLoader.parseKeyboardBindings(xml);
        assertEquals(KeyEvent.VK_NUMPAD1, map.get("OrderDefensiveBehaviour").getVirtualKey());
        assertEquals("Num1", map.get("OrderDefensiveBehaviour").getDisplayLabel());
        assertEquals(KeyEvent.VK_NUMPAD2, map.get("OrderAggressiveBehaviour").getVirtualKey());
        // Primary joystick → fall back to Secondary keyboard
        assertEquals(KeyEvent.VK_NUMPAD3, map.get("OrderFocusTarget").getVirtualKey());
        assertNull(map.get("OrderHoldFire"));
        EliteKeyBinding follow = map.get("OrderFollow");
        assertNotNull(follow);
        assertEquals(KeyEvent.VK_F, follow.getVirtualKey());
        assertEquals(1, follow.getModifierVirtualKeys().size());
        assertEquals(KeyEvent.VK_CONTROL, follow.getModifierVirtualKeys().get(0).intValue());
        assertTrue(follow.getDisplayLabel().contains("Ctrl"));
        assertTrue(follow.getDisplayLabel().contains("F"));
        assertEquals(KeyEvent.VK_NUMPAD7, map.get("OpenOrders").getVirtualKey());
        assertEquals(CombatTabCommands.FIGHTER.size(), EliteBindsLoader.FIGHTER_ORDER_BINDINGS.length);
        assertEquals(CombatTabCommands.TARGETING.size(), EliteBindsLoader.TARGETING_BINDINGS.length);
    }

    @Test
    void mapsCommonKeyTokens() {
        assertEquals(KeyEvent.VK_SPACE, EliteKeyCodeMapper.mapKeyToken("Key_Space").getVirtualKey());
        assertEquals(KeyEvent.VK_A, EliteKeyCodeMapper.mapKeyToken("Key_A").getVirtualKey());
        assertNull(EliteKeyCodeMapper.mapKeyToken("Key_DoesNotExist"));
    }
}
