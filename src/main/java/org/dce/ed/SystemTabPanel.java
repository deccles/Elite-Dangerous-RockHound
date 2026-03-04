package org.dce.ed;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BioScanPredictionEvent;
import org.dce.ed.logreader.event.BioScanPredictionEvent.PredictionKind;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemEventProcessor;
import org.dce.ed.state.SystemState;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.EdoUi.User;
import org.dce.ed.util.EdsmClient;

import org.dce.ed.edsm.UtilTable;
/**
 * System tab – now a *pure UI* renderer.
 *
 * All parsing, prediction, and system-state logic lives in:
 *   SystemState
 *   SystemEventProcessor
 *   SystemCache
 */
public class SystemTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // Bio column icons (painted, no external resources)
    private static final Icon BIO_LEAF_ICON = new LeafIcon(14, 14);
    private static final Icon BIO_DOLLAR_ICON = new DollarIcon(14, 14);

    private static final long BIO_DOLLAR_THRESHOLD = 20_000_000L;
    // NEW: semi-transparent orange for separators, similar to RouteTabPanel
    private static final Color ED_ORANGE_TRANS = EdoUi.ED_ORANGE_TRANS;
    // NEW: shared ED font (similar to Route tab)
        private Font uiFont = OverlayPreferences.getUiFont();

    private final JTable table;
    private final JTextField headerLabel;
    private final SystemBodiesTableModel tableModel;

    private final SystemState state = new SystemState();
    private final SystemEventProcessor processor = new SystemEventProcessor(EliteDangerousOverlay.clientKey, state, new EdsmClient());

    private final EdsmClient edsmClient = new EdsmClient();

    // When we receive Status.json indicating we're near/on a body, we highlight that body and its bio rows.
    // Stored as bodyId so it remains stable even if the display name changes slightly.
    private volatile Integer nearBodyId;
    private volatile String nearBodyName;
    private volatile Consumer<BodyInfo> nearBodyChangedListener;
    
    // When a body is actively targeted (Status.json Destination.Body), we outline that body block.
    private volatile Integer targetBodyId;
    private volatile String targetBodyName;

    // When a station/carrier is targeted, Destination.Body is the parent body and Destination.DisplayName is the station/carrier.
    private volatile Integer targetDestinationParentBodyId;
    private volatile String targetDestinationName;
	private JLabel headerSummaryLabel;

	/** Optional callback when system tab target/near/destination state changes (for debounced session persist). */
	private Runnable sessionStateChangeCallback;

	public void setSessionStateChangeCallback(Runnable callback) {
	    this.sessionStateChangeCallback = callback;
	}

	private void fireSessionStateChanged() {
	    if (sessionStateChangeCallback != null) {
	        sessionStateChangeCallback.run();
	    }
	}

	/** Fill system-tab-related fields of the given session state (for save). */
	public void fillSessionState(EdoSessionState state) {
	    if (state == null) return;
	    state.setTargetBodyId(targetBodyId);
	    state.setTargetBodyName(targetBodyName);
	    state.setNearBodyId(nearBodyId);
	    state.setNearBodyName(nearBodyName);
	    state.setTargetDestinationParentBodyId(targetDestinationParentBodyId);
	    state.setTargetDestinationName(targetDestinationName);
	}

	/** Apply persisted system tab state (for restore on startup). */
	public void applySessionState(EdoSessionState state) {
	    if (state == null) return;
	    if (state.getTargetBodyId() != null) {
	        targetBodyId = state.getTargetBodyId();
	        targetBodyName = state.getTargetBodyName();
	    }
	    if (state.getNearBodyId() != null || state.getNearBodyName() != null) {
	        nearBodyId = state.getNearBodyId();
	        nearBodyName = state.getNearBodyName();
	    }
	    if (state.getTargetDestinationParentBodyId() != null || state.getTargetDestinationName() != null) {
	        targetDestinationParentBodyId = state.getTargetDestinationParentBodyId();
	        targetDestinationName = state.getTargetDestinationName();
	    }
	    requestRebuild();
	}
    
	public void setNearBodyChangedListener(Consumer<BodyInfo> listener) {
	    this.nearBodyChangedListener = listener;
	}
	
    public SystemTabPanel() {
        super(new BorderLayout());
        setOpaque(false);
//        setBackground(Color.BLACK);
        // Header label
        headerLabel = new JTextField("Waiting for system data…");
        headerLabel.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {}
            public void keyReleased(KeyEvent e) {}
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String text = headerLabel.getText();
                    if (text == null) {
                        return;
                    }
//PLOEA EURL mn-j d9-22
//PLOEA EURL ZP-T C18-0
                    
                    System.out.println("User hit enter for system: '" + text + "'");

                    // User is specifying by name; let loadSystem resolve address
                    state.setSystemName(text);
                    state.setSystemAddress(0L);

                    loadSystem(text, 0L);
                }
            }
        });
        headerLabel.setForeground(EdoUi.User.MAIN_TEXT);
//        headerLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        headerLabel.setOpaque(false);
        headerLabel.setBorder(null);
        headerLabel.setFont(uiFont.deriveFont(Font.BOLD));

        headerSummaryLabel = new JLabel();
        headerSummaryLabel.setForeground(EdoUi.User.MAIN_TEXT);
        headerSummaryLabel.setFont(uiFont.deriveFont(Font.BOLD));
