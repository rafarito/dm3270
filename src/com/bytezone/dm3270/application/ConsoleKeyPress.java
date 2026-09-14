package com.bytezone.dm3270.application;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.Cursor.Direction;
import com.bytezone.dm3270.screen.KeyboardTarget;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ConsoleKeyPress implements EventHandler<KeyEvent>
{
  private static final Logger logger = LoggerFactory.getLogger (ConsoleKeyPress.class);
  private static final KeyCode[] PFKeyCodes =
      { KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6,
        KeyCode.F7, KeyCode.F8, KeyCode.F9, KeyCode.F10, KeyCode.F11, KeyCode.F12 };

  private final KeyboardTarget screen;
  private final ConsoleKeyTarget consolePane;
  private final Cursor cursor;

  public ConsoleKeyPress (ConsoleKeyTarget consolePane, KeyboardTarget screen)
  {
    this.consolePane = consolePane;
    this.screen = screen;
    this.cursor = screen.getScreenCursor ();
  }

  /*
   * A cadeia de guardas, na ordem em que elas decidem - e a ordem e comportamento observavel: o
   * atalho vem antes dos dois blocos de modificador, o teclado travado antes de todo o resto, e
   * o shift+ENTER antes do bloco Control.
   */
  @Override
  public void handle (KeyEvent keyEvent)
  {
    if (keyEvent.getEventType () != KeyEvent.KEY_PRESSED)
      return;

    KeyCode keyCodePressed = keyEvent.getCode ();

    // Handle copy/paste shortcuts before clearing selection
    if (keyEvent.isShortcutDown ())
    {
      handleShortcut (keyEvent, keyCodePressed);
      return;
    }

    // Clear selection for all other non-modifier key presses
    if (!keyCodePressed.isModifierKey ())
      screen.clearSelection ();

    if (screen.isKeyboardLocked ())           // could be in screen history mode
    {
      handleScreenHistory (keyEvent, keyCodePressed);
      return;
    }

    if (keyEvent.isMetaDown ())
    {
      handleMeta (keyEvent, keyCodePressed);
      return;
    }

    if (keyEvent.isShiftDown () && keyCodePressed == KeyCode.ENTER)
    {
      cursor.newLine ();
      keyEvent.consume ();
      return;
    }

    if (keyEvent.isControlDown ())              // OSX has to share ctrl-h
    {
      handleControl (keyEvent, keyCodePressed);
      return;
    }

    if (keyCodePressed.isArrowKey ())
      handleArrowKey (keyEvent, keyCodePressed);
    else
      handlePlainKey (keyEvent, keyCodePressed);
  }

  /*
   * Copiar e colar. Todo OUTRO atalho limpa a selecao e NAO consome: o evento segue para quem
   * estiver ouvindo depois.
   */
  private void handleShortcut (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    // Ignore modifier keys pressed alone (Ctrl, Meta, etc.)
    if (keyCodePressed.isModifierKey ())
      return;

    if (keyCodePressed == KeyCode.C)
    {
      screen.copySelection ();
      keyEvent.consume ();
      return;
    }
    if (keyCodePressed == KeyCode.V)
    {
      screen.clearSelection ();
      screen.pasteText ();
      keyEvent.consume ();
      return;
    }
    // For other shortcut combos, clear selection
    screen.clearSelection ();
  }

  // Com o teclado travado so as duas setas valem; todo o resto e engolido sem ser consumido.
  private void handleScreenHistory (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    if (keyCodePressed == KeyCode.LEFT)
    {
      consolePane.back ();
      keyEvent.consume ();
    }
    else if (keyCodePressed == KeyCode.RIGHT)
    {
      consolePane.forward ();
      keyEvent.consume ();
    }
  }

  private void handleMeta (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    switch (keyCodePressed)
    {
      case ENTER:
        cursor.newLine ();
        keyEvent.consume ();
        break;

      case BACK_SPACE:
      case DELETE:
        cursor.eraseEOL ();
        keyEvent.consume ();
        break;

      case H:                   // OSX ctrl-h conflicts with Hide Windows command
        cursor.home ();
        keyEvent.consume ();
        break;

      case I:
        screen.toggleInsertMode ();
        keyEvent.consume ();
        break;

      case F1:
        consolePane.sendAID (AIDCommand.AID_PA1, "PA1");
        keyEvent.consume ();
        break;

      case F2:
        consolePane.sendAID (AIDCommand.AID_PA2, "PA2");
        keyEvent.consume ();
        break;

      case F3:
        consolePane.sendAID (AIDCommand.AID_PA3, "PA3");
        keyEvent.consume ();
        break;

      default:
        break;
    }
  }

  private void handleControl (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    switch (keyCodePressed)
    {
      case H:
        cursor.home ();
        keyEvent.consume ();
        break;

      default:
        break;
    }
  }

  private void handleArrowKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    switch (keyCodePressed)
    {
      case LEFT:
        cursor.move (Direction.LEFT);
        keyEvent.consume ();
        break;

      case RIGHT:
        cursor.move (Direction.RIGHT);
        keyEvent.consume ();
        break;

      case UP:
        cursor.move (Direction.UP);
        keyEvent.consume ();
        break;

      case DOWN:
        cursor.move (Direction.DOWN);
        keyEvent.consume ();
        break;

      default:
        logger.warn ("Impossible arrow key");
        break;
    }
  }

  private void handlePlainKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    switch (keyCodePressed)
    {
      case ENTER:
        consolePane.sendAID (AIDCommand.AID_ENTER, "ENTR");
        keyEvent.consume ();
        break;

      case TAB:
        cursor.tab (keyEvent.isShiftDown ());
        keyEvent.consume ();
        break;

      case BACK_SPACE:
        cursor.backspace ();
        keyEvent.consume ();
        break;

      case DELETE:
        cursor.delete ();
        keyEvent.consume ();
        break;

      case END:
        cursor.eraseEOL ();
        keyEvent.consume ();
        break;

      case INSERT:
        screen.toggleInsertMode ();
        keyEvent.consume ();
        break;

      case HOME:
        cursor.home ();
        keyEvent.consume ();
        break;

      case ESCAPE:
        logger.debug ("escape");                      // CLR key?
        keyEvent.consume ();
        break;

      default:
        boolean found = false;
        int pfKey = 1;
        for (KeyCode keyCode : PFKeyCodes)
        {
          if (keyCode == keyCodePressed)
          {
            found = true;
            break;
          }
          ++pfKey;
        }
        if (found)
        {
          if (keyEvent.isShiftDown ())
            pfKey += 12;
          String keyName = "PF" + pfKey;
          consolePane.sendAID (AIDCommand.getKey (keyName), keyName);
          keyEvent.consume ();
        }
        break;
    }
  }
}
