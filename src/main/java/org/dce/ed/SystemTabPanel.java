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
import java.awt.image.BufferedImage;
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
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshLandmark;
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

    // Bio column icons (painted, no external resources) - scaled from current UI font.
    private Icon bioLeafIcon = new LeafIcon(18, 18);
    private Icon bioDollarIcon = new DollarIcon(16, 16);
    private Icon bioGeoIcon = new RingedPlanetIcon(16, 16);
    private Icon landSneakerIcon = new SneakerIcon(16, 10);

    // NEW: semi-transparent orange for separators, similar to RouteTabPanel
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

                    loadSystem(text, 0L, true);
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
        refreshBioIcons();
        table.setRowHeight(computeRowHeight(table, uiFont, 8));

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
                boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
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

                // Detail rows: bio (sample colors) or ring lines (muted gray)
                Row r = tableModel.getRowAt(row);
                boolean isBioRow = r != null && r.detail && !r.destinationRow && !r.isRingDetail()
                        && (r.bioText != null || r.bioValue != null);

                if (isSelected) {
                    c.setForeground(Color.BLACK);
                } else if (r != null && r.detail && r.isRingDetail()) {
                    c.setForeground(EdoUi.Internal.GRAY_180);
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

                Row r = tableModel.getRowAt(row);
                boolean isBioRow = r != null && r.detail && !r.destinationRow && !r.isRingDetail()
                        && (r.bioText != null || r.bioValue != null);

                if (isSelected) {
                    c.setForeground(Color.BLACK);
                } else if (r != null && r.detail && r.isRingDetail()) {
                    c.setForeground(EdoUi.Internal.GRAY_180);
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
        DefaultTableCellRenderer landRenderer = new DefaultTableCellRenderer() {
            {
                setOpaque(false);
                setHorizontalAlignment(SwingConstants.CENTER);
                setForeground(EdoUi.User.MAIN_TEXT);
            }
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table,
                                                                        "",
                                                                        isSelected,
                                                                        hasFocus,
                                                                        row,
                                                                        column);
                Row r = tableModel.getRowAt(row);
                if (isSelected) {
                    c.setForeground(Color.BLACK);
                } else if (r != null && r.detail && r.isRingDetail()) {
                    c.setForeground(EdoUi.Internal.GRAY_180);
                } else {
                    c.setForeground(EdoUi.User.MAIN_TEXT);
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(false);
                }
                c.setBackground(EdoUi.Internal.TRANSPARENT);
                boolean showSneaker = false;
                if (r != null && !r.detail && r.body != null) {
                    showSneaker = r.body.isLandable();
                }
                c.setIcon(showSneaker ? landSneakerIcon : null);
                c.setText("");
                c.setHorizontalTextPosition(SwingConstants.RIGHT);
                c.setIconTextGap(0);
                return c;
            }
        };

        // Column index 3 is "Value"
        table.getColumnModel().getColumn(2).setCellRenderer(new BioCellRenderer());

        table.getColumnModel().getColumn(3).setCellRenderer(valueRightRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(landRenderer);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        // Prevent LAF default white corner/scrollbar paints in transparent overlay mode.
        javax.swing.JPanel upperRightCorner = new javax.swing.JPanel();
        upperRightCorner.setOpaque(false);
        upperRightCorner.setBackground(EdoUi.Internal.TRANSPARENT);
        javax.swing.JPanel lowerRightCorner = new javax.swing.JPanel();
        lowerRightCorner.setOpaque(false);
        lowerRightCorner.setBackground(EdoUi.Internal.TRANSPARENT);
        scrollPane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, upperRightCorner);
        scrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, lowerRightCorner);
        if (scrollPane.getVerticalScrollBar() != null) {
            javax.swing.JScrollBar vsb = scrollPane.getVerticalScrollBar();
            vsb.setOpaque(false);
            vsb.setBackground(EdoUi.Internal.TRANSPARENT);
            vsb.setUI(new SubtleScrollBarUI());
            // Slightly wider hit area while keeping a subtle visual thumb.
            vsb.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
        }
        
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
        
        // Prevent the first columns from expanding too much (which pushes the "Bio" column
        // off-screen in small overlay widths). We cap only the leading columns so Bio starts
        // further left and stays readable.
        // Column order: 0=Body, 1=Atmo/Body, 2=Bio, 3=Value, 4=Land, 5=Dist (Ls)
        javax.swing.table.TableColumn bodyCol = table.getColumnModel().getColumn(0);
        javax.swing.table.TableColumn atmoCol = table.getColumnModel().getColumn(1);
        javax.swing.table.TableColumn bioCol = table.getColumnModel().getColumn(2);

        // Tuned for typical overlay width ~423px (see screenshot):
        // shift Bio left by capping earlier columns.
        int bodyWidth = 44;
        int atmoWidth = 140;
        int bioMinWidth = 110;

        bodyCol.setPreferredWidth(bodyWidth);
        bodyCol.setMinWidth(bodyWidth);

        atmoCol.setPreferredWidth(atmoWidth);
        atmoCol.setMinWidth(atmoWidth);

        // Ensure Bio has at least enough room for icon stack + text.
        if (bioCol.getPreferredWidth() < bioMinWidth) {
            bioCol.setPreferredWidth(bioMinWidth);
            bioCol.setMinWidth(bioMinWidth);
        }

        // Fill horizontal space: fixed-ish leading columns, last column stretches with viewport.
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
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
        		if (highestPayout >= OverlayPreferences.getBioValuableThresholdCredits()) {
        			long speakCredits = TtsSprintf.roundCreditsForSpeech(highestPayout);
        			ttsSprintf.speakf("{n} species discovered on planetary body {body} with estimated value of {credits} credits",
        					candidates.size(),
        					e.getBodyName(),
        					speakCredits);
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
                    loadSystem(e.getStarSystem(), e.getSystemAddress(), true);
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
        long startedAtMs = System.currentTimeMillis();
        try {
            java.nio.file.Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
                rebuildTable();
                return;
            }
            EliteJournalReader reader = new EliteJournalReader(journalDir);

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
                System.out.println("[EDO][Cache] refreshFromCache: no current system, took " + (System.currentTimeMillis() - startedAtMs) + "ms");
                return;
            }
            
            loadSystem(systemName, systemAddress, false);
            rebuildTable();
            System.out.println("[EDO][Cache] refreshFromCache: loaded " + systemName + " in " + (System.currentTimeMillis() - startedAtMs) + "ms");

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

    private void loadSystem(String systemName, long systemAddress, boolean allowEdsmEnrichment) {
        long startedAtMs = System.currentTimeMillis();
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

        // 2) Optionally enrich with EDSM via a single bodies call.
        if (allowEdsmEnrichment) {
            try {
                BodiesResponse edsmBodies = edsmClient.showBodies(systemName);
                if (edsmBodies != null) {
                    edsmClient.mergeBodiesFromEdsm(state, edsmBodies);
                }
            } catch (Exception ex) {
                // EDSM is best-effort; overlay should still work from cache/logs.
                ex.printStackTrace();
            }
        }

        // 3) Refresh UI and persist merged result
        rebuildTable();
        persistIfPossible();
        System.out.println("[EDO][Cache] loadSystem lookup+hydrate for " + systemName + " took " + (System.currentTimeMillis() - startedAtMs) + "ms");
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
                if (r.isRingDetail()) {
                    c.setForeground(EdoUi.Internal.GRAY_180);
                } else {
                    int samples = r.getBioSampleCount();
                    if (samples >= 3) {
                        c.setForeground(Color.GREEN);
                    } else if (samples > 0) {
                        c.setForeground(Color.YELLOW);
                    } else {
                        c.setForeground(EdoUi.Internal.GRAY_180);
                    }
                }
            } else {
                c.setForeground(EdoUi.User.MAIN_TEXT);
            }

            if (r == null) {
                return c;
            }

            // Detail rows: prepend icons before genus/ring text.
            if (r.detail) {
                Icon icon = null;
                int stackedWidth = (bioLeafIcon != null ? bioLeafIcon.getIconWidth() : 0)
                        + 1
                        + (bioDollarIcon != null ? bioDollarIcon.getIconWidth() : 0);
                int slotWidth = Math.max(stackedWidth, bioGeoIcon != null ? bioGeoIcon.getIconWidth() : 0);
                if (!r.destinationRow) {
                    if (r.isRingDetail()) {
                        icon = bioGeoIcon;
                    } else if (r.bioText != null && !r.bioText.isBlank()) {
                        HorizontalIconStack stack = new HorizontalIconStack(-6);
                        stack.add(bioLeafIcon);
                        BodyInfo parent = state.getBodies().get(Integer.valueOf(r.parentId));
                        if (parent != null) {
                            boolean excludeFromExobiology = Boolean.TRUE.equals(parent.getSpanshExcludeFromExobiology());
                            // Renderer path: keep this lightweight and never trigger remote fetches.
                            long maxPredictedBioValue = excludeFromExobiology ? Long.MIN_VALUE : getMaxPredictedBioValueNoFetch(parent);
                            if (maxPredictedBioValue >= OverlayPreferences.getBioValuableThresholdCredits()) {
                                stack.add(bioDollarIcon);
                            }
                        }
                        icon = stack.getIconWidth() > 0 ? stack : bioLeafIcon;
                    }
                }
                c.setIcon(icon != null ? new FixedWidthIcon(icon, slotWidth) : null);
                c.setText(value != null ? String.valueOf(value) : "");
                c.setHorizontalAlignment(SwingConstants.LEFT);
                c.setHorizontalTextPosition(SwingConstants.RIGHT);
                c.setIconTextGap(-4);
                return c;
            }

            BodyInfo b = r.body;
            if (b == null) {
                c.setIcon(null);
                c.setText("");
                return c;
            }

            boolean excludeFromExobiology = Boolean.TRUE.equals(b.getSpanshExcludeFromExobiology());
            boolean hasBio = !excludeFromExobiology && b.hasBio();

            // Bio column keeps text/count only; icons are shown in Atmo/Body column.
            c.setIcon(null);
            c.setHorizontalAlignment(SwingConstants.LEFT);

            // Keep text minimal when iconography already conveys type.
            String text = "";

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

    /**
     * Lightweight variant for paint/render paths.
     * Uses only already-available prediction data and never performs Spansh/network fetches.
     */
    private static long getMaxPredictedBioValueNoFetch(BodyInfo b) {
        if (b == null) {
            return Long.MIN_VALUE;
        }
        List<ExobiologyData.BioCandidate> preds = b.getPredictions();
        if (preds == null || preds.isEmpty()) {
            return Long.MIN_VALUE;
        }
        boolean firstBonus = FirstBonusHelper.firstBonusApplies(b);
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

    private static final class FixedWidthIcon implements Icon {
        private final Icon delegate;
        private final int width;

        FixedWidthIcon(Icon delegate, int width) {
            this.delegate = delegate;
            this.width = Math.max(0, width);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return delegate != null ? delegate.getIconHeight() : 0;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (delegate == null) {
                return;
            }
            delegate.paintIcon(c, g, x, y);
        }
    }

    private static final class CachedIcon implements Icon {
        private final Icon delegate;
        private transient BufferedImage cachedImage;
        private transient int cachedW = -1;
        private transient int cachedH = -1;

        CachedIcon(Icon delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getIconWidth() {
            return delegate != null ? delegate.getIconWidth() : 0;
        }

        @Override
        public int getIconHeight() {
            return delegate != null ? delegate.getIconHeight() : 0;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (delegate == null) {
                return;
            }
            int w = Math.max(1, delegate.getIconWidth());
            int h = Math.max(1, delegate.getIconHeight());
            if (cachedImage == null || cachedW != w || cachedH != h) {
                cachedW = w;
                cachedH = h;
                cachedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = cachedImage.createGraphics();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    delegate.paintIcon(c, g2, 0, 0);
                } finally {
                    g2.dispose();
                }
            }
            g.drawImage(cachedImage, x, y, null);
        }
    }

    private static final class SubtleScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(10, 24);
        }

        @Override
        protected void configureScrollBarColors() {
            trackColor = EdoUi.Internal.TRANSPARENT;
            thumbColor = EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 72);
            thumbDarkShadowColor = EdoUi.Internal.TRANSPARENT;
            thumbHighlightColor = EdoUi.Internal.TRANSPARENT;
            thumbLightShadowColor = EdoUi.Internal.TRANSPARENT;
            trackHighlightColor = EdoUi.Internal.TRANSPARENT;
        }

        @Override
        protected javax.swing.JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected javax.swing.JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private javax.swing.JButton createZeroButton() {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setOpaque(false);
            b.setFocusable(false);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // Intentionally minimal/transparent track for overlay look.
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds == null || thumbBounds.width <= 0 || thumbBounds.height <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 90));
                int padX = 2; // keep visual thumb slim inside larger hit area
                int padY = 1;
                int arc = Math.max(6, thumbBounds.width - padX * 2);
                g2.fillRoundRect(
                        thumbBounds.x + padX,
                        thumbBounds.y + padY,
                        Math.max(1, thumbBounds.width - padX * 2),
                        Math.max(1, thumbBounds.height - padY * 2),
                        arc,
                        arc);
            } finally {
                g2.dispose();
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

        private final Color fillColor = new Color(81, 189, 87);
        private final Color highlightColor = new Color(135, 228, 137);
        private final Color outlineColor = EdoUi.Internal.BLACK_ALPHA_180;
        private final Color veinColor = new Color(51, 130, 56, 220);
        private final Color stemColor = new Color(113, 76, 44);
        private final Color accentLineColor = new Color(194, 156, 112, 220);

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Inset drawing area by 1px so outline/strokes do not clip at icon bounds.
                // (ix, iy) is the top-left anchor of the drawable area.
                double ix = x + 1.0;
                double iy = y + 1.0;
                // Keep a minimum drawable size so the icon still reads if the configured icon size shrinks.
                double iw = Math.max(8.0, getIconWidth() - 2.0);
                double ih = Math.max(8.0, getIconHeight() - 2.0);

                Path2D leaf = new Path2D.Double();
                // Leaf outer silhouette:
                // - start at left base shoulder
                // - curve up left edge
                // - run into pointed tip
                // - curve down right edge
                // - return to base
                leaf.moveTo(ix + iw * 0.16, iy + ih * 0.62); // start: left base shoulder
                leaf.curveTo(
                    ix + iw * 0.26, iy + ih * 0.30, // left-edge control 1
                    ix + iw * 0.54, iy + ih * 0.10, // left-edge control 2
                    ix + iw * 0.88, iy + ih * 0.25  // near-tip left endpoint (lowered again)
                );
                leaf.lineTo(ix + iw * 0.96, iy + ih * 0.30); // tip point
                leaf.curveTo(
                    ix + iw * 0.82, iy + ih * 0.40, // right-edge control 1
                    ix + iw * 0.74, iy + ih * 0.70, // right-edge control 2
                    ix + iw * 0.56, iy + ih * 0.90  // lower-right body point
                );
                leaf.curveTo(
                    ix + iw * 0.40, iy + ih * 0.98, // bottom control 1
                    ix + iw * 0.22, iy + ih * 0.88, // bottom control 2
                    ix + iw * 0.16, iy + ih * 0.62  // close back to left shoulder
                );
                leaf.closePath();

                // Base fill for the leaf body.
                g2.setColor(fillColor);
                g2.fill(leaf);

                Path2D highlight = new Path2D.Double();
                // Internal shape for soft top-left highlight on the leaf surface.
                highlight.moveTo(ix + iw * 0.30, iy + ih * 0.58); // highlight start (lower-left of highlight)
                highlight.curveTo(
                    ix + iw * 0.36, iy + ih * 0.36, // highlight control 1 (upward lift)
                    ix + iw * 0.54, iy + ih * 0.24, // highlight control 2 (toward tip)
                    ix + iw * 0.72, iy + ih * 0.30  // highlight upper-right
                );
                highlight.curveTo(
                    ix + iw * 0.58, iy + ih * 0.34, // return control 1
                    ix + iw * 0.44, iy + ih * 0.44, // return control 2
                    ix + iw * 0.36, iy + ih * 0.62  // highlight lower edge
                );
                highlight.closePath();
                g2.setColor(highlightColor);
                g2.fill(highlight);

                // Crisp outer border to keep shape legible on bright/dark backgrounds.
                g2.setColor(outlineColor);
                g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(leaf);

                // Main (central) vein running from lower-left base region toward tip.
                g2.setColor(veinColor);
                g2.setStroke(new BasicStroke(0.95f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new QuadCurve2D.Double(
                    ix + iw * 0.22, iy + ih * 0.78, // central vein root (at stem/leaf junction)
                    ix + iw * 0.52, iy + ih * 0.40, // central vein control (higher for more arc)
                    ix + iw * 0.92, iy + ih * 0.30  // central vein near-tip termination
                ));

                // Lower-right offshoot vein:
                // root stays at the same central branch point; endpoint trends toward tip.
                drawTaperedVein(g2,
                    ix + iw * 0.44, iy + ih * 0.56, // offshoot root (aligned to central vein)
                    ix + iw * 0.57, iy + ih * 0.67, // lower offshoot control
                    ix + iw * 0.72, iy + ih * 0.58  // lower offshoot termination (right/lower)
                );

                // Upper-right offshoot vein:
                // same root as lower offshoot, terminating above it toward the tip.
                drawTaperedVein(g2,
                    ix + iw * 0.44, iy + ih * 0.56, // offshoot root (shared, aligned to central vein)
                    ix + iw * 0.43, iy + ih * 0.34, // upper offshoot control (mid-height, shifted further left)
                    ix + iw * 0.58, iy + ih * 0.24  // upper offshoot termination (near top, more vertical)
                );

                // Secondary (short) offshoot veins:
                // start about halfway between the primary offshoot root and stem root,
                // and use roughly half-length / half-curve versions of the two primary branches.
                drawTaperedVein(g2,
                    ix + iw * 0.30, iy + ih * 0.70, // secondary shared root (shifted ~25% toward stem)
                    ix + iw * 0.43, iy + ih * 0.78, // lower secondary control
                    ix + iw * 0.53, iy + ih * 0.75  // lower secondary termination (near lower edge)
                );
                drawTaperedVein(g2,
                    ix + iw * 0.30, iy + ih * 0.70, // secondary shared root (shifted ~25% toward stem)
                    ix + iw * 0.25, iy + ih * 0.51, // upper secondary control (shifted with root)
                    ix + iw * 0.37, iy + ih * 0.37  // upper secondary termination (shifted with root)
                );

                // Tiny dark connector near base.
                g2.draw(new Line2D.Double(
                    ix + iw * 0.20, iy + ih * 0.74, // connector start (near central root)
                    ix + iw * 0.10, iy + ih * 0.95  // connector end (toward stem area)
                ));

                // Brown stem line extending down-left from the leaf base.
                g2.setColor(stemColor);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new QuadCurve2D.Double(
                    ix + iw * 0.22, iy + ih * 0.78, // stem root at leaf base
                    ix + iw * 0.13, iy + ih * 0.88, // stem control (gentle bend)
                    ix + iw * 0.07, iy + ih * 0.99  // stem tip
                ));

                // Stem edge highlight (slightly lighter brown), drawn on top of stem.
                g2.setColor(accentLineColor);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new QuadCurve2D.Double(
                    ix + iw * 0.20, iy + ih * 0.80, // highlight root (offset from stem root)
                    ix + iw * 0.12, iy + ih * 0.89, // highlight control
                    ix + iw * 0.08, iy + ih * 0.97  // highlight tip
                ));
            } finally {
                g2.dispose();
            }
        }

        private void drawTaperedVein(Graphics2D g2,
                                     double x0, double y0,
                                     double cx, double cy,
                                     double x1, double y1) {
            // Continuous taper along the full curve: wide at root -> narrow at tip.
            final int segments = 12;
            // Scale taper geometry with icon size so small-font icons don't look too chunky.
            final double iconScale = Math.max(0.65, Math.min(1.8, Math.min(getIconWidth(), getIconHeight()) / 18.0));
            final double taperScale = Math.pow(iconScale, 1.2);
            final double rootHalfWidth = 1.15 * taperScale;
            final double tipHalfWidth = Math.max(0.04, 0.06 * taperScale);
            final double tipAdvance = 1.15 * taperScale;

            double[] lx = new double[segments + 1];
            double[] ly = new double[segments + 1];
            double[] rx = new double[segments + 1];
            double[] ry = new double[segments + 1];
            double lastUx = 1.0;
            double lastUy = 0.0;

            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double omt = 1.0 - t;
                double px = omt * omt * x0 + 2.0 * omt * t * cx + t * t * x1;
                double py = omt * omt * y0 + 2.0 * omt * t * cy + t * t * y1;

                double dx = 2.0 * omt * (cx - x0) + 2.0 * t * (x1 - cx);
                double dy = 2.0 * omt * (cy - y0) + 2.0 * t * (y1 - cy);
                double dl = Math.sqrt(dx * dx + dy * dy);
                if (dl > 1e-6) {
                    lastUx = dx / dl;
                    lastUy = dy / dl;
                }

                double nx = -lastUy;
                double ny = lastUx;
                double hw = rootHalfWidth + (tipHalfWidth - rootHalfWidth) * t;
                lx[i] = px + nx * hw;
                ly[i] = py + ny * hw;
                rx[i] = px - nx * hw;
                ry[i] = py - ny * hw;
            }

            double tipX = x1 + lastUx * tipAdvance;
            double tipY = y1 + lastUy * tipAdvance;

            Path2D tapered = new Path2D.Double();
            tapered.moveTo(lx[0], ly[0]);
            for (int i = 1; i <= segments; i++) {
                tapered.lineTo(lx[i], ly[i]);
            }
            tapered.lineTo(tipX, tipY);
            for (int i = segments; i >= 0; i--) {
                tapered.lineTo(rx[i], ry[i]);
            }
            tapered.closePath();

            g2.setColor(veinColor);
            g2.fill(tapered);
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

                double ix = x + 1.0;
                double iy = y + 1.0;
                double iw = Math.max(8.0, w - 2.0);
                double ih = Math.max(8.0, h - 2.0);

                Color bagFill = new Color(194, 154, 72, 235);
                Color bagDark = new Color(128, 95, 36, 220);
                Color tie = new Color(88, 62, 24, 235);

                // Main bag body.
                Path2D bag = new Path2D.Double();
                bag.moveTo(ix + iw * 0.50, iy + ih * 0.30);
                bag.curveTo(ix + iw * 0.28, iy + ih * 0.34, ix + iw * 0.18, iy + ih * 0.56, ix + iw * 0.26, iy + ih * 0.77);
                bag.curveTo(ix + iw * 0.34, iy + ih * 0.93, ix + iw * 0.66, iy + ih * 0.93, ix + iw * 0.74, iy + ih * 0.77);
                bag.curveTo(ix + iw * 0.82, iy + ih * 0.56, ix + iw * 0.72, iy + ih * 0.34, ix + iw * 0.50, iy + ih * 0.30);
                bag.closePath();
                g2.setColor(bagFill);
                g2.fill(bag);
                g2.setColor(bagDark);
                g2.setStroke(new BasicStroke(Math.max(0.8f, (float) (w * 0.06f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(bag);

                // Inverted triangle "flap" above the tie (user requested shape cue).
                Path2D flap = new Path2D.Double();
                flap.moveTo(ix + iw * 0.35, iy + ih * 0.14);
                flap.lineTo(ix + iw * 0.65, iy + ih * 0.14);
                flap.lineTo(ix + iw * 0.50, iy + ih * 0.28);
                flap.closePath();
                g2.setColor(EdoUi.withAlpha(new Color(222, 186, 102), 230));
                g2.fill(flap);
                g2.setColor(bagDark);
                g2.draw(flap);

                // Tie band directly under the flap.
                g2.setColor(tie);
                g2.setStroke(new BasicStroke(Math.max(0.9f, (float) (w * 0.08f))));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.34, iy + ih * 0.30, ix + iw * 0.66, iy + ih * 0.30));
                g2.fill(new java.awt.geom.Ellipse2D.Double(ix + iw * 0.45, iy + ih * 0.27, iw * 0.10, ih * 0.08));

                // Visible $ mark on the body.
                g2.setColor(new Color(84, 56, 18, 235));
                Font f = c != null ? c.getFont() : new Font("Dialog", Font.BOLD, 12);
                g2.setFont(f.deriveFont(Font.BOLD, Math.max(8f, (float) (h * 0.46f))));
                FontMetrics fm = g2.getFontMetrics();
                String s = "$";
                int tx = (int) Math.round(ix + (iw - fm.stringWidth(s)) * 0.50);
                int ty = (int) Math.round(iy + ih * 0.70);
                g2.drawString(s, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class RingedPlanetIcon implements Icon {
        private final int w;
        private final int h;

        RingedPlanetIcon(int w, int h) {
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

                double ix = x + 1.0;
                double iy = y + 1.0;
                double iw = Math.max(8.0, w - 2.0);
                double ih = Math.max(8.0, h - 2.0);

                Color planetFill = new Color(70, 130, 210, 235);   // blue
                Color planetShadow = new Color(35, 76, 140, 210);  // darker blue edge/shade
                Color ringColor = new Color(220, 72, 72, 220);     // red
                Color ringShadow = new Color(130, 35, 35, 200);    // darker red edge

                // Shared center: keep planet centered inside the ring.
                double cx = ix + iw * 0.50;
                double cy = iy + ih * 0.50;

                // Planet body geometry (centered on ring center).
                double planetD = Math.min(iw, ih) * 0.62;
                double planetX = cx - planetD * 0.50;
                double planetY = cy - planetD * 0.50;
                java.awt.geom.Ellipse2D planetCircle = new java.awt.geom.Ellipse2D.Double(planetX, planetY, planetD, planetD);

                // Build ring as a true annulus (outer ellipse minus inner ellipse), then rotate.
                double ringW = iw * 0.98;
                double ringH = ih * 0.42;
                double ringThickness = Math.max(1.2, Math.min(iw, ih) * 0.10);
                java.awt.geom.Ellipse2D outer = new java.awt.geom.Ellipse2D.Double(
                        cx - ringW * 0.50, cy - ringH * 0.50, ringW, ringH);
                java.awt.geom.Ellipse2D inner = new java.awt.geom.Ellipse2D.Double(
                        cx - (ringW - ringThickness * 2.0) * 0.50,
                        cy - (ringH - ringThickness * 2.0) * 0.50,
                        Math.max(1.0, ringW - ringThickness * 2.0),
                        Math.max(1.0, ringH - ringThickness * 2.0));
                java.awt.geom.Area ringArea = new java.awt.geom.Area(outer);
                ringArea.subtract(new java.awt.geom.Area(inner));
                java.awt.geom.AffineTransform ringTx = java.awt.geom.AffineTransform.getRotateInstance(
                        Math.toRadians(-22), cx, cy);
                ringArea.transform(ringTx);

                // Split into back/front halves, then layer around the planet.
                java.awt.geom.Area frontHalf = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(
                        cx - ringW * 0.75, cy, ringW * 1.50, ringH * 1.20));
                java.awt.geom.Area backHalf = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(
                        cx - ringW * 0.75, cy - ringH * 1.20, ringW * 1.50, ringH * 1.20));
                frontHalf.transform(ringTx);
                backHalf.transform(ringTx);
                java.awt.geom.Area ringFront = new java.awt.geom.Area(ringArea);
                ringFront.intersect(frontHalf);
                java.awt.geom.Area ringBack = new java.awt.geom.Area(ringArea);
                ringBack.intersect(backHalf);

                // Back half first (behind planet).
                g2.setColor(ringShadow);
                g2.fill(ringBack);
                g2.setColor(ringColor);
                g2.setStroke(new BasicStroke(Math.max(0.55f, (float) (ringThickness * 0.16f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(ringBack);

                // Planet body on top.
                g2.setColor(planetFill);
                g2.fill(planetCircle);
                g2.setColor(planetShadow);
                g2.setStroke(new BasicStroke(Math.max(0.8f, (float) (w * 0.05))));
                g2.draw(planetCircle);
                g2.setColor(EdoUi.withAlpha(new Color(164, 207, 255), 170));
                g2.fill(new java.awt.geom.Ellipse2D.Double(
                        planetX + planetD * 0.16, planetY + planetD * 0.14, planetD * 0.30, planetD * 0.22));

                // Front half last (in front of planet).
                g2.setColor(ringColor);
                g2.fill(ringFront);
                g2.setColor(ringShadow);
                g2.setStroke(new BasicStroke(Math.max(0.55f, (float) (ringThickness * 0.16f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(ringFront);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class SneakerIcon implements Icon {
        private final int w;
        private final int h;

        SneakerIcon(int w, int h) {
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
                double ix = x + 0.5;
                double iy = y + 0.5;
                double iw = Math.max(8.0, w - 1.0);
                double ih = Math.max(6.0, h - 1.0);

                Color sneakerBase = EdoUi.User.SNEAKER;
                Color upper = EdoUi.withAlpha(sneakerBase, 245);
                Color upperShade = new Color(
                        clamp255((int) Math.round(sneakerBase.getRed() * 0.797f)),
                        clamp255((int) Math.round(sneakerBase.getGreen() * 0.682f)),
                        clamp255((int) Math.round(sneakerBase.getBlue() * 0.682f)),
                        220);
                Color outline = new Color(50, 50, 50, 235);
                Color sole = new Color(252, 252, 252, 250);
                Color trim = new Color(150, 150, 150, 230);
                Color stripe = new Color(35, 35, 35, 245);
                Color lace = new Color(238, 238, 238, 245);

                // Exaggerated Chuck-style high-top silhouette for readability.
                Path2D shoe = new Path2D.Double();
                shoe.moveTo(ix + iw * 0.05, iy + ih * 0.72); // heel bottom (closer to sole)
                shoe.lineTo(ix + iw * 0.06, iy + ih * 0.10); // high collar back (taller)
                shoe.lineTo(ix + iw * 0.34, iy + ih * 0.11); // collar top (flat/high)
                shoe.lineTo(ix + iw * 0.42, iy + ih * 0.40); // lace throat
                shoe.lineTo(ix + iw * 0.72, iy + ih * 0.42); // vamp
                shoe.curveTo(ix + iw * 0.95, iy + ih * 0.44, ix + iw * 1.00, iy + ih * 0.62, ix + iw * 0.87, iy + ih * 0.72);
                shoe.lineTo(ix + iw * 0.75, iy + ih * 0.73);
                shoe.lineTo(ix + iw * 0.05, iy + ih * 0.73);
                shoe.closePath();

                g2.setColor(upper);
                g2.fill(shoe);
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(shoe);
                g2.setColor(EdoUi.withAlpha(new Color(upperShade.getRed(), upperShade.getGreen(), upperShade.getBlue()), 180));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.16, iy + ih * 0.22, ix + iw * 0.30, iy + ih * 0.70));

                // Rubber toe cap: 90deg corner + quarter-arc to the right.
                Path2D toeCap = new Path2D.Double();
                double toeLeft = ix + iw * 0.74;
                double toeTop = iy + ih * 0.52;
                double toeBottom = iy + ih * 0.76;
                double toeRight = ix + iw * 0.95;
                toeCap.moveTo(toeLeft, toeBottom);
                toeCap.lineTo(toeLeft, toeTop); // vertical edge (90deg corner)
                toeCap.lineTo(ix + iw * 0.86, toeTop); // top flat
                toeCap.curveTo(
                        ix + iw * 0.93, toeTop,      // arc control 1
                        toeRight, iy + ih * 0.59,    // arc control 2
                        toeRight, toeBottom          // arc end
                );
                toeCap.closePath();
                g2.setColor(sole);
                g2.fill(toeCap);
                g2.setColor(trim);
                g2.draw(toeCap);

                // Sole and foxing stripe.
                int sx = (int) Math.round(ix + iw * 0.03);
                int sy = (int) Math.round(iy + ih * 0.73);
                int sw = (int) Math.round(iw * 0.92);
                int sh = Math.max(2, (int) Math.round(ih * 0.15));
                g2.setColor(sole);
                g2.fillRoundRect(sx, sy, sw, sh, 3, 3);
                g2.setColor(trim);
                g2.drawRoundRect(sx, sy, sw, sh, 3, 3);
                g2.setColor(stripe);
                g2.setStroke(new BasicStroke(0.9f));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.10, iy + ih * 0.81, ix + iw * 0.84, iy + ih * 0.81));
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(0.75f));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.10, iy + ih * 0.83, ix + iw * 0.84, iy + ih * 0.83));

                // Circular ankle patch + eyelets.
                double patchD = Math.min(iw, ih) * 0.21;
                java.awt.geom.Ellipse2D patchOuter = new java.awt.geom.Ellipse2D.Double(ix + iw * 0.15, iy + ih * 0.22, patchD, patchD);
                java.awt.geom.Ellipse2D patchInner = new java.awt.geom.Ellipse2D.Double(ix + iw * 0.19, iy + ih * 0.26, patchD * 0.58, patchD * 0.58);
                g2.setColor(sole);
                g2.fill(patchOuter);
                g2.setColor(trim);
                g2.draw(patchOuter);
                g2.setColor(new Color(58, 84, 170, 235));
                g2.fill(patchInner);
                for (int i = 0; i < 4; i++) {
                    double ex = ix + iw * (0.42 + i * 0.07);
                    double ey = iy + ih * 0.49;
                    g2.fill(new java.awt.geom.Ellipse2D.Double(ex, ey, iw * 0.025, ih * 0.05));
                }
                g2.setColor(lace);
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.43, iy + ih * 0.47, ix + iw * 0.53, iy + ih * 0.39));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.50, iy + ih * 0.50, ix + iw * 0.60, iy + ih * 0.42));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.57, iy + ih * 0.53, ix + iw * 0.67, iy + ih * 0.45));
            } finally {
                g2.dispose();
            }
        }

        private static int clamp255(int v) {
            return Math.max(0, Math.min(255, v));
        }
    }

