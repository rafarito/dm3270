package com.bytezone.dm3270.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.ScreenDimensions;

// Duble de PluginHost que grava chamadas em PluginProbe para preservar ordem com os plugins.
// -----------------------------------------------------------------------------------//
public class RecordingPluginHost implements PluginHost
// -----------------------------------------------------------------------------------//
{
  private final ScreenDimensions screenDimensions = new ScreenDimensions (24, 80);
  private final Cursor cursor = new RecordingCursor ();
  private final List<Field> fields = new ArrayList<> ();

  private AIDCommand reply;

  // ---------------------------------------------------------------------------------//
  public void setReply (AIDCommand reply)
  // ---------------------------------------------------------------------------------//
  {
    this.reply = reply;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public ScreenDimensions getScreenDimensions ()
  // ---------------------------------------------------------------------------------//
  {
    return screenDimensions;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean isKeyboardLocked ()
  // ---------------------------------------------------------------------------------//
  {
    return false;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Cursor getScreenCursor ()
  // ---------------------------------------------------------------------------------//
  {
    return cursor;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public List<Field> getFields ()
  // ---------------------------------------------------------------------------------//
  {
    return fields;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Optional<Field> getFieldAt (int position)
  // ---------------------------------------------------------------------------------//
  {
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void lockKeyboard (String keyName)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("host.lockKeyboard:" + keyName);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void setAID (byte aid)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("host.setAID:" + aid);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public AIDCommand readModifiedFields ()
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("host.readModifiedFields");
    return reply;
  }

  // Subclasse duble de Cursor; passa colaboradores reais ao super para falhar rapido se usados.
  // ---------------------------------------------------------------------------------//
  private static final class RecordingCursor extends Cursor
  // ---------------------------------------------------------------------------------//
  {
    RecordingCursor ()
    {
      super (new HeadlessScreenTarget (), new ScreenDimensions (24, 80));
    }

    @Override
    public int getLocation ()
    {
      return 0;
    }

    @Override
    public boolean isVisible ()
    {
      return false;
    }

    @Override
    public void setVisible (boolean visible)
    {
      PluginProbe.record ("cursor.setVisible:" + visible);
    }

    @Override
    public void moveTo (int newPosition)
    {
      PluginProbe.record ("cursor.moveTo:" + newPosition);
    }
  }
}
