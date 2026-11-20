package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

// -----------------------------------------------------------------------------------//
final class VolumeLayout implements DatasetListLayout
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Override
  public int linesPerDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return 1;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int firstDataRow ()
  // ---------------------------------------------------------------------------------//
  {
    return 7;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void read (DatasetSummary summary, List<Field> rowFields, Dataset delta)
  // ---------------------------------------------------------------------------------//
  {
    if (rowFields.size () != 3)
      return;

    summary.setVolume (rowFields.get (2).getText ().trim ());
    delta.setVolume (summary.getVolume ());
  }
}
