// @ExecutionModes({ON_SINGLE_NODE="/main_menu/aaa"})

// Modified by aaa1386 (2026)
// https://github.com/aaa1386
//
// Based on the original MapCrawler script by bbarbosa.
// Extensively modified and extended.
//
// Original repository:
// https://github.com/i-plasm/freeplane-scripts
//
// Original script:
// https://github.com/i-plasm/freeplane-scripts/blob/main/src/scripts/mapCrawler.groovy

//package scripts
/*
 * MapCrawler: Freeplane tool for searching across different map scopes
 * and quick inspection of results.
 *
 * Info & Discussion:
 * https://github.com/freeplane/freeplane/discussions/2344
 *
 * Last Update: 2025-03-17
 *
 * Copyright (C) 2025 bbarbosa
 * Copyright (C) 2026 aaa1386
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see
 * <https://www.gnu.org/licenses/>.
 */

import groovy.transform.Field
import java.awt.*
import org.freeplane.features.map.clipboard.MapClipboardController
import org.freeplane.features.map.NodeModel
import java.awt.Toolkit
import java.awt.datatransfer.Transferable
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeEvent
import java.io.*
import org.freeplane.features.clipboard.ClipboardController
import org.freeplane.features.map.MapController
import org.freeplane.features.mode.Controller
import javax.swing.*
import javax.swing.table.*
import javax.swing.border.Border
import javax.swing.event.*
import java.awt.datatransfer.*
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.MenuUtils
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.api.Node
import org.freeplane.core.resources.ResourceController
import org.freeplane.core.ui.components.TagIcon
import org.freeplane.features.icon.NamedIcon
import org.freeplane.features.icon.IconController
import org.freeplane.features.icon.mindmapmode.MIconController
import org.freeplane.features.mode.mindmapmode.MModeController
import org.freeplane.plugin.script.proxy.MapProxy
import org.freeplane.features.map.NodeModel
import org.freeplane.core.util.TextUtils
import java.util.zip.CRC32
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.regex.Matcher
import org.freeplane.core.ui.components.HSLColorConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.List
import java.util.ArrayList
import java.util.Deque
import java.util.ArrayDeque
import javax.swing.plaf.basic.BasicSplitPaneUI
import javax.swing.plaf.basic.BasicSplitPaneDivider
import org.freeplane.features.nodestyle.NodeStyleController
import org.freeplane.features.styles.LogicalStyleController.StyleOption

// ========== Main singleton instance ==========
@Field static SimpleMapCrawler mapCrawler

if (mapCrawler == null) {
    mapCrawler = new SimpleMapCrawler()
    mapCrawler.toggle()
    return
}
mapCrawler.toggle()

// ============================================================
// ======================= MAIN CLASS =========================
// ============================================================
class SimpleMapCrawler {
    // ========== Floating breadcrumb and selection polling ==========
    // ========== Tag Drag & Drop ==========
    private JLabel dragGhostLabel = null
    private JWindow dragGhostWindow = null
    private boolean isDragging = false
    // ========================================
    private String draggedTag = null
    private Point dragStartPoint = null
    private int dragSourceRow = -1
    // ========================================
    
    // ========== Map node row in table ==========
    private boolean mapNodeRowAdded = false
    private int mapNodeRowIndex = -1
    
