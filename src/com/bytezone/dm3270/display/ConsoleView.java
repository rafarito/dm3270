package com.bytezone.dm3270.display;

import com.bytezone.dm3270.screen.AidSender;
import com.bytezone.dm3270.screen.KeyboardStatusListener;

/**
 * Porta do painel que contem a Screen.
 * TODO: remover extends quando o composition root fizer a fiacao de AidSender/listeners.
 */
// -----------------------------------------------------------------------------------//
public interface ConsoleView extends AidSender, KeyboardStatusListener
// -----------------------------------------------------------------------------------//
{
  void setIsConsole (boolean value);

  void setStatusText (String text);

  void setStatusFont ();
}
