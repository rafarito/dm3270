package com.bytezone.dm3270.plugins;

/*
 * Lanca de dentro do processRequest. Ao contrario do processAll, o processPluginRequest nao
 * tem try/catch nenhum, entao isto sobe para quem acionou o item de menu.
 */
// -----------------------------------------------------------------------------------//
public class ThrowingRequestPlugin extends RecordingPlugin
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  public ThrowingRequestPlugin ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void processRequest (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    super.processRequest (data);
    throw new IllegalStateException ("falha proposital do duble no request");
  }
}
