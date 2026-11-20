package com.bytezone.dm3270.application;

import com.bytezone.dm3270.session.SessionRecord;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

// Linha imutavel da SessionTable; precisa ser publica para o PropertyValueFactory
// resolver time, sourceName, commandType, commandName e bufferSize por reflexao.
// -----------------------------------------------------------------------------------//
public class SessionRow
// -----------------------------------------------------------------------------------//
{
  private final SessionRecord sessionRecord;

  private StringProperty sourceName;
  private StringProperty commandType;
  private StringProperty commandName;
  private IntegerProperty bufferSize;
  private StringProperty time;

  // ---------------------------------------------------------------------------------//
  SessionRow (SessionRecord sessionRecord)
  // ---------------------------------------------------------------------------------//
  {
    this.sessionRecord = sessionRecord;

    setSourceName (sessionRecord.getSourceName ());
    setCommandType (sessionRecord.getCommandType ());
    setCommandName (sessionRecord.getCommandName ());
    setBufferSize (sessionRecord.getBufferSize ());
    setTime (sessionRecord.getTimeText ());
  }

  // ---------------------------------------------------------------------------------//
  SessionRecord getRecord ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecord;
  }

  // ---------------------------------------------------------------------------------//
  public final void setTime (String value)
  // ---------------------------------------------------------------------------------//
  {
    timeProperty ().set (value);
  }

  // Legado preservado: le commandNameProperty em vez de timeProperty (nao usado pelo FX).
  // ---------------------------------------------------------------------------------//
  public final String getTime ()
  // ---------------------------------------------------------------------------------//
  {
    return commandNameProperty ().get ();
  }

  // ---------------------------------------------------------------------------------//
  public final StringProperty timeProperty ()
  // ---------------------------------------------------------------------------------//
  {
    if (time == null)
      time = new SimpleStringProperty ();
    return time;
  }

  // ---------------------------------------------------------------------------------//
  public final void setSourceName (String value)
  // ---------------------------------------------------------------------------------//
  {
    sourceNameProperty ().set (value);
  }

  // ---------------------------------------------------------------------------------//
  public final String getSourceName ()
  // ---------------------------------------------------------------------------------//
  {
    return sourceNameProperty ().get ();
  }

  // ---------------------------------------------------------------------------------//
  public final StringProperty sourceNameProperty ()
  // ---------------------------------------------------------------------------------//
  {
    if (sourceName == null)
      sourceName = new SimpleStringProperty ();
    return sourceName;
  }

  // ---------------------------------------------------------------------------------//
  public final void setCommandType (String value)
  // ---------------------------------------------------------------------------------//
  {
    commandTypeProperty ().set (value);
  }

  // ---------------------------------------------------------------------------------//
  public final String getCommandType ()
  // ---------------------------------------------------------------------------------//
  {
    return commandTypeProperty ().get ();
  }

  // ---------------------------------------------------------------------------------//
  public final StringProperty commandTypeProperty ()
  // ---------------------------------------------------------------------------------//
  {
    if (commandType == null)
      commandType = new SimpleStringProperty ();
    return commandType;
  }

  // ---------------------------------------------------------------------------------//
  public final void setCommandName (String value)
  // ---------------------------------------------------------------------------------//
  {
    commandNameProperty ().set (value);
  }

  // ---------------------------------------------------------------------------------//
  public final String getCommandName ()
  // ---------------------------------------------------------------------------------//
  {
    return commandNameProperty ().get ();
  }

  // ---------------------------------------------------------------------------------//
  public final StringProperty commandNameProperty ()
  // ---------------------------------------------------------------------------------//
  {
    if (commandName == null)
      commandName = new SimpleStringProperty ();
    return commandName;
  }

  // ---------------------------------------------------------------------------------//
  public final void setBufferSize (int value)
  // ---------------------------------------------------------------------------------//
  {
    bufferSizeProperty ().set (value);
  }

  // ---------------------------------------------------------------------------------//
  public final int getBufferSize ()
  // ---------------------------------------------------------------------------------//
  {
    return bufferSizeProperty ().get ();
  }

  // ---------------------------------------------------------------------------------//
  public final IntegerProperty bufferSizeProperty ()
  // ---------------------------------------------------------------------------------//
  {
    if (bufferSize == null)
      bufferSize = new SimpleIntegerProperty ();
    return bufferSize;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecord.toString ();
  }
}
