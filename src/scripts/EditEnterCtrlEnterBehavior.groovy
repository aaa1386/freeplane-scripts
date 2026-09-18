//@ExecutionModes({ON_SINGLE_NODE="/main_menu/aaa"})
/*This script changes the behavior of the Enter and Ctrl+Enter keys in the Core editor of Freeplane:
Related to the request [Suggestion to add an option for changing the behavior of the Enter key when editing node text · Issue #2893 · freeplane/freeplane](https://github.com/freeplane/freeplane/issues/2893)

Enter → create a new line
Shift+Enter → create a new line, as before
Ctrl+Enter → execute the original logic for exiting edit mode

Grant the required script permissions under Preferences → Plugins → Scripting.
*/
import javax.swing.*
import java.awt.event.*
import java.util.WeakHashMap

final String EDITOR_CLASS =
    "org.freeplane.view.swing.map.mindmapmode.EditNodeTextField"

final String LISTENER_CLASS =
    "org.freeplane.view.swing.map.mindmapmode.EditNodeTextField\$TextFieldListener"

final String CONTROLLER_CLASS =
    "org.freeplane.features.text.mindmapmode.MTextController"

def startCoreEnterHandler = {

    try {

        def controllerClass =
            Class.forName(CONTROLLER_CLASS)

        def controller =
            controllerClass
                .getDeclaredMethod("getController")
                .invoke(null)

        if (controller == null)
            return

        def currentEditorField =
            controllerClass.getDeclaredField(
                "currentBlockingEditor"
            )

        currentEditorField.setAccessible(true)

        def installed =
            new WeakHashMap<Object, Boolean>()

        def installCoreHandler = { Object editField ->

            if (editField == null)
                return

            if (!Class.forName(EDITOR_CLASS)
                    .isInstance(editField))
                return

            if (installed.containsKey(editField))
                return

            try {

                def textFieldField =
                    editField.class.getDeclaredField(
                        "textfield"
                    )

                textFieldField.setAccessible(true)

                JEditorPane editor =
                    textFieldField.get(editField)
                    as JEditorPane

                if (editor == null)
                    return

                KeyListener original = null

                for (KeyListener kl :
                        editor.getKeyListeners()) {

                    if (kl.class.name ==
                        LISTENER_CLASS) {

                        original = kl
                        break
                    }
                }

                if (original == null)
                    return

                editor.removeKeyListener(original)

                KeyListener bridge =
                    new KeyAdapter() {

                    @Override
                    void keyPressed(KeyEvent e) {

                        if (e.getKeyCode() ==
                            KeyEvent.VK_ENTER) {

                            /*
                             * Ctrl+Enter:
                             * اجرای دقیق منطق Enter اصلی Freeplane
                             */
                            if (e.isControlDown() ||
                                e.isMetaDown()) {

                                KeyEvent originalEnter =
                                    new KeyEvent(
                                        editor,
                                        KeyEvent.KEY_PRESSED,
                                        e.getWhen(),
                                        0,
                                        KeyEvent.VK_ENTER,
                                        KeyEvent.CHAR_UNDEFINED
                                    )

                                original.keyPressed(
                                    originalEnter
                                )

                                e.consume()
                                return
                            }

                            /*
                             * Enter:
                             * همان insert-break اصلی
                             */
                            Action action =
                                editor
                                    .getActionMap()
                                    .get("insert-break")

                            if (action != null) {

                                action.actionPerformed(
                                    new ActionEvent(
                                        editor,
                                        ActionEvent.ACTION_PERFORMED,
                                        "insert-break"
                                    )
                                )

                                e.consume()
                            }

                            return
                        }

                        original.keyPressed(e)
                    }

                    @Override
                    void keyTyped(KeyEvent e) {
                        original.keyTyped(e)
                    }

                    @Override
                    void keyReleased(KeyEvent e) {
                        original.keyReleased(e)
                    }
                }

                editor.addKeyListener(bridge)

                installed.put(
                    editField,
                    true
                )

                println(
                    "[CORE-ENTER] Installed."
                )

            } catch (Throwable ex) {

                println(
                    "[CORE-ENTER] Install error: " +
                    ex
                )
            }
        }

        Timer timer =
            new Timer(
                150,
                {
                    try {

                        def editField =
                            currentEditorField
                                .get(controller)

                        if (editField != null)
                            installCoreHandler(
                                editField
                            )

                    } catch (Throwable ignored) {
                    }
                } as ActionListener
            )

        timer.setRepeats(true)
        timer.start()

        println(
            "[CORE-ENTER] Startup handler active."
        )

    } catch (Throwable ex) {

        println(
            "[CORE-ENTER] Startup error: " +
            ex
        )
    }
}

SwingUtilities.invokeLater {

    SwingUtilities.invokeLater {

        startCoreEnterHandler()

    }
}