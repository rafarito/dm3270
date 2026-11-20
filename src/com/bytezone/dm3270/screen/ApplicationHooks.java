package com.bytezone.dm3270.screen;

import java.util.function.Consumer;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.SystemMessage;

/** Hooks de aplicacao acionados por WriteCommand fora do protocolo 3270. */
// -----------------------------------------------------------------------------------//
public interface ApplicationHooks
// -----------------------------------------------------------------------------------//
{
  /**
   * Notifica fim de escrita (apos WCC, antes do redesenho); freshContent indica dados novos.
   * reply so e chamado se plugins rodarem (inclusive com null, limpando a resposta anterior).
   */
  void hostWriteCompleted (boolean freshContent, Consumer<AIDCommand> reply);

  SystemMessage getSystemMessage ();
}
