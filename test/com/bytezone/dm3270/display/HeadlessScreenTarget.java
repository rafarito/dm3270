package com.bytezone.dm3270.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.assistant.BatchJobListener;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.ConsoleLines;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;
import com.bytezone.dm3270.commands.SystemMessage;
import com.bytezone.dm3270.commands.SystemMessageView;
import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.filetransfer.TransferManager;
import com.bytezone.dm3270.screen.ContextManager;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.CursorHost;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.FieldHost;
import com.bytezone.dm3270.screen.FontMetrics;
import com.bytezone.dm3270.screen.Pen;
import com.bytezone.dm3270.screen.ScreenCanvas;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenOption;
import com.bytezone.dm3270.screen.ScreenPosition;
import com.bytezone.dm3270.screen.ScreenTarget;
import com.bytezone.dm3270.streams.TelnetState;

/*
 * Uma tela de verdade, sem interface grafica.
 *
 * E o retorno de toda a onda de desacoplamento. Ate agora nao existia forma de executar um
 * comando 3270 num teste: Buffer.process pedia a Screen concreta, que estende Canvas,
 * constroi o grafo da aplicacao inteira no construtor - banco SQLite, tres janelas, class
 * loader de plugins - e exige toolkit grafico ativo. Com ScreenTarget e CursorHost no lugar,
 * este dublê basta.
 *
 * O buffer de tela aqui NAO e simulado: sao um Pen e um vetor de ScreenPosition reais,
 * exatamente os que a aplicacao usa. As orders escrevem neles do mesmo jeito, e
 * getScreenText devolve o conteudo resultante. O que e dublê e apenas o desenho (um canvas
 * que descarta) e os colaboradores que ainda nao foram desacoplados.
 *
 * O QUE ESTE DUBLE AINDA NAO FAZ, e por que:
 *
 *   Campos. getFieldCount devolve 0 e getFieldAt devolve vazio, porque FieldManager ainda
 *   exige a Screen concreta no construtor - ele sobe uma thread SQLite ali dentro. Enquanto
 *   isso nao for resolvido (onda 3), os ramos de WriteCommand.process que dependem de haver
 *   campos nao sao percorridos: checkRecording e processPluginAuto ficam de fora. Isso esta
 *   registrado em calls, para que nenhum teste conclua por engano que passou por eles.
 *
 *   TransferManager. Devolve null, porque exige um Site. Um teste que precise dele falha
 *   com NullPointerException, que e melhor do que um dublê silencioso devolvendo respostas
 *   inventadas. SystemMessage, ao contrario, e real: o construtor dele so guarda tres
 *   valores, e WriteCommand.process termina SEMPRE chamando checkSystemMessage - devolver
 *   null ali fazia todo comando estourar.
 *
 * A lista calls registra as operacoes que nao alteram o buffer, na ordem. Serve para verificar
 * intencao - "este comando travou o teclado", "aquele pediu alarme" - sem inspecionar estado
 * interno.
 */
