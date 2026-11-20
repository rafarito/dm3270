package com.bytezone.dm3270.plugins;

/**
 * Papel de plugins acionados pelo menu. doesRequest() cria o item uma vez (fica fixo)
 * e pode variar em execucao; nao substituir por instanceof.
 */
// -----------------------------------------------------------------------------------//
public interface RequestPlugin
// -----------------------------------------------------------------------------------//
{
  boolean doesRequest ();

  void processRequest (PluginData data);
}
