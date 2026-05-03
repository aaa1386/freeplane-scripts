// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/Search-Filter-Associations"})

// Modified by aaa1386  Last Update:2026 https://github.com/aaa1386  
/*
 * Info & Discussion: https://github.com/freeplane/freeplane/discussions/2344
 *
 * Last Update: 2025-03-17
 *
 * ---------
 *
 * MapCrawler: Freeplane tool for searching across different map scopes, and quick inspection of results
 *
 * Copyright (C) 2025 bbarbosa
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 *
 *
 */


/*
 * MapCrawler: Freeplane tool for searching across different map scopes, and quick inspection of results
 *
 * Copyright (C) 2025 bbarbosa
 * Modified: add minimize fix (flag), search source (Folder/Open Maps), separate font settings
 */

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import java.util.zip.CRC32
import javax.swing.AbstractAction
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.ButtonModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JRadioButton
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.tree.TreePath
import org.freeplane.api.Loader
import org.freeplane.api.MindMap
import org.freeplane.api.Node
import org.freeplane.core.resources.ResourceController
import org.freeplane.core.ui.components.HSLColorConverter
import org.freeplane.core.ui.components.TagIcon
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.FreeplaneVersion
import org.freeplane.core.util.Hyperlink
import org.freeplane.core.util.MenuUtils
import org.freeplane.features.icon.IconController
import org.freeplane.features.icon.NamedIcon
import org.freeplane.features.icon.mindmapmode.MIconController
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.mindmapmode.MModeController
import org.freeplane.features.styles.LogicalStyleController.StyleOption
import org.freeplane.features.url.UrlManager
import org.freeplane.plugin.script.proxy.MapProxy
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.map.MapView
import groovy.transform.Field

@Field static FreeplaneMapCrawler mapCrawler
@Field static boolean emojisNotDetectedInitially = false
@Field static boolean isReadNotPermittedInitially = false
@Field static String minimumFreeplaneVersionSupported = "1.12.6"

if (!validateEnvironment()) return

if (mapCrawler == null) {
    mapCrawler = new FreeplaneMapCrawler()
    return
}
mapCrawler.show()

boolean validateEnvironment() {
    if (FreeplaneVersion.getVersion().isOlderThan(FreeplaneVersion.getVersion(minimumFreeplaneVersionSupported))) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "This tool currently supports only freeplane versions starting from 1.12.6.",
            FreeplaneMapCrawler.PLUGIN_NAME, JOptionPane.WARNING_MESSAGE)
        return false
    }
    if (!ResourceController.getResourceController().getBooleanProperty("execute_scripts_without_file_restriction")) {
        isReadNotPermittedInitially = true
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "You need to activate the following script permissions: Please check the option 'Preferences\u2026->Plugins->Scripting->Permit file/read operations'. Then restart Freeplane.",
            FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
        return false
    } else if (isReadNotPermittedInitially) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Please restart Freeplane.",
            FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
        return false
    }
    if (!ResourceController.getResourceController().getBooleanProperty("add_emojis_to_menu")) {
        emojisNotDetectedInitially = true
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "You need to activate emojis in order to use this tool. Please check the option 'Tools->Preferences\u2026->Appearance->Icons->Add emojis to menu'. Then restart Freeplane.",
            FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
        return false
    } else if (emojisNotDetectedInitially) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Please restart Freeplane.",
            FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
        return false
    }
    return true
}

class FreeplaneMapCrawler {
    private String baseDir = "D:\\AJ\\OneDrive\\FP"
    private JDialog frame
    private JTextField searchField
    private JButton searchButton
    private JButton aboutButton
    private JButton settingsButton
    private JButton minimizeButton
    private JTable resultsTable
    private JScrollPane resultsScrollPane
    private PreviewPane contentsPreviewer
    private JScrollPane previewScrollPane
    private JPanel previewPane
    private JPanel tagViewer
    private JLabel statusLabel
    private JPanel statusPane
    private JPanel progressPane
    private JProgressBar progressBar
    private JPanel breadCrumbPanel
    private JPanel centerPanel
    private JButton folderBtn

    private boolean matchCase = false
    private boolean wholeWord = false
    private boolean windowHidden = false   // پرچم برای وضعیت مخفی بودن پنجره

    // Scope: All / Style
    private ButtonGroup scopeBtnGroup
    private JRadioButton allScopeRadio
    private JRadioButton styleScopeRadio
    private JTextField styleField

    // Target checkboxes
    private JCheckBox coreCheckBox
    private JCheckBox detailsCheckBox
    private JCheckBox noteCheckBox

    // Source: Folder / Open Maps
    private ButtonGroup sourceBtnGroup
    private JRadioButton folderSourceRadio
    private JRadioButton openMapsSourceRadio

    // Font settings
    private Font previewCoreFont = null
    private Font previewDetailsFont = null
    private Font previewNoteFont = null
    private Font resultsFont = null
    private Font breadcrumbFont = null

    private static boolean isDev = false
    private static final PLUGIN_NAME = "MapCrawler"
    private static final PLUGIN_VERSION = "0.9.4"
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor()
    private static final Map<String, MindMap> LOADED_MAPS = new ConcurrentHashMap()
    private static final Map<String, Long> LOADED_MAPS_MODIFICATION_DATE = new ConcurrentHashMap()
    private static final Map<String, Color> PATH_COLORS = new HashMap()

    enum SearchScope { ALL, STYLE }
    enum SearchSource { FOLDER, OPEN_MAPS }

    FreeplaneMapCrawler() {
        loadSavedSettings()
        render()
    }

    private void loadSavedSettings() {
        ResourceController rc = ResourceController.getResourceController()
        String savedDir = rc.getProperty("mapcrawler.lastUsedDir")
        if (savedDir != null && !savedDir.isEmpty() && new File(savedDir).exists()) {
            baseDir = savedDir
        }
        String savedPreviewCoreFont = rc.getProperty("mapcrawler.previewCoreFont")
        if (savedPreviewCoreFont != null) previewCoreFont = Font.decode(savedPreviewCoreFont)
        String savedPreviewDetailsFont = rc.getProperty("mapcrawler.previewDetailsFont")
        if (savedPreviewDetailsFont != null) previewDetailsFont = Font.decode(savedPreviewDetailsFont)
        String savedPreviewNoteFont = rc.getProperty("mapcrawler.previewNoteFont")
        if (savedPreviewNoteFont != null) previewNoteFont = Font.decode(savedPreviewNoteFont)
        String savedResultsFont = rc.getProperty("mapcrawler.resultsFont")
        if (savedResultsFont != null) resultsFont = Font.decode(savedResultsFont)
        String savedBreadcrumbFont = rc.getProperty("mapcrawler.breadcrumbFont")
        if (savedBreadcrumbFont != null) breadcrumbFont = Font.decode(savedBreadcrumbFont)
    }

    private void saveFontSettings() {
        ResourceController rc = ResourceController.getResourceController()
        rc.setProperty("mapcrawler.previewCoreFont", previewCoreFont != null ? previewCoreFont.getFamily() + "-" + previewCoreFont.getSize() : null)
        rc.setProperty("mapcrawler.previewDetailsFont", previewDetailsFont != null ? previewDetailsFont.getFamily() + "-" + previewDetailsFont.getSize() : null)
        rc.setProperty("mapcrawler.previewNoteFont", previewNoteFont != null ? previewNoteFont.getFamily() + "-" + previewNoteFont.getSize() : null)
        rc.setProperty("mapcrawler.resultsFont", resultsFont != null ? resultsFont.getFamily() + "-" + resultsFont.getSize() : null)
        rc.setProperty("mapcrawler.breadcrumbFont", breadcrumbFont != null ? breadcrumbFont.getFamily() + "-" + breadcrumbFont.getSize() : null)
    }