//        headerSummaryLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        headerSummaryLabel.setOpaque(false);
        
        // Table setup
        tableModel = new SystemBodiesTableModel();
        table = new SystemBodiesTable(tableModel);
        table.setOpaque(false);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        
        table.setBorder(null);//new EmptyBorder(0,0,0,0));
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setGridColor(EdoUi.Internal.TRANSPARENT);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(new Dimension(500, 300));
        // NEW: apply ED font to table cells
        table.setFont(uiFont);
        table.setRowHeight(24);

        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        
        table.setTableHeader(new org.dce.ed.ui.TransparentTableHeader(table.getColumnModel()));
        JTableHeader header = table.getTableHeader();
        header.setUI(org.dce.ed.ui.TransparentTableHeaderUI.createUI(header));
        header.setOpaque(false);
        header.setForeground(EdoUi.User.MAIN_TEXT);
        header.setBackground(EdoUi.User.BACKGROUND);
        header.setFont(uiFont.deriveFont(Font.BOLD));
        header.setBorder(null);
        
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        table, value, false, false, row, column);
                boolean transparent = OverlayPreferences.isOverlayTransparent();
                label.setOpaque(!transparent);
                label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setFont(uiFont.deriveFont(Font.BOLD));
                label.setHorizontalAlignment(LEFT);
