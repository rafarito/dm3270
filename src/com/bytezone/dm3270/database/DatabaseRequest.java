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

  public final Command command;
  public final Initiator initiator;
  public Result result;
  public String databaseName;
  public boolean databaseUpdated;

  // ---------------------------------------------------------------------------------//
  public DatabaseRequest (Initiator initiator, Command command)
  // ---------------------------------------------------------------------------------//
  {
    this.initiator = initiator;
    this.command = command;
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