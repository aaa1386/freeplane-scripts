# SyncTabTitle2.groovy
نسخهٔ ۲ (پیشرفته) این اسکریپت با تشخیص خودکار نقشهٔ اصلی از نماهای فرعی، آیکن‌های زیر را اختصاص می‌دهد:

- **آیکن سبز (✳️)** برای تب‌های مربوط به نقشهٔ کامل (فایل اصلی)
- **آیکن قرمز (♀️)** برای نماهای فرعی (زیرنقشه‌ها)

ویژگی‌های کلیدی:

- آیکن‌ها (سبز/قرمز) همواره ثابت می‌مانند و تغییری نمی‌کنند.
- نام فایل نقشهٔ اصلی هرگز تغییر نمی‌کند.
- در تب‌های زیرنقشه (با آیکن قرمز)، عنوان تب با تغییر ریشه (Root) به‌روز می‌شود.

در نتیجه، پنل تب‌ها همیشه شامل دو دسته است:

1. **تب‌های نقشهٔ کامل** (✳️ سبز): نام ثابت، نمایانگر فایل‌های اصلی.
2. **تب‌های زیرنقشه** (♀️ قرمز): عنوان پویا، مناسب برای نمایش گره‌ها به‌صورت ریشه.



### کاربرد عملی

- می‌توانید چند فایل `.mm` را همزمان باز کنید؛ هر تب آیکن سبز می‌گیرد.
- اگر بخواهید در همان تب نقشهٔ اصلی متمرکز بمانید و تب جدیدی ایجاد نشود، از فرمان **«Jump in»** استفاده کنید. در این حالت آیکن سبز و نام فایل ثابت می‌مانند – بنابراین متوجه می‌شوید که آن تب منحصراً به یک فایل تعلق دارد.
- اگر گره‌ای را در **نمای جدید (New View)** باز کنید، تب جدید آیکن قرمز می‌گیرد؛ این نشان می‌دهد که آن تب برای نمایش گره‌ها به‌صورت ریشه در نظر گرفته شده است.

### نکتهٔ بسیار مهم :

توص
یه می‌شود تب‌های دارای آیکن قرمز را بلافاصله پس از تب نقشهٔ اصلی قرار دهید تا فایل‌های مختلف و زیرنقشه‌های هرکدام از یکدیگر تفکیک شوند

How to run
In Freeplane, assign the F7 shortcut key to the script # SyncTabTitle1.groovy.
Place the following script named "Periodic F7 Execution" (with .groovy extension) in the following folder:
C:\Users<username>\AppData\Roaming\Freeplane\1.12.x\scripts\init
`
import java.awt.Robot
import java.awt.event.KeyEvent
import org.freeplane.features.mode.Controller
import javax.swing.SwingUtilities

def mapViewComponent = Controller.getCurrentController().getMapViewManager().getMapViewComponent()
def freeplaneFrame = SwingUtilities.getWindowAncestor(mapViewComponent)

Thread.start {
    try {
        def robot = new Robot()
        while (true) {
            if (freeplaneFrame.isActive()) {
                robot.keyPress(KeyEvent.VK_F7)
                robot.keyRelease(KeyEvent.VK_F7)
            }
            Thread.sleep(4000)
        }
    } catch (Exception e) {
        e.printStackTrace()
    }
}
`

Note: If you want the # SyncTabTitle1.groovy script to run automatically and periodically, you must place the "Periodic F7 Execution" script inside the init folder. Otherwise, pressing F7 will run the script once, and the tab name will update only when you switch to another tab.

For the script to work correctly, after opening Freeplane for the first time, close all maps and then start..

 این نالگوبر.
# SyncTabTitle1.groovy

تب ها مربوط به نقشه اصلی یا مربوط به زیر نقشه کاملا بروز می شود از جهت آیکن و از جهت نام
Version 1 (General & Simple) – Script Description
This script provides a straightforward way to visually distinguish the main map tab from secondary view tabs.
It makes the tab title exactly match the root node’s text and distinguishes between the full map and sub‑map views using green and pink icons.

It assigns the following icons:

Green icon (✳️) – for the main map tab (full map)
Red icon (♀️) – for any node opened as a root (new view)
Practical usage
Open several .mm files at the same time – each main tab will get a green icon.
Open a node in a New View – the new tab will get a red icon, and its title will change to the root node’s text.

How to run
In Freeplane, assign the F7 shortcut key to the script # SyncTabTitle1.groovy.
Place the following script named "Periodic F7 Execution" (with .groovy extension) in the following folder:
C:\Users<username>\AppData\Roaming\Freeplane\1.12.x\scripts\init
import java.awt.Robot
import java.awt.event.KeyEvent
import org.freeplane.features.mode.Controller
import javax.swing.SwingUtilities

def mapViewComponent = Controller.getCurrentController().getMapViewManager().getMapViewComponent()
def freeplaneFrame = SwingUtilities.getWindowAncestor(mapViewComponent)

Thread.start {
    try {
        def robot = new Robot()
        while (true) {
            if (freeplaneFrame.isActive()) {
                robot.keyPress(KeyEvent.VK_F7)
                robot.keyRelease(KeyEvent.VK_F7)
            }
            Thread.sleep(4000)
        }
    } catch (Exception e) {
        e.printStackTrace()
    }
}
Note: If you want the # SyncTabTitle1.groovy script to run automatically and periodically, you must place the "Periodic F7 Execution" script inside the init folder. Otherwise, pressing F7 will run the script once, and the tab name will update only when you switch to another tab.

For the script to work correctly, after opening Freeplane for the first time, close all maps and then start.
