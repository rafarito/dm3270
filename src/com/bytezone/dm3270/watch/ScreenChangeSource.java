package com.bytezone.dm3270.watch;

// Porta de inscricao em mudancas de tela para desacoplar consumidores de display.
// -----------------------------------------------------------------------------------//
public interface ScreenChangeSource
// -----------------------------------------------------------------------------------//
{
  void addScreenChangeListener (ScreenChangeListener listener);
}
