package com.bytezone.dm3270.database;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * O DatasetStore de verdade: uma fila de 64 posicoes e a thread que a consome.
 *
 * Este e o codigo que morava no construtor do FieldManager e no sendRequest do ScreenWatcher.
 * Nada foi mudado no caminho - a fila tem o mesmo tamanho, a thread e criada e iniciada no
 * mesmo instante da sequencia de construcao, o nome do banco e montado do mesmo jeito e os
 * requests sao os mesmos com os mesmos comandos.
 *
 * O Initiator de cada request virou um lambda que traduz o resultado para o StoreListener de
 * quem pediu a operacao. As gravacoes nao tem interessado, porque ScreenWatcher.processResult
 * era um metodo vazio - e continua sem produzir efeito nenhum.
 */
// -----------------------------------------------------------------------------------//
public class QueuedDatasetStore implements DatasetStore
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (QueuedDatasetStore.class);
  private static final Initiator IGNORE = request -> { };

  private final BlockingQueue<DatabaseRequest> queue = new ArrayBlockingQueue<> (64);

  // ---------------------------------------------------------------------------------//
  public QueuedDatasetStore (String databaseName)
  // ---------------------------------------------------------------------------------//
  {
    DatabaseThread databaseThread = new DatabaseThread (databaseName, queue);
    databaseThread.start ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void open (StoreListener listener)
  // ---------------------------------------------------------------------------------//
  {
    send (new DatabaseRequest (reportTo (listener), DatabaseRequest.Command.OPEN),
        "OPEN");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void close (StoreListener listener)
  // ---------------------------------------------------------------------------------//
  {
    send (new DatabaseRequest (reportTo (listener), DatabaseRequest.Command.CLOSE),
        "CLOSE");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void update (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    send (new DatasetRequest (IGNORE, DatabaseRequest.Command.UPDATE, dataset), "UPDATE");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void update (Member member)
  // ---------------------------------------------------------------------------------//
  {
    send (new MemberRequest (IGNORE, DatabaseRequest.Command.UPDATE, member), "UPDATE");
  }

  // ---------------------------------------------------------------------------------//
  private static Initiator reportTo (StoreListener listener)
  // ---------------------------------------------------------------------------------//
  {
    return request -> listener.storeCompleted (request.toString ());
  }

  // ---------------------------------------------------------------------------------//
  private void send (DatabaseRequest request, String commandName)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      queue.put (request);
    }
    catch (InterruptedException e)
    {
      // O texto e identico ao que FieldManager e ScreenWatcher registravam, de proposito.
      // O nome do logger e o unico detalhe que mudou, e so neste caminho: para preserva-lo
      // seria preciso passar um Logger pela porta, o que nao se paga por um erro que exige a
      // thread ser interrompida com a fila de 64 posicoes cheia.
      logger.error ("Interrupted while putting " + commandName + " request in queue", e);
    }
  }
}
