package com.bytezone.dm3270.display;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Manages text selection state on the terminal screen.
 * Tracks selection start/end positions and provides methods
 * to query and manipulate the selected range.
 */
public class ScreenSelection
{
  private final Screen screen;
  
  private int selectionStart = -1;   // linear position where drag started
  private int selectionEnd = -1;     // linear position where drag currently is
  private boolean active = false;    // true during an active drag
  
  public ScreenSelection (Screen screen)
  {
    this.screen = screen;
  }
  
  /**
   * Begin a new selection at the given linear screen position.
   */
  public void startSelection (int position)
  {
    clearSelection ();
    selectionStart = position;
    selectionEnd = position;
    active = true;
    
    // Highlight the starting position immediately
    screen.getScreenPosition (position).setSelected (true);
    screen.redrawRange (position, position);
  }
  
  /**
   * Extend the current selection to a new position (during drag).
   */
  public void extendSelection (int position)
  {
    if (!active)
      return;
    
    int oldMin = getMinPosition ();
    int oldMax = getMaxPosition ();
    
    selectionEnd = position;
    
    int newMin = getMinPosition ();
    int newMax = getMaxPosition ();
    
    // Update only positions that changed selection state
    updateSelectionFlags (oldMin, oldMax, newMin, newMax);
    screen.redrawSelection (oldMin, oldMax, newMin, newMax);
  }
  
  /**
   * Finish the selection (on mouse release).
   */
  public void endSelection (int position)
  {
    if (!active)
      return;
    
    selectionEnd = position;
    active = false;
    
    // Redraw final state
    int min = getMinPosition ();
    int max = getMaxPosition ();
    updatePositionFlags (min, max, true);
    screen.redrawRange (min, max);
  }
  
  /**
   * Clear the current selection and reset all selected flags.
   */
  public void clearSelection ()
  {
    if (selectionStart >= 0 && selectionEnd >= 0)
    {
      int min = getMinPosition ();
      int max = getMaxPosition ();
      updatePositionFlags (min, max, false);
      screen.redrawRange (min, max);
    }
    
    selectionStart = -1;
    selectionEnd = -1;
    active = false;
  }
  
  /**
   * Returns true if there is a non-empty selection.
   */
  public boolean hasSelection ()
  {
    return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
  }
  
  /**
   * Returns true if an active drag is in progress.
   */
  public boolean isActive ()
  {
    return active;
  }
  
  /**
   * Returns the selected text as a String, preserving line breaks.
   */
  public String getSelectedText ()
  {
    if (!hasSelection ())
      return "";
    
    int min = getMinPosition ();
    int max = getMaxPosition ();
    ScreenDimensions dims = screen.getScreenDimensions ();
    StringBuilder sb = new StringBuilder ();
    
    int lastRow = min / dims.columns;
    for (int i = min; i <= max; i++)
    {
      int row = i / dims.columns;
      if (row != lastRow)
      {
        // Trim trailing spaces from the previous line
        trimTrailingSpaces (sb);
        sb.append ('\n');
        lastRow = row;
      }
      sb.append (screen.getScreenPosition (i).getChar ());
    }
    
    trimTrailingSpaces (sb);
    return sb.toString ();
  }
  
  private void trimTrailingSpaces (StringBuilder sb)
  {
    int len = sb.length ();
    while (len > 0 && sb.charAt (len - 1) == ' ')
      len--;
    sb.setLength (len);
  }
  
  private int getMinPosition ()
  {
    return Math.min (selectionStart, selectionEnd);
  }
  
  private int getMaxPosition ()
  {
    return Math.max (selectionStart, selectionEnd);
  }
  
  private void updatePositionFlags (int min, int max, boolean selected)
  {
    for (int i = min; i <= max; i++)
      screen.getScreenPosition (i).setSelected (selected);
  }
  
  private void updateSelectionFlags (int oldMin, int oldMax, int newMin, int newMax)
  {
    // Clear flags outside new range
    for (int i = oldMin; i <= oldMax; i++)
    {
      boolean inNewRange = (i >= newMin && i <= newMax);
      screen.getScreenPosition (i).setSelected (inNewRange);
    }
    
    // Set flags for new positions not in old range  
    for (int i = newMin; i <= newMax; i++)
      screen.getScreenPosition (i).setSelected (true);
  }

  /**
   * Briefly flashes the selection off and on as visual feedback (e.g. after copy).
   */
  public void flashSelection ()
  {
    if (!hasSelection ())
      return;

    int min = getMinPosition ();
    int max = getMaxPosition ();
    int savedStart = selectionStart;
    int savedEnd = selectionEnd;

    // Temporarily clear the visual highlight
    updatePositionFlags (min, max, false);
    screen.redrawRange (min, max);

    // Restore after a brief pause
    PauseTransition pause = new PauseTransition (Duration.millis (100));
    pause.setOnFinished (e ->
    {
      // Only restore if the selection wasn't changed in the meantime
      if (selectionStart == savedStart && selectionEnd == savedEnd)
      {
        updatePositionFlags (min, max, true);
        screen.redrawRange (min, max);
      }
    });
    pause.play ();
  }
}
