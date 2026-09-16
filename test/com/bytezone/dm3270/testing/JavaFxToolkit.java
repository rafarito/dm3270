package com.bytezone.dm3270.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javafx.application.Platform;

/*
 * Liga o toolkit JavaFX uma vez por JVM.
 *
 * Boa parte do dm3270 - SiteForm, ScreenPosition, Pen, Screen - so pode ser instanciada
 * com o toolkit ativo. Sem esta extensao nao existe teste nenhum para essas classes, que sao
 * justamente as que a refatoracao precisa desmontar.
 *
 * Use com @ExtendWith (JavaFxToolkit.class).
 *
 * No CI headless o job precisa rodar sob xvfb-run; do contrario Platform.startup falha e a
 * mensagem abaixo explica o motivo em vez de estourar um erro de toolkit sem contexto.
 */
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

  // Platform.startup lanca IllegalStateException se ja tiver sido chamado, e o toolkit nao
  // pode ser desligado e religado dentro da mesma JVM - por isso o controle explicito.
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

      // SEM ISTO, O PRIMEIRO TESTE QUE MOSTRAR E FECHAR UMA JANELA MATA TODOS OS SEGUINTES.
      //
      // O implicitExit do JavaFX e true por default: quando a ultima janela e fechada, o
      // runtime se desliga sozinho - e o toolkit nao religa na mesma JVM (ver o comentario
      // de start (), acima). O sintoma nao aponta para a causa: os testes seguintes falham
      // em "o trecho na thread do JavaFX nao terminou em 30s", num @BeforeEach que nao tem
      // nada de errado, porque o Platform.runLater deles fica numa fila que ninguem mais
      // atende.
      //
      // E por isso que nenhum teste de Stage desta suite jamais chamou show (): o
      // OptionStageTest e o PluginsStageDispatchTest tem zero chamadas, e a disciplina foi
      // registrada como "alcance pelo grafo de cena" sem que a razao aparecesse. A razao e
      // esta.
      Platform.setImplicitExit (false);

      started = true;
    }
  }

  /*
   * Executa um trecho na thread da aplicacao JavaFX e devolve o resultado.
   *
   * So e necessario para o que exige mesmo a thread da UI. Construir controles e ler ou
   * escrever suas propriedades funciona fora dela - e e o que o proprio codigo de producao
   * faz, por exemplo em SiteForm.getPort, chamado a partir do codigo de rede.
   */
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
