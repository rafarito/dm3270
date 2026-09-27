package com.bytezone.dm3270.display;

import static com.bytezone.dm3270.runtime.TerminalFunction.TERMINAL;
import static com.bytezone.dm3270.commands.AIDCommand.NO_AID_SPECIFIED;

import java.awt.Toolkit;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.KeyboardStatusListener;
import com.bytezone.dm3270.assistant.TransfersStage;
import com.bytezone.dm3270.attributes.ColorAttribute;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.ConsoleLines;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;
import com.bytezone.dm3270.commands.SystemMessage;
import com.bytezone.dm3270.commands.SystemMessageView;
import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.console.ConsoleLog;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.console.ConsoleLogStage;
import com.bytezone.dm3270.filetransfer.Transfer;
import com.bytezone.dm3270.filetransfer.Transfer.TransferType;
import com.bytezone.dm3270.filetransfer.TransferListener;
import com.bytezone.dm3270.filetransfer.TransferManager;
import com.bytezone.dm3270.filetransfer.TransferManager.TransferStatus;
import com.bytezone.dm3270.filetransfer.TransferMenu;
import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.plugins.PluginHost;
import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.screen.ContextManager;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.CursorHost;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.FieldHost;
import com.bytezone.dm3270.screen.HostWriteCompletion;
import com.bytezone.dm3270.screen.KeyboardTarget;
import com.bytezone.dm3270.screen.Pen;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenOption;
import com.bytezone.dm3270.screen.ScreenPosition;
import com.bytezone.dm3270.screen.ScreenTarget;
import com.bytezone.dm3270.watch.ScreenChangeListener;
import com.bytezone.dm3270.watch.ScreenWatcher;
import com.bytezone.dm3270.streams.SessionDisplay;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.streams.TelnetStateListener;
import com.bytezone.dm3270.utilities.Site;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.MenuItem;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

