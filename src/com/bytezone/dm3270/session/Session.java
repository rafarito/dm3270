package com.bytezone.dm3270.session;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.buffers.ReplyBuffer;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;
import com.bytezone.dm3270.commands.WriteCommand;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.extended.TN3270ExtendedCommand;
import com.bytezone.dm3270.orders.Order;
import com.bytezone.dm3270.orders.TextOrder;
import com.bytezone.dm3270.session.SessionRecord.SessionRecordType;
import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.utilities.Dm3270Utility;

// -----------------------------------------------------------------------------------//
public class Session implements Iterable<SessionRecord>
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (Session.class);

  private final List<SessionRecord> sessionRecords = new ArrayList<> ();
  private final List<SessionRecordListener> recordListeners = new ArrayList<> ();
  private final List<SessionHeaderListener> headerListeners = new ArrayList<> ();
  private final TerminalFunction function;

  private String clientName = null;
  private String serverName = null;
  private final List<String> labels = new ArrayList<> ();
  private boolean safeFlag;
  private ScreenDimensions screenDimensions;

  // called by SpyPane, and by streams.SessionLoader
  // ---------------------------------------------------------------------------------//
  public Session (TerminalFunction function)
  // ---------------------------------------------------------------------------------//
  {
    this.function = function;
  }

  // ---------------------------------------------------------------------------------//
  public List<String> getLabels ()
  // ---------------------------------------------------------------------------------//
  {
    return labels;
  }

  // ---------------------------------------------------------------------------------//
  public int size ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecords.size ();
  }

  // ---------------------------------------------------------------------------------//
  public String getClientName ()
  // ---------------------------------------------------------------------------------//
  {
    return clientName == null ? "Unknown" : clientName;
  }

  // ---------------------------------------------------------------------------------//
  public String getServerName ()
  // ---------------------------------------------------------------------------------//
  {
    return serverName == null ? "Unknown" : serverName;
  }

  // ---------------------------------------------------------------------------------//
  public ScreenDimensions getScreenDimensions ()
  // ---------------------------------------------------------------------------------//
  {
    return screenDimensions;
  }

  // ---------------------------------------------------------------------------------//
  public String getHeaderText ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("%s : %s", getServerName (), getClientName ());
  }

  // Notifica imediatamente se algum lado ja foi identificado (necessario no modo Replay,
  // evitando iniciar o cabecalho do modo Spy com "Unknown : Unknown").
  // ---------------------------------------------------------------------------------//
  public synchronized void addHeaderListener (SessionHeaderListener listener)
  // ---------------------------------------------------------------------------------//
  {
    headerListeners.add (listener);

    if (clientName != null || serverName != null)
      listener.headerChanged ();
  }

  // called from MainframeStage.prepareButtons()
  // called from TelnetListener.addDataRecord()
  // ---------------------------------------------------------------------------------//
  public synchronized void add (SessionRecord sessionRecord)
  // ---------------------------------------------------------------------------------//
  {
    if (sessionRecord == null)
      throw new IllegalArgumentException ("DataRecord is null");

    sessionRecords.add (sessionRecord);       // should this be concurrent?

    // Notifica ouvintes antes da identificacao de cliente/servidor abaixo.
    for (SessionRecordListener listener : recordListeners)
      listener.recordAdded (sessionRecord);

    // this code checks to see whether it can identify the client and/or server
    if (function != TerminalFunction.TERMINAL && sessionRecord.isCommand ())
    {
      switch (sessionRecord.getSource ())
      {
        case CLIENT:
          if (clientName == null)
            checkClientName (sessionRecord.getCommand ());
          if (screenDimensions == null)
            checkScreenDimensions (sessionRecord.getCommand ());
          break;

        case SERVER:
          if (serverName == null)
            checkServerName (sessionRecord.getCommand ());
          break;
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  private void checkScreenDimensions (Command command)
  // ---------------------------------------------------------------------------------//
  {
    if ((command instanceof ReadStructuredFieldCommand))
      screenDimensions = ((ReadStructuredFieldCommand) command).getScreenDimensions ();
  }

  // ---------------------------------------------------------------------------------//
  private void checkClientName (Command command)
  // ---------------------------------------------------------------------------------//
  {
    if (!(command instanceof ReadStructuredFieldCommand))
      return;

    Optional<String> optionalName =
        ((ReadStructuredFieldCommand) command).getClientName ();
    if (optionalName.isPresent ())
    {
      clientName = optionalName.get ();
      notifyHeaderChanged ();
    }
  }

  // ---------------------------------------------------------------------------------//
  private void checkServerName (Command command)
  // ---------------------------------------------------------------------------------//
  {
    if (!(command instanceof WriteCommand))
      return;

    for (Order order : ((WriteCommand) command).getOrdersList ())
      if (order instanceof TextOrder)
      {
        String text = ((TextOrder) order).getTextString ();
        if (text.contains ("Welcome to Fan DeZhi Mainframe System!"))
        {
          serverName = "FanDeZhi";
          break;
        }
        else if (text.contains ("Hercules Version  :"))
        {
          serverName = "Hercules";
          break;
        }
        else if (text.equals ("[(03) 97974300 ]"))
        {
          serverName = "Nissan";
          break;
        }
        else if (text.startsWith ("[InterSession ---"))
        {
          serverName = "InterSession";
          break;
        }
      }

    if (serverName != null)
      notifyHeaderChanged ();
  }

  // ---------------------------------------------------------------------------------//
  private void notifyHeaderChanged ()
  // ---------------------------------------------------------------------------------//
  {
    for (SessionHeaderListener listener : headerListeners)
      listener.headerChanged ();
  }

  // Entrega o historico e registra sob o lock de add(), evitando perda de registros
  // entre leitura e inscricao no modo Spy (e cobrindo sessoes ja carregadas no Replay).
  // ---------------------------------------------------------------------------------//
  public synchronized void addRecordListener (SessionRecordListener listener)
  // ---------------------------------------------------------------------------------//
  {
    for (SessionRecord sessionRecord : sessionRecords)
      listener.recordAdded (sessionRecord);

    recordListeners.add (listener);
  }

  // ---------------------------------------------------------------------------------//
  public SessionRecord getNext (SessionRecordType dataRecordType)
  // ---------------------------------------------------------------------------------//
  {
    for (SessionRecord dataRecord : sessionRecords)
      if (dataRecord.getDataRecordType () == dataRecordType)
        return dataRecord;
    return null;
  }

  // ---------------------------------------------------------------------------------//
  public SessionRecord getBySize (int size)
  // ---------------------------------------------------------------------------------//
  {
    for (SessionRecord dataRecord : sessionRecords)
      if (dataRecord.size () == size)
        return dataRecord;
    return null;
  }

  // ---------------------------------------------------------------------------------//
  public void save (File file)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      PrintWriter writer = new PrintWriter (file, "UTF-8");
      for (SessionRecord dataRecord : sessionRecords)
      {
        writer.printf ("%s %s %s%n",
            dataRecord.getSource () == Source.CLIENT ? "Client" : "Server",
            dataRecord.isGenuine () ? " " : "*", dataRecord.getDateTime ());

        // scramble user input
        if (safeFlag)
        {
          ReplyBuffer message = dataRecord.getMessage ();

          if (message instanceof TN3270ExtendedCommand)
            message = ((TN3270ExtendedCommand) message).getCommand ();

          if (message instanceof AIDCommand)
            ((AIDCommand) message).scramble ();
        }

        // write the data buffer after adding back the double-FF bytes
        writer.println (Dm3270Utility.toHex (dataRecord.getMessage ().getTelnetData ()));
      }
      writer.close ();
    }
    catch (FileNotFoundException | UnsupportedEncodingException e)
    {
      logger.error ("Error saving session", e);
    }
  }

  // ---------------------------------------------------------------------------------//
  public void safeSave (File file)
  // ---------------------------------------------------------------------------------//
  {
    safeFlag = true;
    save (file);
    safeFlag = false;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Iterator<SessionRecord> iterator ()
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecords.iterator ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    for (SessionRecord record : sessionRecords)
    {
      text.append (record);
      text.append ("\n");
    }

    if (text.length () > 0)
      text.deleteCharAt (text.length () - 1);
    else
      text.append ("Empty session");

    return text.toString ();
  }
}