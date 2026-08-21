// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/aaa"})
/*
این اسکریتپ کارت ها را برای افزونه زیرآماده تنظیم و مدیریت می کند https://github.com/aaa1386/anki-freeplane-pro

:برخی از قابلیت ها

آپدیت کارت‌ها هنگام تغییر یا حذف anki:deckbranch با سرشاخه های کارت دیگر
ایجاد خودکار کارت بدون پر کردن فیلد، تنها با زدن OK
افزودن خودکار آیکن ANKI به کارت‌ها - می توان در فایل آن را تغییر داد به برچسب Card
افزودن تگ "Deckbranch" به کارت‌های سر شاخه
گزینه حذف کارت :حذف کارت و همه فیلدهایش با پاک کردن "Card" و آیکن ANKI
حذف خودکار فیلدهایی که پر نشده‌اند -بررسی اجداد برای تعیین نام دسته مناسب با سرشاخه
*/
import javax.swing.*
import java.awt.*
import org.freeplane.api.Node
import java.util.LinkedList

Node cur = node
if (cur == null) return

// ==== Safe field reading ====
String curDeck       = cur['anki:deck'] ?: ""
String curDeckBranch = cur['anki:deckbranch'] ?: ""
String curBackLevels = cur['BackLevels'] ?: ""
String oldDeckBranch = curDeckBranch

// ==== Check tags/icons ====
boolean hasCardTag = false
boolean hasDeckBranchTag = false
boolean hasAnkiIcon = false
try { hasCardTag = cur.getTags().contains("Card") } catch(ignored) {}
try { hasDeckBranchTag = cur.getTags().contains("Deckbranch") } catch(ignored) {}
try { hasAnkiIcon = cur.getIcons().contains("ANKI") } catch(ignored) {}

boolean hasModelField = false
try { hasModelField = cur['anki:model'] != null && cur['anki:model'].trim().length() > 0 } catch(ignored) {}

boolean hasDeckField = false
try { hasDeckField = cur['anki:deck'] != null && cur['anki:deck'].trim().length() > 0 } catch(ignored) {}

boolean hasDeckBranchField = false
try { hasDeckBranchField = cur['anki:deckbranch'] != null && cur['anki:deckbranch'].trim().length() > 0 } catch(ignored) {}

boolean isCard = hasCardTag || hasDeckBranchTag || hasAnkiIcon || hasModelField || hasDeckField || hasDeckBranchField
boolean canDeleteCard = isCard

// ==== Saved option ====
String savedOption = System.getProperty("freeplane.anki.addoption.default", "Both")

// ==== Panel ====
JPanel panel = new JPanel(new GridBagLayout())
GridBagConstraints gc = new GridBagConstraints()
gc.insets = new Insets(4, 6, 4, 6)
gc.fill = GridBagConstraints.HORIZONTAL
gc.gridy = 0

JTextField tfDeck       = new JTextField(curDeck ?: "", 28)
JTextField tfDeckBranch = new JTextField(curDeckBranch ?: "", 28)
JTextField tfBackLevels = new JTextField(curBackLevels ?: "", 28)

def addRow = { String label, JTextField field ->
    gc.gridx = 0; gc.weightx = 0.0
    panel.add(new JLabel(label), gc)
    gc.gridx = 1; gc.weightx = 1.0
    panel.add(field, gc)
    gc.gridx = 2; gc.weightx = 0.0
    JButton btnClear = new JButton("×")
    btnClear.addActionListener({ e -> field.setText("") } as java.awt.event.ActionListener)
    panel.add(btnClear, gc)
    gc.gridy++
}

addRow("anki:deck", tfDeck)
addRow("anki:deckbranch", tfDeckBranch)
addRow("BackLevels", tfBackLevels)

// ==== Radio buttons ====
JRadioButton rbAddAnkiIcon = new JRadioButton("Add ANKI icon")
JRadioButton rbAddCardTag = new JRadioButton("Add Card tag")
JRadioButton rbAddBoth = new JRadioButton("Add Both")
JRadioButton rbNone = new JRadioButton("None")

ButtonGroup group = new ButtonGroup()
group.add(rbAddAnkiIcon)
group.add(rbAddCardTag)
group.add(rbAddBoth)
group.add(rbNone)

