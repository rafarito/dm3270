package com.bytezone.dm3270.session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.bytezone.dm3270.buffers.NamedBuffer;
import com.bytezone.dm3270.buffers.ReplyBuffer;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.extended.TN3270ExtendedCommand;
import com.bytezone.dm3270.runtime.Source;

/*
 * Uma mensagem lida do fio, com a origem, o instante e os rotulos que a descrevem.
 *
 * Os cinco rotulos eram cinco Property do JavaFX, criadas preguicosamente e escritas pelo
 * proprio construtor. Existiam so para o SessionTable ligar colunas por nome de string:
 * nenhum dos quinze metodos que as cercavam era chamado por arquivo nenhum do projeto. Hoje
 * sao campos finais, e a conversao para linha de tabela acontece em application.SessionRow.
 *
 * SAO DERIVADOS UMA VEZ, no construtor, e nunca recalculados - era assim antes, quando o
 * construtor escrevia atraves dos setters, e continua sendo. O rotulo diz o que a mensagem
 * era no instante em que chegou.
 *
 * DOIS PODEM SER NULOS, e isso e carga util: commandName quando a mensagem nao e um
 * NamedBuffer - o CommandHeader e o unico caso, e esta explicado la -, e timeText quando o
 * registro veio de um cabecalho curto, sem instante. As duas colunas aparecem vazias, como
 * antes.
 */
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

    // O CommandHeader nao e um NamedBuffer e continua sem nome, como antes.
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

  // ---------------------------------------------------------------------------------//
  // Os rotulos, na ordem em que o SessionTable os mostra
  // ---------------------------------------------------------------------------------//

  /*
   * NAO se chama getTime (). Esse nome pertencia a um getter que devolvia o commandName - um
   * defeito preservado sob a Regra 1, que hoje mora em application.SessionRow, onde a forma
   * JavaBean e exigida pelo PropertyValueFactory. Nomes diferentes para as duas coisas e o que
   * impede alguem de religar a coluna mm:ss ao getter errado sem que nada quebre na
   * compilacao. Esta no BACKLOG-DEFEITOS.md.
   */
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