    void render() {
        frame = new JDialog(UITools.getCurrentFrame())
        frame.setModal(false)

        // results table
        resultsScrollPane = new JScrollPane()
        resultsTable = createResultsList()
        resultsScrollPane.setViewportView(resultsTable)

        Color topBorderColor = isLightLaF() ? Color.GRAY : Color.DARK_GRAY
        Border border = BorderFactory.createMatteBorder(1, 0, 0, 0,
            new Color(topBorderColor.getRed(), topBorderColor.getGreen(), topBorderColor.getBlue(), 100))

        resultsScrollPane.setBorder(border)
        resultsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)

        // preview panel
        Color previewPanelBG = isLightLaF() ? Color.WHITE : UIManager.getColor("Panel.background").darker().darker().darker()
        previewScrollPane = new JScrollPane()
        contentsPreviewer = new PreviewPane(isLightLaF(), previewPanelBG, { node -> goToNode(getNodeModel(node)) })
        previewScrollPane.setViewportView(contentsPreviewer)
        previewScrollPane.setMinimumSize(new Dimension(300, 200))
        previewScrollPane.setPreferredSize(new Dimension(300, 200))
        previewScrollPane.getViewport().setBackground(previewPanelBG)
        previewScrollPane.setBorder(BorderFactory.createEmptyBorder())

