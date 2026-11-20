package com.bytezone.dm3270.screen;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.bytezone.dm3270.commands.AIDCommand;

/**
 * Pos-processamento de escrita do host sem dependencia de JavaFX.
 * Grava a tela antes dos plugins (que podem retravar o teclado) e repassa reply mesmo nulo.
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
