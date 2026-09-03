package org.dce.ed.engineering;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.EngineerContributionEvent;
import org.dce.ed.logreader.event.MaterialCollectedEvent;
import org.dce.ed.logreader.event.MaterialDiscardedEvent;
import org.dce.ed.logreader.event.MaterialStack;
import org.dce.ed.logreader.event.MaterialTradeEvent;
import org.dce.ed.logreader.event.MaterialsEvent;

import com.google.gson.JsonObject;

/**
 * Commander engineering material inventory from journal events.
 */
public final class EngineeringInventoryTracker {

    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private final EngineeringDatabase database;
    private volatile Runnable changeCallback;

    public EngineeringInventoryTracker() {
        this(EngineeringDatabase.getInstance());
    }

    EngineeringInventoryTracker(EngineeringDatabase database) {
        this.database = database != null ? database : EngineeringDatabase.getInstance();
    }

    public void setChangeCallback(Runnable changeCallback) {
        this.changeCallback = changeCallback;
    }

    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(counts));
    }

    public int getCount(String journalKey) {
        if (journalKey == null || journalKey.isBlank()) {
            return 0;
        }
        return counts.getOrDefault(journalKey, 0);
    }

    public boolean applyEvent(EliteLogEvent event) {
        if (event == null) {
            return false;
        }
        boolean changed = false;
        if (event instanceof MaterialsEvent e) {
            Integer mercCoins = counts.get(EngineeringMaterialKeys.MERC_COINS);
            counts.clear();
            applyStacks(e.getRaw());
            applyStacks(e.getManufactured());
            applyStacks(e.getEncoded());
            if (mercCoins != null) {
                counts.put(EngineeringMaterialKeys.MERC_COINS, mercCoins);
            }
            changed = true;
        } else if (event instanceof MaterialCollectedEvent e) {
            changed = add(e.getName(), e.getNameLocalised(), e.getCount());
        } else if (event instanceof MaterialDiscardedEvent e) {
            changed = add(e.getName(), e.getNameLocalised(), -e.getCount());
        } else if (event instanceof MaterialTradeEvent e) {
            boolean c1 = add(e.getPaidName(), e.getPaidNameLocalised(), -e.getPaidCount());
            boolean c2 = add(e.getReceivedName(), e.getReceivedNameLocalised(), e.getReceivedCount());
            changed = c1 || c2;
        } else if (event instanceof EngineerCraftEvent e) {
            for (MaterialStack ingredient : e.getIngredients()) {
                if (add(ingredient.getName(), ingredient.getNameLocalised(), -ingredient.getCount())) {
                    changed = true;
                }
            }
        } else if (event instanceof EngineerContributionEvent e) {
            if (e.isMaterialContribution()) {
                changed = add(e.getMaterial(), e.getMaterialLocalised(), -e.getQuantity());
            }
        } else if (event.getType() == EliteEventType.STATISTICS) {
            changed = applyMercCoinBalance(event);
        }
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public void bootstrapFromJournal(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        Runnable previousCallback = changeCallback;
        changeCallback = null;
        try {
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            for (EliteLogEvent event : reader.readAllEvents()) {
                EliteEventType type = event.getType();
                if (type == EliteEventType.MATERIALS
                        || type == EliteEventType.MATERIAL_COLLECTED
                        || type == EliteEventType.MATERIAL_DISCARDED
                        || type == EliteEventType.MATERIAL_TRADE
                        || type == EliteEventType.ENGINEER_CRAFT
                        || type == EliteEventType.ENGINEER_CONTRIBUTION
                        || type == EliteEventType.STATISTICS) {
                    applyEvent(event);
                }
            }
        } catch (IOException | IllegalStateException ignored) {
            // journal directory unavailable
        } finally {
            changeCallback = previousCallback;
            notifyChanged();
        }
    }

    private boolean add(String journalKey, String localisedName, int delta) {
        String key = EngineeringMaterialKeys.resolveKey(journalKey, localisedName, database);
        if (key.isBlank() || delta == 0) {
            return false;
        }
        counts.merge(key, delta, (a, b) -> Math.max(0, a + b));
        return true;
    }

    private boolean applyMercCoinBalance(EliteLogEvent event) {
        Integer balance = mercCoinsFromStatistics(event);
        if (balance == null) {
            return false;
        }
        int value = Math.max(0, balance.intValue());
        Integer previous = counts.get(EngineeringMaterialKeys.MERC_COINS);
        if (previous != null && previous.intValue() == value) {
            return false;
        }
        counts.put(EngineeringMaterialKeys.MERC_COINS, value);
        return true;
    }

    /** Journal {@code Statistics.Bank_Account.MercCoins_Current}; null when the field is absent. */
    static Integer mercCoinsFromStatistics(EliteLogEvent event) {
        if (event == null || event.getRawJson() == null) {
            return null;
        }
        JsonObject raw = event.getRawJson();
        if (!raw.has("Bank_Account") || !raw.get("Bank_Account").isJsonObject()) {
            return null;
        }
        JsonObject bank = raw.getAsJsonObject("Bank_Account");
        if (!bank.has("MercCoins_Current") || bank.get("MercCoins_Current").isJsonNull()) {
            return null;
        }
        try {
            return Integer.valueOf(bank.get("MercCoins_Current").getAsInt());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void applyStacks(List<MaterialStack> stacks) {
        for (MaterialStack stack : stacks) {
            String key = EngineeringMaterialKeys.resolveKey(stack.getName(), stack.getNameLocalised(), database);
            counts.put(key, stack.getCount());
        }
    }

    private void notifyChanged() {
        Runnable cb = changeCallback;
        if (cb != null) {
            cb.run();
        }
    }
}
