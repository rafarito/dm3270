package com.bytezone.dm3270.display;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.structuredfields.SetReplyModeSF;

// Estado da resposta 3270 ao host (AID, reply mode e Read Modified All).
// Mantem AID inicial 0 (ate restoreKeyboard) e logger de Screen.class por compatibilidade.
// -----------------------------------------------------------------------------------//
final class ScreenReply
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (Screen.class);

  private static final byte[] saveScreenReplyTypes =
      { Attribute.XA_HIGHLIGHTING, Attribute.XA_FGCOLOR, Attribute.XA_CHARSET,
        Attribute.XA_BGCOLOR, Attribute.XA_TRANSPARENCY };

  private final ScreenPacker screenPacker;
  private final IntSupplier cursorLocation;

  private byte currentAID;
  private byte replyMode;
  private byte[] replyTypes = new byte[0];
  private boolean readModifiedAll = false;

  // ---------------------------------------------------------------------------------//
  ScreenReply (ScreenPacker screenPacker, IntSupplier cursorLocation)
  // ---------------------------------------------------------------------------------//
  {
    this.screenPacker = screenPacker;
    this.cursorLocation = cursorLocation;
  }

  // ---------------------------------------------------------------------------------//
  void setAID (byte aid)
  // ---------------------------------------------------------------------------------//
  {
    currentAID = aid;
  }

  // ---------------------------------------------------------------------------------//
  byte getAID ()
  // ---------------------------------------------------------------------------------//
  {
    return currentAID;
  }

  // ---------------------------------------------------------------------------------//
  void setReplyMode (byte replyMode, byte[] replyTypes)
  // ---------------------------------------------------------------------------------//
  {
    this.replyMode = replyMode;
    this.replyTypes = replyTypes;
  }

  // called from Screen.checkRecording()
  // ---------------------------------------------------------------------------------//
  void recordBuffer (Consumer<AIDCommand> history)
  // ---------------------------------------------------------------------------------//
  {
    byte savedReplyMode = replyMode;
    byte[] savedReplyTypes = replyTypes;

    setReplyMode (SetReplyModeSF.RM_CHARACTER, saveScreenReplyTypes);
    history.accept (readBuffer ());

    setReplyMode (savedReplyMode, savedReplyTypes);
  }

  // ---------------------------------------------------------------------------------//
  AIDCommand readModifiedFields ()
  // ---------------------------------------------------------------------------------//
  {
    return screenPacker.readModifiedFields (currentAID, cursorLocation.getAsInt (),
        readModifiedAll);
  }

  // ---------------------------------------------------------------------------------//
  AIDCommand readBuffer ()
  // ---------------------------------------------------------------------------------//
  {
    return screenPacker.readBuffer (currentAID, cursorLocation.getAsInt (), replyMode,
        replyTypes);
  }

  // ---------------------------------------------------------------------------------//
  AIDCommand readModifiedFields (byte type)
  // ---------------------------------------------------------------------------------//
  {
    switch (type)
    {
      case Command.READ_MODIFIED_F6:
        return readModifiedFields ();

      case Command.READ_MODIFIED_ALL_6E:
        readModifiedAll = true;
        AIDCommand command = readModifiedFields ();
        readModifiedAll = false;
        return command;

      default:
        logger.warn ("Unknown type in Screen.readModifiedFields()");
        break;
    }

    return null;
  }
}
