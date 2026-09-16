package com.bytezone.dm3270.plugins;

/*
 * A base dos dubles de plugin: todo metodo da interface passa pelo PluginProbe, marcado com
 * o nome simples da classe concreta.
 *
 * Os dubles concretos sao classes SEPARADAS, com nomes proprios, e nao instancias de uma so:
 * cada posicao das Preferences guarda UM nome de classe, e o host instancia por reflexao a
 * partir dele. Dois plugins ao mesmo tempo exigem dois tipos.
 */
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
