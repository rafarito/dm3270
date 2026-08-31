package com.bytezone.dm3270.application;

import com.bytezone.dm3270.session.SessionRecord;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/*
 * Uma linha da tabela de replay.
 *
 * As cinco Property que estao aqui moravam dentro do SessionRecord, misturadas com a mensagem
 * parseada, a origem e o instante. Elas nunca serviram a ninguem alem do SessionTable: nenhum
 * arquivo de src/ ou test/ chamava um dos quinze metodos que as cercam - o PropertyValueFactory
 * os resolve por nome de string, e so.
 *
 * O padrao e o mesmo de assistant.TableDataset, que a onda 3 separou de datasets.DatasetSummary
 * pelo mesmo motivo. A diferenca e que uma linha de sessao NAO MUDA depois de construida: o
 * SessionRecord deriva os cinco rotulos no proprio construtor e nunca mais os altera. Por isso
 * nao ha o copy () que o TableDatasets refaz a cada tela - a copia acontece uma vez, aqui.
 *
 * A CLASSE E PUBLICA, e nao por descuido. O PropertyValueFactory alcanca xxxProperty () por
 * reflexao, e uma classe de pacote nao e alcancavel assim de fora do modulo. E a mesma razao
 * pela qual TableDataset e ConsoleMessage sao publicas. Quem a usa continua sendo so o pacote
 * application.
 *
 * AS CINCO STRINGS DE LIGACAO NAO MUDARAM - time, sourceName, commandType, commandName e
 * bufferSize -, porque elas sao o contrato do SessionTable e quebra-lo nao daria erro de
 * compilacao: daria coluna em branco em tempo de execucao (§5.14).
 */
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

  /*
   * O registro por tras da linha. E por aqui que o filtro do ReplayStage pergunta o tipo e que
   * o CommandPane pega a mensagem para desenhar - a tabela mostra a linha, mas quem responde
   * pelo conteudo continua sendo o dominio.
   */
  // ---------------------------------------------------------------------------------//
  SessionRecord getRecord ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecord;
  }

  // ---------------------------------------------------------------------------------//
  // Time
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  public final void setTime (String value)
  // ---------------------------------------------------------------------------------//
  {
    timeProperty ().set (value);
  }

  /*
   * DEFEITO PRESERVADO, e nao engano de quem moveu o codigo: este getter le a property errada -
   * devolve o nome do comando, nao a hora. Veio assim do SessionRecord e continua inalcancavel,
   * porque o PropertyValueFactory resolve timeProperty () primeiro e nunca chega aqui, e nada
   * no projeto o chama. Corrigi-lo seria um commit fix nesta branch, que a Regra 1 proibe.
   * Esta registrado no BACKLOG-DEFEITOS.md, e o SessionRowTest o congela.
   */
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
  // SourceName
  // ---------------------------------------------------------------------------------//

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
  // CommandType
  // ---------------------------------------------------------------------------------//

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
  // CommandName
  // ---------------------------------------------------------------------------------//

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
  // BufferSize
  // ---------------------------------------------------------------------------------//

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