// -----------------------------------------------------------------------------------//
public class Screen extends Canvas implements ScreenTarget, CursorHost, FieldHost,
    SystemMessageView, TransferListener, TelnetStateListener, PluginHost, SessionDisplay,
    KeyboardTarget
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (Screen.class);

  private static final Toolkit defaultToolkit = Toolkit.getDefaultToolkit ();
  private static final boolean SHOW_CURSOR = true;
  private static final boolean HIDE_CURSOR = false;

  private final TerminalFunction function;

  private final ScreenPosition[] screenPositions;
  private final FieldManager fieldManager;
  private final FontManager fontManager;
  private final ContextManager contextManager;
  private final HistoryManager historyManager;
  private final TransferManager transferManager;
  private final ScreenPacker screenPacker;
  private final TransferMenu transferMenu;
  private final SystemMessage systemMessage;

  private final PluginsStage pluginsStage;
  private final TransfersStage transfersStage;
  private final ConsoleLogStage consoleLogStage;
  private ConsoleLog consoleLog;
  private ConsoleView consolePane;
  private final TelnetState telnetState;

  private final GraphicsContext gc;
  private final ScreenDimensions defaultScreenDimensions;
  private ScreenDimensions alternateScreenDimensions;

  private final Pen pen;
  private final Cursor cursor;
  private final ScreenSelection screenSelection;
  private final HostWriteCompletion hostWriteCompletion = new HostWriteCompletion (
      this::getFieldCount, this::isKeyboardLocked, this::checkRecording,
      this::processPluginAuto);
  private ScreenOption currentScreen;

  private final ScreenReply screenReply;

  private int insertedCursorPosition = -1;
  private final KeyboardStatus keyboardStatus = new KeyboardStatus ();

  // ---------------------------------------------------------------------------------//
  public Screen (ScreenDimensions defaultScreenDimensions,
      ScreenDimensions alternateScreenDimensions, Preferences prefs, TerminalFunction function,
      PluginsStage pluginsStage, Site serverSite, TelnetState telnetState,
      DatasetStore datasetStore)
  // ---------------------------------------------------------------------------------//
  {
    this.defaultScreenDimensions = defaultScreenDimensions;
    this.alternateScreenDimensions = alternateScreenDimensions;
    this.function = function;
    this.telnetState = telnetState;

    ScreenDimensions screenDimensions = alternateScreenDimensions == null
        ? defaultScreenDimensions : alternateScreenDimensions;

    cursor = new Cursor (this, screenDimensions);
    gc = getGraphicsContext2D ();
    screenSelection = new ScreenSelection (new Selection ());
    new ScreenMouseInput (this, cursor, screenSelection,
        () -> getFontManager ().getFontDetails (), this::getScreenDimensions).install ();

    contextManager = new ContextManager ();
    fontManager = FontManager.getInstance (this::fontChanged, prefs);
    fieldManager = new FieldManager (this, contextManager, screenDimensions, datasetStore);
    historyManager = new HistoryManager (screenDimensions, contextManager, fieldManager);
    transfersStage = new TransfersStage (this, fieldManager);

    consoleLogStage = new ConsoleLogStage ();
    systemMessage = new SystemMessage (this, transfersStage, screenDimensions, this);

    transferManager = new TransferManager (this::getPrefix, serverSite);
    transferMenu = new TransferMenu (serverSite, transferManager);

    transfersStage.setTransferManager (transferManager);

    screenPositions = new ScreenPosition[screenDimensions.size];
    pen = Pen.getInstance (screenPositions, new FxScreenCanvas (gc), contextManager,
        screenDimensions);

    screenPacker = new ScreenPacker (pen, fieldManager);
    screenReply = new ScreenReply (screenPacker, () -> getScreenCursor ().getLocation ());

    screenPacker.addTSOCommandListener (transfersStage);
    screenPacker.addTSOCommandListener (transferManager);

    addKeyboardStatusChangeListener (transfersStage);

    fieldManager.addScreenChangeListener (transfersStage);
    fieldManager.addScreenChangeListener (screenPacker);
    fieldManager.addScreenChangeListener (transferMenu);

    transferManager.addTransferListener (this);
    transferManager.addTransferListener (transfersStage);

    telnetState.addTelnetStateListener (this);
    setCurrentScreen (ScreenOption.DEFAULT);

    this.pluginsStage = pluginsStage;
    pluginsStage.setScreen (this);
  }

  // ---------------------------------------------------------------------------------//
  void redrawRange (int from, int to)
  // ---------------------------------------------------------------------------------//
  {
    int cursorPos = cursor.getLocation ();
    for (int i = from; i <= to; i++)
    {
      boolean hasCursor = i == cursorPos && cursor.isVisible ()
          && !screenPositions[i].isSelected ();
      screenPositions[i].draw (hasCursor);
    }
  }

  // ---------------------------------------------------------------------------------//
  void redrawSelection (int oldMin, int oldMax, int newMin, int newMax)
  // ---------------------------------------------------------------------------------//
  {
    // Redraw only the positions that changed
    int from = Math.min (oldMin, newMin);
    int to = Math.max (oldMax, newMax);
    redrawRange (from, to);
  }

  /*
   * A porta da ScreenSelection. Interna e privada para que redrawRange e redrawSelection
   * continuem de pacote - ver o cabecalho de SelectionHost.
   */
  // ---------------------------------------------------------------------------------//
  private final class Selection implements SelectionHost
  // ---------------------------------------------------------------------------------//
  {
    @Override
    public ScreenDimensions getScreenDimensions ()
    {
      return Screen.this.getScreenDimensions ();
    }

    @Override
    public ScreenPosition getScreenPosition (int position)
    {
      return Screen.this.getScreenPosition (position);
    }

    @Override
    public void redrawRange (int from, int to)
    {
      Screen.this.redrawRange (from, to);
    }

    @Override
    public void redrawSelection (int oldMin, int oldMax, int newMin, int newMax)
    {
      Screen.this.redrawSelection (oldMin, oldMax, newMin, newMax);
    }
  }

  // ---------------------------------------------------------------------------------//
  public void copySelection ()
  // ---------------------------------------------------------------------------------//
  {
    if (screenSelection.hasSelection ())
    {
      String text = screenSelection.getSelectedText ();
      ClipboardContent content = new ClipboardContent ();
      content.putString (text);
      Clipboard.getSystemClipboard ().setContent (content);

      // Visual flash feedback: briefly clear and restore the selection highlight
      screenSelection.flashSelection ();
    }
  }

  // ---------------------------------------------------------------------------------//
  public void pasteText ()
  // ---------------------------------------------------------------------------------//
  {
    Clipboard clipboard = Clipboard.getSystemClipboard ();
    if (clipboard.hasString ())
    {
      String text = clipboard.getString ();
      if (text != null && !text.isEmpty ())
        cursor.typeText (text);
    }
  }

  /*
   * Delega para a ScreenSelection, que e o que os tres sitios do ConsoleKeyPress e o do
   * ConsoleKeyEvent faziam a mao. Existe para que a porta KeyboardTarget nao precise devolver
   * a ScreenSelection, que e do pacote display e arrasta JavaFX atras.
   */
  // ---------------------------------------------------------------------------------//
  @Override
  public void clearSelection ()
  // ---------------------------------------------------------------------------------//
  {
    screenSelection.clearSelection ();
  }

  // ---------------------------------------------------------------------------------//
  ScreenSelection getScreenSelection ()
  // ---------------------------------------------------------------------------------//
  {
    return screenSelection;
  }


  // ---------------------------------------------------------------------------------//
  public ScreenWatcher getScreenWatcher ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.getScreenWatcher ();
  }

  // ---------------------------------------------------------------------------------//
  public TransferManager getTransferManager ()
  // ---------------------------------------------------------------------------------//
  {
    return transferManager;
  }

  // ---------------------------------------------------------------------------------//
  public SystemMessage getSystemMessage ()
  // ---------------------------------------------------------------------------------//
  {
    return systemMessage;
  }

  // ---------------------------------------------------------------------------------//
  public MenuItem getMenuItemUpload ()
  // ---------------------------------------------------------------------------------//
  {
    return transferMenu.getMenuItemUpload ();
  }

  // ---------------------------------------------------------------------------------//
  public MenuItem getMenuItemDownload ()
  // ---------------------------------------------------------------------------------//
  {
    return transferMenu.getMenuItemDownload ();
  }

  // ---------------------------------------------------------------------------------//
  /*
   * A resposta a um Read Partition (Query), montada a partir do estado da negociacao telnet.
   * O protocolo pedia o TelnetState so para isto - ver ScreenTarget.buildQueryReply.
   */
  @Override
  public ReadStructuredFieldCommand buildQueryReply ()
  {
    return new ReadStructuredFieldCommand (telnetState.getSecondary ());
  }

  // ---------------------------------------------------------------------------------//
  public TelnetState getTelnetState ()
  // ---------------------------------------------------------------------------------//
  {
    return telnetState;
  }

  // called from WriteCommand.process()
  // ---------------------------------------------------------------------------------//
  public void setCurrentScreen (ScreenOption value)
  // ---------------------------------------------------------------------------------//
  {
    if (currentScreen == value)
      return;

    currentScreen = value;
    ScreenDimensions screenDimensions = getScreenDimensions ();

    cursor.setScreenDimensions (screenDimensions);
    pen.setScreenDimensions (screenDimensions);
    historyManager.setScreenDimensions (screenDimensions);
    fieldManager.setScreenDimensions (screenDimensions);
    systemMessage.setScreenDimensions (screenDimensions);

    BufferAddress.setScreenWidth (screenDimensions.columns);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public ScreenDimensions getScreenDimensions ()
  // ---------------------------------------------------------------------------------//
  {
    return currentScreen == ScreenOption.DEFAULT ? defaultScreenDimensions
        : alternateScreenDimensions;
  }

  /*
   * O SystemMessage reconheceu a tela de PROFILE do TSO e pediu para mostra-la. Montar o
   * dialogo e agendar a exibicao na thread da interface e trabalho desta camada.
   */
  // ---------------------------------------------------------------------------------//
  @Override
  public void showProfile (String profileMessageText1, String profileMessageText2)
  // ---------------------------------------------------------------------------------//
  {
    Profile profile = new Profile (profileMessageText1, profileMessageText2);
    Platform.runLater ( () -> profile.showAndWait ());
  }

  /*
   * O SystemMessage reconheceu a mensagem de IPL e vai passar a mandar linhas de console.
   * Criar o log - que e um TextArea com a fonte monoespacada - e trabalho desta camada.
   */
  // ---------------------------------------------------------------------------------//
  @Override
  public ConsoleLines openConsoleLog ()
  // ---------------------------------------------------------------------------------//
  {
    Font displayFont = Font.font ("Monospaced", 13);
    consoleLog = new ConsoleLog (displayFont);
    return consoleLog;
  }

  // ---------------------------------------------------------------------------------//
  public void setIsConsole ()
  // ---------------------------------------------------------------------------------//
  {
    consolePane.setIsConsole (true);
    consoleLogStage.setConsoleLog (consoleLog);
  }

  // called from the ConsolePane constructor
  // ---------------------------------------------------------------------------------//
  public void setConsolePane (ConsoleView consolePane)
  // ---------------------------------------------------------------------------------//
  {
    this.consolePane = consolePane;

    // allow these classes to issue TSO commands
    transfersStage.setConsolePane (consolePane);
    transferMenu.setConsolePane (consolePane);

    addKeyboardStatusChangeListener (consolePane);
  }

  // ---------------------------------------------------------------------------------//
  public void setStatusText (String text)
  // ---------------------------------------------------------------------------------//
  {
    consolePane.setStatusText (text);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int getFieldCount ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.size ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public java.util.Optional<Field> getFieldAt (int position)
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.getFieldAt (position);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public List<Field> getFields ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.getFields ();
  }

  // called from WriteCommand.process()
  // ---------------------------------------------------------------------------------//
  @Override
  public void hostWriteCompleted (boolean freshContent, Consumer<AIDCommand> reply)
  // ---------------------------------------------------------------------------------//
  {
    hostWriteCompletion.completed (freshContent, reply);
  }

  // called from HostWriteCompletion.completed()
  // ---------------------------------------------------------------------------------//
  private AIDCommand processPluginAuto ()
  // ---------------------------------------------------------------------------------//
  {
    return pluginsStage.processPluginAuto ();
  }

  // ---------------------------------------------------------------------------------//
  public FieldManager getFieldManager ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isTSOCommandScreen ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.getScreenWatcher ().isTSOCommandScreen ();
  }

  // ---------------------------------------------------------------------------------//
  public Field getTSOCommandField ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.getScreenWatcher ().getTSOCommandField ();
  }

  // ---------------------------------------------------------------------------------//
  public String getPrefix ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.getScreenWatcher ().getPrefix ();
  }

  // ---------------------------------------------------------------------------------//
  public FontManager getFontManager ()
  // ---------------------------------------------------------------------------------//
  {
    return fontManager;
  }

  // called by WriteCommand.process()
  // ---------------------------------------------------------------------------------//
  public PluginsStage getPluginsStage ()
  // ---------------------------------------------------------------------------------//
  {
    return pluginsStage;
  }

  // ---------------------------------------------------------------------------------//
  public TransfersStage getAssistantStage ()
  // ---------------------------------------------------------------------------------//
  {
    return transfersStage;
  }

  // ---------------------------------------------------------------------------------//
  public ConsoleLogStage getConsoleLogStage ()
  // ---------------------------------------------------------------------------------//
  {
    return consoleLogStage;
  }

  // ---------------------------------------------------------------------------------//
  public void close ()
  // ---------------------------------------------------------------------------------//
  {
    transfersStage.closeWindow ();
    fieldManager.close ();
  }

  // ---------------------------------------------------------------------------------//
  public TerminalFunction getFunction ()
  // ---------------------------------------------------------------------------------//
  {
    return function;
  }

  // called from AIDCommand.process()
  // ---------------------------------------------------------------------------------//
  public void addTSOCommand (String command)
  // ---------------------------------------------------------------------------------//
  {
    screenPacker.addTSOCommand (command);
  }

  // display a message on the screen - only used when logging off
  // ---------------------------------------------------------------------------------//
  public void displayText (String text)
  // ---------------------------------------------------------------------------------//
  {
    gc.setFill (FxPalette.toFx (ColorAttribute.colors[8]));                // black
    gc.fillRect (0, 0, getWidth (), getHeight ());
    gc.setFill (FxPalette.toFx (ColorAttribute.colors[5]));                // turquoise

    int x = 120;
    int y = 100;
    int height = 20;

    for (String line : text.split ("\n"))
    {
      gc.fillText (line, x, y);
      y += height;
    }
  }

  // called from AIDCommand.process()
  // called from ConsolePane constructor
  // called from PluginsStage.processPluginAuto()
  // called from PluginsStage.processPluginRequest()
  // called from PluginsStage.processReply()
  // ---------------------------------------------------------------------------------//
  public Cursor getScreenCursor ()
  // ---------------------------------------------------------------------------------//
  {
    return cursor;
  }

  // called from WriteControlCharacter.process()
  // ---------------------------------------------------------------------------------//
  public void resetInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    keyboardStatus.resetInsertMode ();
  }

  // called from ConsoleKeyPress.handle()
  // called from ConsolePane.sendAID()
  // ---------------------------------------------------------------------------------//
  public void toggleInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    keyboardStatus.toggleInsertMode ();
  }

  // called from Cursor.typeChar()
  // called from ConsolePane.sendAID()
  // ---------------------------------------------------------------------------------//
  public boolean isInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    return keyboardStatus.isInsertMode ();
  }

  // called from EraseAllUnprotectedCommand.process()
  // ---------------------------------------------------------------------------------//
  public void eraseAllUnprotected ()
  // ---------------------------------------------------------------------------------//
  {
    Optional<Field> firstUnprotectedField = fieldManager.eraseAllUnprotected ();

    restoreKeyboard ();         // resets the AID to NO_AID_SPECIFIED
    resetModified ();
    draw ();

    if (firstUnprotectedField.isPresent ())
      cursor.moveTo (firstUnprotectedField.get ().getFirstLocation ());
  }

  // ---------------------------------------------------------------------------------//
  public void buildFields (WriteControlCharacter wcc)
  // ---------------------------------------------------------------------------------//
  {
    fieldManager.buildFields (screenPositions);        // what about resetModified?
  }

  // called from HostWriteCompletion.completed()
  // ---------------------------------------------------------------------------------//
  private void checkRecording ()
  // ---------------------------------------------------------------------------------//
  {
    screenReply.recordBuffer (historyManager::saveScreen);
  }

  // called from this.eraseAllUnprotected()
  // called from this.resize()
  // called from Write.process()
  // ---------------------------------------------------------------------------------//
  public void draw ()
  // ---------------------------------------------------------------------------------//
  {
    int max = getScreenDimensions ().size;
    for (int i = 0; i < max; i++)
      screenPositions[i].draw (HIDE_CURSOR);

    if (insertedCursorPosition >= 0)
    {
      cursor.moveTo (insertedCursorPosition);
      insertedCursorPosition = -1;
      cursor.setVisible (true);
    }

    screenPositions[cursor.getLocation ()].draw (SHOW_CURSOR);
  }

  // called from Field.draw()
  // called from Cursor.moveTo() - when moving the cursor around the screen
  // called from Cursor.setVisible()
  // called from Cursor.backspace()
  // called from Cursor.delete()
  // called from Cursor.eraseEOL()
  // called from Cursor.moveTo()
  // ---------------------------------------------------------------------------------//
  @Override
  public void drawPosition (int position, boolean hasCursor)
  // ---------------------------------------------------------------------------------//
  {
    screenPositions[position].draw (hasCursor);
  }

  // called from FontManager() before we are fully initialised - see FontChangeTarget
  // called from FontManager.setFont() with adjustStage=true
  // called with adjustStage=false when resize comes from user dragging the window
  private void fontChanged (FontDetails fontDetails, boolean adjustStage)
  {
    contextManager.setFontMetrics (fontDetails.metrics ());

    // always use the largest available screen
    ScreenDimensions screenDimensions = alternateScreenDimensions == null
        ? defaultScreenDimensions : alternateScreenDimensions;
    setWidth (
        fontDetails.width * screenDimensions.columns + screenDimensions.xOffset * 2);
    setHeight (fontDetails.height * screenDimensions.rows + screenDimensions.yOffset * 2);

    gc.setFont (fontDetails.font);

    // the two null checks below are the protection for the call from inside our own
    // constructor: neither the console pane nor the screen positions exist yet
    if (consolePane != null)
      consolePane.setStatusFont ();

    if (screenPositions != null)
    {
      if (adjustStage && getScene () != null && getScene ().getWindow () != null)
        ((Stage) getScene ().getWindow ()).sizeToScene ();
      eraseScreen ();
      draw ();
    }
  }

  // called by ConsolePane.handleResize() when the user resizes the window
  public void resizeToFit (double availableWidth, double availableHeight)
  {
    ScreenDimensions dims = alternateScreenDimensions == null
        ? defaultScreenDimensions : alternateScreenDimensions;

    double maxCharWidth = (availableWidth - dims.xOffset * 2) / dims.columns;
    double maxCharHeight = (availableHeight - dims.yOffset * 2) / dims.rows;

    if (maxCharWidth > 0 && maxCharHeight > 0)
      fontManager.setFontToFit (maxCharWidth, maxCharHeight);
  }

  // ---------------------------------------------------------------------------------//
  void eraseScreen ()
  // ---------------------------------------------------------------------------------//
  {
    gc.setFill (FxPalette.toFx (ColorAttribute.colors[8]));             // black
    gc.fillRect (0, 0, getWidth (), getHeight ());
  }

  // called from Cursor.home()
  // ---------------------------------------------------------------------------------//
  @Override
  public Optional<Field> getHomeField ()
  // ---------------------------------------------------------------------------------//
  {
    List<Field> fields = fieldManager.getUnprotectedFields ();
    if (fields != null && fields.size () > 0)
      return Optional.of (fields.get (0));
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  public void setAID (byte aid)
  // ---------------------------------------------------------------------------------//
  {
    screenReply.setAID (aid);
  }

  // ---------------------------------------------------------------------------------//
  public byte getAID ()
  // ---------------------------------------------------------------------------------//
  {
    return screenReply.getAID ();
  }

  // called from SetReplyModeSF
  // ---------------------------------------------------------------------------------//
  public void setReplyMode (byte replyMode, byte[] replyTypes)
  // ---------------------------------------------------------------------------------//
  {
    screenReply.setReplyMode (replyMode, replyTypes);
  }

  // ---------------------------------------------------------------------------------//
  public void setFieldText (Field field, String text)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      field.setText (text.getBytes ("CP1047"));
      field.setModified (true);
      field.draw ();                      // draws the field without a cursor
    }
    catch (UnsupportedEncodingException e)
    {
      logger.error ("Unsupported encoding exception while setting text", e);
    }
  }

  // ---------------------------------------------------------------------------------//
  public String getScreenText ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    text.append (pen.getScreenText ());
    text.append ("\n");
    text.append (fieldManager.getTotalsText ());

    return text.toString ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void telnetStateChanged (TelnetState telnetState)
  // ---------------------------------------------------------------------------------//
  {
    //    ScreenDimensions primary = telnetState.getPrimary ();
    ScreenDimensions alternate = telnetState.getSecondary ();
    //    logger.debug (primary);
    //    logger.debug (alternate);
    if (alternate.size > 0 && alternateScreenDimensions == null)
    {
      alternateScreenDimensions = alternate;
      logger.debug ("setting alternate dimensions: {}", alternate);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void transferStatusChanged (TransferStatus status, Transfer transfer)
  // ---------------------------------------------------------------------------------//
  {
    if (transfer.isData ())
      switch (status)
      {
        case READY:
          break;

        case OPEN:
          if (transfer.getTransferType () == TransferType.UPLOAD)
            setText ("Uploading ...");
          else
            setText ("Downloading ...");
          break;

        case PROCESSING:
          if (transfer.getTransferType () == TransferType.DOWNLOAD)
            setText (String.format ("%,d : Bytes received: %,d", transfer.size (),
                transfer.getDataLength ()));
          else
            setText (String.format ("Bytes sent: %,d", transfer.getDataLength ()));
          break;

        case FINISHED:
          setText ("Closing ...");
          break;
      }
  }

  // ---------------------------------------------------------------------------------//
  private void setText (String text)
  // ---------------------------------------------------------------------------------//
  {
    Platform.runLater ( () -> setStatusText (text));
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Pen getPen ()
  // ---------------------------------------------------------------------------------//
  {
    return pen;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public ScreenPosition getScreenPosition (int position)
  // ---------------------------------------------------------------------------------//
  {
    return screenPositions[position];
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public ScreenPosition[] getScreenPositions ()
  // ---------------------------------------------------------------------------------//
  {
    return screenPositions;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int validate (int position)
  // ---------------------------------------------------------------------------------//
  {
    return pen.validate (position);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void clearScreen ()
  // ---------------------------------------------------------------------------------//
  {
    eraseScreen ();
    cursor.moveTo (0);
    pen.clearScreen ();
    fieldManager.reset ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void insertCursor (int position)
  // ---------------------------------------------------------------------------------//
  {
    insertedCursorPosition = position;                // move it here later
  }

  // ---------------------------------------------------------------------------------//
  // Convert screen contents to an AID command
  // ---------------------------------------------------------------------------------//

  // called from ConsoleKeyPress.handle() in response to a user command

  // ---------------------------------------------------------------------------------//
  public AIDCommand readModifiedFields ()
  // ---------------------------------------------------------------------------------//
  {
    return screenReply.readModifiedFields ();
  }

  // Called from:
  //      ReadCommand.process() in response to a ReadBuffer (F2) command
  //      ReadPartitionSF.process() in response to a ReadBuffer (F2) command
  //      ScreenHistory.requestScreen()
  // ---------------------------------------------------------------------------------//
  public AIDCommand readBuffer ()
  // ---------------------------------------------------------------------------------//
  {
    return screenReply.readBuffer ();
  }

  // Called from ReadCommand.process() in response to a ReadModified (F6)
  // or a ReadModifiedAll (6E) command
  // Called from ReadPartitionSF.process() in response to a ReadModified (F6)
  // or a ReadModifiedAll (6E) command
  // ---------------------------------------------------------------------------------//
  public AIDCommand readModifiedFields (byte type)
  // ---------------------------------------------------------------------------------//
  {
    return screenReply.readModifiedFields (type);
  }

  // ---------------------------------------------------------------------------------//
  // Events to be processed from WriteControlCharacter.process()
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  public void resetPartition ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  public void startPrinter ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  public void soundAlarm ()
  // ---------------------------------------------------------------------------------//
  {
    defaultToolkit.beep ();
  }

  // ---------------------------------------------------------------------------------//
  public void restoreKeyboard ()
  // ---------------------------------------------------------------------------------//
  {
    setAID (NO_AID_SPECIFIED);
    cursor.setVisible (true);
    keyboardStatus.unlock ();
  }

  // ---------------------------------------------------------------------------------//
  public void lockKeyboard (String keyName)
  // ---------------------------------------------------------------------------------//
  {
    keyboardStatus.lock (keyName);

    if (function == TERMINAL)
      cursor.setVisible (false);
  }

  // ---------------------------------------------------------------------------------//
  public void resetModified ()
  // ---------------------------------------------------------------------------------//
  {
    fieldManager.getUnprotectedFields ().forEach (f -> f.setModified (false));
  }

  // ---------------------------------------------------------------------------------//
  public boolean isKeyboardLocked ()
  // ---------------------------------------------------------------------------------//
  {
    return keyboardStatus.isLocked ();
  }

  // ---------------------------------------------------------------------------------//
  // Listener events
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  public void addKeyboardStatusChangeListener (KeyboardStatusListener listener)
  // ---------------------------------------------------------------------------------//
  {
    keyboardStatus.addKeyboardStatusChangeListener (listener);
  }

  // ---------------------------------------------------------------------------------//
  public void removeKeyboardStatusChangeListener (KeyboardStatusListener listener)
  // ---------------------------------------------------------------------------------//
  {
    keyboardStatus.removeKeyboardStatusChangeListener (listener);
  }

  // ---------------------------------------------------------------------------------//
  // Screen history
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  public Optional<HistoryManager> pause ()             // triggered by cmd-s
  // ---------------------------------------------------------------------------------//
  {
    if (historyManager.size () == 0)
      return Optional.empty ();

    historyManager.pause (keyboardStatus.isLocked ());
    keyboardStatus.setLockedQuietly (true);

    return Optional.of (historyManager);
  }

  // ---------------------------------------------------------------------------------//
  public void resume ()                     // also triggered by cmd-s
  // ---------------------------------------------------------------------------------//
  {
    keyboardStatus.setLockedQuietly (historyManager.resume ());
  }
}