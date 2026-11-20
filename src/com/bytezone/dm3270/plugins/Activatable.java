package com.bytezone.dm3270.plugins;

// Ciclo de ativacao no host; executado antes de doesRequest(), permitindo armar capacidades.
// -----------------------------------------------------------------------------------//
public interface Activatable
// -----------------------------------------------------------------------------------//
{
  void activate ();

  void deactivate ();
}
