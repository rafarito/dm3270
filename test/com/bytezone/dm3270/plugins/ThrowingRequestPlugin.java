package com.bytezone.dm3270.plugins;

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
