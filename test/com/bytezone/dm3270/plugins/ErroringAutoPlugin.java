package com.bytezone.dm3270.plugins;

// Lanca Error (e nao Exception) para escapar do catch(Exception) em processAll().
// -----------------------------------------------------------------------------------//
public class ErroringAutoPlugin extends RecordingPlugin
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  public ErroringAutoPlugin ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void processAuto (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    super.processAuto (data);
    throw new DubleError ();
  }

  // ---------------------------------------------------------------------------------//
  static final class DubleError extends Error
  // ---------------------------------------------------------------------------------//
  {
    private static final long serialVersionUID = 1L;
  }
}