        tagViewer = new JPanel(new WrapLayout(FlowLayout.CENTER))
        tagViewer.setBackground(previewPanelBG)
        tagViewer.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0))
        previewPane = new JPanel(new BorderLayout())
        previewPane.setBackground(previewPanelBG)
        previewScrollPane.setBackground(previewPanelBG)
        previewPane.setBorder(border)

        breadCrumbPanel = new JPanel(new BorderLayout())

        previewPane.add(previewScrollPane, BorderLayout.CENTER)
        previewPane.add(tagViewer, BorderLayout.NORTH)

        centerPanel = new JPanel(new BorderLayout())
        centerPanel.add(breadCrumbPanel, BorderLayout.NORTH)
        centerPanel.add(previewPane, BorderLayout.EAST)
        centerPanel.add(resultsScrollPane, BorderLayout.CENTER)

        // search panel
        JPanel searchPanel = new JPanel(new BorderLayout())
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0))
        searchField = new JTextField(20)
        searchField.addActionListener({ l -> requestSearch() })
        searchButton = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-1F50D"))

        JPanel northSearchPanel = new JPanel()
        northSearchPanel.add(searchField)
        northSearchPanel.add(searchButton)

        JPanel searchOptnsPanel = new JPanel(new WrapLayout())

        // ----- Source: Folder / Open Maps -----
        folderSourceRadio = new JRadioButton("Folder")
        folderSourceRadio.setActionCommand(SearchSource.FOLDER.toString())
        openMapsSourceRadio = new JRadioButton("Open Maps")
        openMapsSourceRadio.setActionCommand(SearchSource.OPEN_MAPS.toString())
        folderSourceRadio.setSelected(true)

        sourceBtnGroup = new ButtonGroup()
        sourceBtnGroup.add(folderSourceRadio)
        sourceBtnGroup.add(openMapsSourceRadio)

        searchOptnsPanel.add(folderSourceRadio)
        searchOptnsPanel.add(openMapsSourceRadio)

        JSeparator sourceSep = new JSeparator(SwingConstants.VERTICAL)
        sourceSep.setMinimumSize(new Dimension(2, 15))
        sourceSep.setPreferredSize(new Dimension(2, 15))
        searchOptnsPanel.add(sourceSep)

        // ----- Scope: All / Style -----
        allScopeRadio = new JRadioButton("All (no style filter)")
        allScopeRadio.setActionCommand(SearchScope.ALL.toString())
        styleScopeRadio = new JRadioButton("Style (filter by style)")
        styleScopeRadio.setActionCommand(SearchScope.STYLE.toString())
        allScopeRadio.setSelected(true)

        scopeBtnGroup = new ButtonGroup()
        scopeBtnGroup.add(allScopeRadio)
        scopeBtnGroup.add(styleScopeRadio)

        searchOptnsPanel.add(allScopeRadio)
        searchOptnsPanel.add(styleScopeRadio)

        styleField = new JTextField(15)
        styleField.setEnabled(false)
        styleField.setToolTipText("Enter style name(s), separate with comma, semicolon or dash (e.g., ساختار, پرسش)")
        searchOptnsPanel.add(styleField)

        styleScopeRadio.addActionListener({ e ->
            styleField.setEnabled(styleScopeRadio.isSelected())
            if (!styleScopeRadio.isSelected()) styleField.setText("")
        })
        allScopeRadio.addActionListener({ e ->
            styleField.setEnabled(false)
            styleField.setText("")
        })

        JSeparator separator1 = new JSeparator(SwingConstants.VERTICAL)
        separator1.setMinimumSize(new Dimension(2, 15))
        separator1.setPreferredSize(new Dimension(2, 15))
        searchOptnsPanel.add(separator1)

        // ----- Target checkboxes -----
        coreCheckBox = new JCheckBox("core")
        detailsCheckBox = new JCheckBox("details")
        noteCheckBox = new JCheckBox("note")
        coreCheckBox.setSelected(true)

        searchOptnsPanel.add(coreCheckBox)
        searchOptnsPanel.add(detailsCheckBox)
        searchOptnsPanel.add(noteCheckBox)

        JSeparator separator2 = new JSeparator(SwingConstants.VERTICAL)
        separator2.setMinimumSize(new Dimension(2, 15))
        separator2.setPreferredSize(new Dimension(2, 15))
        searchOptnsPanel.add(separator2)

        // match case / whole word
        JCheckBox matchCaseCheck = new JCheckBox("Aa")
        matchCaseCheck.setToolTipText("Match case")
        matchCaseCheck.addActionListener({ l -> matchCase = ((JCheckBox) l.getSource()).isSelected() })
        JCheckBox wholeWordCheck = new JCheckBox("WW")
        wholeWordCheck.setToolTipText("Whole word")
        wholeWordCheck.addActionListener({ l -> wholeWord = ((JCheckBox) l.getSource()).isSelected() })

        searchOptnsPanel.add(matchCaseCheck)
        searchOptnsPanel.add(wholeWordCheck)

        searchPanel.add(northSearchPanel, BorderLayout.NORTH)
        searchPanel.add(searchOptnsPanel, BorderLayout.SOUTH)

        searchButton.addActionListener({ l -> requestSearch() })

        // status bar
        statusLabel = new JLabel(" ")
        statusLabel.setForeground(isLightLaF() ? Color.BLACK : Color.WHITE)
        aboutButton = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-2139"))
        aboutButton.addActionListener({ l -> displayAbout(frame) })

        settingsButton = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-2699"))
        settingsButton.setToolTipText("Font Settings")
        settingsButton.addActionListener({ l -> showFontSettingsDialog() })

        // Hide button (underscore)
        minimizeButton = new JButton("_")
        minimizeButton.setToolTipText("Hide window (restore by running script again)")
        minimizeButton.setFont(minimizeButton.getFont().deriveFont(Font.BOLD, 14f))
        minimizeButton.addActionListener({ e ->
            frame.setVisible(false)
            windowHidden = true
        })

        statusPane = new JPanel(new FlowLayout(FlowLayout.LEFT))
        folderBtn = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-1F4C2"))
        folderBtn.setText(baseDir.isBlank() ? "Select folder..." : "..." + File.separator + new File(baseDir).getName())
        folderBtn.addActionListener({ l -> showFolderChooseDialog(folderBtn) })
        folderBtn.setToolTipText(baseDir)
        statusPane.add(aboutButton)
        statusPane.add(settingsButton)
        statusPane.add(minimizeButton)
        statusPane.add(folderBtn)
        statusPane.add(statusLabel)

        // enable/disable folder button based on source selection
        folderSourceRadio.addActionListener({ e -> folderBtn.setEnabled(true) })
        openMapsSourceRadio.addActionListener({ e -> folderBtn.setEnabled(false) })

        progressPane = new JPanel(new BorderLayout())
        progressBar = new JProgressBar()
        progressBar.setString("processing...")
        progressBar.setStringPainted(true)
        progressBar.putClientProperty("JProgressBar.largeHeight", true)
        progressBar.putClientProperty("JProgressBar.repaintInterval", 200)
        progressPane.add(progressBar, BorderLayout.CENTER)

        frame.getContentPane().add(searchPanel, BorderLayout.NORTH)
        frame.getContentPane().add(centerPanel, BorderLayout.CENTER)
        frame.getContentPane().add(statusPane, BorderLayout.SOUTH)

        UITools.getCurrentFrame().addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) { frame.dispose() }
        })

        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focusSearch")
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("F1"), "focusSearch")
        frame.getRootPane().getActionMap().put("focusSearch", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { focusSearchField() }
        })

        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                frame.revalidate()
                frame.repaint()
            }
        })

        frame.setTitle(PLUGIN_NAME)                
        // اضافه کردن میانبر Alt+J
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.ALT_DOWN_MASK), "hideWindow")
        frame.getRootPane().getActionMap().put("hideWindow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.setVisible(false)
                windowHidden = true
            }
        })
        frame.setSize(new Dimension(600, 600))
        frame.setLocationRelativeTo(null)
        frame.setVisible(true)

        applyFonts()
    }

    private void requestSearch() {
        if (searchField.getText().isBlank()) return

        List<String> styleNames = []
        if (styleScopeRadio.isSelected()) {
            String raw = styleField.getText().trim()
            if (raw.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter at least one style name when 'Style' scope is selected.",
                    "Missing Style", JOptionPane.WARNING_MESSAGE)
                return
            }
            styleNames = raw.split(/[;,\-]+/).collect { it.trim() }.findAll { !it.isEmpty() }
            if (styleNames.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "No valid style name entered. Use commas, semicolons or dashes.",
                    "Invalid Style", JOptionPane.WARNING_MESSAGE)
                return
            }
        }

        if (!coreCheckBox.isSelected() && !detailsCheckBox.isSelected() && !noteCheckBox.isSelected()) {
            JOptionPane.showMessageDialog(frame, "Please select at least one target (core, details, note).",
                "No Target Selected", JOptionPane.WARNING_MESSAGE)
            return
        }

        searchButton.setEnabled(false)
        searchField.setEnabled(false)
        ((DefaultTableModel) resultsTable.getModel()).setRowCount(0)
        statusLabel.setText(" ")
        contentsPreviewer.removeAll()
        tagViewer.removeAll()
        previewPane.revalidate()
        setActivateAndResetProgressBar(true)

        SearchScope scope = SearchScope.valueOf(scopeBtnGroup.getSelection().getActionCommand())
        SearchSource source = SearchSource.valueOf(sourceBtnGroup.getSelection().getActionCommand())
        boolean searchCore = coreCheckBox.isSelected()
        boolean searchDetails = detailsCheckBox.isSelected()
        boolean searchNote = noteCheckBox.isSelected()

        progressBar.setValue(10)
        progressBar.revalidate()
        progressBar.repaint()

        SwingUtilities.invokeLater({
            searchConcurrent(searchField.getText().trim(), scope, styleNames, source,
                searchCore, searchDetails, searchNote)
        })
    }

    private void showFolderChooseDialog(JButton callerBtn) {
        JFileChooser fileChooser = new JFileChooser()
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
        fileChooser.setDialogTitle("Select folder containing .mm files")
        if (!baseDir.isBlank()) {
            fileChooser.setCurrentDirectory(new File(baseDir))
        }
        int option = fileChooser.showOpenDialog(frame)
        if (option == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile()
            baseDir = selectedFile.getAbsolutePath()
            ResourceController.getResourceController().setProperty("mapcrawler.lastUsedDir", baseDir)
            callerBtn.setText("..." + File.separator + new File(baseDir).getName())
            callerBtn.setToolTipText(baseDir)
            ((DefaultTableModel) resultsTable.getModel()).setRowCount(0)
            statusLabel.setText(" ")
        }
    }

    private void searchConcurrent(String keyword, SearchScope scope, List<String> styleNames, SearchSource source,
                                  boolean searchCore, boolean searchDetails, boolean searchNote) {
        List<MindMap> mapsToSearch = []
        if (source == SearchSource.FOLDER) {
            List<Path> mms
            try {
                mms = findByFileExtension(Paths.get(baseDir), ".mm")
            } catch (Exception e) {
                doWhenSearchHasFinished()
                JOptionPane.showMessageDialog(frame, "Provided path cannot be accessed or processed: " + baseDir +
                    "\n\nCause: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
                return
            }
            if (mms.isEmpty()) {
                doWhenSearchHasFinished()
                JOptionPane.showMessageDialog(frame, "No map was found in the provided directory: " + baseDir)
                return
            }
            for (Path path : mms) {
                try {
                    MindMap map = LOADED_MAPS.compute(path.toString(), { key, val -> getMindMap(path.toFile()) })
                    LOADED_MAPS_MODIFICATION_DATE.compute(path.toString(), { key, val -> LOADED_MAPS.get(key).getFile().lastModified() })
                    PATH_COLORS.putIfAbsent(path.toString(), determineStringColor(path.toString()))
                    mapsToSearch << map
                } catch (Exception e) {
                    // ignore individual map loading errors
                }
            }
        } else { // OPEN_MAPS
            List<MindMap> openMaps = ScriptUtils.c().getOpenMindMaps()
            if (openMaps.isEmpty()) {
                doWhenSearchHasFinished()
                JOptionPane.showMessageDialog(frame, "No open maps found.", "No Maps", JOptionPane.WARNING_MESSAGE)
                return
            }
            for (MindMap map : openMaps) {
                String path = map.getFile()?.toString() ?: "unsaved_" + map.getRoot().getId()
                LOADED_MAPS.putIfAbsent(path, map)
                PATH_COLORS.putIfAbsent(path, determineStringColor(path))
                mapsToSearch << map
            }
        }

        if (mapsToSearch.isEmpty()) {
            doWhenSearchHasFinished()
            return
        }

        final int totalMaps = mapsToSearch.size()
        final AtomicInteger processedCount = new AtomicInteger(0)

        for (MindMap map : mapsToSearch) {
            progressBar.setValue((int) (100.0 / totalMaps * (processedCount.get() + 1)))
            progressBar.repaint()

            final MindMap currentMap = map
            EXECUTOR.execute({
                try {
                    List<? extends Node> foundNodes = currentMap.getRoot().findAll().stream().filter({ mapNode ->
                        return isNodeAMatch(keyword, mapNode, scope, styleNames,
                            searchCore, searchDetails, searchNote)
                    }).collect(Collectors.toList())

                    List<NodeModel> items = foundNodes.stream().map({ match ->
                        ((MapProxy) match.getMindMap()).getDelegate().getNodeForID(match.getId())
                    }).collect(Collectors.toList())

                    SwingUtilities.invokeLater({
                        for (NodeModel item : items) appendMatch(item)
                        if (items.size() > 0 && resultsTable.getModel().getRowCount() == items.size()) {
                            resultsTable.setRowSelectionInterval(0, 0)
                            resultsTable.requestFocusInWindow()
                        }
                    })
                } catch (Exception e) {
                    e.printStackTrace()
                    SwingUtilities.invokeLater({
                        JOptionPane.showMessageDialog(frame, "Error searching map: " +
                            (currentMap.getFile()?.getName() ?: "unsaved") + "\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
                    })
                } finally {
                    int processed = processedCount.incrementAndGet()
                    if (processed == totalMaps) {
                        SwingUtilities.invokeLater({ doWhenSearchHasFinished() })
                    }
                }
            })
        }
    }

    private void doWhenSearchHasFinished() {
        if (resultsTable.getModel().getRowCount() == 0) {
            breadCrumbPanel.removeAll()
            breadCrumbPanel.revalidate()
            breadCrumbPanel.repaint()
        }
        searchButton.setEnabled(true)
        searchField.setEnabled(true)
        setActivateAndResetProgressBar(false)
    }

    private boolean isNodeAMatch(String query, Node mapNode, SearchScope scope, List<String> styleNames,
                                  boolean searchCore, boolean searchDetails, boolean searchNote) {
        if (scope == SearchScope.STYLE) {
            if (styleNames == null || styleNames.isEmpty()) return false
            def styleObj = mapNode.getStyle()
            if (styleObj == null) return false
            String nodeStyleName = styleObj.getName()
            if (nodeStyleName == null) return false
            if (!styleNames.any { it.equals(nodeStyleName) }) return false
        }
        if (searchCore && textContainsMatch(mapNode.getPlainText(), query)) return true
        if (searchDetails && mapNode.details != null && textContainsMatch(mapNode.details.plain, query)) return true
        if (searchNote && mapNode.note != null && textContainsMatch(mapNode.note.plain, query)) return true
        return false
    }

    private boolean textContainsMatch(String baseTxt, String query) {
        if (!matchCase) {
            baseTxt = baseTxt.toLowerCase()
            query = query.toLowerCase()
        }
        if (wholeWord) {
            return baseTxt.matches(".*\\b\\Q" + query + "\\E\\b.*")
        } else {
            return baseTxt.contains(query)
        }
    }

    void focusSearchField() {
        SwingUtilities.invokeLater({
            searchField.requestFocusInWindow()
            searchField.selectAll()
        })
    }

    void show() {
        if (frame != null) {
            if (windowHidden) {
                frame.setVisible(true)
                windowHidden = false
            }
            if (!frame.isVisible()) {
                frame.setVisible(true)
            }
            frame.toFront()
        }
        focusSearchField()
    }

    // ----------------------------------------------------------------------
    // Font settings dialog and helpers
    // ----------------------------------------------------------------------
    private void showFontSettingsDialog() {
        JDialog dialog = new JDialog(frame, "Font Settings", true)
        dialog.setLayout(new BorderLayout())

        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10))
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

        // Core font
        panel.add(new JLabel("Preview - Core Font:"))
        JButton coreFontBtn = new JButton(formatFontButtonText(previewCoreFont))
        coreFontBtn.addActionListener({ e ->
            Font selected = showFontChooser(dialog, previewCoreFont)
            if (selected != null) {
                previewCoreFont = selected
                coreFontBtn.setText(formatFontButtonText(previewCoreFont))
                applyFonts()
                saveFontSettings()
            }
        })
        panel.add(coreFontBtn)

        // Details font
        panel.add(new JLabel("Preview - Details Font:"))
        JButton detailsFontBtn = new JButton(formatFontButtonText(previewDetailsFont))
        detailsFontBtn.addActionListener({ e ->
            Font selected = showFontChooser(dialog, previewDetailsFont)
            if (selected != null) {
                previewDetailsFont = selected
                detailsFontBtn.setText(formatFontButtonText(previewDetailsFont))
                applyFonts()
                saveFontSettings()
            }
        })
        panel.add(detailsFontBtn)

        // Note font
        panel.add(new JLabel("Preview - Note Font:"))
        JButton noteFontBtn = new JButton(formatFontButtonText(previewNoteFont))
        noteFontBtn.addActionListener({ e ->
            Font selected = showFontChooser(dialog, previewNoteFont)
            if (selected != null) {
                previewNoteFont = selected
                noteFontBtn.setText(formatFontButtonText(previewNoteFont))
                applyFonts()
                saveFontSettings()
            }
        })
        panel.add(noteFontBtn)

        // Results table font
        panel.add(new JLabel("Results Table Font:"))
        JButton resultsFontBtn = new JButton(formatFontButtonText(resultsFont))
        resultsFontBtn.addActionListener({ e ->
            Font selected = showFontChooser(dialog, resultsFont)
            if (selected != null) {
                resultsFont = selected
                resultsFontBtn.setText(formatFontButtonText(resultsFont))
                applyFonts()
                saveFontSettings()
            }
        })
        panel.add(resultsFontBtn)

        // Breadcrumb font
        panel.add(new JLabel("Breadcrumb Font:"))
        JButton breadcrumbFontBtn = new JButton(formatFontButtonText(breadcrumbFont))
        breadcrumbFontBtn.addActionListener({ e ->
            Font selected = showFontChooser(dialog, breadcrumbFont)
            if (selected != null) {
                breadcrumbFont = selected
                breadcrumbFontBtn.setText(formatFontButtonText(breadcrumbFont))
                applyFonts()
                saveFontSettings()
            }
        })
        panel.add(breadcrumbFontBtn)

        // Reset button
        JButton resetBtn = new JButton("Reset All to Default")
        resetBtn.addActionListener({ e ->
            previewCoreFont = null
            previewDetailsFont = null
            previewNoteFont = null
            resultsFont = null
            breadcrumbFont = null
            coreFontBtn.setText(formatFontButtonText(null))
            detailsFontBtn.setText(formatFontButtonText(null))
            noteFontBtn.setText(formatFontButtonText(null))
            resultsFontBtn.setText(formatFontButtonText(null))
            breadcrumbFontBtn.setText(formatFontButtonText(null))
            applyFonts()
            saveFontSettings()
        })
        panel.add(resetBtn)
        panel.add(new JLabel("")) // spacer

        dialog.add(panel, BorderLayout.CENTER)

        JButton closeBtn = new JButton("Close")
        closeBtn.addActionListener({ e -> dialog.dispose() })
        dialog.add(closeBtn, BorderLayout.SOUTH)

        dialog.pack()
        dialog.setLocationRelativeTo(frame)
        dialog.setVisible(true)
    }

    private String formatFontButtonText(Font font) {
        return font == null ? "Default" : font.getName() + " " + font.getSize()
    }

    private Font showFontChooser(Component parent, Font currentFont) {
        String defaultName = currentFont != null ? currentFont.getName() : UIManager.getFont("Label.font").getName()
        int defaultSize = currentFont != null ? currentFont.getSize() : 12
        String input = JOptionPane.showInputDialog(parent, "Enter font name and size (e.g., Tahoma 14):", defaultName + " " + defaultSize)
        if (input == null) return null
        String[] parts = input.trim().split(" ")
        if (parts.length < 2) return null
        String name = parts[0]
        int size = 12
        try {
            size = Integer.parseInt(parts[1])
        } catch (Exception e) {}
        return new Font(name, Font.PLAIN, size)
    }

    private void applyFonts() {
        // Core
        if (previewCoreFont != null) {
            contentsPreviewer.textAreaCore.setFont(previewCoreFont.deriveFont((float)(previewCoreFont.getSize() + 4)))
        } else {
            Font defaultFont = UIManager.getFont("Label.font")
            contentsPreviewer.textAreaCore.setFont(defaultFont.deriveFont((float)(defaultFont.getSize() + 4)))
        }
        // Details
        if (previewDetailsFont != null) {
            contentsPreviewer.textAreaDetails.setFont(previewDetailsFont.deriveFont(Font.ITALIC))
        } else {
            Font defaultFont = UIManager.getFont("Label.font")
            contentsPreviewer.textAreaDetails.setFont(defaultFont.deriveFont(Font.ITALIC))
        }
        // Note
        if (previewNoteFont != null) {
            contentsPreviewer.textAreaNote.setFont(previewNoteFont)
        } else {
            contentsPreviewer.textAreaNote.setFont(UIManager.getFont("Label.font"))
        }
        // Results table
        if (resultsFont != null) {
            resultsTable.setFont(resultsFont)
            resultsTable.setRowHeight(resultsFont.getSize() + 10)
        } else {
            Font defaultFont = UIManager.getFont("Table.font")
            resultsTable.setFont(defaultFont)
            resultsTable.setRowHeight(defaultFont.getSize() + 8)
        }
        resultsTable.repaint()
        // Breadcrumb
        if (breadcrumbFont != null) setBreadcrumbFont()
    }

    private void setBreadcrumbFont() {
        if (breadcrumbPanel.getComponentCount() > 0) {
            setFontRecursively(breadcrumbPanel, breadcrumbFont)
        }
    }

    private void setFontRecursively(Component comp, Font font) {
        comp.setFont(font)
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                setFontRecursively(child, font)
            }
        }
    }

    // ----------------------------------------------------------------------
    // Results list with navigation
    // ----------------------------------------------------------------------
    private JTable createResultsList() {
        JTable table = new JTable(0, 1)
        table.setDefaultEditor(Object.class, null)
        table.setIntercellSpacing(new Dimension(0, 1))
        table.setTableHeader(null)
        int cellMargin = 5
        Color bgColor = isLightLaF() ? UIManager.getColor("Table.background") : UIManager.getColor("Table.background").darker().darker()
        Color fgColor = UIManager.getColor("Table.foreground")
        Color selectedBgColor = UIManager.getColor("Table.selectionBackground")
        Color selectedFgColor = UIManager.getColor("Table.selectionForeground")
        Color intercellBorderColor = isLightLaF() ? UIManager.getColor("Table.gridColor").darker() : UIManager.getColor("Table.gridColor").brighter()
        table.setBackground(bgColor)
        table.setGridColor(intercellBorderColor)
        table.getColumnModel().getColumn(0).setCellRenderer(new NodeCellWrapRenderer(cellMargin, bgColor, selectedBgColor, fgColor, selectedFgColor))
        table.setBorder(BorderFactory.createEmptyBorder())
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        // Double-click navigation
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    JTable source = (JTable) e.getSource()
                    int row = source.getSelectedRow()
                    if (row != -1) {
                        NodeModel selectedNode = (NodeModel) source.getModel().getValueAt(row, 0)[0]
                        goToNode(selectedNode)
                    }
                }
            }
        })

        // Enter key navigation
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "navigateToNode")
        table.getActionMap().put("navigateToNode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = table.getSelectedRow()
                if (row != -1) {
                    NodeModel selectedNode = (NodeModel) table.getModel().getValueAt(row, 0)[0]
                    goToNode(selectedNode)
                }
            }
        })

        // Selection listener (preview)
        ListSelectionModel selectionModel = table.getSelectionModel()
        selectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return
                ListSelectionModel lsm = (ListSelectionModel) e.getSource()
                int selectedRow
                if (lsm.isSelectionEmpty()) {
                    selectedRow = -1
                } else {
                    selectedRow = lsm.getMinSelectionIndex()
                    NodeModel selectedNode = (NodeModel) table.getModel().getValueAt(selectedRow, 0)[0]
                    List<TagIcon> tags = FreeplaneMapCrawler.iconController().getTagIcons(selectedNode)
                    Collection<NamedIcon> icons = FreeplaneMapCrawler.iconController().getIcons(selectedNode, StyleOption.FOR_UNSELECTED_NODE)

                    String path = selectedNode.getMap().getFile().toString()
                    Node apiNode = LOADED_MAPS.get(path).node(selectedNode.createID())

                    List<BreadcrumbDetails> breadCrumbStringsWithToolTips = apiNode.getPathToRoot().stream()
                        .map({ node ->
                            return new BreadcrumbDetails(
                                org.freeplane.core.util.TextUtils.getShortText(node.getPlainText(), 8, "\u2026"),
                                node.getPlainText(),
                                {
                                    List<TagIcon> tagsFromSelectedBreadcrumb = FreeplaneMapCrawler.iconController().getTagIcons(getNodeModel(node))
                                    Collection<NamedIcon> iconsFromSelectedBreadcrumb = FreeplaneMapCrawler.iconController().getIcons(getNodeModel(node), StyleOption.FOR_UNSELECTED_NODE)
                                    previewNodeAction(node, getNodeModel(node))
                                    previewTagsAndIconsAction(tagsFromSelectedBreadcrumb, iconsFromSelectedBreadcrumb)
                                    frame.getContentPane().revalidate()
                                    frame.getContentPane().repaint()
                                })
                        })
                        .collect(Collectors.toList())

                    breadCrumbPanel.removeAll()
                    breadCrumbPanel.add(BreadcrumbList.makeBreadcrumbListWithToolTip(breadCrumbStringsWithToolTips,
                        isLightLaF() ? UIManager.getColor("ProgressBar.foreground").brighter() : UIManager.getColor("ProgressBar.foreground").darker()), BorderLayout.CENTER)
                    breadCrumbPanel.revalidate()
                    breadCrumbPanel.repaint()

                    if (breadcrumbFont != null) {
                        setFontRecursively(breadCrumbPanel, breadcrumbFont)
                    }

                    // status text: show relative path if source is folder, else just map name
                    if (folderSourceRadio.isSelected()) {
                        statusLabel.setText(path.substring(path.indexOf(baseDir) + baseDir.length()))
                    } else {
                        statusLabel.setText(selectedNode.getMap().getFile()?.getName() ?: "unsaved")
                    }
                    previewNodeAction(apiNode, selectedNode)
                    previewTagsAndIconsAction(tags, icons)

                    frame.getContentPane().revalidate()
                    frame.getContentPane().repaint()
                }
            }
        })
        return table
    }

    private void previewTagsAndIconsAction(List<TagIcon> tags, Collection<NamedIcon> icons) {
        tagViewer.removeAll()
        if (tags.isEmpty() && icons.isEmpty()) {
            previewPane.remove(tagViewer)
        } else {
            previewPane.add(tagViewer, BorderLayout.NORTH)
            for (NamedIcon icon : icons) tagViewer.add(new JLabel(icon.getIcon()))
            for (TagIcon tag : tags) tagViewer.add(new JLabel(tag))
            tagViewer.revalidate()
            tagViewer.repaint()
        }
    }

    private void previewNodeAction(Node apiNode, NodeModel nodeModel) {
        contentsPreviewer.previewNode(apiNode, nodeModel, false)
        JScrollBar bar = previewScrollPane.getVerticalScrollBar()
        SwingUtilities.invokeLater({ bar.setValue(bar.getMinimum()) })
    }

    private void setActivateAndResetProgressBar(boolean shouldActivate) {
        progressBar.setValue(0)
        if (shouldActivate) {
            frame.getContentPane().remove(statusPane)
            frame.getContentPane().add(progressPane, BorderLayout.SOUTH)
        } else {
            frame.getContentPane().remove(progressPane)
            frame.getContentPane().add(statusPane, BorderLayout.SOUTH)
        }
        frame.revalidate()
        frame.repaint()
    }

    private void appendMatch(NodeModel match) {
        DefaultTableModel model = (DefaultTableModel) resultsTable.getModel()
        model.addRow([match])
    }

    private void displayAbout(Component comp) {
        MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()
        JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem(
            "<b>" + PLUGIN_NAME + "</b><br><i>version " + PLUGIN_VERSION + "</i><br><br>" +
            "Freeplane tool for searching across different map scopes, and quick inspection of results.<br>" +
            "Default folder: D:\\AJ\\OneDrive\\FP (saved). Font settings are saved.<br>" +
            "Search source: Folder (scan directory) or Open Maps (only currently opened maps).<br>" +
            "Hide button: hide window, restore by running script again.",
            "About " + PLUGIN_NAME)
        floatingPopup.add(contentsMenuItem)

        String visitWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Discussion & Updates:",
            "https://github.com/freeplane/freeplane/discussions/2344")
        JMenuItem visitWebsiteItem = createLinkMenuItem("https://github.com/freeplane/freeplane/discussions/2344", visitWebsite)
        floatingPopup.add(visitWebsiteItem)

        JMenuItem licenseMenuItem = MenuHelper.createContentNoticeMenuItem(
            PLUGIN_NAME + " " + PLUGIN_VERSION +
            " - Freeplane tool for searching across different map scopes, and quick inspection of results." +
            "<br><br>Copyright (C) 2025 bbarbosa<br>This program is free software...", "License")
        floatingPopup.add(licenseMenuItem)
        floatingPopup.show(comp, 0, 0)
    }

    // ----------------------------------------------------------------------
    // Inner classes (unchanged)
    // ----------------------------------------------------------------------
    static class PreviewPane extends ScrollablePanel {
        private JTextArea textAreaCore
        private JTextArea textAreaDetails
        private JTextArea textAreaNote
        private Color bgColor
        private boolean isLightLaf
        private Node currentNode
        private Consumer<Node> coreAction

        public PreviewPane(boolean isLightLaf, Color bgColor, Consumer<Node> coreAction) {
            super()
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))
            this.isLightLaf = isLightLaf
            this.bgColor = bgColor
            this.coreAction = coreAction
            this.setScrollableWidth(PreviewPane.ScrollableSizeHint.FIT)

            textAreaCore = new JTextArea()
            textAreaCore.setLineWrap(true)
            textAreaCore.setWrapStyleWord(true)
            textAreaCore.setMargin(new Insets(5, 5, 5, 5))
            textAreaCore.setEditable(false)
            Font originalFont = textAreaCore.getFont()
            textAreaCore.setFont(originalFont.deriveFont((float)(originalFont.getSize() * 1.3f)))
            Color textAreaBG = bgColor
            textAreaCore.setBackground(textAreaBG)
            textAreaCore.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20))
            textAreaCore.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    textAreaCore.setBackground(isLightLaf ? new Color(166, 217, 217) : bgColor.brighter().brighter())
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    textAreaCore.setBackground(textAreaBG)
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    coreAction.accept(currentNode)
                }
            })

            textAreaNote = new JTextArea()
            textAreaNote.setLineWrap(true)
            textAreaNote.setWrapStyleWord(true)
            textAreaNote.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20))
            textAreaNote.setBackground(textAreaBG)
            textAreaNote.setEditable(false)

            textAreaDetails = new JTextArea()
            textAreaDetails.setLineWrap(true)
            textAreaDetails.setBackground(textAreaBG)
            textAreaDetails.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 20))
            textAreaDetails.setMargin(new Insets(13, 13, 13, 13))
            textAreaDetails.setEditable(false)
            textAreaDetails.setFont(originalFont.deriveFont(Font.ITALIC))

            setBackground(textAreaBG)
        }

        void previewNode(Node node, NodeModel nodeModel, boolean addBeginningSeparator) {
            currentNode = node
            for (Component comp : getComponents()) {
                if (comp instanceof PanelSeparator) remove(comp)
            }
            Color pathColor = FreeplaneMapCrawler.PATH_COLORS.get(node.mindMap.getFile().toString())
            if (addBeginningSeparator) add(new PanelSeparator(pathColor))

            textAreaCore.setText(node.getPlainText())
            add(textAreaCore)

            String detailsTxt = node.getDetails() != null ? node.getDetails().getPlain().trim() : ""
            if (!detailsTxt.isEmpty()) {
                textAreaDetails.setText(detailsTxt)
                add(new PanelSeparator(pathColor))
                add(textAreaDetails)
            } else {
                remove(textAreaDetails)
            }

            String noteTxt = node.getNote() != null ? node.getNote().getPlain().trim() : ""
            if (!noteTxt.isEmpty()) {
                textAreaNote.setText(noteTxt)
                if (detailsTxt.isEmpty()) add(new PanelSeparator(pathColor))
                add(textAreaNote)
            } else {
                remove(textAreaNote)
            }
            revalidate()
            repaint()
        }
    }

    static class NodeCellWrapRenderer extends JTextArea implements TableCellRenderer {
        private int margin
        private Color bgColor
        private Color fgColor
        private Color selectedBgColor
        private Color selectedFgColor
        private static final Map<String, Border> BORDER_CACHE = new HashMap()
        private static final Function<String, Border> BORDER_FUNCTION = { key ->
            return getBorderForColor(FreeplaneMapCrawler.PATH_COLORS.get(key))
        }

        public NodeCellWrapRenderer(int margin, Color bgColor, Color selectedBgColor, Color fgColor, Color selectedFgColor) {
            setLineWrap(true)
            setWrapStyleWord(true)
            this.margin = margin
            this.bgColor = bgColor
            this.selectedBgColor = selectedBgColor
            this.selectedFgColor = selectedFgColor
            this.fgColor = fgColor
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            NodeModel nodeItem = (NodeModel) value[0]
            File mapFile = nodeItem.map.getFile()
            String mapName = mapFile.getName()
            Border border = BORDER_CACHE.computeIfAbsent(mapFile.toString(), BORDER_FUNCTION)
            Node apiNode = FreeplaneMapCrawler.LOADED_MAPS.get(mapFile.toString()).node(nodeItem.createID())
            String coreStr = apiNode.getPlainText().replaceAll("\\v+", " ")
            String shortCoreStr = org.freeplane.core.util.TextUtils.getShortText(coreStr, 50, "\u2026")
            setText((row + 1) + (row >= 9 ? "  " : "   ") + shortCoreStr + "\n" + "(" + mapName + ") ")
            setToolTipText(coreStr)

            setSize(table.getColumnModel().getColumn(column).getWidth(), table.getRowHeight(row))
            int preferredHeight = getPreferredSize().height + margin
            if (table.getRowHeight(row) != preferredHeight) {
                table.setRowHeight(row, preferredHeight)
            }

            setBorder(border)
            if (isSelected) {
                setBackground(selectedBgColor)
                setForeground(selectedFgColor)
            } else {
                setBackground(bgColor)
                setForeground(fgColor)
            }
            return this
        }

        private static Border getBorderForColor(Color color) {
            Border border = BorderFactory.createMatteBorder(0, 5, 0, 0, color)
            return BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(0, 10, 0, 10))
        }
    }

    static class ScrollablePanel extends JPanel implements Scrollable, SwingConstants {
        public enum ScrollableSizeHint { NONE, FIT, STRETCH }
        public enum IncrementType { PERCENT, PIXELS }

        private ScrollableSizeHint scrollableHeight = ScrollableSizeHint.NONE
        private ScrollableSizeHint scrollableWidth = ScrollableSizeHint.NONE
        private IncrementInfo horizontalBlock, horizontalUnit, verticalBlock, verticalUnit

        public ScrollablePanel() { this(new FlowLayout()) }
        public ScrollablePanel(LayoutManager layout) {
            super(layout)
            IncrementInfo block = new IncrementInfo(IncrementType.PERCENT, 100)
            IncrementInfo unit = new IncrementInfo(IncrementType.PERCENT, 10)
            setScrollableBlockIncrement(HORIZONTAL, block)
            setScrollableBlockIncrement(VERTICAL, block)
            setScrollableUnitIncrement(HORIZONTAL, unit)
            setScrollableUnitIncrement(VERTICAL, unit)
        }

        public void setScrollableHeight(ScrollableSizeHint hint) { scrollableHeight = hint; revalidate() }
        public void setScrollableWidth(ScrollableSizeHint hint) { scrollableWidth = hint; revalidate() }
        public void setScrollableBlockIncrement(int orientation, IncrementType type, int amount) {
            setScrollableBlockIncrement(orientation, new IncrementInfo(type, amount))
        }
        public void setScrollableBlockIncrement(int orientation, IncrementInfo info) {
            if (orientation == HORIZONTAL) horizontalBlock = info
            else if (orientation == VERTICAL) verticalBlock = info
            else throw new IllegalArgumentException("Invalid orientation")
        }
        public void setScrollableUnitIncrement(int orientation, IncrementType type, int amount) {
            setScrollableUnitIncrement(orientation, new IncrementInfo(type, amount))
        }
        public void setScrollableUnitIncrement(int orientation, IncrementInfo info) {
            if (orientation == HORIZONTAL) horizontalUnit = info
            else if (orientation == VERTICAL) verticalUnit = info
            else throw new IllegalArgumentException("Invalid orientation")
        }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return getScrollableIncrement(orientation == HORIZONTAL ? horizontalUnit : verticalUnit,
                orientation == HORIZONTAL ? visible.width : visible.height)
        }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return getScrollableIncrement(orientation == HORIZONTAL ? horizontalBlock : verticalBlock,
                orientation == HORIZONTAL ? visible.width : visible.height)
        }
        protected int getScrollableIncrement(IncrementInfo info, int distance) {
            if (info.getIncrement() == IncrementType.PIXELS) return info.getAmount()
            else return distance * info.getAmount() / 100
        }
        public boolean getScrollableTracksViewportWidth() {
            if (scrollableWidth == ScrollableSizeHint.NONE) return false
            if (scrollableWidth == ScrollableSizeHint.FIT) return true
            if (getParent() instanceof JViewport) return ((JViewport) getParent()).getWidth() > getPreferredSize().width
            return false
        }
        public boolean getScrollableTracksViewportHeight() {
            if (scrollableHeight == ScrollableSizeHint.NONE) return false
            if (scrollableHeight == ScrollableSizeHint.FIT) return true
            if (getParent() instanceof JViewport) return ((JViewport) getParent()).getHeight() > getPreferredSize().height
            return false
        }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize() }
        static class IncrementInfo {
            private IncrementType type; private int amount
            public IncrementInfo(IncrementType type, int amount) { this.type = type; this.amount = amount }
            public IncrementType getIncrement() { return type }
            public int getAmount() { return amount }
        }
    }

    static class WrapLayout extends FlowLayout {
        public WrapLayout() { super() }
        public WrapLayout(int align) { super(align) }
        public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap) }
        @Override
        public Dimension preferredLayoutSize(Container target) { return layoutSize(target, true) }
        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = layoutSize(target, false)
            minimum.width -= (getHgap() + 1)
            return minimum
        }
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width
                Container container = target
                while (container.getSize().width == 0 && container.getParent() != null) container = container.getParent()
                targetWidth = container.getSize().width
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE
                int hgap = getHgap(), vgap = getVgap()
                Insets insets = target.getInsets()
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2)
                int maxWidth = targetWidth - horizontalInsetsAndGap
                Dimension dim = new Dimension(0, 0)
                int rowWidth = 0, rowHeight = 0
                int nmembers = target.getComponentCount()
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i)
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize()
                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight)
                            rowWidth = 0
                            rowHeight = 0
                        }
                        if (rowWidth != 0) rowWidth += hgap
                        rowWidth += d.width
                        rowHeight = Math.max(rowHeight, d.height)
                    }
                }
                addRow(dim, rowWidth, rowHeight)
                dim.width += horizontalInsetsAndGap
                dim.height += insets.top + insets.bottom + vgap * 2
                Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target)
                if (scrollPane != null && target.isValid()) dim.width -= (hgap + 1)
                return dim
            }
        }
        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth)
            if (dim.height > 0) dim.height += getVgap()
            dim.height += rowHeight
        }
    }

    static class BreadcrumbDetails {
        String text; String tooltip; Runnable runnable
        BreadcrumbDetails(String text, String tooltip, Runnable runnable) { this.text = text; this.tooltip = tooltip; this.runnable = runnable }
    }

    static class BreadcrumbList extends JPanel {
        private static Container makeContainer(int overlap) {
            JPanel p = new JPanel(new WrapLayout(FlowLayout.LEADING, -overlap, 5)) {
                @Override public boolean isOptimizedDrawingEnabled() { return false }
            }
            p.setBorder(BorderFactory.createEmptyBorder(4, overlap + 4, 4, 4))
            p.setOpaque(false)
            return p
        }
        private static Component makeBreadcrumbListWithToolTip(List<BreadcrumbDetails> list, Color hoverColor) {
            Container p = makeContainer(10 + 1)
            ButtonGroup bg = new ButtonGroup()
            list.forEach({ listItem ->
                AbstractButton b = makeButton(null, new TreePath(listItem.text), hoverColor)
                b.setToolTipText(listItem.tooltip)
                b.addActionListener({ l -> listItem.runnable.run() })
                p.add(b)
                bg.add(b)
            })
            return p
        }
        private static AbstractButton makeButton(JTree tree, TreePath path, Color color) {
            AbstractButton b = new JRadioButton(path.getLastPathComponent().toString()) {
                @Override public boolean contains(int x, int y) {
                    return Optional.ofNullable(getIcon()).filter({ it -> ArrowToggleButtonBarCellIcon.class.isInstance(it) })
                        .map({ i -> ((ArrowToggleButtonBarCellIcon) i).getShape() })
                        .map({ s -> ((Shape) s).contains(x, y) })
                        .orElseGet({ super.contains(x, y) })
                }
            }
            if (tree != null) {
                b.addActionListener({ e ->
                    JRadioButton r = (JRadioButton) e.getSource()
                    tree.setSelectionPath(path)
                    r.setSelected(true)
                })
            }
            b.setIcon(new ArrowToggleButtonBarCellIcon())
            b.setContentAreaFilled(false)
            b.setBorder(BorderFactory.createEmptyBorder())
            b.setVerticalAlignment(SwingConstants.CENTER)
            b.setVerticalTextPosition(SwingConstants.CENTER)
            b.setHorizontalAlignment(SwingConstants.CENTER)
            b.setHorizontalTextPosition(SwingConstants.CENTER)
            b.setFocusPainted(false)
            b.setOpaque(false)
            b.setBackground(color)
            return b
        }
    }

    static class ArrowToggleButtonBarCellIcon implements Icon {
        public static final int TH = 10
        private static final int HEIGHT = TH * 2 + 1
        private static final int WIDTH = 100
        private Shape shape
        public Shape getShape() { return shape }
        protected Shape makeShape(Container parent, Component c, int x, int y) {
            int w = c.getWidth() - 1, h = c.getHeight() - 1
            double h2 = Math.round(h * 0.5), w2 = TH
            Path2D p = new Path2D.Double()
            p.moveTo(0d, 0d)
            p.lineTo(w - w2, 0d)
            p.lineTo(w, h2)
            p.lineTo(w - w2, h)
            p.lineTo(0d, h)
            if (!Objects.equals(c, parent.getComponent(0))) p.lineTo(w2, h2)
            p.closePath()
            return AffineTransform.getTranslateInstance(x, y).createTransformedShape(p)
        }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Container parent = c.getParent()
            if (parent == null) return
            shape = makeShape(parent, c, x, y)
            Color bgc = parent.getBackground()
            Color borderColor = Color.GRAY.brighter()
            if (c instanceof AbstractButton) {
                ButtonModel m = ((AbstractButton) c).getModel()
                if (m.isSelected() || m.isRollover()) {
                    bgc = c.getBackground()
                    borderColor = Color.GRAY
                }
            }
            Graphics2D g2 = (Graphics2D) g.create()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setPaint(bgc)
            g2.fill(shape)
            g2.setPaint(borderColor)
            g2.draw(shape)
            g2.dispose()
        }
        @Override public int getIconWidth() { return WIDTH }
        @Override public int getIconHeight() { return HEIGHT }
    }

    static class PanelSeparator extends JPanel {
        public PanelSeparator(Color color) {
            Border border = BorderFactory.createMatteBorder(3, 0, 0, 0, color)
            setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(3, 0, 0, 0)))
            setBackground(new Color(0, 0, 0, 2))
            Dimension size = new Dimension(120, 6)
            setSize(size)
            setPreferredSize(size)
            setMaximumSize(size)
        }
    }

    static class MenuHelper {
        public static class FloatingMsgPopup extends JPopupMenu {}
        public static JMenuItem createHeaderNoticeMenuItem(String message, String title) {
            message = "<html><body width='600px' style='font-size:12px'>[x CLOSE]&nbsp;&nbsp;&nbsp;&nbsp;" + title + "<br><br>" + message + "<br><br></body></html>"
            return new JMenuItem(message)
        }
        public static JMenuItem createContentNoticeMenuItem(String message, String title) {
            message = "<html><body width='600px' style='font-size:12px'><br>" + title + "<br><br>" + message + "<br><br></body></html>"
            return new JMenuItem(message)
        }
        public static String floatingMenuItemUnderlinedActionHTML(String legend, String actionDisplayTitle) {
            return String.format("<html><body width='600px' style='font-size:12px'>%s<br> <a href=\"%s\">%s</a></body></html>", legend, actionDisplayTitle, actionDisplayTitle)
        }
    }

    // Static helper methods
    public static List<Path> findByFileExtension(Path path, String fileExtension) throws IOException {
        if (!Files.isDirectory(path) || path.toString().trim().isEmpty())
            throw new IllegalArgumentException("Invalid path: it either does not exist or is not a directory.")
        if (!Files.isReadable(path))
            throw new IllegalArgumentException("Provided directory must have read permissions")
        List<Path> result = new ArrayList()
        Files.walk(path).iterator().forEachRemaining({ p ->
            try {
                if (p.toString().endsWith(fileExtension) && !p.getFileName().toString().startsWith("."))
                    result.add(p)
            } catch (Exception e) { System.err.print(e.getLocalizedMessage()) }
        })
        return result
    }

    private static JMenuItem createLinkMenuItem(String url, String menuText) {
        return new JMenuItem(menuText) {
            {
                addActionListener({ e ->
                    try {
                        Desktop.getDesktop().browse(new URI(url))
                    } catch (Exception ex) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null)
                        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                            "Your configuration currently does not allow browsing. The URL has been copied to clipboard:\n" + url,
                            "", JOptionPane.INFORMATION_MESSAGE)
                    }
                })
            }
        }
    }

    public static MIconController iconController() {
        return (MIconController) getModeController().getExtension(IconController.class)
    }
    public static MModeController getModeController() {
        return MModeController.getMModeController()
    }
    public static NodeModel getNodeModel(Node node) {
        return ((MapProxy) node.getMindMap()).getDelegate().getNodeForID(node.getId())
    }
    public static MindMap getMindMap(File file) {
        if (file == null) throw new IllegalArgumentException("Null file")
        try {
            return ScriptUtils.c().mapLoader(file).getMindMap()
        } catch (Exception e) {
            throw new RuntimeException("Error loading mindmap from file: " + file.toString(), e)
        }
    }
    private static Color determineStringColor(String str) {
        CRC32 crc = new CRC32()
        crc.update(str.getBytes(StandardCharsets.UTF_8))
        return HSLColorConverter.generateColorFromLong(crc.getValue())
    }

    private static void goToNode(NodeModel nodeModel) {
        String path = nodeModel.getMap().getFile().toString()
        String uriStr = nodeModel.getMap().getFile().toURI().toString() + "#" + nodeModel.getID()
        try {
            Node node = FreeplaneMapCrawler.LOADED_MAPS.get(path).node(nodeModel.createID())
            URI uri = new URI(uriStr)
            Hyperlink link = new Hyperlink(uri)
            UrlManager.getController().loadHyperlink(link)
            ScriptUtils.c().select(node)
        } catch (Exception e1) {
            UITools.errorMessage("It was not possible to go to node at " + uriStr + "\n\n" + e1.getMessage())
            e1.printStackTrace()
        }
    }
    public static List<MapView> refreshMindMapReferences(Map<String, MindMap> loadedMaps, Map<String, MindMap> loadedMapsModificationDate) {
        for (String mapPath : loadedMaps.keySet()) {
            boolean isVisible = getNodeModel(loadedMaps.get(mapPath).getRoot()).hasViewers()
            long modifDate = loadedMaps.get(mapPath).getFile().lastModified()
            boolean modificationDateChanged = !loadedMapsModificationDate.get(mapPath).equals(modifDate)
            loadedMaps.compute(mapPath, { k, v -> (isVisible || modificationDateChanged) ? loadedMaps.get(k).getRoot().getMindMap() : loadedMaps.get(k) })
            loadedMapsModificationDate.compute(mapPath, { k, v -> modificationDateChanged ? modifDate : loadedMapsModificationDate.get(k) })
            if (isVisible && isDev) ScriptUtils.c().setStatusInfo("modification changed: " + modificationDateChanged)
        }
    }
    private static boolean isLightLaF() { return UITools.isLightLookAndFeelInstalled() }
}
