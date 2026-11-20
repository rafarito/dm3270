package com.bytezone.dm3270.display;

import java.util.HashSet;
import java.util.Set;

import com.bytezone.dm3270.screen.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.screen.KeyboardStatusListener;

// Estado do teclado (travado e insercao) e notificacao aos ouvintes;
// efeitos colaterais no cursor e no AID permanecem orquestrados pela Screen.
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
