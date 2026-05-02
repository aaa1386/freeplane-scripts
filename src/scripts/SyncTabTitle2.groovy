// @ExecutionModes({ON_SINGLE_NODE="/main_menu/aaa1386"})
// rename_tab_no_repeat.groovy
//🧩↗️ ♀️♻️✳️🪴آیکن های رنگی
// تغییر داده شده: اگر تب آیکون سبز دارد و متن ریشه با تب مطابق نیست، تب هم‌سان با ریشه می‌شود.

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
def hasPink = currentTitle.startsWith("♀️")

def desired = null

// تغییر اصلی: اگر آیکون سبز دارد و متن تب با متن ریشه متفاوت است، همسان کن
if (hasStar) {
    if (plainNodeText != plainCurrentTitle) {
        desired = "✳️" + plainNodeText
    } else {
        desired = null // بدون تغییر
    }
} else if (hasPink) {
    desired = "♀️" + plainNodeText
} else {
    // منطق قبلی برای حالت بدون آیکون
    if (plainCurrentTitle == plainMapTitle) {
        if (plainCurrentTitle == plainNodeText) {
            if (hasStar) {
                desired = null
            } else {
                desired = "✳️" + plainMapTitle
            }
        } else {
            if (hasStar) {
                desired = "✳️" + plainMapTitle
            } else {
                desired = "♀️" + plainNodeText
            }
        }
    } else {
        desired = "♀️" + plainNodeText
    }
}

if (desired != null && currentTitle != desired) {
    mapView.setName(desired)
    forceUIRefresh(mapView, desired)
}

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
        // خطا نادیده گرفته شود
    }
}
