package com.bytezone.dm3270.plugins;

/*
 * Lanca uma RuntimeException de dentro do processAuto, para medir o isolamento do
 * processAll: o host captura Exception por plugin e segue para o proximo.
 */
// -----------------------------------------------------------------------------------//
public class ThrowingAutoPlugin extends RecordingPlugin
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  public ThrowingAutoPlugin ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void processAuto (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    super.processAuto (data);
    throw new IllegalStateException ("falha proposital do duble");
  }
}