if (hasAnkiIcon && !hasCardTag) {
    rbAddAnkiIcon.selected = true
} else if (!hasAnkiIcon && hasCardTag) {
    rbAddCardTag.selected = true
} else if (hasAnkiIcon && hasCardTag) {
    rbAddBoth.selected = true
} else {
    rbAddAnkiIcon.selected = true
}

gc.gridx = 0; gc.weightx = 1.0; gc.gridwidth = 3
panel.add(rbAddAnkiIcon, gc); gc.gridy++
panel.add(rbAddCardTag, gc); gc.gridy++
panel.add(rbAddBoth, gc); gc.gridy++
panel.add(rbNone, gc); gc.gridy++
gc.gridwidth = 1

// ==== Auto Create button ====
JButton btnAutoCreate = new JButton("Auto Create Card")
btnAutoCreate.enabled = (tfDeck.text.trim().isEmpty() && tfDeckBranch.text.trim().isEmpty() && tfBackLevels.text.trim().isEmpty())

def checkAutoCreateEnabled = {
    boolean allEmpty = tfDeck.text.trim().isEmpty() &&
                       tfDeckBranch.text.trim().isEmpty() &&
                       tfBackLevels.text.trim().isEmpty()
    btnAutoCreate.enabled = allEmpty
}

def docListener = [
    insertUpdate: { e -> checkAutoCreateEnabled() },
    removeUpdate: { e -> checkAutoCreateEnabled() },
    changedUpdate: { e -> checkAutoCreateEnabled() }
] as javax.swing.event.DocumentListener

tfDeck.document.addDocumentListener(docListener)
tfDeckBranch.document.addDocumentListener(docListener)
tfBackLevels.document.addDocumentListener(docListener)

// Action: Auto Create = same as pressing OK with empty fields
btnAutoCreate.addActionListener({ e ->
    String selectedOptionAuto = "Both"
    if (rbAddAnkiIcon.isSelected()) selectedOptionAuto = "AnkiIcon"
    else if (rbAddCardTag.isSelected()) selectedOptionAuto = "CardTag"
    else if (rbAddBoth.isSelected()) selectedOptionAuto = "Both"
    else selectedOptionAuto = "None"

    System.setProperty("freeplane.anki.addoption.default", selectedOptionAuto)
    applyOptionToNode(cur, selectedOptionAuto, "", "") // ⚡ valDeck و valDeckBranch خالی
    SwingUtilities.getWindowAncestor(panel).dispose()
} as java.awt.event.ActionListener)

gc.gridx = 0; gc.gridwidth = 3
panel.add(btnAutoCreate, gc); gc.gridy++
gc.gridwidth = 1

// ==== Delete Card button ====
JButton btnRemoveCard = new JButton("Delete Card")
btnRemoveCard.enabled = canDeleteCard
btnRemoveCard.foreground = canDeleteCard ? Color.RED : Color.GRAY

btnRemoveCard.addActionListener({ e ->
    if (canDeleteCard) {
        try {
            cur.getTags().remove("Card")
            cur.getTags().remove("Deckbranch")
            cur.attributes.remove('BackLevels')
            cur.attributes.remove('anki:deckbranch')
            cur.attributes.remove('anki:deck')
            cur.attributes.remove('anki:model')
            cur.getIcons().remove("ANKI")
        } catch (ignored) {}
        SwingUtilities.getWindowAncestor(panel).dispose()
    }
} as java.awt.event.ActionListener)

gc.gridx = 0; gc.gridwidth = 2
panel.add(btnRemoveCard, gc); gc.gridy++
gc.gridwidth = 1

// ==== Show dialog ====
int result = JOptionPane.showConfirmDialog(
        ui.frame, panel, "Card settings (Freeplane ↔ Anki)",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
)
if (result != JOptionPane.OK_OPTION) return

// ==== Safe text ====
def safeText(JTextField tf) { return (tf != null && tf.getText() != null) ? tf.getText().trim() : "" }
String valDeck       = safeText(tfDeck)
String valDeckBranch = safeText(tfDeckBranch)
String valBackLevels = safeText(tfBackLevels)

// ==== Apply selected option with deckbranch rule ====
String selectedOption = "Both"
if (rbAddAnkiIcon.isSelected()) selectedOption = "AnkiIcon"
else if (rbAddCardTag.isSelected()) selectedOption = "CardTag"
else if (rbAddBoth.isSelected()) selectedOption = "Both"
else selectedOption = "None"

