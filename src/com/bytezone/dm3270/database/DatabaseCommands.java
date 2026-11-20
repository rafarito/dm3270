package com.bytezone.dm3270.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.database.DatabaseRequest.Result;

// Usa o logger de DatabaseThread para preservar o formato de saida do logback.
// -----------------------------------------------------------------------------------//
final class DatabaseCommands
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private final SchemaInitializer schema;
  private final DatasetCache cache;
  private final Runnable onClose;

  // ---------------------------------------------------------------------------------//
  DatabaseCommands (SchemaInitializer schema, DatasetCache cache, Runnable onClose)
  // ---------------------------------------------------------------------------------//
  {
    this.schema = schema;
    this.cache = cache;
    this.onClose = onClose;
  }

  // ---------------------------------------------------------------------------------//
  void execute (DatabaseRequest request)
  // ---------------------------------------------------------------------------------//
  {
    switch (request.command)
    {
      case OPEN:
        if (schema.create ())                       // create if not already there
          request.setResult (Result.SUCCESS);
        break;

      case DROP:
        if (dropTables ())
          request.setResult (Result.SUCCESS);
        break;

      case CREATE:
        if (dropTables () && schema.create ())
          request.setResult (Result.SUCCESS);
        break;

      case CLOSE:
        onClose.run ();
        request.setResult (Result.SUCCESS);
        break;

      default:
        logger.warn ("Unnown database request: {}", request);
        break;
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean dropTables ()
  // ---------------------------------------------------------------------------------//
  {
    if (!schema.drop ())
      return false;

    cache.clear ();
    return true;
  }
}
