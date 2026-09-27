package com.bytezone.dm3270.database;

// -----------------------------------------------------------------------------------//
public class DatabaseRequest
// -----------------------------------------------------------------------------------//
{
  public enum Command
  {
    // dataset/member
    ADD,      // created if not exists, error if exists
    MODIFY,   // error if not exists, modified if exists
    UPDATE,   // created if not exists, modifed if exists
    DELETE,   // must exist
    FIND,     // get dataset/member
    LIST,     // get list of datasets/members

    // database
    CREATE,   // drop tables and create 
    CLOSE,    // close DB
    OPEN,     // open and create tables if not there
    DROP,     // drop if exists
  }

  public enum Result
  {
    SUCCESS, FAILURE
  }

  /*
   * O comando e o Initiator sao final e ficam publicos. Os tres campos de resultado sao
   * escritos pelo DatabaseThread e pelos *Commands, e so por eles: o setter e de pacote, e o
   * getter publico serve a quem implementa o Initiator. Nao ha sincronizacao, e nao precisa
   * haver: todo leitor roda na thread do worker, depois da escrita (item 7 do
   * BACKLOG-DEFEITOS.md).
   */
  public final Command command;
  public final Initiator initiator;
  private Result result;
  private String databaseName;
  private boolean databaseUpdated;

  // ---------------------------------------------------------------------------------//
  public DatabaseRequest (Initiator initiator, Command command)
  // ---------------------------------------------------------------------------------//
  {
    this.initiator = initiator;
    this.command = command;
  }

  // ---------------------------------------------------------------------------------//
  public Result getResult ()
  // ---------------------------------------------------------------------------------//
  {
    return result;
  }

  // ---------------------------------------------------------------------------------//
  void setResult (Result result)
  // ---------------------------------------------------------------------------------//
  {
    this.result = result;
  }

  // ---------------------------------------------------------------------------------//
  public String getDatabaseName ()
  // ---------------------------------------------------------------------------------//
  {
    return databaseName;
  }

  // ---------------------------------------------------------------------------------//
  void setDatabaseName (String databaseName)
  // ---------------------------------------------------------------------------------//
  {
    this.databaseName = databaseName;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isDatabaseUpdated ()
  // ---------------------------------------------------------------------------------//
  {
    return databaseUpdated;
  }

  // ---------------------------------------------------------------------------------//
  void markDatabaseUpdated ()
  // ---------------------------------------------------------------------------------//
  {
    this.databaseUpdated = true;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    text.append (String.format ("Database ...... %s%n", databaseName));
    text.append (String.format ("Command ....... %s%n", command));
    text.append (String.format ("Result ........ %s%n", result));
    text.append (String.format ("Updated ....... %s%n", databaseUpdated));

    return text.toString ();
  }
}