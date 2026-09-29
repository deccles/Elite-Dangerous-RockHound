package org.dce.ed.engineering;

import java.io.IOException;
import java.time.Instant;
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
import org.dce.ed.logreader.event.LoadGameEvent;
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
    /** Operation payouts the journal does not log until the next Statistics. */
    private MercCoinOperationEstimator mercEstimator = new MercCoinOperationEstimator();

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

    /** Replaces on-hand counts without notifying listeners. For tests. */
    public void replaceCountsForTest(Map<String, Integer> inventory) {
        counts.clear();
        mercEstimator = new MercCoinOperationEstimator();
        if (inventory == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            counts.put(EngineeringMaterialKeys.canonicalKey(entry.getKey()), entry.getValue());
        }
    }

    public int getCount(String journalKey) {
        if (journalKey == null || journalKey.isBlank()) {
            return 0;
        }
        return counts.getOrDefault(journalKey, 0);
    }

    /** True when the Merc Coin balance includes estimated Operation payouts not yet reported. */
    public boolean isMercCoinBalanceEstimated() {
        return mercEstimator.pendingOperations() > 0;
    }

    /** Estimated coins from {@link #mercCoinPendingOperations()} Operations since the last report. */
    public int mercCoinPendingEstimate() {
        return mercEstimator.pendingEstimate();
    }

    public int mercCoinPendingOperations() {
        return mercEstimator.pendingOperations();
    }

    /** When the game last reported the real Merc Coin balance, or null. */
    public Instant mercCoinLastReportedAt() {
        return mercEstimator.lastReportedAt();
    }

    /** Merc Coins earned this week (Thursday 07:00 UTC reset), real where reported. */
    public int mercCoinsEarnedThisWeek(Instant now) {
        return mercEstimator.earnedInWeekOf(now);
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
            // Ingredients omit Merc Coins. Subtract the recipe cost for this roll so the
            // balance moves before the next Statistics event overwrites it.
            if (deductUnlistedMercCoins(e)) {
                changed = true;
            }
        } else if (event instanceof EngineerContributionEvent e) {
            if (e.isMaterialContribution()) {
                changed = add(e.getMaterial(), e.getMaterialLocalised(), -e.getQuantity());
            }
        } else if (event.getType() == EliteEventType.STATISTICS) {
            boolean wasEstimated = isMercCoinBalanceEstimated();
            changed = applyMercCoinBalance(event);
            Integer balance = mercCoinsFromStatistics(event);
            if (balance != null) {
                mercEstimator.onStatistics(event.getTimestamp(), Math.max(0, balance),
                        mercCoinsSpentFromStatistics(event));
                changed = changed || wasEstimated;
            }
        } else if (event instanceof LoadGameEvent e) {
            int payout = mercEstimator.onLoadGame(e.getTimestamp(), e.getCredits());
            // Only add to a known balance; an estimate on top of nothing would read as real.
            if (payout > 0 && counts.containsKey(EngineeringMaterialKeys.MERC_COINS)) {
                changed = add(EngineeringMaterialKeys.MERC_COINS, null, payout);
            }
        } else if (event.getType() == EliteEventType.GAME_MODE_CHANGE) {
            JsonObject raw = event.getRawJson();
            String mode = raw != null && raw.has("GameMode") && !raw.get("GameMode").isJsonNull()
                    ? raw.get("GameMode").getAsString()
                    : null;
            mercEstimator.onGameModeChange(event.getTimestamp(), mode);
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
                        || type == EliteEventType.STATISTICS
                        || type == EliteEventType.LOAD_GAME
                        || type == EliteEventType.GAME_MODE_CHANGE) {
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

    private boolean deductUnlistedMercCoins(EngineerCraftEvent craft) {
        if (craft == null || craft.getLevel() <= 0) {
            return false;
        }
        // No balance yet: subtracting would go negative and inflate the shortfall.
        if (!counts.containsKey(EngineeringMaterialKeys.MERC_COINS)) {
            return false;
        }
        // Experimental applies report the module's current Level but are not a grade roll.
        String experimental = craft.getApplyExperimentalEffect();
        if (experimental != null && !experimental.isBlank()) {
            return false;
        }
        for (MaterialStack ingredient : craft.getIngredients()) {
            String key = EngineeringMaterialKeys.resolveKey(
                    ingredient.getName(), ingredient.getNameLocalised(), database);
            if (EngineeringMaterialKeys.isMercCoins(key)) {
                return false;
            }
        }
        var resolved = EngineeringJournalBlueprintResolver.resolve(
                craft.getSlot(), craft.getModule(), craft.getBlueprintName(), database);
        if (resolved.isEmpty()) {
            return false;
        }
        int cost = 0;
        for (BlueprintGrade grade : database.gradesFor(
                resolved.get().moduleType(), resolved.get().blueprintName())) {
            if (grade == null || grade.isExperimental() || grade.getGrade() != craft.getLevel()) {
                continue;
            }
            for (MaterialRequirement mat : grade.getMaterials()) {
                if (mat != null && EngineeringMaterialKeys.isMercCoins(mat.getKey())) {
                    cost += Math.max(0, mat.getCount());
                }
            }
        }
        if (cost <= 0) {
            return false;
        }
        return add(EngineeringMaterialKeys.MERC_COINS, null, -cost);
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

    /** Journal {@code Statistics.Bank_Account.MercCoins_Total_Spent}; null when absent. */
    static Integer mercCoinsSpentFromStatistics(EliteLogEvent event) {
        if (event == null || event.getRawJson() == null) {
            return null;
        }
        JsonObject raw = event.getRawJson();
        if (!raw.has("Bank_Account") || !raw.get("Bank_Account").isJsonObject()) {
            return null;
        }
        JsonObject bank = raw.getAsJsonObject("Bank_Account");
        if (!bank.has("MercCoins_Total_Spent") || bank.get("MercCoins_Total_Spent").isJsonNull()) {
            return null;
        }
        try {
            return Integer.valueOf(bank.get("MercCoins_Total_Spent").getAsInt());
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
