package com.bytezone.dm3270.database;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.datasets.Member;
import com.bytezone.dm3270.datasets.StoreListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      logger.error ("Interrupted while putting " + commandName + " request in queue", e);
    }
  }
}
