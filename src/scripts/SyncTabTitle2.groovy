// @ExecutionModes({ON_SINGLE_NODE="/main_menu/aaa1386"})
// rename_tab_no_repeat.groovy
//////🧩↗️ ♀️♻️✳️🪴آیکن های رنگی

import org.freeplane.features.mode.Controller
import javax.swing.*

def c = Controller.getCurrentController()
def map = c.getMap()
def mapView = c.getMapViewManager().getMapViewComponent()
if (!mapView) return
def rootNodeView = mapView.getRoot()
if (!rootNodeView) return
def nodeText = rootNodeView.getNode().getText()?.trim()
if (!nodeText) return

def currentTitle = mapView.getName() ?: ""
def rawMapTitle = map.getTitle()?.trim() ?: ""

def plainCurrentTitle = currentTitle.replaceAll("^[✳️♀️]*", "").trim()
def plainMapTitle = rawMapTitle.replaceAll("^[✳️♀️]*", "").trim()
def plainNodeText = nodeText.replaceAll("^[✳️♀️]*", "").trim()

def hasStar = currentTitle.startsWith("✳️")
def hasPink = currentTitle.startsWith("♀️")   // آیکن صورتی (مخالف آیکن سبز)

def desired = null

// ردیف 0: اگر آیکن صورتی داشت، اصلاً نقشه اصلی نیست
if (hasPink) {
    desired = "♀️" + plainNodeText
} else {
    if (plainCurrentTitle == plainMapTitle) {
        // T == M
        if (plainCurrentTitle == plainNodeText) {
            // T == M == R
            if (hasStar) {
                // ردیف 2: هیچ دستکاری نمی‌شود
                desired = null
            } else {
                // ردیف 1: نقشه اصلی و کامل
                desired = "✳️" + plainMapTitle
            }
        } else {
            // T == M != R
            if (hasStar) {
                // ردیف 3: نقشه اصلی است (همان فعلی)
                desired = "✳️" + plainMapTitle
            } else {
                // ردیف 4: نقشه اصلی نیست
                desired = "♀️" + plainNodeText
            }
        }
    } else {
        // ردیف 5: T != M
        desired = "♀️" + plainNodeText
    }
}

// اعمال تغییر فقط در صورتی که desired تعریف شده و با عنوان فعلی متفاوت باشد
if (desired != null && currentTitle != desired) {
    mapView.setName(desired)
    forceUIRefresh(mapView, desired)
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
