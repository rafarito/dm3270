package com.bytezone.dm3270.application;

import javafx.application.Application;

/**
 * Stable entrypoint for java -jar.
 */
public final class Launcher
{
  private Launcher ()
  {
  }

  public static void main (String[] args)
  {
    Application.launch (Console.class, args);
  }
}