// -----------------------------------------------------------------------------------//
public final class HeadlessScreenTarget implements ScreenTarget, CursorHost, FieldHost
// -----------------------------------------------------------------------------------//
{
  // metricas fixas: nada e desenhado de verdade, mas ScreenPosition.draw as le
  private static final FontMetrics METRICS = new FontMetrics ("Headless", 10, 20, 16);

  public final List<String> calls = new ArrayList<> ();

  private final ScreenDimensions defaultDimensions;
  private final ScreenDimensions alternateDimensions;
  private final ContextManager contextManager = new ContextManager ();

  private ScreenDimensions dimensions;
  private ScreenPosition[] screenPositions;
  private Pen pen;
  private final Cursor cursor;

  private final TelnetState telnetState = new TelnetState ();

  private final SystemMessage systemMessage;

  private boolean keyboardLocked;
  private boolean insertMode;
  private byte currentAID;
  private byte replyMode;
  private byte[] replyTypes = new byte[0];
  private int insertedCursorPosition = -1;

  // ---------------------------------------------------------------------------------//
  public HeadlessScreenTarget ()
  // ---------------------------------------------------------------------------------//
  {
    this (new ScreenDimensions (24, 80), new ScreenDimensions (43, 80));
  }

  // ---------------------------------------------------------------------------------//
  public HeadlessScreenTarget (ScreenDimensions defaultDimensions,
      ScreenDimensions alternateDimensions)
  // ---------------------------------------------------------------------------------//
  {
    this.defaultDimensions = defaultDimensions;
    this.alternateDimensions = alternateDimensions;

    contextManager.setFontMetrics (METRICS);
    useDimensions (defaultDimensions);

    cursor = new Cursor (this, defaultDimensions);

    systemMessage = new SystemMessage (this, new RecordingBatchJobListener (),
        defaultDimensions, new RecordingSystemMessageView ());
  }

  // ---------------------------------------------------------------------------------//
  private void useDimensions (ScreenDimensions newDimensions)
  // ---------------------------------------------------------------------------------//
  {
    dimensions = newDimensions;
    screenPositions = new ScreenPosition[newDimensions.size];
    pen = Pen.getInstance (screenPositions, new DiscardingCanvas (), contextManager,
        newDimensions);
  }

  /*
   * O conteudo da tela como texto, linha por linha. E o que os testes comparam.
   */
  // ---------------------------------------------------------------------------------//
  public String getScreenText ()
  // ---------------------------------------------------------------------------------//
  {
    return pen.getScreenText ();
  }

  // ---------------------------------------------------------------------------------//
  public byte getAID ()
  // ---------------------------------------------------------------------------------//
  {
    return currentAID;
  }

  // ---------------------------------------------------------------------------------//
  public byte getReplyMode ()
  // ---------------------------------------------------------------------------------//
  {
    return replyMode;
  }

  // ---------------------------------------------------------------------------------//
  public int getInsertedCursorPosition ()
  // ---------------------------------------------------------------------------------//
  {
    return insertedCursorPosition;
  }

  // ---------------------------------------------------------------------------------//
  // DisplayScreen - o modelo, implementado de verdade
  // ---------------------------------------------------------------------------------//

  @Override
  public Pen getPen ()
  {
    return pen;
  }

  @Override
  public ScreenDimensions getScreenDimensions ()
  {
    return dimensions;
  }

  @Override
  public ScreenPosition getScreenPosition (int position)
  {
    return screenPositions[position];
  }

  @Override
  public ScreenPosition[] getScreenPositions ()
  {
    return screenPositions;
  }

  @Override
  public int validate (int position)
  {
    return pen.validate (position);
  }

  @Override
  public void clearScreen ()
  {
    calls.add ("clearScreen");
    pen.clearScreen ();
    cursor.moveTo (0);
  }

  @Override
  public void insertCursor (int position)
  {
    insertedCursorPosition = position;
  }

  // ---------------------------------------------------------------------------------//
  // CursorHost
  // ---------------------------------------------------------------------------------//

  @Override
  public void drawPosition (int position, boolean hasCursor)
  {
    // nada a desenhar: o buffer e o que importa
  }

  @Override
  public boolean isInsertMode ()
  {
    return insertMode;
  }

  @Override
  public Optional<Field> getHomeField ()
  {
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  // Teclado
  // ---------------------------------------------------------------------------------//

  @Override
  public void lockKeyboard (String keyName)
  {
    calls.add ("lockKeyboard:" + keyName);
    keyboardLocked = true;
  }

  @Override
  public void restoreKeyboard ()
  {
    calls.add ("restoreKeyboard");
    currentAID = AIDCommand.NO_AID_SPECIFIED;
    keyboardLocked = false;
  }

  @Override
  public boolean isKeyboardLocked ()
  {
    return keyboardLocked;
  }

  @Override
  public void resetInsertMode ()
  {
    calls.add ("resetInsertMode");
    insertMode = false;
  }

  // ---------------------------------------------------------------------------------//
  // Campos - ver a nota no topo: FieldManager ainda exige a Screen concreta
  // ---------------------------------------------------------------------------------//

  @Override
  public int getFieldCount ()
  {
    return 0;
  }

  @Override
  public Optional<Field> getFieldAt (int position)
  {
    return Optional.empty ();
  }

  @Override
  public void buildFields (WriteControlCharacter wcc)
  {
    calls.add ("buildFields");
  }

  @Override
  public void eraseAllUnprotected ()
  {
    calls.add ("eraseAllUnprotected");
  }

  @Override
  public void resetModified ()
  {
    calls.add ("resetModified");
  }

  @Override
  public Cursor getScreenCursor ()
  {
    return cursor;
  }

  // ---------------------------------------------------------------------------------//
  // Resposta ao host
  // ---------------------------------------------------------------------------------//

  @Override
  public AIDCommand readBuffer ()
  {
    calls.add ("readBuffer");
    return null;
  }

  @Override
  public AIDCommand readModifiedFields (byte type)
  {
    calls.add (String.format ("readModifiedFields:%02X", type));
    return null;
  }

  @Override
  public void setReplyMode (byte replyMode, byte[] replyTypes)
  {
    calls.add (String.format ("setReplyMode:%02X", replyMode));
    this.replyMode = replyMode;
    this.replyTypes = replyTypes;
  }

  // ---------------------------------------------------------------------------------//
  public byte[] getReplyTypes ()
  // ---------------------------------------------------------------------------------//
  {
    return replyTypes;
  }

  @Override
  public void checkRecording ()
  {
    calls.add ("checkRecording");
  }

  // ---------------------------------------------------------------------------------//
  // Comandos TSO
  // ---------------------------------------------------------------------------------//

  @Override
  public boolean isTSOCommandScreen ()
  {
    return false;
  }

  @Override
  public Field getTSOCommandField ()
  {
    return null;
  }

  @Override
  public void addTSOCommand (String command)
  {
    calls.add ("addTSOCommand:" + command);
  }

  // ---------------------------------------------------------------------------------//
  // Sinais do WriteControlCharacter
  // ---------------------------------------------------------------------------------//

  @Override
  public void soundAlarm ()
  {
    calls.add ("soundAlarm");
  }

  @Override
  public void startPrinter ()
  {
    calls.add ("startPrinter");
  }

  @Override
  public void resetPartition ()
  {
    calls.add ("resetPartition");
  }

  // ---------------------------------------------------------------------------------//
  // Ciclo de vida
  // ---------------------------------------------------------------------------------//

  @Override
  public void setCurrentScreen (ScreenOption value)
  {
    calls.add ("setCurrentScreen:" + value);

    ScreenDimensions wanted =
        value == ScreenOption.DEFAULT ? defaultDimensions : alternateDimensions;

    if (wanted != dimensions)
      useDimensions (wanted);
  }

  @Override
  public void draw ()
  {
    calls.add ("draw");
  }

  @Override
  public void setIsConsole ()
  {
    calls.add ("setIsConsole");
  }

  // ---------------------------------------------------------------------------------//
  // Colaboradores
  // ---------------------------------------------------------------------------------//

  /*
   * SystemMessage e real: o construtor so guarda o alvo, o ouvinte e a largura da tela, e
   * WriteCommand.process termina sempre chamando checkSystemMessage - devolver null aqui
   * fazia todo comando estourar NullPointerException.
   *
   * O ouvinte de jobs e um no-op que apenas registra, para que um teste possa verificar que
   * a tela foi reconhecida como saida de JOB sem precisar do assistant inteiro.
   */
  @Override
  public SystemMessage getSystemMessage ()
  {
    return systemMessage;
  }

  // -------------------------------------------------------------------------------//
  @Override
  public ReadStructuredFieldCommand buildQueryReply ()
  // -------------------------------------------------------------------------------//
  {
    return new ReadStructuredFieldCommand (telnetState);
  }

  // O TelnetState real do dublê, para quem quiser inspecionar a negociacao.
  public TelnetState getTelnetState ()
  {
    return telnetState;
  }

  @Override
  public TransferManager getTransferManager ()
  {
    return null;              // exige um Site - ver a nota no topo
  }

  @Override
  public AIDCommand processPluginAuto ()
  {
    calls.add ("processPluginAuto");
    return null;
  }

  /*
   * Ouve os eventos de job em batch e apenas anota. O assistant de verdade abre janela;
   * aqui interessa so poder verificar que a tela foi reconhecida.
   */
  // ---------------------------------------------------------------------------------//
  /*
   * A tela de PROFILE do TSO e um dialogo do JavaFX. Aqui so registramos que o protocolo
   * pediu para mostra-la, e com quais textos.
   */
  // ---------------------------------------------------------------------------------//
  private final class RecordingSystemMessageView implements SystemMessageView
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Override
    public void showProfile (String profileMessageText1, String profileMessageText2)
    // -------------------------------------------------------------------------------//
    {
      calls.add ("showProfile:" + profileMessageText1 + "|" + profileMessageText2);
    }

    // -------------------------------------------------------------------------------//
    @Override
    public ConsoleLines openConsoleLog ()
    // -------------------------------------------------------------------------------//
    {
      calls.add ("openConsoleLog");
      return new RecordingConsoleLines ();
    }
  }

  /*
   * As linhas que o SystemMessage recortou. Guardamos o intervalo pedido, nao as linhas:
   * o array tempLines e reaproveitado entre chamadas, entao guardar a referencia mentiria.
   */
  // ---------------------------------------------------------------------------------//
  private final class RecordingConsoleLines implements ConsoleLines
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Override
    public void addLines1 (String[] lines, int firstLine, int lastLine)
    // -------------------------------------------------------------------------------//
    {
      calls.add (String.format ("addLines1:%d:%d", firstLine, lastLine));
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void addLines2 (String[] lines, int firstLine, int lastLine)
    // -------------------------------------------------------------------------------//
    {
      calls.add (String.format ("addLines2:%d:%d", firstLine, lastLine));
    }
  }

  // ---------------------------------------------------------------------------------//
  private final class RecordingBatchJobListener implements BatchJobListener
  // ---------------------------------------------------------------------------------//
  {
    @Override
    public void batchJobSubmitted (int jobNumber, String jobName)
    {
      calls.add (String.format ("batchJobSubmitted:%d:%s", jobNumber, jobName));
    }

    @Override
    public void batchJobEnded (int jobNumber, String jobName, String time,
        int conditionCode)
    {
      calls.add (String.format ("batchJobEnded:%d:%s", jobNumber, jobName));
    }

    @Override
    public void batchJobFailed (int jobNumber, String jobName, String time)
    {
      calls.add (String.format ("batchJobFailed:%d:%s", jobNumber, jobName));
    }
  }

  /*
   * Descarta todo o desenho. As coordenadas e cores continuam sendo calculadas por
   * ScreenPosition exatamente como em producao - so nao chegam a lugar nenhum.
   */
  // ---------------------------------------------------------------------------------//
  private static final class DiscardingCanvas implements ScreenCanvas
  // ---------------------------------------------------------------------------------//
  {
    @Override
    public void setFill (com.bytezone.dm3270.attributes.TerminalColor color)
    {
    }

    @Override
    public void setStroke (com.bytezone.dm3270.attributes.TerminalColor color)
    {
    }

    @Override
    public void fillRect (double x, double y, double width, double height)
    {
    }

    @Override
    public void fillText (String text, double x, double y)
    {
    }

    @Override
    public void strokeLine (double x1, double y1, double x2, double y2)
    {
    }
  }
}
