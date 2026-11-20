package com.bytezone.dm3270.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javafx.application.Platform;

// Extensao JUnit 5 que inicia o toolkit JavaFX uma vez por JVM (requer xvfb-run em headless).
// -----------------------------------------------------------------------------------//
public final class JavaFxToolkit implements BeforeAllCallback
// -----------------------------------------------------------------------------------//
{
  private static final Object LOCK = new Object ();
  private static boolean started;

  // ---------------------------------------------------------------------------------//
  @Override
  public void beforeAll (ExtensionContext context)
  // ---------------------------------------------------------------------------------//
  {
    start ();
  }

  // Inicia o toolkit uma unica vez na JVM (Platform.startup falha se ja estiver ativo).
  // ---------------------------------------------------------------------------------//
  public static void start ()
  // ---------------------------------------------------------------------------------//
  {
    synchronized (LOCK)
    {
      if (started)
        return;

      CountDownLatch ready = new CountDownLatch (1);

      try
      {
        Platform.startup (ready::countDown);
      }
      catch (IllegalStateException e)
      {
        // outra extensao ou teste ja subiu o toolkit
        started = true;
        return;
      }

      awaitOrFail (ready, "o toolkit JavaFX nao subiu");

      // Evita encerrar o toolkit ao fechar a ultima janela, pois nao reinicia na mesma JVM.
      Platform.setImplicitExit (false);

      started = true;
    }
  }

  // ---------------------------------------------------------------------------------//
  public static <T> T onFxThread (FxSupplier<T> supplier)
  // ---------------------------------------------------------------------------------//
  {
    start ();

    AtomicReference<T> result = new AtomicReference<> ();
    AtomicReference<Throwable> failure = new AtomicReference<> ();
    CountDownLatch done = new CountDownLatch (1);

    Platform.runLater ( () ->
    {
      try
      {
        result.set (supplier.get ());
      }
      catch (Throwable t)
      {
        failure.set (t);
      }
      finally
      {
        done.countDown ();
      }
    });

    awaitOrFail (done, "o trecho na thread do JavaFX nao terminou");

    if (failure.get () != null)
      throw new IllegalStateException ("falha na thread do JavaFX", failure.get ());

    return result.get ();
  }

  // ---------------------------------------------------------------------------------//
  private static void awaitOrFail (CountDownLatch latch, String message)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      if (!latch.await (30, TimeUnit.SECONDS))
        throw new IllegalStateException (
            message + " em 30s. Num ambiente headless rode a suite sob xvfb-run.");
    }
    catch (InterruptedException e)
    {
      Thread.currentThread ().interrupt ();
      throw new IllegalStateException (message + " - espera interrompida", e);
    }
  }

  // ---------------------------------------------------------------------------------//
  @FunctionalInterface
  public interface FxSupplier<T>
  // ---------------------------------------------------------------------------------//
  {
    T get () throws Exception;
  }
}
