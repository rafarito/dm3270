package com.bytezone.dm3270.application;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.display.Cursor;
import com.bytezone.dm3270.display.Cursor.Direction;
import com.bytezone.dm3270.display.Screen;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

class ConsoleKeyPress implements EventHandler<KeyEvent>
{
  private static final KeyCode[] PFKeyCodes =
      { KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6,
        KeyCode.F7, KeyCode.F8, KeyCode.F9, KeyCode.F10, KeyCode.F11, KeyCode.F12 };

  private final Screen screen;
  private final ConsolePane consolePane;
  private final Cursor cursor;

  public ConsoleKeyPress (ConsolePane consolePane, Screen screen)
  {
    this.consolePane = consolePane;
    this.screen = screen;
    this.cursor = screen.getScreenCursor ();
  }

  @Override
  public void handle (KeyEvent keyEvent)
  {
    if (keyEvent.getEventType () != KeyEvent.KEY_PRESSED)
      return;

    KeyCode keyCodePressed = keyEvent.getCode ();

    // Handle copy/paste shortcuts before clearing selection
    if (keyEvent.isShortcutDown ())
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
        screen.getScreenSelection ().clearSelection ();
        screen.pasteText ();
        keyEvent.consume ();
        return;
      }
      // For other shortcut combos, clear selection
      screen.getScreenSelection ().clearSelection ();
      return;
    }

    // Clear selection for all other non-modifier key presses
    if (!keyCodePressed.isModifierKey ())
      screen.getScreenSelection ().clearSelection ();

    if (screen.isKeyboardLocked ())           // could be in screen history mode
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

      return;
    }

    if (keyEvent.isMetaDown ())
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
      switch (keyCodePressed)
      {
        case H:
          cursor.home ();
          keyEvent.consume ();
          break;

        default:
          break;
      }
      return;
    }

    if (keyCodePressed.isArrowKey ())
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
          System.out.println ("Impossible arrow key");
          break;
      }
    else
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
          System.out.println ("escape");                // CLR key?
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