static class Row {
        final BodyInfo body;
        final boolean detail;
        final boolean destinationRow;
        /** True for ring summary lines under a body (not exobiology). */
        final boolean ringDetail;
        final int parentId;
        final String bioText;
        final String bioValue;
        private int bioSampleCount;
        
        private boolean observedGenusHeader;

        boolean isRingDetail() {
            return ringDetail;
        }

        boolean isObservedGenusHeader() {
            return observedGenusHeader;
        }

        void setObservedGenusHeader(boolean observedGenusHeader) {
            this.observedGenusHeader = observedGenusHeader;
        }
        private Row(BodyInfo body,
                    boolean detail,
                    boolean destinationRow,
                    boolean ringDetail,
                    int parentId,
                    String bioText,
                    String bioValue) {
            this.body = body;
            this.detail = detail;
            this.destinationRow = destinationRow;
            this.ringDetail = ringDetail;
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
            Row r = new Row(null, true, false, false, parentId, text, val);
            r.setBioSampleCount(bioSampleCount);
            return r;
        }
        static Row body(BodyInfo b) {
            return new Row(b, false, false, false, -1, null, null);
        }

        static Row bio(int parentId, String text, String val) {
            return new Row(null, true, false, false, parentId, text, val);
        }

        static Row ring(int parentId, String text) {
            String t = (text != null) ? text : "";
            return new Row(null, true, false, true, parentId, t, "");
        }