//                label.setBorder(new EmptyBorder(0, 4, 0, 4));

                return label;
            }
        });
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            {
                setOpaque(false);
                setForeground(EdoUi.User.MAIN_TEXT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                // Biological detail rows (bioText/bioValue) should be gray when not selected
                Row r = tableModel.getRowAt(row);
                boolean isBioRow = (r != null && r.detail && (r.bioText != null || r.bioValue != null));

                if (isSelected) {
                    c.setForeground(Color.BLACK);
                } else if (isBioRow) {
                    int samples = r.getBioSampleCount();

                    if (samples >= 3) {
                        c.setForeground(Color.GREEN);
                    } else if (samples > 0) {
                        c.setForeground(Color.YELLOW);
                    } else {
                        c.setForeground(EdoUi.Internal.GRAY_180); // gray for biologicals
                    }
                } else {
                    c.setForeground(EdoUi.User.MAIN_TEXT);
                }

                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(false);
                }
                c.setBackground(EdoUi.Internal.TRANSPARENT);
                return c;
            }
        };

        table.setDefaultRenderer(Object.class, cellRenderer);


        DefaultTableCellRenderer valueRightRenderer = new DefaultTableCellRenderer() {
            {
                setOpaque(false);
                setForeground(EdoUi.User.MAIN_TEXT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                Component c = super.getTableCellRendererComponent(table,
                                                                  value,
                                                                  isSelected,
                                                                  hasFocus,
                                                                  row,
                                                                  column);

                setHorizontalAlignment(SwingConstants.RIGHT);

                // Biological detail rows should be gray in the Value column too
                Row r = tableModel.getRowAt(row);
                boolean isBioRow = (r != null && r.detail && (r.bioText != null || r.bioValue != null));

                if (isSelected) {
                    c.setForeground(Color.BLACK);
                } else if (isBioRow) {
                    c.setForeground(EdoUi.Internal.GRAY_180);
                } else {
                    c.setForeground(EdoUi.User.MAIN_TEXT);
                }

                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(false);
                }
                c.setBackground(EdoUi.Internal.TRANSPARENT);
                return c;
            }
        };

        // Column index 3 is "Value"
        table.getColumnModel().getColumn(2).setCellRenderer(new BioCellRenderer());

        table.getColumnModel().getColumn(3).setCellRenderer(valueRightRenderer);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        
        JViewport headerViewport = scrollPane.getColumnHeader();
        if (headerViewport != null) {
            headerViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
            headerViewport.setOpaque(false);
            headerViewport.setBackground(EdoUi.Internal.TRANSPARENT);
            headerViewport.setUI(org.dce.ed.ui.TransparentViewportUI.createUI(headerViewport));
        }

        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(headerLabel, BorderLayout.WEST);
        headerPanel.add(headerSummaryLabel, BorderLayout.CENTER);
        headerPanel.setBorder(null);

        add(headerPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);


        refreshFromCache();
        
        UtilTable.autoSizeTableColumns(table);
    }

    // ---------------------------------------------------------------------
    // Event forwarding
    // ---------------------------------------------------------------------

    public void handleLogEvent(EliteLogEvent event) {
        if (event == null) {
            return;
        }

        // 1) Mutate domain state (can be on background thread)
        processor.handleEvent(event);

        // StatusEvent is very high frequency; avoid table rebuilds.
        // We only use it to track which body the player is currently near, and which body is currently targeted.
        if (event instanceof StatusEvent) {
            StatusEvent e = (StatusEvent) event;
            updateTargetBodyFromStatus(e);
            updateNearBodyFromStatus(e);
            return;
        }

        // 2) If we jumped, do the heavy load/merge off the EDT,
        //    then refresh UI on the EDT.
        if (event instanceof BioScanPredictionEvent) {
        	BioScanPredictionEvent e = (BioScanPredictionEvent)event;
        	
        	List<BioCandidate> candidates = e.getCandidates();
        	
        	long highestPayout = 0L;
        	
        	for (BioCandidate bio : candidates) {
        		System.out.println("Need to know which planet so we can tell expected value");
        		highestPayout = Math.max(highestPayout, bio.getEstimatedPayout(e.getBonusApplies()));
        	}
        	TtsSprintf ttsSprintf = new TtsSprintf(new PollyTtsCached());
        	
        	if (e.getKind() == PredictionKind.INITIAL) {
        		if (highestPayout >= BIO_DOLLAR_THRESHOLD) {
        			ttsSprintf.speakf("{n} species discovered on planetary body {body} with estimated value of {credits} credits",
        					candidates.size(),
        					e.getBodyName(),
        					highestPayout);
        		} else {
        			ttsSprintf.speakf("{n} species discovered on planetary body {body}",
        					candidates.size(),
        					e.getBodyName());
        		}
        	}
        }
        if (event instanceof FsdJumpEvent) {
            FsdJumpEvent e = (FsdJumpEvent) event;

            new Thread(() -> {


                javax.swing.SwingUtilities.invokeLater(() -> {
                    loadSystem(e.getStarSystem(), e.getSystemAddress());
                    requestRebuild();
                    persistIfPossible();
                });
            }, "SystemTabPanel-loadSystem").start();

            return;
        }

        // 3) Normal events: just refresh UI on EDT
            requestRebuild();
            persistIfPossible();
    }

    // ---------------------------------------------------------------------
    // Cache loading at startup
    // ---------------------------------------------------------------------

    public void refreshFromCache() {
        try {
            EliteJournalReader reader = new EliteJournalReader(EliteDangerousOverlay.clientKey);

            String systemName = null;
            long systemAddress = 0L;

            List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(3);

            for (EliteLogEvent event : events) {
                if (event instanceof LocationEvent) {
                    LocationEvent e = (LocationEvent) event;
                    systemName = e.getStarSystem();
                    systemAddress = e.getSystemAddress();
                } else if (event instanceof FsdJumpEvent) {
                    FsdJumpEvent e = (FsdJumpEvent) event;
                    systemName = e.getStarSystem();
                    systemAddress = e.getSystemAddress();
                }
            }

            if ((systemName == null || systemName.isEmpty()) && systemAddress == 0L) {
                rebuildTable();
                return;
            }
            
            loadSystem(systemName, systemAddress);
            rebuildTable();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateTargetBodyFromStatus(StatusEvent e) {
    	// Called from handleLogEvent; may be on a background thread.
    	final long currentSystemAddress = state.getSystemAddress();
    	final Integer destBody = SystemTabTargetLogic.effectiveDestBody(e, currentSystemAddress);
    	final String destName = SystemTabTargetLogic.effectiveDestName(e, currentSystemAddress);

        // Body highlighting:
        // - For planet/body targets, DestinationDisplayName matches a body name and we can map it to a stable bodyId.
        // - For station/fleet carrier targets, DestinationDisplayName is the station/carrier name; Destination.Body is
        //   the parent body id (so we highlight the parent body block).
        Integer highlightBodyId = null;
        if (destName != null) {
            highlightBodyId = findBodyIdByName(destName);
        }
        if (highlightBodyId == null && destBody != null) {
            for (BodyInfo bi : state.getBodies().values()) {
                if (bi == null) {
                    continue;
                }
                if (bi.getBodyId() == destBody.intValue()) {
                    highlightBodyId = bi.getBodyId();
                    break;
                }
            }
        }

        final Integer newBodyId = highlightBodyId;
        final String newBodyName = null;

        // Intermediate destination (station/fleet carrier):
        // Show an indented row under the parent body when DestinationDisplayName is NOT a body name.
        final Integer newDestParentBodyId;
        final String newDestName;

        boolean destNameIsBody = false;
        if (destName != null) {
            Integer bodyId = findBodyIdByName(destName);
            if (bodyId != null) {
                destNameIsBody = true;
            }
        }

        if (!destNameIsBody && destBody != null && destName != null) {
            BodyInfo parent = null;
            for (BodyInfo bi : state.getBodies().values()) {
                if (bi == null) {
                    continue;
                }
                if (bi.getBodyId() == destBody.intValue()) {
                    parent = bi;
                    break;
                }
            }

            boolean sameAsBody = false;
            if (parent != null) {
                String bodyName = parent.getBodyName();
                String shortName = parent.getShortName();
                if (bodyName != null && destName.equalsIgnoreCase(bodyName)) {
                    sameAsBody = true;
                }
                if (!sameAsBody && shortName != null && destName.equalsIgnoreCase(shortName)) {
                    sameAsBody = true;
                }
            }

            if (!sameAsBody) {
                newDestParentBodyId = destBody;
                newDestName = destName;
            } else {
                newDestParentBodyId = null;
                newDestName = null;
            }
        } else {
            newDestParentBodyId = null;
            newDestName = null;
        }

        SwingUtilities.invokeLater(() -> {
            boolean changed = false;

            if (newBodyId == null) {
                if (targetBodyId != null) {
                    targetBodyId = null;
                    targetBodyName = null;
                    changed = true;
                }
            } else {
                if (targetBodyId == null || newBodyId.intValue() != targetBodyId.intValue()) {
                    targetBodyId = newBodyId;
                    targetBodyName = newBodyName;
                    changed = true;
                }
            }

            if (newDestParentBodyId == null) {
                if (targetDestinationParentBodyId != null || targetDestinationName != null) {
                    targetDestinationParentBodyId = null;
                    targetDestinationName = null;
                    changed = true;
                }
            } else {
                if (targetDestinationParentBodyId == null
                        || !newDestParentBodyId.equals(targetDestinationParentBodyId)
                        || (targetDestinationName == null && newDestName != null)
                        || (targetDestinationName != null && newDestName == null)
                        || (targetDestinationName != null && newDestName != null && !targetDestinationName.equals(newDestName))) {
                    targetDestinationParentBodyId = newDestParentBodyId;
                    targetDestinationName = newDestName;
                    changed = true;
                }
            }

            if (changed) {
                requestRebuild();
                fireSessionStateChanged();
            } else {
                table.repaint();
            }
        });
    }

    private void updateNearBodyFromStatus(StatusEvent e) {
        // Called from handleLogEvent; may be on a background thread.
        final String newBodyName = (e != null) ? e.getBodyName() : null;

        SwingUtilities.invokeLater(() -> {
            String trimmed = (newBodyName != null) ? newBodyName.trim() : null;
            if (trimmed != null && trimmed.isEmpty()) {
                trimmed = null;
            }

            // No change
            if (trimmed == null && nearBodyName == null) {
                return;
            }
            if (trimmed != null && trimmed.equalsIgnoreCase(nearBodyName)) {
                return;
            }

            nearBodyName = trimmed;
            nearBodyId = findBodyIdByName(trimmed);

            Consumer<BodyInfo> listener = nearBodyChangedListener;
            if (listener != null) {
                BodyInfo nearBody = (nearBodyId != null) ? state.getBodies().get(nearBodyId) : null;
                listener.accept(nearBody);
            }

            // Just repaint; no need to rebuild the model.
            table.repaint();
            fireSessionStateChanged();
        });
    }

    
    private Integer findBodyIdByName(String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return null;
        }

        // 1) Prefer exact match on BodyName
        for (BodyInfo b : state.getBodies().values()) {
            if (b == null || b.getBodyName() == null) {
                continue;
            }
            if (bodyName.equalsIgnoreCase(b.getBodyName())) {
                return b.getBodyId();
            }
        }

        // 2) Fallback: match on "short name" (e.g., system + body index)
        for (BodyInfo b : state.getBodies().values()) {
            if (b == null) {
                continue;
            }
            String shortName = b.getShortName();
            if (shortName != null && bodyName.equalsIgnoreCase(shortName)) {
                return b.getBodyId();
            }
        }

        return null;
    }

    private void loadSystem(String systemName, long systemAddress) {
        SystemCache cache = SystemCache.getInstance();
        CachedSystem cs = cache.get(systemAddress, systemName);

        // Start from a clean state for this system.
        state.setSystemName(systemName);
        state.setSystemAddress(systemAddress);
        state.resetBodies();
        state.setTotalBodies(null);
        state.setNonBodyCount(null);
        state.setFssProgress(null);
        state.setAllBodiesFound(null);

        // 1) Load from cache if we have it
        if (cs != null) {
            cache.loadInto(state, cs);
        }

        // 2) Always try to enrich with EDSM via a single bodies call
        try {
            BodiesResponse edsmBodies = edsmClient.showBodies(systemName);
            if (edsmBodies != null) {
                edsmClient.mergeBodiesFromEdsm(state, edsmBodies);
            }
        } catch (Exception ex) {
            // EDSM is best-effort; overlay should still work from cache/logs.
            ex.printStackTrace();
        }

        // 3) Refresh UI and persist merged result
        rebuildTable();
        persistIfPossible();
    }
    private final AtomicBoolean rebuildPending = new AtomicBoolean(false);

    // ---------------------------------------------------------------------
    // UI rebuild from SystemState
    // ---------------------------------------------------------------------
    private void requestRebuild() {
        if (!rebuildPending.compareAndSet(false, true)) {
            return; // already queued
        }

        SwingUtilities.invokeLater(() -> {
            try {
                rebuildTable();
            } finally {
                rebuildPending.set(false);
            }
        });
    }
    private void rebuildTable() {
        dedupeBodiesByName();
        updateHeaderLabel();

        List<Row> rows = BioTableBuilder.buildRows(state.getBodies().values());
        injectIntermediateDestinationRow(rows);
        tableModel.setRows(rows);

        // Debug only:
        // debugDumpBioRowsToConsole();
    }
    private static String canonicalBioName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return s;
        }

        String[] parts = s.split("\\s+");
        // Collapse "Genus Genus Species..." -> "Genus Species..."
        if (parts.length >= 3 && parts[0].equalsIgnoreCase(parts[1])) {
            StringBuilder sb = new StringBuilder(parts[0]);
            for (int i = 2; i < parts.length; i++) {
                sb.append(' ').append(parts[i]);
            }
            return sb.toString();
        }

        return s;
    }


    public static String firstWord(String s) {
        if (s == null) return "";
        String[] parts = s.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }

    private void updateHeaderLabel() {
        String systemName = state.getSystemName();
        headerLabel.setText(systemName != null ? systemName : "");
        
        StringBuilder sb = new StringBuilder();

        if (state.getTotalBodies() != null) {
            int scanned = state.getBodies().size();
            sb.append("  |  Bodies: ").append(scanned)
              .append(" of ").append(state.getTotalBodies());

            if (state.getFssProgress() != null) {
                sb.append("  (")
                  .append(Math.round(state.getFssProgress() * 100.0))
                  .append("%)");
            }
        }

        if (state.getNonBodyCount() != null) {
            sb.append("  |  Non-bodies: ").append(state.getNonBodyCount());
        }

        headerSummaryLabel.setText(sb.toString());
    }
    public static boolean bodyIssues = false;
    private void persistIfPossible() {
        if (state.getSystemName() == null
                || state.getSystemAddress() == 0L
                || state.getBodies().isEmpty()) {
            return;
        }

        boolean hasAnyRealBodies = false;

        for (BodyInfo x : state.getBodies().values()) {
            if (x == null) {
                continue;
            }

            // Temp bodies created before we learn BodyID are allowed.
            if (x.getBodyId() >= 0) {
                hasAnyRealBodies = true;
            }
        }

        if (!hasAnyRealBodies) {
            return;
        }

        SystemCache.getInstance().storeSystem(state);
    }

    // ---------------------------------------------------------------------
    // Table model
    // ---------------------------------------------------------------------

    private final class BioCellRenderer extends DefaultTableCellRenderer {
        BioCellRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {

            JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            c.setOpaque(false);
            c.setBackground(EdoUi.Internal.TRANSPARENT);

            Row r = tableModel.getRowAt(row);
            boolean isDetailRow = (r != null && r.detail);

            // Preserve the existing coloring semantics used by the default renderer.
            if (isSelected) {
                c.setForeground(Color.BLACK);
            } else if (isDetailRow) {
                int samples = r.getBioSampleCount();
                if (samples >= 3) {
                    c.setForeground(Color.GREEN);
                } else if (samples > 0) {
                    c.setForeground(Color.YELLOW);
                } else {
                    c.setForeground(EdoUi.Internal.GRAY_180);
                }
            } else {
                c.setForeground(EdoUi.User.MAIN_TEXT);
            }

            if (r == null) {
                return c;
            }

            // Only override the main (non-detail) body rows. Detail rows keep their text.
            if (r.detail) {
                c.setIcon(null);
                c.setText(value != null ? String.valueOf(value) : "");
                c.setHorizontalAlignment(SwingConstants.LEFT);
                return c;
            }

            BodyInfo b = r.body;
            if (b == null) {
                c.setIcon(null);
                c.setText("");
                return c;
            }

            boolean hasBio = b.hasBio();
            boolean hasGeo = b.hasGeo();

            long maxPredictedBioValue = getMaxPredictedBioValue(b);
            boolean showDollar = maxPredictedBioValue >= BIO_DOLLAR_THRESHOLD;

// Icon stack: leaf, then optional dollar.
            HorizontalIconStack stack = null;
            if (hasBio) {
                stack = new HorizontalIconStack(4);
                stack.add(BIO_LEAF_ICON);
                if (showDollar) {
                    stack.add(BIO_DOLLAR_ICON);
                }
            }

            c.setIcon(stack);
            c.setHorizontalAlignment(SwingConstants.LEFT);

            // Geo label is only shown when geo exists. Keep it minimal.
            String text = hasGeo ? "Geo" : "";

            // If we are showing the $ indicator, also show the bio signal count as "(n)" after the icons.
            // This is populated from FSSBodySignals / SAASignalsFound.
            Integer bioSignals = b.getNumberOfBioSignals();
            if (hasBio && bioSignals != null && bioSignals.intValue() > 0) {
            	if (text.isEmpty()) {
            		text = "(" + bioSignals + ")";
            	} else {
            		text = text + " (" + bioSignals + ")";
            	}
            }


            c.setText(text);
            c.setHorizontalTextPosition(SwingConstants.RIGHT);
            c.setIconTextGap(6);
return c;
        }
    }
    private static long getMaxPredictedBioValue(BodyInfo b) {
        if (b == null) {
            return Long.MIN_VALUE;
        }
        List<ExobiologyData.BioCandidate> preds = b.getPredictions();
        if (preds == null || preds.isEmpty()) {
            return Long.MIN_VALUE;
        }

        boolean firstBonus = !Boolean.TRUE.equals(b.getWasFootfalled());

        long max = Long.MIN_VALUE;
        for (ExobiologyData.BioCandidate c : preds) {
            if (c == null) {
                continue;
            }
            long v = c.getEstimatedPayout(firstBonus);
            if (v > max) {
                max = v;
            }
        }
        return max;
    }



    private static final class HorizontalIconStack implements Icon {
        private final java.util.List<Icon> icons = new java.util.ArrayList<>();
        private final int gap;

        HorizontalIconStack(int gap) {
            this.gap = gap;
        }

        void add(Icon icon) {
            if (icon != null) {
                icons.add(icon);
            }
        }

        @Override
        public int getIconWidth() {
            if (icons.isEmpty()) {
                return 0;
            }
            int w = 0;
            for (int i = 0; i < icons.size(); i++) {
                w += icons.get(i).getIconWidth();
                if (i < icons.size() - 1) {
                    w += gap;
                }
            }
            return w;
        }

        @Override
        public int getIconHeight() {
            int h = 0;
            for (Icon ic : icons) {
                h = Math.max(h, ic.getIconHeight());
            }
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int xx = x;
            int h = getIconHeight();
            for (int i = 0; i < icons.size(); i++) {
                Icon ic = icons.get(i);
                int yy = y + Math.max(0, (h - ic.getIconHeight()) / 2);
                ic.paintIcon(c, g, xx, yy);
                xx += ic.getIconWidth();
                if (i < icons.size() - 1) {
                    xx += gap;
                }
            }
        }
    }

    private static final class LeafIcon implements Icon {
        private final int w;
        private final int h;

        LeafIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }
        Color fillColor = Color.green;
        Color outlineColor = Color.black;
        Color veinColor = Color.black;
        
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getIconWidth();
                int h = getIconHeight();

                // Slight tilt makes it read much more like a leaf than a teardrop
                g2.rotate(Math.toRadians(-12), x + w / 2.0, y + h / 2.0);

                // Small inset so strokes don't clip, but let it fill most of the box
                double ix = x + 0.5;
                double iy = y + 0.5;
                double iw = w - 1.0;
                double ih = h - 1.0;

                // Leaf outline (pointed tip + slight asymmetry)
                Path2D leaf = new Path2D.Double();
                leaf.moveTo(ix + iw * 0.50, iy + ih * 0.04); // tip

                // Right side (slightly "fatter")
                leaf.curveTo(
                    ix + iw * 0.80, iy + ih * 0.14,  // asymmetry here (was 0.82, 0.12)
                    ix + iw * 0.98, iy + ih * 0.42,
                    ix + iw * 0.64, iy + ih * 0.81
                );

                // Bottom
                leaf.curveTo(
                    ix + iw * 0.56, iy + ih * 0.93,
                    ix + iw * 0.43, iy + ih * 0.95,
                    ix + iw * 0.36, iy + ih * 0.88
                );

                // Left side (kept a bit tighter)
                leaf.curveTo(
                    ix + iw * 0.03, iy + ih * 0.57,
                    ix + iw * 0.16, iy + ih * 0.18,
                    ix + iw * 0.50, iy + ih * 0.04
                );

                leaf.closePath();

                // Fill
                g2.setColor(fillColor);
                g2.fill(leaf);

                // Outline
                g2.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(outlineColor);
                g2.draw(leaf);

                // Stem (tiny, subtle)
                g2.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(outlineColor);
                g2.draw(new Line2D.Double(
                    ix + iw * 0.42, iy + ih * 0.88,
                    ix + iw * 0.30, iy + ih * 1.02
                ));

                // Midrib (main vein)
                g2.setColor(veinColor);
                g2.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new QuadCurve2D.Double(
                    ix + iw * 0.50, iy + ih * 0.10,
                    ix + iw * 0.60, iy + ih * 0.46,
                    ix + iw * 0.44, iy + ih * 0.84
                ));

                // Side veins (a touch offset to match tilt/asymmetry)
                g2.setStroke(new BasicStroke(0.95f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new QuadCurve2D.Double(
                    ix + iw * 0.52, iy + ih * 0.30,
                    ix + iw * 0.73, iy + ih * 0.35,
                    ix + iw * 0.83, iy + ih * 0.45
                ));
                g2.draw(new QuadCurve2D.Double(
                    ix + iw * 0.50, iy + ih * 0.47,
                    ix + iw * 0.30, iy + ih * 0.55,
                    ix + iw * 0.18, iy + ih * 0.65
                ));
            } finally {
                g2.dispose();
            }
        }

        
        
    }

    private static final class DollarIcon implements Icon {
        private final int w;
        private final int h;

        DollarIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int pad = 1;
                int cx = x + pad;
                int cy = y + pad;
                int cw = w - pad * 2;
                int ch = h - pad * 2;

                Color gold = EdoUi.User.VALUABLE;
                Color goldDark = EdoUi.Internal.BROWN_DARK;

                g2.setColor(EdoUi.withAlpha(gold, 220));
                g2.fillOval(cx, cy, cw, ch);

                g2.setColor(goldDark);
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawOval(cx, cy, cw, ch);

                // Dollar sign
                g2.setColor(EdoUi.Internal.BROWN_DARKER);
                Font f = c != null ? c.getFont() : new Font("Dialog", Font.BOLD, 12);
                g2.setFont(f.deriveFont(Font.BOLD, 11f));
                FontMetrics fm = g2.getFontMetrics();
                String s = "$";
                int tx = x + (w - fm.stringWidth(s)) / 2;
                int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent() - 1;
                g2.drawString(s, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

static class Row {
        final BodyInfo body;
        final boolean detail;
        final boolean destinationRow;
        final int parentId;
        final String bioText;
        final String bioValue;
        private int bioSampleCount;
        
        private boolean observedGenusHeader;

        boolean isObservedGenusHeader() {
            return observedGenusHeader;
        }

        void setObservedGenusHeader(boolean observedGenusHeader) {
            this.observedGenusHeader = observedGenusHeader;
        }
        private Row(BodyInfo body,
                    boolean detail,
                    boolean destinationRow,
                    int parentId,
                    String bioText,
                    String bioValue) {
            this.body = body;
            this.detail = detail;
            this.destinationRow = destinationRow;
            this.parentId = parentId;
            this.bioText = bioText;
            this.bioValue = bioValue;
            this.bioSampleCount = 0;
        }
        int getBioSampleCount() {
            return bioSampleCount;
        }

        void setBioSampleCount(int bioSampleCount) {
            this.bioSampleCount = bioSampleCount;
        }

        static Row bio(int parentId, String text, String val, int bioSampleCount) {
            Row r = new Row(null, true, false, parentId, text, val);
            r.setBioSampleCount(bioSampleCount);
            return r;
        }
        static Row body(BodyInfo b) {
            return new Row(b, false, false, -1, null, null);
        }

        static Row bio(int parentId, String text, String val) {
            return new Row(null, true, false, parentId, text, val);
        }

        static Row destination(int parentId, String destinationName) {
            String name = (destinationName != null) ? destinationName : "";
            return new Row(null, true, true, parentId, name, null);
        }
    }

    // NEW: custom JTable to draw separators only between systems
    private class SystemBodiesTable extends JTable {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
        SystemBodiesTable(SystemBodiesTableModel model) {
            super(model);
        }

        @Override
        protected void configureEnclosingScrollPane() {
            super.configureEnclosingScrollPane();

            // JTable may have just installed a LAF "table scrollpane" border with a shadow.
            Container p = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (p instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane)p;
                sp.setBorder(null);
                sp.setViewportBorder(null);
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(ED_ORANGE_TRANS);

                int rowCount = tableModel.getRowCount();
                boolean firstBodySeen = false;

                for (int row = 0; row < rowCount; row++) {
                    Row r = tableModel.getRowAt(row);
                    if (!r.detail) { // body row
                        if (firstBodySeen) {
                            Rectangle rect = getCellRect(row, 0, true);
                            int y = rect.y;
                            g2.setColor(ED_ORANGE_TRANS);
                            g2.drawLine(0, y, getWidth(), y);
                        } else {
                            firstBodySeen = true;
                        }
                    }
                }

                paintTargetBodyOutline(g2);
                paintNearBodyOutline(g2);
                paintDestinationRowText(g2);
            } finally {
                g2.dispose();
            }
        }
        private void paintDestinationRowText(Graphics2D g2) {
            Integer parentId = SystemTabPanel.this.targetDestinationParentBodyId;
            String destName = SystemTabPanel.this.targetDestinationName;

            if (parentId == null || destName == null || destName.isBlank()) {
                return;
            }

            int rowCount = tableModel.getRowCount();
            for (int row = 0; row < rowCount; row++) {
                Row r = tableModel.getRowAt(row);
                if (r == null || !r.detail || !r.destinationRow) {
                    continue;
                }
                if (r.parentId != parentId.intValue()) {
                    continue;
                }

                String text = r.bioText;
                if (text == null || text.isBlank()) {
                    return;
                }

                // Destination text is displayed in column 2 in the model.
                Rectangle cellRect = getCellRect(row, 2, true);

                // Extend drawing across the whole row (so it can spill into later columns).
                Rectangle rowRect = getCellRect(row, 0, true);
                rowRect.width = getWidth();

                Rectangle clip = g2.getClipBounds();
                if (clip != null && !clip.intersects(rowRect)) {
                    return;
                }

                Graphics2D g = (Graphics2D) g2.create();
                try {
                    g.setClip(rowRect);

                    g.setFont(getFont());
                    FontMetrics fm = g.getFontMetrics();

                    // Use the same gray tone as the target outline so it looks intentional.
                    g.setColor(EdoUi.Internal.GRAY_ALPHA_200);

                    int x = cellRect.x + 6;
                    int y = rowRect.y + (rowRect.height + fm.getAscent()) / 2 - 2;

                    g.drawString(text, x, y);
                } finally {
                    g.dispose();
                }

                // Only one destination row is injected, so we can stop.
                return;
            }
        }
        private void paintTargetBodyOutline(Graphics2D g2) {
            Integer targetBodyId = SystemTabPanel.this.targetBodyId;
            if (targetBodyId == null) {
                return;
            }

            int rowCount = tableModel.getRowCount();
            int first = -1;
            int last = -1;

            for (int row = 0; row < rowCount; row++) {
                Row r = tableModel.getRowAt(row);
                if (r == null) {
                    continue;
                }

                boolean match = false;
                if (!r.detail) {
                    if (r.body != null && r.body.getBodyId() == targetBodyId.intValue()) {
                        match = true;
                    }
                } else {
                    if (r.parentId == targetBodyId.intValue()) {
                        match = true;
                    }
                }

                if (match) {
                    if (first < 0) {
                        first = row;
                    }
                    last = row;
                } else if (first >= 0) {
                    // Rows for a body are contiguous; once we leave the block we can stop.
                    break;
                }
            }

            if (first < 0 || last < 0) {
                return;
            }

            Rectangle top = getCellRect(first, 0, true);
            Rectangle bottom = getCellRect(last, getColumnCount() - 1, true);

            int y = top.y;
            int h = (bottom.y + bottom.height) - y;

            Rectangle block = new Rectangle(0, y, getWidth(), h);
            Rectangle clip = g2.getClipBounds();
            if (clip != null && !clip.intersects(block)) {
                return;
            }

            Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Gray dashed outline for the currently targeted body.
            Color outline = EdoUi.Internal.GRAY_ALPHA_140;
            g2.setColor(outline);
            float[] dash = new float[] { 6.0f, 6.0f };
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, dash, 0.0f));

            int inset = 2;
            int arc = 12;
            g2.drawRoundRect(inset,
                    y + inset,
                    getWidth() - (inset * 2) - 1,
                    h - (inset * 2) - 1,
                    arc,
                    arc);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }

        private void paintNearBodyOutline(Graphics2D g2) {
            Integer targetBodyId = SystemTabPanel.this.nearBodyId;
            if (targetBodyId == null) {
                return;
            }

            int rowCount = tableModel.getRowCount();
            int first = -1;
            int last = -1;

            for (int row = 0; row < rowCount; row++) {
                Row r = tableModel.getRowAt(row);
                if (r == null) {
                    continue;
                }

                boolean match = false;
                if (!r.detail) {
                    if (r.body != null && r.body.getBodyId() == targetBodyId.intValue()) {
                        match = true;
                    }
                } else {
                    if (r.parentId == targetBodyId.intValue()) {
                        match = true;
                    }
                }

                if (match) {
                    if (first < 0) {
                        first = row;
                    }
                    last = row;
                } else if (first >= 0) {
                    // Rows for a body are contiguous; once we leave the block we can stop.
                    break;
                }
            }

            if (first < 0 || last < 0) {
                return;
            }

            Rectangle top = getCellRect(first, 0, true);
            Rectangle bottom = getCellRect(last, getColumnCount() - 1, true);

            int y = top.y;
            int h = (bottom.y + bottom.height) - y;

            Rectangle block = new Rectangle(0, y, getWidth(), h);
            Rectangle clip = g2.getClipBounds();
            if (clip != null && !clip.intersects(block)) {
                return;
            }

            Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color outline = EdoUi.Internal.MAIN_TEXT_ALPHA_200;
            g2.setColor(outline);
            g2.setStroke(new BasicStroke(2.0f));

            int inset = 2;
            int arc = 12;
            g2.drawRoundRect(inset,
                    y + inset,
                    getWidth() - (inset * 2) - 1,
                    h - (inset * 2) - 1,
                    arc,
                    arc);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
    }

    class SystemBodiesTableModel extends AbstractTableModel {

        private final String[] columns = {
                "Body",
                                "Atmo / Body",
                "Bio",
                "Value",
                "Land",
                "Dist (Ls)"
        };

        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> newRows) {
            rows.clear();
            if (newRows != null) {
                rows.addAll(newRows);
            }
            fireTableDataChanged();
        }

        // NEW: allow table to inspect rows (for separators)
        Row getRowAt(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int col) {
            Row r = rows.get(rowIndex);

            if (r.detail) {
                if (r.destinationRow) {
                	return "";
                }
                switch (col) {
                    case 2: return r.bioText != null ? r.bioText : "";
                    case 3: return r.bioValue != null ? r.bioValue : "";
                    default: return "";
                }
            }

            BodyInfo b = r.body;
            switch (col) {
                case 0:
                	String shortName = b.getShortName();
                    if (shortName != null
                            && b.getStarType() != null
                            && b.getStarSystem() != null
                            && shortName.equals(b.getStarSystem())) {
                        return "*";
                    }
                    return shortName != null ? shortName : "";
                case 1:
                    String atmo = b.getAtmoOrType() != null ? b.getAtmoOrType() : "";
                    atmo = atmo.replaceAll("content body",  "body");
                    atmo = atmo.replaceAll("No atmosphere",  "");
                    atmo = atmo.replaceAll("atmosphere",  "");
                    return atmo;
                case 2:
                    // CHANGED: remove bare "Bio" label – only show Geo / Bio+Geo
                    if (b.hasBio() && b.hasGeo()) return "Bio + Geo";
                    if (b.hasGeo()) return "Geo";
                    return "";
                case 3:
                    // Keep "High" marker for the main body row;
                    // detail rows carry the M Cr values.
                    return b.isHighValue() ? "High" : "";
                case 4:
                    return b.isLandable() ? "Yes" : "";
                case 5:
                    if (Double.isNaN(b.getDistanceLs())) return "";
                    return String.format(Locale.US, "%.0f Ls", b.getDistanceLs());
                default:
                    return "";
            }
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    }
    private void dedupeBodiesByName() {

        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return;
        }

        Map<String, Integer> nameToKey = new HashMap<>();
        List<Integer> keysToRemove = new ArrayList<>();

        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {

            Integer key = e.getKey();
            BodyInfo bi = e.getValue();

            if (bi == null) {
                continue;
            }

            String name = bi.getBodyName();
            if (name == null) {
                continue;
            }

            String canon = name.trim().toLowerCase(Locale.ROOT);
            if (canon.isEmpty()) {
                continue;
            }

            Integer existingKey = nameToKey.get(canon);
            if (existingKey == null) {
                nameToKey.put(canon, key);
                continue;
            }

            BodyInfo keep = bodies.get(existingKey);
            BodyInfo drop = bi;

            if (keep == null) {
                nameToKey.put(canon, key);
                continue;
            }

            // Prefer the entry with a non-negative bodyId as the "keeper"
            // (some paths still create temp/unknown ids during rescan).
            if (keep.getBodyId() < 0 && drop.getBodyId() >= 0) {
                BodyInfo tmp = keep;
                keep = drop;
                drop = tmp;

                // Swap which key is considered the keeper for later duplicates
                nameToKey.put(canon, key);
                keysToRemove.add(existingKey);
            } else {
                keysToRemove.add(key);
            }

            mergeBodiesKeepBest(keep, drop);

            // Useful debug to prove what's happening (leave it in until stable)
//            System.out.println("DEDUP body name='" + name + "' keepId=" + keep.getBodyId()
//                    + " dropId=" + drop.getBodyId());
        }

        for (Integer k : keysToRemove) {
            bodies.remove(k);
        }
    }

    private static void mergeBodiesKeepBest(BodyInfo keep, BodyInfo drop) {

        if (keep == null || drop == null) {
            return;
        }

        if (keep.getStarSystem() == null && drop.getStarSystem() != null) {
            keep.setStarSystem(drop.getStarSystem());
        }
        if (keep.getBodyName() == null && drop.getBodyName() != null) {
            keep.setBodyName(drop.getBodyName());
        }

        if (keep.getStarPos() == null && drop.getStarPos() != null) {
            keep.setStarPos(drop.getStarPos());
        }

        if (Double.isNaN(keep.getDistanceLs()) && !Double.isNaN(drop.getDistanceLs())) {
            keep.setDistanceLs(drop.getDistanceLs());
        }

        if (keep.getGravityMS() == null && drop.getGravityMS() != null) {
            keep.setGravityMS(drop.getGravityMS());
        }
        if (keep.getSurfaceTempK() == null && drop.getSurfaceTempK() != null) {
            keep.setSurfaceTempK(drop.getSurfaceTempK());
        }
        if (keep.getSurfacePressure() == null && drop.getSurfacePressure() != null) {
            keep.setSurfacePressure(drop.getSurfacePressure());
        }

        if (keep.getPlanetClass() == null && drop.getPlanetClass() != null) {
            keep.setPlanetClass(drop.getPlanetClass());
        }
        if (keep.getAtmosphere() == null && drop.getAtmosphere() != null) {
            keep.setAtmosphere(drop.getAtmosphere());
        }
        if (keep.getAtmoOrType() == null && drop.getAtmoOrType() != null) {
            keep.setAtmoOrType(drop.getAtmoOrType());
        }

        if (!keep.isLandable() && drop.isLandable()) {
            keep.setLandable(true);
        }
        if (!keep.hasBio() && drop.hasBio()) {
            keep.setHasBio(true);
        }
        if (!keep.hasGeo() && drop.hasGeo()) {
            keep.setHasGeo(true);
        }

        if (keep.getNumberOfBioSignals() == null && drop.getNumberOfBioSignals() != null) {
            keep.setNumberOfBioSignals(drop.getNumberOfBioSignals());
        }

        // Merge observed genus/species if present
        if (drop.getObservedGenusPrefixes() != null) {
            for (String g : drop.getObservedGenusPrefixes()) {
                keep.addObservedGenus(g);
            }
        }
        if (drop.getObservedBioDisplayNames() != null) {
            for (String n : drop.getObservedBioDisplayNames()) {
                keep.addObservedBioDisplayName(n);
            }
        }

        // Keep predictions if keep doesn't have them yet
        if ((keep.getPredictions() == null || keep.getPredictions().isEmpty())
                && drop.getPredictions() != null
                && !drop.getPredictions().isEmpty()) {
            keep.setPredictions(drop.getPredictions());
        }
    }


    public void applyUiFontPreferences() {
        applyUiFont(OverlayPreferences.getUiFont());
    }

    public void applyUiFont(Font font) {
        if (font == null) {
            return;
        }

        uiFont = font;

        // Apply recursively so all labels/etc. stay consistent.
        applyFontRecursively(this, uiFont);

        if (headerLabel != null) {
            headerLabel.setFont(uiFont.deriveFont(Font.BOLD));
        }
        if (headerSummaryLabel != null) {
            headerSummaryLabel.setFont(uiFont.deriveFont(Font.BOLD));
        }
        if (table != null) {
            table.setFont(uiFont);
            if (table.getTableHeader() != null) {
                table.getTableHeader().setFont(uiFont.deriveFont(Font.BOLD));
            }
        }
        revalidate();
        repaint();
    }

    private static void applyFontRecursively(Component c, Font font) {
        if (c == null || font == null) {
            return;
        }

        try {
            c.setFont(font);
        } catch (Exception e) {
            // ignore
        }

        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                applyFontRecursively(child, font);
            }
        }
    }


    public SystemState getState() {
        return state;
    }


    private void injectIntermediateDestinationRow(List<Row> rows) {
        Integer parentId = targetDestinationParentBodyId;
        String name = targetDestinationName;

        if (parentId == null || name == null || name.isBlank()) {
            return;
        }

        int insertAt = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!r.detail && r.body != null && r.body.getBodyId() == parentId.intValue()) {
                insertAt = i + 1;
                while (insertAt < rows.size() && rows.get(insertAt).detail) {
                    insertAt++;
                }
                break;
            }
        }

        if (insertAt >= 0) {
            rows.add(insertAt, Row.destination(parentId.intValue(), "> " + name));
        }
    }
}