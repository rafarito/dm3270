package com.bytezone.dm3270.session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.bytezone.dm3270.buffers.NamedBuffer;
import com.bytezone.dm3270.buffers.ReplyBuffer;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.extended.TN3270ExtendedCommand;
import com.bytezone.dm3270.runtime.Source;

// commandName e timeText podem ser null (quando nao ha NamedBuffer ou timestamp).
// -----------------------------------------------------------------------------------//
public class SessionRecord
// -----------------------------------------------------------------------------------//
{
  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern ("dd MMM uuuu HH:mm:ss.S");
  private static final DateTimeFormatter timeFormatter =
      DateTimeFormatter.ofPattern ("mm:ss");
  private final ReplyBuffer message;

  private final Source source;
  private final boolean genuine;
  private final SessionRecordType sessionRecordType;
  private final LocalDateTime dateTime;

  private final String sourceName;
  private final String commandType;
  private final String commandName;
  private final int bufferSize;
  private final String timeText;

  public enum SessionRecordType
  {
    TELNET, TN3270, TN3270E
  }

  // ---------------------------------------------------------------------------------//
  public SessionRecord (SessionRecordType sessionRecordType, ReplyBuffer message,
      Source source, LocalDateTime dateTime, boolean genuine)
  // ---------------------------------------------------------------------------------//
  {
    this.sessionRecordType = sessionRecordType;
    this.message = message;
    this.source = source;
    this.dateTime = dateTime;
    this.genuine = genuine;

    if (genuine)
      sourceName = source == Source.CLIENT ? "Client" : "Server";
    else
      sourceName = "MITM-" + (source == Source.CLIENT ? "C" : "S");

    commandType = switch (sessionRecordType)
    {
      case TELNET -> "Telnet";
      case TN3270 -> "TN3270";
      case TN3270E -> "Extended";
    };

    // CommandHeader nao e NamedBuffer e fica com commandName null.
    commandName = message instanceof NamedBuffer named ? named.getName () : null;

    bufferSize = message.size ();
    timeText = dateTime == null ? null : timeFormatter.format (dateTime);
  }

  // ---------------------------------------------------------------------------------//
  public boolean isTelnet ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecordType == SessionRecordType.TELNET;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isTN3270 ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecordType == SessionRecordType.TN3270;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isTN3270Extended ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecordType == SessionRecordType.TN3270E;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isCommand ()
  // ---------------------------------------------------------------------------------//
  {
    return message instanceof Command || message instanceof TN3270ExtendedCommand;
  }

  // ---------------------------------------------------------------------------------//
  public Command getCommand ()
  // ---------------------------------------------------------------------------------//
  {
    if (message instanceof Command)
      return (Command) message;
    if (message instanceof TN3270ExtendedCommand)
      return ((TN3270ExtendedCommand) message).getCommand ();
    return null;
  }

  // ---------------------------------------------------------------------------------//
  public ReplyBuffer getMessage ()
  // ---------------------------------------------------------------------------------//
  {
    return message;
  }

  // ---------------------------------------------------------------------------------//
  public byte[] getBuffer ()
  // ---------------------------------------------------------------------------------//
  {
    return message.getData ();
  }

  // ---------------------------------------------------------------------------------//
  public int size ()
  // ---------------------------------------------------------------------------------//
  {
    return message.size ();
  }

  // ---------------------------------------------------------------------------------//
  public SessionRecordType getDataRecordType ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecordType;
  }

  // ---------------------------------------------------------------------------------//
  public LocalDateTime getDateTime ()
  // ---------------------------------------------------------------------------------//
  {
    return dateTime;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isGenuine ()
  // ---------------------------------------------------------------------------------//
  {
    return genuine;
  }

  // ---------------------------------------------------------------------------------//
  public Source getSource ()
  // ---------------------------------------------------------------------------------//
  {
    return source;
  }

  // Nome distinto de getTime() para evitar confusao com o getter legado de SessionRow.
  // ---------------------------------------------------------------------------------//
  public String getTimeText ()
  // ---------------------------------------------------------------------------------//
  {
    return timeText;
  }

  // ---------------------------------------------------------------------------------//
  public String getSourceName ()
  // ---------------------------------------------------------------------------------//
  {
    return sourceName;
  }

  // ---------------------------------------------------------------------------------//
  public String getCommandType ()
  // ---------------------------------------------------------------------------------//
  {
    return commandType;
  }

  // ---------------------------------------------------------------------------------//
  public String getCommandName ()
  // ---------------------------------------------------------------------------------//
  {
    return commandName;
  }

  // ---------------------------------------------------------------------------------//
  public int getBufferSize ()
  // ---------------------------------------------------------------------------------//
  {
    return bufferSize;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("%s : %s", source, formatter.format (dateTime));
  }
}