        static Row destination(int parentId, String destinationName) {
            String name = (destinationName != null) ? destinationName : "";
            return new Row(null, true, true, false, parentId, name, null);
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
                g2.setColor(EdoUi.ED_ORANGE_TRANS);

                int rowCount = tableModel.getRowCount();
                boolean firstBodySeen = false;

                for (int row = 0; row < rowCount; row++) {
                    Row r = tableModel.getRowAt(row);
                    if (!r.detail) { // body row
                        if (firstBodySeen) {
                            Rectangle rect = getCellRect(row, 0, true);
                            int y = rect.y;
                            g2.setColor(EdoUi.ED_ORANGE_TRANS);
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
                    // Spansh has signals but none Biological → eliminate from exobiology display
                    if (Boolean.TRUE.equals(b.getSpanshExcludeFromExobiology())) return "";
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

        if (keep.isPlanetaryBodyForRingDisplay()
                && keep.getRingSummaryLines().isEmpty()
                && !drop.getRingSummaryLines().isEmpty()) {
            keep.setRingSummaryLines(new ArrayList<>(drop.getRingSummaryLines()));
        }
        String kRes = keep.getRingReserveHumanized();
        String dRes = drop.getRingReserveHumanized();
        if (keep.isPlanetaryBodyForRingDisplay()
                && (kRes == null || kRes.isEmpty())
                && dRes != null
                && !dRes.isEmpty()) {
            keep.setRingReserveHumanized(dRes);
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
        refreshBioIcons();

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
            table.setRowHeight(computeRowHeight(table, uiFont, 8));
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

    private int computeRowHeight(JTable table, Font font, int verticalPaddingPx) {
        if (table == null || font == null) {
            return 24;
        }
        FontMetrics fm = table.getFontMetrics(font);
        int iconHeight = Math.max(
                bioLeafIcon != null ? bioLeafIcon.getIconHeight() : 0,
                Math.max(
                        bioDollarIcon != null ? bioDollarIcon.getIconHeight() : 0,
                        bioGeoIcon != null ? bioGeoIcon.getIconHeight() : 0
                )
        );
        int textHeight = fm.getAscent() + fm.getDescent();
        int h = Math.max(textHeight, iconHeight) + verticalPaddingPx;
        if (h < 24) {
            h = 24;
        }
        return h;
    }

    private void refreshBioIcons() {
        int fontSize = (uiFont != null) ? uiFont.getSize() : 14;
        int leafSize = Math.max(14, Math.round(fontSize * 1.15f));
        int dollarSize = Math.max(16, Math.round(fontSize * 1.45f));
        int geoSize = Math.max(14, Math.round(fontSize * 1.35f));
        int sneakerW = Math.max(20, Math.round(fontSize * 1.55f));
        int sneakerH = Math.max(12, Math.round(fontSize * 0.90f));
        bioLeafIcon = new CachedIcon(new LeafIcon(leafSize, leafSize));
        bioDollarIcon = new CachedIcon(new DollarIcon(dollarSize, dollarSize));
        bioGeoIcon = new CachedIcon(new RingedPlanetIcon(geoSize, geoSize));
        // Not wrapped in CachedIcon: sneaker color comes from theme prefs and must repaint when it changes.
        landSneakerIcon = new SneakerIcon(sneakerW, sneakerH);
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