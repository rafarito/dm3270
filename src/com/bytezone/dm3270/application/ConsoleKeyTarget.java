package com.bytezone.dm3270.application;

import com.bytezone.dm3270.screen.AidSender;

// Porta de navegacao de historico e envio de AID usada por ConsoleKeyPress.
// -----------------------------------------------------------------------------------//
interface ConsoleKeyTarget extends AidSender
// -----------------------------------------------------------------------------------//
{
  void back ();

  void forward ();
}
