package com.bytezone.dm3270.plugins;

/**
 * Papel de plugins que reagem a telas do host. doesAuto() e consultado a cada tela
 * como estado dinamico; nao usar instanceof (todo Plugin estende AutoPlugin).
 */
// -----------------------------------------------------------------------------------//
public interface AutoPlugin
// -----------------------------------------------------------------------------------//
{
  boolean doesAuto ();

  void processAuto (PluginData data);
}
