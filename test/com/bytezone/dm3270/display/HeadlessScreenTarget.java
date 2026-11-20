package com.bytezone.dm3270.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.bytezone.dm3270.assistant.BatchJobListener;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.ConsoleLines;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;
import com.bytezone.dm3270.commands.SystemMessage;
import com.bytezone.dm3270.commands.SystemMessageView;
import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.filetransfer.TransferManager;
import com.bytezone.dm3270.watch.ScreenWatcher;
import com.bytezone.dm3270.screen.ContextManager;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.CursorHost;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.FieldHost;
import com.bytezone.dm3270.screen.HostWriteCompletion;
import com.bytezone.dm3270.screen.FontMetrics;
import com.bytezone.dm3270.screen.Pen;
import com.bytezone.dm3270.screen.ScreenCanvas;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenOption;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.screen.ScreenPosition;
import com.bytezone.dm3270.screen.ScreenTarget;
import com.bytezone.dm3270.streams.SessionDisplay;
import com.bytezone.dm3270.streams.TelnetState;

// Duble headless com buffer e campos reais; operacoes fora do buffer sao gravadas em calls.
// -----------------------------------------------------------------------------------//
public final class HeadlessScreenTarget implements SessionDisplay, CursorHost, FieldHost
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
  private final FieldManager fieldManager;

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
    this (defaultDimensions, alternateDimensions, DatasetStore.NONE);
  }

  // ---------------------------------------------------------------------------------//
  public HeadlessScreenTarget (ScreenDimensions defaultDimensions,
      ScreenDimensions alternateDimensions, DatasetStore datasetStore)
  // ---------------------------------------------------------------------------------//
  {
    this.defaultDimensions = defaultDimensions;
    this.alternateDimensions = alternateDimensions;

    contextManager.setFontMetrics (METRICS);
    useDimensions (defaultDimensions);

    cursor = new Cursor (this, defaultDimensions);

    fieldManager = new FieldManager (this, contextManager, defaultDimensions,
        datasetStore);

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

  @Override
  public void drawPosition (int position, boolean hasCursor)
  {
  }

  // Acesso de teste ao ScreenWatcher do FieldManager.
  public ScreenWatcher getScreenWatcher ()
  {
    return fieldManager.getScreenWatcher ();
  }

  // Controle direto para testes exercitarem o modo de insercao do Cursor.
  public void setInsertMode (boolean value)
  {
    insertMode = value;
  }

  @Override
  public boolean isInsertMode ()
  {
    return insertMode;
  }

  @Override
  public Optional<Field> getHomeField ()
  {
    List<Field> unprotected = fieldManager.getUnprotectedFields ();
    return unprotected.isEmpty () ? Optional.empty () : Optional.of (unprotected.get (0));
  }

  // ---------------------------------------------------------------------------------//

  @Override
  public void displayText (String text)
  {
    calls.add ("displayText:" + text);
  }

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

  @Override
  public int getFieldCount ()
  {
    return fieldManager.size ();
  }

  @Override
  public Optional<Field> getFieldAt (int position)
  {
    return fieldManager.getFieldAt (position);
  }

  @Override
  public void buildFields (WriteControlCharacter wcc)
  {
    calls.add ("buildFields");
    fieldManager.buildFields (screenPositions);
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

  private final HostWriteCompletion hostWriteCompletion = new HostWriteCompletion (
      this::getFieldCount, this::isKeyboardLocked, this::checkRecording,
      this::processPluginAuto);

  @Override
  public void hostWriteCompleted (boolean freshContent, Consumer<AIDCommand> reply)
  {
    hostWriteCompletion.completed (freshContent, reply);
  }

  private void checkRecording ()
  {
    calls.add ("checkRecording");
  }

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

  // Nao pode ser null: WriteCommand.process sempre chama getSystemMessage().
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
    return new ReadStructuredFieldCommand (telnetState.getSecondary ());
  }

  public TelnetState getTelnetState ()
  {
    return telnetState;
  }

  @Override
  public TransferManager getTransferManager ()
  {
    return null;              // exige um Site
  }

  // Resposta simulada para processPluginAuto; testes podem atribuir antes do comando.
  public AIDCommand pluginReply;

  private AIDCommand processPluginAuto ()
  {
    calls.add ("processPluginAuto");
    return pluginReply;
  }

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

  // Grava apenas o intervalo [firstLine, lastLine], pois o array lines e reutilizado.
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
