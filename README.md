<details> <summary># SyncTabTitle2.groovy</summary>
This script automatically distinguishes the main map from its secondary (sub‑map) views and assigns the following icons:

Green icon (✳️) – for tabs representing the complete map (main file).
Red icon (♀️) – for secondary views (sub‑maps / new views).
Key features
Icons (green/red) remain fixed – they never change.
The file name of the main map never changes.
For sub‑map tabs (with red icon) , the tab title updates automatically whenever the root node changes (e.g., when using "Jump in" or opening a node as root).
As a result, the tab panel always contains two distinct types:

Main map tabs (✳️ green) – fixed title, representing the original files.
Sub‑map tabs (♀️ red) – dynamic title, ideal for displaying nodes as roots.
Practical usage
You can open several .mm files at the same time; each main tab will have a green icon.
If you want to stay focused on the same main map tab without creating a new tab, use the "Jump in" command. The green icon and the file name will remain unchanged – so you know that tab belongs exclusively to that one file.
When you open a node in a New View, the new tab will have a red icon. This indicates that the tab is specifically meant for showing nodes as roots.
Very important note
It is strongly recommended to place all tabs with the red icon immediately after the main map tab – this way different files and their respective sub‑map tabs stay clearly separated.

## How to run
1. In Freeplane, assign the **F7** shortcut key to the script **# SyncTabTitle1.groovy**.
2. Place the following script (with .groovy extension) [Periodic F7 Execution](https://github.com/aaa1386/freeplane-scripts/blob/main/src/scripts/PeriodicF7Execution) in the following folder:  
   C:\Users\<username>\AppData\Roaming\Freeplane\1.12.x\scripts\init

1. If you want the **# SyncTabTitle1.groovy** script to run automatically and periodically, you must place the **"Periodic F7 Execution"** script inside the `init` folder. Otherwise, pressing F7 will run the script once, and the tab name will update only when you switch to another tab.
2. For the script to work correctly, after opening Freeplane for the first time, close all maps and then start.
3. For the script to work properly, always name the file exactly according to the main root of the map.
</details>

<details> <summary># SyncTabTitle1.groovy</summary>
This script provides a straightforward way to visually distinguish the main map tab from secondary view tabs.  
It makes the tab title **exactly match the root node’s text** and distinguishes between the full map and sub‑map views using green and pink icons.

It assigns the following icons:

- **Green icon (✳️)** – for the main map tab (full map)
- **Red icon (♀️)** – for any node opened as a root (new view)

### Practical usage

- Open several `.mm` files at the same time – each main tab will get a green icon.
- Open a node in a **New View** – the new tab will get a red icon, and its title will change to the root node’s text.

## Note

1. If you want the **# SyncTabTitle1.groovy** script to run automatically and periodically, you must place the **"Periodic F7 Execution"** script inside the `init` folder. Otherwise, pressing F7 will run the script once, and the tab name will update only when you switch to another tab.
2. For the script to work correctly, after opening Freeplane for the first time, close all maps and then start.
3. For the script to work properly, always name the file exactly according to the main root of the map.


## How to run

1. In Freeplane, assign the **F7** shortcut key to the script **# SyncTabTitle1.groovy**.
2. Place the following script (with .groovy extension) [Periodic F7 Execution](https://github.com/aaa1386/freeplane-scripts/blob/main/src/scripts/PeriodicF7Execution) in the following folder:  
   C:\Users\<username>\AppData\Roaming\Freeplane\1.12.x\scripts\init

</details>
