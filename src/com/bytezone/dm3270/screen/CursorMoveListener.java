package com.bytezone.dm3270.screen;

public interface CursorMoveListener
{
  public abstract void cursorMoved (int oldLocation, int newLocation, Field field);
}