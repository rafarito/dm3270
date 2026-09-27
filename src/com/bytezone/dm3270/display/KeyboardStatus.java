package com.bytezone.dm3270.display;

import java.util.HashSet;
import java.util.Set;

import com.bytezone.dm3270.screen.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.screen.KeyboardStatusListener;

/*
 * O estado do teclado da Screen - travado e modo de insercao - e o aviso aos ouvintes.
 *
 * Morava na Screen, misturado com cursor e AID. Aqui fica so o estado e a notificacao; os
 * efeitos cruzados continuam na Screen, que orquestra: e ela quem zera o AID e mostra o
 * cursor antes de destravar, e quem esconde o cursor depois de travar no modo TERMINAL.
 *
 * O que e observavel e esta classe preserva (o ScreenKeyboardTest vigia):
 *
 *   O EVENTO leva o estado ja alterado, e um objeto novo a cada aviso, o mesmo para todos
 *   os ouvintes.
 *
 *   O SILENCIO. setLockedQuietly existe para o pause e o resume do historico, que sempre
 *   mudaram o travamento sem avisar ninguem.
 *
 *   OS OUVINTES num HashSet, como antes: a ordem entre eles nao e prometida.
 */
// -----------------------------------------------------------------------------------//
final class KeyboardStatus
// -----------------------------------------------------------------------------------//
{
  private final Set<KeyboardStatusListener> keyboardChangeListeners = new HashSet<> ();

  private boolean keyboardLocked;
  private boolean insertMode;

  // ---------------------------------------------------------------------------------//
  boolean isLocked ()
  // ---------------------------------------------------------------------------------//
  {
    return keyboardLocked;
  }

  // ---------------------------------------------------------------------------------//
  void lock (String keyName)
  // ---------------------------------------------------------------------------------//
  {
    keyboardLocked = true;
    fireKeyboardStatusChange (keyName);
  }

  // ---------------------------------------------------------------------------------//
  void unlock ()
  // ---------------------------------------------------------------------------------//
  {
    keyboardLocked = false;
    fireKeyboardStatusChange ("");
  }

  // called from Screen.pause() and Screen.resume()
  // ---------------------------------------------------------------------------------//
  void setLockedQuietly (boolean keyboardLocked)
  // ---------------------------------------------------------------------------------//
  {
    this.keyboardLocked = keyboardLocked;
  }

  // ---------------------------------------------------------------------------------//
  boolean isInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    return insertMode;
  }

  // ---------------------------------------------------------------------------------//
  void resetInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    if (insertMode)
      toggleInsertMode ();
  }

  // ---------------------------------------------------------------------------------//
  void toggleInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    insertMode = !insertMode;
    fireKeyboardStatusChange ("");
  }

  // ---------------------------------------------------------------------------------//
  private void fireKeyboardStatusChange (String keyName)
  // ---------------------------------------------------------------------------------//
  {
    KeyboardStatusChangedEvent evt =
        new KeyboardStatusChangedEvent (insertMode, keyboardLocked, keyName);
    keyboardChangeListeners.forEach (l -> l.keyboardStatusChanged (evt));
  }

  // ---------------------------------------------------------------------------------//
  void addKeyboardStatusChangeListener (KeyboardStatusListener listener)
  // ---------------------------------------------------------------------------------//
  {
    if (!keyboardChangeListeners.contains (listener))
      keyboardChangeListeners.add (listener);
  }

  // ---------------------------------------------------------------------------------//
  void removeKeyboardStatusChangeListener (KeyboardStatusListener listener)
  // ---------------------------------------------------------------------------------//
  {
    if (keyboardChangeListeners.contains (listener))
      keyboardChangeListeners.remove (listener);
  }
}
