package com.bytezone.dm3270.plugins;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.AidSender;

/*
 * O ConsolePane que o PluginsStage enxerga, dublado. Grava no PluginProbe, na mesma lista dos
 * demais, para que um caso afirme a ordem entre travar o teclado e enviar o AID.
 */
// -----------------------------------------------------------------------------------//
public class RecordingAidSender implements AidSender
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Override
  public void sendAID (byte aid, String name)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("consolePane.sendAID:" + name);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void sendAID (AIDCommand command)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("consolePane.sendAID:" + command.getKeyName ());
  }
}
