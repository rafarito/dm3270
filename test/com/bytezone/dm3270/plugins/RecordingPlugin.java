package com.bytezone.dm3270.plugins;

// Base dos dubles; cada slot exige tipo proprio pois o host instancia por nome de classe.
// -----------------------------------------------------------------------------------//
public abstract class RecordingPlugin implements Plugin
// -----------------------------------------------------------------------------------//
{
  private final String tag = getClass ().getSimpleName ();

  // ---------------------------------------------------------------------------------//
  @Override
  public void activate ()
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record (tag + ".activate");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void deactivate ()
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record (tag + ".deactivate");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean doesAuto ()
  // ---------------------------------------------------------------------------------//
  {
    return PluginProbe.nextAuto (tag);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean doesRequest ()
  // ---------------------------------------------------------------------------------//
  {
    return PluginProbe.nextRequest (tag);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void processAuto (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record (tag + ".processAuto:" + data.sequence);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void processRequest (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record (tag + ".processRequest:" + data.sequence);
  }
}
