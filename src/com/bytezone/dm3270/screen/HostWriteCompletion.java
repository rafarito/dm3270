package com.bytezone.dm3270.screen;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.bytezone.dm3270.commands.AIDCommand;

/*
 * O que uma tela faz quando o host termina de escrever nela: grava uma copia e roda os
 * plugins automaticos. Ate o ciclo C2 estas condicoes moravam em WriteCommand.process, e
 * toda politica nova sobre "o que fazer depois de uma escrita" mudava um comando de
 * protocolo. O comando agora so avisa (ApplicationHooks.hostWriteCompleted) e diz o unico
 * fato que so ele conhece: se a escrita trouxe conteudo novo.
 *
 * Sem JavaFX de proposito: a Screen e o HeadlessScreenTarget delegam a esta mesma classe,
 * e por isso o HeadlessProcessingTest continua exercitando as condicoes de verdade, nao um
 * dublê delas.
 *
 * O que e observavel e esta classe preserva:
 *
 *   A ORDEM. Grava-se a tela ja destravada pelo WCC e antes de o plugin a alterar - o
 *   plugin pode travar o teclado de novo, e a copia gravada nao pode ver isso. Quem desenha
 *   continua sendo o WriteCommand, depois do aviso.
 *
 *   AS CONDICOES. As duas exigem campo e teclado livre, e sao lidas de novo para o plugin,
 *   depois da gravacao, como no codigo de origem. O plugin exige ainda conteudo novo.
 *
 *   A RESPOSTA. So e entregue quando o ramo do plugin roda, e entao mesmo nula: nula apaga a
 *   resposta que o comando tinha. O CommandPane reprocessa o mesmo comando no replay, e um
 *   Optional aqui confundiria "o ramo nao rodou" com "rodou e nao produziu nada".
 */
// -----------------------------------------------------------------------------------//
public final class HostWriteCompletion
// -----------------------------------------------------------------------------------//
{
  private final IntSupplier fieldCount;
  private final BooleanSupplier keyboardLocked;
  private final Runnable recorder;
  private final Supplier<AIDCommand> plugins;

  // ---------------------------------------------------------------------------------//
  public HostWriteCompletion (IntSupplier fieldCount, BooleanSupplier keyboardLocked,
      Runnable recorder, Supplier<AIDCommand> plugins)
  // ---------------------------------------------------------------------------------//
  {
    this.fieldCount = fieldCount;
    this.keyboardLocked = keyboardLocked;
    this.recorder = recorder;
    this.plugins = plugins;
  }

  // ---------------------------------------------------------------------------------//
  public void completed (boolean freshContent, Consumer<AIDCommand> reply)
  // ---------------------------------------------------------------------------------//
  {
    if (fieldCount.getAsInt () > 0 && !keyboardLocked.getAsBoolean ())
      recorder.run ();                            // make a copy of the screen

    if (!keyboardLocked.getAsBoolean () && fieldCount.getAsInt () > 0)
    {
      if (freshContent)
        // should check for suppressDisplay
        reply.accept (plugins.get ());
    }
  }
}
