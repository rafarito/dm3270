package com.bytezone.dm3270.screen;

// -----------------------------------------------------------------------------------//
public interface TsoCommandTarget
// -----------------------------------------------------------------------------------//
{
  boolean isTSOCommandScreen ();

  Field getTSOCommandField ();

  void addTSOCommand (String command);
}
