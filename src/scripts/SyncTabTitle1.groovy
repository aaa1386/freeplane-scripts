// @ExecutionModes({ON_SINGLE_NODE="/main_menu/aaa1386"})
// rename_tab_no_repeat.groovy
//////🧩↗️ ♀️♻️✳️🪴آیکن های رنگی

import org.freeplane.features.mode.Controller
import javax.swing.*

def c = Controller.getCurrentController()
def map = c.getMap()
def mapView = c.getMapViewManager().getMapViewComponent()
if (!mapView) {
    return
}
def rootNodeView = mapView.getRoot()
if (!rootNodeView) {
    return
}
def nodeText = rootNodeView.getNode().getText()?.trim()
if (!nodeText) {
    return
}

def currentTitle = mapView.getName() ?: ""
def mapTitle = map?.getTitle()?.trim() ?: ""

// ----- Helper: get the expected title without any icon -----
def plainMapTitle = mapTitle.replaceAll("^[✳️♀️]*", "").trim()
def plainNodeText = nodeText.replaceAll("^[✳️♀️]*", "").trim()

// ----- Determine what the title SHOULD be -----
def isMainTab = (plainMapTitle == plainNodeText)  // tab with root = map root
if (isMainTab) {
    // Main map tab → should have ✳️ + plainMapTitle
    def desired = "✳️${plainMapTitle}"
    if (currentTitle != desired) {
        mapView.setName(desired)
        forceUIRefresh(mapView, desired)
    }
} else {
    // New view tab → should have ♀️ + nodeText (plain)
    def desired = "♀️${plainNodeText}"
    if (currentTitle != desired) {
        mapView.setName(desired)
        forceUIRefresh(mapView, desired)
    }
}

// ----- Function to force UI refresh (your working code) -----
def forceUIRefresh(mapView, newTitle) {
    try {
        def viewClass = Class.forName("net.infonode.docking.View")
        def dockingView = SwingUtilities.getAncestorOfClass(viewClass, mapView)
        if (dockingView) {
            dockingView.putClientProperty("customizedTabName", newTitle)
            def winProps = dockingView.getWindowProperties()
            def provider = winProps.getTitleProvider()
            if (provider) {
                winProps.setTitleProvider(provider)
            } else {
                def simpleClass = Class.forName("net.infonode.docking.title.SimpleDockingWindowTitleProvider")
                def simple = simpleClass.getField("INSTANCE").get(null)
                winProps.setTitleProvider(simple)
            }
            dockingView.repaint()
            if (dockingView.parent) dockingView.parent.repaint()
        }
    } catch (Exception e) {
        // silent fail
    }
}
