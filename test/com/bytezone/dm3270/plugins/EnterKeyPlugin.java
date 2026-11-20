package com.bytezone.dm3270.plugins;

import com.bytezone.dm3270.commands.AIDCommand;

// Define AID_ENTER no PluginData para acionar o envio de resposta pelo host.
// -----------------------------------------------------------------------------------//
public class EnterKeyPlugin extends RecordingPlugin
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  public EnterKeyPlugin ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void processRequest (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    super.processRequest (data);
    data.setKey (AIDCommand.AID_ENTER);
  }
}
