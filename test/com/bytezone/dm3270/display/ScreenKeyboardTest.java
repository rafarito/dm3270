package com.bytezone.dm3270.display;

import static com.bytezone.dm3270.commands.AIDCommand.AID_ENTER;
import static com.bytezone.dm3270.commands.AIDCommand.NO_AID_SPECIFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.plugins.RecordingPluginsStage;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.screen.KeyboardStatusListener;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.testing.JavaFxToolkit;

// Ordem de notificacao entre ouvintes nao e garantida (armazenados em HashSet).
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Screen - o estado do teclado e o aviso aos ouvintes")
class ScreenKeyboardTest
// -----------------------------------------------------------------------------------//
{
  private static final ScreenDimensions MODEL_2 = new ScreenDimensions (24, 80);

  private static final byte ERASE_WRITE = 0x05;
  private static final byte WCC_RESET_KEYBOARD = (byte) 0xC2;
  private static final byte SF = 0x1D;
  private static final byte PROTECTED = 0x20;

  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private RecordingPluginsStage pluginsStage;

  private final List<String> events = new ArrayList<> ();

  // ---------------------------------------------------------------------------------//
  private Screen createScreen (TerminalFunction function)
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    pluginsStage =
        JavaFxToolkit.onFxThread ( () -> new RecordingPluginsStage (prefs, pluginsDirectory));
    return JavaFxToolkit.onFxThread ( () -> new Screen (MODEL_2, null, prefs, function,
        pluginsStage, null, new TelnetState (), DatasetStore.NONE));
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void removePreferencesNode () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    pluginsStage.closeClassLoader ();
    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a tela nasce com o teclado livre e o modo de insercao desligado")
  void startsUnlockedWithoutInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);

    assertFalse (screen.isKeyboardLocked ());
    assertFalse (screen.isInsertMode ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("lockKeyboard trava, avisa com o nome da tecla e so depois esconde o cursor")
  void lockKeyboardNotifiesBeforeHidingTheCursor ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    onFx ( () -> screen.getScreenCursor ().setVisible (true));
    listen (screen);

    onFx ( () -> screen.lockKeyboard ("ENTR"));

    assertTrue (screen.isKeyboardLocked ());
    assertEquals (List.of ("insert=false locked=true key=ENTR cursor=true"), events);
    assertFalse (screen.getScreenCursor ().isVisible ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("fora do modo TERMINAL, travar o teclado nao esconde o cursor")
  void lockKeyboardKeepsTheCursorOutsideTerminalMode ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.REPLAY);
    onFx ( () -> screen.getScreenCursor ().setVisible (true));

    onFx ( () -> screen.lockKeyboard ("PF3"));

    assertTrue (screen.isKeyboardLocked ());
    assertTrue (screen.getScreenCursor ().isVisible ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("restoreKeyboard zera o AID, mostra o cursor, destrava e so entao avisa")
  void restoreKeyboardShowsTheCursorBeforeNotifying ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    onFx ( () ->
    {
      screen.lockKeyboard ("ENTR");
      screen.setAID (AID_ENTER);
    });
    assertFalse (screen.getScreenCursor ().isVisible ());
    listen (screen);

    onFx (screen::restoreKeyboard);

    assertFalse (screen.isKeyboardLocked ());
    assertEquals (NO_AID_SPECIFIED, screen.getAID ());
    assertEquals (List.of ("insert=false locked=false key= cursor=true"), events);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("toggleInsertMode inverte o modo e avisa sem nome de tecla, a cada vez")
  void toggleInsertModeNotifiesEveryTime ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    listen (screen);

    onFx (screen::toggleInsertMode);
    assertTrue (screen.isInsertMode ());
    onFx ( () -> screen.lockKeyboard ("PA1"));
    onFx (screen::toggleInsertMode);
    assertFalse (screen.isInsertMode ());

    assertEquals (List.of (                              //
        "insert=true locked=false key= cursor=false",   //
        "insert=true locked=true key=PA1 cursor=false", //
        "insert=false locked=true key= cursor=false"), events);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("resetInsertMode so avisa quando havia modo de insercao para desligar")
  void resetInsertModeIsSilentWhenAlreadyOff ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    listen (screen);

    onFx (screen::resetInsertMode);
    assertEquals (List.of (), events);

    onFx (screen::toggleInsertMode);
    onFx (screen::resetInsertMode);

    assertFalse (screen.isInsertMode ());
    assertEquals (List.of (                              //
        "insert=true locked=false key= cursor=false",   //
        "insert=false locked=false key= cursor=false"), events);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("eraseAllUnprotected destrava o teclado pelo mesmo caminho do restore")
  void eraseAllUnprotectedRestoresTheKeyboard ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    onFx ( () ->
    {
      screen.lockKeyboard ("CLR");
      screen.setAID (AID_ENTER);
    });
    listen (screen);

    onFx (screen::eraseAllUnprotected);

    assertFalse (screen.isKeyboardLocked ());
    assertEquals (NO_AID_SPECIFIED, screen.getAID ());
    assertEquals (List.of ("insert=false locked=false key= cursor=true"), events);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o mesmo ouvinte registrado duas vezes recebe um aviso so")
  void aListenerIsRegisteredOnce ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    KeyboardStatusListener listener = recorder (screen);
    screen.addKeyboardStatusChangeListener (listener);
    screen.addKeyboardStatusChangeListener (listener);

    onFx (screen::toggleInsertMode);

    assertEquals (1, events.size ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o ouvinte removido deixa de ser avisado; remover um ausente nao falha")
  void aRemovedListenerIsNoLongerNotified ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    KeyboardStatusListener listener = recorder (screen);
    screen.removeKeyboardStatusChangeListener (listener);
    screen.addKeyboardStatusChangeListener (listener);
    screen.removeKeyboardStatusChangeListener (listener);

    onFx (screen::toggleInsertMode);

    assertEquals (List.of (), events);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("todos os ouvintes recebem o mesmo objeto de evento")
  void everyListenerGetsTheSameEvent ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    List<KeyboardStatusChangedEvent> first = new ArrayList<> ();
    List<KeyboardStatusChangedEvent> second = new ArrayList<> ();
    screen.addKeyboardStatusChangeListener (first::add);
    screen.addKeyboardStatusChangeListener (second::add);

    onFx ( () -> screen.lockKeyboard ("PF12"));
    onFx (screen::restoreKeyboard);

    assertEquals (2, first.size ());
    assertSame (first.get (0), second.get (0));
    assertSame (first.get (1), second.get (1));
    assertTrue (first.get (0) != first.get (1), "um evento novo a cada aviso");
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("sem historico, pause nao faz nada")
  void pauseWithoutHistoryDoesNothing ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    listen (screen);

    Optional<HistoryManager> history = JavaFxToolkit.onFxThread (screen::pause);

    assertTrue (history.isEmpty ());
    assertFalse (screen.isKeyboardLocked ());
    assertEquals (List.of (), events);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("pause trava em silencio e resume devolve o travamento de antes, livre")
  void pauseAndResumeRestoreAnUnlockedKeyboardSilently ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    recordOneScreen (screen);
    listen (screen);

    Optional<HistoryManager> history = JavaFxToolkit.onFxThread (screen::pause);

    assertTrue (history.isPresent ());
    assertTrue (screen.isKeyboardLocked ());

    onFx (screen::resume);

    assertFalse (screen.isKeyboardLocked ());
    assertEquals (List.of (), events);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("resume devolve o teclado travado se ele ja estava travado no pause")
  void resumeRestoresALockedKeyboard ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = createScreen (TerminalFunction.TERMINAL);
    recordOneScreen (screen);
    onFx ( () -> screen.lockKeyboard ("ENTR"));

    JavaFxToolkit.onFxThread (screen::pause);
    onFx (screen::restoreKeyboard);
    onFx (screen::resume);

    assertTrue (screen.isKeyboardLocked ());
  }

  // Historico so grava telas com campo, teclado destravado e > 3 ordens de texto.
  // ---------------------------------------------------------------------------------//
  private static void recordOneScreen (Screen screen)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = { ERASE_WRITE, WCC_RESET_KEYBOARD,          //
                      SF, PROTECTED, (byte) 0xC1, (byte) 0xC1,  //
                      SF, PROTECTED, (byte) 0xC2, (byte) 0xC2,  //
                      SF, PROTECTED, (byte) 0xC3, (byte) 0xC3,  //
                      SF, PROTECTED, (byte) 0xC4, (byte) 0xC4,  //
                      SF, PROTECTED, (byte) 0xC5, (byte) 0xC5 };
    onFx ( () -> Command.getCommand (buffer, 0, buffer.length).process (screen));
    assertFalse (screen.isKeyboardLocked ());
  }

  // ---------------------------------------------------------------------------------//
  private void listen (Screen screen)
  // ---------------------------------------------------------------------------------//
  {
    screen.addKeyboardStatusChangeListener (recorder (screen));
  }

  // o cursor e lido no momento do aviso: e ele que separa restore de lock
  // ---------------------------------------------------------------------------------//
  private KeyboardStatusListener recorder (Screen screen)
  // ---------------------------------------------------------------------------------//
  {
    return evt -> events.add (String.format ("insert=%s locked=%s key=%s cursor=%s",
        evt.insertMode, evt.keyboardLocked, evt.keyName,
        screen.getScreenCursor ().isVisible ()));
  }

  // ---------------------------------------------------------------------------------//
  private static void onFx (Runnable action)
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      action.run ();
      return null;
    });
  }
}
