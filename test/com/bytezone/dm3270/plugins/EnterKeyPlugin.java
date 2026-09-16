package com.bytezone.dm3270.plugins;

import com.bytezone.dm3270.commands.AIDCommand;

/*
 * Marca a tecla ENTER no PluginData, que e como um plugin diz ao host "mande isto para o
 * mainframe". E o que faz processReply sair do ramo que devolve null.
 */
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
