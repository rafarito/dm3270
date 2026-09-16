package com.bytezone.dm3270.plugins;

/*
 * Lanca um Error, e nao uma Exception. O catch do processAll e de Exception, entao isto
 * ESCAPA do laco e aborta os plugins seguintes - a assimetria que o caso
 * umErrorEscapaEAbortaOsSeguintes congela.
 */
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
