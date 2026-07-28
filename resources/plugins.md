### Plugins
This facility allows users to write their own plugin java modules which are able to examine and process 3270 input screens.  Modifiable screen fields may be altered, the cursor may be moved and any command or function key may be pressed. Plugins can be executed automatically after each screen is displayed, on request by the user, or both.
#### Creation
Implement the Plugin interface or extend DefaultPlugin.
```java
package com.bytezone.dm3270.plugins;

public interface Plugin
{
  default void activate ()
  {}
  default void deactivate ()
  {}
  default boolean doesAuto ()
  {
    return false;
  }
  default boolean doesRequest ()
  {
    return false;
  }
  default void processAuto (PluginData data)
  {}
  default void processRequest (PluginData data)
  {}
}
```

#### External Plugin Creation (JAR-based)
You can create plugins externally and add them to the `plugins/` folder to be auto-discovered. The `dm3270` JAR is required on your classpath to resolve the plugin interfaces.

1. **Create the plugin directory structure:**
   ```bash
   mkdir -p meu-plugin/src/com/meuplugin
   ```
2. **Create your Plugin classes** in `src/com/meuplugin/`, ensuring they implement `com.bytezone.dm3270.plugins.Plugin` or extend `DefaultPlugin`.

3. **Compile the plugin** using the fat JAR from `dm3270` in the classpath:
   ```bash
   mkdir meu-plugin/out
   javac -cp dm3270-1.0.0-SNAPSHOT-all.jar -d meu-plugin/out meu-plugin/src/com/meuplugin/*.java
   ```

4. **Package the plugin into a JAR file:**
   ```bash
   jar cf meu-plugin.jar -C meu-plugin/out .
   ```

5. **Installation (Plug-and-Play):**
   Copy `meu-plugin.jar` into a folder named `plugins/` located in the same directory where you execute the `dm3270` JAR.
   When the emulator starts, it will automatically discover the JAR, load the classes, and auto-register them in the Plugin Manager.

#### Linking
Use the Plugin Manager to connect the class name to a command name. The command will appear as a menu item on the Plugins menu.
##### Plugin Manager
![Plugins](plugins.png?raw=true "plugin list")
##### Plugins Menu
![Plugins](pluginmenu.png?raw=true "plugins menu")
#### Activation
Select the plugin from the plugins menu to activate it (plugins that are defined as Active are automatically activated). If the plugin returns true from doesRequest() then it will be assigned a command key (0-9) which will be attached to a new menu entry. This new command can be triggered by the user at any time.  
![Plugins](plugin2menu.png?raw=true "plugins menu")
#### Examples
See the [dm3270Plugins](https://github.com/dmolony/dm3270Plugins/) project for some example code.

