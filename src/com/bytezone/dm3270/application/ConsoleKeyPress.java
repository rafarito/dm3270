package com.bytezone.dm3270.application;

import java.util.EnumMap;
import java.util.Map;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.Cursor.Direction;
import com.bytezone.dm3270.screen.KeyboardTarget;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Despacho por modo em ordem fixa de guardas; todo binding mapeado consome o evento.
class ConsoleKeyPress implements EventHandler<KeyEvent>
{
  private static final Logger logger = LoggerFactory.getLogger (ConsoleKeyPress.class);
  private static final KeyCode[] PFKeyCodes =
      { KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6,
        KeyCode.F7, KeyCode.F8, KeyCode.F9, KeyCode.F10, KeyCode.F11, KeyCode.F12 };

  private final KeyboardTarget screen;
  private final ConsoleKeyTarget consolePane;
  private final Cursor cursor;

  private final Map<KeyCode, KeyAction> shortcutBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> historyBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> metaBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> controlBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> arrowBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> plainBindings = new EnumMap<> (KeyCode.class);

  @FunctionalInterface
  private interface KeyAction
  {
    void perform (KeyEvent keyEvent);
  }

  public ConsoleKeyPress (ConsoleKeyTarget consolePane, KeyboardTarget screen)
  {
    this.consolePane = consolePane;
    this.screen = screen;
    this.cursor = screen.getScreenCursor ();

    KeyAction home = keyEvent -> cursor.home ();
    KeyAction eraseEOL = keyEvent -> cursor.eraseEOL ();
    KeyAction newLine = keyEvent -> cursor.newLine ();
    KeyAction toggleInsertMode = keyEvent -> screen.toggleInsertMode ();

    shortcutBindings.put (KeyCode.C, keyEvent -> screen.copySelection ());
    shortcutBindings.put (KeyCode.V, keyEvent ->
    {
      screen.clearSelection ();
      screen.pasteText ();
    });

    historyBindings.put (KeyCode.LEFT, keyEvent -> consolePane.back ());
    historyBindings.put (KeyCode.RIGHT, keyEvent -> consolePane.forward ());

    metaBindings.put (KeyCode.ENTER, newLine);
    metaBindings.put (KeyCode.BACK_SPACE, eraseEOL);
    metaBindings.put (KeyCode.DELETE, eraseEOL);
    metaBindings.put (KeyCode.H, home);      // OSX ctrl-h conflicts with Hide Windows command
    metaBindings.put (KeyCode.I, toggleInsertMode);
    metaBindings.put (KeyCode.F1, sendAID (AIDCommand.AID_PA1, "PA1"));
    metaBindings.put (KeyCode.F2, sendAID (AIDCommand.AID_PA2, "PA2"));
    metaBindings.put (KeyCode.F3, sendAID (AIDCommand.AID_PA3, "PA3"));

    controlBindings.put (KeyCode.H, home);                     // OSX has to share ctrl-h

    arrowBindings.put (KeyCode.LEFT, keyEvent -> cursor.move (Direction.LEFT));
    arrowBindings.put (KeyCode.RIGHT, keyEvent -> cursor.move (Direction.RIGHT));
    arrowBindings.put (KeyCode.UP, keyEvent -> cursor.move (Direction.UP));
    arrowBindings.put (KeyCode.DOWN, keyEvent -> cursor.move (Direction.DOWN));

    plainBindings.put (KeyCode.ENTER, sendAID (AIDCommand.AID_ENTER, "ENTR"));
    plainBindings.put (KeyCode.TAB, keyEvent -> cursor.tab (keyEvent.isShiftDown ()));
    plainBindings.put (KeyCode.BACK_SPACE, keyEvent -> cursor.backspace ());
    plainBindings.put (KeyCode.DELETE, keyEvent -> cursor.delete ());
    plainBindings.put (KeyCode.END, eraseEOL);
    plainBindings.put (KeyCode.INSERT, toggleInsertMode);
    plainBindings.put (KeyCode.HOME, home);
    plainBindings.put (KeyCode.ESCAPE, keyEvent -> logger.debug ("escape"));      // CLR key?
  }

  @Override
  public void handle (KeyEvent keyEvent)
  {
    if (keyEvent.getEventType () != KeyEvent.KEY_PRESSED)
      return;

    KeyCode keyCodePressed = keyEvent.getCode ();

    // Handle copy/paste shortcuts before clearing selection
    if (keyEvent.isShortcutDown () && handleShortcut (keyEvent, keyCodePressed))
      return;

    // Clear selection for all other non-modifier key presses
    if (!keyCodePressed.isModifierKey ())
      screen.clearSelection ();

    if (screen.isKeyboardLocked ())           // could be in screen history mode
    {
      dispatch (historyBindings, keyEvent, keyCodePressed);
      return;
    }

    if (keyEvent.isMetaDown ())
    {
      dispatch (metaBindings, keyEvent, keyCodePressed);
      return;
    }

    if (keyEvent.isShiftDown () && keyCodePressed == KeyCode.ENTER)
    {
      cursor.newLine ();
      keyEvent.consume ();
      return;
    }

    if (keyEvent.isControlDown ())
    {
      dispatch (controlBindings, keyEvent, keyCodePressed);
      return;
    }

    if (keyCodePressed.isArrowKey ())
      handleArrowKey (keyEvent, keyCodePressed);
    else
      handlePlainKey (keyEvent, keyCodePressed);
  }

  private boolean dispatch (Map<KeyCode, KeyAction> bindings, KeyEvent keyEvent,
      KeyCode keyCodePressed)
  {
    KeyAction action = bindings.get (keyCodePressed);
    if (action == null)
      return false;

    action.perform (keyEvent);
    keyEvent.consume ();
    return true;
  }

  // Retorna false para atalhos nao mapeados para nao bloquear Meta/Ctrl no restante da cadeia.
  private boolean handleShortcut (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    // Ignore modifier keys pressed alone (Ctrl, Meta, etc.)
    if (keyCodePressed.isModifierKey ())
      return true;

    return dispatch (shortcutBindings, keyEvent, keyCodePressed);
  }

  // KP_UP/DOWN/LEFT/RIGHT tambem satisfazem isArrowKey() e caem no warn sem consumir.
  private void handleArrowKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    if (!dispatch (arrowBindings, keyEvent, keyCodePressed))
      logger.warn ("Impossible arrow key");
  }

  private void handlePlainKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    if (dispatch (plainBindings, keyEvent, keyCodePressed))
      return;

    sendProgramFunctionKey (keyEvent, keyCodePressed);
  }

  private void sendProgramFunctionKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    int pfKey = 1;

    for (KeyCode keyCode : PFKeyCodes)
    {
      if (keyCode == keyCodePressed)
      {
        if (keyEvent.isShiftDown ())
          pfKey += 12;

        String keyName = "PF" + pfKey;
        consolePane.sendAID (AIDCommand.getKey (keyName), keyName);
        keyEvent.consume ();
        return;
      }
      ++pfKey;
    }
  }

  private KeyAction sendAID (byte aid, String name)
  {
    return keyEvent -> consolePane.sendAID (aid, name);
  }
}
