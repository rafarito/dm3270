package com.bytezone.dm3270.plugins;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.AidSender;

// Duble de AidSender que grava em PluginProbe para verificar ordem com lockKeyboard().
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
