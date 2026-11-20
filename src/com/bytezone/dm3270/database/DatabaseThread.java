package com.bytezone.dm3270.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;

import com.bytezone.dm3270.database.DatabaseRequest.Result;

// -----------------------------------------------------------------------------------//
public class DatabaseThread extends Thread
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private Connection connection;
  private BlockingQueue<DatabaseRequest> queue;
  private boolean cancelled;
  private final String databaseName;

  private final DatabaseCommands databaseCommands;
  private final DatasetCommands datasetCommands;
  private final MemberCommands memberCommands;

  // ---------------------------------------------------------------------------------//
  public DatabaseThread (String databaseName, BlockingQueue<DatabaseRequest> queue)
  // ---------------------------------------------------------------------------------//
  {
    this.databaseName = databaseName;
    this.queue = queue;

    try
    {
      Class.forName ("org.sqlite.JDBC");     // add sqlite JDBC Driver to DriverManager
      Path path = Paths.get (System.getProperty ("user.home"), "dm3270", "databases",
          databaseName);
      Files.createDirectories (path.getParent ());
      String connectionName = "jdbc:sqlite:" + path.toString ();
      connection = DriverManager.getConnection (connectionName);
      connection.setAutoCommit (true);
    }
    catch (Exception e)
    {
      cancelled = true;
      logger.error ("Error initializing database connection", e);
    }

    DatasetCache cache = new DatasetCache ();
    DatasetRepository datasets = new DatasetRepository (connection, cache);
    MemberRepository members = new MemberRepository (connection, cache, datasets);

    databaseCommands = new DatabaseCommands (new SchemaInitializer (connection), cache,
        () -> cancelled = true);
    datasetCommands = new DatasetCommands (datasets, cache);
    memberCommands = new MemberCommands (members, datasets, cache);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void run ()
  // ---------------------------------------------------------------------------------//
  {
    while (!cancelled)
    {
      try
      {
        DatabaseRequest request = queue.take ();

        request.setResult (Result.FAILURE);
        request.setDatabaseName (databaseName);

        if (request instanceof DatasetRequest)
          datasetCommands.execute ((DatasetRequest) request);
        else if (request instanceof MemberRequest)
          memberCommands.execute ((MemberRequest) request);
        else
          databaseCommands.execute (request);

        request.initiator.processResult (request);
      }
      catch (InterruptedException e)
      {
        logger.info ("interrupted");
        cancelled = true;
        Thread.currentThread ().interrupt ();     // preserve the message
      }
    }

    try
    {
      connection.close ();
      logger.info ("Connection closed");
    }
    catch (SQLException e)
    {
      logger.error ("Error closing connection", e);
    }
  }
}