    // ========== Tag operations ==========
    private JMenuItem tagMenuItem
    private KeyStroke tagShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0)
    
    // ========== Map selection polling ==========
    private Timer mapSelectionPollingTimer = null
    private Node lastPolledMapNode = null
    
    // ========== Breadcrumb only mode ==========
    private boolean showOnlyBreadcrumbs = false
    private JPanel breadcrumbOnlyPanel = null
    private JList<Node> breadcrumbJList = null
    private DefaultListModel<Node> ancestorsModel = new DefaultListModel<>()
    private JCheckBox breadcrumbOnlyCheck = null
    
    // ========== Tag Drag & Drop methods ==========
    
    // ====== Update breadcrumb above table ======
    
    // ====== Helper: Get node under mouse in main map view ======
    private Node getNodeAtPointInMapView(Point screenPoint) {
        try {
            def mapView = Controller.getCurrentController().getMapViewManager().getMapViewComponent()
            if (mapView == null) return null
            
            // Convert screen point to MapView coordinates
            Point localPoint = new Point(screenPoint)
            SwingUtilities.convertPointFromScreen(localPoint, mapView)
            
            // Method 1: getNodeViewAt
            def nodeView = mapView.getNodeViewAt(localPoint)
            if (nodeView != null) {
                return nodeView.getNode()
            }
            
            // Method 2: detectObject
            def detected = mapView.detectObject(localPoint)
            if (detected instanceof org.freeplane.view.swing.map.NodeView) {
                return ((org.freeplane.view.swing.map.NodeView) detected).getNode()
            }
            
            // Method 3: fallback to selected node
            def selectedNodes = ScriptUtils.c().selecteds
            if (selectedNodes && !selectedNodes.isEmpty()) {
                return selectedNodes[0]
            }
            
            return null
        } catch (Exception ex) {
            println "Error finding node: ${ex.message}"
            return null
        }
    }
    
    // ====== Mouse released handler for drag & drop ======
    void mouseReleased(MouseEvent e) {
        if (isDragging && draggedTag != null) {
            Node targetNode = null
            int dropRow = resultsTable.rowAtPoint(e.getPoint())
            
            // Check if dropped on a table row
            if (dropRow != -1 && dropRow != dragSourceRow) {
                targetNode = getNodeFromRow(dropRow)
            }
            
            // If not on table, get node from main map
            if (targetNode == null) {
                try {
                    def mapView = Controller.getCurrentController().getMapViewManager().getMapViewComponent()
                    if (mapView != null) {
                        Point mousePoint = e.getLocationOnScreen()
                        Point localPoint = new Point(mousePoint)
                        SwingUtilities.convertPointFromScreen(localPoint, mapView)
                        
                        // Method 1: getNodeViewAt
                        def nodeView = mapView.getNodeViewAt(localPoint)
                        if (nodeView != null) {
                            targetNode = nodeView.getNode()
                            println "✅ Node found (getNodeViewAt): ${targetNode.getPlainText()}"
                        }
                        
                        // Method 2: detectObject
                        if (targetNode == null) {
                            def detected = mapView.detectObject(localPoint)
                            if (detected instanceof org.freeplane.view.swing.map.NodeView) {
                                targetNode = ((org.freeplane.view.swing.map.NodeView) detected).getNode()
                                println "✅ Node found (detectObject): ${targetNode.getPlainText()}"
                            }
                        }
                        
                        // If still not found, show message
                        if (targetNode == null) {
                            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                                "⚠️ Drop on a node in the main map.", 
                                "Error", JOptionPane.WARNING_MESSAGE)
                            cleanupDrag()
                            return
                        }
                    }
                } catch (Exception ex) {
                    println "Error detecting node in main panel: ${ex.message}"
                    ex.printStackTrace()
                }
            }
            
            // If node found, apply tag
            if (targetNode != null) {
                def oldSelection = ScriptUtils.c().selecteds
                Node oldNode = (oldSelection && !oldSelection.isEmpty()) ? oldSelection[0] : null
                
                try {
                    // Select target node
                    ScriptUtils.c().select(targetNode)
                    
                    // Add tag
                    try {
                        targetNode.tags.add(draggedTag)
                    } catch (Exception ex1) {
                        try {
                            def controller = org.freeplane.features.tag.TagController.getController()
                            if (controller != null) {
                                controller.addTag(targetNode.getDelegate(), draggedTag)
                            } else {
                                targetNode.tags.add(draggedTag)
                            }
                        } catch (Exception ex2) {
                            targetNode.tags.add(draggedTag)
                        }
                    }
                    
                    // Restore previous selection
                    if (oldNode != null) {
                        ScriptUtils.c().select(oldNode)
                    }
                    
                    // Update table
                    if (dropRow != -1 && dropRow != dragSourceRow) {
                        int modelRow = resultsTable.convertRowIndexToModel(dropRow)
                        String tagsString = getSortedTagsString(targetNode)
                        tableModel.setValueAt(tagsString, modelRow, 6)
                        resultsTable.setRowSelectionInterval(dropRow, dropRow)
                        showNodeDetails(dropRow)
                        updateTableBreadcrumb(targetNode)
                    } else {
                        if (dragSourceRow != -1) {
                            int modelRow = resultsTable.convertRowIndexToModel(dragSourceRow)
                            Node sourceNode = getNodeFromRow(dragSourceRow)
                            if (sourceNode != null) {
                                String tagsString = getSortedTagsString(sourceNode)
                                tableModel.setValueAt(tagsString, modelRow, 6)
                            }
                        }
                        updateTableBreadcrumb(targetNode)
                        int selectedRow = resultsTable.getSelectedRow()
                        if (selectedRow != -1) {
                            showNodeDetails(selectedRow)
                        }
                    }
                    
                } catch (Exception ex) {
                    if (oldNode != null) {
                        ScriptUtils.c().select(oldNode)
                    }
                    JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                        "❌ Error: ${ex.message}", 
                        "Error", JOptionPane.ERROR_MESSAGE)
                    ex.printStackTrace()
                }
            }
            
            cleanupDrag()
        }
    }
    
    // ====== Update table breadcrumb ======
    private void updateTableBreadcrumb(Node node) {
        if (node == null) {
            breadcrumbPanel.removeAll()
            breadcrumbPanel.revalidate()
            breadcrumbPanel.repaint()
            return
        }
        
        breadcrumbPanel.removeAll()
        try {
            def fullPath = node.getPathToRoot()
            if (fullPath && !fullPath[0].isRoot()) {
                fullPath = fullPath.reverse()
            }
            def displayPath = fullPath.size() > 1 ? fullPath[0..-2] : []
            
            // If empty path (root node)
            if (displayPath.isEmpty()) {
                JPanel tempPanel = new JPanel()
                tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
                tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                
                JLabel rootLabel = new JLabel("📁 Root")
                if (fontBreadcrumb != null) {
                    rootLabel.setFont(fontBreadcrumb.deriveFont(Font.BOLD, fontBreadcrumb.getSize() + 2))
                } else {
                    rootLabel.setFont(new Font("Segoe UI", Font.BOLD, 14))
                }
                rootLabel.setForeground(new Color(0, 100, 200))
                rootLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5))
                tempPanel.add(rootLabel)
                
                breadcrumbPanel.add(tempPanel)
                breadcrumbPanel.revalidate()
                breadcrumbPanel.repaint()
                breadcrumbPanel.setVisible(true)
                return
            }
            
            // Apply visible root filter
            if (useVisibleRootOnly) {
                def viewRoot = getActiveViewRoot()
                if (viewRoot != null && node.mindMap == viewRoot.mindMap) {
                    int idx = fullPath.indexOf(viewRoot)
                    if (idx != -1) {
                        if (idx <= displayPath.size()) {
                            displayPath = displayPath[idx..-1]
                        } else {
                            displayPath = []
                        }
                    }
                }
            }
            
            if (displayPath.isEmpty()) {
                JPanel tempPanel = new JPanel()
                tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
                tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                
                JLabel rootLabel = new JLabel("📁 Root")
                if (fontBreadcrumb != null) {
                    rootLabel.setFont(fontBreadcrumb.deriveFont(Font.BOLD, fontBreadcrumb.getSize() + 2))
                } else {
                    rootLabel.setFont(new Font("Segoe UI", Font.BOLD, 14))
                }
                rootLabel.setForeground(new Color(0, 100, 200))
                rootLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5))
                tempPanel.add(rootLabel)
                
                breadcrumbPanel.add(tempPanel)
                breadcrumbPanel.revalidate()
                breadcrumbPanel.repaint()
                breadcrumbPanel.setVisible(true)
                return
            }
            
            // Normal breadcrumb display
            int maxNodes = 5
            int start = Math.max(0, displayPath.size() - maxNodes)
            JPanel tempPanel = new JPanel()
            tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
            tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            
            if (start > 0) {
                JLabel ellipsisLabel = new JLabel(" ... ")
                ellipsisLabel.setFont(ellipsisLabel.getFont().deriveFont(Font.BOLD))
                tempPanel.add(ellipsisLabel)
            }
            
            ButtonGroup bg = new ButtonGroup()
            for (int i = start; i < displayPath.size(); i++) {
                Node n = displayPath.get(i)
                String nodeText = n.getPlainText()
                String shortText = nodeText
                if (ancestorTrimLength > 0 && nodeText.length() > ancestorTrimLength) {
                    shortText = TextUtils.getShortText(nodeText, ancestorTrimLength, "\u2026")
                }
                JRadioButton btn = new JRadioButton(shortText)
                btn.setToolTipText(nodeText)
                if (fontBreadcrumb != null) btn.setFont(fontBreadcrumb)
                else btn.setFont(btn.getFont().deriveFont(Font.PLAIN))
                
                Color bgColor = getNodeBackgroundColor(n)
                if (bgColor != null) {
                    btn.setBackground(bgColor)
                    btn.setForeground(getForegroundForBackground(bgColor))
                    btn.setOpaque(true)
                    btn.setContentAreaFilled(true)
                } else {
                    btn.setOpaque(false)
                    btn.setForeground(UIManager.getColor("Label.foreground"))
                }
                Color borderColor = getBorderColorForNode(n)
                Border leftBorder = BorderFactory.createMatteBorder(0, 5, 0, 0, borderColor)
                Border padding = BorderFactory.createEmptyBorder(2, 6, 2, 6)
                btn.setBorder(BorderFactory.createCompoundBorder(leftBorder, padding))
                btn.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                btn.setHorizontalAlignment(SwingConstants.RIGHT)
                btn.setHorizontalTextPosition(SwingConstants.LEFT)
                btn.putClientProperty("node", n)
                
                final Node target = n
                final JRadioButton currentBtn = btn
                btn.addActionListener({ e ->
                    for (Component comp : tempPanel.getComponents()) {
                        if (comp instanceof JRadioButton) {
                            ((JRadioButton)comp).setForeground(UIManager.getColor("Label.foreground"))
                            ((JRadioButton)comp).setFont(((JRadioButton)comp).getFont().deriveFont(Font.PLAIN))
                            Node oldNode = (Node) ((JRadioButton)comp).getClientProperty("node")
                            if (oldNode != null) {
                                Color oldBg = getNodeBackgroundColor(oldNode)
                                if (oldBg != null) {
                                    ((JRadioButton)comp).setBackground(oldBg)
                                    ((JRadioButton)comp).setForeground(getForegroundForBackground(oldBg))
                                } else {
                                    ((JRadioButton)comp).setOpaque(false)
                                }
                            }
                        }
                    }
                    currentBtn.setForeground(Color.BLUE)
                    currentBtn.setFont(currentBtn.getFont().deriveFont(Font.BOLD))
                    try {
                        ScriptUtils.c().select(target)
                        def mapFile = target.getMindMap().getFile()
                        if (mapFile) {
                            def uri = mapFile.toURI().toString() + "#" + target.getId()
                            def link = new org.freeplane.core.util.Hyperlink(new URI(uri))
                            org.freeplane.features.url.UrlManager.getController().loadHyperlink(link)
                            SwingUtilities.invokeLater(new Runnable() {
                                void run() {
                                    if (resultsTable != null && resultsTable.isShowing()) {
                                        resultsTable.requestFocusInWindow()
                                    }
                                }
                            })
                        }
                    } catch (Exception ex) { ex.printStackTrace() }
                })
                tempPanel.add(btn)
                bg.add(btn)
            }
            
            for (Component comp : tempPanel.getComponents()) {
                if (comp instanceof JRadioButton) {
                    Node storedNode = (Node) ((JRadioButton)comp).getClientProperty("node")
                    if (storedNode == node) {
                        ((JRadioButton)comp).setSelected(true)
                        ((JRadioButton)comp).setForeground(Color.BLUE)
                        ((JRadioButton)comp).setFont(((JRadioButton)comp).getFont().deriveFont(Font.BOLD))
                        break
                    }
                }
            }
            breadcrumbPanel.add(tempPanel, BorderLayout.CENTER)
        } catch (Exception e) {
            // Fallback: show at least something
            JPanel tempPanel = new JPanel()
            tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
            tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            
            JLabel rootLabel = new JLabel("📁 " + node.getPlainText())
            if (fontBreadcrumb != null) {
                rootLabel.setFont(fontBreadcrumb)
            } else {
                rootLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12))
            }
            tempPanel.add(rootLabel)
            breadcrumbPanel.add(tempPanel)
            System.err.println("Breadcrumb error: ${e.message}")
        }
        breadcrumbPanel.revalidate()
        breadcrumbPanel.repaint()
        breadcrumbPanel.setVisible(true)
    }
    
    // ====== Setup tag drag and drop on results table ======
    private void setupTagDragAndDrop() {
        if (resultsTable == null) return
        
        resultsTable.addMouseListener(new MouseAdapter() {
            void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    int row = resultsTable.rowAtPoint(e.getPoint())
                    if (row != -1) {
                        int col = resultsTable.columnAtPoint(e.getPoint())
                        if (col == resultsTable.convertColumnIndexToView(6)) {
                            int modelRow = resultsTable.convertRowIndexToModel(row)
                            Object tagsValue = tableModel.getValueAt(modelRow, 6)
                            
                            if (tagsValue != null && tagsValue.toString().trim().length() > 0) {
                                String tagName = extractTagName(tagsValue.toString())
                                if (tagName != null && !isDragging) {
                                    addTagToSelectedNodes(tagName)
                                }
                            }
                        }
                    }
                }
            }
            
            void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int row = resultsTable.rowAtPoint(e.getPoint())
                    if (row != -1) {
                        int col = resultsTable.columnAtPoint(e.getPoint())
                        if (col == resultsTable.convertColumnIndexToView(6)) {
                            int modelRow = resultsTable.convertRowIndexToModel(row)
                            Object tagsValue = tableModel.getValueAt(modelRow, 6)
                            if (tagsValue != null && tagsValue.toString().trim().length() > 0) {
                                String tagName = extractTagName(tagsValue.toString())
                                if (tagName != null) {
                                    draggedTag = tagName
                                    dragSourceRow = row
                                    dragStartPoint = e.getPoint()
                                    resultsTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                                }
                            }
                        }
                    }
                }
            }
            
            void mouseReleased(MouseEvent e) {
                if (isDragging && draggedTag != null) {
                    Node targetNode = null
                    int dropRow = resultsTable.rowAtPoint(e.getPoint())
                    
                    if (dropRow != -1 && dropRow != dragSourceRow) {
                        targetNode = getNodeFromRow(dropRow)
                    }
                    
                    if (targetNode == null) {
                        def selectedNodes = ScriptUtils.c().selecteds
                        if (selectedNodes && !selectedNodes.isEmpty()) {
                            targetNode = selectedNodes[0]
                        }
                    }
                    
                    if (targetNode != null) {
                        def oldSelection = ScriptUtils.c().selecteds
                        Node oldNode = (oldSelection && !oldSelection.isEmpty()) ? oldSelection[0] : null
                        
                        try {
                            ScriptUtils.c().select(targetNode)
                            
                            // Proper method to add tag
                            try {
                                targetNode.tags.add(draggedTag)
                            } catch (Exception ex1) {
                                try {
                                    def controller = org.freeplane.features.tag.TagController.getController()
                                    if (controller != null) {
                                        controller.addTag(targetNode.getDelegate(), draggedTag)
                                    } else {
                                        targetNode.tags.add(draggedTag)
                                    }
                                } catch (Exception ex2) {
                                    targetNode.tags.add(draggedTag)
                                }
                            }
                            
                            if (oldNode != null) {
                                ScriptUtils.c().select(oldNode)
                            }
                            
                            if (dropRow != -1 && dropRow != dragSourceRow) {
                                int modelRow = resultsTable.convertRowIndexToModel(dropRow)
                                String tagsString = getSortedTagsString(targetNode)
                                tableModel.setValueAt(tagsString, modelRow, 6)
                                resultsTable.setRowSelectionInterval(dropRow, dropRow)
                                showNodeDetails(dropRow)
                                updateTableBreadcrumb(targetNode)
                            }
                            
                        } catch (Exception ex) {
                            if (oldNode != null) {
                                ScriptUtils.c().select(oldNode)
                            }
                            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                                "❌ Error: ${ex.message}", 
                                "Error", JOptionPane.ERROR_MESSAGE)
                            ex.printStackTrace()
                        }
                    } else {
                        JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                            "⚠️ No node found to add tag.", 
                            "Error", JOptionPane.WARNING_MESSAGE)
                    }
                    
                    cleanupDrag()
                }
            }
        })
        
        resultsTable.addMouseMotionListener(new MouseMotionAdapter() {
            void mouseDragged(MouseEvent e) {
                if (draggedTag != null && dragStartPoint != null) {
                    Point currentPoint = e.getPoint()
                    int distance = (int) dragStartPoint.distance(currentPoint)
                    
                    if (distance > 10 && !isDragging) {
                        startDrag(e)
                    }
                    
                    if (isDragging) {
                        updateDragGhost(e)
                        
                        def selectedNodes = ScriptUtils.c().selecteds
                        if (selectedNodes && !selectedNodes.isEmpty()) {
                            resultsTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                            if (dragSourceRow != -1) {
                                resultsTable.setRowSelectionInterval(dragSourceRow, dragSourceRow)
                            }
                        } else {
                            resultsTable.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))
                        }
                    }
                }
            }
            
            void mouseMoved(MouseEvent e) {
                int col = resultsTable.columnAtPoint(e.getPoint())
                if (col == resultsTable.convertColumnIndexToView(6)) {
                    int row = resultsTable.rowAtPoint(e.getPoint())
                    if (row != -1) {
                        int modelRow = resultsTable.convertRowIndexToModel(row)
                        Object tagsValue = tableModel.getValueAt(modelRow, 6)
                        if (tagsValue != null && tagsValue.toString().trim().length() > 0) {
                            resultsTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                            return
                        }
                    }
                }
                resultsTable.setCursor(Cursor.getDefaultCursor())
            }
        })
    }
    
    // ====== Add tag to selected nodes ======
    private void addTagToSelectedNodes(String tagName) {
        if (!tagName || tagName.isEmpty()) return
        
        def selectedNodes = ScriptUtils.c().selecteds
        if (!selectedNodes || selectedNodes.isEmpty()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "⚠️ No node selected in the main map.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        int count = 0
        for (def targetNode in selectedNodes) {
            try {
                def controller = org.freeplane.features.tag.TagController.getController()
                if (controller) {
                    controller.addTag(targetNode.getDelegate(), tagName)
                    count++
                } else {
                    targetNode.tags.add(tagName)
                    count++
                }
            } catch (Exception ex) {
                try {
                    targetNode.tags.add(tagName)
                    count++
                } catch (Exception ex2) {}
            }
        }
        
        if (count > 0) {
            if (dragSourceRow != -1) {
                int modelRow = resultsTable.convertRowIndexToModel(dragSourceRow)
                Node sourceNode = getNodeFromRow(dragSourceRow)
                if (sourceNode != null) {
                    String tagsString = getSortedTagsString(sourceNode)
                    tableModel.setValueAt(tagsString, modelRow, 6)
                }
            }
            
            int selectedRow = resultsTable.getSelectedRow()
            if (selectedRow != -1) {
                showNodeDetails(selectedRow)
            }
        }
    }
    
    // ====== Start drag operation ======
    private void startDrag(MouseEvent e) {
        isDragging = true
        resultsTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        
        dragGhostWindow = new JWindow()
        dragGhostWindow.setAlwaysOnTop(true)
        dragGhostWindow.setBackground(new Color(0, 0, 0, 0))
        
        JPanel ghostPanel = new JPanel()
        ghostPanel.setBackground(new Color(255, 200, 0, 220))
        ghostPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.ORANGE, 2),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ))
        ghostPanel.setOpaque(true)
        
        JLabel ghostLabel = new JLabel("🏷️ " + draggedTag)
        ghostLabel.setFont(new Font("Segoe UI", Font.BOLD, 14))
        ghostLabel.setForeground(Color.BLACK)
        ghostPanel.add(ghostLabel)
        
        dragGhostWindow.add(ghostPanel)
        dragGhostWindow.pack()
        
        Point mouseLoc = MouseInfo.getPointerInfo().getLocation()
        dragGhostWindow.setLocation(mouseLoc.x - 10, mouseLoc.y - 10)
        dragGhostWindow.setVisible(true)
    }
    
    // ====== Update drag ghost position ======
    private void updateDragGhost(MouseEvent e) {
        if (dragGhostWindow != null && dragGhostWindow.isVisible()) {
            Point mouseLoc = MouseInfo.getPointerInfo().getLocation()
            dragGhostWindow.setLocation(mouseLoc.x - 10, mouseLoc.y - 10)
        }
    }
    
    // ====== Clean up drag state ======
    private void cleanupDrag() {
        isDragging = false
        draggedTag = null
        dragStartPoint = null
        dragSourceRow = -1
        resultsTable.setCursor(Cursor.getDefaultCursor())
        
        if (dragGhostWindow != null) {
            dragGhostWindow.dispose()
            dragGhostWindow = null
        }
    }
    
    // ====== Get node from table row ======
    private Node getNodeFromRow(int viewRow) {
        if (viewRow == -1) return null
        int modelRow = resultsTable.convertRowIndexToModel(viewRow)
        Object value = tableModel.getValueAt(modelRow, 9)
        return (value instanceof Object[]) ? ((Object[])value)[0] as Node : 
               (value instanceof Node ? value as Node : null)
    }
    
    // ====== Extract tag name from tags string ======
    private String extractTagName(String tagsString) {
        if (tagsString == null || tagsString.trim().isEmpty()) return null
        
        def tagPattern = ~/TagIcon\s*\[tag=([^,\]]+)[,\]]/
        def matcher = (tagsString =~ tagPattern)
        
        if (matcher.find()) {
            return matcher.group(1).trim()
        }
        
        if (!tagsString.contains("TagIcon") && !tagsString.contains("font=")) {
            def parts = tagsString.split("\\s*,\\s*")
            if (parts.length > 0) {
                return parts[0].trim()
            }
        }
        return null
    }
    
    // ====== Update tag column ======
    private void updateTagColumn(int row) {
        int modelRow = resultsTable.convertRowIndexToModel(row)
        Object value = tableModel.getValueAt(modelRow, 9)
        Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : 
                    (value instanceof Node ? value as Node : null)
        
        if (node != null) {
            String tagsString = getSortedTagsString(node)
            tableModel.setValueAt(tagsString, modelRow, 6)
            resultsTable.repaint()
        }
    }
    
    // ====== Highlight drop target row ======
    private void highlightDropTarget(int row) {
        clearDropHighlight()
        resultsTable.setRowSelectionInterval(row, row)
        resultsTable.setSelectionBackground(new Color(100, 255, 100, 150))
        resultsTable.repaint()
    }
    
    // ====== Clear drop highlight ======
    private void clearDropHighlight() {
        resultsTable.setSelectionBackground(new Color(200, 255, 200))
        int selectedRow = resultsTable.getSelectedRow()
        if (selectedRow != -1) {
            resultsTable.setRowSelectionInterval(selectedRow, selectedRow)
        }
    }
    
    // ========== Copy and paste tag ==========
    private void copyTagFromSelectedRow() {
        int selectedRow = resultsTable.getSelectedRow()
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "Please select a row in the results table first.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        int modelRow = resultsTable.convertRowIndexToModel(selectedRow)
        Object tagsValue = tableModel.getValueAt(modelRow, 6)  // Tags column
        
        if (tagsValue == null || tagsValue.toString().trim().length() == 0) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "⚠️ This node has no tags!", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        String tagsString = tagsValue.toString().trim()
        def tagPattern = ~/TagIcon\s*\[tag=([^,\]]+)[,\]]/
        def matcher = (tagsString =~ tagPattern)
        
        if (matcher.find()) {
            copiedTag = matcher.group(1).trim()
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "✅ Tag \"${copiedTag}\" copied.", 
                "Success", JOptionPane.INFORMATION_MESSAGE)
        } else {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "⚠️ No tag found in this row.", 
                "Error", JOptionPane.WARNING_MESSAGE)
        }
    }
    
    // ====== Paste tag to selected rows ======
    private void pasteTagToSelectedRows() {
        if (copiedTag == null || copiedTag.isEmpty()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "⚠️ No tag has been copied.\nPlease copy a tag first.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        int[] selectedRows = resultsTable.getSelectedRows()
        if (selectedRows == null || selectedRows.length == 0) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "Please select at least one row in the results table.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        int count = 0
        for (int viewRow : selectedRows) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow)
            Object value = tableModel.getValueAt(modelRow, 9)
            Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
            if (node != null) {
                try {
                    def controller = org.freeplane.features.tag.TagController.getController()
                    if (controller) {
                        controller.addTag(node.getDelegate(), copiedTag)
                        count++
                    } else {
                        try {
                            node.tags.add(copiedTag)
                            count++
                        } catch (Exception e2) {
                            System.err.println("Error tagging node: ${node.getText()}")
                        }
                    }
                } catch (Exception e) {
                    try {
                        node.tags.add(copiedTag)
                        count++
                    } catch (Exception e2) {
                        System.err.println("Error tagging node: ${node.getText()}")
                    }
                }
            }
        }
    }
    
    // ========== Copy/paste tag variables ==========
    private String copiedTag = null
    private KeyStroke copyTagShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)
    private KeyStroke pasteTagShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)
    
    // ====== Helper: Add tag to node ======
    private void addTagToNode(Node targetNode, String tagName) {
        if (targetNode == null || tagName == null || tagName.isEmpty()) return
        
        try {
            def controller = org.freeplane.features.tag.TagController.getController()
            if (controller) {
                controller.addTag(targetNode.getDelegate(), tagName)
            } else {
                try {
                    targetNode.tags.add(tagName)
                } catch (Exception e2) {
                    System.err.println("Error tagging node: ${targetNode.getText()}")
                }
            }
        } catch (Exception e) {
            try {
                targetNode.tags.add(tagName)
            } catch (Exception e2) {
                System.err.println("Error tagging node: ${targetNode.getText()}")
            }
        }
    }
    
    // ========== Manage map node row in table ==========
    private void addMapNodeRow(Node node) {
        if (node == null) {
            removeMapNodeRow()
            return
        }
        
        if (mapNodeRowAdded) {
            updateMapNodeRow(node)
            return
        }
        
        // Get node info
        String fileName = node.mindMap.file?.name ?: "Unnamed"
        String styleName = node.style?.name ?: "(no style)"
        String pathStr = getAncestorsPathCached(node)
        String modifiedDate = node.getLastModifiedAt() ? dateFormat.format(node.getLastModifiedAt()) : ""
        String createdDate = node.getCreatedAt() ? dateFormat.format(node.getCreatedAt()) : ""
        String detailsText = node.details?.plain ?: ""
        String noteText = node.note?.plain ?: ""
        String tagsString = getSortedTagsString(node)
        
        // Insert at top (index 0)
        tableModel.insertRow(0, [fileName, styleName, pathStr, modifiedDate, createdDate, "", tagsString, detailsText, noteText, [node] as Object[]] as Object[])
        mapNodeRowAdded = true
        mapNodeRowIndex = 0
        
        // Highlight row
        resultsTable.setRowSelectionInterval(0, 0)
        resultsTable.repaint()
    }
    
    private void updateMapNodeRow(Node node) {
        if (!mapNodeRowAdded || mapNodeRowIndex == -1) {
            addMapNodeRow(node)
            return
        }
        
        // Update row info
        String fileName = node.mindMap.file?.name ?: "Unnamed"
        String styleName = node.style?.name ?: "(no style)"
        String pathStr = getAncestorsPathCached(node)
        String modifiedDate = node.getLastModifiedAt() ? dateFormat.format(node.getLastModifiedAt()) : ""
        String createdDate = node.getCreatedAt() ? dateFormat.format(node.getCreatedAt()) : ""
        String detailsText = node.details?.plain ?: ""
        String noteText = node.note?.plain ?: ""
        String tagsString = getSortedTagsString(node)
        
        tableModel.setValueAt(fileName, mapNodeRowIndex, 0)
        tableModel.setValueAt(styleName, mapNodeRowIndex, 1)
        tableModel.setValueAt(pathStr, mapNodeRowIndex, 2)
        tableModel.setValueAt(modifiedDate, mapNodeRowIndex, 3)
        tableModel.setValueAt(createdDate, mapNodeRowIndex, 4)
        tableModel.setValueAt("", mapNodeRowIndex, 5) // Icons
        tableModel.setValueAt(tagsString, mapNodeRowIndex, 6)
        tableModel.setValueAt(detailsText, mapNodeRowIndex, 7)
        tableModel.setValueAt(noteText, mapNodeRowIndex, 8)
        tableModel.setValueAt([node] as Object[], mapNodeRowIndex, 9)
        
        // Highlight row
        resultsTable.setRowSelectionInterval(mapNodeRowIndex, mapNodeRowIndex)
        resultsTable.repaint()
    }
    
    private void removeMapNodeRow() {
        if (!mapNodeRowAdded || mapNodeRowIndex == -1) {
            return
        }
        
        try {
            if (mapNodeRowIndex < tableModel.getRowCount()) {
                tableModel.removeRow(mapNodeRowIndex)
            }
        } catch (Exception e) {
            // Ignore
        }
        
        mapNodeRowAdded = false
        mapNodeRowIndex = -1
    }
    
    // ====== Add tag from selected row ======
    private void addTagFromSelectedRow() {
        int selectedRow = resultsTable.getSelectedRow()
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "Please select a row in the results table first.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        int modelRow = resultsTable.convertRowIndexToModel(selectedRow)
        
        // Get tag from Tags column (index 6)
        Object tagsValue = tableModel.getValueAt(modelRow, 6)
        String tagToAdd = null
        List<String> existingTags = []
        
        if (tagsValue != null && tagsValue.toString().trim().length() > 0) {
            String tagsString = tagsValue.toString().trim()
            
            // Extract with regex
            def tagPattern = ~/TagIcon\s*\[tag=([^,\]]+)[,\]]/
            def matcher = (tagsString =~ tagPattern)
            
            while (matcher.find()) {
                String tagName = matcher.group(1).trim()
                if (tagName && !tagName.isEmpty()) {
                    existingTags.add(tagName)
                }
            }
            
            // Fallback: simple text
            if (existingTags.isEmpty()) {
                if (!tagsString.contains("TagIcon") && !tagsString.contains("font=")) {
                    existingTags = tagsString.split("\\s*,\\s*").collect { it.trim() }.findAll { !it.isEmpty() }
                }
            }
        }
        
        if (existingTags.isEmpty()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "⚠️ This node has no tags in the Tags column!\nPlease select a node with tags.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        // If exactly one tag, use it directly
        if (existingTags.size() == 1) {
            tagToAdd = existingTags[0]
        } else {
            // Ask user which tag to use
            String[] options = existingTags.toArray(new String[0])
            String selectedTag = (String) JOptionPane.showInputDialog(
                UITools.getCurrentFrame(),
                "This node has ${existingTags.size()} tags:\nWhich tag should be added to selected nodes?",
                "Select Tag",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
            )
            
            if (selectedTag == null) {
                return // User cancelled
            }
            tagToAdd = selectedTag
        }
        
        if (!tagToAdd || tagToAdd.isEmpty()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "No tag selected.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        // Get selected nodes in main map
        def selectedNodes = ScriptUtils.c().selecteds
        if (!selectedNodes || selectedNodes.isEmpty()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), 
                "No node selected in the main map.\nPlease select at least one node in the main map.", 
                "Error", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        // Apply tag to selected nodes
        int count = 0
        for (def targetNode in selectedNodes) {
            try {
                def controller = org.freeplane.features.tag.TagController.getController()
                if (controller) {
                    controller.addTag(targetNode.getDelegate(), tagToAdd)
                    count++
                } else {
                    try {
                        targetNode.tags.add(tagToAdd)
                        count++
                    } catch (Exception e2) {
                        System.err.println("Error tagging node: ${targetNode.getText()}")
                    }
                }
            } catch (Exception e) {
                try {
                    targetNode.tags.add(tagToAdd)
                    count++
                } catch (Exception e2) {
                    System.err.println("Error tagging node: ${targetNode.getText()}")
                }
            }
        }
    }
    
    // ====== Setup tag keyboard shortcuts ======
    private void setupTagShortcut() {
        if (resultsTable == null) return
        
        // F8 shortcut on table
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(tagShortcut, "addTagFromRow")
        resultsTable.getActionMap().put("addTagFromRow", new AbstractAction() {
            void actionPerformed(ActionEvent e) {
                addTagFromSelectedRow()
            }
        })
        
        // Copy tag shortcut (Ctrl+Shift+C)
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(copyTagShortcut, "copyTag")
        resultsTable.getActionMap().put("copyTag", new AbstractAction() {
            void actionPerformed(ActionEvent e) {
                copyTagFromSelectedRow()
            }
        })
        
        // Paste tag shortcut (Ctrl+Shift+V)
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(pasteTagShortcut, "pasteTag")
        resultsTable.getActionMap().put("pasteTag", new AbstractAction() {
            void actionPerformed(ActionEvent e) {
                pasteTagToSelectedRows()
            }
        })
        
        // Global shortcuts
        if (currentSplitPane != null) {
            currentSplitPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(tagShortcut, "addTagFromRowGlobal")
            currentSplitPane.getActionMap().put("addTagFromRowGlobal", new AbstractAction() {
                void actionPerformed(ActionEvent e) {
                    addTagFromSelectedRow()
                }
            })
            
            currentSplitPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(copyTagShortcut, "copyTagGlobal")
            currentSplitPane.getActionMap().put("copyTagGlobal", new AbstractAction() {
                void actionPerformed(ActionEvent e) {
                    copyTagFromSelectedRow()
                }
            })
            
            currentSplitPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(pasteTagShortcut, "pasteTagGlobal")
            currentSplitPane.getActionMap().put("pasteTagGlobal", new AbstractAction() {
                void actionPerformed(ActionEvent e) {
                    pasteTagToSelectedRows()
                }
            })
        }
    }
    
    // ========== Column management ==========
    private List<String> getDefaultFullColumnOrder() {
        return ["File", "Style", "Ancestors", "Date Modified", "Date Created",
                "Icons", "Tags", "Details", "Note", "Node"]
    }
    
    private void saveFullTableState() {
        if (resultsTable == null) return
        TableColumnModel colModel = resultsTable.getColumnModel()
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            TableColumn col = colModel.getColumn(i)
            String header = (String) col.getHeaderValue()
            int width = col.getWidth()
            if (width > 0) {
                storedColumnWidths.put(header, width)
            }
        }
        def rc = ResourceController.getResourceController()
        StringBuilder widthsStr = new StringBuilder()
        storedColumnWidths.each { header, width ->
            if (widthsStr.length() > 0) widthsStr.append("|")
            widthsStr.append("${header}=${width}")
        }
        rc.setProperty("mapcrawler.storedColumnWidths", widthsStr.toString())
    }

    private void loadFullTableState() {
        def rc = ResourceController.getResourceController()
        String storedWidths = rc.getProperty("mapcrawler.storedColumnWidths")
        if (storedWidths) {
            storedColumnWidths.clear()
            storedWidths.split("\\|").each { pair ->
                def parts = pair.split("=")
                if (parts.length == 2) {
                    storedColumnWidths[parts[0]] = parts[1].toInteger()
                }
            }
        }
        loadColumnWidths()
        applySettings()
    }
    
    private void saveColumnOrder() {
        if (resultsTable == null) return
        def rc = ResourceController.getResourceController()
        TableColumnModel colModel = resultsTable.getColumnModel()
        List<String> visibleOrder = []
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            visibleOrder << (String) colModel.getColumn(i).getHeaderValue()
        }
        String savedFullOrderStr = rc.getProperty("mapcrawler.fullColumnOrder")
        List<String> lastFullOrder = getDefaultFullColumnOrder()
        if (savedFullOrderStr != null && !savedFullOrderStr.isEmpty()) {
            lastFullOrder = savedFullOrderStr.split(",") as List
        }
        List<String> newFullOrder = []
        Set<String> allColumns = getDefaultFullColumnOrder() as Set
        Set<String> hiddenColumns = allColumns - visibleOrder
        newFullOrder.addAll(visibleOrder)
        for (String col in lastFullOrder) {
            if (hiddenColumns.contains(col) && !newFullOrder.contains(col)) {
                int insertPos = newFullOrder.indexOf(col)
                if (insertPos == -1) {
                    newFullOrder << col
                } else {
                    newFullOrder.add(insertPos, col)
                }
            }
        }
        String fullOrderStr = newFullOrder.join(",")
        rc.setProperty("mapcrawler.fullColumnOrder", fullOrderStr)
    }

    private void loadColumnOrder() {
        if (resultsTable == null) return
        def rc = ResourceController.getResourceController()
        String fullOrderStr = rc.getProperty("mapcrawler.fullColumnOrder")
        if (fullOrderStr == null || fullOrderStr.isEmpty()) return
        List<String> desiredOrder = fullOrderStr.split(",") as List
        TableColumnModel colModel = resultsTable.getColumnModel()
        List<String> currentVisible = []
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            currentVisible << (String) colModel.getColumn(i).getHeaderValue()
        }
        int nextPos = 0
        for (String colName in desiredOrder) {
            if (currentVisible.contains(colName)) {
                for (int i = nextPos; i < colModel.getColumnCount(); i++) {
                    if (colModel.getColumn(i).getHeaderValue() == colName) {
                        if (i != nextPos) {
                            colModel.moveColumn(i, nextPos)
                        }
                        nextPos++
                        break
                    }
                }
            }
        }
    }

    private void adjustLastColumnWidth() {
        if (resultsTable == null || tableScroll == null) return
        JViewport viewport = tableScroll.getViewport()
        if (viewport == null) return
        int viewWidth = viewport.getWidth()
        if (viewWidth <= 0) return
        TableColumnModel colModel = resultsTable.getColumnModel()
        int lastColIndex = colModel.getColumnCount() - 1
        int otherColumnsWidth = 0
        for (int i = 0; i < lastColIndex; i++) {
            otherColumnsWidth += colModel.getColumn(i).getWidth()
        }
        if (otherColumnsWidth >= viewWidth) {
            return
        }
        int remaining = viewWidth - otherColumnsWidth
        int minWidth = colModel.getColumn(lastColIndex).getMinWidth()
        int newWidth = Math.max(minWidth, remaining)
        if (newWidth != colModel.getColumn(lastColIndex).getWidth()) {
            colModel.getColumn(lastColIndex).setWidth(newWidth)
            colModel.getColumn(lastColIndex).setPreferredWidth(newWidth)
            updateAllRowHeights()
            resultsTable.revalidate()
            resultsTable.repaint()
        }
    }
    
    private void fixViewportToRight() {
        if (tableScroll == null) return
        JViewport viewport = tableScroll.getViewport()
        if (viewport == null) return
        viewport.setViewPosition(new Point(0, 0))
    }
    
    private void initColumnWidths() {
        defaultColumnWidths.clear()
        currentColumnWidths.clear()
        totalDefaultWidth = 0
        TableColumnModel colModel = resultsTable.getColumnModel()
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            int w = colModel.getColumn(i).getWidth()
            defaultColumnWidths.add(w)
            currentColumnWidths.add(w)
            totalDefaultWidth += w
        }
    }

    private void adjustColumnsForAvailableWidth(int availableWidth) {
        if (adjusting || defaultColumnWidths.isEmpty()) return
        adjusting = true
        try {
            if (availableWidth >= totalDefaultWidth) {
                TableColumnModel colModel = resultsTable.getColumnModel()
                for (int i = 0; i < colModel.getColumnCount(); i++) {
                    int w = defaultColumnWidths.get(i)
                    colModel.getColumn(i).setWidth(w)
                    colModel.getColumn(i).setPreferredWidth(w)
                    currentColumnWidths.set(i, w)
                }
            } else {
                int shortage = totalDefaultWidth - availableWidth
                TableColumnModel colModel = resultsTable.getColumnModel()
                for (int i = 0; i < colModel.getColumnCount() && shortage > 0; i++) {
                    int current = currentColumnWidths.get(i)
                    int minWidth = colModel.getColumn(i).getMinWidth()
                    int canReduce = current - minWidth
                    if (canReduce > 0) {
                        int reduce = Math.min(shortage, canReduce)
                        int newWidth = current - reduce
                        colModel.getColumn(i).setWidth(newWidth)
                        colModel.getColumn(i).setPreferredWidth(newWidth)
                        currentColumnWidths.set(i, newWidth)
                        shortage -= reduce
                    }
                }
            }
            resultsTable.revalidate()
            resultsTable.repaint()
        } finally {
            adjusting = false
        }
    }

    private void updateBaseLineHeight() {
        if (resultsTable == null) return
        Font nodeFont = (fontWeightColumn != null) ? fontWeightColumn : resultsTable.getFont()
        FontMetrics fm = resultsTable.getFontMetrics(nodeFont)
        baseLineHeight = fm.getHeight() + 4
    }

    private int computeNodeLineCount(Node node, int columnWidth) {
        if (singleLineMode) return 1
        if (columnWidth <= 10) return 1
        String rawText = node.getHtmlText() ?: node.getPlainText()
        if (rawText == null || rawText.isEmpty()) return 1
        String plainText = rawText.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim()
        if (trimMode && !fullMode && plainText.length() > trimLength) {
            plainText = plainText.substring(0, trimLength) + "\u2026"
        }
        Font nodeFont = (fontWeightColumn != null) ? fontWeightColumn : resultsTable.getFont()
        FontMetrics fm = resultsTable.getFontMetrics(nodeFont)
        int availableWidth = columnWidth - 16
        if (availableWidth <= 0) return 1
        String[] words = plainText.split("(?<=\\s+)|(?=\\s+)")
        int lines = 1
        int currentWidth = 0
        for (String w : words) {
            int ww = fm.stringWidth(w)
            if (currentWidth + ww > availableWidth) {
                lines++
                currentWidth = ww
            } else {
                currentWidth += ww
            }
        }
        return lines
    }
    
    private void updateAllRowHeights() {
        if (resultsTable == null) return
        if (singleLineMode) {
            resultsTable.setRowHeight(baseLineHeight)
            return
        }
        int nodeColView = resultsTable.convertColumnIndexToView(9)
        if (nodeColView == -1) return
        int nodeColWidth = resultsTable.getColumnModel().getColumn(nodeColView).getWidth()
        for (int row = 0; row < resultsTable.getRowCount(); row++) {
            int modelRow = resultsTable.convertRowIndexToModel(row)
            Object nodeValue = tableModel.getValueAt(modelRow, 9)
            Node node = (nodeValue instanceof Object[]) ? ((Object[])nodeValue)[0] as Node : nodeValue as Node
            if (node == null) continue
            int lines = computeNodeLineCount(node, nodeColWidth)
            int newHeight = baseLineHeight * lines
            if (resultsTable.getRowHeight(row) != newHeight) {
                resultsTable.setRowHeight(row, newHeight)
            }
        }
    }
    
    private String getStyledNodeColumnContentSingleLine(Node node, Font font, int columnWidth, boolean applyHighlight) {
        Color fgColor = getNodeForegroundColor(node)
        String colorStyle = (fgColor != null) ? "color: rgb(${fgColor.red}, ${fgColor.green}, ${fgColor.blue});" : ""
        String rawHtml = node.getHtmlText()
        if (rawHtml == null || !rawHtml.trim().startsWith("<")) {
            String plainText = node.getPlainText() ?: ""
            if (plainText.length() > trimLength)
                plainText = TextUtils.getShortText(plainText, trimLength, "\u2026")
            String escaped = escapeHtml(plainText).replace("\n", "<br>")
            String highlightWord = null
            if (applyHighlight) {
                if (currentFilterText != null && !currentFilterText.isEmpty())
                    highlightWord = currentFilterText
                else if (lastSearchKeyword != null && !lastSearchKeyword.isEmpty())
                    highlightWord = lastSearchKeyword
            }
            if (highlightWord != null && !highlightWord.isEmpty()) {
                String patternStr = Pattern.quote(highlightWord)
                try {
                    int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE
                    Pattern p = Pattern.compile("($patternStr)", flags)
                    Matcher m = p.matcher(escaped)
                    StringBuffer sb = new StringBuffer()
                    while (m.find())
                        m.appendReplacement(sb, "<span style='background-color: yellow;'>\$1</span>")
                    m.appendTail(sb)
                    escaped = sb.toString()
                } catch (Exception e) {}
            }
            String family = font.getFamily()
            int size = font.getSize()
            String weight = font.isBold() ? "bold" : "normal"
            String styleFlag = font.isItalic() ? "italic" : "normal"
            String wrapperStyle = "font-family: ${family}; font-size: ${size}pt; font-weight: ${weight}; font-style: ${styleFlag}; ${colorStyle} white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: ${columnWidth - 10}px; direction: rtl; text-align: right; margin:0; padding:0;"
            return "<div style=\"${wrapperStyle}\">${escaped}</div>"
        }
        String html = rawHtml.replaceAll("(?i)<\\/?(html|head|body)[^>]*>", "")
        html = html.replaceAll("(?i)<style[^>]*>.*?<\\/style>", "")
        html = html.replaceAll(/(?i)font-size\s*:\s*[^;]+;?/, "")
        html = html.replaceAll(/(?i)font-family\s*:\s*[^;]+;?/, "")
        html = html.replaceAll(/(?i)line-height\s*:\s*[^;]+;?/, "")
        html = html.replaceAll(/;\s*;/, ";")
        html = html.replaceAll(/;\s*}/, "}")
        html = html.replaceAll(/style\s*=\s*["']\s*["']/, "")
        html = html.replaceAll("(?i)<\\/?font[^>]*>", "")
        html = html.replaceAll("(?i)\\s+size\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
        html = html.replaceAll("(?i)\\s+face\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
        if (html.trim().isEmpty()) html = node.getPlainText()
        String family = font.getFamily()
        int size = font.getSize()
        String weight = font.isBold() ? "bold" : "normal"
        String styleFlag = font.isItalic() ? "italic" : "normal"
        String wrapperStyle = "font-family: ${family}; font-size: ${size}pt; font-weight: ${weight}; font-style: ${styleFlag}; ${colorStyle} white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: ${columnWidth - 10}px; direction: rtl; text-align: right; margin:0; padding:0;"
        return "<div style=\"${wrapperStyle}\">${html}</div>"
    }

    private String getStyledDetailsContent(Node node, Font font) {
        return getStyledTextContent(node.getDetails()?.getHtml() ?: node.getDetails()?.getPlain() ?: "", font, node)
    }

    private String getStyledNoteContent(Node node, Font font) {
        return getStyledTextContent(node.getNote()?.getHtml() ?: node.getNote()?.getPlain() ?: "", font, node)
    }

    private Color getNodeForegroundColor(Node node) {
        if (node == null) return null
        try {
            NodeModel nodeModel = getNodeModel(node)
            return NodeStyleController.getController().getColor(nodeModel, StyleOption.FOR_UNSELECTED_NODE)
        } catch (Exception e) {
            return node.getStyle()?.getForegroundColor() ?: UIManager.getColor("Label.foreground")
        }
    }

    private String getStyledNodeColumnContent(Node node, Font font, boolean applyHighlight) {
        String highlightWord = null
        if (applyHighlight) {
            if (currentFilterText != null && !currentFilterText.isEmpty())
                highlightWord = currentFilterText
            else if (lastSearchKeyword != null && !lastSearchKeyword.isEmpty())
                highlightWord = lastSearchKeyword
        }
        String htmlContent = node.getHtmlText()
        if (htmlContent && htmlContent.trim().startsWith("<")) {
            htmlContent = htmlContent.replaceAll("(?i)<\\/?(html|head|body)[^>]*>", "")
            htmlContent = htmlContent.replaceAll("(?i)<style[^>]*>.*?<\\/style>", "")
            htmlContent = htmlContent.replaceAll(/(?i)font-size\s*:\s*[^;]+;?/, "")
            htmlContent = htmlContent.replaceAll(/(?i)font-family\s*:\s*[^;]+;?/, "")
            htmlContent = htmlContent.replaceAll(/(?i)line-height\s*:\s*[^;]+;?/, "")
            htmlContent = htmlContent.replaceAll(/;\s*;/, ";")
            htmlContent = htmlContent.replaceAll(/;\s*}/, "}")
            htmlContent = htmlContent.replaceAll(/style\s*=\s*["']\s*["']/, "")
            htmlContent = htmlContent.replaceAll("(?i)<\\/?font[^>]*>", "")
            htmlContent = htmlContent.replaceAll("(?i)\\s+size\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
            htmlContent = htmlContent.replaceAll("(?i)\\s+face\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
            if (htmlContent.trim().isEmpty()) htmlContent = node.getPlainText()
            String family = font.getFamily()
            int size = font.getSize()
            String weight = font.isBold() ? "bold" : "normal"
            String styleFlag = font.isItalic() ? "italic" : "normal"
            String divStyle = "font-family: ${family}; font-size: ${size}pt; font-weight: ${weight}; font-style: ${styleFlag}; margin:0; padding:0;"
            return "<div style=\"${divStyle}\">${htmlContent}</div>"
        } else {
            String plainText = node.getPlainText() ?: ""
            if (trimMode && plainText.length() > trimLength) {
                plainText = TextUtils.getShortText(plainText, trimLength, "\u2026")
            }
            String escaped = escapeHtml(plainText).replace("\n", "<br>")
            if (highlightWord != null && !highlightWord.isEmpty()) {
                String patternStr = Pattern.quote(highlightWord)
                try {
                    int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE
                    Pattern p = Pattern.compile("($patternStr)", flags)
                    Matcher m = p.matcher(escaped)
                    StringBuffer sb = new StringBuffer()
                    while (m.find())
                        m.appendReplacement(sb, "<span style='background-color: yellow;'>\$1</span>")
                    m.appendTail(sb)
                    escaped = sb.toString()
                } catch (Exception e) {}
            }
            String family = font.getFamily()
            int size = font.getSize()
            String weight = font.isBold() ? "bold" : "normal"
            String styleFlag = font.isItalic() ? "italic" : "normal"
            String divStyle = "font-family: ${family}; font-size: ${size}pt; font-weight: ${weight}; font-style: ${styleFlag}; margin:0; padding:0;"
            return "<div style=\"${divStyle}\">${escaped}</div>"
        }
    }

    private Color getBorderColorForNode(Node node) {
        if (node?.mindMap?.file) {
            String path = node.mindMap.file.absolutePath
            if (PATH_COLORS.containsKey(path))
                return PATH_COLORS.get(path)
        }
        return Color.GRAY
    }

    private Color getNodeBackgroundColor(Node node) {
        if (node == null) return null
        try {
            NodeModel nodeModel = getNodeModel(node)
            return NodeStyleController.getController().getBackgroundColor(nodeModel, StyleOption.FOR_UNSELECTED_NODE)
        } catch (Exception e) {
            return node.backgroundColor
        }
    }

    private Color getForegroundForBackground(Color bg) {
        if (bg == null) return Color.BLACK
        int brightness = (int)(0.299*bg.getRed() + 0.587*bg.getGreen() + 0.114*bg.getBlue())
        return brightness > 128 ? Color.BLACK : Color.WHITE
    }

    private void deleteSelectedNode() {
        int selectedRow = resultsTable.getSelectedRow()
        if (selectedRow == -1) return
        int modelRow = resultsTable.convertRowIndexToModel(selectedRow)
        Object value = tableModel.getValueAt(modelRow, 9)
        Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
        if (node == null) return
        if (node.isRoot()) {
            JOptionPane.showMessageDialog(
                UITools.getCurrentFrame(),
                "Cannot delete the root node of the map.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        Node viewRoot = ScriptUtils.c().viewRoot
        if (viewRoot != null && node.getId() == viewRoot.getId()) {
            JOptionPane.showMessageDialog(
                UITools.getCurrentFrame(),
                "Cannot delete the View Root node.\nPlease double-click another node first to change the root.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        try {
            SwingUtilities.invokeLater(new Runnable() {
                void run() {
                    node.delete()
                }
            })
            SwingUtilities.invokeLater(new Runnable() {
                void run() {
                    tableModel.removeRow(modelRow)
                    updateResultCount()
                    if (tableModel.getRowCount() == 0) {
                        clearPreview()
                    } else {
                        int newRow = Math.min(modelRow, tableModel.getRowCount() - 1)
                        if (newRow >= 0) {
                            int viewRow = resultsTable.convertRowIndexToView(newRow)
                            resultsTable.setRowSelectionInterval(viewRow, viewRow)
                            showNodeDetails(viewRow)
                        }
                    }
                    pathCache.clear()
                    rowHeightCache.clear()
                }
            })
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                UITools.getCurrentFrame(),
                "Error deleting node: ${e.message}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            e.printStackTrace()
        }
    }

    private void copySelectedNodesDeep() {
        List<Node> selectedNodes = getSelectedNodesFromTable()
        if (selectedNodes.isEmpty()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No node selected.")
            return
        }
        List<NodeModel> nodeModels = selectedNodes.collect { getNodeModel(it) }
        MapClipboardController clipboardController = MapClipboardController.getController()
        if (clipboardController == null) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Error accessing clipboard.")
            return
        }
        Transferable transferable = clipboardController.copy(nodeModels)
        if (transferable == null) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Error copying nodes.")
            return
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null)
    }

    private void cutSelectedNodesDeep() {
        List<Node> selectedNodes = getSelectedNodesFromTable()
        if (selectedNodes.isEmpty()) return
        for (Node node : selectedNodes) {
            if (node.isRoot()) {
                JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Root node cannot be cut.")
                return
            }
            Node viewRoot = ScriptUtils.c().viewRoot
            if (viewRoot != null && node.getId() == viewRoot.getId()) {
                JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                    "View Root node cannot be cut. Double-click another node first.")
                return
            }
        }
        List<NodeModel> nodeModels = selectedNodes.collect { getNodeModel(it) }
        MapClipboardController clipboardController = MapClipboardController.getController()
        if (clipboardController == null) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Error accessing clipboard.")
            return
        }
        Transferable transferable = clipboardController.copy(nodeModels)
        if (transferable == null) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Error copying nodes for cut.")
            return
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null)
        List<Node> deletedNodes = []
        for (Node node : selectedNodes) {
            try {
                node.delete()
                deletedNodes.add(node)
            } catch (Exception e) {
                println "Error deleting node ${node.plainText}: ${e.message}"
            }
        }
        removeRowsFromTable(deletedNodes)
        updateResultCount()
        if (tableModel.getRowCount() == 0) clearPreview()
        else resultsTable.setRowSelectionInterval(0, 0)
        pathCache.clear()
        rowHeightCache.clear()
    }

    private void removeRowsFromTable(List<Node> nodesToRemove) {
        if (nodesToRemove.isEmpty()) return
        Set<String> idsToRemove = nodesToRemove.collect { it.getId() } as Set
        for (int i = tableModel.getRowCount() - 1; i >= 0; i--) {
            Object value = tableModel.getValueAt(i, 9)
            Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
            if (node != null && idsToRemove.contains(node.getId())) {
                tableModel.removeRow(i)
            }
        }
    }

    private List<Node> getSelectedNodesFromTable() {
        List<Node> nodes = new ArrayList<>()
        int[] selectedRows = resultsTable.getSelectedRows()
        if (selectedRows == null || selectedRows.length == 0) return nodes
        for (int viewRow : selectedRows) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow)
            Object value = tableModel.getValueAt(modelRow, 9)
            Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
            if (node != null) nodes.add(node)
        }
        return nodes
    }

    private void pasteNodesFromClipboard() {
        int selectedRow = resultsTable.getSelectedRow()
        if (selectedRow == -1) return
        int modelRow = resultsTable.convertRowIndexToModel(selectedRow)
        Object value = tableModel.getValueAt(modelRow, 9)
        Node targetNode = (value instanceof Object[]) ? ((Object[])value)[0] as Node :
                          (value instanceof Node ? value as Node : null)
        if (targetNode == null) return
        Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null)
        if (transferable == null) return
        MapClipboardController clipboardController = MapClipboardController.getController()
        if (clipboardController == null) return
        try {
            NodeModel targetModel = getNodeModel(targetNode)
            clipboardController.paste(transferable, targetModel)
            SwingUtilities.invokeLater {
                pathCache.clear()
                rowHeightCache.clear()
                resultsTable.repaint()
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                "Error pasting: ${e.message}",
                "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    // ========== UI Components ==========
    private JTextField searchField
    private JTable resultsTable
    private DefaultTableModel tableModel
    private String baseDir
    private boolean matchCase = false
    private boolean wholeWord = false
    private JCheckBox coreCheck, detailsCheck, noteCheck
    private JRadioButton folderRadio, openMapsRadio, selectedDescRadio, selectedSibRadio, rootRadio
    private ButtonGroup sourceGroup
    private JRadioButton allScopeRadio, styleScopeRadio
    private JTextField styleField
    private JButton folderChooserBtn
    private JLabel folderLabel
    private JCheckBox matchCaseCB, wholeWordCB
    private JEditorPane previewCore
    private JEditorPane previewDetails
    private JEditorPane previewNote
    private JPanel tagViewer
    private JPanel breadcrumbPanel
    private JLabel styleLabel
    private ScrollableTextPanel textPanel
    private JPanel leftPreviewPanel
    private JSplitPane innerSplitPane
    private JScrollPane previewScrollPane
    private static final Map<String, Color> PATH_COLORS = new ConcurrentHashMap()
    private Container originalContentPane = null
    private JSplitPane currentSplitPane = null
    private int lastDividerLocation = -1
    private int savedDividerLocation = -1
    private Dimension originalContentPaneMinSize = null
    private JScrollPane tableScroll
    private JSplitPane verticalSplitPane = null
    private int lastVerticalDividerLocation = -1
    private boolean disableTooltips = false
    private boolean hideNodeColumn = false
    private boolean hideDetailsPreview = false
    private boolean hideNotePreview = false
    private boolean hidePreviewPanel = false
    private boolean hideFileColumn = false
    private boolean hideStyleColumn = false
    private boolean hidePathColumn = false
    private boolean hideDateColumn = false
    private boolean hideDateCreatedColumn = false
    private boolean hideIconsColumn = false
    private boolean hideTagsColumn = false
    private boolean hideDetailsColumn = false
    private boolean hideNoteColumn = false
    private JDialog settingsDialog = null
    private JDialog columnVisibilityDialog
    private int lastPreviewDividerLocation = -1
    private Timer previewDebouncer = null
    private int pendingRow = -1
    private Timer filterDebouncer = null
    private int baseLineHeight = 20
    private Map<Integer, Integer> rowMaxLinesCache = new ConcurrentHashMap()
    private JTextField filterField
    private JButton upButton
    private JButton downButton
    private TableRowSorter<TableModel> rowSorter
    private String currentFilterText = ""
    private String lastSearchKeyword = ""
    private JLabel resultCountLabel
    private Font fontFileColumn = null
    private Font fontStyleColumn = null
    private Font fontPathColumn = null
    private Font fontWeightColumn = null
    private Font fontPreviewCore = null
    private Font fontPreviewDetails = null
    private Font fontPreviewNote = null
    private Font fontBreadcrumb = null
    private Font fontDateColumn = null
    private Font fontDateCreatedColumn = null
    private Font fontIconsColumn = null
    private Font fontTagsColumn = null
    private Font fontDetailsColumn = null
    private Font fontNoteColumn = null
    private boolean trimMode = true
    private boolean singleLineMode = false
    private boolean fullMode = false
    private int trimLength = 80
    private int ancestorTrimLength = 30
    private JToggleButton trimBtn, singleBtn, fullBtn
    private JSpinner trimSpinner
    private JSpinner ancestorTrimSpinner
    private java.util.List<String> searchHistory = []
    private java.util.List<String> styleHistory = []
    private java.util.List<String> filterHistory = []
    private AutoCompleteDecorator searchAutoComplete
    private AutoCompleteDecorator styleAutoComplete
    private AutoCompleteDecorator filterAutoComplete
    private Timer hoverTimer
    private int hoverRow = -1
    private String lastSelectedNodeId = null
    private String lastBreadcrumbHtml = null
    private JPanel lastBreadcrumbPanel = null
    private Timer columnResizeTimer = null
    private boolean reverseAncestorOrder = false
    private JToggleButton reversePathBtn
    private boolean useVisibleRootOnly = false
    private JToggleButton ancestorModeBtn
    private Map<String, String> pathCache = new ConcurrentHashMap()
    private Map<String, Integer> rowHeightCache = new ConcurrentHashMap()
    private Map<String, Map<String, Object>> previewCache = new ConcurrentHashMap()
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm")
    private JList<ColumnItem> columnList
    private DefaultListModel<ColumnItem> columnListModel
    private JLabel filterResultLabel
    private Map<String, Integer> storedColumnWidths = new ConcurrentHashMap<>()
    private List<Integer> defaultColumnWidths = new ArrayList<>()
    private List<Integer> currentColumnWidths = new ArrayList<>()
    private int totalDefaultWidth = 0
    private boolean adjusting = false

    // ========== Inner class for column visibility ==========
    class ColumnItem {
        String name
        boolean visible
        Closure onToggle
        ColumnItem(String name, boolean visible, Closure onToggle) {
            this.name = name
            this.visible = visible
            this.onToggle = onToggle
        }
    }

    private void saveColumnWidths() {
        def rc = ResourceController.getResourceController()
        if (resultsTable == null) return
        TableColumnModel colModel = resultsTable.getColumnModel()
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            rc.setProperty("mapcrawler.columnWidth.$i", String.valueOf(colModel.getColumn(i).getWidth()))
        }
        if (currentSplitPane != null) {
            int loc = currentSplitPane.getDividerLocation()
            rc.setProperty("mapcrawler.mainDividerLocation", String.valueOf(loc))
        }
        if (verticalSplitPane != null) {
            int vLoc = verticalSplitPane.getDividerLocation()
            if (vLoc > 0) {
                rc.setProperty("mapcrawler.verticalDividerLocation", String.valueOf(vLoc))
            }
        }
        if (innerSplitPane != null) {
            int innerLoc = innerSplitPane.getDividerLocation()
            if (innerLoc > 0) {
                rc.setProperty("mapcrawler.innerDividerLocation", String.valueOf(innerLoc))
            }
        }
    }

    private void loadColumnWidths() {
        def rc = ResourceController.getResourceController()
        if (resultsTable == null) return
        TableColumnModel colModel = resultsTable.getColumnModel()
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            String val = rc.getProperty("mapcrawler.columnWidth.$i")
            if (val != null && val.isInteger()) {
                int w = val.toInteger()
                if (w > 20) colModel.getColumn(i).setPreferredWidth(w)
            }
        }
        String divLoc = rc.getProperty("mapcrawler.mainDividerLocation")
        if (divLoc != null && divLoc.isInteger() && currentSplitPane != null) {
            int loc = divLoc.toInteger()
            if (loc > 0 && loc < currentSplitPane.getWidth()) {
                currentSplitPane.setDividerLocation(loc)
                lastDividerLocation = loc
                savedDividerLocation = loc
            }
        }
        String vDivLoc = rc.getProperty("mapcrawler.verticalDividerLocation")
        if (vDivLoc != null && vDivLoc.isInteger() && verticalSplitPane != null) {
            int vLoc = vDivLoc.toInteger()
            if (vLoc > 20 && vLoc < verticalSplitPane.getHeight() - 50) {
                verticalSplitPane.setDividerLocation(vLoc)
                lastVerticalDividerLocation = vLoc
            }
        }
        String innerLocStr = rc.getProperty("mapcrawler.innerDividerLocation")
        if (innerLocStr != null && innerLocStr.isInteger() && innerSplitPane != null) {
            int innerLoc = innerLocStr.toInteger()
            if (innerLoc > 20 && innerLoc < innerSplitPane.getWidth() - 20) {
                innerSplitPane.setDividerLocation(innerLoc)
            }
        }
    }

    // ========== Main toggle method ==========
    void toggle() {
        if (currentSplitPane == null) show()
        else toggleExpandCollapse()
    }

    void show() {
        def mainFrame = UITools.getCurrentFrame()
        if (currentSplitPane != null) return
        if (originalContentPane == null) originalContentPane = mainFrame.getContentPane()
        loadSettings()
        loadFontSettings()
        loadHistory()
        UIManager.put("SplitPane.oneTouchExpandableOffset", 0.5)
        JPanel searchPanel = createSearchPanel()
        currentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, originalContentPane, searchPanel)
        currentSplitPane.setResizeWeight(0.75)
        currentSplitPane.setOneTouchExpandable(true)
        currentSplitPane.setContinuousLayout(false)
        currentSplitPane.setDividerSize(15)
        currentSplitPane.setMinimumSize(new Dimension(0, 0))
        originalContentPaneMinSize = originalContentPane.getMinimumSize()
        originalContentPane.setMinimumSize(new Dimension(0, 0))
        searchPanel.setMinimumSize(new Dimension(0, 0))
        mainFrame.setContentPane(currentSplitPane)
        mainFrame.revalidate()
        mainFrame.repaint()
        loadFullTableState()
        SwingUtilities.invokeLater(() -> {
            int totalWidth = currentSplitPane.getWidth()
            if (totalWidth > 0 && lastDividerLocation > 0 && lastDividerLocation < totalWidth) {
                currentSplitPane.setDividerLocation(lastDividerLocation)
                savedDividerLocation = lastDividerLocation
            } else if (totalWidth > 0) {
                int rightWidth = (int)(totalWidth * 0.2625)
                int leftWidth = totalWidth - rightWidth
                currentSplitPane.setDividerLocation(leftWidth)
                savedDividerLocation = leftWidth
            } else {
                currentSplitPane.setDividerLocation(0.2625)
                savedDividerLocation = (int)(currentSplitPane.getWidth() * 0.2625)
            }
            if (verticalSplitPane != null) {
                if (lastVerticalDividerLocation > 0 && lastVerticalDividerLocation < verticalSplitPane.getHeight()) {
                    verticalSplitPane.setDividerLocation(lastVerticalDividerLocation)
                } else {
                    verticalSplitPane.setDividerLocation(180)
                }
            }
            updateAllRowHeights()
            initColumnWidths()
            fixViewportToRight()
            adjustLastColumnWidth()
        })
        currentSplitPane.addPropertyChangeListener("dividerLocation", new PropertyChangeListener() {
            private Timer timer
            void propertyChange(PropertyChangeEvent evt) {
                if (timer != null && timer.isRunning()) timer.stop()
                timer = new Timer(50, {
                    if (tableScroll != null) {
                        fixViewportToRight()
                        adjustLastColumnWidth()
                    }
                })
                timer.setRepeats(false)
                timer.start()
            }
        })
        focusSearchField()
        setupKeyboardShortcut()
        setupTagShortcut()
        applyFontsToComponents()
        updateBaseLineHeight()
        updateModeDependencies()
        resultsTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            void columnMarginChanged(ChangeEvent e) {
                if (columnResizeTimer != null && columnResizeTimer.isRunning())
                    columnResizeTimer.stop()
                columnResizeTimer = new Timer(150, {
                    updateAllRowHeights()
                    fixViewportToRight()
                    saveFullTableState()
                })
                columnResizeTimer.setRepeats(false)
                columnResizeTimer.start()
            }
            void columnMoved(TableColumnModelEvent e) {
                rowHeightCache.clear()
                saveFullTableState()
                fixViewportToRight()
            }
            void columnAdded(TableColumnModelEvent e) { rowHeightCache.clear() }
            void columnRemoved(TableColumnModelEvent e) { rowHeightCache.clear() }
            void columnSelectionChanged(ListSelectionEvent e) { }
        })
        // Start map selection polling
        startMapSelectionPolling()
    }

    private void closePanel() {
        if (currentSplitPane == null) return
        saveFullTableState()
        if (settingsDialog != null && settingsDialog.isVisible()) settingsDialog.dispose()
        def mainFrame = UITools.getCurrentFrame()
        if (originalContentPane != null) {
            if (originalContentPaneMinSize != null) {
                originalContentPane.setMinimumSize(originalContentPaneMinSize)
            }
            mainFrame.setContentPane(originalContentPane)
            mainFrame.revalidate()
            mainFrame.repaint()
        }
        currentSplitPane = null
        if (hoverTimer != null) hoverTimer.stop()
        pathCache.clear()
        rowHeightCache.clear()
        // Stop polling and hide floating breadcrumb
        stopMapSelectionPolling()
        removeBreadcrumbOnly()
    }

    private void toggleExpandCollapse() {
        if (currentSplitPane == null) return
        int max = currentSplitPane.getMaximumDividerLocation()
        int current = currentSplitPane.getDividerLocation()
        if (current >= max - 5) {
            int target = (savedDividerLocation > 0 && savedDividerLocation < max) ? savedDividerLocation : (int)(currentSplitPane.getWidth() * 0.75)
            currentSplitPane.setDividerLocation(target)
        } else {
            savedDividerLocation = current
            currentSplitPane.setDividerLocation(1.0)
        }
        saveColumnWidths()
    }

    private void setupKeyboardShortcut() {
        if (currentSplitPane == null) return
        def mainFrame = UITools.getCurrentFrame()
        mainFrame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.ALT_DOWN_MASK), "toggleCollapse")
        mainFrame.getRootPane().getActionMap().put("toggleCollapse", new AbstractAction() {
            void actionPerformed(ActionEvent e) { toggleExpandCollapse() }
        })
    }

    private void createColumnVisibilityDialog() {
        if (columnVisibilityDialog != null) return
        JFrame mainFrame = UITools.getCurrentFrame()
        columnVisibilityDialog = new JDialog(mainFrame, "Column Visibility Settings", false)
        columnVisibilityDialog.setLayout(new BorderLayout())
        columnListModel = new DefaultListModel<>()
        columnList = new JList<>(columnListModel)
        columnList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                ColumnItem item = (ColumnItem) value
                JCheckBox checkBox = new JCheckBox(item.name, item.visible)
                checkBox.setOpaque(true)
                if (isSelected) {
                    checkBox.setBackground(list.getSelectionBackground())
                    checkBox.setForeground(list.getSelectionForeground())
                } else {
                    checkBox.setBackground(list.getBackground())
                    checkBox.setForeground(list.getForeground())
                }
                checkBox.setFont(list.getFont())
                checkBox.setFocusable(false)
                return checkBox
            }
        })
        columnList.setVisibleRowCount(12)
        columnList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        columnListModel.addElement(new ColumnItem("File Column", !hideFileColumn, { val -> hideFileColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Style Column", !hideStyleColumn, { val -> hideStyleColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Ancestors Column", !hidePathColumn, { val -> hidePathColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Date Modified (Node)", !hideDateColumn, { val -> hideDateColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Date Created (Node)", !hideDateCreatedColumn, { val -> hideDateCreatedColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Icons Column", !hideIconsColumn, { val -> hideIconsColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Tags Column", !hideTagsColumn, { val -> hideTagsColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Details Column", !hideDetailsColumn, { val -> hideDetailsColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Note Column", !hideNoteColumn, { val -> hideNoteColumn = !val; afterColumnToggle() }))
        columnListModel.addElement(new ColumnItem("Node Column", !hideNodeColumn, { val -> hideNodeColumn = !val; afterColumnToggle() }))
        JScrollPane scrollPane = new JScrollPane(columnList)
        scrollPane.setPreferredSize(new Dimension(350, 550))
        columnVisibilityDialog.add(scrollPane, BorderLayout.CENTER)
        columnList.addMouseListener(new MouseAdapter() {
            void mouseClicked(MouseEvent e) {
                int idx = columnList.locationToIndex(e.getPoint())
                if (idx != -1) {
                    ColumnItem item = columnListModel.get(idx)
                    item.visible = !item.visible
                    item.onToggle.call(item.visible)
                    columnListModel.set(idx, item)
                    columnList.setSelectedIndex(idx)
                }
            }
        })
        Action toggleAction = new AbstractAction() {
            void actionPerformed(ActionEvent e) {
                int idx = columnList.getSelectedIndex()
                if (idx != -1) {
                    ColumnItem item = columnListModel.get(idx)
                    item.visible = !item.visible
                    item.onToggle.call(item.visible)
                    columnListModel.set(idx, item)
                    columnList.setSelectedIndex(idx)
                }
            }
        }
        Action closeAction = new AbstractAction() {
            void actionPerformed(ActionEvent e) {
                columnVisibilityDialog.setVisible(false)
            }
        }
        columnList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle")
        columnList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle")
        columnList.getActionMap().put("toggle", toggleAction)
        columnVisibilityDialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close")
        columnVisibilityDialog.getRootPane().getActionMap().put("close", closeAction)
        columnVisibilityDialog.pack()
        columnVisibilityDialog.setLocationRelativeTo(mainFrame)
    }

    private void updateColumnDialogItems() {
        if (columnListModel == null) return
        for (int i = 0; i < columnListModel.size(); i++) {
            ColumnItem item = columnListModel.get(i)
            if (i == 0) item.visible = !hideFileColumn
            else if (i == 1) item.visible = !hideStyleColumn
            else if (i == 2) item.visible = !hidePathColumn
            else if (i == 3) item.visible = !hideDateColumn
            else if (i == 4) item.visible = !hideDateCreatedColumn
            else if (i == 5) item.visible = !hideIconsColumn
            else if (i == 6) item.visible = !hideTagsColumn
            else if (i == 7) item.visible = !hideDetailsColumn
            else if (i == 8) item.visible = !hideNoteColumn
            else if (i == 9) item.visible = !hideNodeColumn
            columnListModel.set(i, item)
        }
    }

    private void afterColumnToggle() {
        TableColumnModel colModel = resultsTable.getColumnModel()
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            TableColumn col = colModel.getColumn(i)
            String header = (String) col.getHeaderValue()
            int width = col.getWidth()
            if (width > 0) {
                storedColumnWidths.put(header, width)
            }
        }
        rowHeightCache.clear()
        applySettings()
        saveSettingsToPrefs()
        saveFullTableState()
        updateColumnDialogItems()
        updateAllRowHeights()
        fixViewportToRight()
    }

    private boolean isColumnHidden(String header) {
        switch(header) {
            case "File": return hideFileColumn
            case "Style": return hideStyleColumn
            case "Ancestors": return hidePathColumn
            case "Date Modified": return hideDateColumn
            case "Date Created": return hideDateCreatedColumn
            case "Icons": return hideIconsColumn
            case "Tags": return hideTagsColumn
            case "Details": return hideDetailsColumn
            case "Note": return hideNoteColumn
            case "Node": return hideNodeColumn
            default: return false
        }
    }

    private void configureRightControl(AbstractButton button) {
        button.setHorizontalTextPosition(SwingConstants.RIGHT)
        button.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL)
        Dimension d = new Dimension(6, 35)
        sep.setMaximumSize(d)
        sep.setMinimumSize(d)
        sep.setForeground(Color.ORANGE)
        sep.setBackground(Color.ORANGE)
        sep.setOpaque(true)
        return sep
    }

    // ========== Create main search panel ==========
    private JPanel createSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout())
        JPanel row1 = new JPanel()
        row1.setLayout(new BoxLayout(row1, BoxLayout.LINE_AXIS))
        row1.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        row1.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5))
        folderRadio = new JRadioButton("Path")
        configureRightControl(folderRadio)
        folderRadio.setMargin(new Insets(0,0,0,0))
        openMapsRadio = new JRadioButton("Open Maps")
        configureRightControl(openMapsRadio)
        openMapsRadio.setMargin(new Insets(0,0,0,0))
        selectedSibRadio = new JRadioButton("Siblings")
        configureRightControl(selectedSibRadio)
        selectedSibRadio.setMargin(new Insets(0,0,0,0))
        selectedDescRadio = new JRadioButton("Branch")
        configureRightControl(selectedDescRadio)
        selectedDescRadio.setMargin(new Insets(0,0,0,0))
        rootRadio = new JRadioButton("Root", true)
        configureRightControl(rootRadio)
        rootRadio.setMargin(new Insets(0,0,0,0))
        sourceGroup = new ButtonGroup()
        [folderRadio, openMapsRadio, selectedDescRadio, selectedSibRadio, rootRadio].each { sourceGroup.add(it) }
        JPanel folderPanel = new JPanel()
        folderPanel.setLayout(new BoxLayout(folderPanel, BoxLayout.X_AXIS))
        folderPanel.setOpaque(false)
        folderPanel.setAlignmentY(Component.CENTER_ALIGNMENT)
        folderLabel = new JLabel(shortenPath(baseDir))
        folderLabel.setFont(folderLabel.getFont().deriveFont(Font.PLAIN, 28f))
        folderLabel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        folderLabel.setHorizontalAlignment(SwingConstants.RIGHT)
        folderLabel.setPreferredSize(new Dimension(350, 40))
        folderLabel.setMinimumSize(new Dimension(200, 40))
        folderLabel.setAlignmentY(Component.CENTER_ALIGNMENT)
        folderChooserBtn = new JButton("📁")
        folderChooserBtn.setFont(folderChooserBtn.getFont().deriveFont(Font.PLAIN, 40f))
        folderChooserBtn.setPreferredSize(new Dimension(50, 30))
        folderChooserBtn.setContentAreaFilled(false)
        folderChooserBtn.setBorderPainted(false)
        folderChooserBtn.setFocusPainted(false)
        folderChooserBtn.setForeground(Color.ORANGE)
        folderChooserBtn.setAlignmentY(Component.CENTER_ALIGNMENT)
        folderChooserBtn.addActionListener({ selectFolder() })
        folderPanel.add(folderLabel)
        folderPanel.add(Box.createHorizontalStrut(15))
        folderPanel.add(folderChooserBtn)
        ItemListener folderEnabler = { ItemEvent e ->
            boolean folderSelected = folderRadio.isSelected()
            folderChooserBtn.setEnabled(folderSelected)
            folderLabel.setEnabled(folderSelected)
        } as ItemListener
        folderRadio.addItemListener(folderEnabler)
        openMapsRadio.addItemListener(folderEnabler)
        selectedDescRadio.addItemListener(folderEnabler)
        selectedSibRadio.addItemListener(folderEnabler)
        rootRadio.addItemListener(folderEnabler)
        row1.add(rootRadio)
        row1.add(selectedDescRadio)
        row1.add(selectedSibRadio)
        row1.add(Box.createHorizontalStrut(5))
        row1.add(createSeparator())
        row1.add(Box.createHorizontalStrut(5))
        row1.add(openMapsRadio)
        row1.add(folderRadio)
        row1.add(folderPanel)
        JPanel row2 = new JPanel()
        row2.setLayout(new BoxLayout(row2, BoxLayout.LINE_AXIS))
        row2.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        matchCaseCB = new JCheckBox("Aa")
        wholeWordCB = new JCheckBox("ww")
        matchCaseCB.setMargin(new Insets(0,0,0,0))
        wholeWordCB.setMargin(new Insets(0,0,0,0))
        matchCaseCB.setHorizontalTextPosition(SwingConstants.RIGHT)
        matchCaseCB.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        wholeWordCB.setHorizontalTextPosition(SwingConstants.RIGHT)
        wholeWordCB.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        matchCaseCB.addActionListener({ matchCase = matchCaseCB.isSelected(); clearCaches() })
        wholeWordCB.addActionListener({ wholeWord = wholeWordCB.isSelected(); clearCaches() })
        styleField = new JTextField(30)
        styleField.setEnabled(false)
        styleField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        styleField.setHorizontalAlignment(JTextField.RIGHT)
        styleField.setMaximumSize(styleField.getPreferredSize())
        styleAutoComplete = new AutoCompleteDecorator(styleField, styleHistory, "style", this, false)
        styleScopeRadio = new JRadioButton("Style")
        configureRightControl(styleScopeRadio)
        styleScopeRadio.setMargin(new Insets(0,0,0,0))
        allScopeRadio = new JRadioButton("All", true)
        configureRightControl(allScopeRadio)
        allScopeRadio.setMargin(new Insets(0,0,0,0))
        ButtonGroup scopeGroup = new ButtonGroup()
        scopeGroup.add(allScopeRadio); scopeGroup.add(styleScopeRadio)
        styleScopeRadio.addActionListener({ styleField.setEnabled(styleScopeRadio.isSelected()) })
        allScopeRadio.addActionListener({ styleField.setEnabled(false); styleField.setText("") })
        row2.add(allScopeRadio)
        row2.add(Box.createHorizontalStrut(10))
        row2.add(styleScopeRadio)
        row2.add(styleField)
        row2.add(Box.createHorizontalStrut(10))
        row2.add(wholeWordCB)
        row2.add(matchCaseCB)
        JPanel row3 = new JPanel()
        row3.setLayout(new BoxLayout(row3, BoxLayout.LINE_AXIS))
        row3.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        searchField = new JTextField(30)
        searchField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        searchField.setHorizontalAlignment(JTextField.RIGHT)
        searchField.setMaximumSize(searchField.getPreferredSize())
        searchAutoComplete = new AutoCompleteDecorator(searchField, searchHistory, "search", this, false)
        JButton searchBtn = new JButton("🔍")
        searchBtn.setPreferredSize(new Dimension(50, 30))
        searchBtn.addActionListener({ doSearch() })
        noteCheck = new JCheckBox("Note", false)
        detailsCheck = new JCheckBox("Details", false)
        coreCheck = new JCheckBox("Core", true)
        [noteCheck, detailsCheck, coreCheck].each {
            it.setHorizontalTextPosition(SwingConstants.RIGHT)
            it.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        }
        row3.add(coreCheck)
        row3.add(detailsCheck)
        row3.add(noteCheck)
        row3.add(searchBtn)
        row3.add(searchField)
        JPanel topPanel = new JPanel(new GridLayout(3, 1, 0, 2))
        topPanel.add(row1)
        topPanel.add(row2)
        topPanel.add(row3)
        topPanel.setMinimumSize(new Dimension(0, 0))
        
        // ========== Breadcrumb Panel ==========
        breadcrumbPanel = new JPanel()
        breadcrumbPanel.setLayout(new BoxLayout(breadcrumbPanel, BoxLayout.LINE_AXIS))
        breadcrumbPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        breadcrumbPanel.setBackground(UIManager.getColor("Panel.background"))
        breadcrumbPanel.setVisible(true)
        
        // ========== Left Preview Panel ==========
        leftPreviewPanel = new JPanel(new BorderLayout())
        leftPreviewPanel.setBorder(BorderFactory.createEmptyBorder())
        JPanel topPreviewPanel = new JPanel(new BorderLayout())
        topPreviewPanel.setBackground(leftPreviewPanel.getBackground())
        styleLabel = new JLabel()
        styleLabel.setFont(styleLabel.getFont().deriveFont(Font.ITALIC, 10f))
        styleLabel.setForeground(Color.GRAY)
        styleLabel.setHorizontalAlignment(SwingConstants.RIGHT)
        styleLabel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        topPreviewPanel.add(styleLabel, BorderLayout.NORTH)
        tagViewer = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5))
        tagViewer.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        tagViewer.setBackground(leftPreviewPanel.getBackground())
        topPreviewPanel.add(tagViewer, BorderLayout.CENTER)
        previewCore = new JEditorPane()
        previewCore.setContentType("text/html")
        previewCore.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        previewCore.setEditable(false)
        previewCore.setMargin(new Insets(10,10,10,10))
        previewCore.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        previewDetails = new JEditorPane()
        previewDetails.setContentType("text/html")
        previewDetails.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        previewDetails.setEditable(false)
        previewDetails.setMargin(new Insets(10,10,10,10))
        previewDetails.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        previewNote = new JEditorPane()
        previewNote.setContentType("text/html")
        previewNote.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        previewNote.setEditable(false)
        previewNote.setMargin(new Insets(10,10,10,10))
        previewNote.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        textPanel = new ScrollableTextPanel()
        textPanel.add(previewCore)
        textPanel.add(Box.createRigidArea(new Dimension(0, 10)))
        textPanel.add(previewDetails)
        textPanel.add(Box.createRigidArea(new Dimension(0, 10)))
        textPanel.add(previewNote)
        previewDetails.setVisible(true)
        previewNote.setVisible(true)
        JScrollPane previewScroll = new JScrollPane(textPanel)
        previewScroll.setBorder(BorderFactory.createEmptyBorder())
        previewScroll.setViewportBorder(BorderFactory.createEmptyBorder())
        previewScroll.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        previewScroll.getViewport().setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        previewScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
        previewScrollPane = previewScroll
        leftPreviewPanel.add(topPreviewPanel, BorderLayout.NORTH)
        leftPreviewPanel.add(previewScroll, BorderLayout.CENTER)
        
        // ========== Results Table ==========
        tableModel = new DefaultTableModel(
            ["File","Style","Ancestors","Date Modified","Date Created","Icons","Tags","Details","Note","Node"] as Object[], 0)
        resultsTable = new JTable(tableModel)
        resultsTable.setRowHeight(20)

        // Setup Tag Drag & Drop
        setupTagDragAndDrop()
        
        // Mouse enter to focus table
        resultsTable.addMouseListener(new MouseAdapter() {
            void mouseEntered(MouseEvent e) {
                resultsTable.requestFocusInWindow()
            }
        })

        // Right-click context menu
        resultsTable.addMouseListener(new MouseAdapter() {
            void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = resultsTable.rowAtPoint(e.getPoint())
                    if (row != -1) {
                        if (!resultsTable.isRowSelected(row)) {
                            resultsTable.setRowSelectionInterval(row, row)
                        }
                        JPopupMenu popup = new JPopupMenu()
                        
                        JMenuItem copyItem = new JMenuItem("Copy Node(s)")
                        copyItem.addActionListener({ copySelectedNodesDeep() })
                        popup.add(copyItem)
                        
                        JMenuItem cutItem = new JMenuItem("Cut Node(s)")
                        cutItem.addActionListener({ cutSelectedNodesDeep() })
                        popup.add(cutItem)
                        
                        JMenuItem pasteItem = new JMenuItem("Paste Node(s) Below")
                        pasteItem.addActionListener({ pasteNodesFromClipboard() })
                        popup.add(pasteItem)
                        popup.addSeparator()
                        
                        JMenuItem copyTagItem = new JMenuItem("Copy Tag (Ctrl+Shift+C)")
                        copyTagItem.addActionListener({ copyTagFromSelectedRow() })
                        popup.add(copyTagItem)
                        
                        JMenuItem pasteTagItem = new JMenuItem("Paste Tag (Ctrl+Shift+V)")
                        pasteTagItem.addActionListener({ pasteTagToSelectedRows() })
                        popup.add(pasteTagItem)
                        popup.addSeparator()
                        
                        JMenuItem deleteItem = new JMenuItem("Delete Node from Map")
                        deleteItem.addActionListener({ deleteSelectedNode() })
                        popup.add(deleteItem)
                        
                        JMenuItem editItem = new JMenuItem("Edit Node")
                        editItem.addActionListener({ editSelectedNode() })
                        popup.add(editItem)
                        
                        JMenuItem tagItem = new JMenuItem("Add Tag (F8)")
                        tagItem.addActionListener({ addTagFromSelectedRow() })
                        popup.add(tagItem)
                        
                        popup.show(resultsTable, e.getX(), e.getY())
                    }
                }
            }
        })

        // Single click to go to node
        resultsTable.addMouseListener(new MouseAdapter() {
            void mouseClicked(MouseEvent e) {
                if (e.clickCount == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = resultsTable.getSelectedRow()
                    if (row != -1) {
                        goToNode(row)
                    }
                }
            }
        })

        // Hover to preview
        resultsTable.addMouseMotionListener(new MouseMotionAdapter() {
            private int lastHoverRow = -1
            void mouseMoved(MouseEvent e) {
                Point p = e.getPoint()
                int row = resultsTable.rowAtPoint(p)
                if (row != -1 && row != lastHoverRow) {
                    lastHoverRow = row
                    if (hoverTimer != null) hoverTimer.stop()
                    hoverTimer = new Timer(200, new ActionListener() {
                        void actionPerformed(ActionEvent ev) {
                            if (row < resultsTable.getRowCount() && row == lastHoverRow) {
                                resultsTable.setRowSelectionInterval(row, row)
                                showNodeDetails(row)
                            }
                            hoverTimer.stop()
                        }
                    })
                    hoverTimer.setRepeats(false)
                    hoverTimer.start()
                } else if (row == -1) {
                    lastHoverRow = -1
                }
            }
        })

        // Keyboard navigation
        resultsTable.addKeyListener(new KeyAdapter() {
            void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    toggleColumnsWithRightArrow()
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    toggleColumnsWithLeftArrow()
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    int row = resultsTable.getSelectedRow()
                    if (row != -1) {
                        goToNode(row)
                        e.consume()
                    }
                }
            }
        })

        // Row selection listener
        resultsTable.getSelectionModel().addListSelectionListener({ e ->
            if (e.getValueIsAdjusting()) return
            int row = resultsTable.getSelectedRow()
            if (row != -1) {
                showNodeDetails(row)
            } else {
                clearPreview()
            }
        })

        // Table settings
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        resultsTable.setSelectionBackground(new Color(200, 255, 200))
        resultsTable.setSelectionForeground(Color.BLACK)
        resultsTable.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        resultsTable.getTableHeader().setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        resultsTable.setShowHorizontalLines(true)
        resultsTable.setShowVerticalLines(true)
        resultsTable.setGridColor(Color.DARK_GRAY)
        resultsTable.setIntercellSpacing(new Dimension(2, 2))
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)

        // RowSorter
        rowSorter = new TableRowSorter<>(tableModel)
        rowSorter.setComparator(3, new Comparator<String>() {
            int compare(String d1, String d2) {
                if (d1 == null && d2 == null) return 0
                if (d1 == null) return -1
                if (d2 == null) return 1
                try {
                    Date date1 = dateFormat.parse(d1)
                    Date date2 = dateFormat.parse(d2)
                    return date1.compareTo(date2)
                } catch (Exception e) { return d1.compareTo(d2) }
            }
        })
        rowSorter.setComparator(4, new Comparator<String>() {
            int compare(String d1, String d2) {
                if (d1 == null && d2 == null) return 0
                if (d1 == null) return -1
                if (d2 == null) return 1
                try {
                    Date date1 = dateFormat.parse(d1)
                    Date date2 = dateFormat.parse(d2)
                    return date1.compareTo(date2)
                } catch (Exception e) { return d1.compareTo(d2) }
            }
        })
        rowSorter.setComparator(2, new Comparator<String>() {
            int compare(String s1, String s2) {
                if (s1 == null && s2 == null) return 0
                if (s1 == null) return -1
                if (s2 == null) return 1
                return s1.compareTo(s2)
            }
        })
        rowSorter.setComparator(6, new Comparator<String>() {
            int compare(String s1, String s2) {
                if (s1 == null && s2 == null) return 0
                if (s1 == null) return -1
                if (s2 == null) return 1
                return s1.compareTo(s2)
            }
        })
        resultsTable.setRowSorter(rowSorter)

        // Cell Renderers
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(new FontAwareRenderer(0))
        resultsTable.getColumnModel().getColumn(1).setCellRenderer(new FontAwareRenderer(1))
        resultsTable.getColumnModel().getColumn(2).setCellRenderer(new FontAwarePathRenderer())
        resultsTable.getColumnModel().getColumn(3).setCellRenderer(new FontAwareDateRenderer())
        resultsTable.getColumnModel().getColumn(4).setCellRenderer(new FontAwareDateRenderer())
        resultsTable.getColumnModel().getColumn(5).setCellRenderer(new FontAwareIconsRenderer())
        resultsTable.getColumnModel().getColumn(6).setCellRenderer(new FontAwareTagsRenderer())
        resultsTable.getColumnModel().getColumn(7).setCellRenderer(new FontAwareHtmlRenderer(7))
        resultsTable.getColumnModel().getColumn(8).setCellRenderer(new FontAwareHtmlRenderer(8))
        resultsTable.getColumnModel().getColumn(9).setCellRenderer(new FontAwareNodeRenderer())

        // Column widths
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(120)
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(80)
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(200)
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(100)
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(100)
        resultsTable.getColumnModel().getColumn(5).setPreferredWidth(100)
        resultsTable.getColumnModel().getColumn(6).setPreferredWidth(80)
        resultsTable.getColumnModel().getColumn(7).setPreferredWidth(150)
        resultsTable.getColumnModel().getColumn(8).setPreferredWidth(150)
        resultsTable.getColumnModel().getColumn(9).setPreferredWidth(350)

        resultsTable.getColumnModel().getColumn(0).setMinWidth(60)
        resultsTable.getColumnModel().getColumn(1).setMinWidth(50)
        resultsTable.getColumnModel().getColumn(2).setMinWidth(80)
        resultsTable.getColumnModel().getColumn(3).setMinWidth(80)
        resultsTable.getColumnModel().getColumn(4).setMinWidth(80)
        resultsTable.getColumnModel().getColumn(5).setMinWidth(60)
        resultsTable.getColumnModel().getColumn(6).setMinWidth(50)
        resultsTable.getColumnModel().getColumn(7).setMinWidth(80)
        resultsTable.getColumnModel().getColumn(8).setMinWidth(80)
        resultsTable.getColumnModel().getColumn(9).setMinWidth(120)

        resultsTable.setDefaultEditor(Object, null)

        // Copy/Cut/Paste shortcuts
        Action copyAction = new AbstractAction() {
            void actionPerformed(ActionEvent e) { copySelectedNodesDeep() }
        }
        Action cutAction = new AbstractAction() {
            void actionPerformed(ActionEvent e) { cutSelectedNodesDeep() }
        }
        Action pasteAction = new AbstractAction() {
            void actionPerformed(ActionEvent e) { pasteNodesFromClipboard() }
        }
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copyNodes")
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "cutNodes")
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "pasteNodes")
        resultsTable.getActionMap().put("copyNodes", copyAction)
        resultsTable.getActionMap().put("cutNodes", cutAction)
        resultsTable.getActionMap().put("pasteNodes", pasteAction)

        createColumnVisibilityDialog()
        resultsTable.getTableHeader().addMouseListener(new MouseAdapter() {
            void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    columnVisibilityDialog.setLocation(e.getXOnScreen(), e.getYOnScreen())
                    columnVisibilityDialog.setVisible(true)
                    SwingUtilities.invokeLater(() -> columnList.requestFocusInWindow())
                }
            }
        })
        resultsTable.addMouseMotionListener(new MouseMotionAdapter() {
            private int lastHoverRow = -1
            void mouseMoved(MouseEvent e) {
                Point p = e.getPoint()
                int row = resultsTable.rowAtPoint(p)
                if (row != -1 && row != lastHoverRow) {
                    lastHoverRow = row
                    if (hoverTimer != null) hoverTimer.stop()
                    hoverTimer = new Timer(200, new ActionListener() {
                        void actionPerformed(ActionEvent ev) {
                            if (row < resultsTable.getRowCount() && row == lastHoverRow) {
                                resultsTable.setRowSelectionInterval(row, row)
                                showNodeDetails(row)
                            }
                            hoverTimer.stop()
                        }
                    })
                    hoverTimer.setRepeats(false)
                    hoverTimer.start()
                } else if (row == -1) {
                    lastHoverRow = -1
                }
            }
        })
        resultsTable.addKeyListener(new KeyAdapter() {
            void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    toggleColumnsWithRightArrow()
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    toggleColumnsWithLeftArrow()
                }
            }
        })
        resultsTable.getSelectionModel().addListSelectionListener({ e ->
            if (e.getValueIsAdjusting()) return
            int row = resultsTable.getSelectedRow()
            if (row != -1) {
                showNodeDetails(row)
            } else {
                clearPreview()
            }
        })
        resultsTable.addMouseListener(new MouseAdapter() {
            void mouseClicked(MouseEvent e) {
                if (e.clickCount == 2) { int row = resultsTable.selectedRow; if (row != -1) goToNode(row) }
            }
        })
        resultsTable.addKeyListener(new KeyAdapter() {
            void keyPressed(KeyEvent e) {
                if (e.keyCode == KeyEvent.VK_ENTER) { int row = resultsTable.selectedRow; if (row != -1) goToNode(row) }
            }
        })
        tableScroll = new JScrollPane(resultsTable)
        tableScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        tableScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED)
        tableScroll.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
        
        // ========== Inner SplitPane (Preview | Table) ==========
        innerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPreviewPanel, tableScroll)
        innerSplitPane.setResizeWeight(0.0)
        innerSplitPane.setContinuousLayout(false)
        innerSplitPane.setDividerSize(6)
        innerSplitPane.setOneTouchExpandable(true)
        innerSplitPane.addPropertyChangeListener("dividerLocation", new PropertyChangeListener() {
            void propertyChange(PropertyChangeEvent evt) {
                SwingUtilities.invokeLater(new Runnable() {
                    void run() { adjustLastColumnWidth() }
                })
            }
        })
        
        JPanel centerPanel = new JPanel(new BorderLayout())
        centerPanel.add(breadcrumbPanel, BorderLayout.NORTH)
        centerPanel.add(innerSplitPane, BorderLayout.CENTER)
        
        // ========== Vertical SplitPane (Top controls | Center) ==========
        verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, centerPanel)
        verticalSplitPane.setResizeWeight(0.0)
        verticalSplitPane.setOneTouchExpandable(false)
        verticalSplitPane.setContinuousLayout(false)
        verticalSplitPane.setDividerSize(8)
        verticalSplitPane.setMinimumSize(new Dimension(0, 0))
        verticalSplitPane.addPropertyChangeListener("dividerLocation", new PropertyChangeListener() {
            void propertyChange(PropertyChangeEvent evt) {
                int current = verticalSplitPane.getDividerLocation()
                if (current > 20 && current < verticalSplitPane.getHeight() - 50) {
                    lastVerticalDividerLocation = current
                    saveColumnWidths()
                }
            }
        })
        mainPanel.add(verticalSplitPane, BorderLayout.CENTER)
        
        // ========== Bottom Filter Panel ==========
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT))
        filterPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        filterPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2))
        filterField = new JTextField(15)
        filterField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        filterField.setHorizontalAlignment(JTextField.RIGHT)
        filterAutoComplete = new AutoCompleteDecorator(filterField, filterHistory, "filter", this, true)
        filterField.addActionListener({ applyFilter() })
        filterField.addKeyListener(new KeyAdapter() { void keyReleased(KeyEvent e) { applyFilter() } })
        
        // AND/OR buttons
        JButton andBtn = new JButton("AND")
        andBtn.setFont(andBtn.getFont().deriveFont(Font.BOLD, 10f))
        andBtn.setPreferredSize(new Dimension(50, 25))
        andBtn.setBackground(new Color(200, 230, 255))
        andBtn.setToolTipText("Add AND to filter expression")
        andBtn.addActionListener({ 
            String current = filterField.getText().trim()
            if (!current.isEmpty() && !current.endsWith(" ")) {
                filterField.setText(current + " AND ")
            } else {
                filterField.setText(current + "AND ")
            }
            filterField.requestFocus()
            filterField.setCaretPosition(filterField.getText().length())
        })
        
        JButton orBtn = new JButton("OR")
        orBtn.setFont(orBtn.getFont().deriveFont(Font.BOLD, 10f))
        orBtn.setPreferredSize(new Dimension(50, 25))
        orBtn.setBackground(new Color(255, 200, 200))
        orBtn.setToolTipText("Add OR to filter expression")
        orBtn.addActionListener({ 
            String current = filterField.getText().trim()
            if (!current.isEmpty() && !current.endsWith(" ")) {
                filterField.setText(current + " OR ")
            } else {
                filterField.setText(current + "OR ")
            }
            filterField.requestFocus()
            filterField.setCaretPosition(filterField.getText().length())
        })
        
        upButton = new JButton("↑")
        upButton.addActionListener({ navigate(-1) })
        downButton = new JButton("↓")
        downButton.addActionListener({ navigate(1) })
        Action upAction = new AbstractAction() { void actionPerformed(ActionEvent e) { navigate(-1) } }
        Action downAction = new AbstractAction() { void actionPerformed(ActionEvent e) { navigate(1) } }
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), "prevMatch")
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "nextMatch")
        resultsTable.getActionMap().put("prevMatch", upAction)
        resultsTable.getActionMap().put("nextMatch", downAction)
        
        filterPanel.add(orBtn)
        filterPanel.add(andBtn)
        filterPanel.add(new JLabel("Filter:")); filterPanel.add(filterField); filterPanel.add(upButton); filterPanel.add(downButton)
        filterResultLabel = new JLabel("0 results")
        filterResultLabel.setFont(filterResultLabel.getFont().deriveFont(Font.BOLD))
        filterPanel.add(filterResultLabel)
        
        // ========== Status Bar ==========

        
        filterPanel.add(new JLabel("Filter:")); filterPanel.add(filterField); filterPanel.add(upButton); filterPanel.add(downButton)
        filterResultLabel = new JLabel("0 results")
        filterResultLabel.setFont(filterResultLabel.getFont().deriveFont(Font.BOLD))
        filterPanel.add(filterResultLabel)
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.RIGHT))
        statusBar.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        JButton closeBtn = new JButton("✖")
        closeBtn.addActionListener({ closePanel() })
        JButton minimizeBtn = new JButton("_")
        minimizeBtn.setFont(minimizeBtn.getFont().deriveFont(Font.BOLD, 27f))
        minimizeBtn.addActionListener({ toggleExpandCollapse() })
        JButton settingsBtn = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-2699"))
        settingsBtn.setToolTipText("Settings")
        settingsBtn.setContentAreaFilled(false)
        settingsBtn.setBorderPainted(false)
        settingsBtn.addActionListener({ showSettingsDialog() })
        
        // Floating breadcrumb toggle button
        JButton toggleBreadcrumbBtn = new JButton("🍞")
        toggleBreadcrumbBtn.setToolTipText("Show/Hide floating breadcrumb")
        toggleBreadcrumbBtn.setContentAreaFilled(false)
        toggleBreadcrumbBtn.setBorderPainted(false)
        toggleBreadcrumbBtn.addActionListener({ toggleBreadcrumbOnlyMode() })
        statusBar.add(toggleBreadcrumbBtn)
        statusBar.add(closeBtn)
        statusBar.add(minimizeBtn)
        statusBar.add(settingsBtn)
        
        trimBtn = new JToggleButton("✂️")
        trimBtn.addActionListener({ setMode('trim') })
        statusBar.add(trimBtn)
        singleBtn = new JToggleButton("‖")
        singleBtn.addActionListener({ setMode('single') })
        statusBar.add(singleBtn)
        fullBtn = new JToggleButton("📄")
        fullBtn.addActionListener({ setMode('full') })
        statusBar.add(fullBtn)
        
        SpinnerNumberModel trimSpinnerModel = new SpinnerNumberModel(trimLength, 10, 500, 5)
        trimSpinner = new JSpinner(trimSpinnerModel)
        int btnHeight = trimBtn.getPreferredSize().height
        trimSpinner.setPreferredSize(new Dimension(140, btnHeight))
        trimSpinner.setFont(trimSpinner.getFont().deriveFont((float)(btnHeight * 0.4)))
        JComponent editor = trimSpinner.getEditor()
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField textField = ((JSpinner.DefaultEditor) editor).getTextField()
            textField.setFont(textField.getFont().deriveFont((float)(btnHeight * 0.4)))
            textField.setColumns(4)
        }
        trimSpinner.addChangeListener({ e ->
            trimLength = trimSpinnerModel.getNumber().intValue()
            saveTrimLength()
            if (trimMode || singleLineMode) {
                rowHeightCache.clear()
                resultsTable.repaint()
                updateAllRowHeights()
                int row = resultsTable.getSelectedRow()
                if (row != -1 && trimMode) showNodeDetails(row)
                else if (row != -1 && singleLineMode) resultsTable.repaint()
            }
        })
        statusBar.add(trimSpinner)
        
        SpinnerNumberModel ancestorTrimModel = new SpinnerNumberModel(ancestorTrimLength, 10, 200, 5)
        ancestorTrimSpinner = new JSpinner(ancestorTrimModel)
        ancestorTrimSpinner.setPreferredSize(new Dimension(150, btnHeight))
        ancestorTrimSpinner.addChangeListener({ e ->
            ancestorTrimLength = ancestorTrimModel.getNumber().intValue()
            saveAncestorTrimLength()
            clearCaches()
            resultsTable.repaint()
            refreshBreadcrumb()
        })
        statusBar.add(ancestorTrimSpinner)
        
        reversePathBtn = new JToggleButton("↺")
        reversePathBtn.addActionListener({ e ->
            reverseAncestorOrder = reversePathBtn.isSelected()
            saveReverseOrderSetting()
            pathCache.clear()
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Object val = tableModel.getValueAt(i, 9)
                if (val instanceof Object[] && ((Object[])val).length > 0) {
                    Node node = ((Object[])val)[0] as Node
                    if (node != null) {
                        String newPath = getAncestorsPathCached(node)
                        tableModel.setValueAt(newPath, i, 2)
                    }
                }
            }
            refreshBreadcrumb()
        })
        statusBar.add(reversePathBtn)
        
        ancestorModeBtn = new JToggleButton(useVisibleRootOnly ? "👁️" : "🗺️")
        ancestorModeBtn.addActionListener({ toggleAncestorMode() })
        statusBar.add(ancestorModeBtn)
        
        resultCountLabel = new JLabel("0 results")
        resultCountLabel.setHorizontalAlignment(SwingConstants.RIGHT)
        resultCountLabel.setFont(resultCountLabel.getFont().deriveFont(Font.BOLD))
        
        JButton infoButton = new JButton("ℹ️")
        infoButton.setContentAreaFilled(false)
        infoButton.setBorderPainted(false)
        infoButton.addActionListener({ e ->
            String message = "1. Alt+J to collapse/expand.\n" +
                             "2. Open Maps and Path search all nodes, Root/Siblings/Branch search only visible nodes.\n" +
                             "3. Benefits: View results as a list for better focus."
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), message, "Help", JOptionPane.INFORMATION_MESSAGE)
        })
        statusBar.add(Box.createHorizontalGlue())
        statusBar.add(Box.createRigidArea(new Dimension(10, 0)))
        statusBar.add(infoButton)
        
        JPanel bottomPanel = new JPanel(new BorderLayout())
        bottomPanel.add(filterPanel, BorderLayout.NORTH)
        bottomPanel.add(statusBar, BorderLayout.SOUTH)
        mainPanel.add(bottomPanel, BorderLayout.SOUTH)
        
        Action deleteAction = new AbstractAction() {
            void actionPerformed(ActionEvent e) {
                deleteSelectedNode()
            }
        }
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteNode")
        resultsTable.getActionMap().put("deleteNode", deleteAction)
        mainPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteNodeGlobal")
        mainPanel.getActionMap().put("deleteNodeGlobal", deleteAction)
        breadcrumbPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteNodeBread")
        breadcrumbPanel.getActionMap().put("deleteNodeBread", deleteAction)
        leftPreviewPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteNodeLeft")
        leftPreviewPanel.getActionMap().put("deleteNodeLeft", deleteAction)
        
        searchBtn.addActionListener({ doSearch() })
        searchField.addActionListener({ doSearch() })
        
        Action spaceAction = new AbstractAction() {
            void actionPerformed(ActionEvent e) {
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
                if (!(focusOwner instanceof JTextField || focusOwner instanceof JTextArea)) focusSearchField()
            }
        }
        mainPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("SPACE"), "focusSearch")
        mainPanel.getActionMap().put("focusSearch", spaceAction)
        
        updateModeDependencies()
        mainPanel.addMouseListener(new MouseAdapter() {
            void mouseEntered(MouseEvent e) {
                if (!resultsTable.hasFocus()) {
                    resultsTable.requestFocusInWindow()
                }
            }
            void mouseExited(MouseEvent e) {
                SwingUtilities.invokeLater {
                    try {
                        def mapView = Controller.getCurrentController().getMapViewManager().getMapViewComponent()
                        if (mapView != null) mapView.requestFocusInWindow()
                    } catch (Exception ex) { }
                }
            }
        })
        mainPanel.setFocusable(true)
        return mainPanel
    }

    // ========== Helper Methods ==========
    private Node getActiveViewRoot() {
        try {
            return ScriptUtils.c().viewRoot
        } catch (Exception e) {
            return null
        }
    }

    private String computeAncestorsPath(Node node, boolean visibleOnly) {
        if (node == null) return ""
        def fullPath = node.getPathToRoot()
        if (fullPath == null || fullPath.isEmpty()) return ""
        if (!fullPath[0].isRoot()) {
            fullPath = fullPath.reverse()
        }
        if (fullPath.size() <= 1) return ""
        def ancestors = fullPath[0..-2]
        if (visibleOnly) {
            def viewRoot = getActiveViewRoot()
            if (viewRoot != null && node.mindMap == viewRoot.mindMap) {
                int idx = fullPath.indexOf(viewRoot)
                if (idx >= 0) {
                    if (idx < fullPath.size() - 1) {
                        ancestors = fullPath[idx..-2]
                    } else {
                        ancestors = []
                    }
                }
            }
        }
        def names = ancestors.collect { it.plainText ?: "?" }
        if (reverseAncestorOrder) {
            return names.reverse().join(" → ")
        } else {
            return names.join(" ← ")
        }
    }

    private String getAncestorsPathCached(Node node) {
        if (node == null) return ""
        String key = "${node.getId()}_rev${reverseAncestorOrder}_vis${useVisibleRootOnly}"
        if (pathCache.containsKey(key)) return pathCache.get(key)
        String path = computeAncestorsPath(node, useVisibleRootOnly)
        pathCache.put(key, path)
        return path
    }

    private void refreshAllPaths() {
        pathCache.clear()
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object val = tableModel.getValueAt(i, 9)
            if (val instanceof Object[] && ((Object[])val).length > 0) {
                Node node = ((Object[])val)[0] as Node
                if (node != null) {
                    String newPath = getAncestorsPathCached(node)
                    tableModel.setValueAt(newPath, i, 2)
                }
            }
        }
        resultsTable.repaint()
    }

    private void toggleAncestorMode() {
        useVisibleRootOnly = !useVisibleRootOnly
        ancestorModeBtn.setText(useVisibleRootOnly ? "👁️" : "🗺️")
        saveSettingsToPrefs()
        refreshAllPaths()
        int selRow = resultsTable.getSelectedRow()
        if (selRow != -1) showNodeDetails(selRow)
        else clearPreview()
    }

    private void clearCaches() {
        pathCache.clear()
        rowHeightCache.clear()
        resultsTable.repaint()
    }

    private List<Node> collectVisibleNodes(Node root) {
        if (root == null) return new ArrayList<>()
        List<Node> result = new ArrayList<>()
        Deque<Node> queue = new ArrayDeque<>()
        queue.offer(root)
        while (!queue.isEmpty()) {
            Node node = queue.poll()
            try {
                if (node.isVisible()) {
                    result.add(node)
                }
                List<Node> children = new ArrayList<>(node.children)
                for (Node child : children) {
                    queue.offer(child)
                }
            } catch (Exception e) {
                System.err.println("Error processing node: ${node?.plainText} - ${e.message}")
            }
        }
        return result
    }

    private void refreshBreadcrumb() {
        int row = resultsTable.getSelectedRow()
        if (row != -1) showNodeDetails(row)
    }

    private void updateResultCount() {
        if (resultCountLabel != null) {
            int count = resultsTable.getRowCount()
            resultCountLabel.setText("$count result${count != 1 ? 's' : ''}")
            if (filterResultLabel != null) filterResultLabel.setText("$count result${count != 1 ? 's' : ''}")
        }
        if (filterResultLabel != null) filterResultLabel.setText(resultsTable.getRowCount() + " result" + (resultsTable.getRowCount() != 1 ? "s" : ""))
    }

    // ========== Column Toggle with Arrow Keys ==========
    private void toggleColumnsWithRightArrow() {
        if (!hideFileColumn) {
            hideFileColumn = true
        } else if (!hideStyleColumn) {
            hideStyleColumn = true
        } else if (!hidePathColumn) {
            hidePathColumn = true
        } else if (!hideDateColumn) {
            hideDateColumn = true
        } else if (!hideDateCreatedColumn) {
            hideDateCreatedColumn = true
        } else if (!hideIconsColumn) {
            hideIconsColumn = true
        } else if (!hideTagsColumn) {
            hideTagsColumn = true
        } else if (!hideDetailsColumn) {
            hideDetailsColumn = true
        } else if (!hideNoteColumn) {
            hideNoteColumn = true
        } else {
            return
        }
        rowHeightCache.clear()
        applySettings()
        saveSettingsToPrefs()
        saveColumnWidths()
        updateColumnDialogItems()
    }

    private void toggleColumnsWithLeftArrow() {
        if (hideNoteColumn && hideDetailsColumn && hideTagsColumn && hideIconsColumn && hideDateCreatedColumn && hideDateColumn && hidePathColumn && hideStyleColumn && hideFileColumn) {
            hideNoteColumn = false
        } else if (hideDetailsColumn && hideTagsColumn && hideIconsColumn && hideDateCreatedColumn && hideDateColumn && hidePathColumn && hideStyleColumn && hideFileColumn) {
            hideDetailsColumn = false
        } else if (hideTagsColumn && hideIconsColumn && hideDateCreatedColumn && hideDateColumn && hidePathColumn && hideStyleColumn && hideFileColumn) {
            hideTagsColumn = false
        } else if (hideIconsColumn && hideDateCreatedColumn && hideDateColumn && hidePathColumn && hideStyleColumn && hideFileColumn) {
            hideIconsColumn = false
        } else if (hideDateCreatedColumn && hideDateColumn && hidePathColumn && hideStyleColumn && hideFileColumn) {
            hideDateCreatedColumn = false
        } else if (hideDateColumn && hidePathColumn && hideStyleColumn && hideFileColumn) {
            hideDateColumn = false
        } else if (hidePathColumn && hideStyleColumn && hideFileColumn) {
            hidePathColumn = false
        } else if (hideStyleColumn && hideFileColumn) {
            hideStyleColumn = false
        } else if (hideFileColumn) {
            hideFileColumn = false
        } else {
            return
        }
        rowHeightCache.clear()
        applySettings()
        saveSettingsToPrefs()
        saveColumnWidths()
        updateColumnDialogItems()
    }

    // ========== Display Mode Methods ==========
    private void setMode(String mode) {
        if (mode == 'trim') {
            trimMode = true
            singleLineMode = false
            fullMode = false
        } else if (mode == 'single') {
            trimMode = false
            singleLineMode = true
            fullMode = false
        } else if (mode == 'full') {
            trimMode = false
            singleLineMode = false
            fullMode = true
        }
        updateModeDependencies()
        saveModeSettings()
        rowHeightCache.clear()
        JScrollPane tableScroll = (JScrollPane) resultsTable.getParent().getParent()
        if (tableScroll != null) {
            tableScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        }
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
        updateBaseLineHeight()
        updateAllRowHeights()
        adjustLastColumnWidth()
        resultsTable.revalidate()
        resultsTable.repaint()
        int row = resultsTable.getSelectedRow()
        if (row != -1) {
            showNodeDetails(row)
        }
        updateResultCount()
    }

    private void updateModeDependencies() {
        trimBtn.setSelected(trimMode)
        singleBtn.setSelected(singleLineMode)
        fullBtn.setSelected(fullMode)
        if (trimSpinner != null) {
            trimSpinner.setEnabled(trimMode || singleLineMode)
        }
    }

    class FontAwareRenderer extends DefaultTableCellRenderer {
        private int columnIndex
        FontAwareRenderer(int col) { columnIndex = col }
        private Node getNodeAtRow(JTable table, int row) {
            int modelRow = table.convertRowIndexToModel(row)
            Object value = table.getModel().getValueAt(modelRow, 9)
            if (value instanceof Object[] && ((Object[])value).length > 0) return ((Object[])value)[0] as Node
            if (value instanceof Node) return value as Node
            return null
        }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
            String originalText = node?.plainText ?: ""
            String displayText = originalText
            if (trimMode && !singleLineMode && !fullMode) {
                if (displayText.length() > trimLength)
                    displayText = TextUtils.getShortText(displayText, trimLength, "\u2026")
            } else if (singleLineMode) {
                if (displayText.length() > trimLength)
                    displayText = TextUtils.getShortText(displayText, trimLength, "\u2026")
                TableColumn tableCol = table.getColumnModel().getColumn(9)
                int colWidth = tableCol.getWidth()
                int padding = 21
                int availableWidth = colWidth - padding
                if (availableWidth > 0) {
                    Font useFont = (fontWeightColumn != null) ? fontWeightColumn : table.getFont()
                    FontMetrics fm = getFontMetrics(useFont)
                    String ellipsis = "\u2026"
                    int ellipsisWidth = fm.stringWidth(ellipsis)
                    if (fm.stringWidth(displayText) > availableWidth) {
                        for (int i = displayText.length(); i > 0; i--) {
                            String sub = displayText.substring(0, i)
                            if (fm.stringWidth(sub) + ellipsisWidth <= availableWidth) {
                                displayText = sub + ellipsis
                                break
                            }
                        }
                    }
                }
            }
            // ====== هایلایت چند کلمه‌ای ======
            String highlightWord = null
            if (currentFilterText != null && !currentFilterText.isEmpty())
                highlightWord = currentFilterText
            else if (lastSearchKeyword != null && !lastSearchKeyword.isEmpty())
                highlightWord = lastSearchKeyword
            if (highlightWord != null && !highlightWord.isEmpty()) {
                List<String> wordsToHighlight = []
                if (highlightWord.contains("|")) {
                    wordsToHighlight = highlightWord.split("\\|").collect { it.trim() }.findAll { !it.isEmpty() }
                } else {
                    wordsToHighlight = [highlightWord]
                }
                for (String word : wordsToHighlight) {
                    if (word.isEmpty()) continue
                    String patternStr = Pattern.quote(word)
                    try {
                        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE
                        Pattern p = Pattern.compile("($patternStr)", flags)
                        Matcher m = p.matcher(displayText)
                        StringBuffer sb = new StringBuffer()
                        while (m.find())
                            m.appendReplacement(sb, "<span style='background-color: yellow;'>\$1</span>")
                        m.appendTail(sb)
                        displayText = sb.toString()
                    } catch (Exception e) {}
                }
            }
            // ===================================
            if (singleLineMode) {
                JLabel label = new JLabel()
                label.setOpaque(true)
                Color nodeBg = getNodeBackgroundColor(node)
                Color nodeFg = getNodeForegroundColor(node)
                if (isSelected) {
                    label.setBackground(table.getSelectionBackground())
                    label.setForeground(table.getSelectionForeground())
                } else {
                    if (nodeBg != null) {
                        label.setBackground(nodeBg)
                    } else {
                        label.setBackground(table.getBackground())
                    }
                    if (nodeFg != null) {
                        label.setForeground(nodeFg)
                    } else {
                        label.setForeground(table.getForeground())
                    }
                }
                Font useFont = (fontWeightColumn != null) ? fontWeightColumn : table.getFont()
                label.setFont(useFont)
                label.setText(displayText)
                label.setHorizontalAlignment(SwingConstants.LEFT)
                label.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 5, 0, 0, getBorderColorForNode(node)),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)
                ))
                return label
            } else {
                String htmlText = displayText.contains("<span") ? displayText : escapeHtml(displayText)
                Font useFont = (fontWeightColumn != null) ? fontWeightColumn : table.getFont()
                setFont(useFont)
                String styleAttr = "font-family: " + useFont.getFamily() + "; font-size: " + useFont.getSize() + "pt; direction: rtl; text-align: right; margin: 0; padding: 0; line-height: normal;"
                if (!fullMode) styleAttr += " white-space: normal;" else styleAttr += " white-space: normal;"
                setText("<html><body style='" + styleAttr + "'>" + htmlText + "</body></html>")
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 5, 0, 0, getBorderColorForNode(node)),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)
                ))
                Color nodeBg = getNodeBackgroundColor(node)
                Color nodeFg = getNodeForegroundColor(node)
                if (isSelected) {
                    setBackground(table.getSelectionBackground())
                    setForeground(table.getSelectionForeground())
                } else {
                    if (nodeBg != null) {
                        setBackground(nodeBg)
                    } else {
                        setBackground(table.getBackground())
                    }
                    if (nodeFg != null) {
                        setForeground(nodeFg)
                    } else {
                        setForeground(table.getForeground())
                    }
                }
                return this
            }
        }
    }

    class FontAwareDateRenderer extends DefaultTableCellRenderer {
        FontAwareDateRenderer() { setHorizontalAlignment(SwingConstants.CENTER) }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col)
            Font f = (col == 3) ? fontDateColumn : fontDateCreatedColumn
            if (f != null) setFont(f)
            setHorizontalAlignment(SwingConstants.CENTER)
            setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
            return this
        }
    }

    class FontAwarePathRenderer extends JTextArea implements TableCellRenderer {
        FontAwarePathRenderer() {
            setOpaque(true)
            setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5))
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            setLineWrap(true)
            setWrapStyleWord(true)
        }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String originalText = (value != null) ? value.toString() : ""
            String displayText = originalText
            if (!fullMode) {
                int columnWidth = table.getColumnModel().getColumn(col).getWidth()
                String flatText = originalText.replace('\n', ' ')
                FontMetrics fm = getFontMetrics(getFont())
                int textWidth = fm.stringWidth(flatText)
                if (textWidth + 10 > columnWidth) {
                    String ellipsis = "\u2026"
                    int ellipsisWidth = fm.stringWidth(ellipsis)
                    int availableWidth = columnWidth - 10 - ellipsisWidth
                    if (availableWidth > 0) {
                        StringBuilder sb = new StringBuilder()
                        for (int i = 0; i < flatText.length(); i++) {
                            sb.append(flatText.charAt(i))
                            if (fm.stringWidth(sb.toString()) > availableWidth) {
                                sb.deleteCharAt(sb.length() - 1)
                                break
                            }
                        }
                        displayText = sb.toString() + ellipsis
                    } else {
                        displayText = ellipsis
                    }
                }
            }
            setText(displayText)
            Font useFont = (fontPathColumn != null) ? fontPathColumn : new Font("Segoe UI", Font.PLAIN, 14)
            setFont(useFont)
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            setForeground(table.getForeground())
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground())
            if (!disableTooltips && !originalText.equals(displayText)) {
                setToolTipText(originalText)
            } else {
                setToolTipText(null)
            }
            int colWidth = table.getColumnModel().getColumn(col).getWidth()
            setSize(colWidth, Short.MAX_VALUE)
            return this
        }
    }

    class FontAwareIconsRenderer extends JPanel implements TableCellRenderer {
        FontAwareIconsRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2))
            setOpaque(true)
        }
        private Node getNodeFromTable(JTable table, int row) {
            int modelRow = table.convertRowIndexToModel(row)
            Object value = table.getModel().getValueAt(modelRow, 9)
            if (value instanceof Object[] && ((Object[])value).length > 0) return ((Object[])value)[0] as Node
            if (value instanceof Node) return value as Node
            return null
        }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            removeAll()
            Node node = getNodeFromTable(table, row)
            if (node == null) {
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground())
                return this
            }
            StringBuilder fullTooltip = new StringBuilder()
            NodeModel nodeModel = getNodeModel(node)
            def icons = iconController().getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE)
            icons.each { namedIcon ->
                if (namedIcon.getIcon() != null) {
                    JLabel label = new JLabel(namedIcon.getIcon())
                    label.setToolTipText(namedIcon.getName())
                    add(label)
                    if (fullTooltip.length() > 0) fullTooltip.append(", ")
                    fullTooltip.append(namedIcon.getName())
                }
            }
            if (fullTooltip.length() > 0) setToolTipText(fullTooltip.toString())
            else setToolTipText(null)
            if (isSelected) {
                setBackground(table.getSelectionBackground())
                setForeground(table.getSelectionForeground())
            } else {
                setBackground(table.getBackground())
                setForeground(table.getForeground())
            }
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            return this
        }
    }

    class FontAwareTagsRenderer extends JPanel implements TableCellRenderer {
        FontAwareTagsRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2))
            setOpaque(true)
        }
        private Node getNodeFromTable(JTable table, int row) {
            int modelRow = table.convertRowIndexToModel(row)
            Object value = table.getModel().getValueAt(modelRow, 9)
            if (value instanceof Object[] && ((Object[])value).length > 0) return ((Object[])value)[0] as Node
            if (value instanceof Node) return value as Node
            return null
        }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            removeAll()
            Node node = getNodeFromTable(table, row)
            if (node == null) {
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground())
                return this
            }
            List<String> tagNames = new ArrayList<>()
            NodeModel nodeModel = getNodeModel(node)
            def tags = iconController().getTagIcons(nodeModel)
            def sortedTags = tags.sort { a, b ->
                String nameA = getNameFromTagIcon(a)
                String nameB = getNameFromTagIcon(b)
                nameA.compareToIgnoreCase(nameB)
            }
            sortedTags.each { tag ->
                if (tag instanceof Icon) {
                    JLabel label = new JLabel(tag)
                    String tagName = getNameFromTagIcon(tag)
                    label.setToolTipText(tagName)
                    add(label)
                    tagNames.add(tagName)
                } else if (tag instanceof JLabel) {
                    String tagName = getNameFromTagIcon(tag)
                    tag.setToolTipText(tagName)
                    add(tag)
                    tagNames.add(tagName)
                } else {
                    String txt = tag.toString()
                    String tagName = getNameFromTagIcon(tag)
                    JLabel label = new JLabel(txt)
                    label.setToolTipText(tagName)
                    add(label)
                    tagNames.add(tagName)
                }
            }
            if (!tagNames.isEmpty()) {
                String tooltipText = "<html>" + tagNames.join("<br>") + "</html>"
                setToolTipText(tooltipText)
            } else {
                setToolTipText(null)
            }
            if (isSelected) {
                setBackground(table.getSelectionBackground())
                setForeground(table.getSelectionForeground())
            } else {
                setBackground(table.getBackground())
                setForeground(table.getForeground())
            }
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            return this
        }
        private String getNameFromTagIcon(Object tagIcon) {
            if (tagIcon == null) return ""
            if (tagIcon instanceof NamedIcon) {
                return tagIcon.getName() ?: tagIcon.toString()
            }
            if (tagIcon instanceof JLabel) {
                return tagIcon.getToolTipText() ?: tagIcon.getText() ?: ""
            }
            String className = tagIcon.getClass().getName()
            if (className.contains("TagIcon")) {
                try {
                    Field field = tagIcon.getClass().getDeclaredField("tag")
                    field.setAccessible(true)
                    Object tagValue = field.get(tagIcon)
                    if (tagValue != null) {
                        String tagStr = tagValue.toString()
                        if (tagStr.contains("=")) {
                            int eqIdx = tagStr.lastIndexOf("=")
                            if (eqIdx != -1) tagStr = tagStr.substring(eqIdx + 1)
                        }
                        if (tagStr.contains(",")) tagStr = tagStr.split(",")[0]
                        return tagStr.trim()
                    }
                } catch (Exception ignored) {}
                String str = tagIcon.toString()
                def matcher = (str =~ /tag=(.*?)(?:,|$)/)
                if (matcher.find()) return matcher.group(1).trim()
                return str
            }
            return tagIcon.toString()
        }
    }

    class FontAwareHtmlRenderer extends JLabel implements TableCellRenderer {
        private int columnIndex
        FontAwareHtmlRenderer(int col) {
            this.columnIndex = col
            setOpaque(true)
            setVerticalAlignment(SwingConstants.TOP)
            setHorizontalAlignment(SwingConstants.RIGHT)
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            Node node = null
            int modelRow = table.convertRowIndexToModel(row)
            Object nodeValue = table.getModel().getValueAt(modelRow, 9)
            if (nodeValue instanceof Object[] && ((Object[])nodeValue).length > 0) {
                node = ((Object[])nodeValue)[0] as Node
            } else if (nodeValue instanceof Node) {
                node = nodeValue as Node
            }
            Font useFont = (columnIndex == 7) ? fontDetailsColumn : fontNoteColumn
            if (useFont == null) useFont = table.getFont()
            int colWidth = table.getColumnModel().getColumn(col).getWidth()
            String rawText = (value != null) ? value.toString() : ""
            if (columnIndex == 7 && node?.getDetails()?.getHtml()) {
                rawText = node.getDetails().getHtml()
            } else if (columnIndex == 8 && node?.getNote()?.getHtml()) {
                rawText = node.getNote().getHtml()
            }
            String styledContent = getStyledCellContent(rawText, node, useFont, true, colWidth)
            setText("<html>${styledContent}</html>")
            setFont(useFont)
            Color nodeBg = getNodeBackgroundColor(node)
            setBackground(nodeBg ?: table.getBackground())
            if (!singleLineMode || !rawText.trim().startsWith("<")) {
                Color nodeFg = getNodeForegroundColor(node)
                setForeground(nodeFg ?: table.getForeground())
            }
            Border originalBorder = BorderFactory.createEmptyBorder(2, 5, 2, 5)
            if (isSelected) {
                Border blueBorder = BorderFactory.createLineBorder(Color.BLUE, 2)
                setBorder(BorderFactory.createCompoundBorder(blueBorder, originalBorder))
            } else {
                setBorder(originalBorder)
            }
            return this
        }
    }

    class FontAwareNodeRenderer extends JLabel implements TableCellRenderer {
        FontAwareNodeRenderer() {
            setOpaque(true)
            setVerticalAlignment(SwingConstants.TOP)
            setHorizontalAlignment(SwingConstants.RIGHT)
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
            if (node == null) {
                setText("")
                return this
            }
            Font useFont = (fontWeightColumn != null) ? fontWeightColumn : table.getFont()
            int colWidth = table.getColumnModel().getColumn(9).getWidth()
            String rawText = node.getHtmlText() ?: node.getPlainText()
            
            String content = rawText
            boolean isHtml = rawText != null && rawText.trim().startsWith("<")
            if (!isHtml) {
                content = escapeHtml(rawText).replace("\n", "<br>")
            }
            
            // ====== هایلایت چند کلمه‌ای ======
            String highlightWord = (currentFilterText != null && !currentFilterText.isEmpty()) ? currentFilterText : lastSearchKeyword
            if (highlightWord != null && !highlightWord.isEmpty()) {
                List<String> wordsToHighlight = []
                if (highlightWord.contains("|")) {
                    wordsToHighlight = highlightWord.split("\\|").collect { it.trim() }.findAll { !it.isEmpty() }
                } else {
                    wordsToHighlight = [highlightWord]
                }
                for (String word : wordsToHighlight) {
                    if (word.isEmpty()) continue
                    String patternStr = Pattern.quote(word)
                    try {
                        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE
                        Pattern p = Pattern.compile("($patternStr)", flags)
                        Matcher m = p.matcher(content)
                        StringBuffer sb = new StringBuffer()
                        while (m.find()) {
                            m.appendReplacement(sb, "<span style='background-color: yellow;'>\$1</span>")
                        }
                        m.appendTail(sb)
                        content = sb.toString()
                    } catch (Exception e) {}
                }
            }
            // ===================================
            
            Color fgColor = getNodeForegroundColor(node)
            String colorStyle = (fgColor != null) ? "color: rgb(${fgColor.red}, ${fgColor.green}, ${fgColor.blue});" : ""
            String family = useFont.getFamily()
            int size = useFont.getSize()
            String weight = useFont.isBold() ? "bold" : "normal"
            String styleFlag = useFont.isItalic() ? "italic" : "normal"
            String wrapperStyle = "font-family: ${family}; font-size: ${size}pt; font-weight: ${weight}; font-style: ${styleFlag}; ${colorStyle} margin:0; padding:0; direction: rtl; text-align: right;"
            wrapperStyle += " white-space: normal;"
            
            setText("<html><div style=\"${wrapperStyle}\">${content}</div></html>")
            setFont(useFont)
            Color nodeBg = getNodeBackgroundColor(node)
            setBackground(nodeBg ?: table.getBackground())
            Color nodeFg = getNodeForegroundColor(node)
            setForeground(nodeFg ?: table.getForeground())
            Border leftColorBorder = BorderFactory.createMatteBorder(0, 5, 0, 0, getBorderColorForNode(node))
            Border paddingBorder = BorderFactory.createEmptyBorder(2, 8, 2, 8)
            Border originalBorder = BorderFactory.createCompoundBorder(leftColorBorder, paddingBorder)
            if (isSelected) {
                Border selectionBorder = BorderFactory.createLineBorder(Color.BLUE, 2)
                setBorder(BorderFactory.createCompoundBorder(selectionBorder, originalBorder))
            } else {
                setBorder(originalBorder)
            }
            return this
        }
    }

    private void showSettingsDialog() {
        if (settingsDialog != null && settingsDialog.isVisible()) { settingsDialog.toFront(); return }
        settingsDialog = new JDialog(UITools.getCurrentFrame(), "Settings", true)
        settingsDialog.setLayout(new BorderLayout())
        JPanel contentPanel = new JPanel(new GridBagLayout())
        GridBagConstraints gbc = new GridBagConstraints()
        gbc.insets = new Insets(5, 10, 5, 10)
        gbc.anchor = GridBagConstraints.WEST
        gbc.gridx = 0
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.gridy = 0; contentPanel.add(createFontSettingPanel("File column:", fontFileColumn, { f -> fontFileColumn = f }), gbc)
        gbc.gridy = 1; contentPanel.add(createFontSettingPanel("Style column:", fontStyleColumn, { f -> fontStyleColumn = f }), gbc)
        gbc.gridy = 2; contentPanel.add(createFontSettingPanel("Ancestors column:", fontPathColumn, { f -> fontPathColumn = f }), gbc)
        gbc.gridy = 3; contentPanel.add(createFontSettingPanel("Date Modified column:", fontDateColumn, { f -> fontDateColumn = f }), gbc)
        gbc.gridy = 4; contentPanel.add(createFontSettingPanel("Date Created column:", fontDateCreatedColumn, { f -> fontDateCreatedColumn = f }), gbc)
        gbc.gridy = 5; contentPanel.add(createFontSettingPanel("Icons column:", fontIconsColumn, { f -> fontIconsColumn = f }), gbc)
        gbc.gridy = 6; contentPanel.add(createFontSettingPanel("Tags column:", fontTagsColumn, { f -> fontTagsColumn = f }), gbc)
        gbc.gridy = 7; contentPanel.add(createFontSettingPanel("Details column:", fontDetailsColumn, { f -> fontDetailsColumn = f }), gbc)
        gbc.gridy = 8; contentPanel.add(createFontSettingPanel("Note column:", fontNoteColumn, { f -> fontNoteColumn = f }), gbc)
        gbc.gridy = 9; contentPanel.add(createFontSettingPanel("Node column:", fontWeightColumn, { f -> fontWeightColumn = f }), gbc)
        gbc.gridy = 10; contentPanel.add(createFontSettingPanel("Preview Core:", fontPreviewCore, { f -> fontPreviewCore = f }), gbc)
        gbc.gridy = 11; contentPanel.add(createFontSettingPanel("Details Preview:", fontPreviewDetails, { f -> fontPreviewDetails = f }), gbc)
        gbc.gridy = 12; contentPanel.add(createFontSettingPanel("Note Preview:", fontPreviewNote, { f -> fontPreviewNote = f }), gbc)
        gbc.gridy = 13; contentPanel.add(createFontSettingPanel("Breadcrumb:", fontBreadcrumb, { f -> fontBreadcrumb = f }), gbc)
        JCheckBox disableTooltipCheck = new JCheckBox("Disable tooltips on results", disableTooltips)
        gbc.gridy = 14; contentPanel.add(disableTooltipCheck, gbc)
        
        // Show Only Breadcrumbs option
        breadcrumbOnlyCheck = new JCheckBox("Show Only Breadcrumbs", showOnlyBreadcrumbs)
        gbc.gridy = 15; contentPanel.add(breadcrumbOnlyCheck, gbc)
        
        JScrollPane scrollPane = new JScrollPane(contentPanel)
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS)
        scrollPane.getVerticalScrollBar().setUnitIncrement(16)
        scrollPane.setBorder(BorderFactory.createEmptyBorder())
        JButton saveBtn = new JButton("Save")
        JButton cancelBtn = new JButton("Cancel")
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER))
        btnPanel.add(saveBtn)
        btnPanel.add(cancelBtn)
        settingsDialog.add(scrollPane, BorderLayout.CENTER)
        settingsDialog.add(btnPanel, BorderLayout.SOUTH)
        settingsDialog.setSize(750, 650)
        settingsDialog.setLocationRelativeTo(UITools.getCurrentFrame())
        saveBtn.addActionListener({ e ->
            disableTooltips = disableTooltipCheck.isSelected()
            showOnlyBreadcrumbs = breadcrumbOnlyCheck.isSelected()
            saveSettingsToPrefs()
            saveFontSettings()
            applySettings()
            applyFontsToComponents()
            applyBreadcrumbOnlyMode()
            int selectedRow = resultsTable.getSelectedRow()
            if (selectedRow != -1) showNodeDetails(selectedRow)
            settingsDialog.dispose()
        })
        cancelBtn.addActionListener({ e -> settingsDialog.dispose() })
        settingsDialog.setVisible(true)
    }

    private JPanel createFontSettingPanel(String labelText, Font currentFont, Closure onFontSelected) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT))
        panel.add(new JLabel(labelText))
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        JComboBox<String> fontCombo = new JComboBox<>(fontNames)
        int defaultSize = 12
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(defaultSize, 8, 48, 1))
        if (currentFont != null) {
            fontCombo.setSelectedItem(currentFont.getFamily())
            sizeSpinner.setValue(currentFont.getSize())
        } else {
            fontCombo.setSelectedItem(UIManager.getFont("Label.font").getFamily())
            sizeSpinner.setValue(UIManager.getFont("Label.font").getSize())
        }
        JLabel previewLabel = new JLabel(" Sample Text ")
        previewLabel.setOpaque(true); previewLabel.setBackground(Color.WHITE)
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY))
        Runnable updatePreview = {
            String fam = fontCombo.getSelectedItem()
            int sz = sizeSpinner.getValue()
            Font f = new Font(fam, Font.PLAIN, sz)
            previewLabel.setFont(f); previewLabel.repaint()
        }
        fontCombo.addActionListener({ updatePreview.run() })
        sizeSpinner.addChangeListener({ updatePreview.run() })
        updatePreview.run()
        JButton applyBtn = new JButton("Apply")
        applyBtn.addActionListener({
            String fam = fontCombo.getSelectedItem()
            int sz = sizeSpinner.getValue()
            Font newFont = new Font(fam, Font.PLAIN, sz)
            onFontSelected(newFont); previewLabel.setFont(newFont)
        })
        panel.add(fontCombo); panel.add(sizeSpinner); panel.add(applyBtn); panel.add(previewLabel)
        return panel
    }

    private void saveFontSettings() {
        def rc = ResourceController.getResourceController()
        saveFontProp(rc, "mapcrawler.fontFileColumn", fontFileColumn)
        saveFontProp(rc, "mapcrawler.fontStyleColumn", fontStyleColumn)
        saveFontProp(rc, "mapcrawler.fontPathColumn", fontPathColumn)
        saveFontProp(rc, "mapcrawler.fontDateColumn", fontDateColumn)
        saveFontProp(rc, "mapcrawler.fontDateCreatedColumn", fontDateCreatedColumn)
        saveFontProp(rc, "mapcrawler.fontIconsColumn", fontIconsColumn)
        saveFontProp(rc, "mapcrawler.fontTagsColumn", fontTagsColumn)
        saveFontProp(rc, "mapcrawler.fontDetailsColumn", fontDetailsColumn)
        saveFontProp(rc, "mapcrawler.fontNoteColumn", fontNoteColumn)
        saveFontProp(rc, "mapcrawler.fontWeightColumn", fontWeightColumn)
        saveFontProp(rc, "mapcrawler.fontPreviewCore", fontPreviewCore)
        saveFontProp(rc, "mapcrawler.fontPreviewDetails", fontPreviewDetails)
        saveFontProp(rc, "mapcrawler.fontPreviewNote", fontPreviewNote)
        saveFontProp(rc, "mapcrawler.fontBreadcrumb", fontBreadcrumb)
    }

    private void saveFontProp(ResourceController rc, String key, Font f) {
        if (f != null) rc.setProperty(key, f.getFamily() + "|" + f.getStyle() + "|" + f.getSize())
        else rc.setProperty(key, null)
    }

    private void loadFontSettings() {
        def rc = ResourceController.getResourceController()
        fontFileColumn = loadFontProp(rc, "mapcrawler.fontFileColumn")
        fontStyleColumn = loadFontProp(rc, "mapcrawler.fontStyleColumn")
        fontPathColumn = loadFontProp(rc, "mapcrawler.fontPathColumn")
        fontDateColumn = loadFontProp(rc, "mapcrawler.fontDateColumn")
        fontDateCreatedColumn = loadFontProp(rc, "mapcrawler.fontDateCreatedColumn")
        fontIconsColumn = loadFontProp(rc, "mapcrawler.fontIconsColumn")
        fontTagsColumn = loadFontProp(rc, "mapcrawler.fontTagsColumn")
        fontDetailsColumn = loadFontProp(rc, "mapcrawler.fontDetailsColumn")
        fontNoteColumn = loadFontProp(rc, "mapcrawler.fontNoteColumn")
        fontWeightColumn = loadFontProp(rc, "mapcrawler.fontWeightColumn")
        fontPreviewCore = loadFontProp(rc, "mapcrawler.fontPreviewCore")
        fontPreviewDetails = loadFontProp(rc, "mapcrawler.fontPreviewDetails")
        fontPreviewNote = loadFontProp(rc, "mapcrawler.fontPreviewNote")
        fontBreadcrumb = loadFontProp(rc, "mapcrawler.fontBreadcrumb")
        if (fontWeightColumn == null) fontWeightColumn = new Font("Segoe UI", Font.PLAIN, 16)
        if (fontFileColumn == null) fontFileColumn = new Font("Segoe UI", Font.PLAIN, 14)
        if (fontStyleColumn == null) fontStyleColumn = new Font("Segoe UI", Font.PLAIN, 14)
        if (fontPathColumn == null) fontPathColumn = new Font("Segoe UI", Font.PLAIN, 14)
        if (fontDateColumn == null) fontDateColumn = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontDateCreatedColumn == null) fontDateCreatedColumn = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontIconsColumn == null) fontIconsColumn = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontTagsColumn == null) fontTagsColumn = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontDetailsColumn == null) fontDetailsColumn = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontNoteColumn == null) fontNoteColumn = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontPreviewCore == null) fontPreviewCore = new Font("Segoe UI", Font.PLAIN, 14)
        if (fontPreviewDetails == null) fontPreviewDetails = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontPreviewNote == null) fontPreviewNote = new Font("Segoe UI", Font.PLAIN, 12)
        if (fontBreadcrumb == null) fontBreadcrumb = new Font("Segoe UI", Font.PLAIN, 12)
    }

    private Font loadFontProp(ResourceController rc, String key) {
        String val = rc.getProperty(key)
        if (val != null && !val.isEmpty()) {
            def parts = val.split("\\|")
            if (parts.length == 3) return new Font(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))
        }
        return null
    }

    private void applyFontsToComponents() {
        if (resultsTable != null) {
            rowHeightCache.clear()
            resultsTable.repaint()
        }
        if (previewCore != null && fontPreviewCore != null)
            previewCore.setFont(fontPreviewCore.deriveFont((float)(fontPreviewCore.getSize() + 4)))
        if (previewDetails != null && fontPreviewDetails != null)
            previewDetails.setFont(fontPreviewDetails)
        if (previewNote != null && fontPreviewNote != null)
            previewNote.setFont(fontPreviewNote)
        if (breadcrumbPanel != null && fontBreadcrumb != null) {
            for (Component comp : breadcrumbPanel.getComponents()) {
                if (comp instanceof JPanel) {
                    for (Component inner : ((JPanel)comp).getComponents()) {
                        if (inner instanceof JRadioButton) inner.setFont(fontBreadcrumb)
                    }
                }
            }
            breadcrumbPanel.revalidate()
            breadcrumbPanel.repaint()
        }
        updateBaseLineHeight()
        updateAllRowHeights()
        if (resultsTable != null) resultsTable.repaint()
    }

    // ========== Filter Methods ==========
    private void applyFilter() {
        if (filterDebouncer != null && filterDebouncer.isRunning()) {
            filterDebouncer.stop()
        }
        filterDebouncer = new Timer(400, { e ->
            actuallyApplyFilter()
        })
        filterDebouncer.setRepeats(false)
        filterDebouncer.start()
    }

    private void actuallyApplyFilter() {
        currentFilterText = filterField.getText().trim()
        if (currentFilterText.isEmpty()) {
            rowSorter.setRowFilter(null)
            filterResultLabel.setText("0 results")
            updateResultCount()
            return
        }
    
        String filterText = currentFilterText
        boolean isAnd = filterText.contains(" AND ")
        boolean isOr = filterText.contains(" OR ")
    
        List<String> searchTerms
        String operator
    
        if (isAnd) {
            searchTerms = filterText.split(" AND ").collect { it.trim().toLowerCase() }.findAll { !it.isEmpty() }
            operator = "AND"
        } else if (isOr) {
            searchTerms = filterText.split(" OR ").collect { it.trim().toLowerCase() }.findAll { !it.isEmpty() }
            operator = "OR"
        } else {
            searchTerms = [filterText.toLowerCase()]
            operator = "SINGLE"
        }
    
        if (searchTerms.isEmpty()) {
            rowSorter.setRowFilter(null)
            filterResultLabel.setText("0 results")
            updateResultCount()
            return
        }
    
        if (!filterHistory.contains(currentFilterText)) {
            filterHistory.add(0, currentFilterText)
            if (filterHistory.size() > 20) filterHistory.remove(filterHistory.size()-1)
            saveHistory()
        }
    
        // Set currentFilterText for multi-word highlighting
        if (operator == "AND" || operator == "OR") {
            currentFilterText = searchTerms.join("|")
        } else {
            currentFilterText = searchTerms[0]
        }
    
        final List<String> finalTerms = searchTerms
        final String finalOperator = operator
    
        rowSorter.setRowFilter(new RowFilter<TableModel, Integer>() {
            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                Object value = entry.getValue(9)
                Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
                String nodeText = node?.plainText ?: ""
                String lowerText = nodeText.toLowerCase()
    
                if (finalOperator == "AND") {
                    return finalTerms.every { term -> lowerText.contains(term) }
                } else if (finalOperator == "OR") {
                    return finalTerms.any { term -> lowerText.contains(term) }
                } else {
                    return lowerText.contains(finalTerms[0])
                }
            }
        })
    
        rowHeightCache.clear()
        resultsTable.repaint()
        if (resultsTable.getRowCount() > 0) {
            resultsTable.setRowSelectionInterval(0, 0)
            showNodeDetails(0)
        } else {
            clearPreview()
        }
    
        int count = resultsTable.getRowCount()
        String operatorText = (operator == "AND") ? " (AND)" : (operator == "OR") ? " (OR)" : ""
        filterResultLabel.setText("$count result${count != 1 ? 's' : ''}$operatorText")
        updateResultCount()
        updateAllRowHeights()
    }

    private void navigate(int direction) {
        int rowCount = resultsTable.getRowCount()
        if (rowCount == 0) return
        int current = resultsTable.getSelectedRow()
        if (current == -1) current = 0
        int newRow = current + direction
        if (newRow < 0) newRow = rowCount - 1
        if (newRow >= rowCount) newRow = 0
        resultsTable.setRowSelectionInterval(newRow, newRow)
        resultsTable.scrollRectToVisible(resultsTable.getCellRect(newRow, 0, true))
        showNodeDetails(newRow)
    }

    private void selectFolder() {
        JFileChooser fc = new JFileChooser()
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
        fc.setDialogTitle("Select Folder containing .mm files")
        if (new File(baseDir).exists()) fc.setCurrentDirectory(new File(baseDir))
        if (fc.showOpenDialog(UITools.getCurrentFrame()) == JFileChooser.APPROVE_OPTION) {
            baseDir = fc.getSelectedFile().absolutePath
            folderLabel.setText(shortenPath(baseDir))
            saveSettings()
            tableModel.setRowCount(0)
            clearPreview()
            updateResultCount()
        }
    }

    private void saveSettings() {
        ResourceController.getResourceController().setProperty("mapcrawler.lastUsedDir", baseDir)
        saveSettingsToPrefs()
    }

    private void saveSettingsToPrefs() {
        def rc = ResourceController.getResourceController()
        rc.setProperty("mapcrawler.disableTooltips", String.valueOf(disableTooltips))
        rc.setProperty("mapcrawler.hideFileColumn", String.valueOf(hideFileColumn))
        rc.setProperty("mapcrawler.hideStyleColumn", String.valueOf(hideStyleColumn))
        rc.setProperty("mapcrawler.hidePathColumn", String.valueOf(hidePathColumn))
        rc.setProperty("mapcrawler.hideDateColumn", String.valueOf(hideDateColumn))
        rc.setProperty("mapcrawler.hideDateCreatedColumn", String.valueOf(hideDateCreatedColumn))
        rc.setProperty("mapcrawler.hideIconsColumn", String.valueOf(hideIconsColumn))
        rc.setProperty("mapcrawler.hideTagsColumn", String.valueOf(hideTagsColumn))
        rc.setProperty("mapcrawler.hideDetailsColumn", String.valueOf(hideDetailsColumn))
        rc.setProperty("mapcrawler.hideNoteColumn", String.valueOf(hideNoteColumn))
        rc.setProperty("mapcrawler.hideNodeColumn", String.valueOf(hideNodeColumn))
        rc.setProperty("mapcrawler.hideDetailsPreview", String.valueOf(hideDetailsPreview))
        rc.setProperty("mapcrawler.hideNotePreview", String.valueOf(hideNotePreview))
        rc.setProperty("mapcrawler.hidePreviewPanel", String.valueOf(hidePreviewPanel))
        rc.setProperty("mapcrawler.reverseAncestorOrder", String.valueOf(reverseAncestorOrder))
        rc.setProperty("mapcrawler.ancestorTrimLength", String.valueOf(ancestorTrimLength))
        rc.setProperty("mapcrawler.useVisibleRootOnly", String.valueOf(useVisibleRootOnly))
        rc.setProperty("mapcrawler.showOnlyBreadcrumbs", String.valueOf(showOnlyBreadcrumbs))
        saveModeSettings()
        saveTrimLength()
        saveHistory()
    }
    
    private void saveReverseOrderSetting() {
        ResourceController.getResourceController().setProperty("mapcrawler.reverseAncestorOrder", String.valueOf(reverseAncestorOrder))
    }

    private void saveAncestorTrimLength() {
        ResourceController.getResourceController().setProperty("mapcrawler.ancestorTrimLength", String.valueOf(ancestorTrimLength))
    }

    private void saveModeSettings() {
        def rc = ResourceController.getResourceController()
        rc.setProperty("mapcrawler.trimMode", String.valueOf(trimMode))
        rc.setProperty("mapcrawler.singleLineMode", String.valueOf(singleLineMode))
        rc.setProperty("mapcrawler.fullMode", String.valueOf(fullMode))
    }

    private void saveTrimLength() {
        ResourceController.getResourceController().setProperty("mapcrawler.trimLength", String.valueOf(trimLength))
    }

    private void loadSettings() {
        def rc = ResourceController.getResourceController()
        String savedDir = rc.getProperty("mapcrawler.lastUsedDir")
        baseDir = (savedDir != null && new File(savedDir).exists()) ? savedDir : "D:\\AJ\\OneDrive\\FP"
        disableTooltips = "true".equals(rc.getProperty("mapcrawler.disableTooltips"))
        hideFileColumn = "true".equals(rc.getProperty("mapcrawler.hideFileColumn"))
        hideStyleColumn = "true".equals(rc.getProperty("mapcrawler.hideStyleColumn"))
        hidePathColumn = "true".equals(rc.getProperty("mapcrawler.hidePathColumn"))
        hideDateColumn = "true".equals(rc.getProperty("mapcrawler.hideDateColumn"))
        hideDateCreatedColumn = "true".equals(rc.getProperty("mapcrawler.hideDateCreatedColumn"))
        hideIconsColumn = "true".equals(rc.getProperty("mapcrawler.hideIconsColumn"))
        hideTagsColumn = "true".equals(rc.getProperty("mapcrawler.hideTagsColumn"))
        hideDetailsColumn = "true".equals(rc.getProperty("mapcrawler.hideDetailsColumn"))
        hideNoteColumn = "true".equals(rc.getProperty("mapcrawler.hideNoteColumn"))
        hideNodeColumn = "true".equals(rc.getProperty("mapcrawler.hideNodeColumn"))
        hideDetailsPreview = rc.getProperty("mapcrawler.hideDetailsPreview", "false") == "true"
        hideNotePreview = rc.getProperty("mapcrawler.hideNotePreview", "false") == "true"
        hidePreviewPanel = "true".equals(rc.getProperty("mapcrawler.hidePreviewPanel"))
        reverseAncestorOrder = "true".equals(rc.getProperty("mapcrawler.reverseAncestorOrder"))
        useVisibleRootOnly = "true".equals(rc.getProperty("mapcrawler.useVisibleRootOnly", "false"))
        String ancestorTrim = rc.getProperty("mapcrawler.ancestorTrimLength")
        ancestorTrimLength = (ancestorTrim != null && ancestorTrim.isInteger()) ? ancestorTrim.toInteger() : 30
        trimMode = "true".equals(rc.getProperty("mapcrawler.trimMode", "true"))
        singleLineMode = "true".equals(rc.getProperty("mapcrawler.singleLineMode", "false"))
        fullMode = "true".equals(rc.getProperty("mapcrawler.fullMode", "false"))
        if (trimMode && (singleLineMode || fullMode)) { trimMode = true; singleLineMode = false; fullMode = false }
        else if (singleLineMode && fullMode) { singleLineMode = true; fullMode = false }
        else if (!trimMode && !singleLineMode && !fullMode) trimMode = true
        String lenStr = rc.getProperty("mapcrawler.trimLength")
        trimLength = (lenStr != null && lenStr.isInteger()) ? lenStr.toInteger() : 80
        // Load showOnlyBreadcrumbs setting
        showOnlyBreadcrumbs = "true".equals(rc.getProperty("mapcrawler.showOnlyBreadcrumbs", "false"))
    }

    private void loadHistory() {
        def rc = ResourceController.getResourceController()
        String searchHist = rc.getProperty("mapcrawler.searchHistory")
        if (searchHist) searchHistory = searchHist.split("\\|") as List
        else searchHistory = []
        String styleHist = rc.getProperty("mapcrawler.styleHistory")
        if (styleHist) styleHistory = styleHist.split("\\|") as List
        else styleHistory = []
        String filterHist = rc.getProperty("mapcrawler.filterHistory")
        if (filterHist) filterHistory = filterHist.split("\\|") as List
        else filterHistory = []
    }

    private void saveHistory() {
        def rc = ResourceController.getResourceController()
        rc.setProperty("mapcrawler.searchHistory", searchHistory.join("|"))
        rc.setProperty("mapcrawler.styleHistory", styleHistory.join("|"))
        rc.setProperty("mapcrawler.filterHistory", filterHistory.join("|"))
    }

    private void addSearchHistory(String term) {
        if (!term || term.isEmpty()) return
        searchHistory.remove(term)
        searchHistory.add(0, term)
        if (searchHistory.size() > 20) searchHistory.remove(searchHistory.size()-1)
        saveHistory()
        if (searchAutoComplete != null) searchAutoComplete.updateList(searchHistory)
    }

    private void addStyleHistory(String term) {
        if (!term || term.isEmpty()) return
        styleHistory.remove(term)
        styleHistory.add(0, term)
        if (styleHistory.size() > 20) styleHistory.remove(styleHistory.size()-1)
        saveHistory()
        if (styleAutoComplete != null) styleAutoComplete.updateList(styleHistory)
    }

    private void addFilterHistory(String term) {
        if (!term || term.isEmpty()) return
        filterHistory.remove(term)
        filterHistory.add(0, term)
        if (filterHistory.size() > 20) filterHistory.remove(filterHistory.size()-1)
        saveHistory()
        if (filterAutoComplete != null) filterAutoComplete.updateList(filterHistory)
    }

    private void applySettings() {
        if (resultsTable == null) return
        TableColumnModel colModel = resultsTable.getColumnModel()
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            TableColumn col = colModel.getColumn(i)
            String header = (String) col.getHeaderValue()
            boolean hide = false
            int minWidth = 0
            int defaultPref = 0
            switch (header) {
                case "File": hide = hideFileColumn; minWidth = 60; defaultPref = 120; break
                case "Style": hide = hideStyleColumn; minWidth = 50; defaultPref = 80; break
                case "Ancestors": hide = hidePathColumn; minWidth = 80; defaultPref = 200; break
                case "Date Modified": hide = hideDateColumn; minWidth = 80; defaultPref = 100; break
                case "Date Created": hide = hideDateCreatedColumn; minWidth = 80; defaultPref = 100; break
                case "Icons": hide = hideIconsColumn; minWidth = 60; defaultPref = 100; break
                case "Tags": hide = hideTagsColumn; minWidth = 50; defaultPref = 80; break
                case "Details": hide = hideDetailsColumn; minWidth = 80; defaultPref = 150; break
                case "Note": hide = hideNoteColumn; minWidth = 80; defaultPref = 150; break
                case "Node": hide = hideNodeColumn; minWidth = 120; defaultPref = 350; break
                default: continue
            }
            if (hide) {
                col.setMinWidth(0)
                col.setMaxWidth(0)
                col.setPreferredWidth(0)
                col.setWidth(0)
            } else {
                col.setMinWidth(minWidth)
                col.setMaxWidth(Integer.MAX_VALUE)
                Integer stored = storedColumnWidths.get(header)
                int pref = (stored != null && stored > minWidth) ? stored : defaultPref
                col.setPreferredWidth(pref)
                col.setWidth(pref)
            }
        }
        rowHeightCache.clear()
        if (previewDetails != null && previewNote != null && textPanel != null) {
            for (Component c : textPanel.getComponents()) {
                if (c == previewDetails) c.setVisible(!hideDetailsPreview)
                else if (c == previewNote) c.setVisible(!hideNotePreview)
            }
            textPanel.revalidate()
        }
        if (innerSplitPane != null && leftPreviewPanel != null) {
            if (hidePreviewPanel) {
                if (leftPreviewPanel.isVisible() && lastPreviewDividerLocation == -1)
                    lastPreviewDividerLocation = innerSplitPane.getDividerLocation()
                leftPreviewPanel.setVisible(false)
                innerSplitPane.setDividerLocation(0)
                innerSplitPane.setDividerSize(0)
            } else {
                leftPreviewPanel.setVisible(true)
                innerSplitPane.setDividerSize(6)
                if (lastPreviewDividerLocation > 0 && lastPreviewDividerLocation < innerSplitPane.getMaximumDividerLocation())
                    innerSplitPane.setDividerLocation(lastPreviewDividerLocation)
                else
                    innerSplitPane.setDividerLocation(0.35)
            }
            innerSplitPane.revalidate()
        }
        resultsTable.repaint()
    }

    private String shortenPath(String path) {
        if (path.length() <= 40) return path
        return "..." + path.substring(path.length() - 37)
    }

    private void clearPreview() {
        tagViewer.removeAll()
        styleLabel.setText("")
        previewCore.setText("")
        previewDetails.setText("")
        previewNote.setText("")
        breadcrumbPanel.removeAll()
        breadcrumbPanel.revalidate()
        breadcrumbPanel.repaint()
        textPanel.revalidate(); textPanel.repaint()
        tagViewer.revalidate(); tagViewer.repaint()
    }

    private String getStyledTextContent(String rawText, Font font, Node node) {
        if (!rawText) return ""
        boolean isHtml = rawText.trim().startsWith("<")
        String contentToProcess = rawText
        if (trimMode && !isHtml) {
            if (contentToProcess.length() > trimLength) {
                contentToProcess = TextUtils.getShortText(contentToProcess, trimLength, "\u2026")
            }
        }
        Color fgColor = getNodeForegroundColor(node)
        String colorStyle = (fgColor != null) ? "color: rgb(${fgColor.red}, ${fgColor.green}, ${fgColor.blue});" : ""
        String family = font.getFamily()
        int size = font.getSize()
        String weight = font.isBold() ? "bold" : "normal"
        String styleFlag = font.isItalic() ? "italic" : "normal"
        String wrapperStyle = "font-family: ${family}; font-size: ${size}pt; font-weight: ${weight}; font-style: ${styleFlag}; ${colorStyle} margin:0; padding:0; direction: rtl; text-align: right;"
        if (isHtml) {
            String html = contentToProcess.replaceAll("(?i)<\\/?(html|head|body)[^>]*>", "")
            html = html.replaceAll("(?i)<style[^>]*>.*?<\\/style>", "")
            html = html.replaceAll(/(?i)font-size\s*:\s*[^;]+;?/, "")
            html = html.replaceAll(/(?i)font-family\s*:\s*[^;]+;?/, "")
            html = html.replaceAll(/(?i)line-height\s*:\s*[^;]+;?/, "")
            html = html.replaceAll(/;\s*;/, ";")
            html = html.replaceAll(/;\s*}/, "}")
            html = html.replaceAll(/style\s*=\s*["']\s*["']/, "")
            html = html.replaceAll("(?i)<\\/?font[^>]*>", "")
            html = html.replaceAll("(?i)\\s+size\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
            html = html.replaceAll("(?i)\\s+face\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
            if (html.trim().isEmpty()) html = node.getPlainText()
            return "<div style=\"${wrapperStyle}\">${html}</div>"
        } else {
            String escaped = escapeHtml(contentToProcess).replace("\n", "<br>")
            return "<div style=\"${wrapperStyle}\">${escaped}</div>"
        }
    }

    private String getStyledCellContent(String rawText, Node node, Font font, boolean applyHighlight, int columnWidth) {
        if (!rawText) return ""
        boolean isHtml = rawText.trim().startsWith("<")
        String content = rawText
        
        // Preview mode (columnWidth == 0): never trim
        if (!isHtml && trimMode && !singleLineMode && !fullMode && columnWidth > 0) {
            if (content.length() > trimLength) {
                content = TextUtils.getShortText(content, trimLength, "\u2026")
            }
        }
        
        if (isHtml) {
            content = content.replaceAll("(?i)<\\/?(html|head|body)[^>]*>", "")
            content = content.replaceAll("(?i)<style[^>]*>.*?<\\/style>", "")
            content = content.replaceAll(/(?i)font-size\s*:\s*[^;]+;?/, "")
            content = content.replaceAll(/(?i)font-family\s*:\s*[^;]+;?/, "")
            content = content.replaceAll(/(?i)line-height\s*:\s*[^;]+;?/, "")
            content = content.replaceAll(/;\s*;/, ";")
            content = content.replaceAll(/;\s*}/, "}")
            content = content.replaceAll(/style\s*=\s*["']\s*["']/, "")
            content = content.replaceAll("(?i)<\\/?font[^>]*>", "")
            content = content.replaceAll("(?i)\\s+size\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
            content = content.replaceAll("(?i)\\s+face\\s*=\\s*[\"']?[^\"'\\s>]+[\"']?", "")
            if (content.trim().isEmpty()) content = node.getPlainText()
            if (singleLineMode) {
                content = content.replaceAll("(?i)</?(div|p|h[1-6]|table|tr|td|ul|ol|li|br)[^>]*>", " ")
                content = content.replaceAll("\\s+", " ")
            }
        } else {
            content = escapeHtml(content).replace("\n", "<br>")
            // Preview mode (columnWidth == 0): never trim
            if (singleLineMode && trimMode && columnWidth > 0) {
                if (content.length() > trimLength) {
                    content = TextUtils.getShortText(content, trimLength, "\u2026")
                }
            }
        }
        
        // Multi-word highlighting
        if (applyHighlight) {
            String highlightWord = (currentFilterText != null && !currentFilterText.isEmpty()) ? currentFilterText : lastSearchKeyword
            if (highlightWord != null && !highlightWord.isEmpty()) {
                List<String> wordsToHighlight = []
                if (highlightWord.contains("|")) {
                    wordsToHighlight = highlightWord.split("\\|").collect { it.trim() }.findAll { !it.isEmpty() }
                } else {
                    wordsToHighlight = [highlightWord]
                }
                for (String word : wordsToHighlight) {
                    if (word.isEmpty()) continue
                    String patternStr = Pattern.quote(word)
                    try {
                        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE
                        Pattern p = Pattern.compile("($patternStr)", flags)
                        Matcher m = p.matcher(content)
                        StringBuffer sb = new StringBuffer()
                        while (m.find()) {
                            m.appendReplacement(sb, "<span style='background-color: yellow;'>\$1</span>")
                        }
                        m.appendTail(sb)
                        content = sb.toString()
                    } catch (Exception e) {}
                }
            }
        }
        
        Color fgColor = getNodeForegroundColor(node)
        String colorStyle = (fgColor != null) ? "color: rgb(${fgColor.red}, ${fgColor.green}, ${fgColor.blue});" : ""
        String family = font.getFamily()
        int size = font.getSize()
        String weight = font.isBold() ? "bold" : "normal"
        String styleFlag = font.isItalic() ? "italic" : "normal"
        String wrapperStyle = "font-family: ${family}; font-size: ${size}pt; font-weight: ${weight}; font-style: ${styleFlag}; ${colorStyle} margin:0; padding:0; direction: rtl; text-align: right;"
        
        // Preview mode (columnWidth == 0): use normal wrap
        if (singleLineMode && columnWidth > 0) {
            wrapperStyle += " white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: ${columnWidth - 10}px; unicode-bidi: plaintext;"
        } else {
            wrapperStyle += " white-space: normal;"
        }
        
        return "<div style=\"${wrapperStyle}\">${content}</div>"
    }
    
    // ========== Show Node Details ==========
    private void actuallyShowNodeDetails(int row) {
        int modelRow = resultsTable.convertRowIndexToModel(row)
        Object value = tableModel.getValueAt(modelRow, 9)
        Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
        if (node == null) { clearPreview(); return }
        String nodeId = node.getId()
        if (nodeId.equals(lastSelectedNodeId)) {
            if (leftPreviewPanel != null && !leftPreviewPanel.isVisible() && !hidePreviewPanel) {
                leftPreviewPanel.setVisible(true)
                if (innerSplitPane != null) innerSplitPane.setDividerLocation(0.35)
            }
            return
        }
        lastSelectedNodeId = nodeId
        if (leftPreviewPanel != null && !leftPreviewPanel.isVisible() && !hidePreviewPanel) {
            leftPreviewPanel.setVisible(true)
            if (innerSplitPane != null) innerSplitPane.setDividerLocation(0.35)
        }
        NodeModel nodeModel = getNodeModel(node)
        String styleName = node.getStyle()?.getName()
        if (styleName) {
            Font previewFont = (fontPreviewCore != null) ? fontPreviewCore : new Font("Segoe UI", Font.PLAIN, 14)
            styleLabel.setText(styleName)
            styleLabel.setFont(previewFont.deriveFont(Font.PLAIN, previewFont.getSize()))
            styleLabel.setForeground(Color.BLACK)
        } else {
            styleLabel.setText("")
        }
        tagViewer.removeAll()
        def icons = iconController().getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE)
        def tags = iconController().getTagIcons(nodeModel)
        icons.each { tagViewer.add(new JLabel(it.getIcon())) }
        tags.each { tagViewer.add(new JLabel(it)) }
        tagViewer.revalidate(); tagViewer.repaint()
        Font useFontCore = (fontPreviewCore != null) ? fontPreviewCore : previewCore.getFont()
        String coreRaw = node.getHtmlText() ?: node.getPlainText()
        String styledCore = getStyledCellContent(coreRaw, node, useFontCore, true, 0)
        previewCore.setText("<html>${styledCore}</html>")
        Color nodeBg = getNodeBackgroundColor(node)
        if (nodeBg != null) {
            previewCore.setBackground(nodeBg)
        } else {
            previewCore.setBackground(UIManager.getColor("Panel.background"))
        }
        previewCore.setOpaque(true)
        Font detailsFont = (fontPreviewDetails != null) ? fontPreviewDetails : previewDetails.getFont()
        String detailsRaw = node.getDetails()?.getHtml() ?: node.getDetails()?.getPlain() ?: ""
        String styledDetails = getStyledCellContent(detailsRaw, node, detailsFont, true, 0)
        previewDetails.setText("<html>${styledDetails}</html>")
        previewDetails.setBackground(nodeBg != null ? nodeBg : UIManager.getColor("Panel.background"))
        previewDetails.setOpaque(true)
        Font noteFont = (fontPreviewNote != null) ? fontPreviewNote : previewNote.getFont()
        String noteRaw = node.getNote()?.getHtml() ?: node.getNote()?.getPlain() ?: ""
        String styledNote = getStyledCellContent(noteRaw, node, noteFont, true, 0)
        previewNote.setText("<html>${styledNote}</html>")
        previewNote.setBackground(nodeBg != null ? nodeBg : UIManager.getColor("Panel.background"))
        previewNote.setOpaque(true)
        breadcrumbPanel.removeAll()
        try {
            def fullPath = node.getPathToRoot()
            if (fullPath && !fullPath[0].isRoot()) {
                fullPath = fullPath.reverse()
            }
            def displayPath = fullPath.size() > 1 ? fullPath[0..-2] : []
            
            // If empty path (root node), show default
            if (displayPath.isEmpty()) {
                JPanel tempPanel = new JPanel()
                tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
                tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                
                JLabel rootLabel = new JLabel("📁 Root")
                if (fontBreadcrumb != null) {
                    rootLabel.setFont(fontBreadcrumb.deriveFont(Font.BOLD, fontBreadcrumb.getSize() + 2))
                } else {
                    rootLabel.setFont(new Font("Segoe UI", Font.BOLD, 14))
                }
                rootLabel.setForeground(new Color(0, 100, 200))
                rootLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5))
                tempPanel.add(rootLabel)
                
                breadcrumbPanel.add(tempPanel)
                breadcrumbPanel.revalidate()
                breadcrumbPanel.repaint()
                breadcrumbPanel.setVisible(true)
                return
            }
            
            // Apply visible root filter
            if (useVisibleRootOnly) {
                def viewRoot = getActiveViewRoot()
                if (viewRoot != null && node.mindMap == viewRoot.mindMap) {
                    int idx = fullPath.indexOf(viewRoot)
                    if (idx != -1) {
                        if (idx <= displayPath.size()) {
                            displayPath = displayPath[idx..-1]
                        } else {
                            displayPath = []
                        }
                    }
                }
            }
            
            // If still empty, show root
            if (displayPath.isEmpty()) {
                JPanel tempPanel = new JPanel()
                tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
                tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                
                JLabel rootLabel = new JLabel("📁 Root")
                if (fontBreadcrumb != null) {
                    rootLabel.setFont(fontBreadcrumb.deriveFont(Font.BOLD, fontBreadcrumb.getSize() + 2))
                } else {
                    rootLabel.setFont(new Font("Segoe UI", Font.BOLD, 14))
                }
                rootLabel.setForeground(new Color(0, 100, 200))
                rootLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5))
                tempPanel.add(rootLabel)
                
                breadcrumbPanel.add(tempPanel)
                breadcrumbPanel.revalidate()
                breadcrumbPanel.repaint()
                breadcrumbPanel.setVisible(true)
                return
            }
            
            // Normal breadcrumb display
            int maxNodes = 5
            int start = Math.max(0, displayPath.size() - maxNodes)
            JPanel tempPanel = new JPanel()
            tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
            tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            if (start > 0) {
                JLabel ellipsisLabel = new JLabel(" ... ")
                ellipsisLabel.setFont(ellipsisLabel.getFont().deriveFont(Font.BOLD))
                tempPanel.add(ellipsisLabel)
            }
            ButtonGroup bg = new ButtonGroup()
            for (int i = start; i < displayPath.size(); i++) {
                Node n = displayPath.get(i)
                String nodeText = n.getPlainText()
                String shortText = nodeText
                if (ancestorTrimLength > 0 && nodeText.length() > ancestorTrimLength) {
                    shortText = TextUtils.getShortText(nodeText, ancestorTrimLength, "\u2026")
                }
                JRadioButton btn = new JRadioButton(shortText)
                btn.setToolTipText(nodeText)
                if (fontBreadcrumb != null) btn.setFont(fontBreadcrumb)
                else btn.setFont(btn.getFont().deriveFont(Font.PLAIN))
                Color bgColor = getNodeBackgroundColor(n)
                if (bgColor != null) {
                    btn.setBackground(bgColor)
                    btn.setForeground(getForegroundForBackground(bgColor))
                    btn.setOpaque(true)
                    btn.setContentAreaFilled(true)
                } else {
                    btn.setOpaque(false)
                    btn.setForeground(UIManager.getColor("Label.foreground"))
                }
                Color borderColor = getBorderColorForNode(n)
                Border leftBorder = BorderFactory.createMatteBorder(0, 5, 0, 0, borderColor)
                Border padding = BorderFactory.createEmptyBorder(2, 6, 2, 6)
                btn.setBorder(BorderFactory.createCompoundBorder(leftBorder, padding))
                btn.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                btn.setHorizontalAlignment(SwingConstants.RIGHT)
                btn.setHorizontalTextPosition(SwingConstants.LEFT)
                btn.putClientProperty("node", n)
                final Node target = n
                final JRadioButton currentBtn = btn
                btn.addActionListener({ e ->
                    for (Component comp : tempPanel.getComponents()) {
                        if (comp instanceof JRadioButton) {
                            ((JRadioButton)comp).setForeground(UIManager.getColor("Label.foreground"))
                            ((JRadioButton)comp).setFont(((JRadioButton)comp).getFont().deriveFont(Font.PLAIN))
                            Node oldNode = (Node) ((JRadioButton)comp).getClientProperty("node")
                            if (oldNode != null) {
                                Color oldBg = getNodeBackgroundColor(oldNode)
                                if (oldBg != null) {
                                    ((JRadioButton)comp).setBackground(oldBg)
                                    ((JRadioButton)comp).setForeground(getForegroundForBackground(oldBg))
                                } else {
                                    ((JRadioButton)comp).setOpaque(false)
                                }
                            }
                        }
                    }
                    currentBtn.setForeground(Color.BLUE)
                    currentBtn.setFont(currentBtn.getFont().deriveFont(Font.BOLD))
                    try {
                        ScriptUtils.c().select(target)
                        def mapFile = target.getMindMap().getFile()
                        if (mapFile) {
                            def uri = mapFile.toURI().toString() + "#" + target.getId()
                            def link = new org.freeplane.core.util.Hyperlink(new URI(uri))
                            org.freeplane.features.url.UrlManager.getController().loadHyperlink(link)
                            SwingUtilities.invokeLater(new Runnable() {
                                void run() {
                                    if (resultsTable != null && resultsTable.isShowing()) {
                                        resultsTable.requestFocusInWindow()
                                    }
                                }
                            })
                        }
                    } catch (Exception ex) { ex.printStackTrace() }
                })
                tempPanel.add(btn)
                bg.add(btn)
            }
            for (Component comp : tempPanel.getComponents()) {
                if (comp instanceof JRadioButton) {
                    Node storedNode = (Node) ((JRadioButton)comp).getClientProperty("node")
                    if (storedNode == node) {
                        ((JRadioButton)comp).setSelected(true)
                        ((JRadioButton)comp).setForeground(Color.BLUE)
                        ((JRadioButton)comp).setFont(((JRadioButton)comp).getFont().deriveFont(Font.BOLD))
                        break
                    }
                }
            }
            breadcrumbPanel.add(tempPanel, BorderLayout.CENTER)
        } catch (Exception e) {
            breadcrumbPanel.add(new JLabel(" "), BorderLayout.CENTER)
            System.err.println("Breadcrumb error: ${e.message}")
        }
        breadcrumbPanel.revalidate()
        breadcrumbPanel.repaint()
        breadcrumbPanel.setVisible(true) // Always visible
    }

    private void showNodeDetails(int row) {
        if (previewDebouncer != null && previewDebouncer.isRunning()) {
            previewDebouncer.stop()
        }
        pendingRow = row
        previewDebouncer = new Timer(20, { e ->
            if (pendingRow != -1 && pendingRow < resultsTable.getRowCount()) {
                actuallyShowNodeDetails(pendingRow)
                pendingRow = -1
            }
        })
        previewDebouncer.setRepeats(false)
        previewDebouncer.start()
    }

    private String escapeHtml(String s) {
        if (s == null) return ""
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    // ========== Search Methods ==========
    private void doSearch() {
        String keyword = searchField.text.trim()
        boolean searchCore = coreCheck.selected, searchDetails = detailsCheck.selected, searchNote = noteCheck.selected
        if (!searchCore && !searchDetails && !searchNote && !keyword.isEmpty()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Select at least one search option")
            return
        }
        def styleNames = []
        if (styleScopeRadio.selected) {
            String raw = styleField.text.trim()
            if (raw.isEmpty()) { JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Enter style name"); return }
            styleNames = raw.split(/[;,\\.]+/).collect{ it.trim() }.findAll{ !it.isEmpty() }
            if (styleNames.isEmpty()) { JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Enter valid style name(s)"); return }
            raw.split(/[;,\\.]+/).each { addStyleHistory(it.trim()) }
        }
        tableModel.setRowCount(0)
        clearPreview()
        pathCache.clear()
        rowHeightCache.clear()
        filterField.setText("")
        currentFilterText = ""
        rowSorter.setRowFilter(null)
        lastSearchKeyword = keyword
        boolean filterVisible = !folderRadio.isSelected()
        if (!keyword.isEmpty()) addSearchHistory(keyword)
        if (folderRadio.isSelected()) {
            File dir = new File(baseDir)
            if (!dir.exists() || !dir.isDirectory()) { JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Invalid folder: $baseDir"); return }
            def files = dir.listFiles().findAll { it.name.endsWith(".mm") }
            if (files.isEmpty()) { JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No .mm files found"); return }
            def maps = []
            files.each { file -> try { maps << ScriptUtils.c().mapLoader(file).mindMap } catch (Exception e) { println "Error loading $file.name: $e.message" } }
            if (maps.isEmpty()) { JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No maps to search"); return }
            performSearchOnMaps(maps, keyword, styleNames, searchCore, searchDetails, searchNote, false)
        } else if (openMapsRadio.isSelected()) {
            def maps = ScriptUtils.c().openMindMaps
            if (maps.isEmpty()) {
                JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No open maps")
                return
            }
            performSearchOnMaps(maps, keyword, styleNames, searchCore, searchDetails, searchNote, false)
        } else if (selectedDescRadio.isSelected()) {
            Node selected = ScriptUtils.c().getSelected()
            if (selected == null) { JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No node selected. Please select a node first."); return }
            java.util.List<Node> nodes
            if (filterVisible) {
                nodes = collectVisibleNodes(selected)
            } else {
                nodes = [selected] + selected.findAll()
            }
            performSearchOnNodes(nodes, keyword, styleNames, searchCore, searchDetails, searchNote, filterVisible)
        } else if (selectedSibRadio.isSelected()) {
            Node selected = ScriptUtils.c().getSelected()
            if (selected == null) { JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No node selected. Please select a node first."); return }
            Node parent = selected.getParent()
            java.util.List<Node> nodes
            if (filterVisible) {
                nodes = (parent == null) ? [selected].findAll { it.isVisible() } : parent.getChildren().findAll { it.isVisible() }
            } else {
                nodes = (parent == null) ? [selected] : parent.getChildren()
            }
            performSearchOnNodes(nodes, keyword, styleNames, searchCore, searchDetails, searchNote, filterVisible)
        } else if (rootRadio.isSelected()) {
            def visibleRoot = ScriptUtils.c().viewRoot
            if (visibleRoot == null) {
                JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No visible root found.\nPlease double-click a node to set it as the view root.", "Root Search", JOptionPane.WARNING_MESSAGE)
                return
            }
            java.util.List<Node> allNodes
            if (filterVisible) {
                allNodes = collectVisibleNodes(visibleRoot)
            } else {
                allNodes = [visibleRoot] + visibleRoot.findAll()
            }
            performSearchOnNodes(allNodes, keyword, styleNames, searchCore, searchDetails, searchNote, filterVisible)
        }
        updateResultCount()
    }

    private void performSearchOnMaps(def maps, String keyword, def styleNames, boolean searchCore, boolean searchDetails, boolean searchNote, boolean filterVisible) {
        int resultCount = 0
        for (def map : maps) {
            try {
                map.root.findAll().each { node ->
                    if (filterVisible && !node.isVisible()) return
                    if (isMatch(node, keyword, styleNames, searchCore, searchDetails, searchNote)) {
                        resultCount++
                        String fileName = map.file?.name ?: "Unnamed"
                        String styleName = node.style?.name ?: "(no style)"
                        String pathStr = getAncestorsPathCached(node)
                        String modifiedDate = node.getLastModifiedAt() ? dateFormat.format(node.getLastModifiedAt()) : ""
                        String createdDate = node.getCreatedAt() ? dateFormat.format(node.getCreatedAt()) : ""
                        String detailsText = node.details?.plain ?: ""
                        String noteText = node.note?.plain ?: ""
                        String tagsString = getSortedTagsString(node)
                        tableModel.addRow([ fileName, styleName, pathStr, modifiedDate, createdDate, "", tagsString, detailsText, noteText, [node] as Object[] ] as Object[])
                        String pathKey = map.file?.absolutePath ?: "unsaved"
                        if (!PATH_COLORS.containsKey(pathKey)) PATH_COLORS.put(pathKey, determineStringColor(pathKey))
                    }
                }
            } catch (Exception e) { println "Error in map ${map.file?.name}: $e.message" }
        }
        if (resultCount == 0) JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No results found")
        else resultsTable.setRowSelectionInterval(0, 0)
        updateAllRowHeights()
    }

    private void performSearchOnNodes(java.util.List<Node> nodes, String keyword, def styleNames, boolean searchCore, boolean searchDetails, boolean searchNote, boolean filterVisible) {
        int resultCount = 0
        for (Node node : nodes) {
            if (filterVisible && !node.isVisible()) continue
            if (isMatch(node, keyword, styleNames, searchCore, searchDetails, searchNote)) {
                resultCount++
                String fileName = node.mindMap.file?.name ?: "Unnamed"
                String styleName = node.style?.name ?: "(no style)"
                String pathStr = getAncestorsPathCached(node)
                String modifiedDate = node.getLastModifiedAt() ? dateFormat.format(node.getLastModifiedAt()) : ""
                String createdDate = node.getCreatedAt() ? dateFormat.format(node.getCreatedAt()) : ""
                String detailsText = node.details?.plain ?: ""
                String noteText = node.note?.plain ?: ""
                String tagsString = getSortedTagsString(node)
                tableModel.addRow([ fileName, styleName, pathStr, modifiedDate, createdDate, "", tagsString, detailsText, noteText, [node] as Object[] ] as Object[])
                String pathKey = node.mindMap.file?.absolutePath ?: "unsaved"
                if (!PATH_COLORS.containsKey(pathKey)) PATH_COLORS.put(pathKey, determineStringColor(pathKey))
            }
        }
        if (resultCount == 0) JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "No results found in the selected scope")
        else resultsTable.setRowSelectionInterval(0, 0)
        updateAllRowHeights()
    }

    private String getSortedTagsString(Node node) {
        if (node == null) return ""
        NodeModel nodeModel = getNodeModel(node)
        def tags = iconController().getTagIcons(nodeModel)
        List<String> tagNames = new ArrayList<>()
        tags.each { tag ->
            String name = ""
            if (tag instanceof NamedIcon) {
                name = tag.getName() ?: tag.toString()
            } else if (tag instanceof JLabel) {
                name = tag.getToolTipText() ?: tag.getText() ?: ""
            } else {
                name = tag.toString()
            }
            if (name) tagNames.add(name)
        }
        tagNames.sort { a, b -> a.compareToIgnoreCase(b) }
        return tagNames.join(", ")
    }

    private boolean isMatch(Node node, String keyword, def styleNames, boolean searchCore, boolean searchDetails, boolean searchNote) {
        if (styleScopeRadio.selected && styleNames) {
            String nodeStyle = node.style?.name
            if (!nodeStyle || !styleNames.contains(nodeStyle)) return false
        }
        if (keyword == null || keyword.isEmpty()) return true
        String kw = matchCase ? keyword : keyword.toLowerCase()
        if (searchCore) {
            String text = node.plainText ?: ""
            if (!matchCase) text = text.toLowerCase()
            if (wholeWord ? text ==~ /.*\b$kw\b.*/ : text.contains(kw)) return true
        }
        if (searchDetails && node.details?.plain) {
            String text = node.details.plain
            if (!matchCase) text = text.toLowerCase()
            if (wholeWord ? text ==~ /.*\b$kw\b.*/ : text.contains(kw)) return true
        }
        if (searchNote && node.note?.plain) {
            String text = node.note.plain
            if (!matchCase) text = text.toLowerCase()
            if (wholeWord ? text ==~ /.*\b$kw\b.*/ : text.contains(kw)) return true
        }
        return false
    }

    private void goToNode(int row) {
        int modelRow = resultsTable.convertRowIndexToModel(row)
        Object value = tableModel.getValueAt(modelRow, 9)
        Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
        if (node != null) {
            try {
                // Save current row before navigating
                int currentRow = row
                
                ScriptUtils.c().select(node)
                def mapFile = node.mindMap.file
                if (mapFile) {
                    def uri = mapFile.toURI().toString() + "#" + node.id
                    def link = new org.freeplane.core.util.Hyperlink(new URI(uri))
                    org.freeplane.features.url.UrlManager.getController().loadHyperlink(link)
                    SwingUtilities.invokeLater(new Runnable() {
                        void run() {
                            if (resultsTable != null && resultsTable.isShowing()) {
                                resultsTable.requestFocusInWindow()
                                // Re-select same row
                                if (currentRow < resultsTable.getRowCount()) {
                                    resultsTable.setRowSelectionInterval(currentRow, currentRow)
                                }
                            }
                        }
                    })
                }
            } catch (Exception e) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Error navigating to node: ${e.message}")
            }
        }
    }

    private void focusSearchField() {
        SwingUtilities.invokeLater({ if (searchField != null) { searchField.requestFocusInWindow(); searchField.selectAll() } })
    }

    // ========== Scrollable Panel ==========
    class ScrollableTextPanel extends JPanel implements Scrollable {
        ScrollableTextPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))
            setBorder(BorderFactory.createEmptyBorder())
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        }
        boolean getScrollableTracksViewportWidth() { return true }
        boolean getScrollableTracksViewportHeight() { return false }
        Dimension getPreferredScrollableViewportSize() { return getPreferredSize() }
        int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 10 }
        int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width
        }
    }

    // ========== AutoCompleteDecorator ==========
    class AutoCompleteDecorator {
        private JTextField textField
        private JWindow popup
        private JList<String> list
        private DefaultListModel<String> listModel
        private java.util.List<String> data
        private String type
        private SimpleMapCrawler crawler
        private boolean preferAbove
        private Timer popupTimer
        AutoCompleteDecorator(JTextField field, java.util.List<String> history, String type, SimpleMapCrawler crawler, boolean preferAbove = false) {
            this.textField = field
            this.data = history
            this.type = type
            this.crawler = crawler
            this.preferAbove = preferAbove
            listModel = new DefaultListModel<>()
            list = new JList<>(listModel)
            list.setVisibleRowCount(6)
            list.setFocusable(false)
            int popupWidth = Math.max(400, textField.getWidth() * 3)
            JScrollPane scroll = new JScrollPane(list)
            scroll.setPreferredSize(new Dimension(popupWidth, 150))
            scroll.setBorder(BorderFactory.createLineBorder(Color.GRAY))
            popup = new JWindow()
            popup.setBackground(new Color(0,0,0,0))
            popup.add(scroll)
            popup.setFocusableWindowState(false)
            popupTimer = new Timer(80, { e -> actuallyShowPopup() })
            popupTimer.setRepeats(false)
            list.addMouseListener(new MouseAdapter() {
                void mouseClicked(MouseEvent e) {
                    selectCurrent()
                }
            })
            textField.addMouseListener(new MouseAdapter() {
                void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) showAllHistory()
                }
            })
            textField.addFocusListener(new FocusAdapter() {
                void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        if (textField.getText().isEmpty()) showAllHistory()
                    })
                }
                void focusLost(FocusEvent e) { 
                    popupTimer.stop()
                    popup.setVisible(false) 
                }
            })
            textField.addKeyListener(new KeyAdapter() {
                void keyPressed(KeyEvent e) {
                    int size = listModel.size()
                    if (size == 0) return
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        int next = list.getSelectedIndex() + 1
                        if (next >= size) next = 0
                        list.setSelectedIndex(next)
                        list.ensureIndexIsVisible(next)
                        e.consume()
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_UP) {
                        int prev = list.getSelectedIndex() - 1
                        if (prev < 0) prev = size - 1
                        list.setSelectedIndex(prev)
                        list.ensureIndexIsVisible(prev)
                        e.consume()
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        if (popup.isVisible() && list.getSelectedValue() != null) {
                            selectCurrent()
                            e.consume()
                        }
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        popup.setVisible(false)
                        e.consume()
                    }
                }
            })
            textField.getDocument().addDocumentListener(new DocumentListener() {
                void insertUpdate(DocumentEvent e) { scheduleSuggestions() }
                void removeUpdate(DocumentEvent e) { scheduleSuggestions() }
                void changedUpdate(DocumentEvent e) { scheduleSuggestions() }
            })
        }
        private void scheduleSuggestions() {
            popupTimer.restart()
        }
        private void actuallyShowPopup() {
            showSuggestionsInternal()
        }
        private void selectCurrent() {
            String selected = list.getSelectedValue()
            if (selected != null) {
                textField.setText(selected)
                popup.setVisible(false)
                if (type == "search") {
                    crawler.doSearch()
                } else if (type == "filter") {
                    crawler.applyFilter()
                }
            }
        }
        void updateList(java.util.List<String> newData) {
            this.data = newData
        }
        private void showAllHistory() {
            if (data == null || data.isEmpty()) return
            listModel.clear()
            data.each { listModel.addElement(it) }
            if (listModel.size() > 0) {
                list.clearSelection()
                popupTimer.stop()
                showPopupNow()
            } else {
                popup.setVisible(false)
            }
        }
        private void showSuggestionsInternal() {
            String text = textField.getText()
            if (text.length() < 1) {
                popup.setVisible(false)
                return
            }
            listModel.clear()
            java.util.List<String> matches = data.findAll { it.toLowerCase().startsWith(text.toLowerCase()) }
            if (matches.isEmpty()) {
                popup.setVisible(false)
                return
            }
            matches.each { listModel.addElement(it) }
            list.clearSelection()
            showPopupNow()
        }
        private void showPopupNow() {
            if (listModel.isEmpty()) {
                popup.setVisible(false)
                return
            }
            try {
                popup.setVisible(false)
                Point fieldLoc = textField.getLocationOnScreen()
                int x = fieldLoc.x
                int yBelow = fieldLoc.y + textField.getHeight()
                int yAbove = fieldLoc.y - popup.getPreferredSize().height
                popup.pack()
                Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds()
                int y = yBelow
                if (preferAbove) {
                    if (yAbove >= screen.y) y = yAbove
                    else y = yBelow
                } else {
                    if (yBelow + popup.getHeight() <= screen.y + screen.height) y = yBelow
                    else if (yAbove >= screen.y) y = yAbove
                    else y = yBelow
                }
                if (y < screen.y) y = yBelow
                popup.setLocation(x, y)
                popup.setVisible(true)
            } catch (Exception ex) {
                ex.printStackTrace()
            }
        }
    }

    // ========== Static Helper Methods ==========
    private static MIconController iconController() { return (MIconController) getModeController().getExtension(IconController.class) }
    private static MModeController getModeController() { return MModeController.getMModeController() }
    private static NodeModel getNodeModel(Node node) { return ((MapProxy) node.getMindMap()).getDelegate().getNodeForID(node.getId()) }
    private static Color determineStringColor(String str) {
        CRC32 crc = new CRC32()
        crc.update(str.getBytes(StandardCharsets.UTF_8))
        return HSLColorConverter.generateColorFromLong(crc.getValue())
    }

    private void editSelectedNode() {
        int selectedRow = resultsTable.getSelectedRow()
        if (selectedRow == -1) return
        int modelRow = resultsTable.convertRowIndexToModel(selectedRow)
        Object value = tableModel.getValueAt(modelRow, 9)
        Node node = (value instanceof Object[]) ? ((Object[])value)[0] as Node : (value instanceof Node ? value as Node : null)
        if (node != null) {
            try {
                ScriptUtils.c().select(node)
                def mapFile = node.mindMap.file
                if (mapFile) {
                    def uri = mapFile.toURI().toString() + "#" + node.id
                    def link = new org.freeplane.core.util.Hyperlink(new URI(uri))
                    org.freeplane.features.url.UrlManager.getController().loadHyperlink(link)
                }
            } catch (Exception e) { e.printStackTrace() }
        }
    }

    // ========== Floating breadcrumb and selection polling ==========
    private void startMapSelectionPolling() {
        if (mapSelectionPollingTimer != null && mapSelectionPollingTimer.isRunning()) return
        mapSelectionPollingTimer = new Timer(200, new ActionListener() {
            void actionPerformed(ActionEvent e) {
                try {
                    Node currentMapNode = ScriptUtils.c().getSelected()
                    if (currentMapNode == null) return
                    if (lastPolledMapNode == null || !currentMapNode.getId().equals(lastPolledMapNode.getId())) {
                        lastPolledMapNode = currentMapNode
                        SwingUtilities.invokeLater(new Runnable() {
                            void run() {
                                populatePreview(currentMapNode)
                                if (showOnlyBreadcrumbs && breadcrumbOnlyPanel != null && breadcrumbOnlyPanel.isVisible()) {
                                    refreshBreadcrumbModel()
                                }
                            }
                        })
                    }
                } catch (Exception ex) { }
            }
        })
        mapSelectionPollingTimer.start()
    }

    private void stopMapSelectionPolling() {
        if (mapSelectionPollingTimer != null) {
            mapSelectionPollingTimer.stop()
            mapSelectionPollingTimer = null
        }
        lastPolledMapNode = null
    }

    private void toggleBreadcrumbOnlyMode() {
        showOnlyBreadcrumbs = !showOnlyBreadcrumbs
        saveSettingsToPrefs()
        applyBreadcrumbOnlyMode()
        if (showOnlyBreadcrumbs && breadcrumbOnlyPanel != null && breadcrumbOnlyPanel.isVisible()) {
            refreshBreadcrumbModel()
            updateBreadcrumbPosition()
        }
    }

    private void applyBreadcrumbOnlyMode() {
        if (showOnlyBreadcrumbs) {
            showBreadcrumbOnly()
        } else {
            removeBreadcrumbOnly()
        }
    }

    // ========== Floating breadcrumb methods ==========
    private void showBreadcrumbOnly() {
        if (breadcrumbOnlyPanel != null) {
            breadcrumbOnlyPanel.setVisible(true)
            refreshBreadcrumbModel()
            updateBreadcrumbPosition()
            return
        }
        def mapView = Controller.getCurrentController().getMapViewManager().getMapView()
        if (mapView == null) return
        def frame = SwingUtilities.getWindowAncestor(mapView)
        if (!(frame instanceof JFrame)) return
        def layeredPane = frame.getLayeredPane()
        if (layeredPane == null) return

        breadcrumbOnlyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0))
        breadcrumbOnlyPanel.setOpaque(false)
        breadcrumbOnlyPanel.setBackground(new Color(0, 0, 0, 0))
        breadcrumbOnlyPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)

        breadcrumbJList = new JList<>(ancestorsModel)
        breadcrumbJList.setLayoutOrientation(JList.HORIZONTAL_WRAP)
        breadcrumbJList.setVisibleRowCount(1)
        breadcrumbJList.setFixedCellWidth(200)
        breadcrumbJList.setFixedCellHeight(30)
        breadcrumbJList.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)

        breadcrumbJList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus) {
                Node node = (Node) value
                String displayText = node.getPlainText()
                if (ancestorTrimLength > 0 && displayText.length() > ancestorTrimLength) {
                    displayText = TextUtils.getShortText(displayText, ancestorTrimLength, "\u2026")
                }
                JLabel label = (JLabel) super.getListCellRendererComponent(list, displayText, index, isSelected, hasFocus)
                label.setToolTipText(node.getPlainText())
                Color bg = getNodeBackgroundColor(node)
                if (bg != null) {
                    label.setBackground(bg)
                    label.setForeground(getForegroundForBackground(bg))
                    label.setOpaque(true)
                } else {
                    label.setOpaque(false)
                }
                Color borderColor = getBorderColorForNode(node)
                label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 5, 0, 0, borderColor),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)
                ))
                return label
            }
        })

        breadcrumbJList.addMouseListener(new MouseAdapter() {
            void mouseClicked(MouseEvent e) {
                int idx = breadcrumbJList.locationToIndex(e.getPoint())
                if (idx != -1) {
                    Node target = ancestorsModel.getElementAt(idx)
                    navigateToNode(target)
                }
            }
        })

        JScrollPane scroll = new JScrollPane(breadcrumbJList)
        scroll.setBorder(BorderFactory.createEmptyBorder())
        scroll.setOpaque(false)
        scroll.getViewport().setOpaque(false)
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER)

        breadcrumbOnlyPanel.add(scroll)
        layeredPane.add(breadcrumbOnlyPanel, JLayeredPane.DRAG_LAYER)
        layeredPane.setComponentZOrder(breadcrumbOnlyPanel, 0)

        def viewport = mapView.getParent()
        if (viewport instanceof JViewport) {
            viewport.addChangeListener({ e -> updateBreadcrumbPosition() } as ChangeListener)
        }
        frame.addComponentListener(new ComponentAdapter() {
            void componentResized(ComponentEvent e) { updateBreadcrumbPosition() }
        })

        updateBreadcrumbPosition()
        refreshBreadcrumbModel()
    }

    private void removeBreadcrumbOnly() {
        if (breadcrumbOnlyPanel != null) {
            breadcrumbOnlyPanel.setVisible(false)
        }
    }

    private void updateBreadcrumbPosition() {
        if (breadcrumbOnlyPanel == null || !breadcrumbOnlyPanel.isVisible()) return
        def mapView = Controller.getCurrentController().getMapViewManager().getMapView()
        if (mapView == null) return
        def viewport = mapView.getParent()
        if (!(viewport instanceof JViewport)) return

        Point viewportScreenLoc = viewport.getLocationOnScreen()
        def parent = breadcrumbOnlyPanel.getParent()
        if (parent == null) return
        Point parentScreenLoc = parent.getLocationOnScreen()

        int x = viewportScreenLoc.x - parentScreenLoc.x
        int y = viewportScreenLoc.y - parentScreenLoc.y
        int width = viewport.getWidth()
        int height = 40

        breadcrumbOnlyPanel.setBounds(x, y, width, height)
        breadcrumbOnlyPanel.revalidate()
        breadcrumbOnlyPanel.repaint()
    }

    private void refreshBreadcrumbModel() {
        Node currentNode = ScriptUtils.c().getSelected()
        if (currentNode == null) return
        ancestorsModel.clear()

        def fullPath = currentNode.getPathToRoot()
        if (fullPath && !fullPath[0].isRoot()) {
            fullPath = fullPath.reverse()
        }
        def ancestors = fullPath.size() > 1 ? fullPath[0..-2] : []

        if (useVisibleRootOnly) {
            def viewRoot = getActiveViewRoot()
            if (viewRoot != null && currentNode.getMindMap() == viewRoot.getMindMap()) {
                int idx = fullPath.indexOf(viewRoot)
                if (idx != -1 && idx < ancestors.size()) {
                    ancestors = ancestors[idx..-1]
                } else if (idx == ancestors.size()) {
                    ancestors = []
                }
            }
        }

        if (reverseAncestorOrder) {
            ancestors = ancestors.reverse()
        }

        ancestors.each { ancestorsModel.addElement(it) }

        SwingUtilities.invokeLater({
            if (breadcrumbJList != null && ancestorsModel.size() > 0) {
                breadcrumbJList.ensureIndexIsVisible(ancestorsModel.size() - 1)
            }
            if (breadcrumbOnlyPanel != null && !breadcrumbOnlyPanel.isVisible() && showOnlyBreadcrumbs) {
                breadcrumbOnlyPanel.setVisible(true)
            }
        })
    }

    private void navigateToNode(Node target) {
        if (target == null) return
        try {
            ScriptUtils.c().select(target)
            def mapFile = target.getMindMap().getFile()
            if (mapFile) {
                def uri = mapFile.toURI().toString() + "#" + target.getId()
                def link = new org.freeplane.core.util.Hyperlink(new URI(uri))
                org.freeplane.features.url.UrlManager.getController().loadHyperlink(link)
            }
        } catch (Exception ex) { ex.printStackTrace() }
    }

    // ========== Populate preview for map selection ==========
    private void populatePreview(Node node) {
        if (node == null) {
            clearPreview()
            removeMapNodeRow()
            return
        }
        
        // Add row at top of table
        addMapNodeRow(node)
        
        if (leftPreviewPanel != null && !leftPreviewPanel.isVisible() && !hidePreviewPanel) {
            leftPreviewPanel.setVisible(true)
            if (innerSplitPane != null) innerSplitPane.setDividerLocation(0.35)
        }
    
        NodeModel nodeModel = getNodeModel(node)
        String styleName = node.getStyle()?.getName()
        if (styleName) {
            Font previewFont = (fontPreviewCore != null) ? fontPreviewCore : new Font("Segoe UI", Font.PLAIN, 14)
            styleLabel.setText(styleName)
            styleLabel.setFont(previewFont.deriveFont(Font.PLAIN, previewFont.getSize()))
            styleLabel.setForeground(Color.BLACK)
        } else {
            styleLabel.setText("")
        }
    
        tagViewer.removeAll()
        def icons = iconController().getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE)
        def tags = iconController().getTagIcons(nodeModel)
        icons.each { tagViewer.add(new JLabel(it.getIcon())) }
        tags.each { tagViewer.add(new JLabel(it)) }
        tagViewer.revalidate(); tagViewer.repaint()
    
        Font useFontCore = (fontPreviewCore != null) ? fontPreviewCore : previewCore.getFont()
        String coreRaw = node.getHtmlText() ?: node.getPlainText()
        String styledCore = getStyledCellContent(coreRaw, node, useFontCore, true, 0)
        previewCore.setText("<html>${styledCore}</html>")
        Color nodeBg = getNodeBackgroundColor(node)
        if (nodeBg != null) {
            previewCore.setBackground(nodeBg)
        } else {
            previewCore.setBackground(UIManager.getColor("Panel.background"))
        }
        previewCore.setOpaque(true)
    
        Font detailsFont = (fontPreviewDetails != null) ? fontPreviewDetails : previewDetails.getFont()
        String detailsRaw = node.getDetails()?.getHtml() ?: node.getDetails()?.getPlain() ?: ""
        String styledDetails = getStyledCellContent(detailsRaw, node, detailsFont, true, 0)
        previewDetails.setText("<html>${styledDetails}</html>")
        previewDetails.setBackground(nodeBg != null ? nodeBg : UIManager.getColor("Panel.background"))
        previewDetails.setOpaque(true)
    
        Font noteFont = (fontPreviewNote != null) ? fontPreviewNote : previewNote.getFont()
        String noteRaw = node.getNote()?.getHtml() ?: node.getNote()?.getPlain() ?: ""
        String styledNote = getStyledCellContent(noteRaw, node, noteFont, true, 0)
        previewNote.setText("<html>${styledNote}</html>")
        previewNote.setBackground(nodeBg != null ? nodeBg : UIManager.getColor("Panel.background"))
        previewNote.setOpaque(true)
    
        breadcrumbPanel.removeAll()
        try {
            def fullPath = node.getPathToRoot()
            if (fullPath && !fullPath[0].isRoot()) {
                fullPath = fullPath.reverse()
            }
            def displayPath = fullPath.size() > 1 ? fullPath[0..-2] : []
            if (useVisibleRootOnly) {
                def viewRoot = getActiveViewRoot()
                if (viewRoot != null && node.mindMap == viewRoot.mindMap) {
                    int idx = fullPath.indexOf(viewRoot)
                    if (idx != -1) {
                        if (idx <= displayPath.size()) {
                            displayPath = displayPath[idx..-1]
                        } else {
                            displayPath = []
                        }
                    }
                }
            }
            int maxNodes = 5
            int start = Math.max(0, displayPath.size() - maxNodes)
            JPanel tempPanel = new JPanel()
            tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS))
            tempPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
            if (start > 0) {
                JLabel ellipsisLabel = new JLabel(" ... ")
                ellipsisLabel.setFont(ellipsisLabel.getFont().deriveFont(Font.BOLD))
                tempPanel.add(ellipsisLabel)
            }
            ButtonGroup bg = new ButtonGroup()
            for (int i = start; i < displayPath.size(); i++) {
                Node n = displayPath.get(i)
                String nodeText = n.getPlainText()
                String shortText = nodeText
                if (ancestorTrimLength > 0 && nodeText.length() > ancestorTrimLength) {
                    shortText = TextUtils.getShortText(nodeText, ancestorTrimLength, "\u2026")
                }
                JRadioButton btn = new JRadioButton(shortText)
                btn.setToolTipText(nodeText)
                if (fontBreadcrumb != null) btn.setFont(fontBreadcrumb)
                else btn.setFont(btn.getFont().deriveFont(Font.PLAIN))
                Color bgColor = getNodeBackgroundColor(n)
                if (bgColor != null) {
                    btn.setBackground(bgColor)
                    btn.setForeground(getForegroundForBackground(bgColor))
                    btn.setOpaque(true)
                    btn.setContentAreaFilled(true)
                } else {
                    btn.setOpaque(false)
                    btn.setForeground(UIManager.getColor("Label.foreground"))
                }
                Color borderColor = getBorderColorForNode(n)
                Border leftBorder = BorderFactory.createMatteBorder(0, 5, 0, 0, borderColor)
                Border padding = BorderFactory.createEmptyBorder(2, 6, 2, 6)
                btn.setBorder(BorderFactory.createCompoundBorder(leftBorder, padding))
                btn.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                btn.setHorizontalAlignment(SwingConstants.RIGHT)
                btn.setHorizontalTextPosition(SwingConstants.LEFT)
                btn.putClientProperty("node", n)
                final Node target = n
                final JRadioButton currentBtn = btn
                btn.addActionListener({ e ->
                    for (Component comp : tempPanel.getComponents()) {
                        if (comp instanceof JRadioButton) {
                            ((JRadioButton)comp).setForeground(UIManager.getColor("Label.foreground"))
                            ((JRadioButton)comp).setFont(((JRadioButton)comp).getFont().deriveFont(Font.PLAIN))
                            Node oldNode = (Node) ((JRadioButton)comp).getClientProperty("node")
                            if (oldNode != null) {
                                Color oldBg = getNodeBackgroundColor(oldNode)
                                if (oldBg != null) {
                                    ((JRadioButton)comp).setBackground(oldBg)
                                    ((JRadioButton)comp).setForeground(getForegroundForBackground(oldBg))
                                } else {
                                    ((JRadioButton)comp).setOpaque(false)
                                }
                            }
                        }
                    }
                    currentBtn.setForeground(Color.BLUE)
                    currentBtn.setFont(currentBtn.getFont().deriveFont(Font.BOLD))
                    try {
                        ScriptUtils.c().select(target)
                        def mapFile = target.getMindMap().getFile()
                        if (mapFile) {
                            def uri = mapFile.toURI().toString() + "#" + target.getId()
                            def link = new org.freeplane.core.util.Hyperlink(new URI(uri))
                            org.freeplane.features.url.UrlManager.getController().loadHyperlink(link)
                            SwingUtilities.invokeLater(new Runnable() {
                                void run() {
                                    if (resultsTable != null && resultsTable.isShowing()) {
                                        resultsTable.requestFocusInWindow()
                                    }
                                }
                            })
                        }
                    } catch (Exception ex) { ex.printStackTrace() }
                })
                tempPanel.add(btn)
                bg.add(btn)
            }
            for (Component comp : tempPanel.getComponents()) {
                if (comp instanceof JRadioButton) {
                    Node storedNode = (Node) ((JRadioButton)comp).getClientProperty("node")
                    if (storedNode == node) {
                        ((JRadioButton)comp).setSelected(true)
                        ((JRadioButton)comp).setForeground(Color.BLUE)
                        ((JRadioButton)comp).setFont(((JRadioButton)comp).getFont().deriveFont(Font.BOLD))
                        break
                    }
                }
            }
            breadcrumbPanel.add(tempPanel, BorderLayout.CENTER)
        } catch (Exception e) {
            breadcrumbPanel.add(new JLabel(" "), BorderLayout.CENTER)
            System.err.println("Breadcrumb error: ${e.message}")
        }
        breadcrumbPanel.revalidate()
        breadcrumbPanel.repaint()
        breadcrumbPanel.setVisible(true)
    }
} // End of SimpleMapCrawler class