System.setProperty("freeplane.anki.addoption.default", selectedOption)
applyOptionToNode(cur, selectedOption, valDeck, valDeckBranch)

// ==== Deck/Deckbranch logic ====
if (valDeckBranch.length() > 0) {
    cur['anki:deckbranch'] = valDeckBranch
} else {
    cur.attributes.remove('anki:deckbranch')
}

if (valDeck.length() > 0) {
    cur['anki:deck'] = valDeck
} else {
    cur.attributes.remove('anki:deck')
}

// اگر هر دو خالی باشند → بررسی والدین برای deck
if (valDeckBranch.length() == 0 && valDeck.length() == 0) {
    Node parent = cur.parent
    boolean found = false
    while (parent != null) {
        String ancestorDeckBranch = parent['anki:deckbranch']
        if (ancestorDeckBranch != null && ancestorDeckBranch.trim().length() > 0) {
            cur['anki:deck'] = ancestorDeckBranch.trim()
            found = true
            break
        }
        parent = parent.parent
    }
    if (!found) cur['anki:deck'] = "FreeplaneDeck"
}

// ==== BackLevels ====
if (valBackLevels.length() > 0) cur['BackLevels'] = valBackLevels
else cur.attributes.remove('BackLevels')

// ==== Manage Deckbranch tag ====
try {
    def tagsList = cur.getTags()
    if (valDeckBranch.length() > 0) {
        if (!tagsList.contains("Deckbranch")) tagsList.add("Deckbranch")
    } else {
        if (tagsList.contains("Deckbranch")) tagsList.remove("Deckbranch")
    }
} catch (ignored) {}

// ==== Update descendants ====
if (valDeckBranch != oldDeckBranch) {
    updateDescendants(cur, oldDeckBranch)
}

void updateDescendants(Node parent, String oldBranch) {
    LinkedList<Node> queue = new LinkedList<>()
    queue.addAll(parent.children)
    while (!queue.isEmpty()) {
        Node child = queue.poll()
        String childBranch = child['anki:deckbranch']
        if (childBranch != null && childBranch.trim().length() > 0) continue

        String childDeck = child['anki:deck']
        if (childDeck != null && childDeck == oldBranch) {
            Node ancestor = child.parent
            boolean found = false
            while (ancestor != null) {
                String aDeckBranch = ancestor['anki:deckbranch']
                if (aDeckBranch != null && aDeckBranch.trim().length() > 0) {
                    child['anki:deck'] = aDeckBranch.trim()
                    found = true
                    break
                }
                ancestor = ancestor.parent
            }
            if (!found) child['anki:deck'] = "FreeplaneDeck"
        }

        boolean hasDeckChild = child.children.any { it['anki:deck'] != null }
        if (hasDeckChild) queue.addAll(child.children)
    }
}

// ==== Helper function: Apply option with deckbranch rule ====
void applyOptionToNode(Node node, String option, String valDeck, String valDeckBranch) {
    boolean cardTagPresent = false
    boolean ankiIconPresent = false
    try { cardTagPresent = node.getTags().contains("Card") } catch(ignored) {}
    try { ankiIconPresent = node.getIcons().contains("ANKI") } catch(ignored) {}

    // ⚡ شرط حذف Card وقتی فقط deckbranch مقدار دارد
    if (valDeckBranch.length() > 0 && valDeck.length() == 0) {
        if (cardTagPresent) {
            node.getTags().remove("Card")
            cardTagPresent = false
        }
    }

    if(option == "AnkiIcon") {
        if (!ankiIconPresent) node.getIcons().add("ANKI")
        if (cardTagPresent) node.getTags().remove("Card")
    } else if(option == "CardTag") {
        // فقط اضافه کردن Card اگر deck مقدار داشته باشد
        if (valDeck.length() > 0 && !cardTagPresent) node.getTags().add("Card")
        if (ankiIconPresent) node.getIcons().remove("ANKI")
    } else if(option == "Both") {
        if (!ankiIconPresent) node.getIcons().add("ANKI")
        // فقط اضافه کردن Card اگر deck مقدار داشته باشد
        if (valDeck.length() > 0 && !cardTagPresent) node.getTags().add("Card")
    } else if(option == "None") {
        if (ankiIconPresent) node.getIcons().remove("ANKI")
        if (cardTagPresent) node.getTags().remove("Card")
    }
}
