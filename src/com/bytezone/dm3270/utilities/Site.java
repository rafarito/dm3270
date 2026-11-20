package com.bytezone.dm3270.utilities;

// Configuracao de conexao lida sob demanda; acessores nao sao necessariamente puros
// (formularios podem ler o estado atual do widget e normalizar valores invalidos).
// -----------------------------------------------------------------------------------//
public interface Site
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  String getName ();

  String getURL ();

  int getPort ();

  boolean getExtended ();

  int getModel ();

  boolean getPlugins ();

  boolean getSsl ();

  boolean getTrustAll ();

  String getFolder ();
}
