package com.bytezone.dm3270.buffers;

import com.bytezone.dm3270.screen.ScreenTarget;

public interface Buffer
{
  public abstract byte[] getData ();

  public abstract byte[] getTelnetData ();

  public abstract int size ();

  public abstract void process (ScreenTarget screen);
}